package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;
import java.util.Map;

import io.github.cpsc559.team16.common.messaging.AckObjectTypes;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ChatLog;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;

/**
 * Handles all incoming messages from the Addressing Server.
 * <p>
 * This includes:
 * <ul>
 * <li>Processing ACK messages upon successful registration, which assigns a PID
 * and initializes the chat log.</li>
 * <li>Handling UPDATE messages containing lists of active peer servers,
 * forwarding them to the core ChatServer logic.</li>
 * </ul>
 * This handler is registered in the {@link ChatServer} and invoked via the
 * {@link ConnectionHandler} interface.
 */
class AddressingServerHandler implements ConnectionHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Debug verbosity level, configurable via environment variable DEBUG_LEVEL
     * (default = 5 right now to make debuggin easy).
     */
    public static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "5"));

    // Debug level constants
    private static final int DEBUG_NONE = 0; // No debug output (production mode)
    private static final int DEBUG_BASIC = 1; // Basic info: startup, shutdown, major events
    private static final int DEBUG_NORMAL = 2; // Normal operation details: connections, requests
    private static final int DEBUG_DETAILED = 3; // Detailed flow: entering methods, decision points
    private static final int DEBUG_LOW_LEVEL = 4; // Low-level operations: byte-level I/O, parsing
    private static final int DEBUG_EXTREME = 5; // Extreme detail: everything, for deep debugging

    /**
     * Outputs debug logs according to the specified verbosity level.
     *
     * @param level   the severity/detail level of the message
     * @param message the message content to print
     */
    private static void debug(int level, String message) {
        if (level <= DEBUG_LEVEL) {
            String prefix = switch (level) {
                case DEBUG_BASIC -> "[BASIC] ";
                case DEBUG_NORMAL -> "[NORMAL] ";
                case DEBUG_DETAILED -> "[DETAILED] ";
                case DEBUG_LOW_LEVEL -> "[LOW_LEVEL] ";
                case DEBUG_EXTREME -> "[EXTREME] ";
                default -> "[INFO] ";
            };
            System.out.println(prefix + message);
        }
    }

    /**
     * This method is part of the {@link ConnectionHandler} interface.
     * Since the Addressing Server only sends {@link BaseAddrServerMessage} objects,
     * this generic method should never be used. Logs an error if invoked.
     *
     * @param message ignored
     * @param ctx     connection context
     * @param key     selector key
     */
    @Override
    public void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key) {
        debug(DEBUG_BASIC,
                "[ADDR_SERVER] Invalid message type passed to AddressingServerHandler: " + message.getClass());
    }

    /**
     * Main entry point for processing messages received from the Addressing Server.
     * Delegates to internal methods based on message type and objectType.
     *
     * @param message the incoming Addressing Server message
     * @param ctx     context for the connection
     * @param key     NIO selector key
     */
    public void handle(BaseAddrServerMessage<?> message, ConnectionContext ctx, SelectionKey key) {
        try {
            debug(DEBUG_LOW_LEVEL, "[ADDR_SERVER] Received message:\n" + message.toJson());

            String type = message.getMsgType();
            String objectType = message.getObjectType();

            debug(DEBUG_DETAILED, "[ADDR_SERVER] Dispatching message — type: " + type + ", objectType: " + objectType);

            if ("ACK".equalsIgnoreCase(type)) {
                handleAck(message, ctx, key);
            } else if ("UPDATE".equalsIgnoreCase(type) && "ChatServerRecord".equalsIgnoreCase(objectType)) {
                handleUpdate(message, ctx, key);
            } else {
                debug(DEBUG_NORMAL, "[ADDR_SERVER] Unhandled message — type: " + type + ", objectType: " + objectType);
            }

        } catch (Exception e) {
            debug(DEBUG_BASIC, "[ADDR_SERVER] Failed to handle message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles registration acknowledgment (ACK) messages from the Addressing
     * Server.
     * Sets the server’s assigned PID, initializes the local chat log, and signals
     * registration success.
     *
     * @param message the ACK message containing the assigned PID
     * @param ctx     connection context
     * @param key     NIO selector key
     */
    private void handleAck(BaseAddrServerMessage<?> message, ConnectionContext ctx, SelectionKey key) {
        debug(DEBUG_NORMAL, "[ADDR_SERVER] Handling ACK...");

        try { // NOTE: Please try to use the AckObjectTypes and MessageTypes and ObjectTypes declared in the Messaging module
            // This helps ensure there are no runtime errors due to syntax errors. It also helps with readability IMO.
            if (!AckObjectTypes.REGISTERED.equals(message.getObjectType())) {
                debug(DEBUG_BASIC, "[ADDR_SERVER] Ignoring ACK with objectType: " + message.getObjectType());
                return;
            }

            String payload = message.getPayload().toString();
            int newID = Integer.parseInt(payload);

            // Set ID first
            ChatServer.setID(newID);

            String CHATLOG_FILE = "src/main/java/com/Logs/chatlog_" + newID + ".log";
            String INDEX_FILE = "src/main/java/com/Logs/index_" + newID + ".json";
            ChatLog chatLog = new ChatLog(CHATLOG_FILE, INDEX_FILE);

            ChatServer.setChatLog(chatLog);

            // Then set registered flag and wake up selector
            ChatServer.setRegistered(true);
            key.selector().wakeup(); // Wake up the selector to check the registered flag

            debug(DEBUG_BASIC, "[ADDR_SERVER] Registration successful — assigned PID: " + newID);
        } catch (NumberFormatException e) {
            debug(DEBUG_BASIC, "[ADDR_SERVER] Failed to parse PID from ACK payload: " + message.getPayload());
        }
    }

    /**
     * Processes UPDATE messages containing a {@link ChatServerRecord} representing
     * a peer server.
     * Converts the payload to a JSON object and forwards it to
     * {@link ChatServer#processChatServerList}.
     *
     * @param message the UPDATE message with peer server details
     * @param ctx     connection context
     * @param key     NIO selector key
     */
    private void handleUpdate(BaseAddrServerMessage<?> message, ConnectionContext ctx, SelectionKey key) {
        debug(DEBUG_NORMAL, "[ADDR_SERVER] Handling UPDATE for ChatServerRecord...");

        if (!"ChatServerRecord".equals(message.getObjectType())) {
            debug(DEBUG_BASIC, "[ADDR_SERVER] UPDATE ignored — objectType is not ChatServerRecord");
            return;
        }

        try {
            Object payload = message.getPayload();
            JSONArray chatServersArray = new JSONArray();

            // Handle single ChatServerRecord
            if (payload instanceof ChatServerRecord record) {
                String jsonStr = objectMapper.writeValueAsString(record);
                chatServersArray.put(new JSONObject(jsonStr));
            } else if (payload instanceof Map<?, ?> map) {
                chatServersArray.put(new JSONObject(map));
            } else {
                debug(DEBUG_BASIC, "[ADDR_SERVER] Unexpected payload type: " + payload.getClass());
                return;
            }

            JSONObject wrapper = new JSONObject();
            wrapper.put("chatServers", chatServersArray);

            debug(DEBUG_LOW_LEVEL, "[ADDR_SERVER] Forwarding peer list to processChatServerList()");
            ChatServer.processChatServerList(wrapper.toString(), ChatServer.getSelector());

        } catch (Exception e) {
            debug(DEBUG_BASIC, "[ADDR_SERVER] Failed to handle UPDATE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

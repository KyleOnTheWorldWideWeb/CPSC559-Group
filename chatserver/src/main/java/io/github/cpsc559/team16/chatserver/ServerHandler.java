package io.github.cpsc559.team16.chatserver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ChatLogUpdate;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;
import io.github.cpsc559.team16.common.utilities.ServerServerMessage;

@SuppressWarnings("unused")
class ServerHandler implements ConnectionHandler {

    public static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "5"));

    // Debug level constants
    private static final int DEBUG_NONE = 0; // No debug output (production mode)
    private static final int DEBUG_BASIC = 1; // Basic info: startup, shutdown, major events
    private static final int DEBUG_NORMAL = 2; // Normal operation details: connections, requests
    private static final int DEBUG_DETAILED = 3; // Detailed flow: entering methods, decision points
    private static final int DEBUG_LOW_LEVEL = 4; // Low-level operations: byte-level I/O, parsing
    private static final int DEBUG_EXTREME = 5; // Extreme detail: everything, for deep debugging

    private static void debug(int level, String message) {
        if (level <= DEBUG_LEVEL) {
            String prefix = switch (level) {
                case 1 -> "[BASIC] ";
                case 2 -> "[NORMAL] ";
                case 3 -> "[DETAILED] ";
                case 4 -> "[LOW_LEVEL] ";
                case 5 -> "[EXTREME] ";
                default -> "[INFO] ";
            };
            System.out.println(prefix + message);
        }
    }

    public void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key) {
        if (!(message instanceof ServerServerMessage msg)) {

            if (message instanceof ClientServerMessage clientMsg) {
                ConnectionHandler clientHandler = ChatServer.getHandler(ChatServer.ConnectionType.CLIENT);
                if (clientHandler != null) {
                    clientHandler.handle(clientMsg, ctx, key);
                } else {
                    System.err.println("No ClientHandler found in handler map.");
                }
            } else {
                System.err.println("Unhandled message type in ServerHandler: " + message.getClass().getSimpleName());
            }
            return;
        } else {

            debug(DEBUG_DETAILED, String.format(
                    "[SERVER] Received ServerServerMessage:\n" +
                            "  Command   = %s\n" +
                            "  Content   = %s\n" +
                            "  MessageID = %s\n" +
                            "  Sender    = %s\n" +
                            "  Receiver  = %s\n" +
                            "  TimeSent  = %s",
                    msg.getCommand(),
                    msg.getContent(),
                    msg.getMessageId(),
                    msg.getSender(),
                    msg.getReceiver(),
                    msg.getTimeSent()));

            ServerServerMessage reply;

            try {
                if (msg.getCommand().equals("REQUEST_CHATLOG")) {

                    try {
                        int senderPid = Integer.parseInt(msg.getSender());
                        ctx.peerID = senderPid;

                        if (!ChatServer.getConnectedPeers().containsKey(senderPid)) {
                            ChatServer.getConnectedPeers().put(senderPid, ctx);
                            debug(DEBUG_BASIC, "[PEER] Added incoming peer PID=" + senderPid + " to connectedPeers");
                        }

                    } catch (NumberFormatException e) {
                        debug(DEBUG_BASIC, "Invalid peer ID in REQUEST_CHATLOG sender field: " + msg.getSender());
                    }

                    reply = getChatLogContents(msg.getSender());
                    printPrettyJson(reply.toJson());
                    WriteUtils.enqueueResponse(ctx, key, reply.toJson() + "\n");
                    key.selector().wakeup(); // ensure selector wakes to handle OP_WRITE

                } else if (msg.getCommand().equals("RESPONSE_CHATLOG")) {

                    int senderPid = Integer.parseInt(msg.getSender());
                    ctx.peerID = senderPid;

                    if (!ChatServer.getConnectedPeers().containsKey(senderPid)) {
                        ChatServer.getConnectedPeers().put(senderPid, ctx);
                        debug(DEBUG_BASIC, "[PEER] Added incoming peer PID=" + senderPid + " to connectedPeers");
                    }
                    mergeChatlog(msg);
                    System.out.println("merged chatlog with peer: " + msg.getSender());

                } else if (msg.getCommand().equals("PING")) {
                    debug(DEBUG_NORMAL, "Received PING from " + msg.getSender());
                    ServerServerMessage pong = new ServerServerMessage(
                            String.valueOf(ChatServer.getID()),
                            msg.getSender(),
                            "PONG",
                            "");
                    WriteUtils.enqueueResponse(ctx, key, pong.toJson() + "\n");
                    key.selector().wakeup();
                    return;
                } else if (msg.getCommand().equals("PONG")) {
                    ctx.awaitingPong = false;
                    ctx.missedPongs = 0;
                    ctx.lastActivityTime = System.currentTimeMillis();
                    debug(DEBUG_NORMAL, "Received PONG from " + msg.getSender());
                    return;
                }

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        }
    }

    private static void mergeChatlog(ServerServerMessage msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            debug(DEBUG_BASIC, "Received empty chat log. Nothing to merge.");
            return;
        }

        List<ClientServerMessage> messages = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    ClientServerMessage message = BaseMessage.fromJson(line, ClientServerMessage.class);
                    messages.add(message);
                } catch (Exception e) {
                    System.err.println("Failed to parse chat log message line: " + line);
                    e.printStackTrace();
                }
            }

            if (messages.isEmpty()) {
                debug(DEBUG_BASIC, "No valid messages found in received chat log.");
                return;
            }

            ChatLogUpdate update = new ChatLogUpdate(
                    msg.getSender(),
                    msg.getReceiver(),
                    messages);

            ChatServer.getChatLog().merge(update);
            debug(DEBUG_BASIC, "Merged " + messages.size() + " messages from received chat log.");

        } catch (IOException e) {
            System.err.println("Error while reading chat log content: " + e.getMessage());
        }
    }

    private static ServerServerMessage getChatLogContents(String receiverPid) {
        StringBuilder logContents = new StringBuilder();
        String file = ChatServer.getChatLogFile();

        try (BufferedReader logReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = logReader.readLine()) != null) {
                logContents.append(line).append("\n");
            }
            System.out.println("[INFO] Chat log successfully loaded from file.");
        } catch (IOException e) {
            System.err.println("Error reading chat log: " + e.getMessage());
            return null;
        }

        return new ServerServerMessage(
                String.valueOf(ChatServer.getID()), // sender PID
                receiverPid, // receiver PID
                "RESPONSE_CHATLOG", // command
                logContents.toString() // chat log as content
        );
    }

    public static void printPrettyJson(String jsonString) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode json = mapper.readTree(jsonString);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            System.out.println(prettyJson);
        } catch (Exception e) {
            System.err.println("Invalid JSON: " + e.getMessage());
        }
    }

}

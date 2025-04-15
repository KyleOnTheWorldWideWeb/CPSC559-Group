package io.github.cpsc559.team16.chatserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cpsc559.team16.common.messaging.RegisterMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ChatLog;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;
import io.github.cpsc559.team16.common.utilities.ServerServerMessage;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.MessageDeserializer;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;

/**
 * The main ChatServer class implements a non-blocking I/O chat server that:
 * <ul>
 * <li>Registers with a central Addressing Server</li>
 * <li>Handles client messaging and username registration</li>
 * <li>Synchronizes chat logs with peer servers</li>
 * <li>Performs periodic heartbeat checks and reconnections</li>
 * </ul>
 * Uses Java NIO's {@link Selector} to manage multiple client and server
 * connections concurrently.
 */
// @SuppressWarnings("unused")
public class ChatServer {

    /**
     * The current debug level for controlling verbosity of server logs.
     * <p>
     * This is configurable at runtime using the environment variable
     * <b>DEBUG_LEVEL</b>.
     * If the environment variable is not set, the default level is
     * {@code DEBUG_EXTREME} (5),
     * meaning all debug messages will be printed.
     * </p>
     * <p>
     * Example usage in shell to reduce output to basic info only:
     *
     * <pre>{@code
     * export DEBUG_LEVEL=1
     * }</pre>
     * </p>
     */
    public static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "2"));

    // Debug level constants

    /**
     * Debug level: No debug output. Use in production mode where logs are minimal.
     */
    private static final int DEBUG_NONE = 0; // No debug output (production mode)

    /**
     * Debug level: Basic events such as startup, shutdown, and major transitions.
     */
    private static final int DEBUG_BASIC = 1; // Basic info: startup, shutdown, major events

    /**
     * Debug level: Normal runtime activity such as new connections or message
     * processing.
     */
    private static final int DEBUG_NORMAL = 2; // Normal operation details: connections, requests

    /**
     * Debug level: Step-by-step logic, including function entry points and internal
     * decisions.
     */
    private static final int DEBUG_DETAILED = 3; // Detailed flow: entering methods, decision points

    /**
     * Debug level: Low-level I/O activity like byte reads/writes and selector
     * state.
     */
    private static final int DEBUG_LOW_LEVEL = 4; // Low-level operations: byte-level I/O, parsing

    /**
     * Debug level: Maximum verbosity including every possible detail.
     * Useful for diagnosing edge cases or unexpected behavior.
     */
    private static final int DEBUG_EXTREME = 5; // Extreme detail: everything, for deep debugging

    /**
     * Logs a debug message to standard output if the message level is
     * less than or equal to the configured {@link #DEBUG_LEVEL}.
     * <p>
     * Each message is prefixed with a tag representing its severity.
     * This helps developers visually filter relevant messages while debugging.
     * </p>
     *
     * @param level   the severity level of the message (0–5)
     * @param message the message to log
     */

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

    /**
     * Path to the file that stores the persistent chat log.
     * <p>
     * Populated after successful registration with the Addressing Server.
     * </p>
     */
    private static String CHATLOG_FILE;

    /**
     * Path to the index file associated with the chat log.
     * <p>
     * Used to speed up retrieval and filtering of chat messages.
     * </p>
     */
    private static String INDEX_FILE;

    /**
     * Instance of {@link ChatLog} used to persist and retrieve chat messages.
     */
    private static ChatLog chatLog;

    /**
     * Unique identifier (PID) assigned to this chat server by the Addressing
     * Server.
     */
    private static int ID;

    /**
     * Host address of the Addressing Server, retrieved from the environment
     * variable
     * {@code ADDRESS_SERVER_IP}. Defaults to {@code 127.0.0.1} if not set.
     */
    private static final String ADDRESSING_SERVER_HOST = System.getenv().getOrDefault("ADDRESS_SERVER_IP", "127.0.0.1");

    /**
     * Port on which the Addressing Server is listening.
     * Retrieved from the environment variable {@code CS_ADDRSERVER_PORT}, or
     * defaults to 49802.
     */
    private static final int ADDRESSING_SERVER_PORT = Integer
            .parseInt(System.getenv().getOrDefault("CS_ADDRSERVER_PORT", "49802"));

    /**
     * Port used for accepting client connections.
     * Dynamically assigned at startup using {@link #getAvailablePort()} if not
     * overridden via env.
     */
    private static int CLIENT_PORT = getAvailablePort();

    /**
     * Port used to accept connections from other peer chat servers.
     * Dynamically assigned at startup.
     */
    private static final int PEER_LISTEN_PORT = getAvailablePort();

    /**
     * Map of connected peer servers, keyed by their PID.
     * Each peer is represented by a {@link ConnectionContext}.
     */
    private static final Map<Integer, ConnectionContext> connectedPeers = new ConcurrentHashMap<>();

    /**
     * Maximum number of clients that can be served concurrently by this server.
     */
    private static final int MAX_CLIENTS = 10;

    /**
     * Shared {@link Selector} instance used to handle all non-blocking connections.
     * <p>
     * Includes clients, peers, and the Addressing Server.
     * </p>
     */
    private static Selector selector;

    /**
     * Flag indicating whether this server has successfully registered with the
     * Addressing Server.
     * <p>
     * Used to block further event loop initialization until registration completes.
     * </p>
     */
    private static volatile boolean registered = false;

    /**
     * Enum representing different types of connections this server can manage.
     * <ul>
     * <li>{@code CLIENT} — incoming user/client connection</li>
     * <li>{@code SERVER} — connection to or from another peer chat server</li>
     * <li>{@code ADDRESSING_SERVER} — initial registration and update
     * coordination</li>
     * </ul>
     */
    enum ConnectionType {
        CLIENT, SERVER, ADDRESSING_SERVER
    }

    /**
     * Maps each {@link ConnectionType} to its respective handler implementation.
     * <p>
     * For example, {@code CLIENT} maps to {@link ClientHandler}.
     * </p>
     */
    private static final Map<ConnectionType, ConnectionHandler> handlerMap = new HashMap<>();

    /**
     * Thread pool used to process JSON message parsing and routing in parallel.
     * <p>
     * Improves responsiveness by offloading CPU-bound work from the selector
     * thread.
     * </p>
     */
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(8);

    /**
     * Lightweight record representing a listener binding between a
     * {@link ServerSocketChannel}
     * and its associated {@link ConnectionType}.
     * <p>
     * Used during selector setup to distinguish client vs. peer listeners.
     * </p>
     *
     * @param channel the server channel
     * @param type    the type of connection it accepts (CLIENT or SERVER)
     */
    private static record ServerBinding(ServerSocketChannel channel, ConnectionType type) {
    }

    /**
     * Entry point for the Chat Server application.
     * <p>
     * This method sets up all necessary components and enters the non-blocking
     * selector event loop.
     * </p>
     * <h3>Initialization steps:</h3>
     * <ol>
     * <li>Configures the handler map for CLIENT, SERVER, and ADDRESSING_SERVER
     * connection types.</li>
     * <li>Opens a {@link Selector} for managing asynchronous I/O.</li>
     * <li>Chooses an available port for client connections, falling back if the
     * default is in use.</li>
     * <li>Initiates registration with the Addressing Server and blocks until
     * registration is complete.</li>
     * </ol>
     *
     * <h3>Post-registration operations:</h3>
     * <ul>
     * <li>Binds listener ports for clients and peer servers.</li>
     * <li>Registers these bindings with the selector for ACCEPT events.</li>
     * <li>Starts the {@link HeartbeatMonitor} to maintain peer connectivity.</li>
     * <li>Continuously processes events from the selector: ACCEPT, READ, WRITE,
     * CONNECT.</li>
     * </ul>
     *
     * <p>
     * All operations are non-blocking, and message dispatching is handled by worker
     * threads.
     * </p>
     *
     * @param args command-line arguments (not used)
     * @throws IOException if a selector or socket operation fails during setup or
     *                     runtime
     */
    public static void main(String[] args) throws IOException {
        debug(DEBUG_BASIC, "Starting NIO Server...");
        debug(DEBUG_DETAILED, "Initializing handlers and selector...");

        handlerMap.put(ConnectionType.CLIENT, new ClientHandler());
        handlerMap.put(ConnectionType.SERVER, new ServerHandler());
        handlerMap.put(ConnectionType.ADDRESSING_SERVER, new AddressingServerHandler());

        selector = Selector.open();
        debug(DEBUG_NORMAL, "Selector opened successfully");

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "2424"));
        if (isPortInUse(port)) {
            port = CLIENT_PORT;
            debug(DEBUG_NORMAL, "Port 2424 is in use. Switching to " + port);
        } else {
            debug(DEBUG_NORMAL, "Port 2424 is available.");
            CLIENT_PORT = port;
        }

        connectToAddressingServer(selector);

        while (!isRegistered()) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            debug(DEBUG_EXTREME, "Selector woke up with " + selector.selectedKeys().size() + " keys");

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                    continue;

                ConnectionContext ctx = (ConnectionContext) key.attachment();
                if (ctx == null || ctx.type != ConnectionType.ADDRESSING_SERVER)
                    continue;

                if (key.isConnectable()) {
                    handleConnect(key);
                } else if (key.isWritable()) {
                    handleWrite(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }

        debug(DEBUG_BASIC, String.format("Chat Server registered with assigned PID: %d", getID()));

        List<ServerBinding> serverBindings = List.of(
                new ServerBinding(setupListener(port), ConnectionType.CLIENT),
                new ServerBinding(setupListener(PEER_LISTEN_PORT), ConnectionType.SERVER));

        for (ServerBinding binding : serverBindings) {
            binding.channel().register(selector, SelectionKey.OP_ACCEPT, binding.type());
            debug(DEBUG_NORMAL, "Registered " + binding.type() + " server binding");
        }

        debug(DEBUG_BASIC, String.format("Listening for clients on %d, peers on %d", port, PEER_LISTEN_PORT));

        debug(DEBUG_BASIC, "Starting heartbeat");

        Thread heartbeatThread = new Thread(new HeartbeatMonitor(connectedPeers));
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            debug(DEBUG_EXTREME, "Processing selector events...");

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                try {
                    if (!key.isValid()) {
                        debug(DEBUG_DETAILED, "Skipping invalid key");
                        continue;
                    }
                    if (key.isAcceptable()) {
                        debug(DEBUG_NORMAL, "Handling new connection accept");
                        handleAccept(key, selector);
                    } else if (key.isReadable()) {
                        debug(DEBUG_DETAILED, "Handling read operation");
                        handleRead(key);
                    } else if (key.isWritable()) {
                        debug(DEBUG_DETAILED, "Handling write operation");
                        handleWrite(key);
                    } else if (key.isConnectable()) {
                        debug(DEBUG_DETAILED, "Finishing pending connection");
                        handleConnect(key);
                    }
                } catch (IOException ex) {
                    debug(DEBUG_NORMAL, "Connection error: " + ex.getMessage());
                    closeConnection(key);
                }
            }
        }
    }

    /**
     * Completes a non-blocking connection process when the selector indicates that
     * the channel is connectable.
     * <p>
     * This method is invoked when a {@link SelectionKey} with {@code OP_CONNECT} is
     * ready. It finalizes the connection
     * using {@link SocketChannel#finishConnect()}, then updates the key's interest
     * ops to allow reading and writing.
     * </p>
     *
     * <p>
     * If the connected socket is a peer server (i.e.,
     * {@link ConnectionType#SERVER}), and it's not a connection
     * to self, the method:
     * </p>
     * <ul>
     * <li>Stores the connection context in the {@code connectedPeers} map using the
     * peer ID</li>
     * <li>Immediately sends a {@code REQUEST_CHATLOG} message to the newly
     * connected peer to synchronize logs</li>
     * </ul>
     *
     * @param key the {@link SelectionKey} associated with a peer server or
     *            addressing server connection
     * @throws IOException if an I/O error occurs while finishing the connection or
     *                     accessing the channel
     */
    private static void handleConnect(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        SocketChannel socketChannel = ctx.socketChannel;

        debug(DEBUG_DETAILED, "handleConnect() called for: " + socketChannel.getRemoteAddress());

        if (socketChannel.finishConnect()) {
            debug(DEBUG_NORMAL, "Finished connecting to peer: " + socketChannel.getRemoteAddress());
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            if (ctx.type == ConnectionType.SERVER && ctx.peerID != ID) {
                debug(DEBUG_BASIC, "Marked peer " + ctx.peerID + " as connected.");
                connectedPeers.put(ctx.peerID, ctx);
                requestChatLogFor(ctx, key);
            }
        } else {
            debug(DEBUG_BASIC, "Connection not yet complete");
        }
    }

    /**
     * Sends a {@code REQUEST_CHATLOG} message to a connected peer server to
     * initiate log synchronization.
     * <p>
     * This method is used after a successful connection to a peer server. It
     * constructs a {@link ServerServerMessage}
     * with the command {@code REQUEST_CHATLOG}, indicating that this server is
     * requesting the full chat log
     * from the peer. This is a critical step for eventual consistency, ensuring
     * that newly joined or reconnected
     * peers can update their chat state.
     * </p>
     * <p>
     * The message is converted to a UTF-8 encoded JSON string and placed in the
     * peer's {@code writeQueue}.
     * The selector interest ops are updated to include {@code OP_WRITE}, ensuring
     * that the message will be
     * transmitted on the next writable event.
     * </p>
     *
     * @param ctx the {@link ConnectionContext} of the connected peer server
     * @param key the {@link SelectionKey} associated with the peer's socket channel
     */
    private static void requestChatLogFor(ConnectionContext ctx, SelectionKey key) {
        try {
            ServerServerMessage request = new ServerServerMessage(
                    String.valueOf(ID),
                    String.valueOf(
                            ctx.peerID),
                    "REQUEST_CHATLOG",
                    "");

            String json = request.toJson() + "\n";
            synchronized (ctx.writeQueue) {
                ctx.writeQueue.add(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
            }

            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            debug(DEBUG_NORMAL,
                    "Sent REQUEST_CHATLOG to peer PID=" + ctx.peerID + " at " + ctx.socketChannel.getRemoteAddress());

        } catch (Exception e) {
            debug(DEBUG_BASIC, "Failed to send REQUEST_CHATLOG to peer: " + e.getMessage());
        }
    }

    /**
     * Initializes a non-blocking {@link ServerSocketChannel} on the given port to
     * listen for new TCP connections.
     * <p>
     * This is used for both client and server (peer) bindings. The resulting
     * listener channel is returned and
     * expected to be registered with the selector using {@code OP_ACCEPT} so that
     * new connections can be
     * asynchronously accepted during event loop processing.
     * </p>
     * <p>
     * The returned channel is configured in non-blocking mode to allow integration
     * with the {@link Selector}
     * for scalable handling of many concurrent connections without threads per
     * socket.
     * </p>
     *
     * @param port the TCP port on which to listen for incoming connections
     * @return an initialized {@link ServerSocketChannel} configured for
     *         non-blocking accept
     * @throws IOException if the server socket channel cannot be created or bound
     *                     to the given port
     */

    private static ServerSocketChannel setupListener(int port) throws IOException {
        debug(DEBUG_DETAILED, "Setting up listener on port " + port);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);
        debug(DEBUG_NORMAL, "Listener setup complete on port " + port);
        return server;
    }

    /**
     * Initiates a non-blocking registration flow to the central Addressing Server.
     * <p>
     * This server must register with the Addressing Server to receive a unique PID
     * (Process ID)
     * and a list of currently active peer servers. This registration is performed
     * by sending
     * a {@link BaseAddrServerMessage} of type {@code REGISTER}, which includes
     * metadata
     * such as IP address, port numbers, and client capacity.
     * </p>
     * <p>
     * The connection is configured in non-blocking mode and registered with the
     * selector
     * using {@code OP_CONNECT}, so that it can be completed during the selector's
     * event loop.
     * The registration message is queued in the
     * {@link ConnectionContext#writeQueue} and will be
     * transmitted upon a writable event.
     * </p>
     *
     * @param selector the selector managing the main event loop; used to register
     *                 the channel
     */
    private static void connectToAddressingServer(Selector selector) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(ADDRESSING_SERVER_HOST, ADDRESSING_SERVER_PORT));

            ConnectionContext ctx = new ConnectionContext(channel);
            ctx.type = ConnectionType.ADDRESSING_SERVER;

            // This creates a registration message and a properly formed chat server record
            // all in one.
            RegisterMessage<ChatServerRecord> registrationMsg = RegisterMessage.fromChatServer(CLIENT_PORT,
                    PEER_LISTEN_PORT,
                    ADDRESSING_SERVER_PORT,
                    MAX_CLIENTS);

            String json = registrationMsg.toJson() + "\n";
            ctx.writeQueue.add(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));

            channel.register(selector, SelectionKey.OP_CONNECT, ctx);
            debug(DEBUG_BASIC, "Initiated non-blocking registration to Addressing Server");
        } catch (IOException e) {
            debug(DEBUG_BASIC, "Failed to connect to Addressing Server: " + e.getMessage());
        }
    }

    /**
     * Processes a list of active chat servers received from the Addressing Server
     * and initiates
     * non-blocking peer connections.
     * <p>
     * The method expects a JSON-formatted string containing a {@code chatServers}
     * array. Each entry
     * represents a {@link ChatServerRecord}, including the peer's PID, host
     * address, and peer port.
     * </p>
     * <p>
     * For each peer, this server will attempt to connect unless the peer ID matches
     * its own PID.
     * A connection is initiated using {@link #connectToPeerServer}, which performs
     * a non-blocking
     * TCP connect and registers the resulting socket channel with the selector for
     * {@code OP_CONNECT}.
     * </p>
     * <p>
     * This method is typically invoked once after a successful registration with
     * the Addressing Server
     * (i.e., after receiving an ACK with a peer list).
     * </p>
     *
     * @param jsonString the JSON response from the Addressing Server containing the
     *                   peer list
     * @param selector   the selector to register new peer connections for
     *                   non-blocking I/O
     */

    public static void processChatServerList(String jsonString, Selector selector) {
        debug(DEBUG_NORMAL, "Processing chat server list from Addressing Server...");
        printPrettyJson(jsonString);

        try {
            JSONObject response = new JSONObject(jsonString);
            JSONArray chatServers = response.getJSONArray("chatServers");

            debug(DEBUG_DETAILED, "Found " + chatServers.length() + " servers in the list");

            for (int i = 0; i < chatServers.length(); i++) {
                JSONObject server = chatServers.getJSONObject(i);
                int peerID = server.getInt("pid");

                // Skip self
                if (peerID == ID) {
                    debug(DEBUG_LOW_LEVEL, "Skipping self in server list: PID=" + peerID);
                    continue;
                }

                String peerAddress = server.getString("hostAddress");
                int peerPort = server.getInt("peerPort");

                debug(DEBUG_NORMAL,
                        String.format("Attempting connection to peer PID=%d at %s:%d", peerID, peerAddress, peerPort));
                connectToPeerServer(selector, peerAddress, peerPort, peerID);
            }

            debug(DEBUG_BASIC, "Finished processing peer server list.");

        } catch (Exception e) {
            debug(DEBUG_BASIC, "Error parsing chat server list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Initiates a non-blocking TCP connection to a peer chat server.
     * <p>
     * This method is called after receiving a list of peer servers from the
     * Addressing Server.
     * It attempts to establish a connection to one peer, identified by its host,
     * port, and PID.
     * </p>
     *
     * <h3>Steps performed:</h3>
     * <ol>
     * <li>Opens a {@link SocketChannel} in non-blocking mode</li>
     * <li>Starts connection to the given peer address ({@code host:port})</li>
     * <li>Creates and populates a {@link ConnectionContext} with metadata including
     * peer ID and host info</li>
     * <li>Registers the channel with the selector for {@code OP_CONNECT}, allowing
     * the connection to be finalized later</li>
     * <li>Wakes up the selector to ensure the pending connection is processed
     * immediately</li>
     * </ol>
     *
     * <p>
     * If the connection fails (e.g., due to peer unavailability), a debug message
     * is logged at {@code DEBUG_BASIC}
     * and the method exits silently
     * </p>
     *
     * <h3>Integration notes:</h3>
     * <ul>
     * <li>This method is usually called from
     * {@link #processChatServerList(String, Selector)}</li>
     * <li>The {@code OP_CONNECT} event is handled in the selector loop via
     * {@link #handleConnect(SelectionKey)}</li>
     * </ul>
     *
     * @param selector the {@link Selector} that manages all non-blocking channels
     *                 for this server
     * @param host     the IP address or hostname of the peer server
     * @param port     the peer server's listening port (typically the peer-to-peer
     *                 port)
     * @param peerID   the unique process ID of the peer server, as assigned by the
     *                 Addressing Server
     */

    public static void connectToPeerServer(Selector selector, String host, int port, int peerID) {
        debug(DEBUG_BASIC, String.format("Attempting to connect to peer server PID=%d at %s:%d", peerID, host, port));

        try {
            SocketChannel peerChannel = SocketChannel.open();
            peerChannel.configureBlocking(false);
            peerChannel.connect(new InetSocketAddress(host, port));

            ConnectionContext ctx = new ConnectionContext(peerChannel);
            ctx.type = ConnectionType.SERVER;
            ctx.peerID = peerID;
            ctx.host = host;
            ctx.port = port;

            peerChannel.register(selector, SelectionKey.OP_CONNECT, ctx);
            selector.wakeup();

            debug(DEBUG_NORMAL, String.format(
                    "Initiated non-blocking connection to peer PID=%d at %s:%d — registered for OP_CONNECT",
                    peerID, host, port));
        } catch (IOException e) {
            debug(DEBUG_BASIC,
                    String.format("Failed to connect to peer server at %s:%d — %s", host, port, e.getMessage()));
            return;
        }

        debug(DEBUG_DETAILED, "Requesting chat log after peer connection setup...");
    }

    /**
     * Accepts a new incoming connection on a server socket channel.
     * <p>
     * Extracts the server socket channel and its associated {@link ConnectionType}
     * from the key.
     * Accepts the connection using {@link ServerSocketChannel#accept()}, configures
     * it for non-blocking mode,
     * creates a {@link ConnectionContext} for the new connection, and registers it
     * with the selector for {@code OP_READ}.
     * </p>
     *
     * @param key      the selection key representing the server socket ready to
     *                 accept a connection
     * @param selector the main selector used to register the new connection for
     *                 read events
     * @throws IOException if an error occurs during socket accept or registration
     */

    private static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        ConnectionType type = (ConnectionType) key.attachment();
        debug(DEBUG_DETAILED, "Accepting new " + type + " connection");

        SocketChannel socketChannel = serverChannel.accept();
        socketChannel.configureBlocking(false);

        ConnectionContext ctx = new ConnectionContext(socketChannel);
        ctx.type = type;

        socketChannel.register(selector, SelectionKey.OP_READ, ctx);
        debug(DEBUG_NORMAL, "Accepted " + type + " connection from " + socketChannel.getRemoteAddress());

    }

    /**
     * Handles readable events on a socket channel by extracting, decoding,
     * accumulating, and dispatching messages.
     * <p>
     * This method is invoked by the selector when a {@link SocketChannel} is ready
     * for reading.
     * It performs the following operations:
     * </p>
     *
     * <h3>1. Reading and Decoding:</h3>
     * <ul>
     * <li>Attempts to read data into the connection's
     * {@link ConnectionContext#readBuffer}.</li>
     * <li>If {@code -1} is returned (remote has closed the connection), it cleans
     * up the buffer and closes the connection.</li>
     * <li>If bytes are read, they are decoded from UTF-8 into a string and appended
     * to {@code partialData}.</li>
     * </ul>
     *
     * <h3>2. Message Extraction:</h3>
     * <ul>
     * <li>The method scans {@code partialData} for newline-delimited messages (each
     * message is assumed to be one JSON string per line).</li>
     * <li>Complete JSON strings are extracted one-by-one in a loop, trimmed, and
     * removed from the buffer.</li>
     * </ul>
     *
     * <h3>3. Asynchronous Dispatch:</h3>
     * <ul>
     * <li>Each complete JSON message is submitted to a thread in {@code workerPool}
     * for processing.</li>
     * <li>The socket and key are validated to ensure they're still active before
     * dispatching.</li>
     * <li>If the message is from the Addressing Server, it's deserialized using
     * {@link MessageDeserializer} and passed to
     * {@link AddressingServerHandler}.</li>
     * <li>If the message is from a client or peer server, its type is determined
     * using {@link BaseMessage#peekType(String)}.</li>
     * <li>It is then parsed into its specific class ({@link ClientServerMessage} or
     * {@link ServerServerMessage}) and routed to the corresponding handler from
     * {@code handlerMap}.</li>
     * </ul>
     *
     * <p>
     * If deserialization fails or the message type is unrecognized, the error is
     * logged and ignored.
     * </p>
     *
     * @param key the {@link SelectionKey} for the readable channel; used to get the
     *            context and socket
     * @throws IOException if reading from the socket fails
     */
    private static void handleRead(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        SocketChannel socketChannel = ctx.socketChannel;
        debug(DEBUG_DETAILED, "Reading from connection: " + socketChannel.getRemoteAddress());

        int bytesRead = socketChannel.read(ctx.readBuffer);
        ctx.lastActivityTime = System.currentTimeMillis();
        if (bytesRead == -1) {
            ctx.partialData.setLength(0);
            debug(DEBUG_NORMAL, "End of stream reached, closing connection");
            closeConnection(key);
            return;
        }

        debug(DEBUG_LOW_LEVEL, "Read " + bytesRead + " bytes");
        ctx.readBuffer.flip();
        String data = StandardCharsets.UTF_8.decode(ctx.readBuffer).toString();
        ctx.readBuffer.clear();

        debug(DEBUG_EXTREME, "Received data: " + data);
        ctx.partialData.append(data);

        while (true) {
            int newlineIndex = ctx.partialData.indexOf("\n");
            if (newlineIndex < 0)
                break;

            String json = ctx.partialData.substring(0, newlineIndex).trim();
            ctx.partialData.delete(0, newlineIndex + 1);

            if (json.isEmpty())
                continue;

            debug(DEBUG_LOW_LEVEL, "Processing JSON message: " + json);

            if (ctx.type != null) {
                workerPool.submit(() -> {
                    try {
                        debug(DEBUG_DETAILED, "Processing message for connection type: " + ctx.type);

                        if (!key.isValid() || !socketChannel.isOpen()) {
                            debug(DEBUG_BASIC, "Key invalid or channel closed during message processing. Aborting.");
                            return;
                        }

                        if (ctx.type == ConnectionType.ADDRESSING_SERVER) {
                            BaseAddrServerMessage<?> addrMsg = MessageDeserializer.deserializeMessage(json);
                            if (addrMsg == null) {
                                debug(DEBUG_BASIC, "Failed to deserialize Addressing Server message.");
                                return;
                            }

                            ((AddressingServerHandler) handlerMap.get(ctx.type)).handle(addrMsg, ctx, key);
                            return;
                        }

                        BaseMessage base = BaseMessage.peekType(json);
                        if (base == null || base.getType() == null) {
                            debug(DEBUG_BASIC, "Unknown message type; skipping message.");
                            return;
                        }
                        switch (base.getType()) {
                            case "ClientServerMessage" -> {
                                ClientServerMessage clientMsg = BaseMessage.fromJson(json, ClientServerMessage.class);
                                handlerMap.get(ConnectionType.CLIENT).handle(clientMsg, ctx, key);
                            }
                            case "ServerServerMessage" -> {
                                ServerServerMessage serverMsg = BaseMessage.fromJson(json, ServerServerMessage.class);
                                handlerMap.get(ctx.type).handle(serverMsg, ctx, key);
                            }
                            default -> {
                                debug(DEBUG_BASIC, "Unrecognized message type: " + base.getType());
                            }
                        }

                    } catch (Exception e) {
                        debug(DEBUG_NORMAL, "Failed to parse JSON: " + e.getMessage());
                        debug(DEBUG_DETAILED, "Invalid JSON: " + json);
                    }
                });
            }
        }
    }

    /**
     * Handles writable events on a socket channel by flushing messages from the
     * connection's write queue.
     * <p>
     * This method is triggered by the selector when a {@link SocketChannel} is
     * ready to accept outbound data.
     * It retrieves the associated {@link ConnectionContext} and attempts to write
     * queued messages stored in
     * {@link ConnectionContext#writeQueue}.
     * </p>
     *
     * <h3>Write Process:</h3>
     * <ul>
     * <li>Synchronizes on the write queue to ensure thread-safe access, since
     * writes may be enqueued by other threads.</li>
     * <li>Writes the first {@link ByteBuffer} in the queue to the socket using
     * {@link SocketChannel#write(ByteBuffer)}.</li>
     * <li>If the buffer isn't fully written (i.e., has remaining bytes), the method
     * returns early so the selector
     * will retry later without removing the buffer.</li>
     * <li>If the write completes, the buffer is removed from the queue and the next
     * one is attempted.</li>
     * </ul>
     *
     * <h3>Cleanup:</h3>
     * <ul>
     * <li>When all buffers have been successfully written, the method clears
     * {@code OP_WRITE}
     * from the key's interest set to prevent unnecessary write readiness
     * checks.</li>
     * </ul>
     *
     * @param key the {@link SelectionKey} representing the writable socket channel
     * @throws IOException if writing to the channel fails
     */

    private static void handleWrite(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        SocketChannel socketChannel = ctx.socketChannel;
        debug(DEBUG_DETAILED, "Handling write operation for " + socketChannel.getRemoteAddress());

        synchronized (ctx.writeQueue) {
            while (!ctx.writeQueue.isEmpty()) {
                ByteBuffer buf = ctx.writeQueue.peek();
                int bytesWritten = socketChannel.write(buf);
                debug(DEBUG_LOW_LEVEL, "Wrote " + bytesWritten + " bytes");
                if (buf.hasRemaining())
                    return;
                ctx.writeQueue.poll();
            }
        }
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        debug(DEBUG_DETAILED, "Write queue empty, removing OP_WRITE");
    }

    /**
     * Closes a socket connection and performs cleanup based on its connection type.
     * <p>
     * This method is triggered when a connection becomes invalid, encounters an
     * error,
     * or reaches the end of the stream. It performs the following steps:
     * </p>
     *
     * <h3>1. User Cleanup (Client Connections):</h3>
     * <ul>
     * <li>If the connection has an associated username (i.e., a registered client),
     * it invokes
     * {@link ClientHandler#unregisterUsername(String)} to remove it from the
     * username registry.</li>
     * </ul>
     *
     * <h3>2. Peer Cleanup (Server Connections):</h3>
     * <ul>
     * <li>If the connection belongs to a peer server, retrieves the peer's context
     * using its PID.</li>
     * <li>Attempts a reconnection via {@code attemptRecconnectingToPeert()},
     * passing the stored context.</li>
     * <li>Removes the peer from the {@code connectedPeers} map.</li>
     * </ul>
     *
     * <h3>3. Socket Cleanup:</h3>
     * <ul>
     * <li>Cancels the {@link SelectionKey} so the selector no longer monitors the
     * channel.</li>
     * <li>If the socket channel is still open, it is forcefully closed to release
     * the underlying system resources.</li>
     * </ul>
     *
     * @param key the {@link SelectionKey} corresponding to the channel being closed
     * @throws IOException if closing the socket channel fails
     */

    private static void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        ConnectionContext ctx = (ConnectionContext) key.attachment();
        if (ctx.username != null) {
            ClientHandler.unregisterUsername(ctx.username);
        }

        if (ctx.type == ConnectionType.SERVER) {
            ConnectionContext lostCtx = connectedPeers.get(ctx.peerID);
            int peerId = ctx.peerID;

            // Try reconnection first
            attemptRecconnectingToPeer(lostCtx);
            connectedPeers.remove(peerId);

            // Then notify addressing server about the disconnection
            // Only do this if reconnection fails or after reconnection attempts
            if (handlerMap.get(ConnectionType.ADDRESSING_SERVER) != null) {
                ((AddressingServerHandler) handlerMap.get(ConnectionType.ADDRESSING_SERVER))
                        .notifyPeerCrash(peerId);
                debug(DEBUG_BASIC, "Notified addressing server about disconnected peer " + peerId);
            }
        } else if (ctx.type == ConnectionType.ADDRESSING_SERVER) {
            debug(DEBUG_BASIC, "Lost connection to Addressing Server. Attempting to reconnect...");
            // Start a reconnection attempt to the addressing server
            attemptReconnectingToAddressingServer();
        }

        try {
            SocketAddress remoteAddr = socketChannel.getRemoteAddress();
            debug(DEBUG_NORMAL, "Closing connection to " + remoteAddr);
        } catch (IOException e) {
            debug(DEBUG_NORMAL, "Channel was already closed or not available during cleanup.");
        }
        key.cancel();
        if (socketChannel.isOpen())
            socketChannel.close();
    }

    /**
     * Attempts to reconnect to a lost peer server using its previous connection
     * context.
     * <p>
     * This method is triggered when a connection to a peer server is lost due to
     * network failure,
     * shutdown, or other I/O issues. It tries to re-establish a connection using
     * the last known
     * host and port of the peer.
     * </p>
     *
     * <h3>Reconnection Strategy:</h3>
     * <ul>
     * <li>Performs up to 5 reconnection attempts ({@code MAX_RETRIES}), spaced 2
     * seconds apart.</li>
     * <li>For each attempt:
     * <ul>
     * <li>Opens a new {@link SocketChannel} in non-blocking mode</li>
     * <li>Connects to the peer's last known address</li>
     * <li>Creates a new {@link ConnectionContext} for the peer and registers it for
     * {@code OP_CONNECT}</li>
     * </ul>
     * </li>
     * <li>If all attempts fail, a message is logged and the server stops
     * retrying.</li>
     * </ul>
     *
     * <h3>Interruption Handling:</h3>
     * <ul>
     * <li>If the current thread is interrupted while sleeping between retries,
     * the loop terminates early and the interruption flag is restored.</li>
     * </ul>
     *
     * @param lostCtx the previous {@link ConnectionContext} of the peer that was
     *                disconnected
     * @return true if reconnection was successful, false otherwise
     */
    private static boolean attemptRecconnectingToPeer(ConnectionContext lostCtx) {
        if (lostCtx == null || lostCtx.host == null) {
            debug(DEBUG_NORMAL, "No known host/port for lost peer.");
            return false;
        }

        int peerID = lostCtx.peerID;
        debug(DEBUG_BASIC, "Attempting reconnection to lost peer ID: " + peerID);

        final int MAX_RETRIES = 5;
        final int RETRY_DELAY_MS = 2000;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                debug(DEBUG_NORMAL, "Reconnection attempt " + attempt + " to peer PID=" + peerID);

                SocketChannel peerChannel = SocketChannel.open();
                peerChannel.configureBlocking(false);
                peerChannel.connect(new InetSocketAddress(lostCtx.host, lostCtx.port));

                ConnectionContext newCtx = new ConnectionContext(peerChannel);
                newCtx.type = ConnectionType.SERVER;
                newCtx.peerID = peerID;
                newCtx.host = lostCtx.host;
                newCtx.port = lostCtx.port;

                peerChannel.register(selector, SelectionKey.OP_CONNECT, newCtx);
                debug(DEBUG_BASIC, "Reconnection initiated to peer PID=" + peerID);
                return true;
            } catch (IOException e) {
                debug(DEBUG_NORMAL, "Reconnection attempt " + attempt + " failed: " + e.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    debug(DEBUG_NORMAL, "Reconnection retry sleep interrupted");
                    break;
                }
            }
        }

        debug(DEBUG_BASIC, "All reconnection attempts failed for peer PID=" + peerID);
        return false;
    }

    /**
     * Attempts to reconnect to the Addressing Server after a connection failure.
     * <p>
     * This method is triggered when a connection to the Addressing Server is lost
     * due to
     * network failure, shutdown, or other I/O issues. It tries to re-establish
     * a connection to the Addressing Server and register this chat server again.
     * </p>
     *
     * <h3>Reconnection Strategy:</h3>
     * <ul>
     * <li>Performs up to 10 reconnection attempts, spaced 3 seconds apart.</li>
     * <li>For each attempt:
     * <ul>
     * <li>Opens a new {@link SocketChannel} in non-blocking mode</li>
     * <li>Connects to the Addressing Server's known address</li>
     * <li>Creates a new {@link ConnectionContext} and registers it for
     * {@code OP_CONNECT}</li>
     * <li>Sends a REGISTER message to re-establish this chat server's presence</li>
     * </ul>
     * </li>
     * <li>If all attempts fail, a message is logged and the server continues
     * operating
     * with existing peer connections, but new client connections will not be
     * possible.</li>
     * </ul>
     */
    private static void attemptReconnectingToAddressingServer() {
        debug(DEBUG_BASIC, "Attempting reconnection to Addressing Server...");

        // First, check if we already have any active addressing server connections
        if (hasExistingAddressingServerConnection()) {
            debug(DEBUG_BASIC, "An existing Addressing Server connection was found. Aborting reconnection attempt.");
            return;
        }

        final int MAX_RETRIES = 10;
        final int RETRY_DELAY_MS = 3000;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                debug(DEBUG_NORMAL, "Addressing Server reconnection attempt " + attempt);

                // Create a new connection to the addressing server
                SocketChannel channel = SocketChannel.open();
                channel.configureBlocking(false);
                channel.connect(new InetSocketAddress(ADDRESSING_SERVER_HOST, ADDRESSING_SERVER_PORT));

                ConnectionContext ctx = new ConnectionContext(channel);
                ctx.type = ConnectionType.ADDRESSING_SERVER;

                // Create a new registration message
                RegisterMessage<ChatServerRecord> registrationMsg = RegisterMessage.fromChatServer(
                        CLIENT_PORT,
                        PEER_LISTEN_PORT,
                        ADDRESSING_SERVER_PORT,
                        MAX_CLIENTS);

                String json = registrationMsg.toJson() + "\n";
                ctx.writeQueue.add(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));

                channel.register(selector, SelectionKey.OP_CONNECT, ctx);
                debug(DEBUG_BASIC, "Reconnection attempt to Addressing Server initiated");

                // Wake up the selector to process this connection immediately
                selector.wakeup();
                return;
            } catch (IOException e) {
                debug(DEBUG_NORMAL, "Reconnection attempt " + attempt + " failed: " + e.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    debug(DEBUG_NORMAL, "Reconnection retry sleep interrupted");
                    break;
                }
            }
        }

        debug(DEBUG_BASIC,
                "All reconnection attempts to Addressing Server failed. The Chat Server will continue to operate but may have limited functionality.");
    }

    /**
     * Checks if there are any existing connections to the Addressing Server.
     * <p>
     * This method prevents creating duplicate connections to the Addressing Server,
     * which could cause conflicting PIDs and state inconsistencies.
     * </p>
     * 
     * @return true if an existing addressing server connection is found, false
     *         otherwise
     */
    private static boolean hasExistingAddressingServerConnection() {
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid())
                continue;

            Object attachment = key.attachment();
            if (attachment instanceof ConnectionContext) {
                ConnectionContext ctx = (ConnectionContext) attachment;

                if (ctx.type == ConnectionType.ADDRESSING_SERVER) {
                    debug(DEBUG_DETAILED, "Found existing Addressing Server connection.");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds an available ephemeral port by binding a temporary {@link ServerSocket}
     * to port 0.
     *
     * @return an unused port number assigned by the operating system
     * @throws RuntimeException if no port could be bound
     */
    private static int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find an available port", e);
        }
    }

    /**
     * Checks whether a specific TCP port is already in use on the local machine.
     *
     * @param port the port number to test
     * @return {@code true} if the port is already bound, {@code false} if it is
     *         free
     */
    private static boolean isPortInUse(int port) {
        try (@SuppressWarnings("unused")
        ServerSocket serverSocket = new ServerSocket(port)) {
            // If we can bind to the port, it's available
            return false;
        } catch (IOException e) {
            // If an exception occurs, the port is in use
            return true;
        }
    }

    /**
     * Pretty-prints a JSON string to the standard output using indentation and
     * formatting.
     * If the input is invalid, prints an error message instead.
     *
     * @param jsonString the JSON string to print
     */
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

    /**
     * Gets the file path of the server's persistent chat log file.
     *
     * @return the path to the chat log file
     */
    public static String getChatLogFile() {
        return CHATLOG_FILE;
    }

    /**
     * Gets the PID assigned to this chat server by the Addressing Server.
     *
     * @return the server's unique process ID
     */

    public static int getID() {
        return ID;
    }

    /**
     * Returns the {@link ChatLog} object associated with this server.
     *
     * @return the current chat log instance
     */
    public static ChatLog getChatLog() {
        return chatLog;
    }

    /**
     * Returns the appropriate {@link ConnectionHandler} for a given connection
     * type.
     *
     * @param type the type of connection (client, server, or addressing server)
     * @return the corresponding handler
     */
    public static ConnectionHandler getHandler(ConnectionType type) {
        return handlerMap.get(type);
    }

    /**
     * Returns the map of connection types to their handlers.
     *
     * @return the handlerMap containing all registered connection handlers
     */
    public static Map<ConnectionType, ConnectionHandler> getHandlerMap() {
        return handlerMap;
    }

    /**
     * Sends a heartbeat {@code PING} message to a connected peer server to verify
     * that the connection is still alive.
     * <p>
     * This method is part of the heartbeat monitoring mechanism that detects
     * unresponsive peers and ensures
     * the consistency of the server network. It is typically called periodically by
     * the {@link HeartbeatMonitor}.
     * </p>
     *
     * <h3>Functionality:</h3>
     * <ul>
     * <li>First validates the provided {@link ConnectionContext} and
     * {@link SelectionKey} to ensure the socket is open
     * and the key is valid. If not, the PING is skipped and a debug message is
     * logged.</li>
     * <li>Constructs a {@link ServerServerMessage} of type {@code PING}, with the
     * current server's PID as the sender
     * and the target peer's PID as the recipient.</li>
     * <li>Serializes the message to a JSON string and appends it (along with a
     * newline) to the peer's {@code writeQueue}.</li>
     * <li>Calls {@code WriteUtils.enqueueResponse()} to safely handle queuing, and
     * then calls {@code key.selector().wakeup()}
     * to ensure the selector loop notices the write-ready state immediately.</li>
     * <li>Sets {@code ctx.awaitingPong = true} to indicate that this peer is now
     * expecting a {@code PONG} response,
     * which will later be handled in the heartbeat logic to determine
     * liveness.</li>
     * </ul>
     *
     * <h3>Failure Handling:</h3>
     * <ul>
     * <li>If any exception occurs during message creation or queuing, a debug
     * message is logged,
     * and the failure does not crash the heartbeat thread.</li>
     * </ul>
     *
     * @param ctx the {@link ConnectionContext} representing the peer server
     *            connection
     * @param key the {@link SelectionKey} associated with the peer's socket channel
     */

    public static void sendPing(ConnectionContext ctx, SelectionKey key) {
        try {

            if (ctx == null || key == null || !key.isValid() || !ctx.socketChannel.isOpen()) {
                debug(DEBUG_BASIC, "Not sending PING — socket is closed or key is invalid.");
                return;
            }

            ServerServerMessage ping = new ServerServerMessage(
                    String.valueOf(ID),
                    String.valueOf(ctx.peerID),
                    "PING",
                    "");

            String json = ping.toJson() + "\n";
            WriteUtils.enqueueResponse(ctx, key, json);
            key.selector().wakeup(); // optional but recommended

            ctx.awaitingPong = true;
            debug(DEBUG_NORMAL, "Sent PING to peer PID=" + ctx.peerID);

        } catch (Exception e) {
            debug(DEBUG_BASIC, "Failed to send PING: " + e.getMessage());
        }
    }

    /**
     * Gets the server's main {@link Selector} used for non-blocking I/O event
     * handling.
     *
     * @return the current selector
     */
    public static Selector getSelector() {
        return selector;
    }

    /**
     * Gets a map of currently connected peer servers, keyed by their PID.
     *
     * @return a concurrent map of peer IDs to {@link ConnectionContext}s
     */
    public static Map<Integer, ConnectionContext> getConnectedPeers() {
        return connectedPeers;
    }

    /**
     * Returns whether this chat server has completed registration with the
     * Addressing Server.
     *
     * @return {@code true} if registered, {@code false} otherwise
     */
    public static synchronized boolean isRegistered() {
        return registered;
    }

    /**
     * Sets the registered status of the server.
     *
     * @param value {@code true} if registration is complete, {@code false}
     *              otherwise
     */
    public static synchronized void setRegistered(boolean value) {
        registered = value;
    }

    /**
     * Sets the PID for this server.
     *
     * @param id the process ID assigned by the Addressing Server
     */
    public static void setID(int id) {
        ID = id;
    }

    /**
     * Sets the {@link ChatLog} instance used by the server.
     *
     * @param ChatLog the chat log to associate with this server
     */

    public static void setChatLog(ChatLog ChatLog) {
        chatLog = ChatLog;
    }

}

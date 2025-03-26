package io.github.cpsc559.team16.chatserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import java.util.concurrent.CountDownLatch;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ChatLog;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;
import io.github.cpsc559.team16.common.utilities.ServerServerMessage;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.MessageDeserializer;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;

@SuppressWarnings("unused")
public class ChatServer {

    // Debug level configuration
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

    private static String CHATLOG_FILE; // Unique chatlog filename
    private static String INDEX_FILE; // Unique index filename
    private static ChatLog chatLog;
    private static int ID;

    private static final String ADDRESSING_SERVER_HOST = System.getenv().getOrDefault("ADDRESS_SERVER_IP", "127.0.0.1");
    private static final int ADDRESSING_SERVER_PORT = Integer
            .parseInt(System.getenv().getOrDefault("CS_ADDRSERVER_PORT", "49802"));

    private static final int CHAT_SERVER_PORT = getAvailablePort();
    private static int CLIENT_PORT = getAvailablePort(); // Client connection Port
    private static final int CHAT_PORT = getAvailablePort();
    private static final int PEER_LISTEN_PORT = getAvailablePort(); // Port for connecting to peer servers
    private static final Map<Integer, ConnectionContext> connectedPeers = new ConcurrentHashMap<>();

    private static final int MAX_CLIENTS = 10; // max clients
    private static Selector selector;

    private static volatile boolean registered = false;
    private static final CountDownLatch registrationLatch = new CountDownLatch(1);

    enum ConnectionType {
        CLIENT, SERVER, ADDRESSING_SERVER
    }

    private static final Map<ConnectionType, ConnectionHandler> handlerMap = new HashMap<>();
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(8);

    private static record ServerBinding(ServerSocketChannel channel, ConnectionType type) {
    }

    public static void main(String[] args) throws IOException {
        debug(DEBUG_BASIC, "Starting NIO Server...");
        debug(DEBUG_DETAILED, "Initializing handlers and selector...");

        AddressingServerHandler addressingServerHandler = new AddressingServerHandler();

        handlerMap.put(ConnectionType.CLIENT, new ClientHandler());
        handlerMap.put(ConnectionType.SERVER, new ServerHandler());
        handlerMap.put(ConnectionType.ADDRESSING_SERVER, addressingServerHandler);

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

        debug(DEBUG_BASIC, String.format("Chat Server process started with PID: %d", ProcessUtils.getPid()));

        List<ServerBinding> serverBindings = List.of(
                new ServerBinding(setupListener(port), ConnectionType.CLIENT),
                // new ServerBinding(setupListener(CHAT_PORT), ConnectionType.SERVER),
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

    private static void handleConnect(SelectionKey key) throws IOException {
        ConnectionContext ctx = (ConnectionContext) key.attachment();
        SocketChannel socketChannel = ctx.socketChannel;

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

    private static void requestChatLogFor(ConnectionContext ctx, SelectionKey key) {
        try {
            ServerServerMessage request = new ServerServerMessage(
                    String.valueOf(ID),
                    "PeerServers",
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

    private static ServerSocketChannel setupListener(int port) throws IOException {
        debug(DEBUG_DETAILED, "Setting up listener on port " + port);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);
        debug(DEBUG_NORMAL, "Listener setup complete on port " + port);
        return server;
    }

    private static void connectToAddressingServer(Selector selector) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(ADDRESSING_SERVER_HOST, ADDRESSING_SERVER_PORT));

            ConnectionContext ctx = new ConnectionContext(channel);
            ctx.type = ConnectionType.ADDRESSING_SERVER;

            // Save registration payload in ctx (optional, in case needed later)
            ChatServerRecord record = new ChatServerRecord(
                    0L,
                    InetAddress.getLocalHost().getHostAddress(),
                    CLIENT_PORT,
                    PEER_LISTEN_PORT,
                    ADDRESSING_SERVER_PORT,
                    MAX_CLIENTS);

            BaseAddrServerMessage<ChatServerRecord> registrationMsg = new BaseAddrServerMessage<>(
                    "REGISTER", "ChatServerRecord", 0L, "CHATSERVER", "PRIMARY", record);

            String json = registrationMsg.toJson() + "\n";
            ctx.writeQueue.add(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));

            channel.register(selector, SelectionKey.OP_CONNECT, ctx);
            debug(DEBUG_BASIC, "Initiated non-blocking registration to Addressing Server");
        } catch (IOException e) {
            debug(DEBUG_BASIC, "Failed to connect to Addressing Server: " + e.getMessage());
        }
    }

    public static void processChatServerList(String jsonString, Selector selector) {
        debug(DEBUG_NORMAL, "Processing chat server list from Addressing Server...");
        printPrettyJson(jsonString);

        try {
            JSONObject response = new JSONObject(jsonString);
            JSONArray chatServers = response.getJSONArray("chatServers");

            debug(DEBUG_DETAILED, "Found " + chatServers.length() + " servers in the list");

            if (chatServers.length() <= 1) {
                debug(DEBUG_BASIC, "No other chat servers are currently registered.");
                return;
            }

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

            debug(DEBUG_NORMAL,
                    String.format("Registered peer PID=%d for non-blocking connection (OP_CONNECT)", peerID));
        } catch (IOException e) {
            debug(DEBUG_BASIC,
                    String.format("Failed to connect to peer server at %s:%d — %s", host, port, e.getMessage()));
            return;
        }

        debug(DEBUG_DETAILED, "Requesting chat log after peer connection setup...");
    }

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

    private static void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        ConnectionContext ctx = (ConnectionContext) key.attachment();
        if (ctx.username != null) {
            ClientHandler.unregisterUsername(ctx.username);
        }

        if (ctx.type == ConnectionType.SERVER) {
            ConnectionContext lostCtx = connectedPeers.get(ctx.peerID);
            attemptRecconnectingToPeert(lostCtx); // pass full ctx
            connectedPeers.remove(ctx.peerID);

        }

        debug(DEBUG_NORMAL, "Closing connection: " + socketChannel.getRemoteAddress());
        key.cancel();
        if (socketChannel.isOpen())
            socketChannel.close();
    }

    private static void attemptRecconnectingToPeert(ConnectionContext lostCtx) {
        if (lostCtx == null || lostCtx.host == null) {
            debug(DEBUG_NORMAL, "No known host/port for lost peer.");
            return;
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

        debug(DEBUG_BASIC, "All reconnection attempts failed for peer PID=" + peerID);
    }

    private static int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find an available port", e);
        }
    }

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

    public static String getChatLogFile() {
        return CHATLOG_FILE;
    }

    public static int getID() {
        return ID;
    }

    public static ChatLog getChatLog() {
        return chatLog;
    }

    public static ConnectionHandler getHandler(ConnectionType type) {
        return handlerMap.get(type);
    }

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

    public static Selector getSelector() {
        return selector;
    }

    public static Map<Integer, ConnectionContext> getConnectedPeers() {
        return connectedPeers;
    }

    public static synchronized boolean isRegistered() {
        return registered;
    }

    public static synchronized void setRegistered(boolean value) {
        registered = value;
    }

}

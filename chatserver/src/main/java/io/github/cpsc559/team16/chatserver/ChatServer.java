package io.github.cpsc559.team16.chatserver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.*;

import io.github.cpsc559.team16.addressingserver.ChatServerInfo;
import io.github.cpsc559.team16.client.Client;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;
import io.github.cpsc559.team16.common.utilities.ChatLog;
import io.github.cpsc559.team16.common.utilities.ChatLogUpdate;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;

public class ChatServer {
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final BlockingQueue<BaseMessage> messageQueue = new LinkedBlockingQueue<>();
    private static Map<String, ServerInfo> peerServers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> receivedMessages = new ConcurrentHashMap<>();

    private static String CHATLOG_FILE; // Unique chatlog filename
    private static String INDEX_FILE; // Unique index filename
    private static ChatLog chatLog;
    private static int ID;

    private static final String ADDRESSING_SERVER_HOST = "127.0.0.1";
    private static final int ADDRESSING_SERVER_PORT = 49802; //

    private static final int CHAT_SERVER_PORT = getAvailablePort();
    private static final int ADDR_SERVER_PORT = getAvailablePort(); // Client connection Port
    private static final int CHAT_PEER_PORT = getAvailablePort(); // Port for connecting to peer servers
    private static final int MAX_CLIENTS = 10; // max clients
    // private static final int CHAT_SERVER_PORT = 2424;

    public static void main(String[] args) {
        // Print all environment variables for debugging
        // System.getenv().forEach((key, value) -> System.out.println(key + ": " +
        // value));

        // Read the port from the environment variable, default to 2424 if not set
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "2424"));
        if (isPortInUse(port)) {
            // Assign to a different port (ADDR_SERVER_PORT)
            port = ADDR_SERVER_PORT;
            System.out.println("Port 2424 is in use. Switching to " + port);
        } else {
            System.out.println("Port 2424 is available.");
        }
        System.out.printf("Chat Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        boolean registered = registerWithAddressingServer();
        if (!registered) {
            System.err.println("Failed to register with Addressing Server. Exiting...");
            return;
        }

        System.out.println("Successfully registered with Addressing Server.");

        // **Start listening for incoming peer connections**
        new Thread(ChatServer::listenForPeers).start();

        // Start the message broadcasting thread
        // This is in charge of handling outgoing recieved messages.
        // We spray all messages out to all our clients
        new Thread(ChatServer::broadcastMessages).start();

        // I think we should consider creating a threadpool for this instead of this
        // implementation.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ChatServer is listening on port " + port);

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("New client connected");

                    // Create a new thread to handle the client connection
                    ClientHandler clientHandler = new ClientHandler(socket, messageQueue, clients);
                    Thread thread = new Thread(clientHandler);
                    thread.start();
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    /**
     * Broadcasts messages from the queue to all connected clients and peer servers.
     * <p>
     * This method runs continuously, retrieving messages from the queue and
     * distributing
     * them to all connected clients. If the message originates from a client, it is
     * also
     * appended to the chat log. Additionally, the message is forwarded to all peer
     * servers.
     * </p>
     * <p>
     * If the thread is interrupted, an error message is logged, and the method
     * exits.
     * </p>
     */
    private static void broadcastMessages() {
        try {
            while (true) {
                // Take a message from the queue and broadcast it to all clients
                BaseMessage message = messageQueue.take();

                if (message instanceof ClientServerMessage) {
                    chatLog.appendMessage((ClientServerMessage) message);
                }

                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
                System.out.println("Sending message to peers");

                forwardMessageToPeers(message);

            }
        } catch (InterruptedException e) {
            System.err.println("Broadcasting thread interrupted: " + e.getMessage());
        }
    }

    /**
     * Forwards a message to all connected peer servers.
     * <p>
     * This method iterates through the list of peer servers and sends the provided
     * message to each peer by calling {@code announceIncomingMessages}. If no peer
     * servers are available, it logs an error and exits without sending.
     * </p>
     *
     * @param message The {@link BaseMessage} to be forwarded to peer servers.
     */
    private static void forwardMessageToPeers(BaseMessage message) {
        // System.out.println("IN FORWARDING");
        if (peerServers.isEmpty()) {
            System.err.println("No peer servers available. Message not sent.");
            return;
        }
        // System.out.println("Total peers available: " + peerServers.size());

        for (Map.Entry<String, ServerInfo> peerEntry : peerServers.entrySet()) {
            String peerKey = peerEntry.getKey();
            String peerAddress = peerEntry.getValue().getAddress();
            int peerPort = peerEntry.getValue().getPort();
            int peerID = Integer.parseInt(peerKey);

            System.out.println("Sending message to peer " + peerID + " at " + peerAddress + ":" + peerPort);

            // Announce and send the message
            announceIncomingMessages(peerAddress, peerPort, peerID, message);
        }
    }

    /**
     * Registers the chat server with the Addressing Server.
     * <p>
     * This method establishes a connection to the Addressing Server, sends a
     * JSON-formatted
     * registration request containing the chat server's details, and waits for a
     * response.
     * If registration is successful, it retrieves the list of all registered chat
     * servers
     * and processes the received information.
     * </p>
     *
     * @return {@code true} if the chat server was successfully registered,
     *         {@code false} otherwise.
     */
    private static boolean registerWithAddressingServer() {
        try (Socket socket = new Socket(ADDRESSING_SERVER_HOST, ADDRESSING_SERVER_PORT);
                OutputStream outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            // Construct JSON object for registration
            JSONObject registrationData = new JSONObject();
            registrationData.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
            registrationData.put("addrServerPort", ADDR_SERVER_PORT);
            registrationData.put("chatServerPort", CHAT_SERVER_PORT);
            registrationData.put("chatPeerPort", CHAT_PEER_PORT);
            registrationData.put("maxClients", MAX_CLIENTS);

            // Send registration request
            String jsonString = registrationData.toString();
            System.out.println("Sending registration JSON: " + jsonString);
            outputStream.write((jsonString + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            // Ensure all data is sent before closing output
            // I was having a weird error where the connections were closing while data was
            // sending so sleep
            Thread.sleep(200);
            socket.shutdownOutput();

            // Read server response (PID)
            String response = reader.readLine();
            System.out.println("Received response: " + response);

            if (response == null || !response.matches("\\d+")) {
                System.err.println("Invalid response from Addressing Server: " + response);
                return false;
            }

            ID = Integer.parseInt(response);
            CHATLOG_FILE = "src/main/java/com/example/Logs/chatlog_" + ID + ".log";
            INDEX_FILE = "src/main/java/com/example/Logs/index_" + ID + ".json";
            chatLog = new ChatLog(CHATLOG_FILE, INDEX_FILE);

            // System.out.println("Chat server registered with PID: " + response);

            // **Read the list of registered chat servers**
            StringBuilder serverListJson = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                serverListJson.append(line);
            }

            // make sure that we got a proper message
            if (serverListJson.length() > 0) {
                processChatServerList(serverListJson.toString());
            } else {
                System.out.println("No additional chat server information received.");
            }

            return true;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Error registering with Addressing Server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Processes and prints the list of all registered chat servers received from
     * the Addressing Server.
     * <p>
     * This method parses a JSON string containing an array of registered chat
     * servers,
     * extracts relevant details, and displays them in the console.
     * </p>
     *
     * @param jsonString The JSON string containing chat server information.
     */
    private static void processChatServerList(String jsonString) {
        try {
            JSONObject response = new JSONObject(jsonString);
            JSONArray chatServers = response.getJSONArray("chatServers");

            if (chatServers.length() <= 1) {
                System.out.println("No other chat servers are currently registered.");
                return;
            }

            // System.out.println("Received list of registered chat servers:");
            // int lowestPeerID = Integer.MAX_VALUE;
            // String lowestPeerAddress = null;
            // int lowestPeerPort = -1;

            for (int i = 0; i < chatServers.length(); i++) {
                JSONObject server = chatServers.getJSONObject(i);

                int peerID = server.getInt("pid");
                String peerAddress = server.getString("hostAddress");
                int peerPort = server.getInt("peerPort");

                if (peerID == ID) {
                    System.out.println("Skipping server ID of myself " + ID);
                    continue;
                }

                System.out.printf(" - Server PID: %d | Address: %s | Peer Port: %d | Status: %s%n",
                        peerID, peerAddress, peerPort, server.getString("status"));

                peerServers.put(String.valueOf(peerID), new ServerInfo(peerAddress, peerPort));
                System.out.println("Added peer -> ID: " + peerID + ", Address: " + peerAddress + ", Port: " + peerPort);

                // if (peerID < lowestPeerID) {
                // lowestPeerID = peerID;
                // // lowestPeerAddress = peerAddress;
                // // lowestPeerPort = peerPort;
                // }

                new Thread(() -> connectToPeerServer(peerAddress, peerPort, peerID)).start();
            }
            // System.out.println("Final peer list: " + peerServers.keySet());

            // if (lowestPeerAddress != null && lowestPeerPort != -1) {
            // requestChatLogFromPeer(lowestPeerAddress, lowestPeerPort, lowestPeerID);
            // }

        } catch (Exception e) {
            // System.err.println("Error processing chat server list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Requests the chatlog from the lowest-indexed peer server.
     *
     * @param peerAddress The IP address of the peer server.
     * @param peerPort    The port number of the peer server.
     * @param peerID      The unique identifier of the peer server.
     */
    private static void requestChatLogFromPeer(String peerAddress, int peerPort, int peerID) {
        try (Socket peerSocket = new Socket(peerAddress, peerPort);
                OutputStream outputStream = peerSocket.getOutputStream();
                InputStream inputStream = peerSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            // System.out.println("[INFO] Requesting chatlog from peer server: " + peerID);

            // Send request for chatlog
            String requestMessage = "REQUEST_CHATLOG\n";
            outputStream.write(requestMessage.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            // Read the chatlog response
            StringBuilder receivedChatLog = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END_OF_CHATLOG"))
                    break; // Stop reading when end marker is received
                receivedChatLog.append(line).append("\n");

            }

            System.out.println("Chatlog received from peer " + peerID + ". Merging...");
            mergeChatLog(receivedChatLog.toString());

        } catch (IOException e) {
            System.err.println("Error requesting chatlog from peer " + peerID + ": " + e.getMessage());
        }
    }

    /**
     * Establishes a connection to a peer chat server.
     * <p>
     * This method creates a socket connection to a peer chat server using its host
     * address and port.
     * It then sends a handshake message to the peer server and reads the response.
     * </p>
     *
     * @param peerAddress The IP address of the peer server.
     * @param peerPort    The port number of the peer server.
     * @param peerID      The unique identifier of the peer server.
     */
    private static void connectToPeerServer(String peerAddress, int peerPort, int peerID) {
        try (Socket peerSocket = new Socket(peerAddress, peerPort);
                PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()))) {

            System.out.println("[INFO] Connecting to peer server: " + peerID + " at " + peerAddress + ":" + peerPort);

            // Send enhanced handshake with port information
            String handshakeMessage = "HELLO FROM SERVER " + ID + " " + CHAT_PEER_PORT;
            System.out.println("[SEND] " + handshakeMessage);
            writer.println(handshakeMessage);
            writer.flush();

            // Read handshake response
            String response = reader.readLine();
            System.out.println("[RECEIVE] Handshake response: " + response);

            if (response != null && response.startsWith("HELLO FROM SERVER")) {
                String[] parts = response.split(" ");
                int receivedPeerID = Integer.parseInt(parts[3]);
                int receivedPeerPort = Integer.parseInt(parts[4]);

                System.out.println("[INFO] Handshake successful with peer " + receivedPeerID);
                peerServers.put(String.valueOf(receivedPeerID),
                        new ServerInfo(peerAddress, receivedPeerPort));

                // Immediately request chatlog after successful handshake
                requestChatLogFromPeer(peerAddress, receivedPeerPort, receivedPeerID);
            } else {
                System.err.println("[ERROR] Invalid handshake response: " + response);
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Connection failed to peer " + peerID + ": " + e.getMessage());
        }
    }

    /**
     * Listens for incoming peer server connections.
     * <p>
     * This method creates a server socket that continuously listens for incoming
     * connections
     * from other peer servers. When a new connection is established, it starts a
     * new thread
     * to handle the connection.
     * </p>
     */
    private static void listenForPeers() {
        try (ServerSocket peerServerSocket = new ServerSocket(CHAT_PEER_PORT)) {
            System.out.println("Listening for peer connections on port " + CHAT_PEER_PORT);

            while (true) {
                try {
                    // Accept an incoming peer connection
                    Socket peerSocket = peerServerSocket.accept();
                    // Start a new thread to handle the peer connection
                    System.out.println("New peer connected: " + peerSocket.getInetAddress());

                    new Thread(() -> {
                        handlePeerConnection(peerSocket);

                    }).start();
                } catch (IOException e) {
                    System.err.println("Error accepting peer connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start peer listener on port " + CHAT_PEER_PORT + ": " + e.getMessage());
        }
    }

    /**
     * Handles an incoming peer server connection.
     * <p>
     * When a peer server connects, this method reads the initial message from the
     * peer,
     * prints the received message to the console, and sends an acknowledgment
     * response.
     * If a chatlog request is received, it sends the chatlog back.
     * </p>
     *
     * @param peerSocket The socket representing the connection to the peer server.
     * @throws InterruptedException
     */
    private static void handlePeerConnection(Socket peerSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(), true);

            String peerAddress = peerSocket.getInetAddress().getHostAddress();
            System.out.println("[INFO] Handling peer connection from " + peerAddress);

            // **Process messages as they come in**
            String messageLine;
            while ((messageLine = reader.readLine()) != null) {
                System.out.println("[RECEIVE] " + messageLine);

                if (messageLine.startsWith("HELLO FROM SERVER")) {
                    // **Handle handshake**
                    String[] parts = messageLine.split(" ");
                    int peerID = Integer.parseInt(parts[3]);
                    int peerPort = Integer.parseInt(parts[4]);

                    // **Send handshake response**
                    String response = "HELLO FROM SERVER " + ID + " " + CHAT_PEER_PORT;
                    System.out.println("[SEND] " + response);
                    writer.println(response);
                    writer.flush();

                    // **Register the peer**
                    peerServers.put(String.valueOf(peerID), new ServerInfo(peerAddress, peerPort));

                } else if (messageLine.equals("REQUEST_CHATLOG")) {
                    // **Handle chatlog request immediately**
                    System.out.println("[INFO] Sending chatlog to peer: " + peerAddress);
                    sendChatLog(writer);

                } else if (messageLine.equals("ANNOUNCEMENT:INCOMING_MESSAGES")) {
                    // **Handle incoming messages**
                    handleIncomingMessageAnnouncement(reader, writer);
                }
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Peer connection failed: " + e.getMessage());
        }
    }

    /**
     * Handles the announcement of incoming messages from a peer server.
     * <p>
     * This method acknowledges the announcement, reads the incoming JSON-formatted
     * message,
     * checks for duplicates, and if the message is new, it adds it to the message
     * queue
     * for further processing.
     * </p>
     *
     * @param reader The {@link BufferedReader} used to read the incoming message
     *               from the peer.
     * @param writer The {@link PrintWriter} used to send acknowledgments to the
     *               peer.
     * @throws IOException If an I/O error occurs while reading from the peer.
     */
    private static void handleIncomingMessageAnnouncement(BufferedReader reader, PrintWriter writer)
            throws IOException {
        writer.println("ACK:READY_FOR_MESSAGES");

        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
            if (line.trim().endsWith("}")) { // Detect end of JSON object
                break;
            }
        }
        String messageJson = jsonBuilder.toString();
        System.out.println("[DEBUG] Fully received JSON message: " + messageJson);

        try {
            ClientServerMessage message = BaseMessage.fromJson(messageJson, ClientServerMessage.class);

            // **Check if message was already received**
            if (receivedMessages.containsKey(message.getMessageId())) {
                // System.out.println("[INFO] Duplicate message detected. Skipping.");
                return; // Do not process or forward it again
            }

            // Store message ID to prevent re-sending
            receivedMessages.put(message.getMessageId(), true);

            messageQueue.put(message);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to process peer message: " + e.getMessage());
        }
    }

    /**
     * Announces to a peer server that messages will be sent soon.
     *
     * @param peerAddress The IP address of the peer server.
     * @param peerPort    The port number of the peer server.
     * @param peerID      The unique identifier of the peer server.
     * @return `true` if the peer acknowledges, `false` otherwise.
     */
    private static boolean announceIncomingMessages(String peerAddress, int peerPort, int peerID, BaseMessage message) {
        try (Socket peerSocket = new Socket(peerAddress, peerPort);
                PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()))) {

            // Send enhanced handshake
            writer.println("HELLO FROM SERVER " + ID + " " + CHAT_PEER_PORT);
            writer.flush();

            // Verify handshake response
            String response = reader.readLine();
            if (response == null || !response.startsWith("HELLO FROM SERVER")) {
                return false;
            }

            // Send announcement
            writer.println("ANNOUNCEMENT:INCOMING_MESSAGES");
            writer.flush(); // Ensure header is sent first

            String jsonMessage = message.toJson();
            writer.println(jsonMessage);
            writer.flush();

            return reader.readLine().equals("ACK:READY_FOR_MESSAGES");

        } catch (IOException e) {
            System.err.println("Peer communication error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends the local chat log to a requesting peer server.
     *
     * @param writer The PrintWriter connected to the requesting peer.
     */
    private static void sendChatLog(PrintWriter writer) {
        try (BufferedReader logReader = new BufferedReader(new FileReader(CHATLOG_FILE))) {
            String line;
            while ((line = logReader.readLine()) != null) {
                // **Debugging: Print each line being sent**
                // System.out.println("[DEBUG] Sending chatlog line: " + line);
                writer.println(line);
                writer.flush();
            }
            writer.println("END_OF_CHATLOG");
            writer.flush();
            System.out.println("[INFO] Chat log successfully sent to peer.");
        } catch (IOException e) {
            System.err.println("Error sending chat log: " + e.getMessage());
        }
    }

    /**
     * Merges the received chatlog with the local chatlog.
     * Ensures that no duplicate messages are added.
     *
     * @param receivedLog The chatlog received from a peer server.
     */
    private static void mergeChatLog(String receivedLog) {
        try (BufferedReader reader = new BufferedReader(new StringReader(receivedLog))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {

                    // **Debugging: Print each received message before parsing**
                    // System.out.println("[DEBUG] Received chatlog line: " + line);

                    // Deserialize using BaseMessage's fromJson function
                    BaseMessage baseMessage = BaseMessage.fromJson(line, ClientServerMessage.class);

                    if (baseMessage instanceof ClientServerMessage) {

                        ClientServerMessage message = (ClientServerMessage) baseMessage;

                        // **Check if we already have this message before adding**
                        if (receivedMessages.containsKey(message.getMessageId())) {
                            System.out.println(
                                    "[INFO] Duplicate message in chatlog. Skipping: " + message.getMessageId());
                            continue; // Skip duplicates
                        }

                        // Store message ID to prevent future duplicates
                        receivedMessages.put(message.getMessageId(), true);

                        chatLog.appendMessage(message); // Append only if not already present
                        // System.out.println("[INFO] Chatlog merged: " + message.getMessageId());

                    }
                } catch (Exception e) {
                    System.err.println("Skipping invalid chatlog entry: " + e.getMessage());
                }
            }
            System.out.println("Chatlog successfully merged.");
        } catch (IOException e) {
            System.err.println("Error merging chatlog: " + e.getMessage());
        }
    }

    // we may not need this but i used it for testing since I can only use each port
    // on my device once i can now get new ports

    /**
     * Finds and returns an available port on the system.
     * <p>
     * This method creates a temporary {@link ServerSocket} with a port number of 0,
     * which allows the operating system to allocate an available port. The
     * allocated
     * port is then retrieved and returned.
     * </p>
     *
     * @return an integer representing a free port available on the system.
     * @throws RuntimeException if no available port is found.
     */
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

}

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

    private static void broadcastMessages() {
        try {
            while (true) {
                // Take a message from the queue and broadcast it to all clients
                BaseMessage message = messageQueue.take();

                if (message instanceof ClientServerMessage) {
                    chatLog.appendMessage((ClientServerMessage) message);
                }

                if (message instanceof ClientServerMessage) {
                    chatLog.appendMessage((ClientServerMessage) message);
                }
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }

                forwardMessageToPeers(message);

            }
        } catch (InterruptedException e) {
            System.err.println("Broadcasting thread interrupted: " + e.getMessage());
        }
    }

    private static void forwardMessageToPeers(BaseMessage message) {
        for (String peerKey : peerServers.keySet()) {
            String peerAddress = peerServers.get(peerKey).getAddress();
            int peerPort = peerServers.get(peerKey).getPort();

            try (Socket peerSocket = new Socket(peerAddress, peerPort);
                    PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(), true)) {

                writer.println(message.toJson());
                writer.flush();
            } catch (IOException e) {
                System.err.println("Failed to forward message to peer " + peerKey + ": " + e.getMessage());
            }
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
            CHATLOG_FILE = "chatlog_" + ID + ".log";
            INDEX_FILE = "index_" + ID + ".json";
            chatLog = new ChatLog(CHATLOG_FILE, INDEX_FILE);

            System.out.println("Chat server registered with PID: " + response);

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

            System.out.println("Received list of registered chat servers:");
            int lowestPeerID = Integer.MAX_VALUE;
            String lowestPeerAddress = null;
            int lowestPeerPort = -1;

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

                if (peerID < lowestPeerID) {
                    lowestPeerID = peerID;
                    lowestPeerAddress = peerAddress;
                    lowestPeerPort = peerPort;
                }

                new Thread(() -> connectToPeerServer(peerAddress, peerPort, peerID)).start();
            }
            if (lowestPeerAddress != null && lowestPeerPort != -1) {
                requestChatLogFromPeer(lowestPeerAddress, lowestPeerPort, lowestPeerID);
            }

        } catch (Exception e) {
            System.err.println("Error processing chat server list: " + e.getMessage());
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

            System.out.println("Requesting chatlog from peer server: " + peerID);

            // Send request for chatlog
            String requestMessage = "REQUEST_CHATLOG\n";
            outputStream.write(requestMessage.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            // Read the chatlog response
            StringBuilder receivedChatLog = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
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
                OutputStream outputStream = peerSocket.getOutputStream();
                InputStream inputStream = peerSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            System.out.println("Connected to peer server: " + peerID + " at " + peerAddress + ":" + peerPort);

            // Send a handshake message to the peer server
            String handshakeMessage = "HELLO FROM SERVER " + ID + "\n";
            outputStream.write(handshakeMessage.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            // Read response from the peer server
            String response = reader.readLine();
            System.out.println("Response from peer " + peerID + ": " + response);

        } catch (IOException e) {
            System.err.println("Error connecting to peer server " + peerID + ": " + e.getMessage());
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
                    new Thread(() -> {
                        try {
                            handlePeerConnection(peerSocket);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
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
    private static void handlePeerConnection(Socket peerSocket) throws InterruptedException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(peerSocket.getOutputStream(), true)) {

            String messageJson;
            while ((messageJson = reader.readLine()) != null) {
                try {
                    if (messageJson.trim().equalsIgnoreCase("REQUEST_CHATLOG")) {
                        // Peer requested the full chat log
                        System.out.println("Sending chat log to requesting peer...");
                        sendChatLog(writer);
                    } else if (messageJson.startsWith("{")) { // JSON structure check
                        if (messageJson.contains("\"chatLog\"")) {
                            // Chat Log Sync Message
                            ChatLogUpdate logUpdate = BaseMessage.fromJson(messageJson, ChatLogUpdate.class);
                            chatLog.merge(logUpdate);
                            System.out.println("Received and merged chat log from peer.");
                        } else {
                            // Live Client Message from Peer
                            ClientServerMessage message = BaseMessage.fromJson(messageJson, ClientServerMessage.class);
                            messageQueue.put(message);
                        }
                    }
                } catch (JsonProcessingException e) {
                    System.err.println("Error parsing message from peer: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling peer connection: " + e.getMessage());
        }
    }

    /**
     * Sends the local chat log to a requesting peer server.
     *
     * @param writer The PrintWriter connected to the requesting peer.
     */
    private static void sendChatLog(PrintWriter writer) {
        try (BufferedReader logReader = new BufferedReader(new FileReader("chatlog.log"))) {
            String line;
            while ((line = logReader.readLine()) != null) {
                writer.println(line);
            }
            writer.flush();
            System.out.println("Chat log successfully sent to peer.");
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
                    // Deserialize using BaseMessage's fromJson function
                    BaseMessage baseMessage = BaseMessage.fromJson(line, ClientServerMessage.class);

                    if (baseMessage instanceof ClientServerMessage) {
                        ClientServerMessage message = (ClientServerMessage) baseMessage;
                        chatLog.appendMessage(message); // Append only if not already present
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
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // If we can bind to the port, it's available
            return false;
        } catch (IOException e) {
            // If an exception occurs, the port is in use
            return true;
        }
    }

}

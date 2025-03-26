package io.github.cpsc559.team16.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.AckTypes;
import io.github.cpsc559.team16.common.messaging.RegisterMessage;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;

/**
 * A client implementation for an IRC-style chat application.
 * This client supports:
 * - Connection to a chat server through an addressing server
 * - Real-time message sending and receiving with acknowledgment tracking
 * - Automatic reconnection on connection loss with exponential backoff
 * - Thread-safe message handling with separate threads for input, output,
 * sending, and receiving
 * - Interactive command-line interface with JLine terminal support
 * - Dynamic terminal resizing and display management
 * - Debug logging with configurable levels
 */

@SuppressWarnings("unused")
public class Client {

    /**
     * Debug level configuration from environment variable.
     * Defaults to 1 (BASIC) if not specified.
     * Levels:
     * 0 - No debug output (production mode)
     * 1 - Basic info: startup, shutdown, major events
     * 2 - Normal operation details: connections, requests
     * 3 - Detailed flow: entering methods, decision points
     * 4 - Low-level operations: byte-level I/O, parsing
     * 5 - Extreme detail: everything, for deep debugging
     */
    private static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "0"));

    // Debug level constants
    private static final int DEBUG_NONE = 0; // No debug output (production mode)
    private static final int DEBUG_BASIC = 1; // Basic info: startup, shutdown, major events
    private static final int DEBUG_NORMAL = 2; // Normal operation details: connections, requests
    private static final int DEBUG_DETAILED = 3; // Detailed flow: entering methods, decision points
    private static final int DEBUG_LOW_LEVEL = 4; // Low-level operations: byte-level I/O, parsing
    private static final int DEBUG_EXTREME = 5; // Extreme detail: everything, for deep debugging

    /**
     * Logs a debug message if the current debug level is sufficient.
     * 
     * @param level   The debug level of the message (0-5)
     * @param message The message to log
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

    // Client state
    private String username; // User's display name in the chat
    private int sendCounter = 0; // Unique message ID counter

    // Server connection information
    private String address; // Addressing server hostname
    private int addressPort; // Addressing server port

    // Connection management
    private int reconnectTries = 0; // Current reconnection attempt count
    private static final int MAX_RECONNECT_TRIES = 5; // Maximum reconnection attempts before giving up

    // Chat server connection
    private Socket chatServer; // Socket connection to chat server
    private BufferedReader in; // Input stream reader
    private PrintWriter out; // Output stream writer

    // Message management
    private final List<ClientServerMessage> msgLog = Collections.synchronizedList(new LinkedList<>()); // Complete chat
                                                                                                       // history

    private final List<ClientServerMessage> displayLog = Collections.synchronizedList(new LinkedList<>()); // Messages
                                                                                                           // to display

    private final Queue<ClientServerMessage> messageQueue = new ConcurrentLinkedQueue<>(); // Messages pending send



    private final Queue<ClientServerMessage> awaitingAck = new ConcurrentLinkedQueue<>(); // Messages awaiting
                                                                                          // acknowledgment

    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>()); // Track processed
                                                                                                  // message IDs

    private final Set<String> pendingMessages = Collections.synchronizedSet(new HashSet<>()); // Track messages pending
                                                                                              // acknowledgment

    private final Object messageLock = new Object(); // Lock for message operations

    // Thread management
    private InputThread inputThread; // Handles user input
    private OutputThread outputThread; // Handles UI updates
    private SenderThread senderThread; // Handles message sending
    private ReceiverThread receiverThread; // Handles message receiving
    private final CountDownLatch shutdownLatch = new CountDownLatch(4); // One for each thread

    // System utilities
    private boolean terminate = false; // Shutdown flag
    private boolean isConnected = false; // Connection state flag

    // UI components
    private Terminal terminal; // Terminal interface
    private LineReader lineReader; // Command line reader

    /**
     * Creates a new chat client.
     * 
     * @param username   The display name for this client
     * @param serverName The hostname of the addressing server
     * @param serverPort The port number of the addressing server
     * @param terminal   The terminal instance to use
     * @param lineReader The line reader instance to use
     */
    public Client(String username, String serverName, int serverPort, Terminal terminal, LineReader lineReader) {
        debug(DEBUG_BASIC, "Initializing client for user: " + username);
        this.username = username;
        this.address = serverName;
        this.addressPort = serverPort;
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    /**
     * Starts the client application.
     * This method:
     * 1. Sets up the terminal interface
     * 2. Establishes server connection
     * 3. Registers with the server
     * 4. Starts all necessary threads
     * 5. Maintains the main application loop
     */
    public void run() {
        debug(DEBUG_BASIC, "Starting client...");
        terminate = false;

        try {
            connect();
            // Initialize and start worker threads
            debug(DEBUG_DETAILED, "Creating client threads");
            inputThread = new InputThread(lineReader);
            outputThread = new OutputThread(lineReader);
            senderThread = new SenderThread();
            receiverThread = new ReceiverThread();

            inputThread.start();
            outputThread.start();
            senderThread.start();
            receiverThread.start();

            debug(DEBUG_BASIC, "Client threads started successfully");

            // Main application loop
            while (!terminate) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            debug(DEBUG_BASIC, "Error in client run: " + e.getMessage());
            shutdown();
        } finally {
            try {
                if (chatServer != null) {
                    chatServer.close();
                    debug(DEBUG_NORMAL, "Chat server connection closed");
                }
            } catch (IOException e) {
                debug(DEBUG_NORMAL, "Error closing chat server: " + e.getMessage());
            }
        }
    }

    private String[] registerWithAddressingServer() throws IOException {
        // Open a socket to the addressing server
        try (Socket addressSocket = new Socket(address, addressPort)) {
            // Create the streams
            PrintWriter out = new PrintWriter(addressSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(addressSocket.getInputStream()));

            // Create a REGISTER message for the client. Currently it doesn't send any additional information,
            // but I'm guessing this is where we would do the token stuff??? - Aidan
            RegisterMessage<String> regMsg = RegisterMessage.fromClient();
            // Send the registration JSON message
            out.println(regMsg.toJson());

            // Wait for the addressing server's response ()
            String ackJson = in.readLine();
            if (ackJson == null) {
                throw new IOException("No response from Addressing Server.");
            }

            // Deserialize the JSON into an AckMessage
            ObjectMapper mapper = new ObjectMapper();
            AckMessage ackMessage = mapper.readValue(ackJson, AckMessage.class);

            // Extract the payload. It should be a String in the format "pid:hostAddress:clientPort"

            if (ackMessage.getObjectType().equals(AckTypes.HOSTADDRESS)) {
                String payload = (String) ackMessage.getPayload();
                if (payload == null || !payload.contains(":")) {
                    throw new IOException("Invalid payload from Addressing Server: " + payload);
                }
                // Split the payload into 3 parts: pid, host, and port
                String[] substrings = payload.split(":");
                if (substrings.length != 3) {
                    throw new IOException("Expected 3 parts in the payload, but got " + substrings.length);
                }
                return substrings;
            }
            else {
                System.out.println("ACK message indicated there were no chat servers available.");
                return null;
            }
        }
    }


    /**
     * Establishes connection to the chat server through the addressing server.
     * The process:
     * 1. Connects to the addressing server
     * 2. Retrieves chat server information
     * 3. Establishes connection to the chat server
     * 
     * @throws IOException if connection fails
     */
    @SuppressWarnings("resource")
    private void connect() throws IOException {
        if (!terminate) {
            try {
                // Establish server connection
                String[] substrings = null;
                while (substrings == null) {
                    substrings = registerWithAddressingServer();
                    wait(1000); // Artificiallly high - dono what to set it as.
                }
                Long serverPid = Long.parseLong(substrings[0]);
                String chatServerAddress = substrings[1];
                int chatServerPort = Integer.parseInt(substrings[2]);

                // Step 2: Connect to chat server
                debug(DEBUG_NORMAL, "Connecting to chat server at " + chatServerAddress + ":" + chatServerPort);
                chatServer = new Socket(chatServerAddress, chatServerPort);
                out = new PrintWriter(chatServer.getOutputStream(), true); // autoFlush = true
                in = new BufferedReader(new InputStreamReader(chatServer.getInputStream()));
                isConnected = true;
                debug(DEBUG_BASIC, "Successfully connected to chat server");
                System.out.println("CONNECTED");
            } catch (IOException e) {
                debug(DEBUG_BASIC, "Connection error: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                System.err.println("Error parsing chat server address: " + e.getMessage());
            }
        }
    }

    /**
     * Attempts to reconnect to the server network.
     * Implements exponential backoff with a maximum number of attempts.
     * Features:
     * - Exponential backoff between attempts (starting at 1s, doubling each time)
     * - Maximum retry limit (5 attempts)
     * - Connection state tracking
     * - Automatic retry on failure
     * - Graceful shutdown if max attempts reached
     * - Fallback to addressing server for new server assignment
     */
    private void reconnect() {
        debug(DEBUG_BASIC, "Starting reconnection process");
        isConnected = false;

        while (reconnectTries < MAX_RECONNECT_TRIES) {
            try {
                debug(DEBUG_NORMAL, "Reconnection attempt " + (reconnectTries + 1) + " of " + MAX_RECONNECT_TRIES);
                connect();
                reconnectTries = 0; // Reset counter on successful connection
                debug(DEBUG_BASIC, "Reconnection successful");
                return;
            } catch (Exception e) {
                reconnectTries++;
                debug(DEBUG_NORMAL, "Reconnection attempt failed: " + e.getMessage());
                try {
                    Thread.sleep(5000); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        debug(DEBUG_BASIC, "Max reconnection attempts reached");

        try {
            connect(); // Will re-connect to sys through Addressing Server
            debug(DEBUG_BASIC, "Connected to new chat server after Addressing Server redirect.");
        } catch (IOException e) {
            debug(DEBUG_BASIC, "Unable to connect to any chat server. Client shutdown.");
            // Add user-friendly error message to display log
            synchronized (displayLog) {
                ClientServerMessage errorMsg = new ClientServerMessage("System", "all", -1,
                        "ERROR: No chat servers are currently available. Please try again later.");
                errorMsg.setCommand("INFO");
                displayLog.add(errorMsg);
            }
            // Give the OutputThread time to display the message
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            shutdown();
        }
    }

    private String[] getNewChatServerFromAddressingServer() throws IOException {
        debug(DEBUG_DETAILED, "Connecting to addressing server at " + address + ":" + addressPort);
        try (Socket addressSocket = new Socket(address, addressPort)) {
            BufferedReader addrReader = new BufferedReader(new InputStreamReader(addressSocket.getInputStream()));
            String serverInfo = addrReader.readLine();
            debug(DEBUG_LOW_LEVEL, "Received server info: " + serverInfo);

            if (serverInfo == null || !serverInfo.contains(":")) {
                throw new IOException("Invalid response from Addressing Server: " + serverInfo);
            }

            String[] parts = serverInfo.split(":");
            return new String[] { parts[0].trim(), parts[1].trim() };
        }
    }

    /**
     * Gracefully shuts down all client threads.
     * Ensures clean termination of all running threads.
     */
    private void shutdownThreads() {
        debug(DEBUG_DETAILED, "Shutting down client threads");
        if (senderThread != null)
            senderThread.interrupt();
        if (receiverThread != null)
            receiverThread.interrupt();
        if (inputThread != null)
            inputThread.interrupt();
        if (outputThread != null)
            outputThread.interrupt();
    }

    /**
     * Performs a clean shutdown of the client.
     * This includes:
     * 1. Setting the terminate flag
     * 2. Signaling shutdown to all threads
     * 3. Waiting for threads to complete (with timeout)
     * 4. Closing all network connections
     * 5. Cleaning up resources
     * 
     * The shutdown process uses a CountDownLatch to coordinate thread termination,
     * ensuring all threads complete their work before proceeding. A 5-second
     * timeout
     * prevents hanging if threads fail to respond to the shutdown signal.
     */
    public void shutdown() {
        debug(DEBUG_BASIC, "Initiating client shutdown");
        terminate = true;

        // Signal shutdown to all threads
        shutdownThreads();

        try {
            // Wait for all threads to complete (with timeout)
            if (!shutdownLatch.await(5, TimeUnit.SECONDS)) {
                debug(DEBUG_NORMAL, "Shutdown timeout - forcing exit");
            }

            // Clean up resources
            if (chatServer != null) {
                chatServer.close();
                chatServer = null;
            }
            if (in != null) {
                in.close();
                in = null;
            }
            if (out != null) {
                out.close();
                out = null;
            }
            if (lineReader != null) {
                lineReader.getTerminal().close();
            }
            debug(DEBUG_NORMAL, "All resources closed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug(DEBUG_NORMAL, "Shutdown interrupted");
        } catch (IOException e) {
            debug(DEBUG_NORMAL, "Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Creates a new chat message with a unique ID.
     * The message is created with:
     * - Current timestamp
     * - Incremented message counter
     * - Specified content and receiver
     * 
     * @param msgContents The content of the message to send
     * @param receiver    The intended recipient of the message (e.g., "fellow
     *                    clients" for broadcast)
     * @return A new ClientServerMessage with a unique ID and current timestamp
     */
    public ClientServerMessage createMessage(String msgContents, String receiver) {
        debug(DEBUG_DETAILED, "Creating message for receiver: " + receiver);
        return new ClientServerMessage(username, receiver, sendCounter++, msgContents);
    }

    /**
     * Sends a message to the server.
     * If the message fails to send:
     * - It is added to the message queue for retry
     * - A reconnection attempt is triggered
     * - The error is logged at DEBUG_NORMAL level
     * 
     * @param msg The message to send to the server
     * @throws IllegalStateException if the client is not connected to the server
     */
    public void sendMessage(ClientServerMessage msg) {
        try {
            debug(DEBUG_LOW_LEVEL, "Sending message: " + msg.toJson());
            out.println(msg.toJson());
        } catch (Exception e) {
            debug(DEBUG_NORMAL, "Error sending message: " + e.getMessage());
            messageQueue.add(msg);
            reconnect();
        }
    }

    /**
     * Thread responsible for sending messages to the server.
     * Features:
     * - Polls message queue every 100ms for pending messages
     * - Implements automatic retry for failed messages
     * - Maintains connection state awareness
     * - Handles reconnection scenarios
     * - Thread-safe message queue management
     * 
     * The thread will:
     * 1. Check connection status
     * 2. Poll message queue for pending messages
     * 3. Attempt to send messages if connected
     * 4. Sleep if disconnected or no messages
     * 5. Handle any exceptions during sending
     */
    private class SenderThread extends Thread {
        @Override
        public void run() {
            try {
                while (!terminate) {
                    if (isConnected) {
                        ClientServerMessage msg = messageQueue.poll();
                        if (msg != null) {
                            debug(DEBUG_DETAILED, "Retrying to send message from queue");
                            sendMessage(msg);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            if (!terminate) {
                                debug(DEBUG_NORMAL, "Sender thread interrupted");
                            }
                            break;
                        }
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            if (!terminate) {
                                debug(DEBUG_NORMAL, "Sender thread interrupted");
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                debug(DEBUG_NORMAL, "Sender thread error: " + e.getMessage());
            } finally {
                shutdownLatch.countDown();
            }
        }
    }

    /**
     * Thread responsible for receiving messages from the server.
     * Features:
     * - Processes incoming messages in real-time
     * - Handles message acknowledgments
     * - Manages message status updates (sending, sent)
     * - Maintains chat history in both msgLog and displayLog
     * - Handles reconnection scenarios
     * - Thread-safe message handling with synchronized collections
     * - Prevents duplicate message processing and display
     * - Special handling for registration messages
     * 
     * Message Processing Flow:
     * 1. Read message from server
     * 2. Parse JSON into ClientServerMessage
     * 3. Check for duplicate message IDs
     * 4. Process based on message type:
     * - Registration messages
     * - Acknowledgment messages
     * - Regular chat messages
     * 5. Update appropriate logs and display
     */
    private class ReceiverThread extends Thread {
        private volatile boolean isRegistered = false;
        private final Set<String> displayedMessageIds = Collections.synchronizedSet(new HashSet<>());

        @Override
        public void run() {
            try {
                while (!terminate) {
                    if (isConnected) {
                        String serializedMsg = in.readLine();
                        if (serializedMsg == null) {
                            debug(DEBUG_NORMAL, "Connection closed by server");
                            reconnect();
                            continue;
                        }

                        debug(DEBUG_LOW_LEVEL, "Received message: " + serializedMsg);
                        ClientServerMessage msg = BaseMessage.fromJson(serializedMsg, ClientServerMessage.class);

                        synchronized (messageLock) {
                            // Skip if we've already processed this message
                            if (!processedMessageIds.add(msg.getMessageId())) {
                                continue;
                            }

                            if (msg.getCommand().equals("REGISTER")) {
                                debug(DEBUG_BASIC, "Successfully registered with username: " + msg.getSender());
                                awaitingAck
                                        .removeIf(pendingMsg -> pendingMsg.getMessageId().equals(msg.getMessageId()));

                                // Only add registration success message if not already registered
                                if (!isRegistered) {
                                    isRegistered = true;
                                    // Add a success message to both logs
                                    ClientServerMessage successMsg = new ClientServerMessage("System", "all", -1,
                                            "Successfully registered with username: " + msg.getSender());
                                    successMsg.setCommand("INFO");
                                    msgLog.add(successMsg);
                                    if (!displayedMessageIds.contains(successMsg.getMessageId())) {
                                        displayLog.add(successMsg);
                                        displayedMessageIds.add(successMsg.getMessageId());
                                    }
                                }
                            } else if (msg.getSender().equals(username)) {
                                debug(DEBUG_DETAILED, "Message acknowledged by server");
                                msgLog.add(msg);
                                pendingMessages.remove(msg.getMessageId());
                                awaitingAck
                                        .removeIf(pendingMsg -> pendingMsg.getMessageId().equals(msg.getMessageId()));

                                // Only add to display log if not already displayed
                                if (!displayedMessageIds.contains(msg.getMessageId())) {
                                    displayLog.add(msg);
                                    displayedMessageIds.add(msg.getMessageId());
                                }
                            } else {
                                msgLog.add(msg);
                                if (!displayedMessageIds.contains(msg.getMessageId())) {
                                    displayLog.add(msg);
                                    displayedMessageIds.add(msg.getMessageId());
                                }
                            }
                        }
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            if (!terminate) {
                                debug(DEBUG_NORMAL, "Receiver thread interrupted");
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                debug(DEBUG_NORMAL, "Receiver thread error: " + e.getMessage());
            } finally {
                shutdownLatch.countDown();
            }
        }
    }

    /**
     * Thread responsible for handling user input.
     * Features:
     * - Reads user input using JLine with line editing support
     * - Processes special commands (/exit, /quit)
     * - Creates and queues new messages with unique IDs
     * - Maintains input buffer state
     * - Thread-safe message queue management
     * - Rate limiting for message sending (100ms minimum interval)
     * - Prevents duplicate message sending
     * - Tracks pending messages for acknowledgment
     * 
     * Input Processing Flow:
     * 1. Read input from terminal
     * 2. Clear input buffer
     * 3. Check for special commands
     * 4. Apply rate limiting if needed
     * 5. Create new message
     * 6. Add to pending messages
     * 7. Queue for sending
     */
    private class InputThread extends Thread {
        private final LineReader lineReader;
        private static final long MIN_MESSAGE_INTERVAL = 100; // Minimum time between messages in milliseconds
        private long lastMessageTime = 0;
        private final Set<String> sentMessageIds = Collections.synchronizedSet(new HashSet<>());

        public InputThread(LineReader lineReader) {
            this.lineReader = lineReader;
        }

        @Override
        public void run() {
            while (!terminate) {
                try {
                    String msgContents = lineReader.readLine();
                    if (msgContents == null) {
                        break; // EOF or interrupted
                    }

                    lineReader.getBuffer().clear();

                    if (msgContents.trim().equalsIgnoreCase("/exit") || msgContents.trim().equalsIgnoreCase("/quit")) {
                        debug(DEBUG_BASIC, "Received exit command. Shutting down client...");
                        shutdown();
                        return;
                    }

                    if (!msgContents.trim().isEmpty()) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastMessageTime < MIN_MESSAGE_INTERVAL) {
                            try {
                                Thread.sleep(MIN_MESSAGE_INTERVAL - (currentTime - lastMessageTime));
                            } catch (InterruptedException e) {
                                if (!terminate) {
                                    debug(DEBUG_NORMAL, "Input thread interrupted during rate limiting");
                                }
                                break;
                            }
                        }

                        debug(DEBUG_DETAILED, "Processing user input: " + msgContents);
                        ClientServerMessage newMsg = createMessage(msgContents, "fellow clients");

                        synchronized (messageLock) {
                            // Skip if we've already sent this message
                            if (!sentMessageIds.add(newMsg.getMessageId())) {
                                continue;
                            }

                            // Add to pending messages before sending
                            pendingMessages.add(newMsg.getMessageId());

                            // Add to awaitingAck and messageQueue
                            awaitingAck.add(newMsg);
                            messageQueue.add(newMsg);
                            lastMessageTime = System.currentTimeMillis();
                        }
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        if (!terminate) {
                            debug(DEBUG_NORMAL, "Input thread interrupted");
                        }
                        break;
                    }
                } catch (UserInterruptException e) {
                    if (!terminate) {
                        debug(DEBUG_NORMAL, "Input interrupted");
                    }
                    break;
                } catch (Exception e) {
                    debug(DEBUG_NORMAL, "Input thread error: " + e.getMessage());
                    break;
                }
            }
            shutdownLatch.countDown();
        }
    }

    /**
     * Thread responsible for updating the user interface.
     * Features:
     * - Manages terminal display updates with synchronized output
     * - Handles message area redrawing with dynamic sizing
     * - Maintains input line state at the bottom of the terminal
     * - Adapts to terminal size changes automatically
     * - Ensures thread-safe display updates
     * - 20ms refresh rate for UI updates
     * - Preserves message history with proper spacing
     * - Shows pending messages with [sending...] status
     * - Limits display to most recent 100 messages
     * - Special formatting for system messages (INFO command)
     * 
     * Display Layout:
     * 1. Header (lines 1-3)
     * - Title
     * - Connection status
     * - Separator lines
     * 2. Message area (starting at line 4)
     * - Most recent messages first
     * - Timestamp and sender for each message
     * - Special formatting for system messages
     * 3. Pending messages section
     * - Messages awaiting acknowledgment
     * - [sending...] status indicator
     * 4. Spacing (3 lines)
     * 5. Input line (bottom of terminal)
     * - Command prompt
     * - Current input buffer
     * 
     * Thread Safety:
     * - All display operations are synchronized
     * - Message lists are thread-safe collections
     * - Terminal operations are atomic
     * 
     * Performance:
     * - Only redraws when necessary (content changed)
     * - Maintains display position to minimize screen updates
     * - Efficient message history management
     */
    private class OutputThread extends Thread {
        private final LineReader lineReader;
        private int lastDisplaySize = 0;
        private int lastAwaitingSize = 0;
        private boolean needsRedraw = true;
        private int messageAreaStartLine = 4;
        private int inputLine = 0;
        private static final int MAX_MESSAGES = 100;
        private static final int MESSAGE_INPUT_SPACING = 3;

        public OutputThread(LineReader lineReader) {
            this.lineReader = lineReader;
            updateTerminalDimensions();
        }

        private void updateTerminalDimensions() {
            try {
                inputLine = terminal.getHeight() - 1;
            } catch (Exception e) {
                // If terminal operations fail during shutdown, use a default value
                inputLine = 24; // Default terminal height
            }
        }

        private void setupDisplay() {
            try {
                synchronized (System.out) {
                    System.out.print("\033[H\033[2J");
                    System.out.println("=== Chat Client ===");
                    System.out.println("Status: Connected");
                    System.out.println("-------------------");
                    System.out.println("-------------------");
                    System.out.println();
                    System.out.print("> ");
                    System.out.flush();
                }
            } catch (Exception e) {
                // Ignore terminal errors during shutdown
            }
        }

        private void render() {
            try {
                synchronized (System.out) {
                    int currentDisplaySize = displayLog.size();
                    int currentAwaitingSize = awaitingAck.size();
                    boolean sizeChanged = currentDisplaySize != lastDisplaySize
                            || currentAwaitingSize != lastAwaitingSize;

                    if (needsRedraw || sizeChanged) {
                        // Move to start of message area
                        System.out.printf("\033[%d;0H", messageAreaStartLine);
                        System.out.print("\033[J");

                        // Get the most recent messages
                        List<ClientServerMessage> recentMessages;
                        synchronized (displayLog) {
                            recentMessages = displayLog.stream()
                                    .skip(Math.max(0, displayLog.size() - MAX_MESSAGES))
                                    .toList();
                        }

                        // Display messages
                        for (ClientServerMessage msg : recentMessages) {
                            if (msg.getCommand().equals("INFO")) {
                                System.out.println(msg.getContent());
                            } else {
                                String timeStr = msg.getTimeSent().toString().split(" ")[3];
                                System.out.printf("[%s] %s: %s%n", timeStr, msg.getSender(), msg.getContent());
                            }
                        }

                        // Display pending messages
                        for (ClientServerMessage msg : awaitingAck) {
                            if (!msg.getCommand().equals("REGISTER")) {
                                String timeStr = msg.getTimeSent().toString().split(" ")[3];
                                System.out.printf("[%s] %s: %s [sending...]%n", timeStr, msg.getSender(),
                                        msg.getContent());
                            }
                        }

                        // Add spacing before input
                        for (int i = 0; i < MESSAGE_INPUT_SPACING; i++) {
                            System.out.println();
                        }

                        lastDisplaySize = currentDisplaySize;
                        lastAwaitingSize = currentAwaitingSize;
                        needsRedraw = false;
                    }

                    // Update input line
                    System.out.printf("\033[%d;0H", inputLine);
                    String currentInput = lineReader.getBuffer().toString();
                    System.out.print("> " + currentInput);
                    System.out.flush();
                }
            } catch (Exception e) {
                // Ignore terminal errors during shutdown
            }
        }

        @Override
        public void run() {
            try {
                setupDisplay();
                while (!terminate) {
                    try {
                        if (terminal.getHeight() != inputLine + 1) {
                            updateTerminalDimensions();
                            needsRedraw = true;
                        }
                        render();
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        if (!terminate) {
                            debug(DEBUG_NORMAL, "Output thread interrupted");
                        }
                        break;
                    } catch (Exception e) {
                        // If terminal operations fail, continue with default values
                        continue;
                    }
                }
            } catch (Exception e) {
                debug(DEBUG_NORMAL, "Output thread error: " + e.getMessage());
            } finally {
                shutdownLatch.countDown();
            }
        }
    }

    /**
     * Main entry point for the client application.
     * Configures the client using environment variables:
     * - SERVER_ADDRESS: The addressing server hostname (defaults to "localhost")
     * - SERVER_PORT: The addressing server port (defaults to 49800)
     * - DEBUG_LEVEL: The level of debug output (0-5, defaults to 0)
     * 
     * Features:
     * - Interactive username prompt with JLine support
     * - Fallback to "Anonymous" if no username provided
     * - Terminal cleanup after username input
     * - Debug logging of configuration
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        debug(DEBUG_BASIC, "Starting client application");

        // Initialize terminal for username input
        Terminal terminal = null;
        LineReader lineReader = null;
        String username = "Anonymous";
        try {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            System.out.print("Enter your username: ");
            username = lineReader.readLine().trim();
            if (username.isEmpty()) {
                username = "Anonymous";
            }
            // Clear the terminal after username input
            terminal.writer().print("\033[H\033[2J");
            terminal.writer().flush();
        } catch (Exception e) {
            debug(DEBUG_BASIC, "Error reading username, using default: Anonymous");
        }

        String serverAddress = System.getenv().getOrDefault("SERVER_ADDRESS", "localhost");
        int serverPort = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "49800"));

        debug(DEBUG_NORMAL, String.format("Client configuration - Username: %s, Server: %s:%d",
                username, serverAddress, serverPort));

        Client client = new Client(username, serverAddress, serverPort, terminal, lineReader);
        client.run();
    }
}
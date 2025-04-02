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
import io.github.cpsc559.team16.common.messaging.AckObjectTypes;
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
 *
 * <h2>Key Functionalities</h2>
 * <ul>
 * <li>Client connects to an addressing server, retrieves chat server
 * information, and connects to the chat server.</li>
 * <li>Manages sending and receiving messages asynchronously with retry
 * mechanisms on failure.</li>
 * <li>Handles real-time user input, message sending, and updates the display
 * with chat history and pending messages.</li>
 * <li>Handles graceful shutdown, including cleanup of threads and
 * resources.</li>
 * <li>Provides interactive command-line interface (CLI) with JLine, allowing
 * input from users.</li>
 * <li>Logs debug messages at different levels based on a configurable debug
 * level.</li>
 * </ul>
 *
 * 
 */
// @SuppressWarnings("unused")
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
    /**
     * The username assigned to the client for display purposes in the chat
     * application.
     * This value is set when the client registers with the chat server.
     */
    private String username;

    /**
     * A counter that increments each time a message is sent.
     * It is used to generate unique message IDs for tracking sent messages.
     */
    private int sendCounter = 0;

    // Server connection information
    /**
     * The hostname or IP address of the addressing server that facilitates the
     * initial connection to the chat server.
     */
    private String address;

    /**
     * The port number of the addressing server used for connecting and retrieving
     * chat server information.
     */
    private int addressPort;

    // Connection management
    /**
     * Tracks the number of reconnection attempts made by the client.
     * It is reset after a successful reconnection.
     */
    private int reconnectTries = 0;

    /**
     * The maximum number of reconnection attempts allowed.
     * If this limit is reached, the client will stop attempting to reconnect.
     */
    private static final int MAX_RECONNECT_TRIES = 5;

    // Chat server connection
    /**
     * The socket used to establish a connection to the chat server.
     * This is where messages are sent and received during the chat session.
     */
    private Socket chatServer;

    /**
     * The input stream reader used to read messages received from the chat server.
     */
    private BufferedReader in;

    /**
     * The output stream writer used to send messages to the chat server.
     */
    private PrintWriter out;

    // Message management
    /**
     * A synchronized list that stores the complete chat history of messages
     * exchanged
     * between the client and other peers.
     */
    private final List<ClientServerMessage> msgLog = Collections.synchronizedList(new LinkedList<>());

    /**
     * A synchronized list that holds the messages ready to be displayed in the
     * client
     * interface. These messages are selected from the complete chat history.
     */
    private final List<ClientServerMessage> displayLog = Collections.synchronizedList(new LinkedList<>());

    /**
     * A thread-safe queue holding the messages that are pending to be sent to the
     * chat server.
     */
    private final Queue<ClientServerMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * A thread-safe queue holding the messages that have been sent but are still
     * awaiting
     * acknowledgment from the server.
     */
    private final Queue<ClientServerMessage> awaitingAck = new ConcurrentLinkedQueue<>();

    /**
     * A synchronized set that tracks the message IDs of messages that have already
     * been
     * processed by the client to prevent duplicate handling.
     */
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());

    /**
     * A synchronized set that tracks the message IDs of messages that are still
     * awaiting
     * acknowledgment from the server. This helps in ensuring messages are properly
     * acknowledged.
     */
    private final Set<String> pendingMessages = Collections.synchronizedSet(new HashSet<>());

    /**
     * An object used as a lock to synchronize message operations, ensuring
     * thread-safety when
     * processing messages.
     */
    private final Object messageLock = new Object();

    // Thread management
    /**
     * The thread responsible for handling user input.
     * It reads commands and messages from the user, processes them, and adds them
     * to the
     * message queue for sending.
     */
    private InputThread inputThread;

    /**
     * The thread responsible for updating the user interface.
     * It manages the display of messages, system information, and the input prompt.
     */
    private OutputThread outputThread;

    /**
     * The thread responsible for sending messages to the server.
     * It manages the queue of pending messages and ensures they are sent to the
     * server.
     */
    private SenderThread senderThread;

    /**
     * The thread responsible for receiving messages from the server.
     * It processes incoming messages and updates the chat logs and display
     * accordingly.
     */
    private ReceiverThread receiverThread;

    /**
     * A CountDownLatch used to coordinate the shutdown of the client.
     * It ensures that all four threads (input, output, sender, receiver) complete
     * their work
     * before the client shuts down completely.
     */
    private final CountDownLatch shutdownLatch = new CountDownLatch(4);

    // System utilities
    /**
     * A flag that indicates whether the client should shut down.
     * Set to true when the client is being terminated.
     */
    private boolean terminate = false;

    /**
     * A flag that indicates the connection state of the client.
     * It is set to true when the client is successfully connected to the chat
     * server.
     */
    private boolean isConnected = false;

    // UI components
    /**
     * The terminal interface used for displaying the chat interface to the user.
     * It is managed by the JLine library and provides terminal-based input/output
     * handling.
     */
    private Terminal terminal;

    /**
     * The command-line reader used for capturing user input in the terminal.
     * It allows for command-line editing and handles user input interactively.
     */
    private LineReader lineReader;

    /**
     * Constructs a new chat client with the specified configuration.
     * This constructor sets up the client with necessary information for connecting
     * to an addressing server, initializes the terminal interface, and prepares
     * the line reader for command-line input.
     * 
     * @param username   The display name for this client. This will be used as the
     *                   sender's identifier in the chat system.
     * @param serverName The hostname or IP address of the addressing server.
     *                   This server is responsible for directing the client to the
     *                   appropriate chat server.
     * @param serverPort The port number on which the addressing server is running.
     * @param terminal   The terminal interface instance to be used for rendering
     *                   the
     *                   command-line interface. It enables interactive
     *                   input/output.
     * @param lineReader The line reader instance used to capture and process user
     *                   input
     *                   from the command line. This allows the user to interact
     *                   with the
     *                   chat client.
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
     * Starts the client application. This method performs the following steps:
     * <ol>
     * <li>Sets up the terminal interface for the client.</li>
     * <li>Establishes a connection to the server via the addressing server.</li>
     * <li>Registers the client with the server.</li>
     * <li>Initializes and starts the necessary threads for input, output, message
     * sending, and receiving.</li>
     * <li>Maintains the main application loop that runs until termination.</li>
     * </ol>
     * 
     * The method ensures that all components of the client application are running
     * and handles errors gracefully.
     * If any error occurs during the execution, the client will shut down properly.
     * 
     * <h2>Key Features:</h2>
     * <ul>
     * <li>Concurrent operations with separate threads for user input, message
     * sending, and message receiving.</li>
     * <li>Graceful shutdown process with cleanup of resources and thread
     * management.</li>
     * <li>Debug logging to monitor client behavior at various stages.</li>
     * </ul>
     * 
     * <h2>Exception Handling:</h2>
     * <ul>
     * <li><code>InterruptedException</code>: Triggered if the client is interrupted
     * during the main loop sleep.</li>
     * <li><code>IOException</code>: Triggered if an error occurs while closing the
     * chat server connection.</li>
     * </ul>
     * 
     * @throws InterruptedException If the thread is interrupted during sleep.
     * @throws IOException          If there is an error closing the chat server
     *                              connection.
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
                Thread.sleep(1000); // refresh rate
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

    /**
     * Registers the client with the addressing server by sending a registration
     * message and
     * awaiting a response. This method establishes a connection with the addressing
     * server,
     * sends the client's registration details, and processes the acknowledgment
     * received
     * from the server to retrieve the chat server's information.
     * 
     * The method performs the following steps:
     * <ol>
     * <li>Opens a socket connection to the addressing server</li>
     * <li>Sends a registration message in JSON format</li>
     * <li>Waits for the server's acknowledgment</li>
     * <li>Deserializes the acknowledgment response to extract the chat server's
     * information (PID, host, and port)</li>
     * <li>Returns the extracted information as an array of strings in the format:
     * {pid, host, port}</li>
     * </ol>
     * 
     * @return A string array containing the chat server's PID, host address, and
     *         port
     * @throws IOException If there is a failure in communication with the
     *                     addressing server,
     *                     including connection issues, timeouts, or invalid
     *                     responses.
     */

    private String[] registerWithAddressingServer() throws IOException {
        // Open a socket to the addressing server
        try (Socket addressSocket = new Socket(address, addressPort)) {
            addressSocket.setSoTimeout(5000); // Timeout after 5 seconds

            debug(DEBUG_NORMAL, "trying to connect to addressing server");

            // Create the streams
            PrintWriter out = new PrintWriter(addressSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(addressSocket.getInputStream()));

            // Create a REGISTER message for the client. Currently it doesn't send any
            // additional information,
            // but I'm guessing this is where we would do the token stuff??? - Aidan
            RegisterMessage<String> regMsg = RegisterMessage.fromClient();
            // Send the registration JSON message
            out.println(regMsg.toJson());

            // Wait for the addressing server's response ()
            String ackJson = in.readLine();
            if (ackJson == null) {
                throw new IOException("No response from Addressing Server.");
            }

            debug(DEBUG_NORMAL, "RECEIVED FROM SERVER: " + ackJson);

            // Deserialize the JSON into an AckMessage
            ObjectMapper mapper = new ObjectMapper();
            AckMessage ackMessage = mapper.readValue(ackJson, AckMessage.class);

            // Extract the payload. It should be a String in the format
            // "pid:hostAddress:clientPort"

            if (ackMessage.getObjectType().equals(AckObjectTypes.HOSTADDRESS)) {
                String payload = (String) ackMessage.getPayload();
                if (payload == null || !payload.contains(":")) {
                    throw new IOException("Invalid payload from Addressing Server: " + payload);
                }
                // Split the payload into 3 parts: pid, host, and port
                String[] substrings = payload.split(":");
                if (substrings.length != 3) {
                    throw new IOException("Expected 3 parts in the payload, but got " + substrings.length);
                }
                // System.out.println("done");

                return substrings;

            } else {
                System.out.println("ACK message indicated there were no chat servers available.");
                return null;
            }
        }
    }

    /**
     * Establishes a connection to the chat server through the addressing server.
     * 
     * This method performs the following steps:
     * <ol>
     * <li>Connects to the addressing server using the provided address and
     * port.</li>
     * <li>Retrieves chat server details (PID, address, and port) from the
     * addressing server.</li>
     * <li>Establishes a direct connection to the retrieved chat server using the
     * provided address and port.</li>
     * <li>Sends a registration message to the chat server to register the
     * client.</li>
     * </ol>
     * 
     * The method will repeatedly attempt to register with the addressing server if
     * the initial attempt fails.
     * It retries once per second until a successful response is received. Upon
     * success, the client will be registered
     * with the chat server.
     * 
     * If the connection to the chat server fails at any point, an
     * {@link IOException} will be thrown.
     * 
     * @throws IOException if there is a failure while connecting to the addressing
     *                     server,
     *                     the chat server, or during the message exchange.
     */
    @SuppressWarnings("resource")
    private void connect() throws IOException {
        if (!terminate) {
            try {
                debug(DEBUG_NORMAL, "trying to connect to server");

                // Establish server connection
                String[] substrings = null;
                while (substrings == null) {
                    substrings = registerWithAddressingServer();
                    Thread.sleep(1000); // pause 1s before retrying
                    // System.out.println("read");
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

                ClientServerMessage registration = new ClientServerMessage(username, "server", -1, "");
                registration.setCommand("REGISTER");
                out.println(registration.toJson());

                debug(DEBUG_BASIC, "Successfully connected to chat server");
                // System.out.println("CONNECTED");
            } catch (IOException e) {
                debug(DEBUG_BASIC, "Connection error: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                System.err.println("Error parsing chat server address: " + e.getMessage());
            }
        }
    }

    /**
     * Attempts to reconnect to the server network after a connection failure.
     * This method ensures the client can recover from network interruptions by
     * trying to
     * reconnect multiple times with an exponential backoff strategy.
     * 
     * <p>
     * The reconnect process involves retrying the connection up to a maximum number
     * of attempts
     * (defined by {@code MAX_RECONNECT_TRIES}). The backoff time between each
     * reconnection attempt
     * doubles, starting with a 1-second wait time. If all reconnection attempts
     * fail, the method will
     * attempt to retrieve a new chat server address from the Addressing Server and
     * reconnect using
     * that information. If the reconnection attempts ultimately fail, an error
     * message is displayed
     * and the client shuts down gracefully.
     * </p>
     *
     * <p>
     * Key operations:
     * </p>
     * <ul>
     * <li>Establishes a new connection with the chat server using exponential
     * backoff after each failure.</li>
     * <li>If the reconnection attempts are exhausted, it tries connecting via the
     * Addressing Server for a new server.</li>
     * <li>Handles retries with an interval that doubles after each failed attempt
     * to reduce server load.</li>
     * <li>In case of failure after all attempts, the method provides feedback to
     * the user and shuts down the client.</li>
     * </ul>
     *
     * @throws IOException If the connection attempts to either the chat server or
     *                     the Addressing Server fail
     *                     after the maximum retry attempts have been reached. This
     *                     ensures the client gracefully
     *                     handles persistent connection issues.
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

    /**
     * Gracefully shuts down all client threads.
     * This method ensures that all running client threads are cleanly terminated.
     * It interrupts each thread to signal them to stop their execution, ensuring
     * that resources are properly released, and no background processes are left
     * running.
     * 
     * <p>
     * This method is called during the shutdown process to ensure that all
     * active threads are safely interrupted before the client application
     * terminates.
     * It helps avoid issues such as memory leaks, incomplete operations, or
     * unhandled exceptions in any of the worker threads.
     * </p>
     * 
     * <p>
     * The threads that are interrupted include:
     * </p>
     * <ul>
     * <li>{@code senderThread} - Handles message sending.</li>
     * <li>{@code receiverThread} - Handles receiving messages.</li>
     * <li>{@code inputThread} - Handles reading user input.</li>
     * <li>{@code outputThread} - Handles updating the user interface.</li>
     * </ul>
     * 
     * <p>
     * By interrupting the threads, we signal them to stop their tasks and exit
     * cleanly.
     * Each thread will be responsible for handling its own shutdown operations once
     * interrupted.
     * </p>
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
     * Performs a clean shutdown of the client application.
     * This method ensures all client resources are properly cleaned up and all
     * threads
     * are terminated before the client shuts down. It handles the following steps:
     * <ul>
     * <li>Setting the terminate flag to signal that the client should stop.</li>
     * <li>Signaling shutdown to all running threads to stop their execution.</li>
     * <li>Waiting for all threads to complete their tasks with a timeout (5
     * seconds).</li>
     * <li>Closing network connections (e.g., chat server connection).</li>
     * <li>Cleaning up other resources such as input/output streams and
     * terminal.</li>
     * </ul>
     *
     * <p>
     * The shutdown process utilizes a {@link CountDownLatch} to coordinate the
     * termination
     * of threads, ensuring they finish their operations before the client
     * application fully shuts down.
     * </p>
     * 
     * <p>
     * If any thread fails to terminate within the specified timeout, the shutdown
     * will proceed
     * with a forced exit. This prevents the client from hanging indefinitely due to
     * unresponsive threads.
     * </p>
     *
     * <p>
     * Steps involved in this process:
     * </p>
     * <ol>
     * <li>Set the {@code terminate} flag to {@code true} to signal all threads to
     * stop.</li>
     * <li>Interrupt all running threads using {@link #shutdownThreads()}.</li>
     * <li>Wait for all threads to finish with a timeout of 5 seconds using the
     * {@code shutdownLatch}.</li>
     * <li>Close all network connections (chat server and input/output streams) and
     * cleanup resources.</li>
     * <li>If any resources cannot be cleaned up, handle the errors gracefully and
     * log them.</li>
     * </ol>
     * 
     * <p>
     * Note: The method gracefully handles interruptions and errors during shutdown
     * to ensure
     * that no resources are left in an inconsistent state.
     * </p>
     * 
     * @throws InterruptedException if the current thread is interrupted while
     *                              waiting for other threads to finish
     * @throws IOException          if there is an error while closing network
     *                              connections or resources
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
     * A thread responsible for sending messages to the server.
     * <p>
     * This thread manages the process of sending messages from a message queue to
     * the server,
     * with features such as:
     * <ul>
     * <li>Polling the message queue every 100ms for pending messages.</li>
     * <li>Implementing automatic retries for failed message sends.</li>
     * <li>Maintaining awareness of the connection state and handling
     * disconnections.</li>
     * <li>Ensuring thread-safe management of the message queue.</li>
     * </ul>
     * </p>
     * <p>
     * The {@link SenderThread} thread performs the following tasks:
     * <ol>
     * <li>Check if the client is connected to the server.</li>
     * <li>Poll the message queue for any pending messages to send.</li>
     * <li>Send messages to the server if the client is connected.</li>
     * <li>Sleep for 100ms between attempts if the queue is empty or there is no
     * connection.</li>
     * <li>Handle any exceptions or errors that occur while sending messages.</li>
     * </ol>
     * </p>
     * 
     * <p>
     * If the connection is lost or the message queue is empty, the thread will
     * sleep for
     * longer periods (up to 1 second) to reduce the load on the system.
     * </p>
     *
     * <p>
     * The {@link SenderThread} uses the {@code messageQueue} to manage messages
     * pending for delivery.
     * The thread ensures that messages are sent in the order they were added to the
     * queue, and it retries
     * messages that failed to send. If the client is disconnected, the thread waits
     * before attempting to
     * send again.
     * </p>
     *
     * <p>
     * After the message is successfully sent or the attempt fails, the thread will
     * proceed to the next message
     * or retry the failed message, depending on the connection status and available
     * messages in the queue.
     * </p>
     * 
     * <p>
     * When the {@code terminate} flag is set to {@code true}, the thread will exit
     * gracefully. The thread also
     * uses {@link #shutdownLatch} to signal that it has finished and is ready for
     * shutdown.
     * </p>
     * 
     * @see ClientServerMessage
     * @see shutdownLatch
     * @see messageQueue
     * @see sendMessage
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
     * A thread responsible for receiving messages from the server.
     * <p>
     * This thread handles the real-time reception of messages from the chat server,
     * managing the acknowledgment and status updates of messages. It ensures that:
     * <ul>
     * <li>Messages are processed as they arrive.</li>
     * <li>Message IDs are checked to prevent processing duplicates.</li>
     * <li>Message logs are updated in a thread-safe manner using synchronized
     * collections.</li>
     * <li>Special handling is applied to registration and acknowledgment
     * messages.</li>
     * <li>Reconnection scenarios are handled if the server connection is lost.</li>
     * </ul>
     * </p>
     * <p>
     * Message Processing Flow:
     * <ol>
     * <li>Read a message from the server.</li>
     * <li>Parse the message from JSON into a {@link ClientServerMessage}
     * object.</li>
     * <li>Check for duplicate message IDs and skip if already processed.</li>
     * <li>Based on the message type:
     * <ul>
     * <li>If it's a "REGISTER" message, update the registration status and log
     * it.</li>
     * <li>If it's an acknowledgment message, update message status in logs.</li>
     * <li>If it's a regular chat message, add it to the chat log and display
     * it.</li>
     * </ul>
     * </li>
     * <li>Update the display log only if the message hasn't been displayed
     * yet.</li>
     * </ol>
     * </p>
     * <p>
     * The thread also manages the following:
     * <ul>
     * <li>Preventing duplicate processing of messages by maintaining a set of
     * processed message IDs.</li>
     * <li>Handling both regular chat messages and system-specific messages like
     * registration.</li>
     * <li>Gracefully handling server disconnections and reconnecting to the server
     * when necessary.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * When the {@code terminate} flag is set to {@code true}, the thread will exit
     * gracefully. The thread also
     * uses {@link #shutdownLatch} to signal that it has finished and is ready for
     * shutdown.
     * </p>
     * 
     * @see ClientServerMessage
     * @see processedMessageIds
     * @see messageLock
     * @see displayLog
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
     * A thread responsible for handling user input from the command line interface.
     * <p>
     * This thread processes user input in real-time, supports command-line editing
     * via JLine, and manages the
     * message sending process with rate limiting to prevent spamming. It also
     * supports special commands like "/exit"
     * and "/quit" to gracefully shut down the client.
     * </p>
     * 
     * <p>
     * Key features of the InputThread:
     * <ul>
     * <li>Reads user input from the terminal using JLine with line editing
     * support.</li>
     * <li>Processes special commands like "/exit" and "/quit" for client
     * shutdown.</li>
     * <li>Creates and queues new messages with unique message IDs to prevent
     * duplicates.</li>
     * <li>Ensures thread-safe management of message queues with proper
     * synchronization.</li>
     * <li>Implements rate limiting for message sending with a minimum interval of
     * 100 milliseconds between messages.</li>
     * <li>Prevents duplicate message sending by tracking sent message IDs.</li>
     * <li>Tracks pending messages and queues them for acknowledgment by the
     * server.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * Input Processing Flow:
     * <ol>
     * <li>Read input from terminal using JLine's line reader.</li>
     * <li>Clear input buffer after reading the line.</li>
     * <li>Check if the input matches special commands such as "/exit" or "/quit" to
     * trigger client shutdown.</li>
     * <li>If the input is not a special command, apply rate limiting to prevent
     * sending messages too quickly (100ms minimum interval).</li>
     * <li>Create a new message with the content entered by the user and a unique
     * message ID.</li>
     * <li>Add the new message to the pending messages set and to the awaiting
     * acknowledgment and message queues.</li>
     * <li>Update the last message timestamp to enforce rate limiting and avoid
     * sending too many messages in a short time.</li>
     * </ol>
     * </p>
     * 
     * <p>
     * This thread also ensures that only one message is sent within the minimum
     * interval, preventing rapid, repeated submissions.
     * It will also gracefully handle any interruptions or errors during its
     * operation, with the ability to shut down the client
     * when appropriate.
     * </p>
     * 
     * @see ClientServerMessage
     * @see messageLock
     * @see pendingMessages
     * @see awaitingAck
     * @see messageQueue
     */
    private class InputThread extends Thread {
        private final LineReader lineReader;
        private static final long MIN_MESSAGE_INTERVAL = 100; // Minimum time between messages in milliseconds
        private long lastMessageTime = 0;
        private final Set<String> sentMessageIds = Collections.synchronizedSet(new HashSet<>());

        /**
         * Constructs a new InputThread for handling user input.
         * 
         * @param lineReader The JLine LineReader instance used to read user input from
         *                   the terminal.
         */
        public InputThread(LineReader lineReader) {
            this.lineReader = lineReader;
        }

        /**
         * The main run method that continuously reads input from the user, processes
         * it, and sends messages to the server.
         * <p>
         * The method performs the following tasks:
         * <ul>
         * <li>Reads user input from the terminal using JLine.</li>
         * <li>Processes special commands like "/exit" and "/quit" to terminate the
         * client.</li>
         * <li>Applies rate limiting to prevent sending too many messages in a short
         * period.</li>
         * <li>Creates new messages with unique IDs and queues them for sending.</li>
         * <li>Manages synchronization of message queues to ensure thread-safe
         * operations.</li>
         * </ul>
         * </p>
         */
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
     * A thread responsible for managing and updating the user interface in the
     * terminal.
     * <p>
     * This thread is responsible for rendering the message area, updating the input
     * line, and refreshing the terminal display
     * periodically. It ensures that the chat messages and the input buffer are
     * displayed correctly while maintaining the
     * message history and ensuring that pending messages are displayed with the
     * [sending...] status. The thread also handles
     * terminal resizing and adapts the layout dynamically.
     * </p>
     * 
     * <p>
     * Key Features:
     * <ul>
     * <li>Manages terminal display updates with synchronized output to ensure
     * thread-safe terminal operations.</li>
     * <li>Handles dynamic resizing of the message area based on the terminal's
     * size.</li>
     * <li>Displays chat history, including timestamps, senders, and content, while
     * limiting the display to the most recent 100 messages.</li>
     * <li>Displays pending messages with a [sending...] status for messages
     * awaiting acknowledgment.</li>
     * <li>Special formatting for system messages (INFO command), which are
     * displayed without sender information.</li>
     * <li>Ensures thread-safe management of the display and message updates using
     * synchronized collections.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * Display Layout:
     * <ol>
     * <li>Header (lines 1-3):
     * <ul>
     * <li>Title of the application (Chat Client)</li>
     * <li>Connection status (Connected or Disconnected)</li>
     * <li>Separator lines</li>
     * </ul>
     * </li>
     * <li>Message Area (starting at line 4):
     * <ul>
     * <li>Most recent messages displayed first</li>
     * <li>Timestamp and sender information for each message</li>
     * <li>Special formatting for system messages (INFO command)</li>
     * </ul>
     * </li>
     * <li>Pending Messages Section:
     * <ul>
     * <li>Displays messages awaiting acknowledgment with a [sending...] status</li>
     * </ul>
     * </li>
     * <li>Input Line (bottom of the terminal):
     * <ul>
     * <li>Displays the command prompt and the current input buffer</li>
     * </ul>
     * </li>
     * </ol>
     * </p>
     * 
     * <p>
     * Thread Safety:
     * <ul>
     * <li>All display operations are synchronized to prevent concurrent access
     * issues.</li>
     * <li>Message lists (displayLog and awaitingAck) are thread-safe collections to
     * ensure consistent access from multiple threads.</li>
     * <li>Terminal operations are atomic to avoid inconsistencies during UI
     * updates.</li>
     * </ul>
     * </p>
     * 
     * <p>
     * Performance:
     * <ul>
     * <li>The display only redraws when necessary (if content changes or terminal
     * dimensions change).</li>
     * <li>Maintains the display position to minimize unnecessary screen
     * updates.</li>
     * <li>Efficient message history management by limiting the display to the most
     * recent 100 messages.</li>
     * </ul>
     * </p>
     * 
     * @see ClientServerMessage
     * @see displayLog
     * @see awaitingAck
     * @see terminal
     * @see inputLine
     */
    private class OutputThread extends Thread {
        private final LineReader lineReader;
        private int lastDisplaySize = 0; // Last size of the display log (for comparison)
        private int lastAwaitingSize = 0; // Last size of the awaiting messages (for comparison)
        private boolean needsRedraw = true; // Flag to track if the display needs to be redrawn
        private int messageAreaStartLine = 4; // The starting line for the message area in the terminal
        private int inputLine = 0; // The line where the input field is displayed in the terminal
        private static final int MAX_MESSAGES = 100; // Maximum number of messages to display at once
        private static final int MESSAGE_INPUT_SPACING = 3; // Number of lines of spacing before the input field

        /**
         * Constructs an OutputThread instance for handling terminal display updates.
         * 
         * @param lineReader The JLine LineReader instance used for handling user input
         *                   and terminal line reading.
         */
        public OutputThread(LineReader lineReader) {
            this.lineReader = lineReader;
            updateTerminalDimensions(); // Initialize terminal dimensions
        }

        /**
         * Updates the terminal dimensions and recalculates the input line position.
         * This method ensures the input line is correctly positioned at the bottom of
         * the terminal.
         */
        private void updateTerminalDimensions() {
            try {
                inputLine = terminal.getHeight() - 1; // Set the input line to the last row of the terminal
            } catch (Exception e) {
                // If terminal operations fail during shutdown, use a default value
                inputLine = 24; // Default terminal height
            }
        }

        /**
         * Sets up the initial display with a title, status, and separator lines.
         * This method is called when the application starts to initialize the terminal
         * view.
         */
        private void setupDisplay() {
            try {
                synchronized (System.out) {
                    System.out.print("\033[H\033[2J"); // Clear the terminal screen
                    System.out.println("=== Chat Client ===");
                    System.out.println("Status: Connected");
                    System.out.println("-------------------");
                    System.out.println("-------------------");
                    System.out.println();
                    System.out.print("> ");
                    System.out.flush(); // Flush the output to ensure it displays immediately
                }
            } catch (Exception e) {
                // Ignore terminal errors during shutdown
            }
        }

        /**
         * Renders the terminal display, including the message area and the input field.
         * This method is responsible for updating the message area, showing pending
         * messages,
         * and ensuring the input buffer is visible at the bottom of the terminal.
         */
        private void render() {
            try {
                synchronized (System.out) {
                    int currentDisplaySize = displayLog.size(); // Get the current size of the displayed messages
                    int currentAwaitingSize = awaitingAck.size(); // Get the current size of the pending messages
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
                                    .skip(Math.max(0, displayLog.size() - MAX_MESSAGES)) // Display the most recent
                                                                                         // messages
                                    .toList();
                        }

                        // Display messages
                        for (ClientServerMessage msg : recentMessages) {
                            if (msg.getCommand().equals("INFO")) {
                                System.out.println(msg.getContent()); // System messages are displayed without sender
                                                                      // (server) info
                            } else {
                                String timeStr = msg.getTimeSent().toString().split(" ")[3]; // Extract timestamp
                                System.out.printf("[%s] %s: %s%n", timeStr, msg.getSender(), msg.getContent());
                            }
                        }

                        // Display pending messages (messages awaiting acknowledgment)
                        for (ClientServerMessage msg : awaitingAck) {
                            if (!msg.getCommand().equals("REGISTER")) { // Skip registration messages
                                String timeStr = msg.getTimeSent().toString().split(" ")[3]; // Extract timestamp
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

        /**
         * The main method for the OutputThread, which continuously updates the terminal
         * display.
         * This method is called in a loop, updating the display at a rate of every
         * 20ms,
         * and checking if the terminal size has changed.
         * <p>
         * The method will continuously render the terminal display and handle any
         * interruptions or exceptions.
         * </p>
         */
        @Override
        public void run() {
            try {
                setupDisplay(); // Set up the initial display
                while (!terminate) {
                    try {
                        if (terminal.getHeight() != inputLine + 1) { // Check if the terminal size has changed
                            updateTerminalDimensions(); // Update terminal dimensions
                            needsRedraw = true; // Mark the display as needing a redraw
                        }
                        render(); // Update the display
                        Thread.sleep(20); // Wait for 20ms before the next update
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
     * <p>
     * This method performs the following tasks:
     * <ul>
     * <li>Initializes the terminal interface for user interaction using JLine.</li>
     * <li>Prompts the user to enter a username. If the user fails to provide one,
     * the default username "Anonymous" is used.</li>
     * <li>Clears the terminal screen after the username input to prepare for the
     * chat application.</li>
     * <li>Reads environment variables to configure the client:
     * <ul>
     * <li>SERVER_ADDRESS: Specifies the hostname of the addressing server (defaults
     * to "localhost").</li>
     * <li>SERVER_PORT: Specifies the port of the addressing server (defaults to
     * 49800).</li>
     * <li>DEBUG_LEVEL: Specifies the level of debug output (0-5, defaults to
     * 0).</li>
     * </ul>
     * </li>
     * <li>Logs the client configuration using the debug level specified in the
     * environment variables.</li>
     * <li>Instantiates and runs the client application with the provided
     * configuration.</li>
     * </ul>
     * </p>
     * 
     * Features:
     * <ul>
     * <li>Interactive username prompt with JLine support for reading input from the
     * terminal.</li>
     * <li>If the user does not provide a username, it defaults to "Anonymous".</li>
     * <li>The terminal is cleared after username input to clean up the screen
     * before starting the chat application.</li>
     * <li>Configurable debug logging based on the provided environment variables,
     * helping track the application's state.</li>
     * </ul>
     * 
     * @param args Command line arguments (not used in this implementation).
     */
    public static void main(String[] args) {
        debug(DEBUG_BASIC, "Starting client application");

        // Initialize terminal for username input
        Terminal terminal = null;
        LineReader lineReader = null;
        String username = "Anonymous"; // Default username
        try {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            // Prompt the user for a username
            System.out.print("Enter your username: ");
            username = lineReader.readLine().trim();

            // If no username is provided, fall back to "Anonymous"
            if (username.isEmpty()) {
                username = "Anonymous";
            }
            // Clear the terminal after username input
            terminal.writer().print("\033[H\033[2J");
            terminal.writer().flush();
        } catch (Exception e) {
            debug(DEBUG_BASIC, "Error reading username, using default: Anonymous");
        }
        // Read the server configuration from environment variables
        String serverAddress = System.getenv().getOrDefault("ADDRESS_HOST", "localhost");
        int serverPort = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "49800"));

        debug(DEBUG_NORMAL, String.format("Client configuration - Username: %s, Server: %s:%d",
                username, serverAddress, serverPort));

        // Instantiate and run the client with the provided configuration
        Client client = new Client(username, serverAddress, serverPort, terminal, lineReader);
        client.run();
    }
}
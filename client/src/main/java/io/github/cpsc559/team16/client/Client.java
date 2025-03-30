package io.github.cpsc559.team16.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cpsc559.team16.common.utilities.ClientServerMessage;

/*
 * Base class for IRC-style application client. 
 * 
 */
public class Client {

    // user data
    private String username;
    private int sendCounter = 0; // Counter for messages sent (used to id messages)

    // handles reconnection
    private int reconnectTries = 0; // number of times the client has failed a reconnection
    private static final int MAX_RECONNECT_TRIES = 5; // the maximum number of times the client can attempt to reconnect before failing.

    // chat server data
    private Socket chatServer;
    private BufferedReader in;
    private PrintWriter out; // should only use this for the login attempt

    private final LinkedList<ClientServerMessage> msgLog = new LinkedList<>(); // Entire chatlog
    private final Queue<ClientServerMessage> messageQueue = new ConcurrentLinkedQueue<>(); // Messages awaiting ack from server
    private final Queue<ClientServerMessage> awaitingAck = new ConcurrentLinkedQueue<>(); // Messages awaiting ack from server

    // threads; grouped to help with shutdown
    private InputThread inputThread;  // thread for handling data input from the UI
    private OutputThread outputThread; // thread for rendering of UI
    private SenderThread senderThread; // thread to send messages to the server
    private ReceiverThread receiverThread; // thread to listen for incoming messages

    // logging utilities
    private static final Logger logger = Logger.getLogger("Client");

    // flag to indicate if the client should terminate
    private boolean terminate;
    private boolean isConnected = false;

    // JLine terminal and line reader for UI
    Terminal terminal;
    LineReader lineReader;

    ObjectMapper objectMapper = new ObjectMapper();

    ConnectionManager connectionManager;

    /**
     * Client app constructor. Handles the initial registration with the address server and setting up a connection to the chatServer.
     * 
     *  @param hostname name of the server that is being connected too; should be the address server
     *          (would it make more sense to use INetAddresses if these are hardcoded anyway?)
     *  @param portnum port number of the address server 
     **/
    public Client(String serverAddress, int serverPort) {
        this.connectionManager = new ConnectionManager(serverAddress, serverPort);
    }

    
    public void run() {

        terminate = false;

        try {

            // Set up terminal and line reader
            this.terminal = TerminalBuilder.builder().system(true).build();
            this.lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            // Establish session with addressing server
            login();

            // Connect to chat server
            connect();

            // Create threads
            inputThread = new InputThread(lineReader);
            outputThread = new OutputThread(lineReader);
            senderThread = new SenderThread();
            receiverThread = new ReceiverThread();

            // Start threads
            inputThread.start();
            outputThread.start();
            senderThread.start();
            receiverThread.start();

            // Wait for threads to finish
            while (!terminate) {
                Thread.sleep(1000); // So it doesn't blow up
            }

        }

        catch (Exception e) {
            // log
            String message =  "Error occured connecting to address server from Client "+ username + "\n" + e.getMessage();
            logger.warning(message);
            shutdown(); // interupt the main thread to shut down the client in a clean way
        }

        finally {
            try {
                chatServer.close();
            } catch (IOException e) {
                logger.warning("Error closing chatServer: " + e.getMessage());
            }
        }
    }

    /**
     * Prompts the user for their username and password, and attempts to log in to the address server.
     */
    private void login() {

        boolean loginSuccess = false;

        while (!loginSuccess) {

            // Get username from user input
            System.out.print("Enter your username: ");
            this.username = lineReader.readLine();

            // Get password from user input
            System.out.print("Enter your password: ");
            String password = lineReader.readLine();

            // Connect to the address server
            loginSuccess = connectionManager.login(username, password);

            if (!loginSuccess) {
                System.out.println("Login failed. Please try again.");
            } else {
                System.out.println("Login successful.");
            }

        }

    }

    /**
     * Connects with address server, which then connects it to a chat server.
     * 
    */
    private void connect() {

        try {

            chatServer = connectionManager.connectToChatServer();

            if (chatServer == null) {
                throw new Exception("Chat server connection failed.");
            } else {
                in = new BufferedReader(new java.io.InputStreamReader(chatServer.getInputStream()));
                out = new PrintWriter(chatServer.getOutputStream(), true);
                isConnected = true;
                reconnectTries = 0; // reset the reconnect tries
            }
            
        } catch (Exception e) {

            e.printStackTrace();
            logger.warning("Failed to connect to chat server.");

            if (reconnectTries >= MAX_RECONNECT_TRIES) {
                logger.warning("Max reconnect attempts reached. Shutting down.");
                shutdown();
            } else {
                reconnectTries++;
                connect();
            }
        }


    }

    /**
     * Shuts down all threads and closes the chat server connection.
     */
    private void shutdownThreads() {
        if (senderThread != null) senderThread.interrupt();
        if (receiverThread != null) receiverThread.interrupt();
        if (inputThread != null) inputThread.interrupt();
        if (outputThread != null) outputThread.interrupt();
    }

    /**
     * Shuts down the client application.
     */
    public void shutdown() {
        terminate = true;
        shutdownThreads();
        try {
            if (chatServer != null) chatServer.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            logger.warning("Error closing resources: " + e.getMessage());
        }
    }

    /**
     * Creates a message object from the user input.
     * 
     * @param msgContents The contents of the message.
     * @param receiver The receiver of the message.
     * @return A ClientServerMessage object representing the message.
     */
    public ClientServerMessage createMessage(String msgContents, String receiver) {
        return new ClientServerMessage(username, receiver, msgContents, sendCounter++);
    }

    /*
     * Sends a message to the server.
     * 
     * @param msg The message to be sent.
     */
    public void sendMessage(ClientServerMessage msg) {
        try {
            out.println(msg.toJson());
        } catch (Exception e) {
            messageQueue.add(msg); // put the message back onto the queue, it should be sent on the next round
            connect();
        }
    }

    /**
     * This class is responsible for sending messages to the server.
     */
    private class SenderThread extends Thread {

        @Override
        public void run() {
            try {
                while (!terminate) { 
                    if (isConnected) {
                        ClientServerMessage msg = messageQueue.poll(); // Check messageQueue

                        // If msg exists, send it
                        if (msg != null) {
                            sendMessage(msg);
                        }

                        Thread.sleep(2000); // So it doesn't blow up
                    }
                    else {
                        Thread.sleep(5000); // Wait for reconnect (5 sec)
                    }
                }
            } catch (Exception e) {
                System.out.println("Receiver error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    

    /*
     * This class is responsible for receiving messages from the server.
     */
    private class ReceiverThread extends Thread {

        @Override
        public void run() {
            try {
                // Receive messages from server until socket closed
                while (!terminate) {

                    if (isConnected) {

                        // Get message and reconnect if socket closed
                        String serializedMsg = in.readLine();
                        if (serializedMsg == null) {
                            connect();
                            continue;
                        }

                        // Deserialize JSON to ClientServerMessage
                        ClientServerMessage msg = objectMapper.readValue(serializedMsg, ClientServerMessage.class);

                        // If the message was sent by the current user, remove it from the awaitingAck queue
                        if (msg.getSender().equals(username)) {
                            awaitingAck.removeIf(pendingMsg -> pendingMsg.getClientCounter() == msg.getClientCounter());
                        }
                        // Add it to the msgLog for printing to UI
                        msgLog.add(msg);
                    }
                }
            }

            catch (Exception e) {
                System.out.println("Receiver error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /*
     * This class is responsible for handling user input.
     */
    private class InputThread extends Thread {

        private final LineReader lineReader;

        public InputThread(LineReader lineReader) {
            this.lineReader = lineReader;
        }

        @Override
        public void run() {
            while (!terminate) {
                try {

                    // Read user input
                    String msgContents = lineReader.readLine();
                    lineReader.getBuffer().clear();

                    // If the message isn't empty, proceed
                    if (!msgContents.trim().isEmpty()) {

                        // Create a message and add it to the messageQueue
                        messageQueue.add(createMessage(msgContents, "placeholder"));
                    }
                    Thread.sleep(1000); // So it doesn't blow up
                }
                
                catch (Exception e) {
                    System.out.println("Sender error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * This class is responsible for updating the UI.
     */
    private class OutputThread extends Thread {

        private final LineReader lineReader;

        public OutputThread(LineReader lineReader) {
            this.lineReader = lineReader;
        }

        @Override
        public void run() {
            try {
                while (!terminate) {
                    render();
                    Thread.sleep(500);
                }
            }
            
            catch (Exception e) {
                System.out.println("UI error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void render() {
            synchronized (System.out) {

                // Clear the screen
                System.out.print("\033[H\033[2J");
                System.out.flush();

                // Print chatlog
                for (ClientServerMessage msg : msgLog) {
                    System.out.println(msg);
                }

                // Print "sending" messages
                for (ClientServerMessage msg : awaitingAck) {
                    System.out.println(msg + " [sending...]");
                }

                // Reprint the current line
                System.out.print("> " + lineReader.getBuffer().toString());
                System.out.flush();
            }
        }
    }

    /*
     * Main method for the client application.
     */
    public static void main(String[] args){
        // from command line get:
        //  - hardcoded address server hostname 
        //  - hardcoded address server portnum
        //  - hardcoded username
        //  - whatever else we need help

        // get command line args (help!)  
        String STATIC_SERVER_ADDRESS = System.getenv().getOrDefault("AS_HOST_ADDRESS", "0.0.0.0");
        
        int STATIC_PORT = Integer.parseInt(System.getenv().getOrDefault("AS_CLIENT_PORT", "2424"));

        // launch a client 
        Client client = new Client(STATIC_SERVER_ADDRESS, STATIC_PORT);
        
        client.run();
            
        }
}
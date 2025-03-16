package io.github.cpsc559.team16.client;

import java.net.Socket;
import java.security.MessageDigest;

import javax.net.ssl.SSLSocket;  
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Queue;
import java.util.logging.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

// IMPORTS THAT NEED FIXING
import io.github.cpsc559.team16.utilities.BaseMessage; // can't get this import configured???
import io.github.cpsc559.team16.utilities.ClientServerMessage; // can't get this import configured???
import org.jline.reader.LineReader; // requires jline dependency
import org.jline.reader.LineReaderBuilder; // requires jline dependency
import org.jline.terminal.Terminal; // requires jline dependency
import org.jline.terminal.TerminalBuilder; // requires jline dependency

/*
 * Base class for IRC-style application client. 
 * 
 */
public class Client {

    // user data
    private String username;
    private int sendCounter = 0; // Counter for messages sent (used to id messages)

    // address server information
    // in future iterations this will be fetched from a static URL
    private String address;
    private int addressPort;

    // handles reconnection
    private int reconnectTries = 0; // number of times the client has failed a reconnection
    private static final int MAX_RECONNECT_TRIES = 5; // the maximum number of times the client can attempt to reconnect before failing.

    // chat server data
    private Socket chatServer;
    private ObjectInputStream in;
    private ObjectOutputStream out; // should only use this for the login attempt

    private final LinkedList<ClientServerMessage> msgLog = new LinkedList<>(); // Entire chatlog
    private final Queue<ClientServerMessage> messageQueue = new ConcurrentLinkedQueue<>(); // Messages awaiting ack from server
    private final Queue<ClientServerMessage> awaitingAck = new ConcurrentLinkedQueue<>(); // Messages awaiting ack from server

    // threads; grouped to help with shutdown
    private InputThread inputThread;  // thread for handling data input from the UI
    private OutputThread outputThread; // thread for rendering of UI
    private Receiver receiverThread; // thread to listen for incoming messages

    // logging utilities
    private static final Logger logger = Logger.getLogger("Client");

    // flag to indicate if the client should terminate
    private boolean terminate;

    // JLine terminal and line reader for UI
    Terminal terminal;
    LineReader lineReader;

    /**
     * Client app constructor. Handles the initial registration with the address server and setting up a connection to the chatServer.
     * 
     *  @param hostname name of the server that is being connected too; should be the address server
     *          (would it make more sense to use INetAddresses if these are hardcoded anyway?)
     *  @param portnum port number of the address server 
     **/
    public Client(String username, String serverName, int serverPort) {
        
        // sets up client data 
        this.username = username; 
        this.address = serverName;
        this.addressPort = serverPort;
        this.terminate = false;

        this.terminal = TerminalBuilder.builder().system(true).build();
        this.lineReader = LineReaderBuilder.builder().terminal(terminal).build();
    }

    public void run() {

        try {

            // Establish connection and session
            login();
            connectToAddress();

            // Create threads
            inputThread = new InputThread(lineReader);
            outputThread = new OutputThread(lineReader);
            receiverThread = new Receiver(in);

            // Start threads
            inputThread.start();
            outputThread.start();
            receiverThread.start();

            // Loop sending messaages
            while (true) {

                ClientServerMessage msg = messageQueue.poll();

                if (msg != null) {
                    try {
                        sendMessage(msg);
                    } catch (IOException e) {
                        reconnect();
                        messageQueue.add(msg); // put the message back onto the queue, it should be sent on the next round
                    }
                }

                Thread.sleep(1000); // So it doesn't blow up
            }

        } catch (Exception e) {
            // log
            String message =  "Error occured connecting to address server from Client "+ username + "\n" + e.getMessage();
            e.printStackTrace();
            logger.warning(message);
            shutdown(); // interupt the main thread to shut down the client in a clean way
            return;
        }
    }

    /**
     * Connects to the addressing server via hardcoded address and port number. 
     * Connects with a persistent chat server.
    */
    private void connectToAddress() throws IOException{
        try{
            Socket addressSocket = new Socket(this.address, this.addressPort); // connect to the port

            // handshake with the address server would go here
            // for now the address server just sends the Chat server's address and port number as a String
            //////////

            // GETTING CHATSERVER address and port:
            // this will have to eventually be received from the addressing server, for now hardcoded
            String chatServerAddress = "localhost";
            int chatServerPort = 8080;
            //////////

            chatServer = new Socket(address, chatServerPort); // connect to the chat server
            out = new ObjectOutputStream(chatServer.getOutputStream());
            in = new ObjectInputStream(chatServer.getInputStream());
        }
        catch(IOException e) {  
            // :((
        }
        return;

    }

    /**
     * Attempts to reconnect to the server network
     * @throws InterruptedException 
     */

    private void reconnect() {
        while(reconnectTries < MAX_RECONNECT_TRIES){
            try {
                connectToAddress();
            } catch (Exception e) {
                // iterate the reconnect tries by 1
                reconnectTries ++;  
                // wait a bit before trying again 
            }
        }
    }

    /**
     * Performs handshake with assosiated chat server.
     * For demo 2 this just means passing a username
    */
    private boolean login(){
        return false;
    }

    /**
     * Ends the client application.
     * Shuts down threads.
     */
    public void shutdown(){
        try{
            receiverThread.join();
            inputThread.join();
            outputThread.join();
        }
        catch(InterruptedException e){
            // if the main thread is interupted while waiting on threads
            // try to close them again
            if(receiverThread.isAlive()){ receiverThread.interrupt();}
            if(inputThread.isAlive()){ inputThread.interrupt();}
            if(outputThread.isAlive()){ outputThread.interrupt();}
 
        }
        // threads are all closed        
        // do something to save user data here? i guess?

    }

    /*
     * Clears the terminal screen.
     */
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /*
     * Sends a message to the server.
     * 
     * TODO: make sure this actually works
     */
    public void sendMessage(ClientServerMessage msg) throws IOException {
        out.writeObject(msg.toJson());
    }

    /*
     * Blocks until a message is received from server.
     * 
     * TODO: make sure this actually works
     */
    public ClientServerMessage receiveMessage() throws IOException {
        return (Message) in.readObject();
    }

    /*
     * Creates a message object from the user input.
     * 
     * TODO: ensure this works with Parmeet's serializable message class
     */
    public ClientServerMessage createMessage(String msgContents) {
        return new ClientServerMessage(username, id, msgContents);
    }

    /*
     * Receives messages from server and updates the chatlog.
     */
    private class Receiver extends Thread {

        private final ObjectInputStream in;

        public Receiver(ObjectInputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            while (true) {
                try {

                    // Receive message from server
                    ClientServerMessage msg = receiveMessage();


                    // If the message isn't null, proceed
                    if (msg != null) {

                        // If the message was sent by the current user, remove it from the awaitingAck queue
                        if (msg.getSender().equals(username)) {
                            awaitingAck.removeIf(pendingMsg -> pendingMsg.getId() == msg.getId());
                        }
                        // Add it to the msgLog for printing to UI
                        msgLog.add(msg);
                    }

                    Thread.sleep(2000);
                } catch (Exception e) {
                    System.out.println("Receiver error: " + e.getMessage());
                }
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
            while (true) {
                try {

                    // Read user input
                    String msgContents = lineReader.readLine();
                    lineReader.getBuffer().clear();


                    // If the message isn't empty, proceed
                    if (!msgContents.trim().isEmpty()) {

                        // Create a message and add it to the messageQueue
                        ClientServerMessage msg = createMessage(msgContents);
                        messageQueue.add(msg);
                        int id = sendCounter++;
                    }
                    Thread.sleep(1000); // So it doesn't blow up
                } catch (Exception e) {
                    System.out.println("Sender error: " + e.getMessage());
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
                while (true) {
                    render();
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                System.out.println("UI error: " + e.getMessage());
            }
        }

        private void render() {
            synchronized (System.out) {

                clearScreen();

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

    public static void main(String[] args){
        // from command line get:
        //  - hardcoded address server hostname 
        //  - hardcoded address server portnum
        //  - hardcoded username
        //  - whatever else we need help

        // get command line args (help!)   
        String username = "Chloe";
        String STATIC_SERVER_ADDRESS = "localhost";
        int STATIIC_PORT = 2424;

        // launch a client 
        Client client = new Client(username, STATIC_SERVER_ADDRESS, STATIIC_PORT);
        
        client.run();
            
        // :(
    
        }
}
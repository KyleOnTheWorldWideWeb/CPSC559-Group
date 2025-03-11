package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.utilities.ProcessUtils ; 
import io.github.cpsc559.team16.utilities.BaseMessage ; 
import io.github.cpsc559.team16.utilities.ClientServerMessage ; 


/*
 * Base class for IRC-style application client. 
 * 
 */

import java.net.Socket;
import java.security.MessageDigest;

import javax.net.ssl.SSLSocket;  
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

 import java.util.Queue;

import java.util.logging.*;

 public class Client {
    // user data
    private String username; 

    // address server information
    // in future iterations this will be fetched from a static URL
    private String address;
    private int addresssPort;
    
    // handles reconnection
    private int reconnectTries = 0; // number of times the client has failed a reconnection
    private static final int MAX_RECONNECT_TRIES = 5; // the maximum number of times the client can attempt to reconnect before failing.

    // chat server data
    private Socket chatServer;
    private InputStream inStream;
    private OutputStream outStream; // should only use this for the login attempt


    // chat messaging data
    private Queue<ClientServerMessage> messagQueue; // a queue for sending messages
    private Queue<ClientServerMessage> toPrintQueue; // a queue for printing messages to the 
    private AbstractChatLog messageLog;     

    // threads; grouped to help with shutdown
    private FetchMessageThread fetchThread; // thread for fetching messages from the server
    private InputThread inputThread;  // thread for handling data passing to the UI
    private OutputThread outputThread; // thread for handling data passing to the UI


    // logging utilities
    private static final Logger logger = Logger.getLogger("Client");

    /**
     * Client app constructor. Handles the initial registration with the address server and setting up a connection to the chatServer.
     * 
     *  @param hostname name of the server that is being connected too; should be the address server
     *          (would it make more sense to use INetAddresses if these are hardcoded anyway?)
     *  @param portnum port number of the address server 
     **/
    public Client(String username, String serverName, int serverPort){
        // sets up client data 
        this.username = username; 
        this.address = serverName;
        this.addresssPort = serverPort;

            try {
                connectToAddress();      

                // start UI threads for handling user input
                inputThread = new InputThread();
                outputThread = new OutputThread();;

                inputThread.start();
                outputThread.start();

            } catch (Exception e) {
                // log
                String message =  "Error occured connecting to address server from Client "+ username + "\n";
                message += e.getMessage();
                e.printStackTrace();
                logger.warning(message);

                shutdown(); // interupt the main thread to shut down the client in a clean way
                return;
            }
            // Client has been properly set up with a chat server
        }

        /**
         *  Run the client application.
         *  
         */
        public void run(){
            ClientServerMessage toSend = null; 

                        // later on this could be changed to be triggered by a user command but for now its just automated
            // log in to the chat server
            boolean loginSuccess = login();


            // start listening for messages from the server
            fetchThread = new FetchMessageThread(this.chatServer);
            fetchThread.start();

            // once connected to the
            while(true){
                // check for messages to send
                if(! messagQueue.isEmpty()){
                    // if a message exists in the message queue attempt to send it
                    try{
                        toSend = messagQueue.poll();
                        sendMessage(toSend);
                    }  
                    catch(IOException e){
                        // attempt to reconnect to the server
                        reconnect();
                        messagQueue.add(toSend); // put the message back onto the queue, it should be sent on the next round
                    }
                } 

            } // end run loop
        } // end run()

    /**
     * Connects to the addressing server via hardcoded address and port number. 
     * Connects with a persistent chat server.
    */
    private void connectToAddress() throws IOException{
        try{
            Socket addressSocket = new Socket(this.address, this.addresssPort); // connect to the port

            // handshake with the address server would go here
            // for now the address server just sends the Chat server's address and port number as a String
        }
        catch(IOException e){  
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
     * Sends a chat message to the associated chat server.
     * Encapsulates the chat message object into a ClientServerMessage
     * @param message 
    */
    private void sendMessage(ClientServerMessage message) throws IOException{
        // converts the message into its String format
        String messageStr = message.toString();

        // sends the message over the socket
        // waits for an ack?

    }

    /**
     * Ends the client application.
     * Shuts down threads.
     */
    public void shutdown(){
        try{
            fetchThread.join();
            inputThread.join();
            outputThread.join();
        }
        catch(InterruptedException e){
            // if the main thread is interupted while waiting on threads
            // try to close them again
            if(fetchThread.isAlive()){ fetchThread.interrupt();}
            if(inputThread.isAlive()){ inputThread.interrupt();}
            if(outputThread.isAlive()){ outputThread.interrupt();}
 
        }
        // threads are all closed        
        // do something to save user data here? i guess?

    }
    

    /*
     * Spawns a thread for handling connection to the chat server specifically for recieving messages from the server 
     */
    private class FetchMessageThread extends Thread {
        private Socket chatServerSocket;
        private InputStream csInStream; 
        private OutputStream csOutStream; // we probabably will never need this

        private BufferedReader inStream;

        /**
         * establishes communication with the chat server
         * @param
         * csSocket     a socket connected to the current chat server
         */
        public FetchMessageThread(Socket csSocket){
            try{
                this.chatServerSocket = csSocket;
                this.csInStream = csSocket.getInputStream();
            }
            catch(IOException e){
                // log that an error occured
            }
        }

        /**
         * Run when the Client reconnects to a new server. 
         * 
         * @param newCSSocket
         */
        public void reconnect(Socket newCSSocket){
            try{
                this.chatServerSocket = newCSSocket;
                this.csInStream = newCSSocket.getInputStream();
            }
            catch(IOException e){
                // log that an error occured
                e.printStackTrace();
                return;
            }
        }

        /**
         *  Continually attempts to read from the socket to get messages from the server.
         *  When a message is read adds to the outgoing message queue.
         */
        @Override
        public void run(){
            // assumes client has already logged in, so all messages being read in will be chat messages
            // attempts to read data in from the client

            while(true){
                // attempt to read from the inputStream

                // if there is something to read then parseMessage()

            }
        }


        /**
         * Reads a clientServerMessage from the socket and packages it into a chat-Message type.
         * Passes the parsed Message to the output writer 
         */
        private void parseMessage() throws IOException{

            return;
        }

        
        /*
		* Runs when this thread is interrupted.
		* Closes socket connection, closes the Input and Output Streams as well as the file object if it exists
		* After this method runs this thread will kill itself (end)
		*/
	   public void interrupt(){
        // clean up
        // clean up socket and the streams
        closeSocketStreams();

        return; // kill self 
    }

        /**
         * Wrapper method for closing the ChatServer socket and its associated file streams
        */
        private void closeSocketStreams() {
        try{
            // close the InputStream
            this.csInStream.close();

            // close the OutputStream
            this.csOutStream.close();

            // close the socket
            this.chatServerSocket.close();
        }
        catch(IOException e){
            e.printStackTrace(); // something went wrong closeing the sockets
                                 // this thread is already closing itself up
                                 // so no elegent handling will be done
            return;
        }
    }



    } // end ConnectionThread

    /**
     *  Spawns a thread for geting user input from the UI (terminal?)
     */
    private class InputThread extends Thread{
        private Scanner reader;
       
        public InputThread(){
            this.reader = new Scanner(System.in); // get user input via standred in        

        }


        /**
         * Method that runs when this thread is created.
         */
        @Override
        public void run(){
            // continually checks for input
            // when input comes in, formats a message `
            while(true){
                String messageContent = reader.nextLine(); 
                // mutext this section
                // end mutext
            }
        }

        /**
         * interupts this thread, performs clean up, and ends itself
         */
        public void interrupt(){
            // clean up
            reader.close(); // close the Scanner
            return;
        }
    }

    /**
     * Spawns a thread for displaying message logs to the UI (terminal)
     */
    private class OutputThread extends Thread{


        /**
         * Method that runs when this thread is created.
         * Idk what to put here. Help
         */
        @Override
        public void run(){

        }

        /**
         * interupts this thread, performs clean up, and ends itself
         */
        public void interrupt(){
            // clean up
            return;
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


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

    // chat messaging data
    private Queue<AbstractMessage> messagQueue; // a queue for sending messages
    private Queue<AbstractMessage> toPrintQueue; // a queue for printing messages to the 
    private AbstractChatLog messageLog;     

    // threads; grouped to help with shutdown
    private ConnectionThread connectionThread;
    private InputThread inputThread; 
    private OutputThread outputThread;


    // logging utilities
    private static final Logger logger = Logger.getLogger("Client");

    /**
     * Client app constructor
     * 
     *  @param hostname name of the server that is being connected too; should be the address server
     *          (would it make more sense to use INetAddresses if these are hardcoded anyway?)
     *  @param portnum port number of the address server 
     **/
    public Client(String username, String serverName, int serverPort){
        // sets up client data 
        this.username = username; 

        // spawns threads and links them
        connectionThread = new ConnectionThread(serverName, serverPort);
        inputThread = new InputThread();
        outputThread = new OutputThread();;

        // start threads
        connectionThread.start();
        inputThread.start();
        outputThread.start();

        // client now has very little to do
    }

    /**
     * Ends the client application.
     * Shuts down threads.
     */
    public void shutdown(){
        try{
            connectionThread.join();
            inputThread.join();
            outputThread.join();
        }
        catch(InterruptedException e){
            // if the main thread is interupted while waiting on threads
            // try to close them again
            if(connectionThread.isAlive()){ connectionThread.interrupt();}
            if(inputThread.isAlive()){ inputThread.interrupt();}
            if(outputThread.isAlive()){ outputThread.interrupt();}
 
        }
        // threads are all closed
        
        // do something to save user data here? i guess?

    }
    

    /*
     * Spawns a thread for handling connection to the server(s). 
     */
    private class ConnectionThread extends Thread {
        private Socket css;
        private InputStream inStream;
        private OutputStream outStream;


        // handles reconnection
        private int reconnectTries = 0; // number of times the client has failed a reconnection
        private static final int MAX_RECONNECT_TRIES = 5; // the maximum number of times the client can attempt to reconnect before failing.


        // tracks
        private OutputThread output; // track and inform

        public ConnectionThread(String address, int port){

        }

        /**
         *  Running code for the thread
         */
        @Override
        public void run(){

            return;
        }
        



        /**
         * Connects to the addressing server via provided address. 
         * Connects with a persistent chat server.
         */
        private void connectToAddress(String address, int port){

            return;
        }

        /**
         * Performs handshake with assosiated chat server.
         * For demo 2 this just means passing a username
         */
        private void login(){

        }

        /**
         * sends a chat message to the associated chat server.
         * @param message 
         */
        private void sendMessage(AbstractMessage message){

        }

        /**
         * Reads a clientServerMessage from the socket and packages it into a chat-Message type.
         * Passes the parsed Message to the output writer 
         */
        private void parseMessage(){

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
            this.inStream.close();

            // close the OutputStream
            this.outStream.close();

            // close the socket
            this.css.close();
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
                messagQueue.add(new Message(username, messageContent)); // add the
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


}


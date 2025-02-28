package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.utilities.ProcessUtils;

/*
 * Base class for IRC-style application client. 
 * 
 */

 import java.net.Socket;
 import javax.net.ssl.SSLSocket; 
 import java.io.*;
 import java.util.Scanner;
 import org.json.simple.JSONObject;

import com.fasterxml.jackson.databind.util.JSONPObject;

import java.util.logging.*;

 public class Client {
    private String username; // the name that the Client will use with the Server
    // something to store messages with?


    

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
        // probably needs to establish local copies of message objects? maybe?

        // spawns threads and links them
        ConnectionThread connectionThread = new ConnectionThread(serverName, serverPort);

    }

    /**
     * Ends the client application.
     * Shuts down threads.
     */
    public void shutdown(){

    }
    

    /*
     * Spawns a thread for handling connection to the server. 
     */
    private class ConnectionThread extends Thread {
        private Socket cs;
        private InputStream readStream;
        private OutputStream writeStream;
        private int buffSize = 1000 * 32; // size of buffers used by this class for read and write operations
    

        /**
         * Thread constructor
         * @param hostname
         * @param portnum
         */
        public ConnectionThread(String hostname, int portnum)  {
            // creates a socket that connects to the server
            try{
                this.cs = Socket(hostname, portnum);
                this.readStream = cs.getInputStream();
                this.writeStream = cs.getOutputStream();

            } catch(IOException e){
                // handle this error later; Logger
                //logger.log(null, hostname, e);
                return; 
            }
            // thread was created correctly?
        }

        /**
        * Method that runs when a thread is spawned. 
        * Overwrites the Threads.run() method.
        **/

        @Override
        public void run(){

        }

        /**
         * Sends a message to the server.
         * Will need to figure out how to format messages, i think?
         * 
         * @param username
         * @param message
         */
        public void sendMessage(String username, String message){
            // help???
            writeStream.write(message);
            writeStream.flush();
        }
        
        /**
         * Fetches a message log from the server
         */
        public void getMessageLog(){
                      // help!  
        }


    }

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
        }

        private void getInput(){

        }




    }

    /**
     * Spawns a thread for displaying message logs to the UI (terminal)
     */
    private class OutputThread extends Thread{

        /**
         * Method that runs when this thread is created.
         */
        @Override
        public void run(){

        }
    }


}


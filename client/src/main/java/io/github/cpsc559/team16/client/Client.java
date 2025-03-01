package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.utilities.ProcessUtils;

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
 import org.json.simple.JSONObject;

 import java.util.Queue;

import java.util.logging.*;

 public class Client {
    // user data
    private String username; 

    // messaging data
    private Queue<AbstractMessage> messagQueue; 
    private AbstractChatLog messageLog; // multiple copies for multiple chats? what defines a chat?    

    // threads
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
        outputThread = new OutputThread();

        // link threads that need to be linked
        // link input thread and connection thread
        // link output thread and connection thread

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

        // tracks
        private OutputThread output; // track and inform

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
         * Connects this thread to an output thread
         * @param outputThread
         */
        public void link(OutputThread outputThread){
            this.output = outputThread;
        }

        /**
         * Informs the output thread about a new message on the message log (???)
         */
        private void notifyOutput(){
            
        }

        /**
        * Method that runs when a thread is spawned. 
        * Overwrites the Threads.run() method.
        **/

        @Override
        public void run(){
            while(true){
                // check for new messages in the message queue 
                // if a message exists:
                if( !messagQueue.isEmpty()){
                    // send next message in the queue to the server
                    // this section needs to be in a mutex 
                    AbstractMessage nextMessage = messagQueue.pull();
                    try{
                        this.sendMessage(username, messagQueue.pull());
                        // should do something to make sure the message is actually sent but idk what
                    }
                    catch(IOException e){
                        // help
                    }

                    // end mutex 
                    
                    // add message to local message log
                    messageLog.add(nextMessage);
                    // notify the output about a new message on the messageLog
                    this.notifyOutput();
                }
                // else: no new messages to send

                // check for updates from the server (???)
                // if an update exists:
                        // pull update
                        //pass to

            }
        }

        /**
         * Attempts to reconnect to the address server, should be tried if connection is disconnected
         * Might still fail.
         */
        private void reconnect(){
            return;
        }


        /**
         * Sends a message to the server.
         * Will need to figure out how to format messages, i think?
         * 
         * @param username
         * @param message
         */
        public void sendMessage(String username, String message) throws IOException{
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
            while(true){
                String messageContent = reader.nextLine(); 
                // mutext this section
                messagQueue.add(new Message(username, messageContent)); // add the
                // end mutext
            }
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
    }


}


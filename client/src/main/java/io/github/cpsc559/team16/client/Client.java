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

 import java.util.Queue;

import java.util.logging.*;

 public class Client {
    // user data
    private String username; 

    // messaging data
    private Queue<AbstractMessage> messagQueue; 
    private AbstractChatLog messageLog; // multiple copies for multiple chats? what defines a chat?    

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
        outputThread = new OutputThread();

        // link threads that need to be linked
        connectionThread.link(outputThread);

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
     * Spawns a thread for handling connection to the server. 
     */
    private class ConnectionThread extends Thread {
        private Socket cs;
        private InputStream inStream;
        private OutputStream outStream;
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
                this.cs = new Socket(hostname, portnum);
                this.inStream = cs.getInputStream();
                this.outStream = cs.getOutputStream();

            } catch(IOException e){
                // handle this error later; Logger
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
                    AbstractMessage nextMessage = messagQueue.remove();
                    try{
                        this.sendMessage(username, messagQueue.remove());
                        // should do something to make sure the message is actually sent but idk what
                    }
                    catch(IOException e){
                        // help
                        // try reconnect? idk!
                    }

                    // end mutex 

                    // add message to local message log
                    messageLog.addMessage(nextMessage);
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
        public void sendMessage(String username, AbstractMessage message) throws IOException{
            // help???
            //outStream.write();
            outStream.flush();
        }
        
        /**
         * Fetches a message log from the server
         */
        public void getMessageLog(){
                      // help!  
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
     * Wrapper method for closing the connected socket and the associated input-output streams
     */
    private void closeSocketStreams() {
        try{
            // close the InputStream
            this.inStream.close();

            // close the OutputStream
            this.outStream.close();

            // close the socket
            this.cs.close();
        }
        catch(IOException e){
            e.printStackTrace(); // something went wrong closeing the sockets
                                 // this thread is already closing itself up
                                 // so no elegent handling will be done
            return;
        }
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


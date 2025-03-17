package io.github.cpsc559.team16.chatserver;

import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;
import io.github.cpsc559.team16.addressingserver.ChatServerInfo;
import io.github.cpsc559.team16.client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

public class ChatServer {


    /**
     * The port used for client connections.
     */
    private int clientPort;

    /**
     * The port reserved for peer-to-peer communication amongst
     * {@code ChatServer} processes.
     */
    private int peerPort;

    /**
     * The port used for communicating with the primary {@code AddressingServer}.
     * This is the port chat server uses to register itself with the {@code AddressingServer}.
     */
    private int addrServerPort;

    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the ChatServer to monitor multiple channels using a single thread.
     */
    private Selector selector;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from peer {@code ChatServer}'s.
     * When a connection is accepted, a new data channel is created for communicating with that chat server.
     */
    private ServerSocketChannel peerListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from clients.
     * When a connection is accepted, a new data channel is created for communicating with that client.
     * Clients connect to this channel to gain access to the chat-service on our network.
     */
    private ServerSocketChannel clientListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from {@code AddressingServer}'s.
     * This channel is used for establishing (but not maintaining) communication between this process
     * and the primary addressing server.
     */
    private ServerSocketChannel addrServerListenerChannel;

    /**
     * Each {@code ChatServer} process has a distinct id amongst its peers. When it registers
     * with the Primary {@code AddressingServer} it receives an ACK in the form of its {@code pid}.
     */
    private long pid;

    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final BlockingQueue<BaseMessage> messageQueue = new LinkedBlockingQueue<>();

    /**
     * A mapping of unique chat server IDs to their corresponding {@link ChatServerInfo} records.
     * <p>
     * A {@code ChatServerInfo} record is maintained and updated by the Primary Addressing Server
     * for each registered chat server in the network.
     * </p>
     * Chat Servers retrieve these records when initially registering with the Addressing Server.
     * Incremental updates occur from that point on.
     */
    private Map<Long, ChatServerInfo> chatServerRecords;

    /**
     * Contains the set of all open channels between this {@code ChatServer} and its peers.
     * <p>
     * Our chat server protocol is such that when they communicate, it is a system wide broadcast,
     * so a simple Data structure like an Array is suitable.
     * </p>
     */
    private ArrayList<SocketChannel> peerChannels;


    // TODO: Need a unique identifier (instead of Client) so that when a request on a persistent channel
    //  between the chatserver and the client is received, it can be used as a key to look up which client
    //   is on the other end of the channel.

    /**
     * Contains the set of all open channels between the {@code ChatServer} and client processes.
     */
    private Map<SocketChannel, Client> clientChannels;



    public ChatServer() {
        // Print all environment variables for debugging
        System.out.println("ChatServer environment variables: ");
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));
        // Read the network address from the environment variable
        clientPort = Integer.parseInt(System.getenv().getOrDefault("CS_CLIENT_PORT", "2424").trim());
        peerPort = Integer.parseInt(System.getenv().getOrDefault("CS_PEER_PORT", "2425").trim());
        addrServerPort = Integer.parseInt(System.getenv().getOrDefault("CS_ADDRSERVER_PORT", "2426").trim());

        chatServerRecords = new ConcurrentHashMap<>();
    }


    public void mainEventLoop() throws IOException {
        while (true) {
            // Any thread calling this method blocks until an event occurs on a channel registered with the `selector`.
            selector.select();
            /*
             * When you register a channel with a selector, you get back a SelectionKey.
             * Here, we iterate through all keys (channels) that are "ready" for an I/O operation.
             * We know that at least one such key exists because of the preceding `selector.select()` method invocation.
             */
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {      // Loop until there are no more keys (channels with I/O events).
                SelectionKey key = keys.next();    // Retrieve a new key
                keys.remove();                     // Remove the key used in the last iteration of this loop.

                if (!key.isValid()) {               // skip current loop iteration
                    continue;
                }

                /* Establishing a new connection. `isAcceptable` returns true if a ServerSocketChannel registered
                 *  with `selector` is ready to accept (detected) a new connection. SocketChannel's registered with
                 *   the `selector should not appear here.
                 */
                if (key.isAcceptable()) {
                    /*
                     * `listeningSC` is not a "new" channel - it is one of the ServerSocketChannels we registered
                     *  with the selector in the `initializeChannels` method.
                     */
                    ServerSocketChannel listeningSC = (ServerSocketChannel) key.channel();
                    // We use a `SocketChanel` for established connections. `newChannel.isAcceptable()` will ALWAYS return False
                    SocketChannel newChannel = listeningSC.accept();
                    newChannel.configureBlocking(false); // Set channel to "non-blocking" I/O
                    /*
                     * Register the `newChannel` with the `selector` which will add a `SelectionKey` for that
                     * channel and monitor it until it is explicitly closed.
                     */
                    newChannel.register(selector, SelectionKey.OP_READ); // SocketChannel set to persistent non-blocking I/O
                    if (listeningSC == clientListenerChannel) {
                        // TODO: Implement Client connection handling
                        // Add the persistent channel for this client to clientChannels.put()
                    } else if (listeningSC == peerListenerChannel) {
                        // TODO: Implement peer-to-peer connection handling
                    } else if (listeningSC == addrServerListenerChannel) {
                        // Don't need addressing server connection handling at this stage of the project
                    }
                    /*
                     * Only keys tied to channels that were registered with OP_READ
                     * (as we are doing above with `newChannel`) will return True when invoking `isReadable()`.
                     * Typically, only a SocketChannel would ever be registered with OP_READ.
                     */
                    if (key.isReadable()) {
                    /*
                     This is where persistent channel I/O occurs.
                     Not sure how you want to handle differentiating between chat server and client Channels.
                     I was thinking....
                     clientID = clientChannels.get(newChannel)
                     if (int clientID != null) then it must be a client
                     else it must be a chat server
                     */

                    }


                }
            }
        }
    }

    public static void main(String[] args) {
        // This timeout is necessary for proper output in the new terminal window...
        // ... without it, the initial output to console occurs before the terminal is open
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {System.err.println(e.getMessage());}
        // >------------------- CODE AIDAN ADDED ------------------<
        ChatServer server = new ChatServer();
        try {
            server.mainEventLoop();
        } catch (Exception ioe) {
            System.err.println("Main event loop in Chat Server failure - process halted.\nError message: " + ioe.getMessage());
            ioe.printStackTrace();
        }



        // Read the port from the environment variable, default to 2424 if not set
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "2424"));
        System.out.printf("Chat Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        // Start the message broadcasting thread
        // This is in charge of handling outgoing recieved messages.
        // We spray all messages out to all our clients
        new Thread(ChatServer::broadcastMessages).start();

        // I think we should consider creating a threadpool for this instead of this
        // implementation.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.print("ChatServer is attempting to connect to addressing server.......");
            try (Socket addrServerSocket = new Socket("host.docker.internal", 49802)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(addrServerSocket.getInputStream(), StandardCharsets.UTF_8));
                String ack = reader.readLine();
                System.out.println(ack);
            } catch (IOException e) {
                System.err.println("An error occured while attempting to register with the addressing server: " + e.getMessage());
                e.printStackTrace();
            }

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
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Broadcasting thread interrupted: " + e.getMessage());
        }
    }
}
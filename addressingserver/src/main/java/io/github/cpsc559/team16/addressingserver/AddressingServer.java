package io.github.cpsc559.team16.addressingserver;
// External Dependencies
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional; // Used for conditionals that don't rely on non-null checks
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;


// Internal (Project) Dependencies
import io.github.cpsc559.team16.addressingserver.ChatServerInfo.ServerStatus;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;



public class AddressingServer {

    /**
     * The network configuration for this {@code AddressingServer} process.
     */
    private final AddrServerConfig config;

    /**
     * Responsible for opening listener channels, managing the selector,
     * and dispatching accepted connections for the AddressingServer.
     */
    private final AddrServerNetworkManager networkManager;

    /**
     * The process responsible for managing {@code ChatServer} connections.
     */
    private final ChatServerRegistry chatServerRegistry;

    /**
     * The number of chat servers that have been registered.
     * Count begins at 1, not zero, because normals don't start at zero.
     */
    private long pidCounter;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from chat servers.
     * When a connection is accepted, a new data channel is created for communicating with that chat server.
     */
    private ServerSocketChannel chatServerListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from clients.
     * When a connection is accepted, a new data channel is created for communicating with that client.
     * Clients connect to this channel to be assigned to an active chat server.
     */
    private ServerSocketChannel clientListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from replica servers.
     * This channel is used for establishing (but not maintaining) peer-to-peer communication between
     * the primary addressing server and its backup replicas.
     */
    private ServerSocketChannel replicaListenerChannel;

    /**
     * Each AddressingServer process has a distinct id amongst its peers.
     * {@code pid} is used as a 'tie-breaker' during leader elections.
     */
    // CURRENTLY, PID IS TIED TO THE PEER SOCKET THAT THE PROCESS IS USING
            // TODO - implement method to generate and assign PID to replicas
    private long pid;

    /**
     * This address log is used by each AddressingServer to keep records
     * of all other addressing servers in the network.
     * <p>
     * All AddressingServer processes will have {@code ChatServerInfo.maxClientCount} = 0
     * </p>
     */
    private ArrayList<AddrServerInfo> replicaRecords;

    /**
     * Contains the set of all open channels between the Primary {@code AddressingServer} and its Replicas.
     * <p>
     * The implementation of our push protocol for consistency and leader election for fault tolerance is such that
     * every server in the data structure will be used during updates and elections, so a simple DS (array) is ideal.
     * </p>
     */
    private ArrayList<SocketChannel> replicaChannels;

    /**
     * Represents the leadership status of an addressing server.
     * Addressing server backups are `Passive Replicas` - they only sync data with
     * the primary server and do not handle requests until failover occurs.
     * <ul>
     *     <li>{@code PRIMARY} - This server is the Primary Addressing Server.</li>
     *     <li>{@code BACKUP} - This server is a Passive Replica for failover.</li>
     * </ul>
     */
    public enum ServerRole {
        PRIMARY, BACKUP
    }


    /**
     * Constructs an AddressingServer with an {@code AddrServerConfig} object storing its network details.
     * <p>
     * This constructor initializes a new, empty address log to track registered chat servers.
     * </p>
     *
     *
     */
    public AddressingServer() {
        this.config = new AddrServerConfig();
        try {
            this.networkManager = new AddrServerNetworkManager();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize network manager", e);
        }
        chatServerRegistry = new ChatServerRegistry();
        replicaRecords = new ArrayList<>();
        replicaChannels = new ArrayList<>();
    }



    // To be completed for Replication
//    /**
//     * Replaces the current address log with the one received from a remote server.
//     * <p>
//     * The new address log is transmitted over the network, deserialized, and then assigned by reference.
//     * This ensures that the server uses the most up-to-date address log information.
//     * </p>
//     *
//     * @param newAddressLog the address log object received from the network
//     */
//    public void updateAddressLogFromNetwork(Map<Long,ChatServerInfo> newAddressLog) {
//
//    }

    /**
     * FOR USE IN REPLICATION STAGE OF PROJECT
     * // TODO - Implement methods for all three channels in the 559Project - Replication Iteration
     *
     * @param channel
     */
//    private void setChatServerPort(ServerSocketChannel channel) {
//        try {
//            chatServerChannel.bind(new InetSocketAddress(0));
//            this.chatServerPort = ((InetSocketAddress) channel.getLocalAddress()).getPort();
//            System.out.println("Listening for Chat Servers on Port: " + this.chatServerPort);
//        } catch (IOException ioe) {
//            System.err.println("Chat server failed to bind: " + ioe.getMessage());
//        }
//    }


    /**
     * Generates a unique identifier for a chat server by incrementing the internal counter.
     *
     * @return a unique {@code Long} representing the chat server's ID.
     */
    private Long generatePID() {
        return ++this.pidCounter;
    }


    /**
     * Handles a client connection by sending the address of an active chat server.
     * <p>
     * This method calls {@code chatServerRegistry.getActiveHost()} to determine the active chat server address.
     * If an active host is found, its address (formatted as "hostAddress:clientPort") is sent to the client.
     * Otherwise, a message indicating no active host is available is sent.
     * </p>
     * <p>
     * Since the connection does not need to persist in either case, the message is sent, and the channel is closed.
     *</p>
     * @param newClientChannel the {@link SocketChannel} representing the client connection.
     * @throws IOException if an I/O error occurs while writing to or closing the channel.
     *
     * @see io.github.cpsc559.team16.addressingserver.ChatServerRegistry#getActiveHost()
     * For details about ChatServer host address retrieval
     */
    private void connectClientToHost(SocketChannel newClientChannel) throws IOException {
        Optional<String> activeHost = chatServerRegistry.getActiveHost();
        String message = activeHost.orElse("No active chat server available.");
        // Send network address to client as a string in the form ip-address:port
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            newClientChannel.write(buffer);
        }
        /* Clients do not keep a persistent connection to the addressing server
        * If they have an issue connecting to the ChatServer address they receive, they
        * simply initiate a new connection and request with the AddressingServer.
        */
        newClientChannel.close();
    }



    /**
     * Registers a chat server by retrieving its IP address from the provided SocketChannel
     * and passing that IP along with port numbers and maximum client count to create a chat server record.
     * <p>Responds to the chat server with an ACK in the form of it's newly assigned process ID.</p>
     *
     * @param newChatServerChannel the {@link SocketChannel} representing the incoming connection from a chat server.
     * @throws IOException if an I/O error occurs while obtaining the remote address.
     */
    private void registerChatServer(SocketChannel newChatServerChannel) throws IOException{
        // Retrieving the remote address from the SocketChannel and casting it to InetSocketAddress.
        InetSocketAddress remoteAddress = (InetSocketAddress) newChatServerChannel.getRemoteAddress();
        // Extracting the IP address of the chat server from the established connection.
        String chatServerHostAddr = remoteAddress.getAddress().getHostAddress();
        // TODO - Implement proper handshaking protocol for Replication iteration
        //  The chat server should be telling the AddressingServer what ports its using and its max connections.
        try {
            long serverPID = generatePID();
            this.chatServerRegistry.createChatServerRecord(serverPID, chatServerHostAddr, 2426, 2424, 2425, 3);

            ByteBuffer buffer = ByteBuffer.wrap(Long.toString(serverPID).getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                newChatServerChannel.write(buffer);
            }
        } catch (Exception e) {
            System.err.println("Failed during an attempt to register the chat server with address: " + chatServerHostAddr);
            e.printStackTrace();
        }
        newChatServerChannel.close();
    }

    /**
     * Registers a replica server by retrieving its IP address from the provided SocketChannel.
     *
     * @param newReplicaChannel the {@link SocketChannel} representing the incoming connection from a replica {@code AddressingServer}.
     * @throws IOException if an I/O error occurs while obtaining the remote address.
     */
    private void registerReplicaServer(SocketChannel newReplicaChannel) throws IOException {
        // Retrieving the remote address from the SocketChannel and casting it to InetSocketAddress.
        InetSocketAddress remoteAddress = (InetSocketAddress) newReplicaChannel.getRemoteAddress();
        // Extracting the IP address of the replica from the established connection.
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();

        this.replicaRecords.add(new AddrServerInfo(generatePID(), replicaHostAddr, 49800, 49801, 49802 ));
        String message = "ACK!";
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

        while (buffer.hasRemaining()) {
            newReplicaChannel.write(buffer);
        }
        newReplicaChannel.close();
    }


    /**
     * Initializes the AddressingServer network access by binding all required NIO channels.
     * These are "listening channels" used solely to monitor incoming connections.
     *
     * @throws IOException if binding any channel or opening the NIO selector fails
     */
    public void initializeChannels() {
        try {
            clientListenerChannel = networkManager.openListenerChannel(config.getClientPort());
            replicaListenerChannel = networkManager.openListenerChannel(config.getReplicaPort());
            chatServerListenerChannel = networkManager.openListenerChannel(config.getChatServerPort());
        } catch (IOException ioe) {
            System.err.println("Failed to establish AddressingServer connections: " + ioe.getMessage());
        }
    }

    public ServerSocketChannel getChatServerListenerChannel() {
        return chatServerListenerChannel;
    }

    public ServerSocketChannel getClientListenerChannel() {
        return clientListenerChannel;
    }

    public ServerSocketChannel getReplicaListenerChannel() {
        return replicaListenerChannel;
    }

    // Handles incoming client connections
    public void handleClientConnection(SocketChannel channel) {
        try {
            connectClientToHost(channel);
        } catch (IOException ioe) {
            System.err.println("Error sending chat server host address to client: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    public void handleChatServerRegistration(SocketChannel channel) {
        try {
            registerChatServer(channel);
            // TODO: Share the entire {@code addressLog} with chat servers when they register.
        } catch (IOException ioe) {
            System.err.println("Error retrieving chat server IP address: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /*
    Only replicas are given a persistent connection, so we must
    register the `newChannel` with the `selector` which will add a `SelectionKey`
    for that channel and monitor it until it is explicitly closed.
    */
    public void handleReplicaConnection(SocketChannel channel) {
        // TODO - May need to add conditional so only the primary activates this code
        try {
            networkManager.openPersistentChannel(channel);
            registerReplicaServer(channel);
        } catch (IOException ioe) {
            System.err.println("Error registering/opening persistent channel with peer server: " + ioe.getMessage());
            ioe.printStackTrace();
        }
        // The Primary `AddressingServer` maintains a persistent connection to replicas so that it
        // can push updates to them. Store the channel in an array.
        this.replicaChannels.add(channel);
    }

    /**
     * Begins the main event loop, listening for incoming connections on the
     * {@code config.clientPort}, {@code config.replicaPort} and {@code config.chatServerPort}
     *
     * @throws IOException if an I/O error occurs while selecting
     */
//    public void mainEventLoop() throws IOException {
//        while (true) {
//            // Any thread calling this method blocks until an event occurs on a channel registered with the `selector`.
//            selector.select();
//            /*
//             * When you register a channel with a selector, you get back a SelectionKey.
//             * Here, we iterate through all keys (channels) that are "ready" for an I/O operation.
//             * We know that at least one such key exists because of the preceding `selector.select()` method invocation.
//             */
//            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
//
//            while (keys.hasNext()) {      // Loop until there are no more keys (channels with I/O events).
//                SelectionKey key = keys.next();    // Retrieve a new key
//                keys.remove();                     // Remove the key used in the last iteration of this loop.
//
//                if (!key.isValid()) {               // skip current loop iteration
//                    continue;
//                }
//
//                /* Establishing a new connection. `isAcceptable` returns true if a ServerSocketChannel registered
//                 *  with `selector` is ready to accept (detected) a new connection. SocketChannel's registered with
//                 *   the `selector should not appear here.
//                 */
//                if (key.isAcceptable()) {
//                    /*
//                     * `listeningSC` is not a "new" channel - it is one of the ServerSocketChannels we registered
//                     *  with the selector in the `initializeChannels` method.
//                     */
//                    ServerSocketChannel listeningSC = (ServerSocketChannel) key.channel();
//                    // We use a `SocketChanel` for established connections. `newChannel.isAcceptable()` will ALWAYS return False
//                    SocketChannel newChannel = listeningSC.accept();
//                    // All requests on this channel will be sent to "non-blocking"
//                    newChannel.configureBlocking(false);
//                    // Using identity comparison to determine which channel this is and act as a switch statement:
//                    // Currently, the only connections that should be persistent are those between addressing server processes.
//                    if (listeningSC == clientListenerChannel) {
//                        try {
//                            connectClientToHost(newChannel);
//                        } catch (IOException ioe) {
//                            System.err.println("Error sending chat server host address to client: " + ioe.getMessage());
//                            ioe.printStackTrace();
//                        }
//                    } else if (listeningSC == replicaListenerChannel) {
//                        /*
//                         Only replicas are given a persistent connection, so we must
//                         register the `newChannel` with the `selector` which will add a `SelectionKey`
//                         for that channel and monitor it until it is explicitly closed.
//                         */
//                        newChannel.register(selector, SelectionKey.OP_READ); // SocketChannel set to persistent non-blocking I/O
//                        registerReplicaServer(newChannel);
//                        // The Primary `AddressingServer` maintains a persistent connection to replicas. Store the channel in an array.
//                        this.replicaChannels.add(newChannel);
//                    } else if (listeningSC == chatServerListenerChannel) {
//                        try {
//                            registerChatServer(newChannel);
//                            // TODO: Share the entire {@code addressLog} with chat servers when they register.
//                        } catch (IOException ioe) {
//                            System.err.println("Error retrieving chat server IP address: " + ioe.getMessage());
//                            ioe.printStackTrace();
//                        }
//                    }
//                }
//
//                /*
//                 * Only keys tied to channels that were registered with OP_READ
//                 * (as we are doing above with `newChannel`) will return True when invoking `isReadable()`.
//                 * Typically, only a SocketChannel would ever be registered with OP_READ.
//                 */
//                if (key.isReadable()) {
//                    /*
//                     Since replica servers are the only persistent connections, the logic contained
//                     in this conditional pertains solely to our replication implentation.
//                     */
//                }
//            }
//        }
//    }

    public void start() throws IOException {
        initializeChannels();
        // Create a dispatcher that uses this instance’s request handler methods
        AddrServerRequestDispatcher dispatcher = new AddrServerRequestDispatcher(this);
        networkManager.startEventLoop(dispatcher);
    }

    public static void main(String[] args) {
        /* This timeout is necessary for proper output in the new terminal window..
        * ... without it, the initial output to console occurs before the terminal is open.
        */
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {System.err.println(e.getMessage());}

        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        AddressingServer server = new AddressingServer();

        try {
            server.start();
        } catch (IOException ioe) {
            System.err.println("Error during AddressingServer main event loop, process halted.\nError message: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }


}
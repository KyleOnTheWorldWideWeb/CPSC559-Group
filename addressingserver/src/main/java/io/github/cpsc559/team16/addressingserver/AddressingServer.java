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
import io.github.cpsc559.team16.addressingserver.ServerInfo.ServerStatus;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;


public class AddressingServer {

    /**
     * The number of chat servers that have been registered.
     * Count begins at 1, not zero, because normals don't start at zero.
     */
    private long idCounter;

    /**
     * The network address of this Addressing Server.
     * The Primary Addressing Server posts this address to the
     * A-record in the static DNS.
     * TODO - Assign dynamically at runtime for replication.
     */
    private final String hostAddress;

    /**
     * The port used for client connections.
     * TODO - Assign dynamically at runtime for replication.
     * Clients use this port to connect and send messages.
     */
    private int clientPort;

    /**
     * The port reserved for peer-to-peer communication amongst the
     * TODO - Assign dynamically at runtime for replication.
     * Primary Addressing Server and it's backups.
     */
    private int replicaPort;

    /**
     * The port used for communicating with Chat Servers.
     * This should be the port the chat server used to register itself with the Addressing Server.
     */
    private int chatServerPort;

    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the AddressingServer to monitor multiple channels using a single thread.
     */
    private Selector selector;

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
    private long pid;

    /**
     * AddressingServer processes are either:
     * <ul>
     *     <li>PRIMARY - the leader process in charge of coordinating connections in the network.</li>
     *     <li>BACKUP - a `Passive Replica` receiving and retrieving updates.</li>
     * </ul>
     *
     */
    private String role;

    /**
     * A mapping of unique chat server IDs to their corresponding {@link ServerInfo} records.
     * <p>
     * This address log is used by the AddressingServer to track all registered chat servers
     * in the distributed network, enabling operations such as client assignment and server status monitoring.
     * </p>
     */
    private Map<Long, ServerInfo> addressLog;

    /**
     * This address log is used by each AddressingServer to track all other addressing servers in the network.
     * <p>
     * The implementation of our push protocol for consistency and leader election for fault tolerance is such that
     * every server in the data structure will be used during updates and elections, so a simple DS (array) is ideal.
     * </p>
     * All AddressingServer processes will have {@code ServerInfo.maxClientCount} = 0
     */
    private ArrayList<ServerInfo> replicaLog;

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
     * Constructs an AddressingServer with the specified host address, client port, peer port, and chat server port.
     * <p>
     * This constructor initializes a new, empty address log to track registered chat servers.
     * </p>
     *
     * @see #setAddressLog(Map) to assign an address log.
     *
     */
    public AddressingServer() {
        // Print all environment variables for debugging
        System.out.println("Addressing server network info dump:");
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));
        // Read the network address from the environment variable
        hostAddress = System.getenv("HOST_ADDRESS");
        clientPort = Integer.parseInt(System.getenv().getOrDefault("CLIENT_PORT", "49800"));
        replicaPort = Integer.parseInt(System.getenv().getOrDefault("REPLICA_PORT", "49801"));
        chatServerPort = Integer.parseInt(System.getenv().getOrDefault("CHAT_SERVER_PORT", "49802"));
        addressLog = new ConcurrentHashMap<>();
    }

    /**
     * Replaces the current address log with the provided new address log.
     * <p>
     * This method updates the AddressingServer's registry of chat servers by replacing its internal
     * address log with the new map passed as a parameter. This allows for dynamic reconfiguration or recovery
     * by resetting the address log.
     * </p>
     *
     * @param newAddressLog a {@code Map} of chat server IDs to {@link ServerInfo} objects representing the new address log.
     */
    public void setAddressLog(Map<Long, ServerInfo> newAddressLog) {
        this.addressLog = newAddressLog;
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
//    public void updateAddressLogFromNetwork(Map<Long,ServerInfo> newAddressLog) {
//
//    }


    /**
     * Generates a unique identifier for a chat server by incrementing the internal counter.
     *
     * @return a unique {@code Long} representing the chat server's ID.
     */
    private Long generateID() {
        return ++this.idCounter;
    }

    /**
     * Creates a record for a chat server by generating a unique ID and inserting its
     * ServerInfo record into the global addressLog.
     * <p>
     * This method generates a unique ID using {@code generateID()}, creates a new
     * {@link ServerInfo} object with the given parameters, and then inserts it into the addressLog. If a record
     * with the same ID already exists, it logs a warning message (and overwrites it).
     * </p>
     *
     * @param chatHostAddress the host address (IP or hostname) of the chat server.
     * @param chatClientPort  the port used for client connections.
     * @param chatPeerPort    the port used for peer-to-peer (gossip) communication.
     * @param maxClientCount  the maximum number of client connections allowed for this server.
     * @throws Exception if any error occurs during the registration process.
     */
    private void createChatServerRecord(String chatHostAddress, int chatClientPort,
                                    int chatPeerPort, int maxClientCount) throws Exception {
        try {
            Long serverID = generateID();
            ServerInfo newServer = new ServerInfo(serverID, chatHostAddress, chatPeerPort, chatClientPort, maxClientCount);
            // Check if the key already existed (should be null for a new key)
            ServerInfo previous = addressLog.put(serverID, newServer);
            if (previous != null) {
                System.err.println("WARNING: A server with ID " + serverID + " already existed. Overwriting existing entry.");
                // TODO: add logic to skip a range of ID values and re-insert the overwritten record - "previous"
            } else {
                System.out.println("Server registered successfully with ID " + serverID);
                // TODO: RM debug print statement
                debugPrintServer(newServer);
            }
        } catch (Exception e) {
            System.err.println("Error registering chat server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Deregisters a chat server by removing it from the {@code addressLog} containing all known chat servers.
     *
     * @param chatServerID the unique identifier of the chat server to be deregistered.
     * @return {@code true} if the server was successfully removed; {@code false} otherwise.
     */
    private boolean deregisterChatServer(Long chatServerID) {
        return addressLog.remove(chatServerID) != null;
    }

    /**
     * Retrieves the address of an active chat server.
     *
     * @return an {@link Optional} containing the active host address in the format "hostAddress:clientPort",
     *         or {@code Optional.empty()} if no active host exists.
     */
    private Optional<String> getActiveHost() {
        for (ServerInfo host : addressLog.values()) {
            if (host.getStatus() == ServerStatus.ACTIVE) {
                try {
                    host.addClient();
                } catch (ChatServerFullException csfe) {
                    System.err.printf("Chat Server ID #%d Full - attempting to find another.%n", host.getID());
                    continue; // Skip over this host and look for another ACTIVE chat server.
                }
                debugPrintServer(host);
                return Optional.of(host.getHostAddress() + ":" + host.getClientPort());
            }
        }
        return Optional.empty();
    }

    /**
     * Helper method to open and bind a ServerSocketChannel.
     *
     * @param port the port number to bind the channel to
     * @return the opened ServerSocketChannel
     * @throws IOException if an I/O error occurs while opening or binding the channel
     */
    private ServerSocketChannel openServerChannel(int port) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false); // NIO channel set to non-blocking
        channel.socket().bind(new InetSocketAddress(port));
        channel.register(selector, SelectionKey.OP_ACCEPT);
        return channel;
    }

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
     * Handles a client connection by sending the address of an active chat server.
     * <p>
     * This method calls {@link #getActiveHost()} to determine the active chat server address.
     * If an active host is found, its address (formatted as "hostAddress:clientPort") is sent to the client.
     * Otherwise, a message indicating no active host is available is sent.
     * </p>
     * <p>
     * Since the connection does not need to persist in either case, the message is sent, and the channel is closed.
     *</p>
     * @param newClientChannel the {@link SocketChannel} representing the client connection.
     * @throws IOException if an I/O error occurs while writing to or closing the channel.
     *
     * @see #getActiveHost() For details about ChatServer host address retrieval
     */
    private void connectClientToHost(SocketChannel newClientChannel) throws IOException {
        Optional<String> activeHost = getActiveHost();
        String message = activeHost.orElse("No active chat server available.");

        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

        while (buffer.hasRemaining()) {
            newClientChannel.write(buffer);
        }

        newClientChannel.close();
    }


    /**
     * Registers a chat server by retrieving its IP address from the provided SocketChannel
     * and passing that IP along with port numbers and maximum client count to create a chat server record.
     *
     * @param newChatServerChannel the {@link SocketChannel} representing the incoming connection from a chat server.
     * @throws IOException if an I/O error occurs while obtaining the remote address.
     */
    private void registerChatServer(SocketChannel newChatServerChannel) throws IOException{
        // Retrieving the remote address from the SocketChannel and casting it to InetSocketAddress.
        InetSocketAddress remoteAddress = (InetSocketAddress) newChatServerChannel.getRemoteAddress();
        // Extracting the IP address of the chat server from the established connection.
        String chatServerHostAddr = remoteAddress.getAddress().getHostAddress();
        try {
            createChatServerRecord(chatServerHostAddr, 2424, 2424, 200);
            // TODO - Implement proper handshaking protocol for Replication iteration
            String message = "ACK!";
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
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
     * Initializes the AddressingServer network access by binding all required NIO channels.
     * These are "listening channels" used solely to monitor incoming connections.
     *
     * @throws IOException if binding any channel or opening the NIO selector fails
     */
    public void initializeChannels() {
        try {
            selector = Selector.open();  // Initializing the selector used to access the multiplexed channels
            clientListenerChannel = openServerChannel(clientPort);
            replicaListenerChannel = openServerChannel(replicaPort);
            chatServerListenerChannel = openServerChannel(chatServerPort);
        } catch (IOException ioe) {
            System.err.println("Failed to establish Addressing Server connections: " + ioe.getMessage());
        }
    }

    /**
     * Begins the main event loop, listening for incoming connections on the
     * clientPort, replicaPort and chatServerPort.
     *
     * @throws IOException if an I/O error occurs while selecting
     */
    public void mainEventLoop() throws IOException {
        while (true) {
            selector.select();  // Thread blocks until events occur
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) { // exit loop and retrieve next key
                    continue;
                }

                // Establishing a new connection
                if (key.isAcceptable()) {
                    ServerSocketChannel listeningSC = (ServerSocketChannel) key.channel();
                    SocketChannel newChannel = listeningSC.accept();
                    newChannel.configureBlocking(false);
                    // Register newChannel for reading. Not needed for this iteration
                    newChannel.register(selector, SelectionKey.OP_READ);

                    // Using identity comparison to determine which channel this is and act as a switch statement:
                    // Currently, the only connections that should be persistent are those between addressing server processes.
                    if (listeningSC == clientListenerChannel) {
                        try {
                            connectClientToHost(newChannel);
                        } catch (IOException ioe) {
                            System.err.println("Error sending chat server host address to client: " + ioe.getMessage());
                            ioe.printStackTrace();
                        }
                    } else if (listeningSC == replicaListenerChannel) {
                        System.out.println("This functionality will be implemented for the Replication Iteration of the project.");
                    } else if (listeningSC == chatServerListenerChannel) {
                        try {
                            registerChatServer(newChannel);
                        } catch (IOException ioe) {
                            System.err.println("Error retrieving chat server IP address: " + ioe.getMessage());
                            ioe.printStackTrace();
                        }
                    }
                }

                // THIS CODE WILL BE NEEDED FOR PERSISTENT CONNECTIONS IN FUTURE ITERATIONS
//                if (key.isReadable()) {
//                    SocketChannel channel = (SocketChannel) key.channel();
//                    if (channel == chatServerDataChannel) {
//                        readChatServerData(channel);
//                    } else if (channel == clientDataChannel) {
//                        readClientData(channel);
//                    } else if (channel == replicaDataChannel) {
//                        readReplicaData(channel);
//                    }
//                }
            }
        }
    }

    public static void main(String[] args) {
        // This timeout is necessary for proper output in the new terminal window..
        // ... without it, the initial output to console occurs before the terminal is open
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {System.err.println(e.getMessage());}
        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
        AddressingServer server = new AddressingServer();
        server.initializeChannels();
        try {
            server.mainEventLoop();
        } catch (IOException ioe) {
            System.err.println("Error during main event loop: "+ ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /**
     * Logs the details of this ServerInfo object to the console for debugging purposes.
     */
    public void debugPrintServer(ServerInfo s) {
        System.out.println("---------- ServerInfo Debug ----------");
        System.out.printf("Host Address : %s%n", s.getHostAddress());
        System.out.printf("Client Port  : %d%n", s.getClientPort());
        System.out.printf("Peer Port    : %d%n", s.getPeerPort());
        System.out.printf("Client Count : %d%n", s.getClientCount());
        System.out.printf("Status       : %s%n", s.getStatus());
        System.out.println("--------------------------------------");
    }

    public void debugPrintAllServers() {
        for (ServerInfo s : addressLog.values()){
            System.out.println("---------- ServerInfo Debug ----------");
            System.out.printf("Host Address : %s%n", s.getHostAddress());
            System.out.printf("Client Port  : %d%n", s.getClientPort());
            System.out.printf("Peer Port    : %d%n", s.getPeerPort());
            System.out.printf("Client Count : %d%n", s.getClientCount());
            System.out.printf("Status       : %s%n", s.getStatus());
            System.out.println("--------------------------------------");
        }
    }


}
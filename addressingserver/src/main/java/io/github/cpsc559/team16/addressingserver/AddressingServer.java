package io.github.cpsc559.team16.addressingserver;
// External Dependencies
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel; // Used for conditionals that don't rely on non-null checks
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.MessageIDGenerator;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;



public class AddressingServer {

    /**
     * Indicates whether the server has been instructed to restart. Typically used after an orphaned or failed
     * AddressingServer has been instructed to terminate and reinitialize.
     * <p>
     * This flag is marked {@code volatile} to ensure visibility across threads. It can be
     * safely updated by any thread (e.g. the {@code AddrServerReadDispatcher}) and read by the main thread
     * to trigger a controlled in-process restart of the {@code AddressingServer}.
     * </p>
     * <p>
     * When {@code true}, the main event loop exits and the server is re-instantiated as a new process.
     * </p>
     */
    private volatile boolean restartRequested = false;

    /**
     * Sets the {@code restartRequested} flag to {@code true}.
     * <p>
     * This method is typically called when a shutdown or restart message is received from the network.
     * It signals the {@code AddressingServer} to exit its current event loop and reinitialize as a new replica.
     * </p>
     */
    public void requestRestart() {
        this.restartRequested = true;
    }

    /**
     * Checks whether the server has been flagged for restart.
     *
     * @return {@code true} if a restart has been requested, {@code false} otherwise.
     */
    public boolean isRestartRequested() {
        return this.restartRequested;
    }


    /**
     * The network configuration for this {@code AddressingServer} process.
     */
    private final AddrServerConfig config;

    public AddrServerConfig getConfig() {
        return config;
    }

    /**
     * Responsible for opening listener channels, managing the selector,
     * and dispatching accepted connections for the AddressingServer.
     */
    private final AddrServerNetworkManager networkManager;

    /**
     * The process responsible for managing {@code ChatServer} connections.
     */
    private final ChatServerRegistry chatServerRegistry;

    public ChatServerRegistry getChatServerRegistry() {
        return chatServerRegistry;
    }

    /**
     * The process responsible for managing {@code AddrServerRecord} records.
     */
    private final AddrServerRegistry addrServerRegistry;

    public AddrServerRegistry getAddrServerRegistry() {
        return addrServerRegistry;
    }

    /**
     * The process responsible for managing interactions between the Primary
     * {@code AddressingServer} and its replicas.
     */
    private final PeerManager peerManager;

    public PeerManager getPeerManager() {
        return peerManager;
    }

    /**
     * The process responsible for managing interactions between the
     * {@code AddressingServer} and {@code ChatServer}'s
     */
    private final ChatServerManager chatServerManager;

    public ChatServerManager getChatServerManager() {
        return chatServerManager;
    }

    /**
     * The process responsible for initiating elections and handling election messages from
     * {@code AddressingServer} peers.
     */
    private final LeaderElectionManager leaderElectionManager;

    public LeaderElectionManager getLeaderElectionManager() {
        return leaderElectionManager;
    }

    /**
     * The process responsible for handling ping messages and managing the ping timeout.
     */
    private final PingManager pingManager;

    public PingManager getPingManager() {
        return pingManager;
    }


    /**
     * The process responsible for managing interactions between the
     * {@code AddressingServer} and {@code ChatServer}'s
     */
    private final ClientManager clientManager;

    public ClientManager getClientManager() {
        return clientManager;
    }

    /**
     * The BroadcastManager is responsible for sending messages to all active channels.
     * It works directly with the live channel maps retrieved from PeerManager and ChatServerManager,
     * ensuring that any changes in those maps (such as channel additions or removals) are immediately visible.
     */
    private final BroadcastManager broadcastManager;

    /**
     * Retrieves the BroadcastManager instance for this AddressingServer.
     *
     * @return the BroadcastManager used to broadcast messages across channels.
     */
    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    /**
     * The ConnectionCleanupManager centralizes the logic for cleaning up and closing failed connections.
     * It holds references to both PeerManager and ChatServerManager so that any channel failures can be
     * promptly removed from the live channel maps and properly closed.
     */
    private final ConnectionCleanupManager cleanupManager;

    /**
     * Retrieves the ConnectionCleanupManager instance for this AddressingServer.
     *
     * @return the ConnectionCleanupManager used for cleaning up failed connections.
     */
    public ConnectionCleanupManager getCleanupManager() {
        return cleanupManager;
    }

    /**
     * The {@code MessageIDGenerator} instance used by this server to produce globally unique message IDs.
     * <p>
     * This generator ensures that every message requiring acknowledgment or ordering has a distinct identifier,
     * which is critical for maintaining consistency guarantees (e.g., in replication or event tracking).
     * </p>
     * <p>
     * The generator is initialized once per process and typically updated with the server's assigned PID
     * after registration, ensuring message IDs are globally unique across all AddressingServer processes.
     * </p>
     */
    private final MessageIDGenerator genMID;

    /**
     * Returns the {@link MessageIDGenerator} associated with this server.
     * <p>
     * This allows access to generate unique message IDs when constructing messages that
     * require tracking across replicas or clients.
     * </p>
     *
     * @return the server's {@code MessageIDGenerator} instance.
     */
    public MessageIDGenerator getMessageIDGenerator() {
        return this.genMID;
    }

    /**
     * The number of servers that have been registered cummulatively since network initialization - i.e. PID's are not recycled or reassigned.
     * Count begins at 1, not zero, because normals don't start at zero.
     */
    private Long pidCounter;

    /**
     * Sets the internal process ID counter to a specified value.
     * <p>
     * This method is typically called during replica promotion, where the newly
     * elected {@code PRIMARY} {@code AddressingServer} must resume assigning
     * unique process IDs without conflicting with existing ones. The provided
     * value should reflect the current highest PID observed across the network.
     * </p>
     *
     * @param currentNetworkMaxPID the highest known PID from all registered processes,
     *                             used to initialize the counter for new PID assignment.
     */
    public void setPidCounter(Long currentNetworkMaxPID) {
        this.pidCounter = currentNetworkMaxPID;
    }


    /**
     * Constructs an AddressingServer with an {@code AddrServerConfig} object storing its network details.
     * <p>
     * This constructor initializes a new, empty address log to track registered chat servers.
     * </p>
     */
    public AddressingServer() {
        this.config = new AddrServerConfig();
        this.addrServerRegistry = new AddrServerRegistry();
        this.peerManager = new PeerManager(addrServerRegistry);
        this.leaderElectionManager = new LeaderElectionManager(this);
        this.pingManager = new PingManager(this);
        this.chatServerRegistry = new ChatServerRegistry();
        this.chatServerManager = new ChatServerManager(chatServerRegistry);
        this.clientManager = new ClientManager(chatServerRegistry);
        this.cleanupManager = new ConnectionCleanupManager(peerManager, chatServerManager);
        try {
            this.networkManager = new AddrServerNetworkManager(cleanupManager, this.config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize network manager", e);
        }
        this.broadcastManager = new BroadcastManager(peerManager.getChannels(), chatServerManager.getChannels(), cleanupManager);
        this.pidCounter = 0L;
        this.genMID = new MessageIDGenerator();
    }

    /**
     * Generates a unique identifier for a chat server by incrementing the internal counter.
     *
     * @return a unique {@code Long} representing the chat server's ID.
     */
    public Long generatePID() {
        return ++this.pidCounter;
    }


    /**
     * Handles the full registration workflow for the first replica connecting to the primary {@code AddressingServer}.
     * <p>
     * This method:
     * <ol>
     *     <li>Registers the replica with the {@link PeerManager}, sending an acknowledgment (ACK) back to confirm registration.</li>
     *     <li>Sends all known chat server and address server records from the primary to the new replica
     *         using the {@link BroadcastManager} to synchronize state.</li>
     *     <li>Broadcasts the newly registered replica’s {@link AddrServerRecord} to all connected chat servers
     *         so they are aware of the updated network state.</li>
     *     <li>Prints the current address server registry to the console for debugging purposes.</li>
     * </ol>
     * <p>
     * If an {@link IOException} occurs at any point during this process, the associated connection is cleaned up
     * using the {@link ConnectionCleanupManager}.
     * </p>
     *
     * @param primaryPID   the process ID of the primary {@code AddressingServer}
     * @param newPID       the newly assigned process ID of the replica
     * @param channel      the raw {@link SocketChannel} associated with the replica
     * @param nioChannel   the {@link NIOMessageChannel} wrapper for communicating with the replica
     * @param record       the {@link AddrServerRecord} representing the newly registered replica
     */
    public void registerFirstReplicaServer(long primaryPID, long newPID,
                                               SocketChannel channel,
                                               NIOMessageChannel nioChannel,
                                               AddrServerRecord record) {
        try {
            this.peerManager.registerPeerSendACK(channel, nioChannel, primaryPID, newPID, record);
            this.broadcastManager.sendAllRecordsToProcess(primaryPID, nioChannel,
                    this.chatServerRegistry.getRecords(),
                    this.addrServerRegistry.getRecords());
            this.broadcastManager.broadcastAddrServerRecordToCS(primaryPID, record);
            this.addrServerRegistry.debugPrintAllServers();
        } catch (IOException ioe) {
            System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
            this.cleanupManager.cleanupPersistentConnection(channel, true);
        }
    }

    /**
     * Creates a {@link PendingEvent} representing the registration of a new replica with the primary {@code AddressingServer}.
     * <p>
     * This event sends an initial acknowledgment message to the new replica and tracks acknowledgments from
     * all currently registered replicas. The event is considered complete once all required ACKs are received.
     * </p>
     *
     * <p>
     * Once the event is complete, the following actions are performed in sequence:
     * </p>
     * <ul>
     *     <li>The new replica is formally added to the list of active NIOChannels in {@link PeerManager}.</li>
     *     <li>The new replica record is added to set of {@link AddrServerRecord} in {@link AddrServerRegistry}.</li>
     *     <li>The full set of chat server and addressing server records are sent to the new replica.</li>
     *     <li>The new replica's {@link AddrServerRecord} is broadcast to all connected chat servers.</li>
     *     <li>The updated address server registry is printed for debugging purposes.</li>
     * </ul>
     *
     * <p>
     * This method encapsulates the full coordination logic required to safely and consistently register
     * a replica across a distributed system, ensuring that all participating replicas are aware of the new node.
     * </p>
     *
     * @param primaryPID  the PID of the primary {@code AddressingServer}
     * @param newPID      the PID assigned to the newly registering replica
     * @param channel     the {@link SocketChannel} associated with the requester
     * @param nioChannel  the {@link NIOMessageChannel} used to communicate with the requester
     * @param record      the {@link AddrServerRecord} representing the new replica's state
     * @param recipients  a map containing all of the registered replica address server {@code NIOMessageChannel}'s and their PIDs
     * @return a {@link PendingEvent} configured to complete registration once all ACKs have been received
     */
    public PendingEvent createReplicaRegistrationEvent(long primaryPID, long newPID,
                                                       SocketChannel channel,
                                                       NIOMessageChannel nioChannel,
                                                       AddrServerRecord record,
                                                       Map<Long, NIOMessageChannel> recipients) {
        return new PendingEvent(
                AckMessage.replicaRegistered(primaryPID, newPID),
                recipients,
                3,
                nioChannel,
                () -> {  // THESE ARE ALL THE ACTIONS THAT WILL OCCUR ONCE AddressingServer STATES ARE CONSISTENT.
                    // All replicas have successfully replicated the update. Update state locally and continue with response.
                    this.peerManager.registerPeer(channel, nioChannel, record);
                    this.addrServerRegistry.debugPrintAllServers();
                    // An ACK containing the PID for the newly registered replica will already have been sent by the pendingEvent (see above).
                    // Once all ACKs received, send all the server records to the new replica
                    try {
                        this.broadcastManager.sendAllRecordsToProcess(primaryPID, nioChannel,
                                this.chatServerRegistry.getRecords(),
                                this.addrServerRegistry.getRecords());
                        this.broadcastManager.broadcastAddrServerRecordToCS(primaryPID, record);
                    } catch (IOException e) {
                        System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
                        this.cleanupManager.cleanupPersistentConnection(channel, true);
                    }
                }
        );
    }





    public void registerPrimaryAddrServer() throws IOException {
        Long pid = generatePID();
        config.setPID(pid); // Assign a process id to the primary
        genMID.setPID(pid); // Set the PID in the message ID generator (it needs this to generate unique network message ID's)
        System.out.println("PRIMARY AddressingServer .env host address: " + config.getHostAddress());
        try {
            System.out.println("PRIMARY AddressingServer runtime host address: " + InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            System.err.println("Error reading host address: " + e.getMessage());
        }
        addrServerRegistry.registerAddrServer(pid, config.getHostAddress(),
                config.getClientPort(), config.getReplicaPort(), config.getChatServerPort(), config.getRole());
    }

    /**
     * Attempts to register this replica AddressingServer with the PRIMARY AddressingServer.
     * <p>
     * This method attempts to open a network connection to the PRIMARY and send a registration
     * message. If the first attempt fails (e.g., the PRIMARY is temporarily unreachable), it retries
     * once after a brief pause.
     * </p>
     * <p>
     * If a connection is successfully established, the resulting {@link SocketChannel} is passed
     * to the {@code AddrServerNetworkManager} to be monitored via its {@code Selector}.
     * If both attempts fail, an error is logged, and the replica will not be registered.
     * </p>
     *
     * <p><strong>Retry behavior:</strong></p>
     * <ul>
     *   <li>Initial connection attempt is made immediately.</li>
     *   <li>If it fails, a second attempt is made after a short delay (500 ms).</li>
     *   <li>If both attempts fail, no further retries are attempted in this call.</li>
     * </ul>
     *
     * @see PeerManager#registerWithPrimary(String, int, int, int, int)
     * @see AddrServerNetworkManager#openPersistentChannel(SocketChannel)
     */
    public void registerReplicaAddrServer() throws IOException {
        Optional<SocketChannel> maybeChannel = peerManager.registerWithPrimary(
                System.getenv("HOST_ADDRESS"), 49801,
                config.getClientPort(), config.getReplicaPort(), config.getChatServerPort());

        if (maybeChannel.isEmpty()) {
            System.err.println("First attempt to register with primary failed. Retrying...");
            try {
                Thread.sleep(500); // Optional: brief delay before retry
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Retry sleep interrupted.");
            }

            maybeChannel = peerManager.registerWithPrimary(
                    System.getenv("HOST_ADDRESS"), 49801,
                    config.getClientPort(), config.getReplicaPort(), config.getChatServerPort());
        }

        if (maybeChannel.isPresent()) {
            SocketChannel channel = maybeChannel.get();
            try {
                networkManager.openPersistentChannel(channel);
            } catch (IOException ioe) {
                System.err.println("Error occurred while opening persistent channel to PRIMARY: " + ioe.getMessage());
            }
        } else {
            System.err.println("Failed to register with PRIMARY after 2 attempts.");
            // Optional: we can escalate the issue by starting a leader election.
        }
    }

    /**
     * Close all connections in the {@link AddrServerNetworkManager}.
     * Shut down the thread running the heartbeat pings in {@link PingManager}.
     * <p>
     * Throw a party with one candle! Play the cake song:
     * </p>
     * <p><a href="https://youtu.be/6ug6Bbc6diA?si=Empv4R9Wg6kdo1lD">"We do what we must, because we can"</a></p>
     */
    public void shutdown() {
            networkManager.closeAllConnections();
            pingManager.shutdown();
    }


    /**
     * Initializes the AddressingServer network access by binding all required NIO channels.
     * These are "listening channels" used solely to monitor incoming connections.
     * <p>
     * Starts the main event loop for this {@code AddressingServer} instance.
     * </p>
     */
    public void start() throws IOException {
        networkManager.openListenerChannels(config.getClientPort(),
                config.getReplicaPort(), config.getChatServerPort());

        networkManager.startEventLoop(new AddrServerReadDispatcher(this));

        pingManager.shutdown();
    }

    public static void main(String[] args) {
        /* This timeout is necessary for proper output in the new terminal window..
         * ... without it, the initial output to console occurs before the terminal is open.
         */
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        // Track whether this is the first time the loop has occurred.
        boolean firstIteration = true;

        while (true) {
            try {
                AddressingServer server = new AddressingServer();
                String initialServerRole = System.getenv("AS_ROLE");
                String currentServerRole = firstIteration ? initialServerRole : "REPLICA";

                if (currentServerRole.equals(Roles.PRIMARY)) {
                    System.out.println("Launching AddressingServer as PRIMARY");
                    server.registerPrimaryAddrServer();
                } else {
                    System.out.println("Launching AddressingServer as REPLICA");
                    // TODO - retrieve the address of the primary addressing server from the Domain A record
                    server.registerReplicaAddrServer();
                }
//                // TODO - A thread(s) must be spun up for this method call.
//                //  Since this invocation causes an infinite loop, the main thread will get hung up.
//                //  And the Replica won't enter the main event loop and function as intended.
//                //server.getPingManager().run();

                try {
                    server.start();         // blocks in main event loop of AddrServerNetworkManager
                } catch (IOException e) {
                    System.err.println("Server exited due to IOException: " + e.getMessage());
                }

                // If server didn’t request a restart and we are at this point -> exit the JVM
                if (!server.isRestartRequested()) {
                    System.out.println("Server exited normally. Shutting down.");
                    break;
                }

                System.out.println("Restart requested. Reinitializing as REPLICA...");
                server.shutdown();

            } catch (IOException ioe) {
                System.err.println("Error during AddressingServer main event loop, process halted.\nError message: " + ioe.getMessage());
                ioe.printStackTrace();
            }
            firstIteration = false; // All restarts become replicas
            try {
                TimeUnit.MILLISECONDS.sleep(500); // brief pause before retry
            } catch (InterruptedException ignored) {}

        }
//            AddressingServer server = new AddressingServer();
//        String serverRole = System.getenv("AS_ROLE");
//        if (serverRole != null) {
//            if (serverRole.equals("PRIMARY")) {
//                System.out.println("AS_ROLE is set to: " + serverRole);
//                // Server role is already set when the server is instantiated, using AddrServerConfig and environment variables
//                server.registerPrimaryAddrServer(); // Puts the addressing server into the AddrServerRegistry
//            } else {
//                System.out.println("AS_ROLE is set to: " + serverRole);
//                // TODO - retrieve the address of the primary addressing server from the Domain A record
//                server.registerReplicaAddrServer();
//                // TODO - A thread(s) must be spun up for this method call.
//                //  Since this invocation causes an infinite loop, the main thread will get hung up.
//                //  And the Replica won't enter the main event loop and function as intended.
//                //server.getPingManager().run();
//            }
//            try {
//                server.start();
//                while (true) {
//                    AddressingServer newServer = new AddressingServer();
//
//                    try {
//                        newServer.start();  // Blocks inside startEventLoop()
//                    } catch (IOException e) {
//                        System.err.println("Fatal error in event loop: " + e.getMessage());
//                    }
//
//                    if (!server.isRestartRequested()) {
//                        break; // Only restart if restart was explicitly requested
//                    }
//
//                    System.out.println("Restart requested. Restarting server as fresh replica.");
//                    // Optional: pause briefly
//                    try {
//                        TimeUnit.MILLISECONDS.sleep(500);
//                    } catch (InterruptedException ignored) {}
//                }
//            } catch (IOException ioe) {
//                System.err.println("Error during AddressingServer main event loop, process halted.\nError message: " + ioe.getMessage());
//                ioe.printStackTrace();
//                server.requestRestart();
//            }
//        }
        //}


    }
}
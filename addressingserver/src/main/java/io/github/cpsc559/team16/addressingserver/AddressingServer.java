package io.github.cpsc559.team16.addressingserver;
// External Dependencies
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;  // Used for conditionals that don't rely on non-null checks
import java.util.concurrent.TimeUnit;

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
    private final ChatServerManager chatServerManager;

    public ChatServerManager getChatServerManager() {
        return chatServerManager;
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


    public AddrServerConfig getConfig() {
        return config;
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
        this.addrServerRegistry = new AddrServerRegistry();
        this.peerManager = new PeerManager(addrServerRegistry);
        this.leaderElectionManager = new LeaderElectionManager(config, peerManager);
        this.pingManager = new PingManager(leaderElectionManager);
        this.chatServerRegistry = new ChatServerRegistry();
        chatServerManager = new ChatServerManager(chatServerRegistry);
        try {
            this.networkManager = new AddrServerNetworkManager(peerManager, chatServerManager);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize network manager", e);
        }
        this.pidCounter = 1L;
    }

    /**
     * Generates a unique identifier for a chat server by incrementing the internal counter.
     *
     * @return a unique {@code Long} representing the chat server's ID.
     */
    public Long generatePID() {
        return ++this.pidCounter;
    }



    public void registerPrimaryAddrServer() {
        Long pid = generatePID();
        config.setPID(pid); // Assign a process id to the primary
        System.out.println(config.getHostAddress());
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
    public void registerReplicaAddrServer() {
        Optional<SocketChannel> maybeChannel = peerManager.registerWithPrimary(
                "host.docker.internal", 49801,
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
                    "host.docker.internal", 49801,
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
     * Initializes the AddressingServer network access by binding all required NIO channels.
     * These are "listening channels" used solely to monitor incoming connections.
     *<p>
     * Starts the main event loop for this {@code AddressingServer} instance.
     *</p>
     *
     */
    public void start() throws IOException {
        networkManager.openListenerChannels(config.getClientPort(),
                config.getReplicaPort(), config.getChatServerPort());
        networkManager.startEventLoop(new AddrServerReadDispatcher(this));
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
        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        AddressingServer server = new AddressingServer();

        String serverRole = System.getenv("AS_ROLE");

        if (serverRole != null) {
            if (serverRole.equals("PRIMARY")) {
                System.out.println("AS_ROLE is set to: " + serverRole);
                // Server role is already set when the server is instantiated, using AddrServerConfig and environment variables
                server.registerPrimaryAddrServer(); // Puts the addressing server into the AddrServerRegistry
            } else {
                System.out.println("AS_ROLE is set to: " + serverRole);
                // TODO - retrieve the address of the primary addressing server from the Domain A record
                server.registerReplicaAddrServer();
            }
        }
        try {
            server.start();
        } catch (IOException ioe) {
            System.err.println("Error during AddressingServer main event loop, process halted.\nError message: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }


}
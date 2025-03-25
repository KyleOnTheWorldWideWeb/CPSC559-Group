package io.github.cpsc559.team16.addressingserver;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.messaging.PingMessage;

/**
 * <h1>PingManager</h1>
 * Responsible for managing heartbeat messages between the primary and replica servers
 * to detect failures and maintain leader availability in a distributed system.
 * <p>
 * The primary server sends periodic pings to all replicas to signal its presence.
 * Replicas monitor these pings and maintain a suspicion counter. If multiple consecutive 
 * pings are missed, the replica assumes the primary has failed and initiates a leader election 
 * via {@link LeaderElectionManager}.
 * </p>
 *
 * <h2>Functionality:</h2>
 * <ul>
 *   <li><b>Primary Role:</b> Periodically broadcasts ping messages to all replicas.</li>
 *   <li><b>Replica Role:</b> Monitors incoming pings and increases a suspicion counter for missed pings.</li>
 *   <li><b>Failure Detection:</b> If the suspicion counter reaches a threshold, an election is initiated.</li>
 *   <li><b>Graceful Shutdown:</b> Ensures clean termination of scheduled tasks.</li>
 * </ul>
 *
 * <h2>Threading Model:</h2>
 * <p>
 * This class utilizes a {@link ScheduledExecutorService} for efficient scheduling of heartbeat
 * messages and timeout checks. This design prevents blocking operations and ensures a responsive
 * failure detection mechanism.
 * </p>
 *
 * <h2>Leader Election:</h2>
 * <p>
 * When a replica detects a primary failure, it initiates a leader election by invoking
 * {@link LeaderElectionManager#initiateElection()}. The election process follows the Bully Algorithm
 * </p>
 *
 */
public class PingManager implements Runnable {

    private static final Logger logger = Logger.getLogger(PingManager.class.getName());

    /** Executor for scheduled tasks */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /** Flag used to mark whether the process should shut down */
    private volatile boolean shutdown;

    /** Configuration settings */
    private final AddrServerConfig config;

    /** Leader election handler */
    private final LeaderElectionManager leaderElectionManager;

    /** Peer manager for handling connections */
    private final PeerManager peerManager;

    /** Tracks whether a ping was received since the last reset */
    private final AtomicBoolean receivedPing = new AtomicBoolean(false);

    /** Ping timeout duration (milliseconds) */
    private int pingTimeout = 3000;

    /**
     * Constructs a new {@link PingManager} instance.
     *
     * @param config The {@link AddrServerConfig} instance for configuration settings.
     * @param leaderElectionManager The {@link LeaderElectionManager} instance for leader elections.
     * @param peerManager The {@link PeerManager} instance for managing peer connections.
     */
    public PingManager(AddressingServer server) {
        this.config = server.getConfig();
        this.leaderElectionManager = server.getLeaderElectionManager();
        this.peerManager = server.getPeerManager();
    }

    /**
     * Sets the timeout duration for receiving pings.
     *
     * @param milliseconds The timeout in milliseconds.
     */
    public void setPingTimeout(int milliseconds) {
        pingTimeout = milliseconds;
    }

    /**
     * Marks the server as having received a ping.
     */
    public void markPingReceived() {
        receivedPing.set(true);
    }

    /**
     * Processes an incoming ping message.
     *
     * @param ping The {@link PingMessage} to process.
     */
    public void processPing(Long senderPID) {

        long leaderPID = leaderElectionManager.getLeaderPID();

        if (senderPID == leaderPID) {
            markPingReceived();
        }
    }

    /**
     * Determines if this server is the primary.
     *
     * @return {@code true} if the server is primary, {@code false} otherwise.
     */
    private boolean isPrimary() {
        return config.getRole() == ServerRole.PRIMARY;
    }

    /**
     * Retrieves the PID of this server.
     *
     * @return The server's process ID.
     */
    private Long getSelfPID() {
        return config.getPID();
    }

    /**
     * Sends a ping to all replicas.
     */
    private void pingReplicas() {
        logger.info("Primary sending ping to replicas...");
        peerManager.broadcast(new PingMessage(getSelfPID()));
    }

    /**
     * Checks if a ping was received within the timeout period.
     * If no ping was received, an election is initiated.
     */
    private void checkPing() {
        if (receivedPing.getAndSet(false)) {
            logger.fine("Ping received. Resetting flag.");
        } else {
            logger.warning("Ping timeout! Initiating leader election.");
            leaderElectionManager.initiateElection();
        }
    }

    /**
     * Starts the pinging mechanism using a scheduled executor.
     */
    @Override
    public void run() {
        shutdown = false;

        if (isPrimary()) {
            // Schedule primary to send pings at fixed intervals
            scheduler.scheduleAtFixedRate(this::pingReplicas, 0, pingTimeout, TimeUnit.MILLISECONDS);
        } else {
            // Schedule replicas to check for pings at fixed intervals
            scheduler.scheduleAtFixedRate(this::checkPing, pingTimeout, pingTimeout, TimeUnit.MILLISECONDS);
        }

        logger.info("PingManager started.");
    }

    /**
     * Shuts down the ping manager gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down PingManager...");
        shutdown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.severe("PingManager shutdown interrupted!");
            Thread.currentThread().interrupt();
        }
    }
}
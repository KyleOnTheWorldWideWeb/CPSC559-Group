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
 * Manages periodic pings between the primary and replica servers to detect failures.
 * <p>
 * If a replica stops receiving pings, it initiates a leader election. The primary
 * periodically pings all replicas to signal its availability.
 * </p>
 *
 * <h2>Functionality:</h2>
 * <ul>
 *   <li>The primary server sends pings to all replicas at a fixed interval.</li>
 *   <li>Replicas wait for a ping within a timeout period.</li>
 *   <li>If no ping is received, an election process starts via {@link LeaderElectionManager}.</li>
 * </ul>
 *
 * <h3>Threading Model:</h3>
 * <p>
 * Uses a {@link ScheduledExecutorService} for periodic pinging and timeout handling.
 * This avoids blocking the thread with {@code Thread.sleep()}.
 * </p>
 *
 * @author YourName
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
    public PingManager(AddrServerConfig config, LeaderElectionManager leaderElectionManager, PeerManager peerManager) {
        this.config = config;
        this.leaderElectionManager = leaderElectionManager;
        this.peerManager = peerManager;
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
    public void processPing(PingMessage ping) {

        long senderPID = ping.getSenderPID();
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
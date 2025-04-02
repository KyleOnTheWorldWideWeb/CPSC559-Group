package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRole;

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

    /** Reference to the AddressingServer instance */
    private final AddressingServer server;

    /** Configuration settings */
    private final AddrServerConfig config;

    /** Leader election handler */
    private final LeaderElectionManager leaderElectionManager;

    /** Ping timeout duration (milliseconds) */
    private int pingTimeout = 3000;

    /** Flag to terminate the ping manager */
    private boolean terminate;

    /**
     * Constructs a new {@link PingManager} instance.
     *
     * @param config The {@link AddrServerConfig} instance for configuration settings.
     * @param leaderElectionManager The {@link LeaderElectionManager} instance for leader elections.
     * @param peerManager The {@link PeerManager} instance for managing peer connections.
     */
    public PingManager(AddressingServer server) {
        this.server = server;
        this.config = server.getConfig();
        this.leaderElectionManager = server.getLeaderElectionManager();
    }

    /**
     * Shuts down the ping manager gracefully.
     */
    public void shutdown() {
        terminate = true;
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
     * Determines if this server is the primary.
     *
     * @return {@code true} if the server is primary, {@code false} otherwise.
     */
    private boolean isPrimary() {
        return config.getRole() == ServerRole.PRIMARY;
    }

    /**
     * Starts the pinging process.
     */
    @Override
    public void run() {

        /** Map for storing channels for pinging peers */
        HashMap<SocketChannel, Long> channelPIDs;

        /** Map for storing channels for pinging peers */
        HashMap<Long, Date> lastPingFromPID = new HashMap<>();

        terminate = false;

        while (!terminate) {

            if (!isPrimary() && !terminate) {
                try (Selector selector = Selector.open()) {

                    /** Map for storing channels for pinging peers */
                    channelPIDs = new HashMap<>();

                    // Open and configure each SocketChannel
                    for (AddrServerRecord peer : server.getAddrServerRegistry().getRecords().values()) {
                        SocketChannel channel = SocketChannel.open();
                        channel.configureBlocking(false);
                        channel.connect(new InetSocketAddress(peer.getHostAddress(), 5050));
                        // Register for connection completion and read events
                        channel.register(selector, SelectionKey.OP_READ);
                        // Store the last ping time for this PID
                        lastPingFromPID.putIfAbsent(peer.getPID(), new Date());
                        // Store the channel for this PID
                        channelPIDs.put(channel, peer.getPID());
                    }
                        
                    // Wait for events with a timeout (in milliseconds)
                    int readyChannels = selector.select(5000);

                    if (readyChannels == 0) {
                        // Optionally, you can perform periodic actions here
                        continue;
                    }

                    // Check for ping responses from channels and update last ping time
                    Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();

                        if (key.isReadable()) {
                            SocketChannel channel = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            int bytesRead = channel.read(buffer);
                            if (bytesRead > 0) {
                                lastPingFromPID.put(channelPIDs.get(channel), new Date());
                                String response = new String(buffer.array(), 0, bytesRead);
                                System.out.println("Received response: " + response);
                            }
                        }
                    }

                    // Check for each PID if a ping was received within the timeout
                    server.getAddrServerRegistry().getRecords().values().forEach(peer -> {
                        Date lastPing = lastPingFromPID.get(peer.getPID());
                        Date now = new Date();
                        long diff = now.getTime() - lastPing.getTime();
                        if (diff > pingTimeout) {

                            // Tell peers (is this necessary?)
                            AddrServerRecord record = server.getAddrServerRegistry().getRecords().get(peer.getPID());
                            if (record != null) {
                                record.setCrashSuspicious(true);
                                server.getPeerManager().broadcastAddrServerRecord(peer.getPID(), record);
                            }

                            // Deregister from own registry
                            server.getAddrServerRegistry().removeRecordByKey(peer.getPID()); // Deregister the server
                            
                            // If the primary server has failed, initiate an election
                            if (peer.getRole() == ServerRole.PRIMARY) {
                                System.out.println("Primary server has failed. Initiating leader election...");
                                leaderElectionManager.initiateElection();
                            }
                        }
                    });

                } catch (IOException e) {
                    System.err.println("Error while pinging peers: " + e.getMessage());
                }
            }
        }
    }
}
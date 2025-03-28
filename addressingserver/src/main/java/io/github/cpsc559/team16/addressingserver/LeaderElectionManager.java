package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;

import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.ElectionMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

/**
 * Manages the leader election process for an Addressing Server using the Bully Algorithm.
 * <p>
 * This class is responsible for handling leader election messages, determining the leader, 
 * and ensuring fault tolerance by re-electing a leader if necessary. 
 * </p>
 * <p>
 * The election process follows these steps:
 * </p>
 * <ol>
 *     <li>Identify the highest available PID among peers and self.</li>
 *     <li>If a process detects a failure in the leader, it initiates an election.</li>
 *     <li>Election messages are sent to all peers with a higher PID.</li>
 *     <li>If no higher PID responds, the process declares itself the leader.</li>
 *     <li>If a higher PID responds, it waits for a leader announcement.</li>
 * </ol>
 */
public class LeaderElectionManager {

    /** Configuration instance for accessing Addressing Server settings */
    private final AddrServerConfig config;

    /** Manages peer connections for communication during elections */
    private final PeerManager peerManager;

    /** Stores the current leader's PID */
    private Long leaderPID;

    /** Flag indicating whether an election is currently running */
    private boolean running;

    /** Timeout (in milliseconds) to wait for a "Bully" response before assuming leadership */
    private int bullyResponseTimeout;

    /** Timeout (in milliseconds) to wait for a leader announcement before re-initiating an election */
    private int leaderAnnouncementTimeout;

    /** Flags used to track election responses */
    private boolean bullyResponseReceived = false;
    private boolean leaderAnnouncementReceived = false;

    /** Flag indicating whether an election is in progress */
    private boolean midElection = false;

    /**
     * Retrieves the PID of the current leader.
     *
     * @return The leader's PID.
     */
    public long getLeaderPID() {
        return leaderPID;
    }

    /**
     * Retrieves the current election status.
     * 
     * @return True if an election is in progress, false otherwise.
     */
    public boolean isMidElection() {
        return midElection;
    }

    /**
     * Constructs a LeaderElectionManager and determines the initial leader.
     *
     * @param config The Addressing Server configuration.
     * @param peerManager Manages peer server communication.
     */
    public LeaderElectionManager(AddressingServer server) {
        this.config = server.getConfig();
        this.peerManager = server.getPeerManager();
        this.running = false;

        // Initialize leaderPID to the highest PID among self and peers
        leaderPID = config.getPID();
        for (NIOMessageChannel peerChannel : getPeerChannels()) {
            Long peerPID = peerChannel.getServerPID();
            if (peerPID > leaderPID) {
                leaderPID = peerPID;
            }
        }
    }

    /**
     * Processes an incoming election-related message.
     * Depending on the message payload, it delegates to the appropriate handler:
     * <ul>
     *     <li>"Election" → {@link #handleElection(Long, NIOMessageChannel)}</li>
     *     <li>"Bully" → {@link #handleBully()}</li>
     *     <li>"Leader" → {@link #handleLeader(Long)}</li>
     * </ul>
     *
     * @param channel The {@link SocketChannel} that received the message.
     * @param nioChannel The {@link NIOMessageChannel} used for message decoding and encoding.
     * @param message The parsed {@link BaseAddrServerMessage} containing the election details.
     */
    public void processElectionMessage(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        long senderPID = message.getSenderPID();
        String payload = (String) message.getPayload();

        switch (payload) {
            case "Election" -> handleElection(senderPID, nioChannel);
            case "Leader" -> handleLeader(senderPID);
            case "Bully" -> handleBully();
            default -> System.err.println("Received unknown election message payload: " + payload);
        }
    }

    /**
     * Handles an "Election" message, responding only if the sender has a lower PID.
     * <p>
     * If this process has a higher PID, it sends a "Bully" message back and optionally 
     * starts its own election if one is not already in progress.
     * </p>
     *
     * @param senderPID The PID of the process that sent the election message.
     * @param peerChannel The {@link NIOMessageChannel} of the sender.
     */
    private void handleElection(Long senderPID, NIOMessageChannel peerChannel) {
        midElection = true;

        if (senderPID < getSelfPID()) {  // Only respond if we have a higher PID
            bully(peerChannel);  // Send a "Bully" message to the sender
            if (!running) {
                initiateElection();  // Start an election if not already in progress
            }
        }
    }

    /**
     * Handles a "Bully" message, marking that a higher PID exists and preventing self-declaration as leader.
     */
    private void handleBully() {
        bullyResponseReceived = true;
    }

    /**
     * Handles a "Leader" message, updating the known leader and stopping the election process.
     *
     * @param senderPID The PID of the announced leader.
     */
    private void handleLeader(Long senderPID) {
        leaderAnnouncementReceived = true;
        running = false;
        leaderPID = senderPID;
        midElection = false;
    }

    /**
     * Retrieves a collection of all connected peers' message channels.
     *
     * @return A collection of {@link NIOMessageChannel} objects representing connected peers.
     */
    private Collection<NIOMessageChannel> getPeerChannels() {
        return peerManager.getChannels().values();
    }

    /**
     * Retrieves the PID of the current process.
     *
     * @return The PID of this Addressing Server.
     */
    private long getSelfPID() {
        return config.getPID();
    }

    /**
     * Initiates the leader election process.
     * <p>
     * If a higher PID exists, election messages are sent to them.
     * If no higher PID responds, this process declares itself the leader.
     * </p>
     */
    public void initiateElection() {
        try {

            midElection = true;
            if (!running) {
                running = true;
                bullyResponseReceived = false;
                leaderAnnouncementReceived = false;

                boolean higherExists = false;  // Track if a higher PID exists

                // Send ELECTION messages to all peers with a higher PID
                for (NIOMessageChannel peerChannel : getPeerChannels()) {
                    if (peerChannel.getServerPID() > getSelfPID()) {
                        higherExists = true;
                        sendTo(generateElectionMessage(), peerChannel);
                    }
                }

                // If no higher PID exists, declare self as leader
                if (!higherExists) {
                    declareSelfLeader();
                } else {
                    // Wait for a "Bully" response; if none, declare self as leader
                    Thread.sleep(bullyResponseTimeout);
                    if (!bullyResponseReceived) {
                        declareSelfLeader();
                    } else {
                        // Wait for a "Leader" announcement; if none, restart election
                        Thread.sleep(leaderAnnouncementTimeout);
                        if (!leaderAnnouncementReceived) {
                            initiateElection();
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for response during election.");
        }
    }

    /**
     * Sends a "Bully" message to a peer to challenge their election.
     *
     * @param peerChannel The {@link NIOMessageChannel} of the peer being challenged.
     */
    public void bully(NIOMessageChannel peerChannel) {
        sendTo(generateBullyMessage(), peerChannel);
    }

    /**
     * Declares this process as the new leader and notifies all peers.
     */
    public void declareSelfLeader() {
        BaseAddrServerMessage leaderMessage = generateLeaderMessage();
        for (NIOMessageChannel peerChannel : getPeerChannels()) {
            sendTo(leaderMessage, peerChannel);
        }
        midElection = false;
    }

    // Methods to generate election-related messages
    public BaseAddrServerMessage generateElectionMessage() { return ElectionMessage.election(getSelfPID()); }
    public BaseAddrServerMessage generateBullyMessage() { return ElectionMessage.bully(getSelfPID()); }
    public BaseAddrServerMessage generateLeaderMessage() { return ElectionMessage.leader(getSelfPID()); }

    /**
     * Sends a message to a peer's channel.
     *
     * @param message The message to send.
     * @param peerChannel The peer's {@link NIOMessageChannel}.
     */
    public void sendTo(BaseAddrServerMessage message, NIOMessageChannel peerChannel) {
        try {
            peerChannel.sendMessage(message.toJson());
        } catch (IOException e) {
            System.out.println("Failed to send '" + message.getPayload() + "' message to peer with PID " + peerChannel.getServerPID());
        }
    }
}

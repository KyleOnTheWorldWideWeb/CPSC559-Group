package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;

import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.ElectionMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

public class LeaderElectionManager {

    // Configuration, registry, and peer manager
    private final AddrServerConfig config;
    private final PeerManager peerManager;

    // PID of leader
    private Long leaderPID;
    public long getLeaderPID() {
        return leaderPID;
    }

    // Leader election algorithm running flag
    private boolean running;

    // Timeout values (milliseconds)
    private int bullyResponseTimeout;
    private int leaderAnnouncementTimeout;

    // Flags to indicate whether certain messages have been received, used with the above timeouts
    private boolean bullyResponseReceived = false;
    private boolean leaderAnnouncementReceived = false;

    /**
     * Constructor for LeaderElectionManager.
     * 
     * @param config AddrServerConfig instance.
     * @param registry AddrServerRegistry instance.
     */
    public LeaderElectionManager(AddrServerConfig config, PeerManager peerManager) {

        // Set configuration and peer manager
        this.config = config;
        this.peerManager = peerManager;

        // Set running to false initially
        running = false;

        // Set leaderPID to highest PID among self and peers
        leaderPID = config.getPID();
        for (NIOMessageChannel peerChannel : getPeerChannels()) {

            Long peerPID = peerChannel.getServerPID();

            if (peerPID > leaderPID) {
                leaderPID = peerPID;
            }
        }
    }

    /**
     * Process an election message and handle depending on payload (which will be "Election", "Bully", or "Leader")
     * 
     * @param channel The {@link SocketChannel} that was used to receive the message.
     * @param nioChannel The {@link NIOMessageChannel} that was used to decode and encode the message.
     * @param message The {@link BaseAddrServerMessage} that was received.
     * 
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
     * Behaviour for handling an election message from a peer.
     * 
     * @param senderPID PID of sender.
     * @param peerChannel {@link NIOMessageChannel} of sender.
     */
    private void handleElection(Long senderPID, NIOMessageChannel peerChannel) {
        
        if (senderPID < getSelfPID()) { // If sender PID is lower than own PID

            bully(peerChannel); // Send BULLY message to sender

            if (!running) initiateElection(); // If not already running, initiate election
        }
    }

    /**
     * Behaviour for handling a bully message from a peer.
     */
    private void handleBully() {
        bullyResponseReceived = true;
    }

    /**
     * Notify this process of a leader message from a peer.
     * 
     * @param senderPID PID of sender.
     */
    private void handleLeader(Long senderPID) {
        leaderAnnouncementReceived = true;

        running = false;
        leaderPID = senderPID;
    }

    /**
     * Get all peer {@link NIOMessageChannel} channels.
     * 
     * @return Collection containing each peer's {@link NIOMessageChannel}.
     */
    private Collection<NIOMessageChannel> getPeerChannels() {
        return peerManager.getPeerChannels().values();
    }
    
    /**
     * Get own PID.
     * 
     * @return PID of self.
     */
    private long getSelfPID() {
        return config.getPID();
    }

    /**
     * Initiate a leader election.
     */
    public void initiateElection() {
        try {
            if (!running) {
                running = true; // Set flag
                bullyResponseReceived = false; // Reset flag for this election (as no bully has been received so far for this election)
                leaderAnnouncementReceived = false; // Reset flag for this election (as no leader has been received so far for this election)
    
                boolean higherExists = false; // Flag used to immediately declare leader if no higher PID is found
    
                // Loop through all peers
                for (NIOMessageChannel peerChannel : getPeerChannels()) {
                    if (peerChannel.getServerPID() > getSelfPID()) { // If peer PID is higher than own PID
                        higherExists = true; // Set flag to true
                        sendTo(generateElectionMessage(), peerChannel); // Send ELECTION message to peer
                    }
                }
                
                // If self has the highest PID, immediately declare self as leader
                if (!higherExists) declareSelfLeader();
    
                // Otherwise, wait to hear response
                else {
                    // If no BULLY message is received after timeout, declare self as leader
                    Thread.sleep(bullyResponseTimeout);
                    if (!bullyResponseReceived) declareSelfLeader();
    
                    // If a BULLY message is received, wait to hear following LEADER message
                    else {
                        running = false;
                        // If no LEADER message is received after timeout, initiate election again
                        Thread.sleep(leaderAnnouncementTimeout);
                        if (!leaderAnnouncementReceived) initiateElection();
                    }
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for response during election.");
        }

        bullyResponseReceived = false; // Reset flag for next election
        leaderAnnouncementReceived = false; // Reset flag for next election
    }

    /**
     * Bully a process.
     * 
     * @param peerPID PID of peer to bully.
     */
    public void bully(NIOMessageChannel peerChannel) {
        sendTo(generateBullyMessage(), peerChannel);
    }

    /**
     * Send a LEADER message to all peers, announcing self as leader.
     */
    public void declareSelfLeader() {

        BaseAddrServerMessage leaderMessage = generateLeaderMessage();

        for (NIOMessageChannel peerChannel : getPeerChannels()) {
            sendTo(leaderMessage, peerChannel);
        }
    }

    /**
     * Generate an election message, announcing self as running for leader.
     * 
     * @return {@code ElectionMessage} with payload "Election".
     */
    public BaseAddrServerMessage generateElectionMessage() {
        return ElectionMessage.election(getSelfPID());
    }

    /**
     * Generate a bully message to respond to an election message from a lower PID.
     * 
     * @return {@code ElectionMessage} with payload "Bully".
     */
    public BaseAddrServerMessage generateBullyMessage() {
        return ElectionMessage.bully(getSelfPID());
    }
    
    /**
     * Generate a message that announces self as leader.
     * 
     * @return {@code ElectionMessage} with payload "Leader".
     */
    public BaseAddrServerMessage generateLeaderMessage() {
        return ElectionMessage.leader(getSelfPID());
    }

    /**
     * Send a message to a peer.
     * 
     * @param message Message to send.
     * @param peerPID {@link NIOMessageChannel} of peer to send message to.
     */
    public void sendTo(BaseAddrServerMessage message, NIOMessageChannel peerChannel) {
        try {
            peerChannel.sendMessage(message.toJson());
        } catch (IOException e) {
            String payload = (String) message.getPayload();
            Long peerPID = peerChannel.getServerPID();
            System.out.println("Failed to send " + payload + " message to peer with PID " + peerPID);
        }
    }
}

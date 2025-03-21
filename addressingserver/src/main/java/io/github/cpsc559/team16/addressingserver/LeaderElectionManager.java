package io.github.cpsc559.team16.addressingserver;

import java.util.Map;
import java.util.Set;

import io.github.cpsc559.team16.common.utilities.ServerServerMessage;

public class LeaderElectionManager {

    // Flag to indicate whether the leader election manager should shutdown
    private boolean shutdown = false;

    // Configuration, registry, and peer manager
    private final AddrServerConfig config;
    private final AddrServerRegistry registry;

    // Leader election algorithm variables
    private Long leaderPID;
    private boolean running;

    // Timeout values (milliseconds)
    private int leaderPingTimeout;
    private int bullyResponseTimeout;
    private int leaderAnnouncementTimeout;

    // Flags to indicate whether certain messages have been received, used with the above timeouts
    private boolean leaderPingReceived = false;
    private boolean bullyResponseReceived = false;
    private boolean leaderAnnouncementReceived = false;

    /*
     * Constructor for LeaderElectionManager.
     * 
     * @param peerManager PeerManager instance.
     * @param config AddrServerConfig instance.
     * @param registry AddrServerRegistry instance.
     */
    public LeaderElectionManager(AddrServerConfig config, AddrServerRegistry registry) {

        // Set configuration, registry, and peer manager
        this.config = config;
        this.registry = registry;

        // Set running to false initially
        running = false;

        // Set leaderPID to highest PID among self and peers
        leaderPID = config.getPID();
        for (Long peerPID : getPeers().keySet()) {
            if (peerPID > leaderPID) {
                leaderPID = peerPID;
            }
        }

        // Loop pinging the leader until shutdown
        while (!shutdown) {
            pingLeader();
        }
    }

    /*
     * Notify this process of an election message from a peer.
     * 
     * @param senderPID PID of sender.
     */
    public void receiveElectionMessage(Long senderPID) {
        if (senderPID < getSelfPID()) { // If sender PID is lower than own PID

            bully(senderPID); // Send BULLY message to sender

            if (!running) initiateElection(); // If not already running, initiate election
        }
    }

    /*
     * Notify this process of a bully message from a peer.
     */
    public void receiveBullyMessage() {
        bullyResponseReceived = true;
    }

    /*
     * Notify this process of a leader message from a peer.
     * 
     * @param senderPID PID of sender.
     */
    public void receiveLeaderMessage(Long senderPID) {
        leaderAnnouncementReceived = true;

        running = false;
        leaderPID = senderPID;
    }

    /*
     * Notify this process of a ping from the leader.
     */
    public void receiveLeaderPing() {
        leaderPingReceived = true;
    }

    /*
     * Shutdown the leader election manager.
     */
    public void shutdown() {
        shutdown = true;
    }

    /*
     * Get map of peers from registry.
     * 
     * @return Map of peers. Key = (Long) PID of peer; Value = AddrServerInfo of peer.
     */
    private Map<Long, AddrServerInfo> getPeers() {
        return registry.getRecords();
    }

    /*
     * Get list of peer PIDs.
     * 
     * @return List of peer PIDs.
     */
    private Set<Long> getPeerPIDs() {
        return getPeers().keySet();
    }

    /*
     * Get own PID.
     * 
     * @return PID of self.
     */
    private long getSelfPID() {
        return config.getPID();
    }

    /*
     * Initiate an election.
     * 
     * TODO: just exception handling (on Thread.sleep() causing InterruptedException)
     */
    private void initiateElection() {

        if (!running) {
            running = true; // Set flag
            bullyResponseReceived = false; // Reset flag for this election (as no bully has been received so far for this election)
            leaderAnnouncementReceived = false; // Reset flag for this election (as no leader has been received so far for this election)

            boolean higherExists = false; // Flag used to immediately declare leader if no higher PID is found

            // Loop through all peers
            for (Long peerPID : getPeerPIDs()) {
                if (peerPID > getSelfPID()) { // If peer PID is higher than own PID
                    higherExists = true; // Set flag to true
                    sendTo(generateElectionMessage(), peerPID); // Send ELECTION message to peer
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

            bullyResponseReceived = false; // Reset flag for next election
            leaderAnnouncementReceived = false; // Reset flag for next election
        }
    }

    /*
     * Ping the leader, wait for timeout, if no message received, initiate election 
     * 
     * TODO: just exception handling (on Thread.sleep() causing InterruptedException)
     */
    public void pingLeader() {
        sendTo(generatePingMessage(), leaderPID); // Send ping message to leader
        Thread.sleep(leaderPingTimeout); // Wait until timeout
        if (!leaderPingReceived) initiateElection(); // If no message received, initiate election
        else leaderPingReceived = false; // Otherwise, reset flag for next ping
    }

    /*
     * Bully a process.
     * 
     * @param peerPID PID of peer to bully.
     * 
     * TODO: nothing
     */
    public void bully(Long peerPID) {
        ServerServerMessage bullyMessage = generateBullyMessage();
        sendTo(bullyMessage, peerPID);
    }

    /*
     * Send a LEADER message to all peers, announcing self as leader.
     * 
     * TODO: nothing
     */
    public void declareSelfLeader() {
        for (Long peerPID : getPeerPIDs()) {
            sendTo(generateLeaderMessage(), peerPID);
        }
    }

        /*
     * Generate a ping message to send to the leader.
     * 
     * TODO: Implement this method
     */
    public ServerServerMessage generatePingMessage() {
        return null;
    }

    /*
     * Generate an election message, announcing self as running for leader.
     * 
     * TODO: Implement this method
     */
    public ServerServerMessage generateElectionMessage() {

        Long selfPID = getSelfPID();

        return null;
    }

    /*
     * Generate a bully message to respond to an election message from a lower PID.
     * 
     * TODO: Implement this method
     */
    public ServerServerMessage generateBullyMessage() {

        Long selfPID = getSelfPID();

        return null;
    }
    
    /*
     * Generate a message that announces self as leader.
     * 
     * TODO: Implement this method
     */
    public ServerServerMessage generateLeaderMessage() {
        return null;
    }

    /*
     * Send a message to a peer.
     * 
     * @param message Message to send.
     * @param peerPID PID of peer to send message to.
     * 
     * TODO: Implement this method
     */
    public void sendTo(ServerServerMessage message, Long peerPID) {

        AddrServerInfo peer = getPeers().get(peerPID);

        // Send message to peer
    }
}

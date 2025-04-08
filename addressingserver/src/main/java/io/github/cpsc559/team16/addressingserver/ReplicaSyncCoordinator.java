package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.messaging.MessageIDGenerator;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReplicaSyncCoordinator {

    private final PeerManager peerManager;
    private final BroadcastManager broadcastManager;
    private final ConnectionCleanupManager cleanupManager;
    /**
     * A thread-safe map of message or event IDs to their corresponding {@link PendingEvent} instances.
     * <p>
     * Each entry represents an ongoing network event (e.g., a registration or broadcast operation)
     * that requires acknowledgments from one or more remote processes before completion.
     * </p><p>
     * This structure allows the system to track which events are still waiting for ACKs, trigger
     * associated actions once all responses have been received, and perform retries or timeouts
     * if necessary. Keys must be unique and are typically generated using the {@link MessageIDGenerator}.
     * </p>
     */
    private final ConcurrentMap<Long, PendingEvent> pendingEvents = new ConcurrentHashMap<>();

    public void addPendingEvent(Long messageID, PendingEvent event) {
        pendingEvents.put(messageID, event);
    }


    public ReplicaSyncCoordinator(PeerManager peerManager, BroadcastManager broadcastManager, ConnectionCleanupManager cleanupManager) {
        this.peerManager = peerManager;
        this.broadcastManager = broadcastManager;
        this.cleanupManager = cleanupManager;
    }

    public void trackEvent(Long messageID, PendingEvent event) {
        pendingEvents.put(messageID, event);
    }

    /**
     * Processes an acknowledgment from a replica for a previously broadcasted message.
     *
     * <p>This method tracks the receipt of an acknowledgment for a given message ID and replica PID.
     * Once all expected replicas have acknowledged the message, it attempts to respond to the original
     * requester via the stored {@link NIOMessageChannel}. If this final response fails due to an
     * {@link IOException}, the channel used to communicate with the requester is returned so it can be
     * cleaned up by the caller (e.g., closed or deregistered).
     *
     * @param messageID the ID of the message that was acknowledged
     * @param recipientPID the process ID of the replica that sent the acknowledgment
     * @return the {@link NIOMessageChannel} of the original requester if responding failed,
     *         or {@code null} if no cleanup is needed.
     */
    public NIOMessageChannel processAck(Long messageID, Long recipientPID) {
        PendingEvent message = pendingEvents.get(messageID);
        if (message != null) {
            // DEBUG
            System.out.println("ACK received from network process with PID: " + recipientPID);
            message.removePendingRecipient(recipientPID);
            if (message.isComplete()) {
                pendingEvents.remove(messageID);
                try {
                    message.respondToRequester();
                } catch (IOException e) {
                    System.err.println("Failed to respond to process with PID: " + message.getRequestChannel().getServerPID());
                    return message.getRequestChannel(); // return the channel to the caller for cleanup (non-null return indicates failure)
                }
            }
        }
        return null; // no cleanup needed
    }

    public void retryUnackedEvents() {
        // Timer-driven retry logic
    }
}

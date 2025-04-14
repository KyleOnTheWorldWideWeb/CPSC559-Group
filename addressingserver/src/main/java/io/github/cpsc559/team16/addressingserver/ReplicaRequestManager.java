package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.Request;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.MessageIDGenerator;
import io.github.cpsc559.team16.common.messaging.RequestMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;

/**
 * The {@code ReplicaRequestManager} is responsible for initiating outbound requests
 * from a {@code REPLICA} process to the {@code PRIMARY} {@code AddressingServer}.
 * <p>
 * This class acts as a communication interface for the replica, enabling it to:
 * <ul>
 *   <li>Request full synchronization of server state or records</li>
 *   <li>Request specific updates (e.g., delta syncs, targeted state queries)</li>
 *   <li>Report conditions or errors to the primary (optional, future functionality)</li>
 * </ul>
 * <p>
 * The {@code ReplicaRequestManager} may be extended in the future to support additional
 * synchronization types, health checks, or telemetry reporting.
 * </p>
 */
public class ReplicaRequestManager {

    private static final int FULL_SYNC_THRESHOLD = 6;

    /**
     * The outbound channel for communicating with the primary AddressingServer.
     * Assumes that this connection is already established and persistent.
     */
    private NIOMessageChannel primaryChannel;


    /**
     * Tracks how many sync requests this replica has made to the primary.
     * <p>
     * Used in combination with {@link #incrementSyncCounter()} to control the frequency
     * of different types of synchronization operations. For example, a full
     * {@code AddrServerRecord} sync might be triggered every 6th request, while
     * {@code ChatServerRecord} syncs happen more frequently.
     * </p>
     */
    private int syncRequestCounter = 0;

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
     * Constructs a {@code ReplicaRequestManager}.
     *
     */
    public ReplicaRequestManager(MessageIDGenerator messageIDGenerator, NIOMessageChannel primaryChannel) {
        this.genMID = messageIDGenerator;
        this.primaryChannel = primaryChannel;
    }



    public void requestAllServerRecords(Long senderPID) {
        RequestMessage<Void> message = RequestMessage.requestAllServerRecords(genMID.nextID(), senderPID);
        try {
            primaryChannel.sendMessage(message.toJson());
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize RequestMessage<Void>AllServerRecords: " + e.getMessage());
        } catch (IOException ioe) {
            System.err.println("Failed to send RequestMessage<Void>AllServerRecords: " + ioe.getMessage());
        }
    }

    public void requestAllChatServerRecords(Long senderPID) {
        RequestMessage<Void> message = RequestMessage.requestAllChatServerRecords(genMID.nextID(), senderPID);
        try {
            primaryChannel.sendMessage(message.toJson());
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize RequestMessage<Void>AllChatServerRecords: " + e.getMessage());
        } catch (IOException ioe) {
            System.err.println("Failed to send RequestMessage<Void>AllChatServerRecords: " + ioe.getMessage());
        }
    }

    public void requestAllAddrServerRecords(Long senderPID) {
        RequestMessage<Void> message = RequestMessage.requestAllAddrServerRecords(genMID.nextID(), senderPID);
        try {
            primaryChannel.sendMessage(message.toJson());
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize RequestMessage<Void>AllAddrServerRecords: " + e.getMessage());
        } catch (IOException ioe) {
            System.err.println("Failed to send RequestMessage<Void>AllAddrServerRecords: " + ioe.getMessage());
        }
    }


    /**
     * Increments the sync request counter and determines whether to trigger a full sync.
     * <p>
     * This method returns {@code true} every {@link #FULL_SYNC_THRESHOLD} times it is called,
     * using modulo arithmetic to automatically wrap the counter. This helps balance the frequency
     * of full {@code AddrServerRecord} syncs relative to more frequent {@code ChatServerRecord} syncs.
     * </p>
     *
     * @return {@code true} if a full AddressingServer sync should be performed, {@code false} otherwise.
     */
    public boolean incrementSyncCounter() {
        this.syncRequestCounter = ++this.syncRequestCounter % FULL_SYNC_THRESHOLD;
        return this.syncRequestCounter == 0;
    }

    // Future ideas:
    // public void reportPrimaryFailure(...)

}

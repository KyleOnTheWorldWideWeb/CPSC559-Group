package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.messaging.ServerFailureMessage;
import io.github.cpsc559.team16.common.messaging.UpdateMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Map;

public class BroadcastManager {

    ConnectionCleanupManager cleanupManager;

    private final Map<SocketChannel, NIOMessageChannel> chatServerChannels;

    /**
     * A thread-safe mapping of persistent peer connections.
     * Each peer (replica AddressingServer) is tracked by its associated {@code SocketChannel}
     * and wrapped in an {@code NIOMessageChannel} for structured messaging.
     */
    private final Map<SocketChannel, NIOMessageChannel> peerChannels;


    public BroadcastManager(Map<SocketChannel, NIOMessageChannel> peerChannels,
                            Map<SocketChannel, NIOMessageChannel> chatServerChannels, ConnectionCleanupManager cleanupManager) {
        this.peerChannels = peerChannels;
        this.chatServerChannels = chatServerChannels;
        this.cleanupManager = cleanupManager;
    }

    /**
     * Broadcasts a single {@link AddrServerRecord} to all connected {@code ChatServer}s.
     * <p>
     * This method is used by the PRIMARY {@code AddressingServer} to inform chat servers
     * of changes to the network topology, such as the registration or update of a replica.
     * The update is packaged as an {@code UpdateMessage} with objectType {@code "AddrServerInfo"},
     * and is sent over all currently active {@code NIOMessageChannel}s.
     * </p>
     * <p>Updating processes throughout the distributed network is necessary to maintain consistency.</p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record        the {@link AddrServerRecord} containing the update information.
     */
    public void broadcastAddrServerRecordToCS(Long primaryPID, AddrServerRecord record) {
        UpdateMessage<AddrServerRecord> forChatServer = UpdateMessage.asRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecordToCS(forChatServer, chatServerChannels);
    }



    /**
     * Broadcasts a single {@link ChatServerRecord} to all connected {@code ChatServer}s.
     * <p>
     * This method is used by the PRIMARY {@code AddressingServer} to inform chat servers
     * about a new or updated {@code ChatServerRecord}. This ensures the distributed state
     * remains consistent across all registered nodes.
     * </p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record        the {@link AddrServerRecord} containing the update information.
     */
    public void broadcastChatServerRecordToCS(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> forChatServer = UpdateMessage.csRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecordToCS(forChatServer, chatServerChannels);
    }


    /**
     * Generic helper method for broadcasting {@code UpdateMessage<T>} to all connected peer addressing servers.
     * <p>
     * This method handles JSON serialization and transmission errors consistently,
     * logging any failures without interrupting the loop.
     * </p>
     *
     *<strong>NOTE:</strong> This method automatically handles any I/O exceptions and cleans up the connection that
     * triggered it.
     *
     * @param message the update message to be broadcast.
     * @param <T>     the type of record being broadcast (e.g., {@code AddrServerRecord}, {@code ChatServerRecord}).
     */
    public <T> void broadcastServerRecordToCS(UpdateMessage<T> message, Map<SocketChannel, NIOMessageChannel> channelHashMap) {
        try {
            String jsonMessage = message.toJson();
            for (NIOMessageChannel nioChannel : channelHashMap.values()) {
                try {
                    // Only new connections have a PID set to zero. ALL registered connections have the PID of the remote process.
                    if (nioChannel.getServerPID() == 0) continue;
                    nioChannel.sendMessage(jsonMessage);
                } catch (IOException ioe) {
                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                    cleanupManager.cleanupPersistentConnectionNIO(nioChannel,true);
                    // TODO - remove this from the list of channels passed in
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
        }
    }

    /**
     * Sends a broadcast {@link UpdateMessage} to all remaining recipients tracked by the given {@link PendingEvent}.
     * <p>
     * This method serializes the message and attempts delivery to each {@link NIOMessageChannel} still listed
     * in the {@code PendingEvent}'s recipient map. If a channel fails to deliver the message due to an
     * {@link IOException}, it is cleaned up and removed from the pending recipient list.
     * </p>
     *
     * <p>
     * This method ensures consistency between the state of the messaging layer and the {@code PendingEvent},
     * allowing retry logic or completion checks to rely on the recipient map accurately.
     * </p>
     *
     * @param message       the {@link UpdateMessage} to broadcast to registered replicas
     * @param pendingEvent  the {@link PendingEvent} tracking the recipients and acknowledgments for this update
     * @param <T>           the type of object included in the {@code UpdateMessage} payload (e.g., {@code AddrServerRecord})
     */
    public <T> void broadcastServerRecord(UpdateMessage<T> message, PendingEvent pendingEvent)
    {
        try {
            String jsonMessage = message.toJson();
            for (NIOMessageChannel nioChannel : pendingEvent.getPendingRecipients().values()) {
                try {
                    nioChannel.sendMessage(jsonMessage);
                } catch (IOException ioe) {
                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                    cleanupManager.cleanupPersistentConnectionNIO(nioChannel,true);
                    pendingEvent.removeRecipientChannel(nioChannel.getServerPID()); // The PID cannot null because the replicaChannelMap only contains registered replica channels
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a single {@link AddrServerRecord} update to all registered {@code AddressingServer} replicas,
     * and associates the broadcast with a {@link PendingEvent} for coordination and retry tracking.
     *
     * <p>
     * This method wraps the update in an {@link UpdateMessage}, stores it in the pending event,
     * and delegates actual message transmission to {@link #broadcastServerRecord}.
     * </p>
     *
     * <p>
     * This supports eventual consistency by ensuring all known replicas receive and acknowledge
     * the updated addressing server state. Any replicas that fail to receive the message are removed
     * from the pending set and will not be expected to ACK.
     * </p>
     *
     * @param messageID        the unique message ID used to correlate ACKs and retries
     * @param primaryPID       the PID of the primary addressing server initiating the update
     * @param record           the {@link AddrServerRecord} containing the update to replicate
     * @param pendingEvent     the {@link PendingEvent} tracking acknowledgments and recipient state
     */
    public void broadcastASRecordToReplicas(long messageID, Long primaryPID, AddrServerRecord record,
                                            PendingEvent pendingEvent)
    {
        // Create the message to send to Replicas for synchronization of state across all Addressing Servers.
        UpdateMessage<AddrServerRecord> updateMessage = UpdateMessage.asRecordPrimaryToReplica(messageID, primaryPID, record);
        // Add this message to the pending event in case we need to retry sending the message.
        pendingEvent.setMessageRequiringACK(updateMessage);
        // Broadcast the message to all registered replicas - pass the map that is referenced by the pending event
        // so that we can remove any channels that had an I/O failure and had their connections cleaned up (we can't be waiting for ACK's that will never come!)
        broadcastServerRecord(updateMessage, pendingEvent);
    }

    /**
     * Broadcasts a single {@link ChatServerRecord} update to all registered {@code AddressingServer} replicas,
     * and associates the broadcast with a {@link PendingEvent} for coordination and retry tracking.
     *
     * <p>
     * This method wraps the update in an {@link UpdateMessage}, stores it in the pending event,
     * and delegates actual message transmission to {@link #broadcastServerRecord}.
     * </p>
     *
     * <p>
     * This supports eventual consistency by ensuring all known replicas receive and acknowledge
     * the updated addressing server state. Any replicas that fail to receive the message are removed
     * from the pending set and will not be expected to ACK.
     * </p>
     *
     * @param messageID        the unique message ID used to correlate ACKs and retries
     * @param primaryPID       the PID of the primary addressing server initiating the update
     * @param record           the {@link ChatServerRecord} containing the update to replicate
     * @param pendingEvent     the {@link PendingEvent} tracking acknowledgments and recipient state
     */
    public void broadcastCSRecordToReplicas(long messageID, Long primaryPID, ChatServerRecord record,
                                            PendingEvent pendingEvent) {
        // Create the message to send to Replicas for synchronization of state across all Addressing Servers.
        UpdateMessage<ChatServerRecord> updateMessage = UpdateMessage.csRecordPrimaryToReplica(messageID, primaryPID, record);
        // Add this message to the pending event in case we need to retry sending the message.
        pendingEvent.setMessageRequiringACK(updateMessage);
        // Broadcast the message to all registered replicas - pass the map that is referenced by the pending event
        // so that we can remove any channels that had an I/O failure and had their connections cleaned up (we can't be waiting for ACK's that will never come!)
        broadcastServerRecord(updateMessage, pendingEvent);
    }



    /**
     * Sends all currently known {@code AddrServerRecord} entries from the primary
     * to a newly connected replica.
     * <p>
     * This ensures that the new replica is fully synchronized with the current
     * network topology known to the primary.
     * </p>
     *
     * <strong>NOTE:</strong> We want this to throw an IOException - we do not want to clean up the channel in this class.
     * This is because we are only sending data to one recipient. No actions involving this recipient should continue, thus
     * an error is thrown and the calling code will be notified and have the option of handling it there, or passing it "up the chain".
     *
     * @param primaryPID the PID of the primary server sending the updates.
     * @param nioChannel the channel over which to send the records.
     */
    public void sendAllAddrServerRecords(Long primaryPID, NIOMessageChannel nioChannel,
                                         Map<Long, AddrServerRecord> registry) throws IOException {
        for (AddrServerRecord record : registry.values()) {
            UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
            try {
                nioChannel.sendMessage(message.toJson());
            } catch (JsonProcessingException e) {
                System.err.println("Failed to serialize UpdateMessage<AddrServerRecord>: " + e.getMessage());
            } catch (IOException ioe) {
                System.err.println("Failed to send UpdateMessage<AddrServerRecord>: " + ioe.getMessage());
                throw ioe;
            }
        }
        System.out.println("Done sending all AddrServerRecords to newly registered REPLICA.");
    }

    /**
     * Sends all currently known {@code ChatServerRecord} entries in the network.
     * <p>
     * This is typicall used to ensure that a new replica is fully synchronized with all of the
     * active ChatServer's known to the primary.
     * </p>
     *
     * <strong>NOTE:</strong> We want this to throw an IOException - we do not want to clean up the channel in this class.
     * This is because we are only sending data to one recipient. No actions involving this recipient should continue, thus
     * an error is thrown and the calling code will be notified and have the option of handling it there, or passing it "up the chain".
     *
     * @param primaryPID  the PID of the primary server sending the updates.
     * @param nioChannel  the channel over which to send the records.
     * @param registry a {@code HashMap} containing all {@code ChatServerRecord} entries.
     */
    public void sendAllChatServerRecords(Long primaryPID, NIOMessageChannel nioChannel,
                                         Map<Long, ChatServerRecord> registry) throws IOException {
        for (ChatServerRecord record : registry.values()) {
            UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToReplica(primaryPID, record);
            try {
                nioChannel.sendMessage(message.toJson());
            } catch (JsonProcessingException e) {
                System.err.println("Failed to serialize UpdateMessage<ChatServerRecord>: " + e.getMessage());
            } catch (IOException ioe) {
                System.err.println("Failed to send UpdateMessage<ChatServerRecord>: " + ioe.getMessage());
                throw ioe;
            }
        }
        System.out.println("Done sending all ChatServerRecords to newly registered REPLICA.");
    }


    /**
     * Sends all known records from the primary server to a newly connected replica.
     * <p>
     * This method consolidates the functionality of sending both Addressing Server records
     * (AddrServerRecord) and Chat Server records (ChatServerRecord) to a single recipient.
     * It sequentially invokes {@code sendAllAddrServerRecords} to send all Addressing Server records,
     * followed by {@code sendAllChatServerRecords} to send all Chat Server records.
     * </p>
     * <p>
     * <strong>NOTE:</strong> If an {@code IOException} occurs during the sending of any record,
     * the exception is propagated to the caller. This ensures that any issues with the network channel
     * are handled by the calling code, as no cleanup or recovery actions are performed within this method.
     * </p>
     *
     * @param primaryPID         the PID of the primary server sending the records.
     * @param nioChannel         the NIOMessageChannel over which the records are sent.
     * @param chatServerRegistry a {@code Map} containing all active {@code ChatServerRecord} entries,
     *                           keyed by their process IDs.
     * @param addrServerRegistry a {@code Map} containing all active {@code AddrServerRecord} entries,
     *                           keyed by their process IDs.
     * @throws IOException if an error occurs while sending any of the records.
     */
    public void sendAllRecordsToProcess(Long primaryPID, NIOMessageChannel nioChannel,
                                        Map<Long, ChatServerRecord> chatServerRegistry,
                                        Map<Long, AddrServerRecord> addrServerRegistry ) throws IOException {
        this.sendAllAddrServerRecords(primaryPID, nioChannel, addrServerRegistry);
        this.sendAllChatServerRecords(primaryPID, nioChannel, chatServerRegistry);
    }


}

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
     * @param record     the {@link AddrServerRecord} to broadcast.
     */
    public void broadcastAddrServerRecordToCS(Long primaryPID, AddrServerRecord record) {
        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecord(message, this.chatServerChannels);
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
     * @param record     the {@link ChatServerRecord} to broadcast.
     */
    public void broadcastChatServerRecordToCS(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecord(message, this.chatServerChannels);
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
    public void broadcastAddrServerRecord(Long primaryPID, AddrServerRecord record) {
        UpdateMessage<AddrServerRecord> forChatServer = UpdateMessage.asRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecord(forChatServer, chatServerChannels);
        UpdateMessage<AddrServerRecord> forReplica = UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
        broadcastServerRecord(forReplica, peerChannels);
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
    public void broadcastChatServerRecord(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> forChatServer = UpdateMessage.csRecordPrimaryToCS(primaryPID, record);
        broadcastServerRecord(forChatServer, chatServerChannels);
        UpdateMessage<ChatServerRecord> forReplica = UpdateMessage.csRecordPrimaryToReplica(primaryPID, record);
        broadcastServerRecord(forReplica, peerChannels);
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
    public void broadcastCSRecordToReplicas(long messageID, Long primaryPID, ChatServerRecord record) {
        broadcastServerRecord(UpdateMessage.csRecordPrimaryToReplica(messageID, primaryPID, record), peerChannels);
    }


    /**
     * Generic helper method for broadcasting {@code UpdateMessage<T>} to all connected peer addressing servers.
     * <p>
     * This method handles JSON serialization and transmission errors consistently,
     * logging any failures without interrupting the loop.
     * </p>
     *
     * @param message the update message to be broadcast.
     * @param <T>     the type of record being broadcast (e.g., {@code AddrServerRecord}, {@code ChatServerRecord}).
     */
    public <T> void broadcastServerRecord(UpdateMessage<T> message, Map<SocketChannel, NIOMessageChannel> channelHashMap) {
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
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
        }
    }

    /**
     * Generic helper method for broadcasting {@code UpdateMessage<T>} to all connected peer addressing servers.
     * <p>
     * This method handles JSON serialization and transmission errors consistently,
     * logging any failures without interrupting the loop.
     * </p>
     *
     * @param message the update message to be broadcast.
     * @param <T>     the type of record being broadcast (e.g., {@code AddrServerRecord}, {@code ChatServerRecord}).
     */
    public <T> void broadcastServerRecord(UpdateMessage<T> message, Collection<NIOMessageChannel> channels) {
        try {
            String jsonMessage = message.toJson();
            for (NIOMessageChannel nioChannel : channels) {
                try {
                    nioChannel.sendMessage(jsonMessage);
                } catch (IOException ioe) {
                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                    cleanupManager.cleanupPersistentConnectionNIO(nioChannel,true);
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a single {@link AddrServerRecord} to all registered {@code AddressingServer} REPLICA's.

     * <p>Updating processes throughout the distributed network is necessary to maintain consistency.</p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record        the {@link AddrServerRecord} containing the update information.
     */
    public void broadcastASRecordToReplicas(long messageID, Long primaryPID, AddrServerRecord record,
                                            Collection<NIOMessageChannel> connectedChannels) {
        broadcastServerRecord(UpdateMessage.asRecordPrimaryToReplica(messageID, primaryPID, record), connectedChannels);
    }


    /**
     *
     * @param message The server record that needs to be sent to all of the chat servers.
     */
    public <T> void broadcastServerRecordToChatServers(UpdateMessage<T> message) {
        this.broadcastServerRecord(message, this.chatServerChannels);
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

package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

/**
 * Centralized coordinator for handling registration logic for both
 * {@code ChatServer}s and {@code AddressingServer} replicas.
 * <p>
 * This class consolidates registration pathways that follow a common pattern
 * — receiving a {@link BaseAddrServerMessage}, assigning a PID, updating
 * internal registries, and broadcasting state — to avoid code duplication and improve modularity.
 */
public class RegistrationCoordinator {

    /** Reference to the primary AddressingServer instance that owns this coordinator. */
    private final AddressingServer server;

    /**
     * The process responsible for managing {@code AddrServerRecord} records.
     */
    private final AddrServerRegistry addrServerRegistry;

    /** Reference to the peer manager, used for updating peer records and managing replica state. */
    private final PeerManager peerManager;

    /**
     * The process responsible for managing {@code ChatServer} connections.
     */
    private final ChatServerRegistry chatServerRegistry;

    /** Reference to the chat server manager, responsible for handling ChatServer registration logic. */
    private final ChatServerManager chatServerManager;

    /** Broadcast manager used to propagate server state updates across the network. */
    private final BroadcastManager broadcastManager;

    /** Replica coordinator that tracks pending consistency events and manages ACK logic. */
    private final ReplicaSyncCoordinator replicaCoordinator;

    /**
     * The ConnectionCleanupManager centralizes the logic for cleaning up and closing failed connections.
     * It holds references to both PeerManager and ChatServerManager so that any channel failures can be
     * promptly removed from the live channel maps and properly closed.
     */
    private final ConnectionCleanupManager cleanupManager;

    /**
     * Constructs a {@code RegistrationCoordinator} tied to a specific {@code AddressingServer}.
     * <p>
     * All supporting components are fetched from the server and stored for convenience.
     *
     * @param server the AddressingServer instance this coordinator operates within
     */
    public RegistrationCoordinator(AddressingServer server) {
        this.server = server;
        this.peerManager = server.getPeerManager();
        this.chatServerManager = server.getChatServerManager();
        this.broadcastManager = server.getBroadcastManager();
        this.replicaCoordinator = server.getReplicaSyncCoordinator();
        this.cleanupManager = server.getCleanupManager();
        this.chatServerRegistry = server.getChatServerRegistry();
        this.addrServerRegistry = server.getAddrServerRegistry();
    }




    /**
     * Handles the registration process for a newly connected {@code ChatServer}.
     * <p>
     * This method coordinates the following steps:
     * <ul>
     * <li>Assigns a new unique process ID (PID) to the incoming chat server.</li>
     * <li>Updates the provided {@link ChatServerRecord} with the actual host address and assigned PID.</li>
     * <li>Updates the nioChannel connection to the chat server with the assigned PID</li>
     * <li>If this is the first replica to register (i.e., no other replicas are active), it bypasses coordination logic
     * and directly completes registration.</li>
     * <li>If other replicas exist, it triggers a synchronization event where all existing replicas must acknowledge
     * the update before the new chat server is officially registered.</li>
     * <li>Creates a {@link PendingEvent} to track replica ACKs and triggers the associated completion routine once all ACKs arrive.</li>
     * <li>Broadcasts the updated state to all registered replicas.</li>
     * </ul>
     *
     * <p>
     * This ensures strong consistency in the network by requiring all replicas to synchronize their
     * internal state before accepting the new chat server into the system.
     * </p>
     *
     * @param channel the socket connection from the registering chat server
     * @param nioChannel the message channel used to communicate with the chat server
     * @param msg the registration message containing the {@link ChatServerRecord}
     */
    public void handleChatServerRegistration(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> msg) {
        Long primaryPID = server.getConfig().getPID();
        // Check to see if the ChatServerRecord sent by the registering process contains a PID (0L signifies a new network process)
        ChatServerRecord record = msg.safeCastPayload(ChatServerRecord.class);
        Long csPID = server.generatePID();
        // If there are no other Replica addressing servers, register directly without coordinating with others.
        if (addrServerRegistry.getRecords().size() == 1) {
            this.registerChatServerNoReplicasExist(primaryPID, csPID, channel, nioChannel, record);
            return;
        } // Otherwise, initiate strong consistency: wait for ACKs from all existing replicas.

        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager.getRegisteredReplicaChannelMap();
        // Set the NIOChannel PID before continuing any of the response to the request (to avoid errors when closing connections).
        nioChannel.setServerPID(csPID);
        // Create unique message ID that will be used to track ACK messages as well as the pending event.
        long messageID = server.getMessageIDGenerator().nextID();
        // Create a new event that will trigger once all ACKs for synchronizing state have been received.
        PendingEvent event = this.createChatServerRegistrationEvent(primaryPID, csPID, channel, nioChannel, record, replicaChannelMap, msg.getMessageID());
        // Add this to the list of pending events. The message ID used for replication messages is the key.
        replicaCoordinator.addPendingEvent(messageID, event);
        // Broadcast the update to all current Replicas. Any NIOChannel with PID 0 (unregistered channels) will not be included.
        broadcastManager.broadcastCSRecordToReplicas(messageID, primaryPID, record, event);
        System.out.println("PRIMARY has sent ChatServerRecord to all REPLICA's - waiting for `Replicated` ACK before a response is issued.");
    }


    /**
     * Handles the full registration workflow for the first {@code ChatServer} connecting to the primary {@code AddressingServer}.
     * <p>
     * This method performs the following steps:
     * <ol>
     *     <li>Registers the chat server with the internal registry and associates it with the connection channel.</li>
     *     <li>Sends an acknowledgment (ACK) to confirm registration.</li>
     *     <li>Broadcasts all known chat server and address server records to the new chat server
     *         using the {@link BroadcastManager} to synchronize state.</li>
     *     <li>Broadcasts the newly registered {@link ChatServerRecord} to all connected chat servers.</li>
     *     <li>Prints the current chat server registry to the console for debugging purposes.</li>
     * </ol>
     * <p>
     * If an {@link IOException} occurs during this process, the associated connection is cleaned up
     * using the {@link ConnectionCleanupManager}.
     * </p>
     *
     * @param primaryPID   the process ID of the primary {@code AddressingServer}
     * @param newPID       the assigned process ID for the new {@code ChatServer}
     * @param channel      the {@link SocketChannel} associated with the chat server connection
     * @param nioChannel   the {@link NIOMessageChannel} used to communicate with the chat server
     * @param record       the {@link ChatServerRecord} representing the newly registered server
     */
    public void registerChatServerNoReplicasExist(long primaryPID, long newPID,
                                        SocketChannel channel,
                                        NIOMessageChannel nioChannel,
                                        ChatServerRecord record) {
        try {
            this.chatServerManager.registerServerSendACK(channel, nioChannel, primaryPID, newPID, record);
            this.broadcastManager.sendAllRecordsToCS(primaryPID, nioChannel,
                    this.chatServerRegistry.getRecords(),
                    this.addrServerRegistry.getRecords());
            this.broadcastManager.broadcastChatServerRecordToCS(primaryPID, record);
            this.chatServerRegistry.debugPrintAllServers();
        } catch (IOException ioe) {
            System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
            this.cleanupManager.cleanupPersistentConnection(channel, true);
        }
    }



    /**
     * Creates a {@link PendingEvent} representing the registration of a new chat server with the primary {@code AddressingServer}.
     * <p>
     * This event sends an initial acknowledgment message to the new chat server and tracks acknowledgments from
     * all currently registered replicas. The event is considered complete once all required ACKs are received.
     * </p>
     *
     * <p>
     * Once the event is complete, the following actions are performed in sequence:
     * </p>
     * <ul>
     * <li>The new chat server is formally added to the list of active NIOChannels in {@link ChatServerManager}.</li>
     * <li>The new chat server record is added to set of {@link ChatServerRecord} in {@link ChatServerRegistry}.</li>
     * <li>The full set of chat server and addressing server records are sent to the new chat server.</li>
     * <li>The new chat server's {@link ChatServerRecord} is broadcast to all connected chat servers.</li>
     * <li>The updated chat server registry is printed for debugging purposes.</li>
     * </ul>
     *
     * <p>
     * This method encapsulates the full coordination logic required to safely and consistently register
     * a chat server across a distributed system, ensuring that all participating replicas are aware of the new node.
     * </p>
     *
     * @param primaryPID  the PID of the primary {@code AddressingServer}
     * @param newPID      the PID assigned to the newly registering chat server
     * @param channel     the {@link SocketChannel} associated with the requester
     * @param nioChannel  the {@link NIOMessageChannel} used to communicate with the requester
     * @param record      the {@link ChatServerRecord} received from the registering process.
     * @param recipients  a map containing all the registered replica address server {@code NIOMessageChannel}'s and their PIDs
     * @param requestMessageID the message ID from the message that made the request causing this event to be created.
     * @return a {@link PendingEvent} configured to complete registration once all ACKs have been received
     */
    public PendingEvent createChatServerRegistrationEvent(long primaryPID, long newPID,
                                                       SocketChannel channel,
                                                       NIOMessageChannel nioChannel,
                                                       ChatServerRecord record,
                                                       Map<Long, NIOMessageChannel> recipients, long requestMessageID) {
        return new PendingEvent(
                AckMessage.chatServerRegistered(primaryPID, newPID),
                recipients,
                nioChannel, () -> {  // THESE ARE ALL THE ACTIONS THAT WILL OCCUR ONCE AddressingServer STATES ARE CONSISTENT.
                    // DEBUG
                    System.out.println("PRIMARY has received all ACK's from REPLICA's - server state synchronized, sending response...");
                    // All replicas have successfully replicated the update. Update state locally and continue with response.
                    this.chatServerManager.registerServer(channel, nioChannel, record);
                    this.chatServerRegistry.debugPrintAllServers();
                    // An ACK.Registered containing the PID for the newly registered will already have been sent by the pendingEvent (see above).
                    // Once all ACKs received, send all the server records to the new chat server
                    try {
                        this.broadcastManager.sendAllRecordsToCS(primaryPID, nioChannel,
                                this.chatServerRegistry.getRecords(),
                                this.addrServerRegistry.getRecords());
                        this.broadcastManager.broadcastChatServerRecordToCS(primaryPID, record);
                    } catch (IOException e) {
                        System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
                        // An error occurred while trying to sync the new ChatServer's state. Remove it from the network.
                        this.server.getCleanupManager().cleanupPersistentConnection(channel, true);
                    }
                },
                3, requestMessageID
        );
    }

    /**
     * Creates a {@link PendingEvent} representing the registration of a new replica with the primary {@code AddressingServer}.
     * <p>
     * This event sends an initial acknowledgment message to the new replica and tracks acknowledgments from
     * all currently registered replicas. The event is considered complete once all required ACKs are received.
     * </p>
     *
     * <p>
     * Once the event is complete, the following actions are performed in sequence:
     * </p>
     * <ul>
     *     <li>The new replica is formally added to the list of active NIOChannels in {@link PeerManager}.</li>
     *     <li>The new replica record is added to set of {@link AddrServerRecord} in {@link AddrServerRegistry}.</li>
     *     <li>The full set of chat server and addressing server records are sent to the new replica.</li>
     *     <li>The new replica's {@link AddrServerRecord} is broadcast to all connected chat servers.</li>
     *     <li>The updated address server registry is printed for debugging purposes.</li>
     * </ul>
     *
     * <p>
     * This method encapsulates the full coordination logic required to safely and consistently register
     * a replica across a distributed system, ensuring that all participating replicas are aware of the new node.
     * </p>
     *
     * @param primaryPID  the PID of the primary {@code AddressingServer}
     * @param newPID      the PID assigned to the newly registering replica
     * @param channel     the {@link SocketChannel} associated with the requester
     * @param nioChannel  the {@link NIOMessageChannel} used to communicate with the requester
     * @param record      the {@link AddrServerRecord} representing the new replica's state
     * @param recipients  a map containing all of the registered replica address server {@code NIOMessageChannel}'s and their PIDs
     * @param requestMessageID the message ID from the message that made the request causing this event to be created.
     * @return a {@link PendingEvent} configured to complete registration once all ACKs have been received
     */
    public PendingEvent createReplicaRegistrationEvent(long primaryPID, long newPID,
                                                       SocketChannel channel,
                                                       NIOMessageChannel nioChannel,
                                                       AddrServerRecord record,
                                                       Map<Long, NIOMessageChannel> recipients, long requestMessageID) {
        return new PendingEvent(
                AckMessage.replicaRegistered(primaryPID, newPID),
                recipients,
                nioChannel, () -> {  // THESE ARE ALL THE ACTIONS THAT WILL OCCUR ONCE AddressingServer STATES ARE CONSISTENT.
                    // All replicas have successfully replicated the update. Update state locally and continue with response.
                    this.peerManager.registerPeer(channel, nioChannel, record);
                    this.addrServerRegistry.debugPrintAllServers();
                    // An ACK containing the PID for the newly registered replica will already have been sent by the pendingEvent (see above).
                    // Once all ACKs received, send all the server records to the new replica
                    try {
                        this.broadcastManager.sendAllRecordsToReplica(primaryPID, nioChannel,
                                this.chatServerRegistry.getRecords(),
                                this.addrServerRegistry.getRecords());
                        this.broadcastManager.broadcastAddrServerRecordToCS(primaryPID, record);
                    } catch (IOException e) {
                        System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
                        // An error occurred while trying to sync the new Replicas state. Remove it from the network.
                        this.server.getCleanupManager().cleanupPersistentConnection(channel, true);
                    }
                },
                3, requestMessageID
        );
    }

    /**
     * Handles the full registration workflow for the first replica connecting to the primary {@code AddressingServer}.
     * <p>
     * This method:
     * <ol>
     *     <li>Registers the replica with the {@link PeerManager}, sending an acknowledgment (ACK) back to confirm registration.</li>
     *     <li>Sends all known chat server and address server records from the primary to the new replica
     *         using the {@link BroadcastManager} to synchronize state.</li>
     *     <li>Updates the nioChannel connection to the replica with the assigned PID</li>
     *     <li>Broadcasts the newly registered replica’s {@link AddrServerRecord} to all connected chat servers
     *         so they are aware of the updated network state.</li>
     *     <li>Prints the current address server registry to the console for debugging purposes.</li>
     * </ol>
     * <p>
     * If an {@link IOException} occurs at any point during this process, the associated connection is cleaned up
     * using the {@link ConnectionCleanupManager}.
     * </p>
     *
     * @param primaryPID   the process ID of the primary {@code AddressingServer}
     * @param newPID       the newly assigned process ID of the replica
     * @param channel      the raw {@link SocketChannel} associated with the replica
     * @param nioChannel   the {@link NIOMessageChannel} wrapper for communicating with the replica
     * @param record       the {@link AddrServerRecord} representing the newly registered replica
     */
    public void registerFirstReplicaServer(long primaryPID, long newPID,
                                           SocketChannel channel,
                                           NIOMessageChannel nioChannel,
                                           AddrServerRecord record) {
        try {
            this.peerManager.registerPeerSendACK(channel, nioChannel, primaryPID, newPID, record);
            this.broadcastManager.sendAllRecordsToReplica(primaryPID, nioChannel,
                    this.chatServerRegistry.getRecords(),
                    this.addrServerRegistry.getRecords());
            this.broadcastManager.broadcastAddrServerRecordToCS(primaryPID, record);
            this.addrServerRegistry.debugPrintAllServers();
        } catch (IOException ioe) {
            System.err.printf("IOException triggered while registering PID: %d - triggering connection cleanup.%n", newPID);
            this.cleanupManager.cleanupPersistentConnection(channel, true);
        }
    }

    /**
     * Handles the registration process for a newly connected {@code AddressingServer} replica.
     * <p>
     * This method coordinates the following steps:
     * <ul>
     *     <li>Assigns a new unique process ID (PID) to the incoming replica.</li>
     *     <li>Updates the provided {@link AddrServerRecord} with the actual host address and assigned PID.</li>
     *     <li>If this is the first replica to register (i.e., no other replicas are active), it bypasses coordination logic
     *         and directly completes registration.</li>
     *     <li>Updates the nioChannel connection to the replica with the assigned PID</li>
     *     <li>If other replicas exist, it triggers a synchronization event where all existing replicas must acknowledge
     *         the update before the new replica is officially registered.</li>
     *     <li>Creates a {@link PendingEvent} to track replica ACKs and triggers the associated completion routine once all ACKs arrive.</li>
     *     <li>Broadcasts the updated state to all registered replicas.</li>
     * </ul>
     *
     * <p>
     * This ensures strong consistency in the network by requiring all replicas to synchronize their
     * internal state before accepting the new replica into the system.
     * </p>
     *
     * @param channel the socket connection from the registering replica
     * @param nioChannel the message channel used to communicate with the replica
     * @param msg the registration message containing the {@link AddrServerRecord}
     */
    public void handleReplicaRegistration(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> msg)
    {
        Long primaryPID = server.getConfig().getPID();
        Long newPID = server.generatePID();
        // Update the AddrServerRecord sent by the registering process before synchronizing with current Replicas
        AddrServerRecord record = msg.safeCastPayload(AddrServerRecord.class);
        record.setPID(newPID);
        // If this is the first and only replica, register directly without coordinating with others.
        if (addrServerRegistry.getRecords().size() == 1) {
            this.registerFirstReplicaServer(primaryPID, newPID, channel, nioChannel, record);
            return;
        } // Otherwise, initiate strong consistency: wait for ACKs from all existing replicas.

        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager.getRegisteredReplicaChannelMap();
        // Set the NIOChannel PID before continuing any of the response to the request (to avoid errors when closing connections).
        nioChannel.setServerPID(newPID);
        // Create unique message ID that will be used to track ACK messages as well as the pending event.
        long messageID = server.getMessageIDGenerator().nextID();
        // Create a new event that will trigger once all ACKs for synchronizing state have been received.
        PendingEvent event = this.createReplicaRegistrationEvent(primaryPID, newPID, channel, nioChannel, record, replicaChannelMap, msg.getMessageID());
        // Add this to the list of pending events. The message ID used for replication messages is the key.
        replicaCoordinator.addPendingEvent(messageID, event);
        // Broadcast the update to all current Replicas. Any NIOChannel with PID 0 (unregistered channels) will not be included.
        broadcastManager.broadcastASRecordToReplicas(messageID, primaryPID, record, event);
    }

    // TODO: go over this and make sure the logic is tight. I think some of it will have to go in AddrServerReadDispatcher
    /**
     * Handles the synchronization process for a REPLICA that already has an identity.
     * This ensures all other Replicas update their view of this peer's socket/state.
     */
    public void handleReplicaSynchronization(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> msg) {
        // TODO: currently, this method assumes the PID received can only belong to one process. As such, it does not check to see if an NIOMessage channel already exists with that PID. Add that check
        AddrServerRecord record = msg.safeCastPayload(AddrServerRecord.class);
        Long replicaPID = record.getPID();
        // Check to see if the process attempting to synchronize is registered (internal record matches the received record).
        if (!this.addrServerRegistry.validateReplicaIdentity(record)) {
            System.err.printf("[%tT] [SYNCHRONIZE ERROR] Replica PID %d mismatch detected.%n", java.time.LocalTime.now(), replicaPID);
            // Link the NIO channel to the PID the replica is claiming
            // The cleanupPersistentConnection method requires this in order to remove any internal records.
            nioChannel.setServerPID(replicaPID);
            this.server.getCleanupManager().cleanupPersistentConnectionNIO(nioChannel, true);
        }
        else {
            // The record received matches the internal record.
            // A persistent connection has already been registered, and the NIOMessageChannel's PID has been set
            Long primaryPID = server.getConfig().getPID();
            // 2. Update local Registry immediately (Restoring the record)
            this.addrServerRegistry.updateOrInsertRecord(record);
            // If other replicas exist, we must ensure they have the record of this server.
            if (this.addrServerRegistry.getRecords().size() > 1) {
                // TODO: Might need to avoid sending this back to the replica that
                // Since the PID has not been set for the synchronizing replica's NIOMessageChannel, it will not be included here.
                Map<Long, NIOMessageChannel> replicaChannelMap = peerManager.getRegisteredReplicaChannelMap();
                long messageID = server.getMessageIDGenerator().nextID();

                // WRONG!
                // We use a simpler event because we don't need to send "All Records" back to a sync-ing peer
                PendingEvent event = new PendingEvent(
                        AckMessage.replicaRegistered(primaryPID, replicaPID),
                        replicaChannelMap,
                        nioChannel,
                        () -> System.out.println("Sync complete for PID: " + replicaPID),
                        3, msg.getMessageID()
                );

                replicaCoordinator.addPendingEvent(messageID, event);
                broadcastManager.broadcastASRecordToReplicas(messageID, primaryPID, record, event);
            } else {
                // Just send the ACK if we are alone
                try {
                    nioChannel.sendMessage(AckMessage.replicaRegistered(primaryPID, replicaPID).toJson());
                } catch (IOException e) {
                    cleanupManager.cleanupPersistentConnection(channel, true);
                }
            }
            // Link the NIO channel to the PID the replica is claiming.
            // The cleanupPersistentConnection method requires this in order to remove any internal records.
            nioChannel.setServerPID(replicaPID);
        }


    }

}

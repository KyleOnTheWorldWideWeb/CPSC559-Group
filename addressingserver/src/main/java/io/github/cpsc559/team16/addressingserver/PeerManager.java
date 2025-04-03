package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages peer registration, update propagation, and persistent communication
 * between the primary AddressingServer and its replicas.
 * <p>
 * This class maintains a set of persistent {@code SocketChannel} connections
 * to replicas and supports synchronization by pushing updates to them.
 * </p>
 */
public class PeerManager {

    /**
     * A thread-safe mapping of persistent peer connections.
     * Each peer (replica AddressingServer) is tracked by its associated {@code SocketChannel}
     * and wrapped in an {@code NIOMessageChannel} for structured messaging.
     */
    private final Map<SocketChannel, NIOMessageChannel> peerChannels;

    // A map of event IDs to PendingEvent objects.
    private final ConcurrentMap<Long, PendingMessage<?>> pendingMessages = new ConcurrentHashMap<>();



    /**
     * The registry containing all known {@code AddrServerRecord} entries,
     * used to track state across the distributed network of AddressingServers.
     */
    private final AddrServerRegistry registry;
    public void debugPrintAllServers() {
        this.registry.debugPrintAllServers();
    }

    /**
     * Constructs a {@code PeerManager} and binds it to a shared {@code AddrServerRegistry}.
     *
     * @param registry the shared registry of AddrServerRecords.
     */
    public PeerManager(AddrServerRegistry registry) {
        this.peerChannels = new ConcurrentHashMap<>();
        this.registry = registry;
    }

    /**
     * Removes a persistent connection and its associated record, logging the removal using the connection’s network PID.
     * <p>
     * This method performs the following actions:
     * <ul>
     *   <li>Removes the mapping between the {@code SocketChannel} and its corresponding {@code NIOMessageChannel}
     *       from the internal collection of persistent connections (e.g., in the PeerManager or ChatServerManager).</li>
     *   <li>Removes the associated record (such as an {@code AddrServerRecord} or {@code ChatServerRecord})
     *       that identifies the remote process connected via the {@code SocketChannel}.</li>
     * </ul>
     * </p>
     *
     * @param channel the {@code SocketChannel} representing the connection to remove.
     * <strong>NOTE:</strong> This method does not close the {@code SocketChannel}; closing the channel is the responsibility
     * of the caller.
     */
    public void removeRemoteProcess(SocketChannel channel) {
        // We already ensured that the key:value pair exists in the calling code AddrServerNetworkManager.cleanupPersistentConnection()
        NIOMessageChannel ch = peerChannels.remove(channel);
        Long pid = ch.getServerPID();
        try {
            this.registry.removeRecordByKey(pid);
            try {
                System.out.printf("Successfully removed *communication channels* for Network Process with PID: %d " +
                        "- and Host Address: %s%n", pid, channel.getRemoteAddress());
            } catch (IOException ignore) {}

        } catch (NullPointerException e){
            System.err.println("Removed a NIOMessageChannel and SocketChannel connection for an AddressingServer that had no AddrServerRecord. It's network PID was - " + pid);
        }
    }

    /**
     * Removes the remote process associated with the given channel and then closes the channel.
     * <p>
     * This method performs two main actions:
     * <ol>
     *   <li>Deregisters the remote process by calling {@code removeRemoteProcess(channelToRemove)},
     *       removing any references to the remote process from internal data structures.</li>
     *   <li>Attempts to close the provided {@code SocketChannel}. If an {@code IOException} occurs during
     *       the close operation, it is caught and ignored.</li>
     * </ol>
     * </p>
     *
     * @param channelToRemove the {@code SocketChannel} representing the connection to be removed and closed.
     */
    public void removeProcessCloseConnection(SocketChannel channelToRemove) {
        this.removeRemoteProcess(channelToRemove);
        try { channelToRemove.close(); }
        catch(IOException ignored) {};
    }

    /**
     * Removes a failed server from the network based on its process ID.
     * <p>
     * This method checks the peer channels for a connection associated with the given failed process ID.
     * If it finds a channel in which the associated {@code NIOMessageChannel} has a matching server PID,
     * it removes the connection and any {@code AddrServerRecord} in the registry by calling the local
     * {@link #removeProcessCloseConnection(SocketChannel)} method.
     * </p>
     * <p>
     * If no channel with a matching server PID is found, the method falls back to removing any
     * AddrServerRecord with the same PID directly from the local registry.
     * </p>
     *
     * @param failedPID the process ID of the failed server to remove
     */
    public void removeFailedServer(Long failedPID) {
        for (SocketChannel channel : peerChannels.keySet()) {
            // NIOChannel objects should always have an instance variable set that references the PID of the remote process.
            // We iterate through all the channels(keys) and respective NIOMessageChannels(values) until we find a match.
            if (peerChannels.get(channel).getServerPID().equals(failedPID)) {
                removeProcessCloseConnection(channel);
                return;
            }
        }
        registry.removeRecordByKey(failedPID); // Remove the AddrServerRecord for the failed remote network process.
    }

    /**
     * Registers a replica AddressingServer and sets up a persistent connection to it.
     * <p>
     * This method also updates the replica’s {@code AddrServerRecord} with its resolved host address and PID,
     * stores it in the shared registry, and sends a confirmation {@code AckMessage} followed by
     * the current state of all known AddrServer records.
     * </p>
     *
     * @param socketChannel the socket channel for the replica connection.
     * @param nioChannel    the messaging channel used to communicate with the replica.
     * @param peerPID       the process ID assigned to the replica.
     * @param primaryPID    the process ID of the primary server.
     * @param record        a partially populated record to complete and store.
     * @throws IOException if an error occurs during network communication.
     */
    public AddrServerRecord registerPeer(SocketChannel socketChannel, NIOMessageChannel nioChannel, Long peerPID,
                                         Long primaryPID, AddrServerRecord record) throws IOException {
        // Set the NIOChannel process ID to match that of the remote process before storing it for use.
        nioChannel.setServerPID(peerPID);
        peerChannels.put(socketChannel, nioChannel);
        // Retrieve the remote process Host Address.
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();
        // Update the incoming AddrServerRecord provided by the remote process.
        record.setHostAddress(replicaHostAddr);
        record.setPID(peerPID);
//        System.out.println("PRIMARY REGISTERED PROCESS WITH PID ---------> " + peerPID);
//        System.out.println("NIOChannel PID = " + nioChannel.getServerPID());
//        System.out.println("Socket Channel ID = " + socketChannel.toString());
        // Send all current AddrServerRecord's to the remote process before adding the record.
        // This is done because the AddrServerReadDispatcher will broadcast the new record once this method returns.
        //this.sendAllAddrServerRecords(primaryPID, nioChannel);

        registry.putAddrServerRecord(peerPID, record);

        System.out.println("Replica registered: " + replicaHostAddr + " (PID: " + peerPID + ")");

        // WE SEND THE RECORD TO ALL SERVERS (THIS ONE INCLUDED) AFTER THIS METHOD RETURNS. NO NEED TO SEND IT NOW.

        // Send the new record explicitly. Race conditions can occur where the hashmap doesn't update quick enough.
//        UpdateMessage<AddrServerRecord> selfUpdate =
//                UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
//        nioChannel.sendMessage(selfUpdate.toJson());

        // Send an ACK to notify the server it has been registered.
        nioChannel.sendMessage(AckMessage.replicaRegistered(primaryPID, peerPID).toJson());
        return record;
    }

    /**
     * Sends all currently known {@code AddrServerRecord} entries from the primary
     * to a newly connected replica.
     * <p>
     * This ensures that the new replica is fully synchronized with the current
     * network topology known to the primary.
     * </p>
     *
     * @param primaryPID the PID of the primary server sending the updates.
     * @param nioChannel the channel over which to send the records.
     */
//    public void sendAllAddrServerRecords(Long primaryPID, NIOMessageChannel nioChannel) throws IOException {
//        for (AddrServerRecord record : this.registry.getRecords().values()) {
//            UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
//            try {
//                nioChannel.sendMessage(message.toJson());
//            } catch (JsonProcessingException e) {
//                System.err.println("Failed to serialize UpdateMessage<AddrServerRecord>: " + e.getMessage());
//            } catch (IOException ioe) {
//                System.err.println("Failed to send UpdateMessage<AddrServerRecord>: " + ioe.getMessage());
//                throw ioe;
//            }
//        }
//        System.out.println("Done sending all AddrServerRecords to newly registered REPLICA.");
//    }

    /**
     * Sends all currently known {@code ChatServerRecord} entries in the network.
     * <p>
     * This is typicall used to ensure that a new replica is fully synchronized with all of the
     * active ChatServer's known to the primary.
     * </p>
     *
     *
     * @param primaryPID  the PID of the primary server sending the updates.
     * @param nioChannel  the channel over which to send the records.
     * @param chatRecords a {@code HashMap} containing all {@code ChatServerRecord} entries.
     */
//    public void sendAllChatServerRecords(Long primaryPID, NIOMessageChannel nioChannel,
//                                         Map<Long, ChatServerRecord> chatRecords) throws IOException {
//        for (ChatServerRecord record : chatRecords.values()) {
//            UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToReplica(primaryPID, record);
//            try {
//                nioChannel.sendMessage(message.toJson());
//            } catch (JsonProcessingException e) {
//                System.err.println("Failed to serialize UpdateMessage<ChatServerRecord>: " + e.getMessage());
//            } catch (IOException ioe) {
//                System.err.println("Failed to send UpdateMessage<ChatServerRecord>: " + ioe.getMessage());
//                throw ioe;
//            }
//        }
//        System.out.println("Done sending all ChatServerRecords to newly registered REPLICA.");
//    }


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
    private <T> void broadcastLeadershipStatus(UpdateMessage<T> message) {
        try {
            String jsonMessage = message.toJson();
            for (NIOMessageChannel nioChannel : peerChannels.values()) {
                try {
                    nioChannel.sendMessage(jsonMessage);
                } catch (IOException ioe) {
                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                    removeProcessCloseConnection(nioChannel.getSocketChannel());
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a single {@link AddrServerRecord} to all connected peer replicas.
     * <p>
     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
     * REPLICA servers about a new or updated {@code AddrServerRecord} - a necessary part of
     * maintaining network consistency.
     * </p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record     the {@link AddrServerRecord} to broadcast.
     */
    public void broadcastAddrServerRecord(Long primaryPID, AddrServerRecord record) {
        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
        broadcastServerRecord(message);
    }

    /**
     * Broadcasts a single {@link ChatServerRecord} to all connected peer replicas.
     * <p>
     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
     * REPLICA servers about a new or updated {@code ChatServerRecord} - a necessary part of
     * maintaining network consistency.
     * </p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record     the {@link ChatServerRecord} to broadcast.
     *
     */
    public void broadcastChatServerRecord(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToReplica(primaryPID, record);
        broadcastServerRecord(message);
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
    private <T> void broadcastServerRecord(UpdateMessage<T> message) {
        try {
            String jsonMessage = message.toJson();
            for (NIOMessageChannel nioChannel : peerChannels.values()) {
                try {
                    nioChannel.sendMessage(jsonMessage);
                } catch (IOException ioe) {
                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                    // TODO - I need to log this error and deal with it later, not remove it immediately.
                    removeProcessCloseConnection(nioChannel.getSocketChannel());
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
            return;
        }
    }


    /**
     * Broadcasts a message to all peer replicas in the {@code peerChannels}.
     * <p>
     * This method is used to send one-off messages (e.g., state changes or heartbeats)
     * to all replicas, using their open persistent connections.
     * </p>
     *
     * @param message the {@code BaseAddrServerMessage} to be serialized and sent.
     */
    public void broadcast(BaseAddrServerMessage<?> message) {
        if (peerChannels.size() != (registry.getRecords().size()-1)) {
            System.err.println("NETWORK ERROR - More address server records exist than persistent connections.\n" +
                    "Refactoring necessary.");
        }
        String json;
        try {
            json = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize message: " + e.getMessage());
            return;
        }
        for (NIOMessageChannel nioChannel : peerChannels.values()) {
            try {
                nioChannel.sendMessage(json);
            } catch (IOException e) {
                System.err.println("Failed to send to process with PID " + nioChannel.getServerPID() + ": " + e.getMessage());
                removeProcessCloseConnection(nioChannel.getSocketChannel());
            }
        }
    }

    /**
     * Initializes a connection to the primary AddressingServer and sends a registration request.
     * <p>
     * This is invoked by REPLICA processes on startup to formally register themselves with the PRIMARY.
     * Once connected, the replica will receive back its PID and the full registry of AddrServer records.
     * </p>
     *
     * @param primaryHostAddress the IP address of the PRIMARY AddressingServer.
     * @param primaryReplicaPort the port used by the PRIMARY for peer registration.
     * @param clientPort         the replica’s client port.
     * @param peerPort           the replica’s peer communication port.
     * @param chatServerPort     the replica’s chat server communication port.
     * @return The SocketChannel used to register with the PRIMARY {@code AddressingServer}. This channel must
     * be registered with the {@code Selector} in the {@code AddrServerNetworkManager} for this REPLICA server.
     */
    public Optional<SocketChannel> registerWithPrimary(String primaryHostAddress, int primaryReplicaPort,
                                                       int clientPort, int peerPort, int chatServerPort) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(primaryHostAddress, primaryReplicaPort));
            while (!channel.finishConnect()) {
                Thread.sleep(100);
            }

            NIOMessageChannel nioChannel = new NIOMessageChannel(channel);
            peerChannels.put(channel, nioChannel);

            RegisterMessage<AddrServerRecord> register =
                    RegisterMessage.fromReplica(clientPort, peerPort, chatServerPort);
            nioChannel.sendMessage(register.toJson());

            channel.configureBlocking(false);

            System.out.println("Registration from REPLICA sent to PRIMARY.");
            return Optional.of(channel);
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to register replica with primary: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }



    // Add a "pending event" to the concurrent hashmap
    public void addPendingMessage(Long messageID, PendingMessage message) {
        pendingMessages.put(messageID, message);
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
     * @param replicaPID the process ID of the replica that sent the acknowledgment
     * @return the {@link NIOMessageChannel} of the original requester if responding failed,
     *         or {@code null} if no cleanup is needed
     * @throws IOException if responding to the original requester throws an {@link IOException}
     */
    public NIOMessageChannel processAck(Long messageID, Long replicaPID) {
        PendingMessage<?> message = pendingMessages.get(messageID);
        if (message != null) {
            message.removePendingReplica(replicaPID);
            if (message.isComplete()) {
                pendingMessages.remove(messageID);
                try {
                    message.respondToRequester();
                } catch (IOException e) {
                    return message.getRequestChannel(); // return the channel to the caller for cleanup
                }
            }
        }
        return null; // no cleanup needed
    }



    /**
     * This is a hell of an obtuse way of finding out an addressing servers role, but if you need it,
     * here you go.
     * @param pid The process id of the addressing server you want to know the role of.
     * @return An {@code ServerRole} String - REPLICA or PRIMARY
     */
    public String getServerRole(Long pid) {
        return this.registry.getRecords().get(pid).getRole().toString();
    }

    public Long getPrimaryPID() {
        for (AddrServerRecord record : registry.getRecords().values()) {
            if (record.getRole().equals(ServerRole.PRIMARY)) return record.getPID();
        }
        return 0L;
    }

    /**
     * Returns a set of all connected peer process IDs, excluding the given calling process ID.
     * <p>
     * This method is useful when the caller wants to get all *other* peer PIDs in the network,
     * for example when broadcasting to all replicas except itself.
     * </p>
     *
     * @param callingPID the PID of the current process making the request (to be excluded).
     * @return a {@code Set<Long>} containing the PIDs of all connected peers except the caller.
     */
    public Set<Long> getAllPeerPIDs(Long callingPID) {
        Set<Long> peerPIDs = ConcurrentHashMap.newKeySet();
        for (NIOMessageChannel channel : peerChannels.values()) {
            Long pid = channel.getServerPID();
            if (pid != null && !pid.equals(callingPID)) {
                peerPIDs.add(pid);
            }
        }
        return peerPIDs;
    }


    /**
     * Updates or inserts a record into the shared AddrServer registry.
     * <p>
     * This method is typically called when receiving an {@code UpdateMessage}
     * containing new or modified AddrServer information.
     * </p>
     *
     * @param record the record to insert or update.
     */
    public void updateRecords(AddrServerRecord record) {
        registry.updateOrInsertRecord(record);
    }


    /**
     * Returns a HashMap of SocketChannel and NIOChannel for all the
     * current addressing server connections.
     *
     * @return a map of {@code SocketChannel} to {@code NIOMessageChannel} for peer tracking.
     */
    public Map<SocketChannel, NIOMessageChannel> getChannels() {
        return this.peerChannels;
    }

}

package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.RegisterMessage;
import io.github.cpsc559.team16.common.messaging.UpdateMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

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
     * The AddressingServer instance that owns this PeerManager.
     * This is used to access the server's configuration and state.
     */
    private final AddressingServer server;

    /**
     * A thread-safe mapping of persistent peer connections.
     * Each peer (replica AddressingServer) is tracked by its associated {@code SocketChannel}
     * and wrapped in an {@code NIOMessageChannel} for structured messaging.
     */
    private final Map<SocketChannel, NIOMessageChannel> peerChannels;

    public Map<SocketChannel, NIOMessageChannel> getPeerChannels() {
        return peerChannels;
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

    /**
     * Checks whether a replica with the specified {@code pid} is currently registered and connected.
     * <p>
     * This method iterates through all active {@link NIOMessageChannel}s in the peer channel map
     * and returns {@code true} if any connected replica reports a non-null, matching PID.
     * </p>
     *
     * @param pid the process ID to check for an existing registered replica connection
     * @return {@code true} if a connected replica with the specified PID is found; {@code false} otherwise
     */
    public Boolean isRegistered (Long pid) {
        for (NIOMessageChannel channel: peerChannels.values()) {
            Long nioPID = channel.getServerPID();
            if (nioPID != 0L && nioPID.equals(pid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all connected replica channels that have been assigned a non-zero PID.
     *
     * @return a collection of active {@link NIOMessageChannel}s linked to registered replicas.
     */
    public Collection<NIOMessageChannel> getRegisteredNIOChannels() {
        return peerChannels.values()
                .stream()
                .filter(ch -> ch.getServerPID() != 0L)
                .toList();
    }

    /**
     * Returns a concurrent map of all connected replica channels that have been assigned a non-zero PID.
     * <p>
     * The map is keyed by the replica's PID, with values being their corresponding {@link NIOMessageChannel}.
     * Unregistered channels (PID == 0L) are excluded.
     * </p>
     *
     * @return a {@link ConcurrentHashMap} of replica PIDs to active {@link NIOMessageChannel}s.
     */
    public ConcurrentHashMap<Long, NIOMessageChannel> getRegisteredReplicaChannelMap() {
        ConcurrentHashMap<Long, NIOMessageChannel> registered = new ConcurrentHashMap<>();
        for (NIOMessageChannel ch : peerChannels.values()) {
            Long pid = ch.getServerPID();
            if (pid != 0L) {
                registered.put(pid, ch);
            }
        }
        return registered;
    }

    public ConcurrentHashMap<Long, NIOMessageChannel> getRegisteredReplicaChannelMapNoFailedPID(Long failedPID) {
        ConcurrentHashMap<Long, NIOMessageChannel> registered = new ConcurrentHashMap<>();
        for (NIOMessageChannel ch : peerChannels.values()) {
            Long pid = ch.getServerPID();
            if (pid != 0L && pid != failedPID) {
                registered.put(pid, ch);
            }
        }
        return registered;
    }


    /**
     * The registry containing all known {@code AddrServerRecord} entries,
     * used to track state across the distributed network of AddressingServers.
     */
    private final AddrServerRegistry registry;
    public void debugPrintAllServers() {
        this.registry.debugPrintAllServers();
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
     * Constructs a {@code PeerManager} and binds it to a shared {@code AddrServerRegistry}.
     *
     * @param registry the shared registry of AddrServerRecords.
     */
    public PeerManager(AddressingServer server) {
        this.server = server;
        this.registry = server.getAddrServerRegistry();
        this.peerChannels = new ConcurrentHashMap<>();
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
    private void removeRemoteProcess(SocketChannel channel) {
        NIOMessageChannel ch = this.peerChannels.get(channel);
        if (ch != null) {
            Long pid = ch.getServerPID();
            if (pid != 0L) {
                this.registry.removeRecordByKey(pid);
                try {
                    System.out.printf("Successfully removed *communication channels* for Network Process with PID: %d " +
                            "- and Host Address: %s%n", pid, channel.getRemoteAddress());
                } catch (IOException ignore) {}
            } else {
                System.err.println("Removed a NIOMessageChannel and SocketChannel connection for an AddressingServer with a PID = 0L that had no AddrServerRecord.");
            }
            this.peerChannels.remove(channel);
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
        this.registry.removeRecordByKey(failedPID);
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
     * @param record        a partially populated record to complete and store.
     * @throws IllegalArgumentException  if there is a mismatch between the PID stored in the NIOMessageChannel and the AddrServerRecord.
     */
    public void registerPeer(SocketChannel socketChannel, NIOMessageChannel nioChannel,
                             AddrServerRecord record) throws IllegalArgumentException {

        if (!nioChannel.getServerPID().equals(record.getPID())) {
            String err = String.format("Peer PID in nioChannel : %d does not match Peer PID in AddrServerRecord: %d%n",
                    nioChannel.getServerPID(), record.getPID());
            throw new IllegalArgumentException(err);
        }
        // Store the peer channel for future use.
        peerChannels.put(socketChannel, nioChannel);
        // Update network topology storing the AddrServerRecord, thus updating the local state of the Primary
        registry.putAddrServerRecord(record.getPID(), record);
        System.out.println("New replica successfully registered within the network.");
    }

    /**
     * Registers a replica AddressingServer and sets up a persistent connection to it.
     *
     * @param socketChannel the socket channel for the replica connection.
     * @param nioChannel    the messaging channel used to communicate with the replica.
     * @param peerPID       the process ID assigned to the replica.
     * @param primaryPID    the process ID of the primary server.
     * @param record        a fully populated (PID and Host Address set) {@link AddrServerRecord}
     * @throws IOException if an error occurs during network communication.
     */
    public void registerPeerSendACK(SocketChannel socketChannel, NIOMessageChannel nioChannel,
                                    Long primaryPID, Long peerPID, AddrServerRecord record) throws IOException {
        nioChannel.setServerPID(peerPID);
        peerChannels.put(socketChannel, nioChannel);
        System.out.println("PRIMARY AddrServer has registered a new REPLICA process with network PID: " + peerPID);
        System.out.println("NIOChannel PID = " + nioChannel.getServerPID());
        System.out.println("Socket Channel ID = " + socketChannel.toString());

        registry.putAddrServerRecord(peerPID, record);

        // Send an ACK to notify the server it has been registered.
        nioChannel.sendMessage(AckMessage.replicaRegistered(primaryPID, peerPID).toJson());
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
     * Broadcasts a message to all peer replicas in the {@code peerChannels}.
     * <p>
     * This method is used to send one-off messages (e.g. heartbeats)
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
            String publicAddress = System.getenv("PUBLIC_ADDRESS");
            // Add a fallback if the environment variable isn't set
            if (publicAddress == null || publicAddress.isEmpty()) {
                // Fallback to hostname/IP detection
                InetAddress localHost = InetAddress.getLocalHost();
                publicAddress = localHost.getHostAddress();
                System.out.println("WARNING: PUBLIC_ADDRESS not set in environment, using detected address: " + publicAddress);
            } else {
                System.out.println("Using PUBLIC_ADDRESS from environment: " + publicAddress);
            }
            RegisterMessage<AddrServerRecord> register =
                    RegisterMessage.fromReplica(publicAddress, clientPort, peerPort, chatServerPort);
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

    /**
     * This is a hell of an obtuse way of finding out an addressing servers role, but if you need it,
     * here you go.
     * @param pid The process id of the addressing server you want to know the role of.
     * @return An {@code ServerRole} String - REPLICA or PRIMARY
     */
    public String getServerRole(Long pid) {
        return this.registry.getRecords().get(pid).getRole().toString();
    }

    /**
     * Returns the network process ID (PID) of the current Primary Addressing Server
     * by looking through all the current AddrServer records in the network.
     *
     * @return A Long integer containing the PID of the Primary Addressing Server.
     */
    public Long getPrimaryPID() {

        Long primaryPID = 0L;

        for (AddrServerRecord record : registry.getRecords().values()) {
            if (record.getRole().equals(ServerRole.PRIMARY)) {

                if (primaryPID != 0L) {
                    System.err.println("WARNING: More than one PRIMARY AddressingServer found in the network. Picking highest PID.");
                }
                primaryPID = record.getPID();
            }
        }


        return primaryPID;
    }

    /**
     * Retrieves the active {@link NIOMessageChannel} for the current Primary {@code AddressingServer}, if one exists.
     * <p>
     * This method searches the internal peer channel map to find the {@code NIOMessageChannel}
     * associated with the server process whose {@code ServerRole} is {@code PRIMARY}.
     * It first determines the primary server's PID using the known {@link AddrServerRecord}s,
     * and then attempts to match it with an established network channel.
     * </p>
     *
     * <p>
     * If the primary server has not yet been registered or the connection is not established,
     * this method returns {@code null}. This allows consumers (e.g. background sync threads)
     * to delay execution until the primary becomes reachable.
     * </p>
     *
     * @return the {@code NIOMessageChannel} tied to the current {@code PRIMARY} {@code AddressingServer},
     *         or {@code null} if the primary has not yet been registered or is not connected.
     */
    public NIOMessageChannel getPrimaryNIOChannel() {
        Long primaryPid = this.getPrimaryPID();
        if (primaryPid != 0L) {
            for (NIOMessageChannel ch : this.peerChannels.values()) {
                if (ch.getServerPID().equals(primaryPid)) return ch;
            }
        }
        return null;
    }

    /**
     * Retrieves the {@link SocketChannel} associated with a specific server PID.
     * <p>
     * This method iterates over the internal channel map and returns the first {@code SocketChannel}
     * whose associated {@link NIOMessageChannel} has a matching {@code serverPID}. If no such entry
     * is found, it returns {@code null}.
     * </p>
     *
     * @param pid the process ID of the server to look for.
     * @return the matching {@code SocketChannel}, or {@code null} if no match is found.
     */
    public SocketChannel getSocketChannelByPID(Long pid) {
        if (pid != null) {
            for (Map.Entry<SocketChannel, NIOMessageChannel> entry : peerChannels.entrySet()) {
                if (entry.getValue().getServerPID().equals(pid)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }



//    /**
//     * Updates the provided {@link AddrServerRecord} with runtime information from the given socket connection and PID.
//     * <p>
//     * This method is typically called during replica registration to ensure that the record accurately reflects
//     * the replica's actual host address and assigned PID. The host address is extracted directly from the
//     * {@link SocketChannel}'s remote address to avoid relying on potentially incorrect values sent by the remote process.
//     * </p>
//     *
//     * @param socketChannel the channel representing the remote replica's connection
//     * @param record        the {@link AddrServerRecord} instance provided by the replica
//     * @param peerPID       the process ID assigned to the replica by the primary
//     * @return the updated {@link AddrServerRecord} with corrected host address and assigned PID
//     * @throws IOException if the remote address cannot be resolved from the socket
//     */
//    public static AddrServerRecord updateServerRecord(SocketChannel socketChannel,
//                                               AddrServerRecord record, Long peerPID) throws IOException {
//        // Retrieve the remote process Host Address.
//        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
//        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();
//        // Update the incoming AddrServerRecord provided by the remote process.
//        record.setHostAddress(replicaHostAddr);
//        record.setPID(peerPID);
//        return record;
//    }
//    /**
//     * Sends all currently known {@code AddrServerRecord} entries from the primary
//     * to a newly connected replica.
//     * <p>
//     * This ensures that the new replica is fully synchronized with the current
//     * network topology known to the primary.
//     * </p>
//     *
//     * @param primaryPID the PID of the primary server sending the updates.
//     * @param nioChannel the channel over which to send the records.
//     */
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

//    /**
//     * Sends all currently known {@code ChatServerRecord} entries in the network.
//     * <p>
//     * This is typicall used to ensure that a new replica is fully synchronized with all of the
//     * active ChatServer's known to the primary.
//     * </p>
//     *
//     *
//     * @param primaryPID  the PID of the primary server sending the updates.
//     * @param nioChannel  the channel over which to send the records.
//     * @param chatRecords a {@code HashMap} containing all {@code ChatServerRecord} entries.
//     */
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
//    /**
//     * Sends a single {@link AddrServerRecord} to all the process tied to the NIOChannel.
//     * <p>
//     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
//     * REPLICA servers about a new or updated {@code AddrServerRecord} - a necessary part of
//     * maintaining network consistency.
//     * </p>
//     *
//     * @param primaryPID the PID of the primary server issuing the update.
//     * @param record     the {@link AddrServerRecord} to broadcast.
//     */
//    public void sendAddrServerRecord(long messageID, Long primaryPID, AddrServerRecord record, NIOMessageChannel nioChannel) throws IOException {
//        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToReplica(messageID, primaryPID, record);
//        try {
//            nioChannel.sendMessage(message.toJson());
//        }
//        catch (JsonProcessingException e) {
//            System.err.printf(
//                    "Failed to serialize UpdateMessage<%s>. Context: messageID=%d, senderPID=%d, senderRole=%s, receiverPID=%d. Exception: %s%n",
//                    message.getObjectType(), messageID, primaryPID, Roles.PRIMARY, nioChannel.getServerPID(), e.getMessage()
//            );
//        }
//        catch (IOException ioe) {
//            System.err.println("Failed to send UpdateMessage<AddrServerRecord> for message ID: " + message.getMessageID());
//            throw ioe;
//        }
//    }
//
//    /**
//     * Broadcasts a single {@link AddrServerRecord} to all connected peer replicas.
//     * <p>
//     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
//     * REPLICA servers about a new or updated {@code AddrServerRecord} - a necessary part of
//     * maintaining network consistency.
//     * </p>
//     *
//     * @param primaryPID the PID of the primary server issuing the update.
//     * @param record     the {@link AddrServerRecord} to broadcast.
//     */
//    public void broadcastAddrServerRecord(Long primaryPID, AddrServerRecord record) {
//        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToReplica(primaryPID, record);
//        broadcastServerRecord(message);
//    }
//
//    /**
//     * Broadcasts a single {@link ChatServerRecord} to all connected peer replicas.
//     * <p>
//     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
//     * REPLICA servers about a new or updated {@code ChatServerRecord} - a necessary part of
//     * maintaining network consistency.
//     * </p>
//     *
//     * @param primaryPID the PID of the primary server issuing the update.
//     * @param record     the {@link ChatServerRecord} to broadcast.
//     *
//     */
//    public void broadcastChatServerRecord(Long primaryPID, ChatServerRecord record) {
//        UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToReplica(primaryPID, record);
//        broadcastServerRecord(message);
//    }
//
//    /**
//     * Generic helper method for broadcasting {@code UpdateMessage<T>} to all connected peer addressing servers.
//     * <p>
//     * This method handles JSON serialization and transmission errors consistently,
//     * logging any failures without interrupting the loop.
//     * </p>
//     *
//     * @param message the update message to be broadcast.
//     * @param <T>     the type of record being broadcast (e.g., {@code AddrServerRecord}, {@code ChatServerRecord}).
//     */
//    private <T> void broadcastServerRecord(UpdateMessage<T> message) {
//        try {
//            String jsonMessage = message.toJson();
//            for (NIOMessageChannel nioChannel : peerChannels.values()) {
//                try {
//                    nioChannel.sendMessage(jsonMessage);
//                } catch (IOException ioe) {
//                    System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
//                    removeProcessCloseConnection(nioChannel.getSocketChannel());
//                }
//            }
//        } catch (JsonProcessingException e) {
//            System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
//            return;
//        }
//    }


}

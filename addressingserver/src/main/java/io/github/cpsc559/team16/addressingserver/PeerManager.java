package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * The registry containing all known {@code AddrServerRecord} entries,
     * used to track state across the distributed network of AddressingServers.
     */
    private AddrServerRegistry registry;

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
     * Removes a peer connection and logs the removal using its known network PID.
     *
     * @param channel the {@code SocketChannel} representing the peer connection to remove.
     */
    public void removeChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerChannels.remove(channel);
        if (ch != null) {
            System.out.println("Removed peer with network PID: " + ch.getServerPID());
        } else {
            System.out.println("No matching peer found for the given channel.");
        }
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
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();

        nioChannel.setServerPID(peerPID);
        peerChannels.put(socketChannel, nioChannel);

        record.setHostAddress(replicaHostAddr);
        record.setServerID(peerPID);
        registry.putAddrServerRecord(peerPID, record);

        System.out.println("Replica registered: " + replicaHostAddr + " (PID: " + peerPID + ")");

        AckMessage ack = new AckMessage("Registered", primaryPID, "PRIMARY", "REPLICA", peerPID.toString());
        nioChannel.sendMessage(ack.toJson());

        this.sendAllAddrServerRecords(primaryPID, nioChannel);

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
    public void sendAllAddrServerRecords(Long primaryPID, NIOMessageChannel nioChannel) {
        for (AddrServerRecord record : this.registry.getRecords().values()) {
            UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToNetwork(primaryPID, record);
            try {
                nioChannel.sendMessage(message.toJson());
            } catch (JsonProcessingException e) {
                System.err.println("Failed to serialize UpdateMessage<AddrServerRecord>: " + e.getMessage());
                return;
            } catch (IOException ioe) {
                System.err.println("Failed to send UpdateMessage<AddrServerRecord>: " + ioe.getMessage());
            }
        }
        System.out.println("Done sending all records");
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
        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToNetwork(primaryPID, record);
        broadcastServerRecord(message);
    }

    /**
     * Broadcasts a single {@link ChatServerRecord} to all connected peer replicas.
     * <p>
     * This method is used by the PRIMARY {@code AddressingServer} to notify all registered
     * REPLICA servers about a new or updated {@code AddrServerRecord} - a necessary part of
     * maintaining network consistency.
     * </p>
     *
     * @param primaryPID the PID of the primary server issuing the update.
     * @param record     the {@link ChatServerRecord} to broadcast.
     *
     */
    public void broadcastChatServerRecord(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToNetwork(primaryPID, record);
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
        for (NIOMessageChannel nioChannel : peerChannels.values()) {
            try {
                nioChannel.sendMessage(message.toJson());
            } catch (JsonProcessingException e) {
                System.err.println("Failed to serialize UpdateMessage<" + message.getObjectType() + ">: " + e.getMessage());
                return;
            } catch (IOException ioe) {
                System.err.println("Failed to send UpdateMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
            }
        }
    }


    /**
     * Broadcasts a message to all registered peer replicas.
     * <p>
     * This method is used to send one-off messages (e.g., state changes or heartbeats)
     * to all replicas, using their open persistent connections.
     * </p>
     *
     * @param message the {@code BaseAddrServerMessage} to be serialized and sent.
     */
    public void broadcast(BaseAddrServerMessage<?> message) {
        String json;
        try {
            json = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize message: " + e.getMessage());
            return;
        }

        for (NIOMessageChannel channel : peerChannels.values()) {
            try {
                channel.sendMessage(json);
            } catch (IOException e) {
                System.err.println("Failed to send to peer PID " + channel.getServerPID() + ": " + e.getMessage());
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
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(primaryHostAddress, primaryReplicaPort));
            while (!channel.finishConnect()) {
                Thread.sleep(100);
            }

            NIOMessageChannel nioChannel = new NIOMessageChannel(channel);
            peerChannels.put(channel, nioChannel);

            RegisterMessage<AddrServerRecord> register =
                    RegisterMessage.fromReplica(clientPort, peerPort, chatServerPort);
            nioChannel.sendMessage(register.toJson());

            return Optional.of(channel);

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to register replica with primary: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
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
     * Returns a map of all currently connected peer replicas.
     *
     * @return a map of {@code SocketChannel} to {@code NIOMessageChannel} for peer tracking.
     */
    public Map<SocketChannel, NIOMessageChannel> getPeerChannels() {
        return this.peerChannels;
    }
}

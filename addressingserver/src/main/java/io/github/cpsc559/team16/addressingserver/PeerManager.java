package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages peer registration, synchronization, and communication.
 * <p>
 * This class handles interactions between Addressing Server instances, including:
 * <ul>
 *     <li>Primary server registering replicas.</li>
 *     <li>Replicas communicating with each other.</li>
 *     <li>Peer-to-peer failover coordination.</li>
 * </ul>
 * </p>
 */
public class PeerManager {

    /**
     * A thread-safe map storing active replica channels.
     * The key is the replica's unique identifier (Long) and the value is the associated SocketChannel.
     * This allows the manager to efficiently track and send updates to all replicas.
     */
    private final Map <SocketChannel, NIOMessageChannel> peerChannels;

    /**
     * ObjectMapper instance used for serializing and deserializing JSON messages.
     * This is essential for converting message objects to and from JSON format.
     */
    ObjectMapper objectMapper;

    /**
     * Constructs a new PeerManager.
     *
     * <p>Initializes the JSON ObjectMapper and creates an empty ConcurrentHashMap to hold replica channels.</p>
     */
    public PeerManager() {
        this.objectMapper = new ObjectMapper();
        this.peerChannels = new ConcurrentHashMap<>();
    }


    public void removeNIOChannelByKey(SocketChannel channel) {
        try {
            peerChannels.remove(channel);
        } catch (Exception e) {
            System.err.println("Attempt to remove NIOChannel by key failed: "+ e.getMessage());
        }
    }

    /**
     * Removes a peer entry from the {@code peerChannels} map based on the given {@code NIOMessageChannel}.
     * <p>
     * This method searches the map for an entry where the associated {@code NIOMessageChannel}
     * matches the provided instance. If a match is found, the corresponding entry is removed.
     * </p>
     *
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *     <li>If the channel exists in the map, it is removed, and a message is printed confirming removal.</li>
     *     <li>If no matching channel is found, a warning message is logged.</li>
     * </ul>
     *
     * @param channel The {@link NIOMessageChannel} instance to be removed.
     */
    public void removeChannel(NIOMessageChannel channel) {
        // Use Java Streams to remove the entry efficiently
        boolean removed = peerChannels.entrySet().removeIf(entry -> entry.getValue().equals(channel));

        if (removed) {
            System.out.println("🗑 Removed peer: " + channel.getSocketChannel());
        } else {
            System.out.println("⚠ No matching peer found for the given channel.");
        }
    }


    /**
     * Registers a new replica server in the network. The replicas information is stored in
     * an {@code AddrServerInfo} record in the {@code AddrServerRegistry}.
     * <p>
     * A persistent channel between the Primary {@code AddressingServer} and the replica is created
     * and stored locally in the {@code replicaChannels} ArrayList.
     * </p>
     *
     * @param newPeerChannel The socket channel for the new replica connection.
     * @throws IOException If an error occurs while retrieving network info.
     *
     * @see PeerManager#peerChannels
     */
    public void registerPeer(Long primaryPID, Long peerPID, SocketChannel newPeerChannel,
                             AddrServerRegistry registry) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) newPeerChannel.getRemoteAddress();
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();
        // TODO - I need to retrieve this information from the Replica!
        registry.registerAddrServer(peerPID, replicaHostAddr, 49810, 49811, 49812, AddrServerConfig.ServerRole.REPLICA);
        this.peerChannels.put(newPeerChannel, new NIOMessageChannel(newPeerChannel, peerPID));
        // Send ACK to confirm registration
        sendPIDAck(newPeerChannel, primaryPID);
        System.out.println("Replica registered: " + replicaHostAddr + " (PID: " + peerPID + ")");
    }

    /**
     * Sends an acknowledgment message to confirm registration.
     */
    private void sendPIDAck(SocketChannel channel, Long primaryPID) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Long.toString(primaryPID).getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }



    public void sendServerInfo(String serverInfoJSON) {
        for (NIOMessageChannel replicaChannel : peerChannels.values()) {
            try {
                replicaChannel.sendMessage(serverInfoJSON);
            } catch (IOException e) {
                System.err.println("Failed to send message to replica: " + e.getMessage());
            }
        }
    }

    /**
     * Pushes updates to all replicas.
     *
     */
    public void pushUpdatesToReplicas(ServerInfo serverInfo) {
        try {
            // Serialize the ChatServerInfo object to JSON
            String serverInfoJSON = objectMapper.writeValueAsString(serverInfo);
            sendServerInfo(serverInfoJSON);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize ServerInfo object: " + e.getMessage());
        }
    }

    public Map<SocketChannel, NIOMessageChannel> getPeerChannels() {
        return this.peerChannels;
    }

    public void registerReplicaWithPrimary(String primaryHostAddress, int primaryReplicaPort,
                                           AddrServerNetworkManager networkManager, int clientPort, int peerPort, int chatServerPort) {
        try {
            // Create a non-blocking SocketChannel to reach the primary’s replica port
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress("host.docker.internal", primaryReplicaPort));

            // (Optional) Wait for connection to finish
            while (!channel.finishConnect()) {
                Thread.sleep(100);
            }
            // TODO - Update all of this now that I have a messaging class
            // Send a simple handshake message to let the primary know we are a BACKUP
            // In practice, you would send actual metadata (our host address, ports, etc.).
            String handshake = "REGISTER BACKUP "
                    + clientPort + " "
                    + peerPort + " "
                    + chatServerPort;
            ByteBuffer buffer = ByteBuffer.wrap(handshake.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            System.out.println("Backup -> Primary registration request sent.");

            // (Optionally) Read the primary’s ACK, or handle any handshake it sends back here.
            // READ ACK HERE SO I KNOW THE PROCESS ID OF THE PRIMARY
            // Temporarily switch to blocking mode
            channel.configureBlocking(true);

            ByteBuffer ackBuffer = ByteBuffer.allocate(256);
            int bytesRead = channel.read(ackBuffer);
            if (bytesRead > 0) {
                ackBuffer.flip();
                String ack = StandardCharsets.UTF_8.decode(ackBuffer).toString();
                System.out.println("Received primary ACK: " + ack);
                // TODO - STORE THE PRIMARY PID FOR THE REPLICA TO KNOW WITHOUT HAVING TO LOOP THROUGH CHANNELS
                long primaryPID = Long.parseLong(ack.trim());
                this.peerChannels.put(channel, new NIOMessageChannel(channel, primaryPID));
            } else {
                System.out.println("No ACK received.");
            }

            // Switch back to non-blocking mode for further read operations
            channel.configureBlocking(false);
            // Register this channel with our selector so the Replica can handle future read events
            // The Primary Addressing server will be pushing updates to the replica through this channel.
            // MUST DO THIS AFTER THE ACK - CANNOT SET TO BLOCKING AFTER REGISTERING THE CHANNEL AS OP_READ
            networkManager.openPersistentChannel(channel);

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to register backup with primary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void pushChatServerUpdate(ChatServerInfo chatServerInfo) {
    }

    public void handleUpdateMessage(String jsonMessage, ChatServerRegistry chatServerRegistry) {

    }

}


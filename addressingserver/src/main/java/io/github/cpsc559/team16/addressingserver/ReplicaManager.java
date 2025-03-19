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
 * Manages replica registration, synchronization, and communication.
 * This class is responsible for handling primary-replica interactions.
 */
public class ReplicaManager {

    /**
     * A thread-safe map storing active replica channels.
     * The key is the replica's unique identifier (Long) and the value is the associated SocketChannel.
     * This allows the manager to efficiently track and send updates to all replicas.
     */
    private final Map <Long, NIOMessageChannel> replicaChannels;

    /**
     * ObjectMapper instance used for serializing and deserializing JSON messages.
     * This is essential for converting message objects to and from JSON format.
     */
    ObjectMapper objectMapper;

    /**
     * Constructs a new ReplicaManager.
     *
     * <p>Initializes the JSON ObjectMapper and creates an empty ConcurrentHashMap to hold replica channels.</p>
     */
    public ReplicaManager() {
        this.objectMapper = new ObjectMapper();
        this.replicaChannels = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new replica server in the network. The replicas information is stored in
     * an {@code AddrServerInfo} record in the {@code AddrServerRegistry}.
     * <p>
     * A persistent channel between the Primary {@code AddressingServer} and the replica is created
     * and stored locally in the {@code replicaChannels} ArrayList.
     * </p>
     *
     * @param newReplicaChannel The socket channel for the new replica connection.
     * @throws IOException If an error occurs while retrieving network info.
     *
     * @see ReplicaManager#replicaChannels
     */
    public void registerReplica(Long primaryPID, Long replicaPID, SocketChannel newReplicaChannel, AddrServerRegistry registry) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) newReplicaChannel.getRemoteAddress();
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();
        // TODO - I need to retrieve this information from the Replica!
        registry.registerAddrServer(replicaPID, replicaHostAddr, 49810, 49811, 49812, AddrServerConfig.ServerRole.REPLICA);
        this.replicaChannels.put(replicaPID, new NIOMessageChannel(newReplicaChannel));
        // Send ACK to confirm registration
        sendPIDAck(newReplicaChannel, primaryPID);
        System.out.println("Replica registered: " + replicaHostAddr + " (PID: " + replicaPID + ")");
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


    /**
     * Pushes updates to all replicas.
     *
     * @param updateMessage The update message to send.
     */
    public void pushUpdatesToReplicas(String updateMessage) {
        ByteBuffer buffer = ByteBuffer.wrap(updateMessage.getBytes(StandardCharsets.UTF_8));

        for (NIOMessageChannel replicaChannel : replicaChannels.values()) {
            try {
                // TODO - Add code now that I have a messaging class
            } catch (IOException e) {
                System.err.println("Failed to push update to replica: " + e.getMessage());
            }
        }
    }

    public Map<Long, NIOMessageChannel> getReplicaChannels() {
        return this.replicaChannels;
    }

    public void registerBackupWithPrimary(String primaryHostAddress, int primaryReplicaPort,
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
                long primaryPID = Long.parseLong(ack.trim());
                this.replicaChannels.put(primaryPID, new NIOMessageChannel(channel));
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
        try {
            // Serialize the ChatServerInfo object to JSON
            String chatServerJson = objectMapper.writeValueAsString(chatServerInfo);
            // Create a ServerUpdateMessage with the type indicator "CHAT_SERVER_UPDATE"
            ServerUpdateMessage updateMessage = new ServerUpdateMessage("Primary", "Replica", "CHAT_SERVER_UPDATE", chatServerJson);
            // Serialize the update message to JSON
            String jsonMessage = updateMessage.toJson();
            ByteBuffer buffer = ByteBuffer.wrap(jsonMessage.getBytes(StandardCharsets.UTF_8));

            // Push the update to each replica's channel
            for (NIOMessageChannel replicaChannel : replicaChannels.values()) {
                try {
                    while (buffer.hasRemaining()) {
                        replicaChannel.write(buffer);
                    }
                    buffer.rewind(); // Prepare buffer for next replica
                } catch (IOException e) {
                    System.err.println("Failed to push update to replica: " + e.getMessage());
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize ChatServerInfo update: " + e.getMessage());
        }
    }

    public void handleUpdateMessage(String jsonMessage, ChatServerRegistry chatServerRegistry) {
        try {
            // Deserialize the JSON into a ServerUpdateMessage
            ServerUpdateMessage updateMessage = ServerUpdateMessage.fromJson(jsonMessage, ServerUpdateMessage.class);
            if ("CHAT_SERVER_UPDATE".equals(updateMessage.getMsgType())) {
                // Deserialize the payload into a ChatServerInfo object
                ChatServerInfo chatServerInfo = objectMapper.readValue(updateMessage.getPayload(), ChatServerInfo.class);
                // Update the local ChatServerRegistry with the new record
                chatServerRegistry.registerServer(chatServerInfo.getPID(), chatServerInfo);
                System.out.println("Replica updated with ChatServerInfo: " + chatServerInfo);
            } else {
                System.err.println("Unknown update message type: " + updateMessage.getMsgType());
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to process update message: " + e.getMessage());
        }
    }

}


package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages peer registration, synchronization, and communication.
 * Handles sending and receiving structured messages using the new messaging protocol.
 */
public class PeerManager {
    private final Map<SocketChannel, NIOMessageChannel> peerChannels;

    /**
     * This Hashmap is used by each AddressingServer to keep {@code AddrServerRecord}
     * records of all other addressing servers in the network.
     */
    private Map<Long, AddrServerRecord> addrServerRecords;

    public PeerManager(Map<Long, AddrServerRecord> records) {
        this.peerChannels = new ConcurrentHashMap<>();
        this.addrServerRecords = records;
    }

    /**
     * Registers a replica as a persistent peer and tracks its channel.
     */
    public void registerPeer(SocketChannel socketChannel, NIOMessageChannel nioChannel, Long peerPID,
                             Long primaryPID, AddrServerRecord record) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String replicaHostAddr = remoteAddress.getAddress().getHostAddress();
        // Set the PID of the remote process connected to this channel.
        nioChannel.setServerPID(peerPID);
        // Store the persistent channel for use doing PUSH updates to peer {@code AddresingServer}'s
        peerChannels.put(socketChannel, nioChannel);
        // Update the blank placeholder address with the actual address of the REPLICA server
        record.setHostAddress(replicaHostAddr);
        // Update the zero placeholder PID with the newly created network PID for this process
        record.setServerID(peerPID);

        System.out.println("Replica registered: " + replicaHostAddr + " (PID: " + peerPID + ")");

        // Send confirmation
        AckMessage ack = new AckMessage("Registered", primaryPID, "PRIMARY", "REPLICA" , peerPID.toString());
        nioChannel.sendMessage(ack.toJson());
        // TODO - broadcast new AddrServer registration
    }

    public void removeChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerChannels.remove(channel);
        if (ch != null) {
            System.out.println("Removed peer with network PID: " + ch.getServerPID());
        } else {
            System.out.println("No matching peer found for the given channel.");
        }
    }

    /**
     * Sends a message to all registered peers.
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
     * Registers this replica with the primary server.
     */
    public void registerWithPrimary(PersistentConnectionManager connectionManager,
                                    String primaryHostAddress, int primaryReplicaPort,
                                    int clientPort, int peerPort, int chatServerPort) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(primaryHostAddress, primaryReplicaPort));
            while (!channel.finishConnect()) {
                Thread.sleep(100);
            }

            NIOMessageChannel nioChannel = new NIOMessageChannel(channel);
            connectionManager.addPersistentConnection(channel, nioChannel);
            peerChannels.put(channel, nioChannel);


            RegisterMessage<AddrServerRecord> register = RegisterMessage.fromReplica(clientPort, peerPort, chatServerPort);
            nioChannel.sendMessage(register.toJson());

        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to register replica with primary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void registrationAck() {

    }

    public Map<SocketChannel, NIOMessageChannel> getPeerChannels() {
        return this.peerChannels;
    }
}

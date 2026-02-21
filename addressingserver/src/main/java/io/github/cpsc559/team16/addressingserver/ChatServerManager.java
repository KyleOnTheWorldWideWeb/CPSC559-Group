package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.UpdateMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

public class ChatServerManager {


    /**
     * This Hashmap is used by each AddressingServer to keep {@code ChatServerRecord}'s
     * of all chat servers in the network. It is maintained by the ChatServerRegistry class.
     */
    private ChatServerRegistry registry;

    public void debugPrintAllServers() {
        this.registry.debugPrintAllServers();
    }

    private final Map<SocketChannel, NIOMessageChannel> chatServerChannels;


    public ChatServerManager(ChatServerRegistry registry) {
        this.chatServerChannels = new ConcurrentHashMap<>();
        this.registry = registry;
    }

    /**
     * Returns a HashMap of SocketChannel and NIOChannel for all the
     * current {@code ChatServer} connections.
     *
     * @return a map of {@code SocketChannel} to {@code NIOMessageChannel} for chat server tracking.
     */
    public Map<SocketChannel, NIOMessageChannel> getChannels() {
        return this.chatServerChannels;
    }

    /**
     * Removes a ChatServer connection and logs the removal using its known network PID.
     * This is achieved by removing the SocketChannel:NioChannel key-value pair from
     * the HashMap of persistent connections {@code chatServerChannels}, as well as the
     * ChatServerRecord associated with the remote process that was connected to the SocketChannel.
     *
     * @param channel the {@code SocketChannel} representing the peer connection to remove.
     *                <strong>NOTE:</strong> This method does not close the SocketChannel connection. It is up to the calling
     *                code to enact this behaviour.
     */
    public void removeRemoteProcess(SocketChannel channel) {
        NIOMessageChannel ch = this.chatServerChannels.get(channel);
        if (ch != null) {
            Long pid = ch.getServerPID();
            if (pid != 0L) {
                this.registry.removeRecordByKey(pid);
                System.out.println("Removed the network communication channels for the ChatServer with PID: " + pid);
            } else {
                System.err.println("Removed a NIOMessageChannel and SocketChannel connection for a ChatServer that had no ChatServerRecord. It's network PID was - " + pid);
            }
            this.chatServerChannels.remove(channel);
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
        try {
            channelToRemove.close();
        } catch (IOException ignored) {
        };
    }


    /**
     * Removes a failed chat server's connection and its registry record using only its process ID.
     * <p>
     * This method iterates over the chat server channels to locate a connection whose associated
     * {@code NIOMessageChannel} has a matching server PID. If found, it removes the connection and any
     * {@code ChatServerRecord} in the registry by calling
     * {@code removeProcessCloseConnection} on that channel and then returns. If no channel is found,
     * the method directly removes the record from the local registry.
     * </p>
     *
     * @param failedPID the process ID of the failed chat server to remove
     */
    public void removeFailedChatServer(Long failedPID) {
        for (SocketChannel channel : chatServerChannels.keySet()) {
            if (chatServerChannels.get(channel).getServerPID().equals(failedPID)) {
                removeProcessCloseConnection(channel);
                return;
            }
        }
        this.registry.removeRecordByKey(failedPID);
    }


    /**
     * Updates or inserts a record into the shared ChatServerRegistry registry.
     * <p>
     * This method is typically called when receiving an {@code UpdateMessage}
     * containing new or modified ChatServer information.
     * </p>
     *
     * @param record the record to insert or update.
     */
    public void updateRecords(ChatServerRecord record) {
        registry.updateOrInsertRecord(record);
    }


    /**
     * Registers a new {@code ChatServer} and sets up a persistent connection to it.
     * <p>
     * This method also updates the replica’s {@link ChatServerRecord} with its resolved host address and PID,
     * stores it in the shared registry, and sends a confirmation {@link AckMessage} followed by
     * the current state of all known AddrServer records.
     * </p>
     *
     * @param socketChannel the socket channel for the ChatServer connection.
     * @param nioChannel    the messaging channel used to communicate with the rChatServer.
     * @param record        a partially populated record to complete and store.
     * @throws IllegalArgumentException  if there is a mismatch between the PID stored in the NIOMessageChannel and the ChatServerRecord.
     */
    public void registerServer(SocketChannel socketChannel, NIOMessageChannel nioChannel,
                             ChatServerRecord record) throws IllegalArgumentException {

        if (!nioChannel.getServerPID().equals(record.getPID())) {
            String err = String.format("Peer PID in nioChannel : %d does not match Peer PID in AddrServerRecord: %d%n",
                    nioChannel.getServerPID(), record.getPID());
            throw new IllegalArgumentException(err);
        }
        // Store the chat server channel for future use.
        chatServerChannels.put(socketChannel, nioChannel);
        // Update network topology storing the ChatServerRecord, thus updating the local state of the Primary
        registry.putChatServerRecord(record.getPID(), record);
    }


    /**
     * Registers a ChatServer and sets up a persistent connection to it.
     * <p>
     *     This is typically used to register a chat server <strong>when no REPLICA addressing server exists.</strong>
     * </p>
     *
     * @param socketChannel the socket channel for the ChatServer connection.
     * @param nioChannel    the messaging channel used to communicate with the ChatServer.
     * @param peerPID       the process ID assigned to the ChatServer.
     * @param primaryPID    the process ID of the primary server.
     * @param record        a fully populated (PID and Host Address set) {@link ChatServerRecord}
     * @throws IOException if an error occurs during network communication.
     */
    public void registerServerSendACK(SocketChannel socketChannel, NIOMessageChannel nioChannel,
                                    Long primaryPID, Long peerPID, ChatServerRecord record) throws IOException {
        nioChannel.setServerPID(peerPID);
        // This occurs in AddrServerNetworkManager already
        //chatServerChannels.put(socketChannel, nioChannel);
        System.out.println("PRIMARY AddrServer has registered a new ChatServer process with network PID: " + peerPID);
        System.out.println("NIOChannel PID = " + nioChannel.getServerPID());
        System.out.println("Socket Channel ID = " + socketChannel.toString());
        record.setPID(peerPID);
        registry.putChatServerRecord(peerPID, record);
        // Send an ACK to notify the server it has been registered.
        nioChannel.sendMessage(AckMessage.chatServerRegistered(primaryPID, peerPID).toJson());
        System.out.println("New Chat Server successfully registered within the network.");
    }

    /**
     * Returns a concurrent map of all connected ChatServer channels that have been assigned a non-zero PID.
     * <p>
     * The map is keyed by the chat server's PID, with values being their corresponding {@link NIOMessageChannel}.
     * Unregistered channels (PID == 0L) are excluded.
     * </p>
     *
     * @return a {@link ConcurrentHashMap} of chat server PIDs to active {@link NIOMessageChannel}s.
     */
    public ConcurrentHashMap<Long, NIOMessageChannel> getRegisteredServerChannelMap() {
        ConcurrentHashMap<Long, NIOMessageChannel> registered = new ConcurrentHashMap<>();
        for (NIOMessageChannel ch : chatServerChannels.values()) {
            Long pid = ch.getServerPID();
            if (pid != 0L) {
                registered.put(pid, ch);
            }
        }
        return registered;
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
    public SocketChannel getChannelByPID(Long pid) {
        if (pid != null) {
            for (Map.Entry<SocketChannel, NIOMessageChannel> entry : chatServerChannels.entrySet()) {
                if (entry.getValue().getServerPID().equals(pid)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }


}

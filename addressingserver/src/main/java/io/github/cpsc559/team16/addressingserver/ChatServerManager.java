package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import javax.management.relation.Role;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        // We already ensured that the key:value pair exists in the calling code AddrServerNetworkManager.cleanupPersistentConnection()
        NIOMessageChannel ch = chatServerChannels.remove(channel);
        Long pid = ch.getServerPID();
        if (pid != 0L) {
            this.registry.removeRecordByKey(pid);
            System.out.println("Removed the network communication channels for the ChatServer with PID: " + pid);
        } else {
            System.err.println("Removed a NIOMessageChannel and SocketChannel connection for a ChatServer that had no ChatServerRecord. It's network PID was - " + pid);
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
        }
        ;
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
        registry.removeRecordByKey(failedPID);
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
     * Registers a {@code ChatServer} by  persistent connection to it.
     * <p>
     * This method completes the provided {@code ChatServerRecord} with the resolved host address and PID,
     * stores it in the internal registry, sends a confirmation {@code AckMessage} containing the newly
     * registered {@code ChatServer}'s network process ID (PID), and then pushes all
     * current {@code ChatServerRecord} entries to the newly connected chat server for synchronization.
     * </p>
     *
     * @param socketChannel the socket channel for the chat server connection.
     * @param nioChannel    the messaging channel used to communicate with the chat server.
     * @param csPID         the process ID assigned to the chat server.
     * @param primaryPID    the process ID of the primary AddressingServer.
     * @param record        a partially populated record to complete and store.
     * @throws IOException if an error occurs during message transmission.
     */
    public ChatServerRecord registerServer(SocketChannel socketChannel, NIOMessageChannel nioChannel, Long csPID,
                                           Long primaryPID, ChatServerRecord record) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String chatServerHostAddr = remoteAddress.getAddress().getHostAddress();

        nioChannel.setServerPID(csPID);
        chatServerChannels.put(socketChannel, nioChannel);

        record.setHostAddress(chatServerHostAddr);
        record.setPID(csPID);
        // Send all current records. This helps avoid race conditions.
        //this.sendAllChatServerRecords(primaryPID, nioChannel);
        // Add the new record - this might not be inserted quickly enough to call the sendAll method directly after.
        registry.putChatServerRecord(csPID, record);

        System.out.println("ChatServer registered: " + chatServerHostAddr + " (PID: " + csPID + ")");

        // WE SEND THE RECORD TO ALL SERVERS (THIS ONE INCLUDED) AFTER THIS METHOD RETURNS. NO NEED TO SEND IT NOW.

        // Send the new record explicitly. Race conditions can occur where the hashmap doesn't update quick enough.
        UpdateMessage<ChatServerRecord> selfUpdate =
                UpdateMessage.csRecordPrimaryToCS(primaryPID, record);
        nioChannel.sendMessage(selfUpdate.toJson());

        // Send an ACK to notify the server it has been registered.
        nioChannel.sendMessage(AckMessage.chatServerRegistered(primaryPID, csPID).toJson());

        return record;
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
        System.out.println("New replica successfully registered within the network.");
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
        chatServerChannels.put(socketChannel, nioChannel);
        System.out.println("PRIMARY AddrServer has registered a new ChatServer process with network PID: " + peerPID);
        System.out.println("NIOChannel PID = " + nioChannel.getServerPID());
        System.out.println("Socket Channel ID = " + socketChannel.toString());

        registry.putChatServerRecord(peerPID, record);

        // Send an ACK to notify the server it has been registered.
        nioChannel.sendMessage(AckMessage.chatServerRegistered(primaryPID, peerPID).toJson());
    }

}

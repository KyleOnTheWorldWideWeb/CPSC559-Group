package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerManager {


    private final Map<SocketChannel, NIOMessageChannel> chatServerChannels;
    public Map<SocketChannel, NIOMessageChannel> getChannels() {
        return chatServerChannels;
    }

    public void removeChannel(SocketChannel channel) {
        NIOMessageChannel ch = chatServerChannels.remove(channel);
        if (ch != null) {
            System.out.println("Removed peer with network PID: " + ch.getServerPID());
        } else {
            System.out.println("No matching chat server found for the given channel.");
        }
    }

    /**
     * This Hashmap is used by each AddressingServer to keep {@code ChatServerRecord}'s
     * of all chat servers in the network. It is maintained by the ChatServerRegistry class.
     */
    private ChatServerRegistry registry;

    public ChatServerManager(ChatServerRegistry registry) {
        this.chatServerChannels = new ConcurrentHashMap<>();
        this.registry = registry;
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
     * Sends all currently known {@code ChatServerRecord} entries from the primary
     * to a newly connected replica.
     * <p>
     * This ensures that the new replica is fully synchronized with the current
     * chat server topology known to the primary.
     * </p>
     *
     * @param primaryPID the PID of the primary server sending the updates.
     * @param nioChannel the channel over which to send the records.
     */
    public void sendAllChatServerRecords(Long primaryPID, NIOMessageChannel nioChannel) {
        for (ChatServerRecord record : this.registry.getRecords().values()) {
            UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToNetwork(primaryPID, record);
            try {
                nioChannel.sendMessage(message.toJson());
            } catch (JsonProcessingException e) {
                System.err.println("Failed to serialize UpdateMessage<ChatServerRecord>: " + e.getMessage());
                return;
            } catch (IOException ioe) {
                System.err.println("Failed to send UpdateMessage<ChatServerRecord>: " + ioe.getMessage());
            }
        }
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
    public void broadcastAddrServerRecord(Long primaryPID, AddrServerRecord record) {
        UpdateMessage<AddrServerRecord> message = UpdateMessage.asRecordPrimaryToNetwork(primaryPID, record);
        broadcastServerRecord(message);
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
    public void broadcastChatServerRecord(Long primaryPID, ChatServerRecord record) {
        UpdateMessage<ChatServerRecord> message = UpdateMessage.csRecordPrimaryToNetwork(primaryPID, record);
        broadcastServerRecord(message);
    }

    /**
     * Generic helper method for broadcasting {@code UpdateMessage<T>} to all connected chat servers.
     * <p>
     * This method handles JSON serialization and transmission errors consistently,
     * logging any failures without interrupting the loop.
     * </p>
     *
     * @param message the update message to be broadcast.
     * @param <T>     the type of record being broadcast (e.g., {@code AddrServerRecord}, {@code ChatServerRecord}).
     */
    private <T> void broadcastServerRecord(UpdateMessage<T> message) {
        for (NIOMessageChannel nioChannel : chatServerChannels.values()) {
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
     * @param peerPID       the process ID assigned to the chat server.
     * @param primaryPID    the process ID of the primary AddressingServer.
     * @param record        a partially populated record to complete and store.
     * @throws IOException if an error occurs during message transmission.
     */
    public ChatServerRecord registerServer(SocketChannel socketChannel, NIOMessageChannel nioChannel, Long peerPID,
                                           Long primaryPID, ChatServerRecord record) throws IOException {
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String chatServerHostAddr = remoteAddress.getAddress().getHostAddress();

        nioChannel.setServerPID(peerPID);
        chatServerChannels.put(socketChannel, nioChannel);

        record.setHostAddress(chatServerHostAddr);
        record.setPID(peerPID);
        registry.putChatServerRecord(peerPID, record);

        System.out.println("ChatServer registered: " + chatServerHostAddr + " (PID: " + peerPID + ")");

        AckMessage ack = new AckMessage("Registered", primaryPID, "PRIMARY", "CHATSERVER", peerPID.toString());
        nioChannel.sendMessage(ack.toJson());

        this.sendAllChatServerRecords(primaryPID, nioChannel);

        return record;
    }

    /**
     * Handles a client connection by sending the address of an active chat server.
     * <p>
     * This method calls {@code chatServerRegistry.getActiveHost()} to determine the active chat server address.
     * If an active host is found, its address (formatted as "hostAddress:clientPort") is sent to the client.
     * Otherwise, a message indicating no active host is available is sent.
     * </p>
     * <p>
     * Since the connection does not need to persist in either case, the message is sent, and the channel is closed.
     *</p>
     * @param channel the {@link SocketChannel} representing the client connection.
     * @param nioChannel the {@link NIOMessageChannel}
     * @throws IOException if an I/O error occurs while writing to or closing the channel.
     *
     * @see io.github.cpsc559.team16.addressingserver.ChatServerRegistry#getActiveHost()
     * For details about ChatServer host address retrieval
     */
    private void connectClientToHost(SocketChannel channel, NIOMessageChannel nioChannel) throws IOException {
        Optional<String> activeHost = registry.getActiveHost();
        // If there are no active hosts with room, send an ACK to notify the Client there is no network access at this time.
        String message = activeHost.orElse("No Host");

    }
}

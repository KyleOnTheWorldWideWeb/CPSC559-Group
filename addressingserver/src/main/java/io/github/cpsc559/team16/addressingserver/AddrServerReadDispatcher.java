package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import static io.github.cpsc559.team16.common.messaging.MessageDeserializer.deserializeMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;

/**
 * Handles read events from registered {@code SocketChannel}'s and routes them based on the server's role.
 * <p>
 * This class is responsible for delegating read events to the appropriate handlers.
 * It uses a cascading sequence of switch statements to decide which method to call:
 * </p>
 * <ul>
 *     <li>{@code Message Type} - e.g. UPDATE, REQUEST, PING</li>
 *     <li>{@code Sender Role} - e.g. REPLICA, PRIMARY, CHATSERVER</li>
 *     <li>{@code Object Type} - e.g. ChatServerRecord, AddrServerRecord.
 *     <p>This can be an actual object, or simply an "identifier" - e.g. AllChatServerInfo -
 *     the {@code MessageDeserializer} is capable of parsing non-objects and will not cause an error.</p>
 *     </li>
 * </ul>
 */
public class AddrServerReadDispatcher implements NetworkManager.ReadDispatcher {
    private final AddressingServer server;

    private final Long pid;

    public AddrServerReadDispatcher(AddressingServer server) {
        this.server = server;
        this.pid = server.getConfig().getPID();
    }



    /**
     * Uses an {@link NIOMessageChannel} for safe and abstracted message I/O between two processes
     * on a {@link SocketChannel}. JSON messages are automatically deserialized into a {@link BaseAddrServerMessage}
     * and routed through the {@code dispatchMsgType} method - the first in a cascading sequence of routing methods.
     *
     * @param channel The {@link SocketChannel} used to receive the message.
     * @param nioChannel The {@link NIOMessageChannel} used to decode and encode the message.
     *
     * @throws ConnectionClosedException if the remote process has closed the connection.
     * @throws IOException if an I/O error occurs during message reading or processing.
     */
    public void dispatch(SocketChannel channel, NIOMessageChannel nioChannel) throws ConnectionClosedException, IOException {
        String msgJson;
        while ((msgJson = nioChannel.receiveMessage()) != null) {
            BaseAddrServerMessage<?> message = deserializeMessage(msgJson);
            if (message != null) {
                dispatchMsgType(channel, nioChannel, message);
            } else {
                System.err.println("Could not deserialize incoming message: " + msgJson);
            }
        }
    }


    /**
     * Dispatches a received message based on its type, sender role, and object type.
     *
     * @param channel The {@link SocketChannel} used to receive the message.
     * @param nioChannel The {@link NIOMessageChannel} used to decode and encode the message.
     * @param message The parsed {@link BaseAddrServerMessage} containing the message details.
     *
     */
    private void dispatchMsgType(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getMsgType()) {
            case "ACK" -> handleAck(channel, nioChannel, message);
            case "REGISTER" -> handleRegistration(channel, nioChannel, message);
            case "UPDATE" -> handleUpdate(channel, nioChannel, message);
            case "REQUEST" -> handleRequest(channel, nioChannel, message);
            case "PING" -> handlePing(channel, nioChannel, message);
            case "NOTIFICATION" -> handleNotification(channel, nioChannel, message);
            case "ELECTION" -> handleElection(channel, nioChannel, message);
            default -> System.err.println("Unrecognized message type: " + message.getMsgType());
        }
    }


    /**
     * <NOTE> Can probably remove the channel parameters, but I'm keeping them for now in case an ACK needs to trigger an action.</NOTE>
     */
    private void handleAck(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getObjectType()) {
            case "Registered" -> {
                // This should ONLY ever be received by a REPLICA from the PRIMARY AddressingServer
                // A Registration ACK is always sent with the pid as a string for the process as the payload.
                Long assignedPID = Long.parseLong((String) message.getPayload());
                server.getConfig().setPID(assignedPID);
                nioChannel.setServerPID(assignedPID);
                System.out.println("This Replica has registered itself with PID: " + server.getConfig().getPID());
            }
            default -> System.err.println("Unrecognized ACK response: " + message.getObjectType());
        }
    }

    /**
     * Handles a register message based on the sender role and object type.
     *
     * @param channel The channel from which the message originated.
     * @param message The received update message.
     */
    private void handleRegistration(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getSenderRole()) {
            case "CHATSERVER" -> {
                try {
                    /* Add the REPLICA to the {@code AddressingServerRegistry}, store the persistent channel
                    in {@code ChatServerManager}, and broadcast the record to ALL chat servers in the network.
                    */
                    ChatServerRecord record = this.server.getChatServerManager().registerServer(channel, nioChannel,
                            this.server.generatePID(), pid, message.safeCastPayload(ChatServerRecord.class));
                    // Broadcast the new record to all REPLICA {@code AddressingServer}'s in the network.
                    this.server.getPeerManager().broadcastChatServerRecord(this.server.getConfig().getPID(), record);
                } catch (IOException ioe) {
                    System.err.println("Network error occurred during REPLICA registration: " + ioe.getMessage());
                }
            }
            case "REPLICA" -> {
                try {
                    // Add the REPLICA to the {@code AddressingServerRegistry}, store the persistent channel in {@code PeerManager}, and broadcast the record to ALL replicas.
                    AddrServerRecord record = this.server.getPeerManager().registerPeer(channel, nioChannel,
                            this.server.generatePID(), pid, message.safeCastPayload(AddrServerRecord.class));
                    // Broadcast the new record to all {@code ChatServer}'s in the network.
                    this.server.getChatServerManager().broadcastAddrServerRecord(this.server.getConfig().getPID(), record);
                } catch (IOException ioe) {
                    System.err.println("Network error occurred during REPLICA registration: " + ioe.getMessage());
                }
            }
            default -> System.err.println("Unrecognized sender role for REGISTER: " + message.getSenderRole());
        }
    }
    private void handleUpdate(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getSenderRole()) {
            case "CHATSERVER" -> {
                switch (message.getObjectType()) {
                    case "ClientCount" -> {
                        // server.updateClientCount(channel, nioChannel, message.getPayload());
                    }
                    case "ChatServerRecord" -> {
                        // server.updateChatServerInfo(channel, nioChannel, message.getPayload());
                    }
                }
            }
            case "PRIMARY" -> {
                switch (message.getObjectType()) {
                    case "AddrServerRecord" -> {
                        System.out.println("REPLICA is in switch statement for updating AddrServerRecord from PRIMARY");
                        server.getPeerManager().updateRecords(message.safeCastPayload(AddrServerRecord.class));
                    }
                    case "ChatServerRecord" -> {
                        server.getChatServerManager().updateRecords(message.safeCastPayload(ChatServerRecord.class));
                    }
                    // server.updateAddrServerInfo(channel, nioChannel, message.getPayload());
                }
            }
            case "REPLICA" -> {
                switch (message.getObjectType()) {
                    case "AddrServerRecord" -> {
                        System.out.println("REPLICA is in switch statement for updating AddrServerRecord from REPLICA");
                        server.getPeerManager().updateRecords(message.safeCastPayload(AddrServerRecord.class));
                    }
                    case "ChatServerRecord" -> {

                    }
                    // server.updateAddrServerInfo(channel, nioChannel, message.getPayload());
                }
            }
            default -> System.err.println("Unrecognized sender role for UPDATE: " + message.getSenderRole());
        }
    }


    /**
     * Handles a request message based on the sender role and object type.
     *
     * @param channel The channel from which the request originated.
     * @param message The received request message.
     */
    private void handleRequest(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getSenderRole()) {
            case "CHATSERVER" -> {
                if ("AllAddrServerInfo".equals(message.getObjectType())) {
                    // server.sendAddrServerInfo(channel);
                }
            }
            case "REPLICA" -> {
                if ("AllChatServerInfo".equals(message.getObjectType())) {
                    // server.sendChatServerInfo(channel);
                }
            }
            default -> System.err.println("Unrecognized sender role for REQUEST: " + message.getSenderRole());
        }
    }


    /**
     * Handles a ping request, typically used for failure detection.
     *
     * @param channel The channel from which the ping request originated.
     * @param message The received ping message.
     */
    private void handlePing(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        server.getPingManager().processPing(message);
    }

    /**
     * Handles notification messages such as server failures or new registrations.
     *
     * @param channel The channel from which the notification originated.
     * @param message The received notification message.
     */
    private void handleNotification(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        if ("ServerFailure".equals(message.getObjectType())) {
            //server.handleServerFailure(channel, nioChannel, message.getPayload());
        }
    }

    /**
     * Handles election-related messages, used for leader election among Addressing Servers.
     *
     * @param channel The channel from which the election message originated.
     * @param message The received election message.
     */
    private void handleElection(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        server.getLeaderElectionManager().processElectionMessage(channel, nioChannel, message);
    }

}

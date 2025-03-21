package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;

import java.io.IOException;
import java.nio.channels.SocketChannel;

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
     * Dispatches a received message based on its type, sender role, and object type.
     *
     * @param channel The {@link SocketChannel} representing the sender of the message.
     * @param message The parsed {@link BaseAddrServerMessage} containing the message details.
     * @throws IOException If an error occurs while processing the message.
     */
    public void dispatch(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getMsgType()) {
            case "REGISTER":
                handleRegistration(channel, nioChannel, message);
                break;

            case "UPDATE":
                handleUpdate(channel, nioChannel, message);
                break;

            case "REQUEST":
                handleRequest(channel, nioChannel, message);
                break;

            case "PING":
                handlePing(channel, nioChannel, message);
                break;

            case "NOTIFICATION":
                handleNotification(channel, nioChannel, message);
                break;

            case "ELECTION":
                handleElection(channel, nioChannel, message);
                break;

            default:
                System.err.println("Unrecognized message type: " + message.getMsgType());
                break;
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
            case "CHATSERVER": {
                try {
                    this.server.getPeerManager().registerPeer(channel, nioChannel, this.server.generatePID(), pid,
                            message.safeCastPayload(AddrServerRecord.class));
                } catch (IOException ioe) {
                    System.err.println("Network error occurred during REPLICA registration: " + ioe.getMessage());
                }
                break;
            }
            case "REPLICA": {
                try {
                    this.server.getPeerManager().registerPeer(channel, nioChannel, this.server.generatePID(), pid,
                            message.safeCastPayload(AddrServerRecord.class));
                } catch (IOException ioe) {
                    System.err.println("Network error occurred during REPLICA registration: " + ioe.getMessage());
                }
            }
            default: System.err.println("Unrecognized sender role for REGISTER: " + message.getSenderRole());
        }
//        switch (message.getSenderRole()) {
//            case "CHATSERVER":
//                if ("ClientCount".equals(message.getObjectType())) {
//                    //server.updateClientCount(channel, message.getPayload());
//                } else if ("ChatServerRecord".equals(message.getObjectType())) {
//                    //server.updateChatServerInfo(channel, message.getPayload());
//                }
//                break;
//
//            case "PRIMARY":
//                if ("AddrServerRecord".equals(message.getObjectType())) {
//                    //server.updateAddrServerInfo(channel, message.getPayload());
//                }
//                break;
//
//            case "REPLICA":
//                if ("ChatServerRecord".equals(message.getObjectType())) {
//                    //server.replicaUpdateChatServerInfo(channel, message.getPayload());
//                }
//                break;
//
//            default:
//                System.err.println("Unrecognized sender role for UPDATE: " + message.getSenderRole());
//        }
    }

    /**
     * Handles an update message based on the sender role and object type.
     *
     * @param channel The channel from which the message originated.
     * @param message The received update message.
     */
    private void handleUpdate(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getSenderRole()) {
            case "CHATSERVER":
                if ("ClientCount".equals(message.getObjectType())) {
                    //server.updateClientCount(channel, nioChannel, message.getPayload());
                } else if ("ChatServerRecord".equals(message.getObjectType())) {
                    //server.updateChatServerInfo(channel, nioChannel, message.getPayload());
                }
                break;

            case "PRIMARY":
                if ("AddrServerRecord".equals(message.getObjectType())) {
                    //server.updateAddrServerInfo(channel, nioChannel, message.getPayload());
                }
                break;

            case "REPLICA":
                if ("ChatServerRecord".equals(message.getObjectType())) {
                    //server.replicaUpdateChatServerInfo(channel, nioChannel, message.getPayload());
                }
                break;

            default:
                System.err.println("Unrecognized sender role for UPDATE: " + message.getSenderRole());
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
            case "CHATSERVER":
                if ("AllAddrServerInfo".equals(message.getObjectType())) {
                    //server.sendAddrServerInfo(channel);
                }
                break;

            case "REPLICA":
                if ("AllChatServerInfo".equals(message.getObjectType())) {
                    //server.sendChatServerInfo(channel);
                }
                break;

            default:
                System.err.println("Unrecognized sender role for REQUEST: " + message.getSenderRole());
        }
    }

    /**
     * Handles a ping request, typically used for failure detection.
     *
     * @param channel The channel from which the ping request originated.
     * @param message The received ping message.
     */
    private void handlePing(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        if ("PRIMARY".equals(message.getTargetRole())) {
            //server.respondToPing(channel);
        }
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
        //server.processElectionMessage(channel, nioChannel, message.getPayload());
    }
}

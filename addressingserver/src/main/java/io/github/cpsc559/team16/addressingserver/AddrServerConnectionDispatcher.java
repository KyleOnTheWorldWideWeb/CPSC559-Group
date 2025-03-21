package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Handles incoming network messages, categorizing them based on message type (`msgType`),
 * sender role (`senderRole`), and object type (`objectType`).
 * <p>
 * The dispatcher processes messages in a cascading order:
 * </p>
 * <ul>
 *     <li><strong>Step 1:</strong> Categorize based on `msgType` (UPDATE, REQUEST, PING, NOTIFICATION, ELECTION)</li>
 *     <li><strong>Step 2:</strong> Filter by `senderRole` (PRIMARY, REPLICA, CHATSERVER, CLIENT)</li>
 *     <li><strong>Step 3:</strong> Handle based on `objectType` (ChatServerRecord, AddrServerRecord, etc.)</li>
 * </ul>
 * <p>
 * This ensures messages are handled efficiently while maintaining structured communication.
 * </p>
 */
public class AddrServerConnectionDispatcher implements NetworkManager.ConnectionDispatcher {
    private final AddressingServer server;

    /**
     * Constructs a request dispatcher for handling incoming messages.
     *
     * @param server The {@link AddressingServer} instance this dispatcher is associated with.
     */
    public AddrServerConnectionDispatcher(AddressingServer server) {
        this.server = server;
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
            case "UPDATE":
                handleUpdate(channel, message);
                break;

            case "REQUEST":
                handleRequest(channel, message);
                break;

            case "PING":
                handlePing(channel, message);
                break;

            case "NOTIFICATION":
                handleNotification(channel, message);
                break;

            case "ELECTION":
                handleElection(channel, message);
                break;

            default:
                System.err.println("Unrecognized message type: " + message.getMsgType());
                break;
        }
    }

    /**
     * Handles an update message based on the sender role and object type.
     *
     * @param channel The channel from which the message originated.
     * @param message The received update message.
     */
    private void handleUpdate(SocketChannel channel, BaseAddrServerMessage<?> message) {
        switch (message.getSenderRole()) {
            case "CHATSERVER":
                if ("ClientCount".equals(message.getObjectType())) {
                    //server.updateClientCount(channel, message.getPayload());
                } else if ("ChatServerRecord".equals(message.getObjectType())) {
                    //server.updateChatServerInfo(channel, message.getPayload());
                }
                break;

            case "PRIMARY":
                if ("AddrServerRecord".equals(message.getObjectType())) {
                    //server.updateAddrServerInfo(channel, message.getPayload());
                }
                break;

            case "REPLICA":
                if ("ChatServerRecord".equals(message.getObjectType())) {
                    //server.replicaUpdateChatServerInfo(channel, message.getPayload());
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
    private void handleRequest(SocketChannel channel, BaseAddrServerMessage<?> message) {
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
    private void handlePing(SocketChannel channel, BaseAddrServerMessage<?> message) {
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
    private void handleNotification(SocketChannel channel, BaseAddrServerMessage<?> message) {
        if ("ServerFailure".equals(message.getObjectType())) {
            //server.handleServerFailure(channel, message.getPayload());
        }
    }

    /**
     * Handles election-related messages, used for leader election among Addressing Servers.
     *
     * @param channel The channel from which the election message originated.
     * @param message The received election message.
     */
    private void handleElection(SocketChannel channel, BaseAddrServerMessage<?> message) {
        //server.processElectionMessage(channel, message.getPayload());
    }
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
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
public class AddrServerConnectionDispatcher {
    private final AddressingServer server;

    /**
     * Constructs a request dispatcher for handling incoming messages.
     *
     * @param server The {@link AddressingServer} instance this dispatcher is associated with.
     */
    public AddrServerConnectionDispatcher(AddressingServer server) {
        this.server = server;
    }

//    /**
//     * Dispatches a received message based on its type, sender role, and object type.
//     *
//     * @param channel The {@link SocketChannel} representing the sender of the message.
//     * @param message The parsed {@link BaseAddrServerMessage} containing the message details.
//     * @throws IOException If an error occurs while processing the message.
//     */
//    public void dispatch(ServerSocketChannel listenerSC, SocketChannel channel) throws IllegalStateException {
//        if (listenerSC.equals(server)) {
//            // Guard Condition. Replicas/Backups outnumber Primary -> check for REPLICA first.
//            if (server.getConfig().getRole() == AddrServerConfig.ServerRole.REPLICA) {
//                server.replicaHandlePeerConnection(channel);
//
//            }
//            else if (server.getConfig().getRole() == AddrServerConfig.ServerRole.PRIMARY){
//                server.handleReplicaConnection(channel);
//
//            }
//            else {
//                throw new IllegalStateException("Unexpected server role: " + server.getConfig().getRole()
//                        + ". Halting process ID: " + server.getConfig().getPID() + ".");
//            }
//        } else if (listenerSC.equals(server.getChatServerlistenerSCChannel())) {
//            server.handleChatServerRegistration(channel);
//        }
//
//    }

}

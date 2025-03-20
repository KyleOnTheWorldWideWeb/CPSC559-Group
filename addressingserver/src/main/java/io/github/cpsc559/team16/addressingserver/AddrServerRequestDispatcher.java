package io.github.cpsc559.team16.addressingserver;
import io.github.cpsc559.team16.common.utilities.NetworkManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Handles incoming connection requests and delegates them to the appropriate processing method
 * based on the type of listener (client, replica, or chat server).
 * <p>
 * This dispatcher ensures that different types of incoming connections are routed correctly
 * within the {@link AddressingServer}. It differentiates between:
 * <ul>
 *     <li>Client connections → Routed to {@code handleClientConnection}.</li>
 *     <li>Replica connections → Handled differently depending on whether this server is a {@code PRIMARY} or {@code REPLICA}.</li>
 *     <li>Chat server connections → Routed to {@code handleChatServerRegistration}.</li>
 * </ul>
 * If an unexpected server role is detected, an {@link IllegalStateException} is thrown, halting execution.
 */
public class AddrServerRequestDispatcher implements NetworkManager.ConnectionDispatcher {
    private final AddressingServer server;

    /**
     * Constructs a request dispatcher for handling new incoming connections.
     *
     * @param server The {@link AddressingServer} instance this dispatcher is associated with.
     */
    public AddrServerRequestDispatcher(AddressingServer server) {
        this.server = server;
    }

    /**
     * Dispatches incoming connection requests based on the listener type.
     * <p>
     * This method determines whether the incoming connection originates from:
     * <ul>
     *     <li>A client requesting a chat server assignment.</li>
     *     <li>A replica (backup) server establishing peer-to-peer communication.</li>
     *     <li>A chat server attempting to register with the addressing server.</li>
     * </ul>
     * Based on this classification, the appropriate handling method is invoked.
     * </p>
     *
     * @param channel  The {@link SocketChannel} representing the incoming connection.
     * @param listener The {@link ServerSocketChannel} that accepted the connection.
     * @throws IllegalStateException If the server is in an unexpected role (neither PRIMARY nor REPLICA).
     */
    @Override
    public void dispatch(SocketChannel channel) throws IllegalStateException {
        try {
            int port = (InetSocketAddress) channel.getLocalAddress().getPort();
        } catch (ClosedChannelException cce) {
            System.out.println("Channel is closed: " + cce.getMessage());
        } catch (IOException ioe) {

        }

        if () {
            server.handleClientConnection(channel);

        } else if (listener.equals(server.getReplicaListenerChannel())) {
            // Guard Condition. Replicas/Backups outnumber Primary -> check for REPLICA first.
            if (server.getConfig().getRole() == AddrServerConfig.ServerRole.REPLICA) {
                server.replicaHandlePeerConnection(channel);

            }
            else if (server.getConfig().getRole() == AddrServerConfig.ServerRole.PRIMARY){
                server.handleReplicaConnection(channel);
                
            }
            else {
                throw new IllegalStateException("Unexpected server role: " + server.getConfig().getRole()
                        + ". Halting process ID: " + server.getConfig().getPID() + ".");
            }
        } else if (listener.equals(server.getChatServerListenerChannel())) {
            server.handleChatServerRegistration(channel);
        }

    }
}

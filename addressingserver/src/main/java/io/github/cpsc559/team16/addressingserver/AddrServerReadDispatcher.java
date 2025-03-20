package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.utilities.NetworkManager;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * Handles read events from registered {@code SocketChannel}'s and routes them based on the server's role.
 * <p>
 * This class is responsible for delegating read events to the appropriate handlers.
 * It differentiates between:
 * </p>
 * <ul>
 *     <li>{@code PRIMARY} - Processes incoming updates from replicas.</li>
 *     <li>{@code REPLICA} - Receives data from the primary server.</li>
 * </ul>
 * If the server role is unrecognized, an {@link IllegalStateException} is thrown to halt execution.
 */
public class AddrServerReadDispatcher implements NetworkManager.ReadDispatcher {
    private final AddressingServer server;

    public AddrServerReadDispatcher(AddressingServer server) {
        this.server = server;
    }


    /**
     * Dispatches a read event based on the current role of the AddressingServer.
     * <p>
     * This method determines how to process incoming data depending on whether the server
     * is acting as a {@code PRIMARY} or a {@code REPLICA}. If the server is in an unknown state,
     * an {@link IllegalStateException} is thrown to halt execution.
     * </p>
     *
     *
     * @throws IllegalStateException If the server role is not recognized (neither PRIMARY nor REPLICA).
     */
    @Override
    public void dispatch(SocketChannel channel, String message) throws IllegalStateException {
        // Guard Condition. Replicas/Backups outnumber Primary -> check for REPLICA first.
        if (server.getConfig().getRole() == AddrServerConfig.ServerRole.REPLICA) {
            //server.replicaHandleReadEvent(key);
        }
        else if (server.getConfig().getRole() == AddrServerConfig.ServerRole.PRIMARY) {
            //server.primaryHandleReadEvent(key);
        }
        else {
            throw new IllegalStateException("Unexpected server role: " + server.getConfig().getRole()
                    + ". Halting process ID: " + server.getConfig().getPID() + ".");
        }
    }
}

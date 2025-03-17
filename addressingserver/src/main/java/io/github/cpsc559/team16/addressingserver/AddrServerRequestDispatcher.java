package io.github.cpsc559.team16.addressingserver;
import io.github.cpsc559.team16.common.utilities.NetworkManager;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class AddrServerRequestDispatcher implements NetworkManager.ConnectionDispatcher {
    private final AddressingServer server;

    public AddrServerRequestDispatcher(AddressingServer server) {
        this.server = server;
    }

    @Override
    public void dispatch(SocketChannel channel, ServerSocketChannel listener) throws IOException {
        if (listener.equals(server.getClientListenerChannel())) {
            server.handleClientConnection(channel);
        } else if (listener.equals(server.getReplicaListenerChannel())) {
            server.handleReplicaConnection(channel);
        } else if (listener.equals(server.getChatServerListenerChannel())) {
            server.handleChatServerRegistration(channel);
        }
    }
}

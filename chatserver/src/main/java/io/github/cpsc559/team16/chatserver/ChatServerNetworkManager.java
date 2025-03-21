package io.github.cpsc559.team16.chatserver;

import io.github.cpsc559.team16.common.utilities.NetworkManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * Implementation of {@link NetworkManager} for the Chat Server.
 * This handles incoming client connections and communication with other chat servers.
 */
public class ChatServerNetworkManager implements NetworkManager {
    private final Selector selector;

    public ChatServerNetworkManager() throws IOException {
        this.selector = Selector.open();
    }

    @Override
    public Selector getSelector() {
        return selector;
    }

    @Override
    public ServerSocketChannel openListenerChannel(int port) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(port));
        channel.register(selector, SelectionKey.OP_ACCEPT);
        return channel;
    }

    @Override
    public void openPersistentChannel(SocketChannel channel) throws IOException {
        channel.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void startEventLoop(ConnectionDispatcher dispatcher1, ReadDispatcher dispatcher2) throws IOException {



    }
}

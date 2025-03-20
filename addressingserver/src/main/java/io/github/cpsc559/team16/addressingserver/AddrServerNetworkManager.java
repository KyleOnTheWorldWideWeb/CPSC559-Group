package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;

public class AddrServerNetworkManager implements NetworkManager {
    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the AddressingServer to monitor multiple channels using a single thread.
     */
    private final Selector selector;


    private PeerManager peerManager;

    public void setReplicaManager(PeerManager peerManager) {
        this.peerManager = peerManager;
    }


    public AddrServerNetworkManager(PeerManager peerManager) throws IOException {
        this.peerManager = peerManager;
        selector = Selector.open();
    }

    /**
     * Opens and binds a ServerSocketChannel to the specified port.
     * <p>
     * This method is used to create a listener channel that monitors incoming
     * connection requests on a given port. The channel is set to non-blocking mode,
     * allowing it to be used with a Selector for persistent asynchronous I/O operations.
     * </p>
     *
     * @param port The port number to bind the channel to.
     * @return The opened ServerSocketChannel.
     * @throws IOException If an error occurs while opening or binding the channel.
     */
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
    public Selector getSelector() {
        return selector;
    }


    /**
     * Begins the main event loop for the {@code AddressingServer} process by
     * listening for incoming connections on the ports defined in its instance of the
     * {@code AddrServerConfig} class -
     * <ul>
     *     <li>{@code config.clientPort}</li>
     *     <li>{@code config.replicaPort}</li>
     *     <li>{@code config.chatServerPort}</li>
     * </ul>
     * <p>
     * This method blocks until an event occurs on a registered channel (i.e., a connection request
     * is received). When an event is detected, it retrieves the corresponding SelectionKey,
     * processes the event, and removes the key(event) from the selector to prevent re-processing.
     * </p>
     *
     * @param requestDispatcher The dispatcher responsible for routing accepted connections to the appropriate handlers.
     * @throws IOException If an I/O error occurs while selecting or processing events.
     */
    @Override
    public void startEventLoop(NetworkManager.ConnectionDispatcher requestDispatcher,
                               NetworkManager.ReadDispatcher readDispatcher) throws IOException {
        while (true) {
            // Any thread calling this method blocks until an event occurs on a channel registered with the `selector`.
            selector.select();
            /*
             * When you register a channel with a selector, you get back a SelectionKey.
             * Here, we iterate through all keys (channels) that are "ready" for an I/O operation.
             * We know that at least one such key exists because of the preceding `selector.select()` method invocation.
             */
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {               // Loop until there are no more keys (channels with I/O events).
                SelectionKey key = keys.next();    // Retrieve a new key
                keys.remove();                     // Remove the key used in the last iteration of this loop.

                if (!key.isValid()) {               // Skip current loop iteration if the key is invalid
                    continue;
                }

                /*
                 * Establishing a new connection. `isAcceptable` returns true if a ServerSocketChannel registered
                 * with `selector` is ready to accept (detected) a new connection. SocketChannel instances
                 * registered with the `selector` should not appear here.
                 */
                if (key.isAcceptable()) {
                    /*
                     * `listenerSC` is not a "new" channel - it is one of the ServerSocketChannels we registered
                     * with the selector in the `initializeChannels` method.
                     */
                    ServerSocketChannel listenerSC = (ServerSocketChannel) key.channel();

                    // We use a `SocketChannel` for established connections. `channel.isAcceptable()` will ALWAYS return False.
                    SocketChannel channel = listenerSC.accept();

                    // All requests on this channel will be sent to "non-blocking"
                    channel.configureBlocking(false);
                    // Dispatches the new connection to the appropriate handler based on the listenerSC type.
                    try {
                        requestDispatcher.dispatch(channel, listenerSC);
                    } catch (IllegalStateException ise) {
                        // Any AddressingServer without a role is a problem waiting to happen.
                        // Best for it to finish execution and take a long nap.
                        System.err.println("Error occurred while dispatching new network connection event -->" + ise.getMessage());
                    }
                }

                /*
                 * Only keys tied to channels that were registered with OP_READ
                 * (as we are doing above with `newChannel`) will return True when invoking `isReadable()`.
                 * Typically, only a SocketChannel would ever be registered with OP_READ.
                 */
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    NIOMessageChannel nioChannel = peerManager.getPeerChannels().get(channel);
                    try {
                        /* Reading a message throws a custom ConnectionClosedException if the SocketChannel
                         * associated with it has been closed -> the key should be removed along with both channels.
                         */
                        String message = nioChannel.receiveMessage();
                        readDispatcher.dispatch(channel, message);
                    } catch (ConnectionClosedException cce) {
                        NIOMessageChannel ch = peerManager.getPeerChannels().get(channel);
                        Long pid = ch.getServerPID();
                        peerManager.removeNIOChannelByKey(channel);
                        key.cancel();
                        channel.close();
                        System.out.println("Removing closed peer (Server ID: " + pid + "): " + cce.getMessage());
                    } catch (IllegalStateException ise) {
                        System.err.println("Error occurred while dispatching read event -->" + ise.getMessage());
                        ise.printStackTrace();
                        return; // Any AddressingServer without a role is a problem waiting to happen.
                        // Best for it to finish execution and take a long nap.
                    }
                }
            }
        }
    }
}

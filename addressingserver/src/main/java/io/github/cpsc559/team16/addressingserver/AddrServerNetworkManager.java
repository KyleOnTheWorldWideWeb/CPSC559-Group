package io.github.cpsc559.team16.addressingserver;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import io.github.cpsc559.team16.common.utilities.NetworkManager;

public class AddrServerNetworkManager implements NetworkManager {
    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the AddressingServer to monitor multiple channels using a single thread.
     */
    private final Selector selector;



    private ReplicaManager replicaManager;
    public void setReplicaManager(ReplicaManager replicaManager) {
        this.replicaManager = replicaManager;
    }


    private ChatServerRegistry chatServerRegistry;
    public void setChatServerRegistry(ChatServerRegistry chatServerRegistry) {
        this.chatServerRegistry = chatServerRegistry;
    }

    public AddrServerNetworkManager() throws IOException {
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
    public void startEventLoop(NetworkManager.ConnectionDispatcher requestDispatcher, NetworkManager.ReadDispatcher readDispatcher) throws IOException {
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
                    requestDispatcher.dispatch(channel, listenerSC);
                }

                /*
                 * Only keys tied to channels that were registered with OP_READ
                 * (as we are doing above with `newChannel`) will return True when invoking `isReadable()`.
                 * Typically, only a SocketChannel would ever be registered with OP_READ.
                 */
                if (key.isReadable()) {

                //    {
                    readDispatcher.dispatch(key);
                //    } catch (IOException ioe) {
                //        System.err.println("Error with Replica read event");
                //    }
//                    // This branch handles persistent connections, such as replica channels.
//                    SocketChannel channel = (SocketChannel) key.channel();
//                    ByteBuffer buffer = ByteBuffer.allocate(1024);
//                    int bytesRead = channel.read(buffer);
//                    if (bytesRead == -1) {
//                        key.cancel();
//                        channel.close();
//                        System.out.println("Replica connection closed by remote host.");
//                        continue;
//                    }
//                    buffer.flip();
//                    String jsonMessage = StandardCharsets.UTF_8.decode(buffer).toString();
//                    System.out.println("Received update message: " + jsonMessage);
//
//                    // You need to have your local ChatServerRegistry available (e.g., from your AddressingServer)
//                    replicaManager.handleUpdateMessage(jsonMessage, this.chatServerRegistry);
                }
            }
        }
    }


    public interface ConnectionDispatcher {
        void dispatch(SocketChannel channel, ServerSocketChannel listenerSC) throws IOException;
    }

    public interface ReadDispatcher {
        void dispatch(SelectionKey key) throws IOException;
    }
}

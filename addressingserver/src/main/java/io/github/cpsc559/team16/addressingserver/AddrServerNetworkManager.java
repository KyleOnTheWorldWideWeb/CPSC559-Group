package io.github.cpsc559.team16.addressingserver;

// For thread management
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;

import static io.github.cpsc559.team16.common.messaging.MessageDeserializer.deserializeMessage;

/**
 * Manages the network interactions for the AddressingServer.
 * <p>
 * This class handles accepting incoming connections, registering them for non-blocking I/O,
 * and dispatching read events. It utilizes a {@code PersistentConnectionManager} to track
 * and manage persistent connections for chat servers and replicas.
 * </p>
 */
public class AddrServerNetworkManager implements NetworkManager {

    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the AddressingServer to monitor multiple channels using a single thread.
     */
    private final Selector selector;

    /**
     * Manages persistent connections for replicas and chat servers.
     */
    private final PersistentConnectionManager persistentConnectionManager;

    private final PeerManager peerManager;

    private final ExecutorService executorService;


    /**
     * Constructs the network manager for the AddressingServer.
     *
     * @param persistentConnectionManager The manager that handles persistent connections.
     * @throws IOException If the selector fails to initialize.
     */
    public AddrServerNetworkManager(PersistentConnectionManager persistentConnectionManager, PeerManager peerManager) throws IOException {
        this.persistentConnectionManager = persistentConnectionManager;
        this.peerManager = peerManager;
        this.selector = Selector.open();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
    public void startEventLoop(ConnectionDispatcher requestDispatcher,
                               ReadDispatcher readDispatcher) throws IOException {
        while (true) {
            // Any thread calling this method blocks until an event occurs on a channel registered with the `selector`.
            selector.select();

            // Iterate through all keys (channels) that are "ready" for an I/O operation.
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) {  // Skip current loop iteration if the key is invalid
                    continue;
                }

                /*
                 * Establishing a new connection. `isAcceptable` returns true if a ServerSocketChannel registered
                 * with `selector` is ready to accept (detected) a new connection.
                 */
                if (key.isAcceptable()) {
                    ServerSocketChannel listenerSC = (ServerSocketChannel) key.channel();
                    SocketChannel channel = listenerSC.accept();

                    if (channel != null) {
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Accepted new connection from: " + channel.getRemoteAddress());
                    }
                }

                /*
                 * Handling read events for registered channels.
                 * Only keys tied to channels registered with OP_READ will trigger `isReadable()`.
                 */
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();

                    try {
                        // Retrieve existing NIOMessageChannel for persistent connections, or create a new one for clients
                        NIOMessageChannel persistentNioChannel = persistentConnectionManager.getNIOChannel(channel);

                        // Handle Client Requests (We don't keep a persistent connection with clients)
                        if (persistentNioChannel == null) {
                            final NIOMessageChannel tempNioChannel = new NIOMessageChannel(channel);

                            // Read the message
                            String messageJson = tempNioChannel.receiveMessage();
                            if (messageJson == null) {
                                return; // No message available
                            }

                            // Deserialize message without assuming a payload class
                            BaseAddrServerMessage<?> message = deserializeMessage(messageJson);
                            if (message == null) {
                                System.err.println("Could not deserialize incoming message.");
                                return;
                            }

                            // Process message asynchronously to prevent blocking the event loop
                            executorService.submit(() -> {
                                try {
                                    readDispatcher.dispatch(channel, tempNioChannel, message);

                                    // Close the channel if it is a temporary client connection
                                    if (!persistentConnectionManager.isPersistent(channel)) {
                                        channel.close();
                                        key.cancel();
                                    }
                                } catch (IOException e) {
                                    System.err.println("Error processing client request: " + e.getMessage());
                                    try {
                                        channel.close();
                                    } catch (IOException ignored) {}
                                    key.cancel();
                                }
                            });
                        }
                        // Handle persistent connection reads (Chat Servers & Replicas)
                        else {
                            // Read the message. This throws a ConnectionClosedException if the SocketChannel
                            // linked to the NIOChannel has been closed (e.g. the remote process closed the connection)
                            String messageJson = persistentNioChannel.receiveMessage();
                            if (messageJson == null) {
                                return;
                            }

                            // Deserialize message without assuming a payload class
                            BaseAddrServerMessage<?> message = deserializeMessage(messageJson);
                            if (message == null) {
                                System.err.println("Could not deserialize incoming message.");
                                return;
                            }

                            // Dispatch message for processing
                            readDispatcher.dispatch(channel, persistentNioChannel, message);
                        }

                    } catch (ConnectionClosedException cce) {
                        // Retrieve the NIOMessageChannel before removing it
                        NIOMessageChannel ch = persistentConnectionManager.getNIOChannel(channel);
                        if (ch != null) {
                            Long pid = ch.getServerPID();
                            persistentConnectionManager.removeConnection(channel);
                            peerManager.removeChannel(channel);
                            System.out.println("Removing closed peer (Server ID: " + pid + "): " + cce.getMessage());
                        }
                        key.cancel();
                        channel.close();
                    } catch (IOException e) {
                        System.err.println("Error reading from channel: " + e.getMessage());
                        key.cancel();
                        try {
                            channel.close();
                        } catch (IOException ignored) {}
                    }
                }


            }
        }
    }
}

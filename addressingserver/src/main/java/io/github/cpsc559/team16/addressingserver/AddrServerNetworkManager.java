package io.github.cpsc559.team16.addressingserver;

// For thread management
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.messaging.*;

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
public class AddrServerNetworkManager {

    /**
     * The ServerSocketChannel that listens for incoming connection requests from chat servers.
     * When a connection is accepted, a new data channel is created for communicating with that chat server.
     */
    private ServerSocketChannel chatServerListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from clients.
     * When a connection is accepted, a new data channel is created for communicating with that client.
     * Clients connect to this channel to be assigned to an active chat server.
     */
    //private ServerSocketChannel clientListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming connection requests from replica servers.
     * This channel is used for establishing (but not maintaining) peer-to-peer communication between
     * the primary addressing server and its backup replicas.
     */
    private ServerSocketChannel peerListenerChannel;

    /**
     * The ServerSocketChannel that listens for incoming TCP health check requests.
     * <p>
     * This channel responds to external health probes by sending a lightweight response,
     * indicating the server is alive and accepting connections. It is not registered
     * as a persistent channel and is closed immediately after replying.
     * </p>
     */
    private ServerSocketChannel healthCheckListenerChannel;

    /**
     * The Selector used for multiplexing non-blocking I/O operations on the registered channels.
     * This allows the AddressingServer to monitor multiple channels using a single thread.
     */
    private final Selector selector;

    /**
     * Manages all peer-to-peer communication and synchronization tasks between this
     * {@code AddressingServer} and its peers.
     * <p>
     * The {@code PeerManager} handles replica registration, state broadcasting, and
     * persistent connection tracking for other {@code AddressingServer} processes in the network.
     * It maintains a map of {@link SocketChannel} to {@link NIOMessageChannel} for message exchange.
     * </p>
     */
    private final PeerManager peerManager;

    /**
     * Handles all communication, registration, and record synchronization
     * between this {@code AddressingServer} and the network of {@code ChatServer}s.
     * <p>
     * The {@code ChatServerManager} maintains persistent socket connections to registered chat servers,
     * pushes network updates to them, and manages updates to the shared {@link ChatServerRegistry}.
     * </p>
     */
    private final ChatServerManager chatServerManager;

    /**
     * The network configuration for this {@code AddressingServer} process.
     */
    private final AddrServerConfig config;

    /**
     * A fixed-size thread pool used to offload network I/O processing from the main selector loop.
     * <p>
     * This {@code ExecutorService} executes read and dispatch tasks asynchronously to prevent
     * the main event loop from blocking during expensive operations such as deserialization,
     * registration, or broadcast updates (multi-message streams).
     * </p>
     * <p>
     * The pool size is typically based on the number of available CPU cores on the host system,
     * but can be adjusted for high-throughput scenarios.
     * </p>
     */
    private final ExecutorService executorService;

    /**
     * Constructs the network manager for the AddressingServer.
     *
     * @throws IOException If the selector fails to initialize.
     */
    public AddrServerNetworkManager(PeerManager peerManager, ChatServerManager csManager, AddrServerConfig config) throws IOException {
        this.peerManager = peerManager;
        this.chatServerManager = csManager;
        this.selector = Selector.open();
        this.config = config;
        /*
         Creates a pool with a fixed number of threads - usually one per CPU core. This will allow us to benefit from
         some level of asynchronous message handling - preventing the main event-loop from blocking during events that
         necessitate many I/O operations. Using a thread pool will allow us to avoid expensive thread creation/destruction,
         while also limiting idle threads due to the constant network traffic the {@code AddressingSerer}'s will experience.
         */
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        // Can try and increase the pool to see if network I/O demands it.
        //this.executorService = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());
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

    public ServerSocketChannel openListenerChannel(int port) throws IOException {
        try {
            ServerSocketChannel channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(new InetSocketAddress(port));
            channel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listener channel opened on port " + port);
            return channel;
        } catch (IOException e) {
            System.err.println("Failed to open listener channel on port " + port + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Opens all listener channels required for normal operation and health monitoring.
     * <p>
     * This method binds and registers non-blocking {@code ServerSocketChannel}s
     * for:
     * <ul>
     *     <li>Client connections (used to assign ChatServers)</li>
     *     <li>Replica (peer) connections</li>
     *     <li>ChatServer registration</li>
     *     <li>Health check requests (used for Docker healthchecks) on port 5050</li>
     * </ul>
     * Each channel is registered with the internal {@code Selector} for accept events.
     * </p>
     *
     * @param clientPort      The port used for incoming client connections.
     * @param peerPort        The port used for replica-to-primary communication.
     * @param chatServerPort  The port used for ChatServer registration.
     */
    public void openListenerChannels(int clientPort, int peerPort, int chatServerPort) {
        try {
            openListenerChannel(clientPort);
            this.peerListenerChannel = openListenerChannel(peerPort);
            this.chatServerListenerChannel = openListenerChannel(chatServerPort);
            this.healthCheckListenerChannel = openListenerChannel(5050); // static port for health checks
        } catch (IOException e) {
            System.err.println("Failed to open a listener channel.");
            // TODO - Could add failure handling here, or just exit the process and spin up a new container.
        }
    }

    /**
     * Registers a {@link SocketChannel} with the internal {@code Selector} to listen for read events.
     * <p>
     * This method is used for persistent peer-to-peer or server-to-server connections, allowing
     * the selector to monitor the channel for non-blocking reads via {@code SelectionKey.OP_READ}.
     * The channel is also set to non-blocking mode.
     * </p>
     *
     * @param channel The {@code SocketChannel} to register for persistent read monitoring.
     * @throws IOException If an error occurs during configuration or registration.
     */
    public void openPersistentChannel(SocketChannel channel) throws IOException {
        try {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            System.out.println("Registered persistent channel: " + channel.getRemoteAddress());
        } catch (IOException e) {
            System.err.println("Failed to register persistent channel: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Determines whether the specified {@link SocketChannel} is associated with a persistent server-to-server connection.
     * <p>
     * Persistent connections are long-lived channels used for internal communication between
     * {@code AddressingServer}s (peers) and {@code ChatServer}s. These are stored and tracked
     * using their respective manager classes.
     * </p>
     *
     * @param channel the {@code SocketChannel} to inspect.
     * @return {@code true} if the channel is known to be persistent (i.e., belongs to a peer or chat server), {@code false} otherwise.
     */
    private boolean isPersistentConnection(SocketChannel channel) {
        return peerManager.getChannels().containsKey(channel)
                || chatServerManager.getChannels().containsKey(channel);
    }

    /**
     * Retrieves the {@link NIOMessageChannel} wrapper for a known persistent connection.
     * <p>
     * This method searches the internal maps of both the {@code PeerManager} and {@code ChatServerManager}
     * to find the {@code NIOMessageChannel} corresponding to the provided {@link SocketChannel}.
     * </p>
     * <p>
     * If the channel is not found in either manager, the method returns {@code null}.
     * </p>
     *
     * @param channel the {@code SocketChannel} to look up.
     * @return the associated {@code NIOMessageChannel}, or {@code null} if not found.
     */
    private NIOMessageChannel getKnownPersistentChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerManager.getChannels().get(channel);
        if (ch != null) return ch;
        return chatServerManager.getChannels().get(channel); // Will return null if it doesn't exist (which is what we want)
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     *     <li>Logs the reason for cleanup (remote disconnect or local I/O failure).</li>
     *     <li>Removes the connection from either the {@code PeerManager} or {@code ChatServerManager}.</li>
     *     <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *
     * @param channel the {@code SocketChannel} being cleaned up.
     * @param key the {@code SelectionKey} associated with the channel, used for deregistration.
     * @param cce {@code true} if the cleanup is due to a remote disconnect (i.e., {@link ConnectionClosedException}),
     *            {@code false} if due to a local I/O failure.
     */
    private void cleanupPersistentConnection(SocketChannel channel, SelectionKey key, Boolean cce) {
        System.err.printf("Channel cleanup triggered for -> %s - due to -> (%s)\n",
                channel,
                cce ? "remote process disconnection." : "I/O failure."
        );
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                Long pid = ch.getServerPID();
                if (chatServerManager.getChannels().containsKey(channel)) {
                    chatServerManager.removeRemoteProcess(channel);
                } else if (peerManager.getChannels().containsKey(channel)) {
                    peerManager.removeRemoteProcess(channel);
                }
            }
        }
        key.cancel();
        try {
            channel.close();
        } catch (IOException ignored) {}  // if the channel is already closed, we don't need to do anything.
    }

    /**
     * Retrieves the internal {@link Selector} used for multiplexing non-blocking I/O operations.
     * <p>
     * The {@code Selector} enables the {@code AddressingServer} to monitor multiple registered
     * {@link SocketChannel}s and {@link ServerSocketChannel}s for events such as connection
     * requests or available data. This method provides access to the selector for components
     * that need to register new channels or monitor channel readiness (e.g., read or accept).
     * </p>
     *
     * @return the internal {@code Selector} used by this {@code AddrServerNetworkManager}.
     */

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
     * @throws IOException If an I/O error occurs while selecting or processing events.
     */
    public void startEventLoop(AddrServerReadDispatcher readDispatcher) throws IOException {
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

                    // Connection must have closed before we got to the request -> skip to next event.
                    if (channel == null) { continue; }
                    channel.configureBlocking(true); // Allow blocking read for initial handshake only. We need to ensure the entire message has arrived.

                    if (listenerSC.equals(healthCheckListenerChannel)) {
                        try {
                            String response = this.config.getPID() + "\n";
                            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
                            while (buffer.hasRemaining()) {
                                channel.write(buffer);
                            }
                            channel.close(); // one-time response -> close channel
                        } catch (IOException e) {
                            System.err.println("Failed to respond to health check ping: " + e.getMessage());
                        }
                        continue;
                    }
                    NIOMessageChannel nioChannel = new NIOMessageChannel(channel);
                    String firstMsg = nioChannel.receiveMessage();

                    if (firstMsg == null) {
                        System.err.println("Connection dropped: no initial message received.");
                        channel.close();
                        continue;
                    }
                    /*
                     * NOTE: The only connections (initial connections, not persistent connections)
                     * AddressingServers accept are those with MessageType.REGISTER or MessageType.ELECTION
                     */
                    BaseAddrServerMessage<?> message = deserializeMessage(firstMsg);
                    if (message == null ) {
                        System.err.println("Connection rejected: NULL message on connection request.");
                        continue;
                    } else {
                        String messageType = message.getMsgType();
                        if (!messageType.equals(MessageTypes.REGISTER) && !messageType.equals(MessageTypes.ELECTION)) {
                            System.err.println("Connection rejected: initial message must be REGISTER or ELECTION.");
                            channel.close();
                            continue;
                        }
                    }

                    if (listenerSC.equals(chatServerListenerChannel)) {
                        openPersistentChannel(channel);
                        chatServerManager.getChannels().put(channel, nioChannel);
                    } else if (listenerSC.equals(peerListenerChannel)) {
                        openPersistentChannel(channel);
                        peerManager.getChannels().put(channel, nioChannel);
                    }
                        /*
                         The only port we haven't checked by this point is the one designated for the client.
                         We don't store persistent channels for clients.
                         */
                    //executorService.submit(() -> {
                        try {
                            readDispatcher.handleRegistration(channel, nioChannel, message);
                            if (!isPersistentConnection(channel)) {
                                // Client process -> close the channel immediately after registration.
                                channel.close();
                                key.cancel();
                            }
                        } catch (ConnectionClosedException cce) {
                            if (isPersistentConnection(channel)) {
                                cleanupPersistentConnection(channel, key, true);
                            } else {
                                try {
                                    channel.close();
                                } catch (IOException ignored) {
                                }
                                key.cancel();
                            }
                        } catch (Exception e) {
                            if (isPersistentConnection(channel)) {
                                cleanupPersistentConnection(channel, key, false);
                            } else {
                                try {
                                    channel.close();
                                } catch (IOException ignored) {
                                }
                                key.cancel();
                            }
                        }
                    //});
                }
                /*
                 * Handling read events for registered channels.
                 * Only keys tied to channels registered with OP_READ will trigger `isReadable()`.
                 */
                else if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();

                    // Retrieve existing NIOMessageChannel for persistent connections.
                    NIOMessageChannel persistentNioChannel = getKnownPersistentChannel(channel);
                    // If it doesn't exist, close the channel and cancel the event key to prevent errors.
                    if (persistentNioChannel == null) {
                        key.cancel();
                        channel.close();
                    }
                    // Handle persistent connection input/read events (Chat Servers & Replicas)
                    else {
                        // Temporarily disable OP_READ to avoid re-triggering on every incoming byte.
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                        // Dispatch message for processing. This will throw errors if the connection is closed or I/O operations fail.
                        // Handle persistent connections asynchronously
                        //executorService.submit(() -> {
                            try {
                                readDispatcher.dispatch(channel, persistentNioChannel);
                                // Now that the data on the input channel has been processed, re-enable read events.
                                if (key.isValid()) {
                                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                                }
                            } catch (ConnectionClosedException cce) {
                                cleanupPersistentConnection(channel, key, true);
                            } catch (IOException ioe) {
                                cleanupPersistentConnection(channel, key, false);
                            }
                        //});
                    }
                }
            }
        }
    }
}

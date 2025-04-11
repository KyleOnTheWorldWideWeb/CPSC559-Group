package io.github.cpsc559.team16.addressingserver;

// For thread management
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentMap;


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
     * Indicates whether the server has been signaled to shut down or exit its main event loop.
     * <p>
     * This flag is marked {@code volatile} to ensure visibility across threads. It is used by the
     * {@link AddrServerNetworkManager} to detect whether a controlled shutdown or restart has been
     * requested (e.g., in response to a restart message or failure condition).
     * </p>
     * <p>
     * When set to {@code true}, the main network event loop will
     * close all connections and exit gracefully, allowing the server
     * to clean up resources and reinitialize.
     * </p>
     */
    private volatile boolean shutdownRequested = false;


    /**
     * Triggers shutdown of the network event loop.
     * Called externally by the {@code AddrServerReadDispatcher}
     * when the AddressingServer needs to exit its main loop (e.g. after a failure message is received).
     */
    public void requestShutdown() {
        this.shutdownRequested = true;
    }


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
     * The network configuration for this {@code AddressingServer} process.
     */
    private final AddrServerConfig config;

    /**
     * Handles cleanup of unresponsive or failed ChatServer and AddressingServer processes.
     * <p>
     * This manager provides logic to remove broken persistent connections and broadcast
     * failure notifications to the remaining network. It is shared with the watchdog
     * and other components that need to remove stale or disconnected processes.
     * </p>
     */
    private final ConnectionCleanupManager cleanupManager;

    /**
     * A thread-safe map of pending synchronization events that require acknowledgment (ACK) messages.
     * <p>
     * Each entry corresponds to a message that was broadcast to peers or chat servers and is still
     * waiting for acknowledgments from one or more recipients. This map is monitored by the watchdog
     * thread to retry, finalize, or fail messages that do not receive timely responses.
     * </p>
     */
    private final ConcurrentMap<Long, PendingEvent> pendingSyncEvents;

    /**
     * A background thread that monitors {@code pendingSyncEvents} for timeouts and retry logic.
     * <p>
     * This watchdog resends unacknowledged messages, tracks retry counts, and invokes cleanup
     * procedures for unresponsive network participants.
     * </p>
     */
    PendingEventWatchdog eventWatchdog;

    /**
     * Returns the {@link PendingEventWatchdog} responsible for monitoring retry timeouts
     * and failed network events.
     *
     * @return the active {@code PendingEventWatchdog} instance for this coordinator
     */
    public PendingEventWatchdog getEventWatchdog() {
        return eventWatchdog;
    }

//    /**
//     * A fixed-size thread pool used to offload network I/O processing from the main selector loop.
//     * <p>
//     * This {@code ExecutorService} executes read and dispatch tasks asynchronously to prevent
//     * the main event loop from blocking during expensive operations such as deserialization,
//     * registration, or broadcast updates (multi-message streams).
//     * </p>
//     * <p>
//     * The pool size is typically based on the number of available CPU cores on the host system,
//     * but can be adjusted for high-throughput scenarios.
//     * </p>
//     */
//    private final ExecutorService executorService;





    /**
     * Constructs the network manager for the AddressingServer.
     *
     * @throws IOException If the selector fails to initialize.
     */
    public AddrServerNetworkManager(ConnectionCleanupManager cleanupManager, AddrServerConfig config, ConcurrentMap<Long, PendingEvent> pendingSyncEvents) throws IOException {
        this.cleanupManager = cleanupManager;
        this.selector = Selector.open();
        this.config = config;
        this.pendingSyncEvents = pendingSyncEvents;
        this.eventWatchdog = new PendingEventWatchdog(pendingSyncEvents, this.cleanupManager,  500);
        /*
         Creates a pool with a fixed number of threads - usually one per CPU core. This will allow us to benefit from
         some level of asynchronous message handling - preventing the main event-loop from blocking during events that
         necessitate many I/O operations. Using a thread pool will allow us to avoid expensive thread creation/destruction,
         while also limiting idle threads due to the constant network traffic the {@code AddressingSerer}'s will experience.
         */
        //this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        // Can try and increase the pool to see if network I/O demands it.
        //this.executorService = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());
    }

    public void closeAllConnections() {
        System.out.println("Closing all listener channels and persistent connections...");
        // Close each listener channel
        tryCloseChannel(chatServerListenerChannel);
        tryCloseChannel(peerListenerChannel);
        tryCloseChannel(healthCheckListenerChannel);

        //Close all persistent channels that may exist for peer and chat server channels
        cleanupManager.getPeerManager().getChannels().keySet().forEach(this::tryCloseChannel);
        cleanupManager.getChatServerManager().getChannels().keySet().forEach(this::tryCloseChannel);

        // Cancel selection keys and close all remaining channels registered with selector
        for (SelectionKey key : selector.keys()) {
            try {
                key.cancel();
                key.channel().close();
            } catch (IOException e) {
                System.err.println("Error closing the selector-registered channel: " + e.getMessage());
            }
        }

        // Shut down the executor (thread) service
//        executorService.shutdownNow();
        try {
            eventWatchdog.shutdown();
            eventWatchdog.join(); // wait for it to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt status
            System.err.println("Interrupted while waiting for watchdog to finish.");
        }


        // Close the selector
        try {
            selector.close();
        } catch (IOException e) {
            System.err.println("Failed to close selector: " + e.getMessage());
        }

        System.out.println("All network resources have been closed for the Addressing Server Object.");
    }

    // Helper method to close channels quietly
    private void tryCloseChannel(Channel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Error closing channel: " + e.getMessage());
            }
        }
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
    public void openListenerChannels(int clientPort, int peerPort, int chatServerPort) throws IOException {
        try {
            openListenerChannel(clientPort);
            this.peerListenerChannel = openListenerChannel(peerPort);
            this.chatServerListenerChannel = openListenerChannel(chatServerPort);
            this.healthCheckListenerChannel = openListenerChannel(5050); // static port for health checks
        } catch (IOException e) {
            System.err.println("Failed to open a listener channel. Exiting main event loop.");
            throw e; // Throw the error so we can catch it in the main method of AddressingServer and reinitialize.
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

    public Selector getSelector() {
        return selector;
    }

    /**
     * Begins the main event loop for the {@code AddressingServer} process by
     * listening for incoming connections on the ports defined in its instance of the
     * {@link AddrServerConfig} class:
     * <ul>
     *     <li>{@code config.clientPort} – used for client connection requests</li>
     *     <li>{@code config.replicaPort} – used for communication with peer replicas</li>
     *     <li>{@code config.chatServerPort} – used for chat server registration</li>
     * </ul>
     *
     * <p>
     * This method uses a {@link Selector} to multiplex I/O across all registered channels,
     * blocking until at least one event becomes available. When a connection or read event
     * is detected, it dispatches the appropriate handler logic and removes the processed
     * key to prevent duplicate handling.
     * </p>
     *
     * <p>
     * The event loop continues to run until a shutdown is explicitly requested via
     * {@link #requestShutdown()}. Once the shutdown flag is set, the loop exits cleanly,
     * calling {@link #closeAllConnections()} to release all associated network resources.
     * </p>
     *
     * @param readDispatcher the object responsible for handling all incoming requests on persistent connections.
     *
     * @throws IOException if an I/O error occurs while selecting or processing channel events
     */
    public void startEventLoop(AddrServerReadDispatcher readDispatcher) throws IOException {
        eventWatchdog.start();
        while (true) {

            if (!eventWatchdog.isAlive()) {
                System.err.println("Watchdog thread is not alive. Restarting...");
                eventWatchdog = new PendingEventWatchdog(this.pendingSyncEvents, cleanupManager, 500);
                eventWatchdog.start();
            }

            if (shutdownRequested) {
                System.out.println("Shutdown signal received. Exiting the AddrServerNetworkManager main event loop.");
                this.closeAllConnections();
                return; // exit to calling code
            }

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
                        cleanupManager.getChatServerManager().getChannels().put(channel, nioChannel);
                    } else if (listenerSC.equals(peerListenerChannel)) {
                        openPersistentChannel(channel);
                        cleanupManager.getPeerManager().getChannels().put(channel, nioChannel);
                    }
                        /*
                         The only port we haven't checked by this point is the one designated for the client.
                         We don't store persistent channels for clients.
                         */
                    //executorService.submit(() -> {
                    try {
                        readDispatcher.handleRegistration(channel, nioChannel, message);
                        if (!cleanupManager.isPersistentConnection(channel)) {
                            // Client process -> close the channel immediately after registration.
                            channel.close();
                            key.cancel();
                        }
                    } catch (ConnectionClosedException cce) {
                        if (cleanupManager.isPersistentConnection(channel)) {
                            cleanupManager.cleanupPersistentConnection(channel, true);
                        } else {
                            try {
                                channel.close();
                            } catch (IOException ignored) {
                            }
                            key.cancel();
                        }
                    } catch (Exception e) {
                        if (cleanupManager.isPersistentConnection(channel)) {
                            cleanupManager.cleanupPersistentConnection(channel, false);
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
                    NIOMessageChannel persistentNioChannel = cleanupManager.getKnownPersistentChannel(channel);
                    // If it doesn't exist, close the channel and cancel the event key to prevent errors.
                    if (persistentNioChannel == null) {
                        try {
                            System.err.printf("No persistent NIO channel found for SocketChannel %s. Closing channel.\n",
                                    channel.getRemoteAddress());
                        } catch (IOException e) {
                            System.err.println("No persistent NIO channel found for a channel (remote address unavailable). Closing channel.");
                        }
                        key.cancel();
                        try {
                            channel.close();
                        } catch (IOException ignored) { }
                    }
                    // Handle persistent connection input/read events (Chat Servers & Replicas)
                    else {
                        // Temporarily disable OP_READ to avoid re-triggering an event on every incoming byte.
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
                            try {
                                System.err.printf("ConnectionClosedException on channel %s (interestOps=%d): %s\n",
                                        channel.getRemoteAddress(), key.interestOps(), cce.getMessage());
                            } catch (IOException e) {
                                System.err.printf("ConnectionClosedException on channel (remote address unavailable, interestOps=%d): %s\n",
                                        key.interestOps(), cce.getMessage());
                            }
                            cleanupManager.cleanupPersistentConnection(channel, true);
                        } catch (IOException ioe) {
                            try {
                                System.err.printf("IOException on channel %s (interestOps=%d): %s\n",
                                        channel.getRemoteAddress(), key.interestOps(), ioe.getMessage());
                            } catch (IOException e) {
                                System.err.printf("IOException on channel (remote address unavailable, interestOps=%d): %s\n",
                                        key.interestOps(), ioe.getMessage());
                            }
                            cleanupManager.cleanupPersistentConnection(channel, false);
                        }
                        //});
                    }
                }
            }
        }
    }
}

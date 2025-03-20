package io.github.cpsc559.team16.common.utilities;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Interface defining the core networking operations for servers that
 * handle incoming connections using NIO (Non-blocking I/O).
 * <p>
 * This interface allows for different implementations of network managers,
 * such as {@code AddrServerNetworkManager} and {@code ChatServerNetworkManager}.
 * Each implementation can define how listeners, selectors, and persistent
 * connections are handled.
 * </p>
 */
public interface NetworkManager {

    /**
     * Returns the selector used for multiplexing non-blocking I/O operations.
     *
     * @return the {@link Selector} instance managing network channels.
     */
    Selector getSelector();

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
    ServerSocketChannel openListenerChannel(int port) throws IOException;

    /**
     * Registers a {@code SocketChannel} for persistent monitoring.
     * <p>
     * This method sets up a persistent connection, allowing the channel to
     * be monitored for read operations without being closed after processing.
     * </p>
     *
     * @param channel The socket channel to register for continuous monitoring.
     * @throws IOException If an I/O error occurs while registering the channel.
     */
    void openPersistentChannel(SocketChannel channel) throws IOException;

    /**
     * Begins the main event loop to monitor and process network events.
     * <p>
     * This method continuously listens for new connections or incoming data.
     * It uses a {@link Selector} to monitor multiple channels simultaneously.
     * When an event occurs, it is dispatched to the appropriate handler.
     * </p>
     *
     * @param connectionDispatcher The dispatcher responsible for routing accepted connections to the appropriate handlers.
     * @param readDispatcher The dispatcher responsible for routing accepted connections to the appropriate handlers.
     * @throws IOException If an I/O error occurs while selecting or processing events.
     */
    void startEventLoop(ConnectionDispatcher connectionDispatcher, ReadDispatcher readDispatcher) throws IOException;

    /**
     * Defines how incoming connections are dispatched to their respective handlers.
     */
    interface ConnectionDispatcher {
        void dispatch(SocketChannel channel, ServerSocketChannel listenerSC);
    }

    /**
     * Defines how data streams on established {@code NIO SocketChannels} are dispatched to their respective handlers.
     */
    interface ReadDispatcher {
        void dispatch(SocketChannel channel, String message);
    }
}

package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;

import io.github.cpsc559.team16.common.utilities.BaseMessage;

/**
 * Defines an interface for handling incoming messages for different connection
 * types.
 * <p>
 * Implementations of this interface are responsible for processing messages
 * received
 * from clients or peer servers, managing connection-specific logic, and taking
 * appropriate actions
 * based on the message content and connection context.
 * </p>
 * 
 * <h3>Methods:</h3>
 * <ul>
 * <li>{@link #handle(BaseMessage, ConnectionContext, SelectionKey)}: Processes
 * an incoming message
 * for a specific connection. This includes deserializing the message,
 * performing necessary validation,
 * updating the connection state, and taking appropriate actions such as sending
 * responses or forwarding
 * the message to other connections.</li>
 * </ul>
 *
 * @see BaseMessage for message structure
 * @see ConnectionContext for managing connection state and metadata
 * @see SelectionKey for representing the channel during non-blocking I/O
 *      operations
 */
interface ConnectionHandler {
    /**
     * Handles incoming messages for a specific connection.
     * <p>
     * The handler is expected to process the message, update the connection context
     * as needed,
     * and perform actions such as responding to the client, broadcasting messages,
     * or maintaining connection state.
     * </p>
     * 
     * @param message the message received from the client or peer server
     * @param ctx     the context of the connection that received the message
     * @param key     the {@link SelectionKey} associated with the connection
     *                channel
     */
    void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key);
}

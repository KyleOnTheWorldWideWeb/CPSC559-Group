package io.github.cpsc559.team16.chatserver;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

/**
 * Utility class for handling the writing of responses to a client or peer.
 * <p>
 * The {@link WriteUtils} class provides a method to enqueue a response to a
 * client or peer server, ensuring that the
 * response is added to the write queue and that the {@code OP_WRITE} operation
 * is properly set for the connection.
 * </p>
 * <p>
 * This utility is typically called when a message needs to be sent from the
 * server to a connected client or peer server,
 * such as in response to a request, during message handling, or for sending
 * periodic heartbeat signals.
 * </p>
 *
 * <h3>Key Methods:</h3>
 * <ul>
 * <li>{@link #enqueueResponse(ConnectionContext, SelectionKey, String)}: Adds a
 * response to the write queue for the
 * specified connection and ensures that the {@code OP_WRITE} operation is
 * properly set for the connection.</li>
 * </ul>
 * 
 * <h3>Method Details:</h3>
 * <ul>
 * <li>The {@link #enqueueResponse} method ensures thread-safety by
 * synchronizing access to the write queue.
 * It wraps the response string into a {@link ByteBuffer} and adds it to the
 * connection's write queue.</li>
 * <li>The method then sets the {@code OP_WRITE} interest operation for the
 * {@link SelectionKey} to ensure that
 * the connection will be ready for writing during the next selector cycle.</li>
 * </ul>
 *
 * <h3>Logging:</h3>
 * <p>
 * The method logs the response to the console for debugging purposes,
 * indicating the response being sent.
 * </p>
 *
 * <h3>Where it's called:</h3>
 * <p>
 * This method is called in various parts of the server, such as within handlers
 * for messages like {@code PING},
 * {@code PONG}, chat log requests, and responses to client messages. It's used
 * to queue the response for sending to
 * the client or peer server, ensuring that the appropriate response is sent
 * when the connection is ready to write.
 * </p>
 *
 * @see ConnectionContext for connection details
 * @see SelectionKey for the key representing the channel's selection state
 */
public class WriteUtils {
    public static void enqueueResponse(ConnectionContext ctx, SelectionKey key, String response) {
        synchronized (ctx.writeQueue) {
            // System.out.println("Sending response " + response);
            ctx.writeQueue.add(ByteBuffer.wrap(response.getBytes()));
        }
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
}

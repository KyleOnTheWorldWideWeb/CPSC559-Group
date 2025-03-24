package io.github.cpsc559.team16.common.exceptions;

import java.io.IOException;

/**
 * Exception thrown when a SocketChannel connection is closed unexpectedly.
 * <p>
 * This exception signals that the remote peer has closed the connection, and the associated
 * SocketChannel should be removed from the Selector and cleaned up.
 * </p>
 */
public class ConnectionClosedException extends IOException {
    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }
}

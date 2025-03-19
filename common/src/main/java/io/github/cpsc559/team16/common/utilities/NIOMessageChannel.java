package io.github.cpsc559.team16.common.utilities;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * A utility class that simplifies reading and writing messages over a SocketChannel.
 * It mimics linking a {@code PrintWriter} to a traditional socket for output streams,
 * and using a {@code BufferedReader} for input streams.
 * <p>
 * This class automatically handles:
 * <ul>
 *     <li>Encoding and decoding messages using UTF-8</li>
 *     <li>Message framing using a newline (`\n`) delimiter</li>
 *     <li>Buffer allocation</li>
 *     <li>Flipping a {@code SocketChannel}'s state between read and write</li>
 * </ul>
 */
public class NIOMessageChannel {
    private final SocketChannel channel;
    private final ByteBuffer buffer;

    /**
     * Creates a new NIOMessageChannel for a given SocketChannel.
     *
     * @param channel The SocketChannel to wrap.
     */
    public NIOMessageChannel(SocketChannel channel) {
        this.channel = channel;
        this.buffer = ByteBuffer.allocate(1024);  // Adjustable buffer size
    }

    /**
     * Retrieves the SocketChannel linked to this instance of
     * {@link NIOMessageChannel}, allowing a developer to
     * store a single NIOMessageChannel in a data structure, while
     * retaining access to both.
     * @return
     */
    public SocketChannel getSocketChannel() {
        return this.channel;
    }

    /**
     * Sends a message over the network.
     * <p>
     * This method automatically appends a newline (`\n`) to the message to act as a delimiter.
     * </p>
     *
     * @param message The string message to send.
     * @throws IOException If an I/O error occurs.
     */
    public void sendMessage(String message) throws IOException {
        String framedMessage = message + "\n";  // Append newline as a delimiter
        ByteBuffer sendBuffer = ByteBuffer.wrap(framedMessage.getBytes(StandardCharsets.UTF_8));
        while (sendBuffer.hasRemaining()) {
            channel.write(sendBuffer);
        }
    }

    /**
     * Reads a message from the network.
     * <p>
     * This method accumulates data until a full message (ending in `\n`) is received.
     * </p>
     *
     * @return The received message as a string, or {@code null} if no complete message is available.
     * @throws IOException If an I/O error occurs.
     */
    public String receiveMessage() throws IOException {
        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            return null;  // Connection closed
        }

        buffer.flip();
        String receivedData = StandardCharsets.UTF_8.decode(buffer).toString();
        buffer.clear();

        // Check if we received a complete message
        if (receivedData.contains("\n")) {
            String[] messages = receivedData.split("\n", 2);
            return messages[0];  // Return the first complete message
        }

        return null;  // No complete message yet
    }

    /**
     * Closes the underlying SocketChannel.
     *
     * @throws IOException If an error occurs while closing.
     */
    public void close() throws IOException {
        channel.close();
    }
}

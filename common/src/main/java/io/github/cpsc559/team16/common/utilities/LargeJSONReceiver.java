package io.github.cpsc559.team16.common.utilities;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class LargeJSONReceiver {
    private static final int CHUNK_SIZE = 16384;  // 16 KB buffer
    private final StringBuilder messageBuffer = new StringBuilder(); // Accumulate JSON

    /**
     * Reads incoming JSON data in chunks from a SocketChannel and reconstructs it.
     *
     * @param channel The SocketChannel to read from.
     * @throws IOException If an I/O error occurs.
     */
    public void receiveLargeJSON(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
        int bytesRead;

        while ((bytesRead = channel.read(buffer)) > 0) {
            buffer.flip();
            messageBuffer.append(StandardCharsets.UTF_8.decode(buffer).toString());
            buffer.clear();
        }

        if (bytesRead == -1) {
            System.out.println("Connection closed by sender.");
        }

        // Deserialize the full JSON message
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> receivedData = objectMapper.readValue(messageBuffer.toString(), HashMap.class);

        System.out.println("Received JSON with " + receivedData.size() + " entries.");
    }
}


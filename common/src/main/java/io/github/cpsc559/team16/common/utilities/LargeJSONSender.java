package io.github.cpsc559.team16.common.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LargeJSONSender {
    private static final int CHUNK_SIZE = 16384;  // 16 KB buffer

    /**
     * Sends a large JSON message over a SocketChannel in chunks.
     *
     * @param channel  The SocketChannel to send data over.
     * @param dataMap  The HashMap containing ChatServerInfo or AddrServerInfo objects.
     * @throws IOException If an I/O error occurs.
     */
    public static void sendLargeJSON(SocketChannel channel, Map<String, Object> dataMap) throws IOException {
        // Convert HashMap to JSON String
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonData = objectMapper.writeValueAsString(dataMap);

        // Convert JSON String to Byte Array
        byte[] jsonBytes = jsonData.getBytes(StandardCharsets.UTF_8);
        int totalBytes = jsonBytes.length;
        int bytesSent = 0;

        // Send data in chunks
        while (bytesSent < totalBytes) {
            int remainingBytes = totalBytes - bytesSent;
            int chunkSize = Math.min(CHUNK_SIZE, remainingBytes);

            ByteBuffer buffer = ByteBuffer.wrap(jsonBytes, bytesSent, chunkSize);
            while (buffer.hasRemaining()) {
                channel.write(buffer);  // Send chunk
            }

            bytesSent += chunkSize;
        }

        System.out.println("JSON message fully sent (" + totalBytes + " bytes).");
    }
}


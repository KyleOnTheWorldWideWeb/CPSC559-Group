package io.github.cpsc559.team16.common.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AddrHandshakeUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean performHandshake(Socket socket, Map<String, Object> handshakeData) {
        try {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Serialize the handshake data to JSON
            String jsonMessage = objectMapper.writeValueAsString(handshakeData);
            writer.println(jsonMessage);

            // Read the acknowledgment from the server
            String ack = reader.readLine();
            System.out.println(ack);

            return ack != null && !ack.isEmpty();
        } catch (IOException e) {
            System.err.println("An error occurred during the handshake: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static Map<String, Object> readHandshakeData(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String jsonMessage = reader.readLine();
        return objectMapper.readValue(jsonMessage, Map.class);
    }
}
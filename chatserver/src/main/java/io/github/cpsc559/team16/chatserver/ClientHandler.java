package io.github.cpsc559.team16.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cpsc559.team16.utilities.BaseMessage;
import io.github.cpsc559.team16.utilities.ClientServerMessage;

public class ClientHandler implements Runnable {
    private Socket socket;
    private String username;
    private PrintWriter output;
    private BlockingQueue<BaseMessage> messageQueue;
    private ConcurrentHashMap<String, ClientHandler> clients;
    private ObjectMapper objectMapper = new ObjectMapper();

    public ClientHandler(Socket socket, BlockingQueue<BaseMessage> messageQueue,
            ConcurrentHashMap<String, ClientHandler> clients) {
        this.socket = socket;
        this.messageQueue = messageQueue;
        this.clients = clients;
    }

    @Override
    public void run() {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            output = new PrintWriter(socket.getOutputStream(), true);

            // LOGIN
            // if (!loginUser(input)) { return; }

            // Read messages from the client and add them to the message queue
            String messageJson;
            while ((messageJson = input.readLine()) != null) {
                try {
                    // Deserialize JSON to ClientServerMessage
                    ClientServerMessage message = objectMapper.readValue(messageJson, ClientServerMessage.class);
                    messageQueue.put(message);
                } catch (JsonProcessingException e) {
                    System.err.println("Error parsing message from client: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling client connection: " + e.getMessage());
        } finally {
            // Remove the client from the list of connected clients
            if (username != null) {
                clients.remove(username);
            }
            try {
                socket.close();
            } catch (Exception e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    public void sendMessage(BaseMessage message) {
        output.println(message);
    }

    private boolean loginUser (BufferedReader input) throws IOException {
        String loginJson = input.readLine();
        BaseMessage loginMessage = BaseMessage.fromJson(loginJson, ClientServerMessage.class);

        if (loginMessage instanceof ClientServerMessage) {
            username = ((ClientServerMessage) loginMessage).getContent();

            if (clients.containsKey(username)) {
                sendMessage(
                        new ClientServerMessage("server", username, "Username already taken. Connection closed."));
                socket.close();
                return false;
            }

            clients.put(username, this);
            sendMessage(new ClientServerMessage("server", username, "Welcome, " + username + "!"));
            return true;
        } else {
            sendMessage(new ClientServerMessage("server", "unknown", "Invalid login message."));
            socket.close();
            return false;
        }
    }
}
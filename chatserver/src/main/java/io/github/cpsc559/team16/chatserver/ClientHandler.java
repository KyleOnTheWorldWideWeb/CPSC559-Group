package io.github.cpsc559.team16.chatserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket socket;
    private String username;
    private PrintWriter output;
    private BlockingQueue<String> messageQueue;
    private ConcurrentHashMap<String, ClientHandler> clients;

    public ClientHandler(Socket socket, BlockingQueue<String> messageQueue, ConcurrentHashMap<String, ClientHandler> clients) {
        this.socket = socket;
        this.messageQueue = messageQueue;
        this.clients = clients;
    }

    @Override
    public void run() {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            output = new PrintWriter(socket.getOutputStream(), true);

            // Read the username
            username = input.readLine();
            if (clients.containsKey(username)) {
                output.println("Username already taken. Connection closed.");
                socket.close();
                return;
            }

            // Add the client to the list of connected clients
            clients.put(username, this);
            output.println("Welcome, " + username + "!");

            // Read messages from the client and add them to the message queue
            String message;
            while ((message = input.readLine()) != null) {
                messageQueue.put(username + ": " + message);
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

    public void sendMessage(String message) {
        output.println(message);
    }
}
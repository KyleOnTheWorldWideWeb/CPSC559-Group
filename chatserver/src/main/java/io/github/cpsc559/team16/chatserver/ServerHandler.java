package io.github.cpsc559.team16.chatserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHandler implements Runnable {
    private Socket socket;
    private PrintWriter output;
    private BlockingQueue<String> messageQueue;
    private ConcurrentHashMap<String, ServerHandler> servers;

    public ServerHandler(Socket socket, BlockingQueue<String> messageQueue, ConcurrentHashMap<String, ServerHandler> servers) {
        this.socket = socket;
        this.messageQueue = messageQueue;
        this.servers = servers;
    }

    @Override
    public void run() {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            output = new PrintWriter(socket.getOutputStream(), true);

            // Perform handshake
            String handshakeMessage = input.readLine();
            if (!"SERVER".equals(handshakeMessage)) {
                output.println("Invalid handshake. Connection closed.");
                socket.close();
                return;
            }

            // Add the server to the list of connected servers
            servers.put(socket.getRemoteSocketAddress().toString(), this);
            output.println("Server connection established.");

            // Read messages from the server and add them to the message queue
            String message;
            while ((message = input.readLine()) != null) {
                messageQueue.put("SERVER: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error handling server connection: " + e.getMessage());
        } finally {
            // Remove the server from the list of connected servers
            servers.remove(socket.getRemoteSocketAddress().toString());
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
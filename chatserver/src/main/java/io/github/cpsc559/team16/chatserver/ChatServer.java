package io.github.cpsc559.team16.chatserver;

import io.github.cpsc559.team16.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatServer {
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final BlockingQueue<BaseMessage> messageQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        // Print all environment variables for debugging
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));

        // Read the port from the environment variable, default to 2424 if not set
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "2424"));
        System.out.printf("Chat Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        // Start the message broadcasting thread
        // This is in charge of handling outgoing recieved messages.
        // We spray all messages out to all our clients
        new Thread(ChatServer::broadcastMessages).start();

        // I think we should consider creating a threadpool for this instead of this
        // implementation.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.print("ChatServer is attempting to connect to addressing server.......");
            try (Socket addrServerSocket = new Socket("host.docker.internal", 49802)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(addrServerSocket.getInputStream(), StandardCharsets.UTF_8));
                String ack = reader.readLine();
                System.out.println(ack);
            } catch (IOException e) {
                System.err.println("An error occured while attempting to register with the addressing server: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("ChatServer is listening on port " + port);

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("New client connected");

                    // Create a new thread to handle the client connection
                    ClientHandler clientHandler = new ClientHandler(socket, messageQueue, clients);
                    Thread thread = new Thread(clientHandler);
                    thread.start();
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static void broadcastMessages() {
        try {
            while (true) {
                // Take a message from the queue and broadcast it to all clients
                BaseMessage message = messageQueue.take();
                for (ClientHandler client : clients.values()) {
                    client.sendMessage(message);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Broadcasting thread interrupted: " + e.getMessage());
        }
    }
}
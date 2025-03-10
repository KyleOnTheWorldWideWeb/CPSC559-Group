package io.github.cpsc559.team16.chatserver;

import io.github.cpsc559.team16.common.utilities.ProcessUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatServer {
    private static final String PASSWORD = "12345";

    public static void main(String[] args) {
        // Print all environment variables for debugging
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));

        // Read the port from the environment variable, default to 12345 if not set
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "2424"));
        System.out.printf("Chat Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ChatServer is listening on port " + port);

            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

                    String receivedPassword = input.readLine();
                    String message = String.format("ChatServer recieved %s from client", receivedPassword);
                    
                    System.out.println(message);
                    if (PASSWORD.equals(receivedPassword)) {
                        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        output.println("Current time: " + currentTime);
                    } else {
                        output.println("Invalid password");
                    }
                } catch (Exception e) {
                    System.err.println("Error handling client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }
}
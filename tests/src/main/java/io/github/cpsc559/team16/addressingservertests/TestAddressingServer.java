package io.github.cpsc559.team16.addressingservertests;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.addressingserver.ServerInfo;
import io.github.cpsc559.team16.addressingserver.AddressingServer;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;

public class TestAddressingServer {

    public static void main(String[] args) {
        System.out.println("Starting tests for ServerInfo...");

        // Create a ServerInfo instance with a maximum of 3 clients
        ServerInfo server = new ServerInfo("127.0.0.1", 3000, 4000, 3);

        // Test addClient() until the server is full
        try {
            System.out.println("Adding client 1");
            server.addClient();
            System.out.println("Adding client 2");
            server.addClient();
            System.out.println("Adding client 3");
            server.addClient();
            // This next addition should trigger a ChatServerFullException
            System.out.println("Attempting to add client 4 (should fail)");
            server.addClient();
        } catch (ChatServerFullException e) {
            System.out.println("Expected exception caught: " + e.getMessage());
        }

        // Remove some clients and verify the server is no longer full
        System.out.println("Removing 2 clients");
        server.removeClients(2);
        System.out.println("Is server full? " + server.isFull());

        // Test status transitions: mark inactive then reactivate
        System.out.println("Marking server as inactive");
        server.markAsInactive();
        System.out.println("Status after marking inactive: " + server.getStatus());
        try {
            System.out.println("Reactivating server (client count should reset to 0)");
            server.markAsActive();
            System.out.println("Status after reactivation: " + server.getStatus());
        } catch (IllegalStateException e) {
            System.out.println("Unexpected error during reactivation: " + e.getMessage());
        }

        // Test AddressingServer functionalities
        System.out.println("\nStarting tests for AddressingServer...");
        AddressingServer addressingServer = new AddressingServer();

        // Create a dummy address log with one ServerInfo instance
        Map<Long, ServerInfo> dummyLog = new HashMap<>();
        dummyLog.put(1L, new ServerInfo("192.168.1.666", 3000, 1234, 5));
        addressingServer.setAddressLog(dummyLog);

        // Use debugPrint to print the ServerInfo details
        System.out.println("Debug printing ServerInfo from AddressingServer's address log:");
        addressingServer.debugPrint(dummyLog.get(1L));

        // Attempt to connect to the Addressing Server as a chat-server
        try (Socket socket = new Socket("host.docker.internal", 49802)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String ack = reader.readLine();
            System.out.println("Received chat server response......" + ack);
        } catch (IOException e) {
            System.err.println("An error occured while attempting to register with the addressing server: " + e.getMessage());
            e.printStackTrace();
        }

        // Attempt to connect to the AddressingServer as a client
        try (Socket socket = new Socket("host.docker.internal", 49800)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            System.out.println("Received chat server address: " + response);
        } catch (IOException e) {
            System.err.println("Error during test client connection: " + e.getMessage());
            e.printStackTrace();
        }


        System.out.println("Test stub execution complete.");
    }
}



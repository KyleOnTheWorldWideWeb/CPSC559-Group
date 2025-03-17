package io.github.cpsc559.team16.tests.addressingservertests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.cpsc559.team16.addressingserver.ChatServerInfo;
import io.github.cpsc559.team16.addressingserver.AddressingServer;

public class TestAddressingServer {

    public static void addressingTest() {
        System.out.println("Starting Addressing Server, Chat Server, Client connection test...");
        System.out.println(">--------<");

        // Test AddressingServer functionalities
        System.out.println("\nStarting tests for AddressingServer...");
        AddressingServer addressingServer = new AddressingServer();

        // Create a dummy address log with one ChatServerInfo instance
        Map<Long, ChatServerInfo> dummyLog = new HashMap<>();
        // dummyLog.put(1L, new ChatServerInfo(234L, "192.168.1.666", 3000, 1234, 5));
        addressingServer.setChatServerRecords(dummyLog);

        // Use debugPrint to print the ChatServerInfo details
        System.out.println("Debug printing ChatServerInfo from AddressingServer's address log:");
        addressingServer.debugPrintServer(dummyLog.get(1L));

        // Attempt to connect to the Addressing Server as a chat-server
        try (Socket socket = new Socket("host.docker.internal", 49802)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String ack = reader.readLine();
            System.out.println("Received chat server response......" + ack);
        } catch (IOException e) {
            System.err.println(
                    "An error occured while attempting to register with the addressing server: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Debug printing all info from AddressingServer's address log:");
        addressingServer.debugPrintServer(dummyLog.get(1L));

        // Attempt to connect to the AddressingServer as a client
        try (Socket socket = new Socket("host.docker.internal", 49800)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            System.out.println("Received chat server address: " + response);
        } catch (IOException e) {
            System.err.println("Error during test client connection: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Addressing server test stub execution complete.\n");
    }

}

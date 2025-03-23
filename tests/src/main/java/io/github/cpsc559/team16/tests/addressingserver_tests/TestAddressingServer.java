package io.github.cpsc559.team16.tests.addressingserver_tests;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.addressingserver.AddressingServer;
import io.github.cpsc559.team16.addressingserver.ChatServerRegistry;


public class TestAddressingServer {

    public static void addressingTest() {
        System.out.println("Starting Addressing Server, Chat Server, Client connection test...");
        System.out.println(">--------<");

        // Test AddressingServer functionalities
        System.out.println("\nStarting tests for AddressingServer...");
        AddressingServer addressingServer = new AddressingServer();
        ChatServerRegistry registry = new ChatServerRegistry();
        // Create a dummy address log with one ChatServerRecord instance
        Map<Long, ChatServerRecord> dummyLog = new HashMap<>();
        dummyLog.put(234L, new ChatServerRecord(234L, "192.168.1.666", 2424, 2425,2426, 30));
        registry.setChatServerRecords(dummyLog);

        // Use debugPrint to print the ChatServerRecord details
        System.out.println("Debug printing ChatServerRecord from AddressingServer's address log:");
        registry.debugPrintServer(dummyLog.get(1L));

        // Attempt to connect to the Addressing Server as a chat-server
        try (Socket socket = new Socket("host.docker.internal", 49802)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String ack = reader.readLine();
            System.out.println("Received chat server response......" + ack);
        } catch (IOException e) {
            System.err.println("An error occured while attempting to register with the addressing server: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Debug printing all info from AddressingServer's address log:");
        registry.debugPrintServer(dummyLog.get(1L));

        // Attempt to connect to the AddressingServer as a client
        try (Socket socket = new Socket("host.docker.internal", 49800)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            System.out.println("Received chat server address: " + response);
        } catch (IOException e) {
            System.err.println("Error during test client connection: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Addressing server test stub execution complete.\n");
    }


}



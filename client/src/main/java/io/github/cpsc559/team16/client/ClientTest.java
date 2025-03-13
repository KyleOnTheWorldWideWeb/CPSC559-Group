package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientTest {
    public static void main(String[] args) {
        System.out.printf("Client process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
        try (Socket socket = new Socket("host.docker.internal", 49800)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            System.out.println("Received chat server address: " + response);
        } catch (IOException e) {
            System.err.println("Error during test client connection: " + e.getMessage());
            e.printStackTrace();
        }
        try (Socket socket = new Socket("host.docker.internal", 2424)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            System.out.println("Received chat server address: " + response);
        } catch (IOException e) {
            System.err.println("Error during test client connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

}

package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.utilities.ProcessUtils;


public class Client {
    public static void main(String[] args) {
        System.out.printf("Client process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }
}

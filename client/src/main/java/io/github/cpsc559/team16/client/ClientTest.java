package io.github.cpsc559.team16.client;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;

public class ClientTest {
    public static void main(String[] args) {
        System.out.printf("Client process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }

}

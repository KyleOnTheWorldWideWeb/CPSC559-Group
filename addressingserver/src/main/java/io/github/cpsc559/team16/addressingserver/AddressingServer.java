package io.github.cpsc559.team16.addressingserver;
import io.github.cpsc559.team16.utilities.ProcessUtils;

public class AddressingServer {
    public static void main(String[] args) {
        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }

}
package io.github.cpsc559.team16.addressingserver;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;
import java.util.HashMap;


public class AddressingServer {

    /**
     * The network address of this Addressing Server.
     */
    private String hostAddress;
    /**
     * The port used for client connections.
     * Clients use this port to connect and send messages.
     */
    private int clientPort;

    /**
     * The port reserved for peer-to-peer communication amongst the
     * Primary Addressing Server and it's backups.
     */
    private int peerPort;

    /**
     * The port used for communicating with Chat Servers.
     * This should be the port the chat server used to register itself with the Addressing Server.
     */
    private int addrServerPort;

    public enum

    private HashMap<Long, ServerInfo> AddressLog;
    private

    public static void main(String[] args) {
        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }

}
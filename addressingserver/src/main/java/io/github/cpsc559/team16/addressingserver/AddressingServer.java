package io.github.cpsc559.team16.addressingserver;
import io.github.cpsc559.team16.common.utilities.ProcessUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional; // Used for conditionals that don't rely on non-null checks

import io.github.cpsc559.team16.addressingserver.ServerInfo.ServerStatus;



public class AddressingServer {

    /**
     * The number of chat servers that have been registered.
     * Count begins at 1, not zero, because normals don't start at zero.
     */
    private long chatServerCount;

    /**
     * The network address of this Addressing Server.
     */
    private final String hostAddress;

    /**
     * The port used for client connections.
     * Clients use this port to connect and send messages.
     */
    private final int clientPort;

    /**
     * The port reserved for peer-to-peer communication amongst the
     * Primary Addressing Server and it's backups.
     */
    private final int peerPort;

    /**
     * The port used for communicating with Chat Servers.
     * This should be the port the chat server used to register itself with the Addressing Server.
     */
    private final int chatServerPort;

    /**
     * A mapping of unique chat server IDs to their corresponding {@link ServerInfo} records.
     * <p>
     * This address log is used by the AddressingServer to track all registered chat servers
     * in the distributed network, enabling operations such as client assignment and server status monitoring.
     * </p>
     */
    private Map<Long, ServerInfo> addressLog;

    /**
     * Represents the leadership status of an addressing server.
     * Addressing server backups are `Passive Replicas` - they only sync data with
     * the primary server and do not handle requests until failover occurs.
     * <ul>
     *     <li>{@code PRIMARY} - This server is the Primary Addressing Server.</li>
     *     <li>{@code BACKUP} - This server is a Passive Replica for failover.</li>
     * </ul>
     */
    public enum ServerRole {
        PRIMARY, BACKUP
    }


    /**
     * Constructs an AddressingServer with the specified host address, client port, peer port, and chat server port.
     * <p>
     * This constructor initializes a new, empty address log to track registered chat servers.
     * </p>
     *
     * @param hostAddress    the network address of the Addressing Server.
     * @param clientPort     the port used for client connections.
     * @param peerPort       the port used for peer-to-peer addressing server communication .
     * @param chatServerPort the port used for chat server communication.
     *
     * @see #setAddressLog(Map) to assign an address log.
     *
     */
    public AddressingServer(String hostAddress, int clientPort, int peerPort, int chatServerPort) {
        this.hostAddress = hostAddress;
        this.clientPort = clientPort;
        this.peerPort = peerPort;
        this.chatServerPort = chatServerPort;
        addressLog = new HashMap<>();
    }

    /**
     * Replaces the current address log with the provided new address log.
     * <p>
     * This method updates the AddressingServer's registry of chat servers by replacing its internal
     * address log with the new map passed as a parameter. This allows for dynamic reconfiguration or recovery
     * by resetting the address log.
     * </p>
     *
     * @param newAddressLog a {@code Map} of chat server IDs to {@link ServerInfo} objects representing the new address log.
     */
    public void setAddressLog(Map<Long, ServerInfo> newAddressLog) {
        this.addressLog = newAddressLog;
    }

    // To be completed for Replication
//    /**
//     * Replaces the current address log with the one received from a remote server.
//     * <p>
//     * The new address log is transmitted over the network, deserialized, and then assigned by reference.
//     * This ensures that the server uses the most up-to-date address log information.
//     * </p>
//     *
//     * @param newAddressLog the address log object received from the network
//     */
//    public void updateAddressLogFromNetwork(Map<Long,ServerInfo> newAddressLog) {
//
//    }


    /**
     * Generates a unique identifier for a chat server by incrementing the internal counter.
     *
     * @return a unique {@code Long} representing the chat server's ID.
     */
    private Long generateID() {
        return ++this.chatServerCount;
    }

    /**
     * Registers a chat server by generating a unique ID and inserting its
     * ServerInfo record into the global addressLog.
     * <p>
     * This method generates a unique ID using {@code generateID()}, creates a new
     * {@link ServerInfo} object with the given parameters, and then inserts it into the addressLog. If a record
     * with the same ID already exists, it logs a warning message (and overwrites it).
     * </p>
     *
     * @param chatHostAddress the host address (IP or hostname) of the chat server.
     * @param chatClientPort  the port used for client connections.
     * @param chatPeerPort    the port used for peer-to-peer (gossip) communication.
     * @param maxClientCount  the maximum number of client connections allowed for this server.
     * @throws Exception if any error occurs during the registration process.
     */
    private void registerChatServer(String chatHostAddress, int chatClientPort,
                                    int chatPeerPort, int maxClientCount) throws Exception {
        try {
            Long id = generateID();
            ServerInfo newServer = new ServerInfo(chatHostAddress, chatPeerPort, chatClientPort, maxClientCount);
            // Check if the key already existed (should be null for a new key)
            ServerInfo previous = addressLog.put(id, newServer);
            if (previous != null) {
                System.err.println("WARNING: A server with ID " + id + " already existed. Overwriting existing entry.");
                // TODO: add logic to skip a range of ID values and re-insert the overwritten record - "previous"
            } else {
                System.out.println("Server registered successfully with ID " + id);
                // TODO: RM debug print statement
                debugPrint(newServer);
            }
        } catch (Exception e) {
            System.err.println("Error registering chat server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Deregisters a chat server by removing it from the {@code addressLog} containing all known chat servers.
     *
     * @param chatServerID the unique identifier of the chat server to be deregistered.
     * @return {@code true} if the server was successfully removed; {@code false} otherwise.
     */
    private boolean deregisterChatServer(Long chatServerID) {
        return addressLog.remove(chatServerID) != null;
    }

    /**
     * Retrieves the address of an active chat server.
     *
     * @return an {@link Optional} containing the active host address in the format "hostAddress:clientPort",
     *         or {@code Optional.empty()} if no active host exists.
     */
    private Optional<String> getActiveHost() {
        for (ServerInfo host : addressLog.values()) {
            if (host.getStatus() == ServerStatus.ACTIVE) {
                return Optional.of(host.getHostAddress() + ":" + host.getClientPort());
            }
        }
        return Optional.empty();
    }

    private void connectClientToHost() {

    }

    public static void main(String[] args) {
        System.out.printf("Addressing Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }

    /**
     * Logs the details of this ServerInfo object to the console for debugging purposes.
     */
    public void debugPrint(ServerInfo s) {
        System.out.println("---------- ServerInfo Debug ----------");
        System.out.printf("Host Address : %s%n", s.getHostAddress());
        System.out.printf("Client Port  : %d%n", s.getClientPort());
        System.out.printf("Peer Port    : %d%n", s.getPeerPort());
        System.out.printf("Client Count : %d%n", s.getClientCount());
        System.out.printf("Status       : %s%n", s.getStatus());
        System.out.println("--------------------------------------");
    }

}
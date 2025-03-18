package io.github.cpsc559.team16.addressingserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AddrServerRegistry {

    /**
     * This Hashmap is used by each AddressingServer to keep {@code AddrServerInfo}
     * records of all other addressing servers in the network.
     */
    private Map<Long, AddrServerInfo> addrServerRecords = new ConcurrentHashMap<>();

    public Map<Long, AddrServerInfo> getRecords() {
        return addrServerRecords;
    }

    public void setAddrServerRecords(Map<Long, AddrServerInfo> newAddrServerRecords) {
        this.addrServerRecords = newAddrServerRecords;
    }

    /**
     * Registers a new Addressing Server record by storing its {@link AddrServerInfo} object.
     * <p>
     * This method adds the provided server record to the {@code addrServerRecords} map,
     * using its unique process ID as the key.
     * </p>
     *
     * @param id     The unique process ID of the Addressing Server being registered.
     * @param record The {@link AddrServerInfo} object containing the server's details
     *               (e.g., host address, ports, role).
     */
    public void registerAddrServerRecord(Long id, AddrServerInfo record) {
        addrServerRecords.put(id, record);
    }

    public boolean deregisterServer(Long id) {
        return addrServerRecords.remove(id) != null;
    }


    /**
     * Creates a record for an addressing server by inserting its AddrServerInfo record into the registry.
     *
     * @param serverPID the unique process ID generated for this addressing server.
     * @param hostAddress the host address (IP or hostname) of the addressing server.
     * @param clientPort the port used for client communication.
     * @param peerPort the port used for peer-to-peer communication.
     * @param chatServerPort the port used for communication with chat servers.
     * @param role the role of the addressing server being registered (PRIMARY || BACKUP)
     *
     * @return serverPID The unique process ID generated for the newly registered {@code AddrServer}.
     */
    public long registerAddrServer(Long serverPID, String hostAddress, int clientPort, int peerPort, int chatServerPort, AddrServerConfig.ServerRole role) {
        try {
            AddrServerInfo newServer = new AddrServerInfo(serverPID, hostAddress, clientPort, peerPort, chatServerPort, role);
            AddrServerInfo previous = this.addrServerRecords.put(serverPID, newServer);

            if (previous != null) {
                System.err.println("WARNING: A server with ID " + serverPID + " already existed. Overwriting existing entry.");
            } else {
                System.out.println("\n**AddressingServer** successfully registered with ID: " + serverPID + "\n");
                debugPrintAllServers();
            }
            return serverPID;
        } catch (Exception e) {
            System.err.println("Error registering addressing server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Logs the details of this AddrServerInfo object to the console for debugging purposes.
     */
    public void debugPrintServer(AddrServerInfo s) {
        System.out.println("---------- AddrServerInfo Record -----------");
        System.out.printf("Host Address : %s%n", s.getHostAddress());
        System.out.printf("Client Port  : %d%n", s.getClientPort());
        System.out.printf("Peer Port    : %d%n", s.getPeerPort());
        System.out.printf("ChatServer Port : %d%n", s.getChatServerPort());
        System.out.printf("Role : %s%n", s.getRole());
        System.out.println("---------------------------------------------------");
    }

    public void debugPrintAllServers() {
        System.out.println("|------------- Currently Registered AddressingServer's -------------|");
        for (AddrServerInfo s : this.addrServerRecords.values()) {
            debugPrintServer(s);
        }
        System.out.println("|--------------------------------------------------------------------|");
    }
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRole;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AddrServerRegistry {

    /**
     * This Hashmap is used by each AddressingServer to keep {@code AddrServerRecord}
     * records of all other addressing servers in the network.
     */
    private Map<Long, AddrServerRecord> addrServerRecords = new ConcurrentHashMap<>();

    public Map<Long, AddrServerRecord> getRecords() {
        return addrServerRecords;
    }

    public void setAddrServerRecords(Map<Long, AddrServerRecord> newAddrServerRecords) {
        this.addrServerRecords = newAddrServerRecords;
    }

    /**
     * Registers a new Addressing Server record by storing its {@link AddrServerRecord} object.
     * <p>
     * This method adds the provided server record to the {@code addrServerRecords} map,
     * using its unique process ID as the key.
     * </p>
     *
     * @param id     The unique process ID of the Addressing Server being registered.
     * @param record The {@link AddrServerRecord} object containing the server's details
     *               (e.g., host address, ports, role).
     */
    public void putAddrServerRecord(Long id, AddrServerRecord record) {
        addrServerRecords.put(id, record);
        debugPrintServer(record);
    }

    /**
     * Updates an existing AddrServerRecord if one with the same ID exists,
     * otherwise inserts the new record.
     *
     * @param record The AddrServerRecord to insert or update.
     */
    public void updateOrInsertRecord(AddrServerRecord record) {
        Long id = record.getPID();
        if (id == null) {
            System.err.println("AddrServerRecord has a null PID. Cannot update or insert record.");
            return;
        }
        AddrServerRecord existing = addrServerRecords.get(id);
        if (existing != null) {
            System.out.println(">--Updating--< existing AddrServerRecord for ID: " + id);
        } else {
            System.out.println(">--Inserting--< new AddrServerRecord for ID: " + id);
        }
        addrServerRecords.put(id, record);
        debugPrintAllServers();
    }


    /**
     * Removes an {@link AddrServerRecord} from the registry using the provided process ID (PID).
     * <p>
     * This method attempts to remove the record associated with the given {@code pid} from the internal
     * {@code addrServerRecords} map. If a record is found and removed, a success message is printed.
     * Otherwise, a warning is issued to indicate that no record existed for the specified PID.
     * </p>
     *
     * @param pid the unique process ID of the AddressingServer to remove from the registry.
     */
    public void removeRecordByKey(Long pid) {
        AddrServerRecord record = addrServerRecords.remove(pid);
        if (record != null) {
            System.out.printf("Successfully removed *AddrServerRecord* for Network Process with PID: %d - and Host Address: %s%n", pid, record.getHostAddress());
        } else {
            System.out.println("No AddrServerRecord found for PID: " + pid + " — nothing to remove.");
        }
    }


    /**
     * Creates a record for an addressing server by inserting its AddrServerRecord record into the registry.
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
    public void registerAddrServer(Long serverPID, String hostAddress, int clientPort, int peerPort, int chatServerPort, ServerRole role) {
        try {
            AddrServerRecord newServer = new AddrServerRecord(serverPID, hostAddress, clientPort, peerPort, chatServerPort, role);
            AddrServerRecord previous = this.addrServerRecords.put(serverPID, newServer);

            if (previous != null) {
                System.err.println("WARNING: A server with ID " + serverPID + " already existed. Overwriting existing entry.");
            } else {
                System.out.println("\n**AddressingServer** successfully registered with ID: " + serverPID + "\n");
            }
        } catch (Exception e) {
            System.err.println("Error registering addressing server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Returns a set of all connected peer process IDs, excluding the given calling process ID.
     * <p>
     * This method is useful when the caller wants to get all *other* peer PIDs in the network,
     * for example when broadcasting to all replicas except itself.
     * </p>
     *
     * @return a {@code Set<Long>} containing the PIDs of all connected peers except the caller.
     */
    public Set<Long> getAllReplicaPIDs() {
        Set<Long> registeredReplicaPIDs = new HashSet<>();
        for (AddrServerRecord record : this.addrServerRecords.values()) {
            if (record.getRole().equals(ServerRole.REPLICA)) {
                registeredReplicaPIDs.add(record.getPID());
            }
        }
        return registeredReplicaPIDs;
    }

    /**
     * Logs the details of this AddrServerRecord object to the console for debugging purposes.
     */
    public void debugPrintServer(AddrServerRecord s) {
        System.out.println("---------- AddrServerRecord Record -----------");
        System.out.printf("Network PID : %s%n", s.getPID());
        System.out.printf("Host Address : %s%n", s.getHostAddress());
        System.out.printf("Client Port  : %d%n", s.getClientPort());
        System.out.printf("Peer Port    : %d%n", s.getPeerPort());
        System.out.printf("ChatServer Port : %d%n", s.getChatServerPort());
        System.out.printf("Role : %s%n", s.getRole());
        System.out.println("---------------------------------------------------");
    }

    public void debugPrintAllServers() {
        System.out.println("|------------- Currently Registered AddressingServer's -------------|");
        for (AddrServerRecord s : this.addrServerRecords.values()) {
            debugPrintServer(s);
        }
        System.out.println("|--------------------------------------------------------------------|");
    }
}

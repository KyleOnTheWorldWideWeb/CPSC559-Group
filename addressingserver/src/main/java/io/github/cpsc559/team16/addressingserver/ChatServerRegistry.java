package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerRegistry {

    /**
     * A mapping of unique chat server IDs to their corresponding {@link ChatServerRecord} records.
     * <p>
     * A {@code ChatServerRecord} record is created for each {@code ChatServer},
     * and subsequently updated anytime the {@code ChatServer} reports any changes to its state
     * to the Primary {@code AddressingServer} for each registered chat server in the network.
     * </p>
     */
    private Map<Long, ChatServerRecord> chatServerRecords = new ConcurrentHashMap<>();


    public Map<Long, ChatServerRecord> getRecords() {
        return chatServerRecords;
    }

    /**
     * Replaces the current set of chat server records with the new set of records.
     * <p>
     * This method updates the AddressingServer's registry of chat servers by replacing its internal
     * address log with the new map passed as a parameter. This allows for dynamic reconfiguration or recovery
     * by resetting the chat server records.
     * </p>
     *
     * @param newChatServerRecords a {@code Map} of chat server IDs to {@link ChatServerRecord} objects representing the new address log.
     */
    public void setChatServerRecords(Map<Long, ChatServerRecord> newChatServerRecords) {
        this.chatServerRecords = newChatServerRecords;
    }


    public void putChatServerRecord(Long chatServerPID, ChatServerRecord record) {
        this.chatServerRecords.put(chatServerPID, record);
    }

    /**
     * Updates an existing ChatServerRecord if the record being passed in contains
     * a process ID (PID) of a process that has already been registered -
     * otherwise a new record is inserted.
     *
     * @param record The ChatServerRecord to insert or update.
     */
    public void updateOrInsertRecord(ChatServerRecord record) {
        Long id = record.getPID();
        if (id == null) {
            System.err.println("AddrServerRecord has a null PID. Cannot update or insert record.");
            return;
        }
        ChatServerRecord existing = chatServerRecords.get(id);
        if (existing != null) {
            debugPrintUpdateServerPID(record);
        } else {
            debugPrintInsertServerPID(record);
        }
        chatServerRecords.put(id, record);
        debugPrintAllServers();
    }


    /**
     * Removes a {@link ChatServerRecord} from the internal registry using the provided process ID (PID) as the key.
     * <p>
     * This method attempts to remove the record associated with the given {@code pid} from the internal
     * {@code chatServerRecords} map. If the record exists and is successfully removed, a confirmation message
     * is printed to the console. If no record is found for the given {@code pid}, a warning message is printed
     * instead.
     * </p>
     *
     * @param pid the unique process ID of the chat server to remove from the registry.
     */
    public void removeRecordByKey(Long pid) {
        ChatServerRecord record = chatServerRecords.remove(pid);
        if (record != null) {
            System.out.printf("Successfully removed *ChatServerRecord* for Network Process with PID: %d - and Host Address: %s%n", pid, record.getHostAddress());
        } else {
            System.out.println("No ChatServerRecord found for PID: " + pid + " — nothing to remove.");
        }
    }



    /**
     * Updates the client count in the {@link io.github.cpsc559.team16.common.dto.ChatServerRecord}
     * associated with the specified ChatServer PID.
     * <p>
     * This method retrieves the {@code ChatServerRecord} corresponding to the provided {@code chatServerPid}
     * from the internal registry map, updates its client count to the value specified by {@code newClientCount},
     * and then returns the updated record.
     * </p>
     *
     * @param newClientCount the new client count to set in the ChatServerRecord.
     * @param chatServerPid  the process ID (PID) of the ChatServer whose record is to be updated.
     * @return the updated {@link io.github.cpsc559.team16.common.dto.ChatServerRecord}.
     * @throws NullPointerException if no ChatServerRecord exists for the given {@code chatServerPid}.
     */
    public ChatServerRecord updateClientCount(int newClientCount, Long chatServerPid) {
        ChatServerRecord record = chatServerRecords.get(chatServerPid);
        if (record == null) {
            System.err.println("Error: ChatServerRecord not found for PID: " + chatServerPid);
            throw new NullPointerException("No ChatServerRecord exists for ChatServer PID: " + chatServerPid);
        }
        record.setClientCount(newClientCount);
        return record;
    }

    /**
     * Returns the greatest process ID (PID) held by any of the active
     * {@code ChatServer}s by iterating through the current set
     * of {@link ChatServerRecord}s.
     * <p>
     *     This method is typically used during failover handling or recovery
     *     to ensure that no newly registered {@code ChatServer} is assigned a
     *     PID that is already in use, preserving PID uniqueness across the system.
     * </p>
     *
     * @return a Long representing the highest PID currently assigned to a {@code ChatServer}
     */
    public Long getMaxPID() {
        Long currentMax = 0L;
        for (ChatServerRecord record : this.chatServerRecords.values()) {
            if (record.getPID() > currentMax) {
                currentMax = record.getPID();
            }
        }
        return currentMax;
    }


    /**
     * Creates a record for a chat server by generating a unique ID and inserting its
     * ChatServerRecord record into the global addressLog.
     * <p>
     * This method generates a unique ID using {@code generatePID()}, creates a new
     * {@link ChatServerRecord} object with the given parameters, and then inserts it into the addressLog. If a record
     * with the same ID already exists, it logs a warning message (and overwrites it).
     * </p>
     *
     * @param serverPID the unique process ID generated by the Primary {@code AddressingServer} for this chat server.
     * @param chatHostAddress the host address (IP or hostname) of the chat server.
     * @param addrServerPort The port used for communication with the addressing server.
     * @param chatClientPort  the port used for client connections.
     * @param chatPeerPort    the port used for peer-to-peer (gossip) communication.
     * @param maxClientCount  the maximum number of client connections allowed for this server.
     *
     */
    public void createChatServerRecord(Long serverPID, String chatHostAddress, int addrServerPort, int chatClientPort,
                                        int chatPeerPort, int maxClientCount) {
        try {
            ChatServerRecord newServer = new ChatServerRecord(serverPID, chatHostAddress, addrServerPort,
                    chatPeerPort, chatClientPort, maxClientCount);
            // Check if the key already existed (should be null for a new key)
            ChatServerRecord previous = this.chatServerRecords.put(serverPID, newServer);

            if (previous != null) {
                System.err.println("WARNING: A server with ID " + serverPID + " already existed. Overwriting existing entry.");
            } else {
                System.out.println("\n**ChatServer** successfully registered with ID: " + serverPID + "\n");
                debugPrintAllServers();
            }
        } catch (Exception e) {
            System.err.println("Error registering chat server: " + e.getMessage());
            throw e;
        }
    }


    /**
     * Logs the details of this ChatServerRecord object to the console for debugging purposes.
     */
    public void debugPrintServer(ChatServerRecord s) {
        System.out.println("\t---------- ChatServerRecord Record ----------");
        System.out.printf("\tProcess ID : %s%n", s.getPID());
        System.out.printf("\tHost Address : %s%n", s.getHostAddress());
        System.out.printf("\tClient Port  : %d%n", s.getClientPort());
        System.out.printf("\tPeer Port    : %d%n", s.getPeerPort());
        System.out.printf("\tClient Count : %d%n", s.getClientCount());
        System.out.printf("\tStatus       : %s%n", s.getStatus());
        System.out.println("\t-------------------------------------------");
    }

    public void debugPrintInsertServerPID(ChatServerRecord s) {
        System.out.println("\t------ Inserting ChatServer Record -------");
        System.out.printf("\tProcess ID : %s%n", s.getPID());
        System.out.printf("\tHost Address : %s%n", s.getHostAddress());
        System.out.println("\t------------------------------------------");
    }

    public void debugPrintUpdateServerPID(ChatServerRecord s) {
        System.out.println("\t------ Updating ChatServer Record -------");
        System.out.printf("\tProcess ID : %s%n", s.getPID());
        System.out.printf("\tHost Address : %s%n", s.getHostAddress());
        System.out.println("\t------------------------------------------");
    }

    public void debugPrintAllServers() {
        System.out.println("|--------------- Currently Registered ChatServer's ---------------|");
        for (ChatServerRecord s : this.chatServerRecords.values()){
            debugPrintServer(s);
        }
        System.out.println("|-----------------------------------------------------------------|");
    }

}

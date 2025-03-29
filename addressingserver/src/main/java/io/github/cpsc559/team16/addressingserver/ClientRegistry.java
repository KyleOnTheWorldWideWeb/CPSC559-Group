package io.github.cpsc559.team16.addressingserver;

import java.util.HashMap;
import java.util.Map;

import io.github.cpsc559.team16.common.dto.ClientRecord;

/**
 * The {@code ClientRegistry} class is responsible for managing client records (login info).
 * <p>
 * It provides methods to add, remove, and validate clients, as well as retrieve client records.
 * </p>
 */
public class ClientRegistry {

    /**
     * A mapping of usernames to their corresponding {@link ClientRecord} records.
     * <p>
     * A {@code ClientRecord} record is created for each client,
     * and subsequently updated anytime the client reports any changes to its state
     * to the Primary {@code AddressingServer} for each registered client in the network.
     * </p>
     */
    private final Map<String, ClientRecord> clientRecords;

    /**
     * Constructs a new {@code ClientRegistry} object.
     * <p>
     * This constructor initializes the client records map.
     * </p>
     */
    public ClientRegistry() {
        this.clientRecords = new HashMap<>();
    }

    /**
     * Adds a new client record to the registry.
     *
     * @param username The username of the client.
     * @param password The password of the client.
     */
    public void addClient(String username, String password) {
        clientRecords.put(username, new ClientRecord(username, password));
    }

    /**
     * Checks if a client exists in the registry.
     *
     * @param username The username of the client.
     * @return true if the client exists, false otherwise.
     */
    public boolean clientExists(String username) {
        return clientRecords.containsKey(username);
    }

    /**
     * Retrieves a client record from the registry.
     *
     * @param username The username of the client.
     * @return The {@link ClientRecord} associated with the username, or null if not found.
     */
    public ClientRecord getClient(String username) {
        return clientRecords.get(username);
    }

    /**
     * Validates a client's credentials.
     *
     * @param username The username of the client.
     * @param password The password of the client.
     * @return true if the credentials are valid, false otherwise.
     */
    public boolean validateClient(String username, String password) {
        ClientRecord record = clientRecords.get(username);
        return record != null && record.getPassword().equals(password);
    }

    /**
     * Removes a client record from the registry.
     *
     * @param username The username of the client.
     */
    public void removeClient(String username) {
        clientRecords.remove(username);
    }
    
}

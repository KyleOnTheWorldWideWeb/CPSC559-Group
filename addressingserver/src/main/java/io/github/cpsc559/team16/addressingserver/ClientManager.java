package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.dto.ClientLogin;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

public class ClientManager {

    private ChatServerRegistry registry;

    private final Map<String, String> clientUsernamePasswordMap = new HashMap<>();

    public ClientManager(ChatServerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Searches for an active {@link ChatServerRecord} that is not full.
     * If found, tries to add a client and returns the updated record.
     *
     * @return Optional containing the updated ChatServerRecord, or empty if none
     *         eligible.
     */
    public Optional<ChatServerRecord> getActiveChatServerRecord() {
        return registry.getRecords().values().stream()
                .peek(server -> System.out.printf("Checking server PID %d — Status: %s, ClientCount: %d, isFull: %b%n",
                        server.getPID(), server.getStatus(), server.getClientCount(), server.isFull()))
                .filter(server -> server.getStatus() == ChatServerRecord.ServerStatus.ACTIVE && !server.isFull())
                .findFirst()
                .flatMap(server -> {
                    int previousCount = server.getClientCount();
                    try {
                        server.addClient();
                        if (server.getClientCount() == previousCount + 1) {
                            return Optional.of(server);
                        } else {
                            System.err.printf("Chat Server ID #%d: client count did not increment correctly.%n",
                                    server.getPID());
                            return Optional.empty();
                        }
                    } catch (ChatServerFullException e) {
                        System.err.printf("Chat Server ID #%d is full after attempting to add a client.%n",
                                server.getPID());
                        return Optional.empty();
                    }
                });
    }

    /**
     * Sends an ACK message to the client with the available ChatServer info.
     *
     * @param primaryPID the PID of the Addressing Server
     * @param nioChannel the channel to send the ACK on
     * @return ChatServerRecord if a host is available, null otherwise
     * @throws IOException if message sending fails
     */
    public ChatServerRecord sendHostAck(Long primaryPID, NIOMessageChannel nioChannel) throws IOException {
        Optional<ChatServerRecord> chatServerOpt = getActiveChatServerRecord();
        if (chatServerOpt.isPresent()) {
            ChatServerRecord updatedRecord = chatServerOpt.get();
            String hostAddress = updatedRecord.getPID() + ":" + updatedRecord.getHostAddress() + ":"
                    + updatedRecord.getClientPort();
            nioChannel.sendMessage(AckMessage.chatHostAddress(primaryPID, hostAddress).toJson());
            return updatedRecord;
        } else {
            nioChannel.sendMessage(AckMessage.noChatHost(primaryPID).toJson());
            return null;
        }
    }

    /**
     * Validates a login attempt by checking the provided username and password.
     * If the username already exists, it checks if the password matches.
     * If the username does not exist, it adds the new username and password to the map.
     *
     * @param username The username of the client.
     * @param password The password of the client.
     * @return true if the login attempt is valid, false otherwise.
     */
    public boolean validateLoginAttempt(ClientLogin loginInfo) {

        String username = loginInfo.getUsername();
        String password = loginInfo.getPassword();

        if (clientUsernamePasswordMap.containsKey(username)) {
            return clientUsernamePasswordMap.get(username).equals(password);
        } else {
            clientUsernamePasswordMap.put(username, password);
            return true;
        }
    }
}

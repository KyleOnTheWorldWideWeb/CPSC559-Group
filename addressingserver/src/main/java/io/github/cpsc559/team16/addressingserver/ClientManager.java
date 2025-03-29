package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.AckObjectTypes;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Optional;

public class ClientManager {

    private ChatServerRegistry registry;

    public ClientManager(ChatServerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Searches for an active {@link io.github.cpsc559.team16.common.dto.ChatServerRecord} that is not full
     * (i.e. clientCount < maxClientCount). Once a candidate is found, it attempts to add a client and performs
     * a failsafe check to ensure that the client count was incremented correctly.
     *
     * @return an Optional containing the updated ChatServerRecord if successful, or Optional.empty()
     *         if no eligible server is found or if the failsafe check fails.
     */
    public Optional<ChatServerRecord> getActiveChatServerRecord() {
        return registry.getRecords().values().stream()
                .filter(server -> server.getStatus() == ChatServerRecord.ServerStatus.ACTIVE && !server.isFull())
                .findFirst()
                .flatMap(server -> {
                    int previousCount = server.getClientCount();
                    try {
                        // Attempt to add a client.
                        server.addClient();
                        // Failsafe: Ensure that clientCount was incremented by one.
                        if (server.getClientCount() == previousCount + 1) {
                            return Optional.of(server);
                        } else {
                            System.err.printf("Chat Server ID #%d: client count did not increment correctly.%n", server.getPID());
                            return Optional.empty();
                        }
                    } catch (ChatServerFullException e) {
                        System.err.printf("Chat Server ID #%d is full after attempting to add a client.%n", server.getPID());
                        return Optional.empty();
                    }
                });
    }

    /**
     * Creates an ACK message to be sent to the client.
     * It leverages {@code getActiveChatServerRecord()} to determine if there is an available active host.
     * If an eligible host is found, it constructs an ACK message with the payload formatted as
     * "pid-hostAddress:clientPort" and sends it via the provided NIOMessageChannel.
     * It then returns the updated ChatServerRecord.
     * If no eligible host is found, it sends an ACK indicating that no host is available and returns null.
     *
     * @param primaryPID  the process ID of the sender (typically the PRIMARY AddressingServer).
     * @param nioChannel the channel used for sending the message.
     * @return the updated ChatServerRecord if a host is available, or null if no eligible host was found.
     * @throws IOException if sending the message fails.
     */
    public ChatServerRecord sendHostAck(Long primaryPID, NIOMessageChannel nioChannel) throws IOException {
        Optional<ChatServerRecord> chatServerOpt = getActiveChatServerRecord();
        if (chatServerOpt.isPresent()) {
            ChatServerRecord updatedRecord = chatServerOpt.get();
            // Construct ACK payload as "pid:hostAddress:clientPort"
            String hostAddress = updatedRecord.getPID() + ":" + updatedRecord.getHostAddress() + ":" + updatedRecord.getClientPort();
            nioChannel.sendMessage(AckMessage.chatHostAddress(primaryPID, hostAddress).toJson());
            return updatedRecord; // This ChatServerRecord has a new client count -> we must broadcast it.
        } else {
            nioChannel.sendMessage(AckMessage.noChatHost(primaryPID).toJson());
            return null;
        }
    }

}

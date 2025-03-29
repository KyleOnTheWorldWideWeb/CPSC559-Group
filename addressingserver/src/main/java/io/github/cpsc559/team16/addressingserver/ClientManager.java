package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.dto.ClientLoginAttempt;
import io.github.cpsc559.team16.common.dto.ClientToken;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.AckTypes;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.ObjectTypes;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

public class ClientManager {

    private final AddressingServer server;

    private final ChatServerRegistry chatServerRegistry;

    private final ClientRegistry clientRegistry;

    public ClientManager(AddressingServer server) {
        this.server = server;
        this.chatServerRegistry = server.getChatServerRegistry();
        this.clientRegistry = server.getClientRegistry();
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
        return chatServerRegistry.getRecords().values().stream()
                .filter(chatServer -> chatServer.getStatus() == ChatServerRecord.ServerStatus.ACTIVE && !chatServer.isFull())
                .findFirst()
                .flatMap(chatServer -> {
                    int previousCount = chatServer.getClientCount();
                    try {
                        // Attempt to add a client.
                        chatServer.addClient();
                        // Failsafe: Ensure that clientCount was incremented by one.
                        if (chatServer.getClientCount() == previousCount + 1) {
                            return Optional.of(chatServer);
                        } else {
                            System.err.printf("Chat Server ID #%d: client count did not increment correctly.%n", chatServer.getPID());
                            return Optional.empty();
                        }
                    } catch (ChatServerFullException e) {
                        System.err.printf("Chat Server ID #%d is full after attempting to add a client.%n", chatServer.getPID());
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
     * @param senderPID  the process ID of the sender (typically the AddressingServer).
     * @param nioChannel the channel used for sending the message.
     * @return the updated ChatServerRecord if a host is available, or null if no eligible host was found.
     * @throws IOException if sending the message fails.
     */
    public ChatServerRecord sendHostAck(long senderPID, NIOMessageChannel nioChannel) throws IOException {
        Optional<ChatServerRecord> chatServerOpt = getActiveChatServerRecord();
        if (chatServerOpt.isPresent()) {
            ChatServerRecord updatedRecord = chatServerOpt.get();
            // Construct ACK payload as "pid-hostAddress:clientPort"
            String payload = updatedRecord.getPID() + "-" + updatedRecord.getHostAddress() + ":" + updatedRecord.getClientPort();
            nioChannel.sendMessage(new AckMessage(AckTypes.HOSTADDRESS, senderPID, Roles.PRIMARY, Roles.CLIENT, payload).toJson());
            return updatedRecord;
        } else {
            nioChannel.sendMessage(new AckMessage(AckTypes.NOHOST, senderPID, Roles.PRIMARY, Roles.CLIENT, "No available host.").toJson());
            return null;
        }
    }

    /**
     * Handles incoming messages from clients.
     * <p>
     * This method is invoked when a client sends a message to the AddressingServer.
     * It processes the message based on its type and takes appropriate actions.
     * </p>
     *
     * @param channel The SocketChannel associated with the client connection.
     * @param nioChannel The NIOMessageChannel used for sending messages.
     * @param message The incoming message from the client.
     */
    public void handleClientMessage(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        // Handle incoming message from a client
        switch (message.getObjectType()) {

            case ObjectTypes.CLIENT_LOGIN_ATTEMPT -> {
                try {
                    handleClientLogin(channel, nioChannel, message);
                } catch (IOException e) {
                    System.err.println("Error handling client login: " + e.getMessage());
                }
            }

            case ObjectTypes.CLIENT_CONNECT_TOKEN -> {
                try {
                    handleClientConnection(channel, nioChannel, message);
                } catch (IOException e) {
                    System.err.println("Error handling client connection: " + e.getMessage());
                }
            }

            default -> {
                System.out.println("Unknown message type received from client.");
            }
        }
    }

    /**
     * 
     * Handles incoming client connections.
     * <p>
     * This method is invoked when an authenticated client attempts to connect to a chat server.
     * It processes the incoming message and sends client chat server information.
     * </p>
     * 
     * @param channel The SocketChannel associated with the client connection.
     * @param nioChannel The NIOMessageChannel used for sending messages.
     * @param message The incoming message from the client.
     */
    public void handleClientConnection(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message)
            throws IOException {

        ClientToken token = message.safeCastPayload(ClientToken.class);

        if (!validateToken(token)) {
            System.out.println("Invalid token received from client.");
            nioChannel.sendMessage(new AckMessage(AckTypes.INVALID_TOKEN, server.getConfig().getPID(), Roles.PRIMARY, Roles.CLIENT, "Invalid token.").toJson());
        } else {
            ChatServerRecord updatedRecord = sendHostAck(server.getConfig().getPID(), nioChannel);
            if (updatedRecord != null) {  // Broadcast ClientCountMessage to all servers.
                System.out.println("Client directed to an active host.");
                long pid = this.server.getConfig().getPID();
                this.server.getPeerManager().broadcastChatServerRecord(pid, updatedRecord);
                this.server.getChatServerManager().broadcastChatServerRecord(pid, updatedRecord);
            } else { System.out.println("All ChatServer's are either FULL or INACTIVE"); }
        }

    }

    /**
     * Handles incoming login from a client.
     * <p>
     * This method is invoked when a client sends a login message to the AddressingServer.
     * It processes the message and sends an appropriate response back to the client.
     * </p>
     *
     * @param channel  The SocketChannel associated with the client connection.
     * @param nioChannel The NIOMessageChannel used for sending messages.
     * @param message  The incoming message from the client.
     */
    private void handleClientLogin(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) 
            throws IOException {
        // WE CAN TUNE THE RESPONSE HERE. FOR NOW I WILL SIMPLY DO AN ACK WITH THE CHATSERVER info as - PID-IPADDRESS:PORTNUMBER

        ClientLoginAttempt clientLoginAttempt = message.safeCastPayload(ClientLoginAttempt.class);

        String username = clientLoginAttempt.getUsername();
        String password = clientLoginAttempt.getPassword();

        // FIRST TIME USER, add client to registry
        if (!clientRegistry.clientExists(username)) {
            System.out.println("New client detected. Adding to registry.");
            server.getClientRegistry().addClient(username, password);

            this.server.getPeerManager().broadcastClientRegistryUpdate(clientRegistry);
            this.server.getChatServerManager().broadcastClientRegistryUpdate(clientRegistry);
        }

        // INCORRECT PASSWORD
        if (!clientRegistry.validateClient(username, password)) {
            System.out.println("Incorrect password for client: " + username);
            nioChannel.sendMessage(new AckMessage(AckTypes.AUTH_FAILED, server.getConfig().getPID(), Roles.PRIMARY, Roles.CLIENT, "Incorrect password.").toJson());
        }
        
        // CLIENT AUTHENTICATED
        else {
            ChatServerRecord updatedRecord = sendHostAck(server.getConfig().getPID(), nioChannel);
            if (updatedRecord != null) {  // Broadcast ClientCountMessage to all servers.
                System.out.println("Client directed to an active host.");
                long pid = this.server.getConfig().getPID();
                this.server.getPeerManager().broadcastChatServerRecord(pid, updatedRecord);
                this.server.getChatServerManager().broadcastChatServerRecord(pid, updatedRecord);
            } else { System.out.println("All ChatServer's are either FULL or INACTIVE"); }
        }

    }

    /**
     * TODO: implement this
     * 
     * Validates the client token.
     * <p>
     * This method checks if the provided token is valid for the client.
     * </p>
     *
     * @param token The client token to be validated.
     * @return true if the token is valid, false otherwise.
     */
    private boolean validateToken(ClientToken token) {
        return true;
    }

}

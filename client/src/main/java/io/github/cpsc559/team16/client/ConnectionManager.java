package io.github.cpsc559.team16.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import io.github.cpsc559.team16.common.dto.ClientToken;
import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.AckTypes;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import static io.github.cpsc559.team16.common.messaging.MessageDeserializer.deserializeMessage;
import io.github.cpsc559.team16.common.messaging.MessageTypes;
import io.github.cpsc559.team16.common.messaging.RegisterMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

public class ConnectionManager {

    /**
     * The host address of the PRIMARY AddressingServer.
     */
    private String addrServerHost;

    public void setAddrServerHost(String addrServerHost) {
        this.addrServerHost = addrServerHost;
    }

    /**
     * The port used for client communication with the PRIMARY AddressingServer.
     */
    private int addrServerPort;

    public void setAddrServerPort(int addrServerPort) {
        this.addrServerPort = addrServerPort;
    }

    /**
     * The host address of the assigned ChatServer.
     */
    private String chatServerHost;

    /**
     * The port used for client communication with the assigned ChatServer.
     */
    private int chatServerPort;

    /**
     * The token used for authentication with the AddressingServer.
     */
    private ClientToken cachedToken;

    /**
     * Constructs a new {@link ConnectionManager} object with the specified parameters.
     *
     * @param primaryHostAddress The host address of the PRIMARY AddressingServer.
     * @param primaryClientPort  The port used for client communication with the PRIMARY AddressingServer.
     */
    public ConnectionManager(String addrServerHost, int addrServerPort) {
        this.addrServerHost = addrServerHost;
        this.addrServerPort = addrServerPort;
    }

    /**
     * Attempts to log in to the AddressingServer using the provided username and password.
     * 
     * @param username
     * @param password
     * @return true if the login was successful, false otherwise.
     */
    public boolean login(String username, String password) {

        AckMessage response = getAddrServerResponse(RegisterMessage.clientLogin(username, password));

        switch (response.getMsgType()) {

            case AckTypes.AUTH_SUCCESS -> {

                ClientToken token = response.safeCastPayload(ClientToken.class);

                if (token == null) {
                    System.err.println("Authentication failed: No token received.");
                    return false;
                } else {
                    // Authentication succeeded
                    this.cachedToken = token;
                    System.out.println("Authentication succeeded!");
                    return true;
                }
            }

            case AckTypes.AUTH_FAILED -> {
                // Authentication failed
                System.err.println("Authentication failed: " + response.safeCastPayload(String.class));
                return false;
            }

            default -> {
                // Unexpected response type
                System.err.println("Unexpected response type: " + response.getMsgType());
                return false;
            }
        }
    }

    /**
     * Attempts to connect to the assigned ChatServer using the cached token.
     * 
     * @return A {@link Socket} object representing the connection to the ChatServer, or null if the connection failed.
     */
    public Socket connectToChatServer() {

        if (cachedToken == null) {
            System.err.println("No token available. Please log in first.");
            return null;
        }

        // Send the token to the AddressingServer
        AckMessage response = getAddrServerResponse(RegisterMessage.clientConnect(cachedToken));

        if (response.getMsgType().equals(MessageTypes.ACK)) {

            // Process the response from the AddressingServer
            switch (response.getMsgType()) {

                case AckTypes.HOSTADDRESS -> {
                    String payload = response.safeCastPayload(String.class);
                    String[] parts = payload.split("-");
                    this.chatServerHost = parts[1].split(":")[0];
                    this.chatServerPort = Integer.parseInt(parts[1].split(":")[1]);
                    System.out.println("Connected to chat server: " + chatServerHost + ":" + chatServerPort);

                    // Attempt to connect to chat server and return the socket
                    try {
                        return new Socket(chatServerHost, chatServerPort);
                    } catch (Exception e) {
                        System.err.println("Failed to connect to chat server: " + e.getMessage());
                        e.printStackTrace();
                        return null;
                    }
                }

                case AckTypes.NOHOST -> {
                    System.err.println("No host available.");
                    return null;
                }

                default -> {
                    System.err.println("Unexpected response type: " + response.getMsgType());
                    return null;
                }
            }
        } else {
            // Handle unexpected response type
            System.err.println("Unexpected response type: " + response.getMsgType());
            return null;
        }

    }

    /**
     * Sends a message to the AddressingServer and waits for a response.
     * <p>
     * This method is used to send a message to the AddressingServer and receive a response.
     * </p>
     *
     * @param message The message to be sent to the AddressingServer.
     * @return The response from the AddressingServer.
     */
    public AckMessage getAddrServerResponse(RegisterMessage message) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(addrServerHost, addrServerPort));
            while (!channel.finishConnect()) {
                Thread.sleep(100);
            }

            NIOMessageChannel nioChannel = new NIOMessageChannel(channel);

            nioChannel.sendMessage(message.toJson());

            channel.configureBlocking(false);

            System.out.println("Message from CLIENT sent to PRIMARY addressing server.");

            String serializedResponse = nioChannel.receiveMessage();
            BaseAddrServerMessage response = deserializeMessage(serializedResponse);

            if (response.getMsgType().equals(MessageTypes.ACK)) {
                return (AckMessage) response;
            } else {
                System.err.println("Unexpected response type received from server: " + response.getMsgType());
            }
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to send message to addressing server: " + e.getMessage());
            e.printStackTrace();
        }
        return null;

    }
    
}

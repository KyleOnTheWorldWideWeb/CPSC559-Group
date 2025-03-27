package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import static io.github.cpsc559.team16.common.messaging.MessageDeserializer.deserializeMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import io.github.cpsc559.team16.common.utilities.NetworkManager;
import io.github.cpsc559.team16.common.messaging.MessageTypes;
import io.github.cpsc559.team16.common.messaging.ObjectTypes;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.messaging.AckObjectTypes;

/**
 * Handles read events from registered {@code SocketChannel}'s and routes them based on the server's role.
 * <p>
 * This class is responsible for delegating read events to the appropriate handlers.
 * It uses a cascading sequence of switch statements to decide which method to call:
 * </p>
 * <ul>
 *     <li>{@code Message Type} - e.g. UPDATE, REQUEST, PING</li>
 *     <li>{@code Sender Role} - e.g. REPLICA, PRIMARY, CHATSERVER</li>
 *     <li>{@code Object Type} - e.g. ChatServerRecord, AddrServerRecord.
 *     <p>This can be an actual object, or simply an "identifier" - e.g. AllChatServerInfo -
 *     the {@code MessageDeserializer} is capable of parsing non-objects and will not cause an error.</p>
 *     </li>
 * </ul>
 */
public class AddrServerReadDispatcher implements NetworkManager.ReadDispatcher {
    private final AddressingServer server;
    private final PeerManager peerManager;
    private final ClientManager clientManager;
    private final ChatServerManager chatServerManager;

    public AddrServerReadDispatcher(AddressingServer server) {
        this.server = server;
        this.peerManager = server.getPeerManager();
        this.clientManager = server.getClientManager();
        this.chatServerManager = server.getChatServerManager();
    }

    /**
     * Retrieves the process ID for this AddrssingServer
     * @return A Long representing the process ID for this process in the network.
     */
    private Long getPID() {
        return this.server.getConfig().getPID();
    }

    /**
     * Uses an {@link NIOMessageChannel} for safe and abstracted message I/O between two processes
     * on a {@link SocketChannel}. JSON messages are automatically deserialized into a {@link BaseAddrServerMessage}
     * and routed through the {@code dispatchMsgType} method - the first in a cascading sequence of routing methods.
     *
     * @param channel The {@link SocketChannel} used to receive the message.
     * @param nioChannel The {@link NIOMessageChannel} used to decode and encode the message.
     *
     * @throws ConnectionClosedException if the remote process has closed the connection.
     * @throws IOException if an I/O error occurs during message reading or processing.
     */
    public void dispatch(SocketChannel channel, NIOMessageChannel nioChannel) throws ConnectionClosedException, IOException {
        String msgJson;
        while ((msgJson = nioChannel.receiveMessage()) != null) {
            BaseAddrServerMessage<?> message = deserializeMessage(msgJson);
            if (message != null) {
                dispatchMsgType(channel, nioChannel, message);
            } else {
                System.err.println("Could not deserialize incoming message: " + msgJson);
            }
        }
    }

    /**
     * Dispatches a received message based on its type, sender role, and object type.
     *
     * @param channel The {@link SocketChannel} used to receive the message.
     * @param nioChannel The {@link NIOMessageChannel} used to decode and encode the message.
     * @param message The parsed {@link BaseAddrServerMessage} containing the message details.
     *
     */
    public void dispatchMsgType(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getMsgType()) {
            case MessageTypes.ACK -> handleAck(channel, nioChannel, message);
            // I have removed this case so that ONLY a new connection request can register with the Primary. This is handled in the main event loop.
            //case MessageTypes.REGISTER -> handleRegistration(channel, nioChannel, message);
            case MessageTypes.UPDATE -> handleUpdate(channel, nioChannel, message);
            case MessageTypes.REQUEST -> handleRequest(channel, nioChannel, message);
            case MessageTypes.PING -> handlePing(channel, nioChannel, message);
            case MessageTypes.NOTIFICATION -> handleNotification(channel, nioChannel, message);
            case MessageTypes.SERVERFAILURE -> handleServerFailure(channel, nioChannel, message);
            case MessageTypes.ELECTION -> handleElection(channel, nioChannel, message);
            default -> System.err.println("Unrecognized message type: " + message.getMsgType());
        }
    }

    /**
     * <NOTE> Can probably remove the channel parameters, but I'm keeping them for now in case an ACK needs to trigger an action.</NOTE>
     */
    private void handleAck(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> ackMessage) {
        switch (ackMessage.getObjectType()) {
            case AckObjectTypes.REGISTERED -> {
                System.out.println("ACK ENTERED");
                // This should ONLY ever be received by a REPLICA from the PRIMARY AddressingServer
                // A Registration ACK is always sent with the pid as a string for the process as the payload.
                Long assignedPID = ackMessage.safeCastPayload(Long.class);
                server.getConfig().setPID(assignedPID);
                // This nioChannel was used to send the REGISTER message that triggered this ACK.
                // We didn't know the PRIMARY AddressingServer PID when making that initial connection -> Set it now
                nioChannel.setServerPID(ackMessage.getSenderPID());
                System.out.println("Registration ACK received. This process has been assigned PID #" + server.getConfig().getPID());
            }
            default -> System.err.println("Unrecognized ACK response: " + ackMessage.getObjectType());
        }
    }

    /**
     * Handles a register message based on the sender role and object type.
     *
     * @param channel The channel from which the message originated.
     * @param registerMessage The received update message.
     */
    @Override
    public void handleRegistration(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> registerMessage)
            throws IOException {
        switch (registerMessage.getSenderRole()) {
            case Roles.CLIENT -> {
                // WE CAN TUNE THE RESPONSE HERE. FOR NOW I WILL SIMPLY DO AN ACK WITH THE CHATSERVER info as - PID-IPADDRESS:PORTNUMBER
                ChatServerRecord updatedRecord = this.clientManager.sendHostAck(server.getConfig().getPID(), nioChannel);
                if (updatedRecord != null) {  // Broadcast ClientCountMessage to all servers.
                    System.out.println("Client directed to an active host.");
                    Long pid = this.getPID();
                    this.chatServerManager.broadcastChatServerRecord(pid, updatedRecord);
                    this.server.getChatServerRegistry().debugPrintServer(updatedRecord);
                } else { System.out.println("All ChatServer's are either FULL or INACTIVE"); }
            }
            case Roles.CHATSERVER -> {
                Long pid = this.getPID();
                // {@code registerServer} sends all the ChatServer records to the Chat Server we are registering.
                ChatServerRecord record = this.chatServerManager.registerServer(
                        channel, nioChannel,
                        this.server.generatePID(), pid,
                        registerMessage.safeCastPayload(ChatServerRecord.class)
                );
                this.chatServerManager.sendAllAddrServerRecords(pid, nioChannel, this.server.getAddrServerRegistry().getRecords());
                this.chatServerManager.broadcastChatServerRecord(pid, record); // Broadcast the record to all chat servers
                this.peerManager.broadcastChatServerRecord(pid, record);       // Broadcast the record to all Replicas
                this.server.getChatServerRegistry().debugPrintAllServers();
            }
            case Roles.REPLICA -> {
                Long pid = this.getPID();
                // {@code registerPeer} sends all the current AddrServer records to the replica we are registering.
                AddrServerRecord record = this.peerManager.registerPeer(
                        channel, nioChannel,
                        this.server.generatePID(), pid,
                        registerMessage.safeCastPayload(AddrServerRecord.class)
                );
                this.peerManager.sendAllChatServerRecords(pid, nioChannel, this.server.getChatServerRegistry().getRecords());
                this.peerManager.broadcastAddrServerRecord(pid, record);
                this.chatServerManager.broadcastAddrServerRecord(pid, record);
                this.server.getAddrServerRegistry().debugPrintAllServers();
            }
            default -> throw new IllegalArgumentException("Unrecognized sender role for REGISTER: " + registerMessage.getSenderRole());
        }
    }

    private void handleUpdate(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> updateMessage) {
        switch (updateMessage.getSenderRole()) {
            case Roles.CHATSERVER -> {
                switch (updateMessage.getObjectType()) {
                    case ObjectTypes.CLIENT_COUNT -> {
                        int newClientCount = updateMessage.safeCastPayload(Integer.class);
                        Long csPid = updateMessage.getSenderPID();
                        try {
                            ChatServerRecord updatedRecord = this.server.getChatServerRegistry().updateClientCount(newClientCount, csPid);
                            peerManager.broadcastChatServerRecord(this.getPID(), updatedRecord);
                            chatServerManager.broadcastChatServerRecord(this.getPID(), updatedRecord);
                        } catch (NullPointerException e) {
                            System.err.println(e.getMessage());
                            // TODO - Add response that tell the ChatServer to re-register.
                        }
                    }
                    case ObjectTypes.CHAT_SERVER_RECORD -> {
                    }
                }
            }
            case Roles.PRIMARY -> {
                switch (updateMessage.getObjectType()) {
                    case ObjectTypes.ADDR_SERVER_RECORD -> {
                        System.out.println("REPLICA is updating AddrServerRecord received from PRIMARY");
                        peerManager.updateRecords(updateMessage.safeCastPayload(AddrServerRecord.class));
                    }
                    case ObjectTypes.CHAT_SERVER_RECORD -> {
                        chatServerManager.updateRecords(updateMessage.safeCastPayload(ChatServerRecord.class));
                        System.out.println("REPLICA is updating ChatServerRecord received from PRIMARY");
                        this.server.getChatServerRegistry().debugPrintAllServers();
                    }
                }
            }
            case Roles.REPLICA -> {
                switch (updateMessage.getObjectType()) {
                    case ObjectTypes.ADDR_SERVER_RECORD -> {
                        System.out.println("REPLICA is in switch statement for updating AddrServerRecord from REPLICA");
                        peerManager.updateRecords(updateMessage.safeCastPayload(AddrServerRecord.class));
                    }
                    case ObjectTypes.CHAT_SERVER_RECORD -> {
                        System.out.println("REPLICA is in switch statement for updating ChatServerRecord from REPLICA");
                        chatServerManager.updateRecords(updateMessage.safeCastPayload(ChatServerRecord.class));
                    }
                }
            }
            default -> System.err.println("Unrecognized sender role for UPDATE: " + updateMessage.getSenderRole());
        }
    }

    /**
     * Handles a request message based on the sender role and object type.
     *
     * @param channel The channel from which the request originated.
     * @param requestMessage The received request message.
     */
    private void handleRequest(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> requestMessage) {
        switch (requestMessage.getSenderRole()) {
            case Roles.CHATSERVER -> {
                if ("AllAddrServerInfo".equals(requestMessage.getObjectType())) {
                    // server.sendAddrServerInfo(channel);
                }
            }
            case Roles.REPLICA -> {
                if ("AllChatServerInfo".equals(requestMessage.getObjectType())) {
                    // server.sendChatServerInfo(channel);
                }
            }
            default -> System.err.println("Unrecognized sender role for REQUEST: " + requestMessage.getSenderRole());
        }
    }

    /**
     * Handles a ping request, typically used for failure detection.
     *
     * @param channel The channel from which the ping request originated.
     * @param message The received ping message.
     */
    private void handlePing(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        if (Roles.PRIMARY.equals(message.getTargetRole())) {
            //server.respondToPing(channel);
        }
    }

    /**
     * Handles server failure messages.
     *
     * @param channel The channel from which the message originated.
     * @param message The received server failure message.
     */
    private void handleServerFailure(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {

    }

    /**
     * Handles notification messages. THIS MAY NOT BE NEEDED
     *
     * @param channel The channel from which the notification originated.
     * @param message The received notification message.
     */
    private void handleNotification(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> message) {
        switch (message.getObjectType()) {
            case ObjectTypes.ELECTION_VOTE -> {

            }
            default -> System.err.println("Unknown notification message received and ignored.");
        }
    }




    /**
     * Handles election-related messages, used for leader election among Addressing Servers.
     *
     * @param channel The channel from which the election message originated.
     * @param electionMessage The received election message.
     */
    private void handleElection(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> electionMessage) {
        server.getLeaderElectionManager().processElectionMessage(channel, nioChannel, electionMessage);
    }

}

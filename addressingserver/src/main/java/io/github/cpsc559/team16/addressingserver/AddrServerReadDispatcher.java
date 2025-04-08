package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.*;

import static io.github.cpsc559.team16.common.messaging.MessageDeserializer.deserializeMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

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
public class AddrServerReadDispatcher {
    private final AddressingServer server;
    private final PeerManager peerManager;
    private final ReplicaSyncCoordinator replicaCoordinator;
    private final ClientManager clientManager;
    private final ChatServerManager chatServerManager;
    private final BroadcastManager broadcastManager;
    private final MessageIDGenerator genMID;

    private final ConnectionCleanupManager cleanupManager;

    public AddrServerReadDispatcher(AddressingServer server) {
        this.server = server;
        this.peerManager = server.getPeerManager();
        this.clientManager = server.getClientManager();
        this.chatServerManager = server.getChatServerManager();
        this.broadcastManager = server.getBroadcastManager();
        this.genMID = server.getMessageIDGenerator();
        this.cleanupManager = server.getCleanupManager();
        this.replicaCoordinator = server.getReplicaCoordinator();
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


    // Example: In handleAck (or a new case for replication acknowledgments), delegate to replicationManager.
    private void handleReplicationAck(BaseAddrServerMessage<?> ackMessage) {
        // The payload is a boolean - True if the message this ACK is for was successfully processed.
        if (ackMessage.safeCastPayload(Boolean.class)) {
            System.out.println("Replicated ACK received, success? " + ackMessage.safeCastPayload(Boolean.class));
            // The messageID of this ackMessage is the same unique message ID as the message that triggered it.
            // This is how we know if a message sent has been successfully received and processed.
            Long eventID = ackMessage.getMessageID(); // adjust extraction as needed
            Long replicaPID = ackMessage.getSenderPID();
            System.out.printf("Primary has received ACK for message ID: %d - from Replica with PID: %d%n", eventID, replicaPID);
            // This will return null if the message is sent successfully to the original requested.
            // Otherwise, the channel is returned so that we can take appropriate action and shut it down/cleanup.
            // NOTE: This is not the channel of a replica, it is a channel tied to whichever process originally made a
            // request of the Primary addressing server which required a write/update to the state -> SC action required.
            NIOMessageChannel senderChannel = replicaCoordinator.processAck(eventID, replicaPID);
            if (senderChannel != null) {
                this.server.getCleanupManager().cleanupPersistentConnection(senderChannel.getSocketChannel(), true);
            }
        }
        else {
            // TODO - Implement retries OR make the replica send a request, but that might not work, because how do we know it got the message
            //  the next time so we can send this response.
            //  Better solution is to create a new event that only includes the PID of the replica who failed to process the update.
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
                genMID.setPID(assignedPID);
                // This nioChannel was used to send the REGISTER message that triggered this ACK.
                // We didn't know the PRIMARY AddressingServer PID when making that initial connection -> Set it now
                nioChannel.setServerPID(ackMessage.getSenderPID());
                System.out.println("Registration ACK received. This process has been assigned PID #" + server.getConfig().getPID());
            }
            case AckObjectTypes.REPLICATED -> {
                System.out.println("Replicated message received.");
                handleReplicationAck(ackMessage);
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
    public void handleRegistration(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> registerMessage)
            throws IOException {
        switch (registerMessage.getSenderRole()) {
            case Roles.CLIENT -> {
                // WE CAN TUNE THE RESPONSE HERE. FOR NOW I WILL SIMPLY DO AN ACK WITH THE CHATSERVER info as - PID-IPADDRESS:PORTNUMBER
                ChatServerRecord updatedRecord = this.clientManager.sendHostAck(server.getConfig().getPID(), nioChannel);
                if (updatedRecord != null) {  // Broadcast ClientCountMessage to all servers.
                    System.out.println("Client directed to an active host.");
                    Long pid = this.getPID();
                    this.broadcastManager.broadcastChatServerRecordToCS(pid, updatedRecord); // Broadcast the updated (client count) record to all servers.
                    this.server.getChatServerRegistry().debugPrintServer(updatedRecord);
                } else { System.out.println("All ChatServer's are either FULL or INACTIVE"); }
            }
            case Roles.CHATSERVER -> {
                Long pid = this.getPID();
                // Send all the AddrServerRecord's and ChatServerRecords to the newly registered process.
                this.broadcastManager.sendAllRecordsToProcess(pid, nioChannel,
                        server.getChatServerRegistry().getRecords(),
                        server.getAddrServerRegistry().getRecords());
                // Register the new process and create a ChatServerRecord for it.
                ChatServerRecord record = this.chatServerManager.registerServer(
                        channel, nioChannel,
                        this.server.generatePID(), pid,
                        registerMessage.safeCastPayload(ChatServerRecord.class)
                );
                this.broadcastManager.broadcastChatServerRecordToCS(pid, record); // Broadcast the record to all servers.
                this.server.getChatServerRegistry().debugPrintAllServers();
            }
            case Roles.REPLICA -> {
                System.out.println("Replica registration message has been received.");
                this.server.getRegistrationCoordinator().handleReplicaRegistration(channel, nioChannel, registerMessage);
            }
            default -> throw new IllegalArgumentException("Unrecognized sender role for REGISTER: " + registerMessage.getSenderRole());
        }
    }

    private void handleUpdate(SocketChannel channel, NIOMessageChannel nioChannel, BaseAddrServerMessage<?> updateMessage) {
        switch (updateMessage.getSenderRole()) {
            case Roles.CHATSERVER -> {
                switch (updateMessage.getObjectType()) {  // THIS IS THE CASE WHERE A CHAT SERVER UPDATES THE ADDRESSING SERVER WITH ITS NEW CLIENT COUNT.
                    case ObjectTypes.CLIENT_COUNT -> {
                        int newClientCount = updateMessage.safeCastPayload(Integer.class);
                        Long csPid = updateMessage.getSenderPID();
                        try {
                            ChatServerRecord updatedRecord = this.server.getChatServerRegistry().updateClientCount(newClientCount, csPid);
                            broadcastManager.broadcastChatServerRecordToCS(this.getPID(), updatedRecord);
//                            peerManager.broadcastChatServerRecord(this.getPID(), updatedRecord);
//                            chatServerManager.broadcastChatServerRecord(this.getPID(), updatedRecord);
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
                        peerManager.updateRecords(updateMessage.safeCastPayload(AddrServerRecord.class));
                        // All update messages that are triggered by a request that requires state synchronization have a messageID > 0
                        if (updateMessage.getMessageID() != 0) {
                            // TODO - move this into peerManager once it is working
                            try {
                                System.out.println("Sending 'Replicated' ACK to Primary for message ID: " + updateMessage.getMessageID());
                                nioChannel.sendMessage(AckMessage.replicated(
                                        updateMessage.getMessageID(),
                                        this.server.getConfig().getPID(), true).toJson());
                            }
                            catch (JsonProcessingException e) {
                                System.err.printf(
                                        "Failed to serialize AckMessage<%s> for broadcast. Context: messageID=%d, senderPID=%d, senderRole=%s. Exception: %s%n",
                                        updateMessage.getObjectType(), updateMessage.getMessageID(), this.server.getConfig().getPID(), Roles.REPLICA, e.getMessage()
                                );
                            }
                            catch (IOException ioe) {
                                System.err.println("Failed to send ACK for message ID: " + updateMessage.getMessageID());
                                this.cleanupManager.cleanupPersistentConnection(channel, true);
                            }
                        }
                    }
                    case ObjectTypes.CHAT_SERVER_RECORD -> {
                        // TODO - add updated logic from above here as well.
                        chatServerManager.updateRecords(updateMessage.safeCastPayload(ChatServerRecord.class));
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
        switch (message.getObjectType()) {
            case ObjectTypes.ADDRSERVER_FAILURE -> {
                Long failedPID = message.safeCastPayload(Long.class);
                peerManager.removeFailedServer(failedPID);
                this.server.getAddrServerRegistry().debugPrintAllServers();
            }
            case ObjectTypes.CHATSERVER_FAILURE -> {
                Long failedPID = message.safeCastPayload(Long.class);
                chatServerManager.removeFailedChatServer(failedPID);
                this.server.getChatServerRegistry().debugPrintAllServers();
            }
        }
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

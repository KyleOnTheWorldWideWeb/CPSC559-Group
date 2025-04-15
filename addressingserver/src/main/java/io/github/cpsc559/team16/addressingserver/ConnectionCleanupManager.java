package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.*;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;

public class ConnectionCleanupManager {

    /**
     * The {@code MessageIDGenerator} instance used by this server to produce
     * globally unique message IDs.
     * <p>
     * This generator ensures that every message requiring acknowledgment or
     * ordering has a distinct identifier,
     * which is critical for maintaining consistency guarantees (e.g., in
     * replication or event tracking).
     * </p>
     * <p>
     * The generator is initialized once per process and typically updated with the
     * server's assigned PID
     * after registration, ensuring message IDs are globally unique across all
     * AddressingServer processes.
     * </p>
     */
    private final MessageIDGenerator genMID;

    /**
     * The process responsible for managing interactions between the Primary
     * {@code AddressingServer} and its replicas.
     */
    private final PeerManager peerManager;

    public PeerManager getPeerManager() {
        return peerManager;
    }

    /**
     * Manages synchronization and state consistency between the PRIMARY and all
     * registered REPLICA AddressingServers.
     * <p>
     * This coordinator encapsulates the logic needed to track acknowledgments,
     * manage pending events,
     * handle retry logic, and ensure that all state changes
     * (e.g. new server registrations/removal, server updates, leadership changes)
     * are safely replicated across the distributed network.
     * </p>
     */
    private ReplicaSyncCoordinator replicaCoordinator;

    /**
     * The process responsible for managing interactions between the
     * {@code AddressingServer} and {@code ChatServer}'s
     */
    private final ChatServerManager chatServerManager;

    public ChatServerManager getChatServerManager() {
        return chatServerManager;
    }

    public ConnectionCleanupManager(PeerManager peerManager,
            ChatServerManager chatServerManager, MessageIDGenerator genMID) {
        this.genMID = genMID;
        this.peerManager = peerManager;
        this.chatServerManager = chatServerManager;
    }

    public void setReplicaCoordinator(ReplicaSyncCoordinator replicaCoordinator) {
        this.replicaCoordinator = replicaCoordinator;
    }

    /**
     * Determines whether the specified {@link SocketChannel} is associated with a
     * persistent server-to-server connection.
     * <p>
     * Persistent connections are long-lived channels used for internal
     * communication between
     * {@code AddressingServer}s (peers) and {@code ChatServer}s. These are stored
     * and tracked
     * using their respective manager classes.
     * </p>
     *
     * @param channel the {@code SocketChannel} to inspect.
     * @return {@code true} if the channel is known to be persistent (i.e., belongs
     *         to a peer or chat server), {@code false} otherwise.
     */
    public boolean isPersistentConnection(SocketChannel channel) {
        return peerManager.getChannels().containsKey(channel)
                || chatServerManager.getChannels().containsKey(channel);
    }

    /**
     * Retrieves the {@link NIOMessageChannel} wrapper for a known persistent
     * connection.
     * <p>
     * This method searches the internal maps of both the {@code PeerManager} and
     * {@code ChatServerManager}
     * to find the {@code NIOMessageChannel} corresponding to the provided
     * {@link SocketChannel}.
     * </p>
     * <p>
     * If the channel is not found in either manager, the method returns
     * {@code null}.
     * </p>
     *
     * @param channel the {@code SocketChannel} to look up.
     * @return the associated {@code NIOMessageChannel}, or {@code null} if not
     *         found.
     */
    public NIOMessageChannel getKnownPersistentChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerManager.getChannels().get(channel);
        if (ch != null)
            return ch;
        return chatServerManager.getChannels().get(channel); // Will return null if it doesn't exist (which is what we
                                                             // want)
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal
     * selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters
     * an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     * <li>Logs the reason for cleanup (remote disconnect or local I/O
     * failure).</li>
     * <li>Removes the connection from either the {@code PeerManager} or
     * {@code ChatServerManager}.</li>
     * <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *
     * @param nioChannel the {@code NIOMessageChannel} associated with the error.
     * @param cce        {@code true} if the cleanup is due to a remote disconnect
     *                   (i.e., {@link ConnectionClosedException}),
     *                   {@code false} if due to a local I/O failure.
     */
    public void cleanupPersistentConnectionNIO(NIOMessageChannel nioChannel, Boolean cce) {
        SocketChannel channel = nioChannel.getSocketChannel();
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                Long failedPID = ch.getServerPID();
                if (failedPID != 0L) { // OL represents an unregistered process - so registering the server is
                                       // unnecessary
                    Long primaryPID = peerManager.getPrimaryPID();
                    if (chatServerManager.getChannels().containsKey(channel)) {
                        // Create Pending event here
                        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
                        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager
                                .getRegisteredReplicaChannelMapNoFailedPID(failedPID);
                        for (Long pid : replicaChannelMap.keySet()) {
                            System.out.println("PID for the PendingEvent replica = " + pid);
                        }
                        // Create unique message ID that will be used to track ACK messages as well as
                        // the pending event.
                        long messageID = genMID.nextID();
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received
                        ServerFailureMessage<Long> msg = this.getFailureMessage(messageID, primaryPID, failedPID,
                                Roles.CHATSERVER);
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received.
                        PendingEvent event = this.createChatServerDeregistrationEvent(
                                msg, channel, replicaChannelMap, primaryPID, failedPID, Roles.CHATSERVER);
                        // Add this to the list of pending events. The message ID used for replication
                        // messages is the key.
                        replicaCoordinator.addPendingEvent(messageID, event);
                        // Broadcast the message to all current Replicas. Any NIOChannel with PID 0
                        // (unregistered channels) will not be included.
                        broadcastFailureToReplicas(msg, primaryPID, failedPID, Roles.CHATSERVER);
                    } else if (peerManager.getChannels().containsKey(channel)) {
                        // Create Pending event here

                        // Create Pending event here
                        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
                        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager
                                .getRegisteredReplicaChannelMapNoFailedPID(failedPID);
                        // Create unique message ID that will be used to track ACK messages as well as
                        // the pending event.
                        long messageID = genMID.nextID();
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received
                        ServerFailureMessage<Long> msg = this.getFailureMessage(messageID, primaryPID, failedPID,
                                Roles.REPLICA);
                        for (Long pid : replicaChannelMap.keySet()) {
                            System.out.println("PID for the PendingEvent replica = " + pid);
                        }
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received.
                        PendingEvent event = this.createReplicaDeregistrationEvent(
                                msg, channel, replicaChannelMap, primaryPID, failedPID, Roles.REPLICA);
                        // Add this to the list of pending events. The message ID used for replication
                        // messages is the key.
                        replicaCoordinator.addPendingEvent(messageID, event);
                        // Broadcast the message to all current Replicas. Any NIOChannel with PID 0
                        // (unregistered channels) will not be included.
                        broadcastFailureToReplicas(msg, primaryPID, failedPID, Roles.REPLICA);

                        broadcastServerFailure(peerManager.getPrimaryPID(), failedPID, Roles.REPLICA);
                        peerManager.removeProcessCloseConnection(channel);
                        this.peerManager.debugPrintAllServers();
                    }
                } else {
                    if (chatServerManager.getChannels().containsKey(channel)) {
                        chatServerManager.removeProcessCloseConnection(channel);
                    } else if (peerManager.getChannels().containsKey(channel)) {
                        peerManager.removeProcessCloseConnection(channel);
                    }
                }
            }
        }
        // key.cancel(); Keys for closed SocketChannels are canceled during the next
        // selector.select event in the main event loop. No need to do that here.
        try {
            channel.close();
        } catch (IOException ignored) {
        } // if the channel is already closed, we don't need to do anything.
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal
     * selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters
     * an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     * <li>Logs the reason for cleanup (remote disconnect or local I/O
     * failure).</li>
     * <li>Removes the connection from either the {@code PeerManager} or
     * {@code ChatServerManager}.</li>
     * <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *
     * @param channel the {@code SocketChannel} being cleaned up.
     * @param cce     {@code true} if the cleanup is due to a remote disconnect
     *                (i.e., {@link ConnectionClosedException}),
     *                {@code false} if due to a local I/O failure.
     */
    public void cleanupPersistentConnection(SocketChannel channel, Boolean cce) {
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                System.out.println("Channel not null");
                Long failedPID = ch.getServerPID();
                if (failedPID != 0L) { // OL represents an unregistered process - so registering the server is
                                       // unnecessary
                    System.out.println("Unregistered process");

                    Long primaryPID = peerManager.getPrimaryPID();
                    if (chatServerManager.getChannels().containsKey(channel)) {
                        // Create Pending event here
                        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
                        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager
                                .getRegisteredReplicaChannelMapNoFailedPID(failedPID);
                        for (Long pid : replicaChannelMap.keySet()) {
                            System.out.println("PID for the PendingEvent replica = " + pid);
                        }
                        // Create unique message ID that will be used to track ACK messages as well as
                        // the pending event.
                        long messageID = genMID.nextID();
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received
                        ServerFailureMessage<Long> msg = this.getFailureMessage(messageID, primaryPID, failedPID,
                                Roles.CHATSERVER);
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received.
                        PendingEvent event = this.createChatServerDeregistrationEvent(
                                msg, channel, replicaChannelMap, primaryPID, failedPID, Roles.CHATSERVER);
                        // Add this to the list of pending events. The message ID used for replication
                        // messages is the key.
                        replicaCoordinator.addPendingEvent(messageID, event);
                        // Broadcast the message to all current Replicas. Any NIOChannel with PID 0
                        // (unregistered channels) will not be included.
                        System.out.println("Broadcasting");
                        broadcastFailureToReplicas(msg, primaryPID, failedPID, Roles.CHATSERVER);
                    } else if (peerManager.getChannels().containsKey(channel)) {
                        // Create Pending event here
                        System.out.println("in else If");

                        // Create Pending event here
                        // Get the list of all NIOMessage channels for registered peers (non-zero PID)
                        Map<Long, NIOMessageChannel> replicaChannelMap = peerManager
                                .getRegisteredReplicaChannelMapNoFailedPID(failedPID);
                        for (Long pid : replicaChannelMap.keySet()) {
                            System.out.println("PID for the PendingEvent replica = " + pid);
                        }
                        // Create unique message ID that will be used to track ACK messages as well as
                        // the pending event.
                        long messageID = genMID.nextID();
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received
                        ServerFailureMessage<Long> msg = this.getFailureMessage(messageID, primaryPID, failedPID,
                                Roles.REPLICA);
                        // Create a new event that will trigger once all ACKs for synchronizing state
                        // have been received.
                        PendingEvent event = this.createReplicaDeregistrationEvent(
                                msg, channel, replicaChannelMap, primaryPID, failedPID, Roles.REPLICA);
                        // Add this to the list of pending events. The message ID used for replication
                        // messages is the key.
                        replicaCoordinator.addPendingEvent(messageID, event);
                        // Broadcast the message to all current Replicas. Any NIOChannel with PID 0
                        // (unregistered channels) will not be included.
                        broadcastFailureToReplicas(msg, primaryPID, failedPID, Roles.REPLICA);

                        System.out.println("Broadcasting");
                        broadcastServerFailure(peerManager.getPrimaryPID(), failedPID, Roles.REPLICA);
                        peerManager.removeProcessCloseConnection(channel);
                        this.peerManager.debugPrintAllServers();
                    }
                } else {
                    System.out.println("Else");
                    if (chatServerManager.getChannels().containsKey(channel)) {
                        chatServerManager.removeProcessCloseConnection(channel);
                    } else if (peerManager.getChannels().containsKey(channel)) {
                        peerManager.removeProcessCloseConnection(channel);
                    }
                }
            }
        }
        // key.cancel(); Keys for closed SocketChannels are canceled during the next
        // selector.select event in the main event loop. No need to do that here.
        try {
            channel.close();
        } catch (IOException ignored) {
        } // if the channel is already closed, we don't need to do anything.
    }

    private ServerFailureMessage<Long> getFailureMessage(Long messageID, Long senderPID, Long failedPID,
            String failedServerRole) {
        // Create the message with the proper {@code ObjectType} so the receiver knows
        // which kind of record/connection to remove.
        ServerFailureMessage<Long> message;
        if (failedServerRole.equals(Roles.CHATSERVER)) {
            message = ServerFailureMessage.chatServerFailed(messageID, senderPID, Roles.PRIMARY, failedServerRole,
                    failedPID);
        } else { // It's an addressing server (REPLICA or PRIMARY)
            message = ServerFailureMessage.addrServerFailed(messageID, senderPID, Roles.PRIMARY, failedServerRole,
                    failedPID);
        }
        return message;
    }

    /**
     * Broadcasts a server failure notification to all connected peer channels.
     * <p>
     * This method creates a {@code ServerFailureMessage} indicating that a server
     * process has failed.
     * If the failed server's role is {@code Roles.CHATSERVER}, the failure message
     * is created via
     * {@code ServerFailureMessage.chatServerFailed(Long, String, String, Long)};
     * otherwise, it is created via
     * {@code ServerFailureMessage.addrServerFailed(Long, String, String, Long)}.
     * </p>
     * <p>
     * The generated message is then serialized into JSON and sent to every channel
     * found in the provided maps.
     * If an error occurs during serialization, the error is logged and an empty
     * list is returned.
     * If an error occurs while sending to a channel, that channel is added to the
     * list of failures.
     * </p>
     *
     */
    private void broadcastFailureToReplicas(BaseAddrServerMessage<?> message, Long senderPID, Long failedPID,
            String failedServerRole) {
        // Serialize the message and return if a failure occurs. This would only happen
        // because of a
        // logic error introduced by us (the programmers), so it shouldn't shut down the
        // program, but we need to log it and fix it.
        String jsonMessage;
        try {
            jsonMessage = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.printf(
                    "Failed to serialize ServerFailureMessage<%s> for broadcast. Context: senderPID=%d, senderRole=%s, failedPID=%d, failedServerRole=%s. Exception: %s%n",
                    message.getObjectType(), senderPID, Roles.PRIMARY, failedPID, failedServerRole, e.getMessage());
            return;
        }
        // Send the message to each addressing server in the network. If a failure
        // occurs, handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : peerManager.getChannels().values()) {
            if (nioChannel.getServerPID().equals(failedPID))
                continue;
            try {
                System.out.println("Sending failure message to replica with PID: " + nioChannel.getServerPID());
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf("Failed to send ServerFailureMessage<%s> on peer channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
    }

    /**
     * Broadcasts a server failure notification to all connected chat server
     * channels.
     * <p>
     * This method creates a {@code ServerFailureMessage} indicating that a server
     * process has failed.
     * If the failed server's role is {@code Roles.CHATSERVER}, the failure message
     * is created via
     * {@code ServerFailureMessage.chatServerFailed(Long, String, String, Long)};
     * otherwise, it is created via
     * {@code ServerFailureMessage.addrServerFailed(Long, String, String, Long)}.
     * </p>
     * <p>
     * The generated message is then serialized into JSON and sent to every channel
     * found in the provided maps.
     * If an error occurs during serialization, the error is logged and an empty
     * list is returned.
     * If an error occurs while sending to a channel, that channel is added to the
     * list of failures.
     * </p>
     *
     * @param senderPID        the process ID of the server sending the failure
     *                         notification
     * @param failedPID        the process ID of the failed server process
     * @param failedServerRole the role of the failed server process (e.g.,
     *                         {@link Roles#CHATSERVER} or an addressing server
     *                         role)
     */
    private void broadcastFailureToChatServers(BaseAddrServerMessage<?> message, Long senderPID, Long failedPID,
            String failedServerRole) {
        // Create the message with the proper {@code ObjectType} so the receiver knows
        // which kind of record/connection to remove.

        // Serialize the message and return if a failure occurs. This would only happen
        // because of a
        // logic error introduced by us (the programmers), so it shouldn't shut down the
        // program, but we need to log it and fix it.
        String jsonMessage;
        try {
            jsonMessage = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.printf(
                    "Failed to serialize ServerFailureMessage<%s> for broadcast. Context: senderPID=%d, senderRole=%s, failedPID=%d, failedServerRole=%s. Exception: %s%n",
                    message.getObjectType(), senderPID, Roles.PRIMARY, failedPID, failedServerRole, e.getMessage());
            return;
        }
        // Send the message to each chat server in the network. If a failure occurs,
        // handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : chatServerManager.getChannels().values()) {
            if (nioChannel.getServerPID().equals(failedPID))
                continue;
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf(
                        "Failed to send ServerFailureMessage<%s> on chat server channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
    }

    /**
     * Broadcasts a server failure notification to all connected peer channels and
     * chat server channels.
     * <p>
     * This method creates a {@code ServerFailureMessage} indicating that a server
     * process has failed.
     * If the failed server's role is {@code Roles.CHATSERVER}, the failure message
     * is created via
     * {@code ServerFailureMessage.chatServerFailed(Long, String, String, Long)};
     * otherwise, it is created via
     * {@code ServerFailureMessage.addrServerFailed(Long, String, String, Long)}.
     * </p>
     * <p>
     * The generated message is then serialized into JSON and sent to every channel
     * found in the provided maps.
     * If an error occurs during serialization, the error is logged and an empty
     * list is returned.
     * If an error occurs while sending to a channel, that channel is added to the
     * list of failures.
     * </p>
     *
     * @param senderPID        the process ID of the server sending the failure
     *                         notification
     * @param failedPID        the process ID of the failed server process
     * @param failedServerRole the role of the failed server process (e.g.,
     *                         {@link Roles#CHATSERVER} or an addressing server
     *                         role)
     */
    private void broadcastServerFailure(Long senderPID, Long failedPID, String failedServerRole) {
        // Create the message with the proper {@code ObjectType} so the receiver knows
        // which kind of record/connection to remove.
        ServerFailureMessage<Long> message;
        if (failedServerRole.equals(Roles.CHATSERVER)) {
            message = ServerFailureMessage.chatServerFailed(senderPID, Roles.PRIMARY, failedServerRole, failedPID);
        } else { // It's an addressing server (REPLICA or PRIMARY)
            message = ServerFailureMessage.addrServerFailed(senderPID, Roles.PRIMARY, failedServerRole, failedPID);
        }
        // Serialize the message and return if a failure occurs. This would only happen
        // because of a
        // logic error introduced by us (the programmers), so it shouldn't shut down the
        // program, but we need to log it and fix it.
        String jsonMessage;
        try {
            jsonMessage = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.printf(
                    "Failed to serialize ServerFailureMessage<%s> for broadcast. Context: senderPID=%d, senderRole=%s, failedPID=%d, failedServerRole=%s. Exception: %s%n",
                    message.getObjectType(), senderPID, Roles.PRIMARY, failedPID, failedServerRole, e.getMessage());
            return;
        }
        // Send the message to each addressing server in the network. If a failure
        // occurs, handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : peerManager.getChannels().values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf("Failed to send ServerFailureMessage<%s> on peer channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
        // Send the message to each chat server in the network. If a failure occurs,
        // handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : chatServerManager.getChannels().values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf(
                        "Failed to send ServerFailureMessage<%s> on chat server channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
    }

    /**
     * Creates a new {@link PendingEvent} for chat server deregistration.
     * <p>
     * This method is used to create a new event that will be triggered once all
     * acknowledgments (ACKs)
     * for synchronizing state have been received from the chat servers.
     * </p>
     *
     * @return a new {@code PendingEvent} instance.
     */
    public PendingEvent createChatServerDeregistrationEvent(ServerFailureMessage<Long> message,
            SocketChannel channel,
            Map<Long, NIOMessageChannel> recipients,
            Long primaryPID, Long failedPID, String failedRole) {
        return new PendingEvent(message, recipients, 3, () -> { // THESE ARE ALL THE ACTIONS THAT WILL OCCUR ONCE
                                                                // AddressingServer STATES ARE CONSISTENT.
            System.out.println(
                    "PRIMARY has received all ACK's from REPLICA's - server state synchronized, sending failure message to ChatServer's.");
            // All replicas have successfully replicated the update. Update state locally
            // and continue with response.
            this.broadcastFailureToChatServers(message, primaryPID, failedPID, failedRole);
            // Create event for messaging chat
            // TODO - Create event for messaging chat servers
            this.chatServerManager.removeProcessCloseConnection(channel);
            this.chatServerManager.debugPrintAllServers();
        });
    }

    /**
     * Creates a new {@link PendingEvent} for replica deregistration, i.e. when a
     * replica fails.
     * <p>
     * This method is used to create a new event that will be triggered once all
     * acknowledgments (ACKs)
     * for synchronizing state have been received from the replicas.
     * </p>
     *
     * @return a new {@code PendingEvent} instance.
     */
    public PendingEvent createReplicaDeregistrationEvent(ServerFailureMessage<Long> message,
            SocketChannel channel,
            Map<Long, NIOMessageChannel> recipients,
            Long primaryPID, Long failedPID, String failedRole) {
        return new PendingEvent(message, recipients, 3, () -> { // THESE ARE ALL THE ACTIONS THAT WILL OCCUR ONCE
                                                                // AddressingServer STATES ARE CONSISTENT.
            System.out.println(
                    "PRIMARY has received all ACK's from REPLICA's - server state synchronized, sending failure message to ChatServer's.");
            // All replicas have successfully replicated the update. Update state locally
            // and continue with response.
            this.broadcastFailureToChatServers(message, primaryPID, failedPID, failedRole);
            // Create event for messaging chat servers
            // TODO - Create event for messaging chat servers
            this.peerManager.removeProcessCloseConnection(channel);
            this.peerManager.debugPrintAllServers();
        });
    }

}

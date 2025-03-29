package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.messaging.ServerFailureMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ConnectionCleanupManager {

    /**
     * The process responsible for managing interactions between the Primary
     * {@code AddressingServer} and its replicas.
     */
    private final PeerManager peerManager;

    public PeerManager getPeerManager() {
        return peerManager;
    }

    /**
     * The process responsible for managing interactions between the
     * {@code AddressingServer} and {@code ChatServer}'s
     */
    private final ChatServerManager chatServerManager;

    public ChatServerManager getChatServerManager() {
        return chatServerManager;
    }

    public ConnectionCleanupManager(PeerManager peerManager,
                                    ChatServerManager chatServerManager) {
        this.peerManager = peerManager;
        this.chatServerManager = chatServerManager;
    }

    /**
     * Determines whether the specified {@link SocketChannel} is associated with a persistent server-to-server connection.
     * <p>
     * Persistent connections are long-lived channels used for internal communication between
     * {@code AddressingServer}s (peers) and {@code ChatServer}s. These are stored and tracked
     * using their respective manager classes.
     * </p>
     *
     * @param channel the {@code SocketChannel} to inspect.
     * @return {@code true} if the channel is known to be persistent (i.e., belongs to a peer or chat server), {@code false} otherwise.
     */
    public boolean isPersistentConnection(SocketChannel channel) {
        return peerManager.getChannels().containsKey(channel)
                || chatServerManager.getChannels().containsKey(channel);
    }

    /**
     * Retrieves the {@link NIOMessageChannel} wrapper for a known persistent connection.
     * <p>
     * This method searches the internal maps of both the {@code PeerManager} and {@code ChatServerManager}
     * to find the {@code NIOMessageChannel} corresponding to the provided {@link SocketChannel}.
     * </p>
     * <p>
     * If the channel is not found in either manager, the method returns {@code null}.
     * </p>
     *
     * @param channel the {@code SocketChannel} to look up.
     * @return the associated {@code NIOMessageChannel}, or {@code null} if not found.
     */
    public NIOMessageChannel getKnownPersistentChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerManager.getChannels().get(channel);
        if (ch != null) return ch;
        return chatServerManager.getChannels().get(channel); // Will return null if it doesn't exist (which is what we want)
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     *     <li>Logs the reason for cleanup (remote disconnect or local I/O failure).</li>
     *     <li>Removes the connection from either the {@code PeerManager} or {@code ChatServerManager}.</li>
     *     <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *
     * @param nioChannel the {@code NIOMessageChannel} associated with the error.
     * @param cce        {@code true} if the cleanup is due to a remote disconnect (i.e., {@link ConnectionClosedException}),
     *                   {@code false} if due to a local I/O failure.
     */
    public void cleanupPersistentConnectionNIO(NIOMessageChannel nioChannel, Boolean cce) {
        SocketChannel channel = nioChannel.getSocketChannel();
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                Long pid = ch.getServerPID();
                if (chatServerManager.getChannels().containsKey(channel)) {
                    chatServerManager.removeRemoteProcess(channel);
                    broadcastServerFailure(peerManager.getPrimaryPID(), pid, Roles.CHATSERVER);
                } else if (peerManager.getChannels().containsKey(channel)) {
                    broadcastServerFailure(peerManager.getPrimaryPID(), pid, Roles.REPLICA);
                    peerManager.removeRemoteProcess(channel);
                }
            }
        }
        //key.cancel();  Keys for closed SocketChannels are canceled during the next selector.select event in the main event loop. No need to do that here.
        try {
            channel.close();
        } catch (IOException ignored) {}  // if the channel is already closed, we don't need to do anything.
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     *     <li>Logs the reason for cleanup (remote disconnect or local I/O failure).</li>
     *     <li>Removes the connection from either the {@code PeerManager} or {@code ChatServerManager}.</li>
     *     <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *
     * @param channel the {@code SocketChannel} being cleaned up.
     * @param cce        {@code true} if the cleanup is due to a remote disconnect (i.e., {@link ConnectionClosedException}),
     *                   {@code false} if due to a local I/O failure.
     */
    public void cleanupPersistentConnection(SocketChannel channel, Boolean cce) {
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                Long pid = ch.getServerPID();
                if (chatServerManager.getChannels().containsKey(channel)) {
                    chatServerManager.removeRemoteProcess(channel);
                    broadcastServerFailure(peerManager.getPrimaryPID(), pid, Roles.CHATSERVER);
                } else if (peerManager.getChannels().containsKey(channel)) {
                    broadcastServerFailure(peerManager.getPrimaryPID(), pid, Roles.REPLICA);
                    peerManager.removeRemoteProcess(channel);
                }
            }
        }
        //key.cancel();  Keys for closed SocketChannels are canceled during the next selector.select event in the main event loop. No need to do that here.
        try {
            channel.close();
        } catch (IOException ignored) {}  // if the channel is already closed, we don't need to do anything.
    }


    /**
     * Broadcasts a server failure notification to all connected peer channels and chat server channels.
     * <p>
     * This method creates a {@code ServerFailureMessage} indicating that a server process has failed.
     * If the failed server's role is {@code Roles.CHATSERVER}, the failure message is created via
     * {@code ServerFailureMessage.chatServerFailed(Long, String, String, Long)}; otherwise, it is created via
     * {@code ServerFailureMessage.addrServerFailed(Long, String, String, Long)}.
     * </p>
     * <p>
     * The generated message is then serialized into JSON and sent to every channel found in the provided maps.
     * If an error occurs during serialization, the error is logged and an empty list is returned.
     * If an error occurs while sending to a channel, that channel is added to the list of failures.
     * </p>
     *
     * @param senderPID        the process ID of the server sending the failure notification
     * @param failedPID        the process ID of the failed server process
     * @param failedServerRole the role of the failed server process (e.g., {@link Roles#CHATSERVER} or an addressing server role)
     */
    private void broadcastServerFailure(Long senderPID, Long failedPID, String failedServerRole) {
        // Create the message with the proper {@code ObjectType} so the receiver knows which kind of record/connection to remove.
        ServerFailureMessage<Long> message;
        if (failedServerRole.equals(Roles.CHATSERVER)) {
            message = ServerFailureMessage.chatServerFailed(senderPID, Roles.PRIMARY, failedServerRole, failedPID);
        } else {    // It's an addressing server (REPLICA or PRIMARY)
            message = ServerFailureMessage.addrServerFailed(senderPID, Roles.PRIMARY, failedServerRole, failedPID);
        }
        // Serialize the message and return if a failure occurs. This would only happen because of a
        // logic error introduced by us (the programmers), so it shouldn't shut down the program, but we need to log it and fix it.
        String jsonMessage;
        try {
            jsonMessage = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.printf(
                    "Failed to serialize ServerFailureMessage<%s> for broadcast. Context: senderPID=%d, senderRole=%s, failedPID=%d, failedServerRole=%s. Exception: %s%n",
                    message.getObjectType(), senderPID, Roles.PRIMARY, failedPID, failedServerRole, e.getMessage()
            );
            return;
        }
        // Send the message to each addressing server in the network. If a failure occurs, handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : peerManager.getChannels().values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf("Failed to send ServerFailureMessage<%s> on peer channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
        // Send the message to each chat server in the network. If a failure occurs, handle removing the process appropriately.
        for (NIOMessageChannel nioChannel : chatServerManager.getChannels().values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.printf("Failed to send ServerFailureMessage<%s> on chat server channel (remote PID: %s): %s%n",
                        message.getObjectType(), nioChannel.getServerPID(), ioe.getMessage());
                cleanupPersistentConnectionNIO(nioChannel, true);
            }
        }
    }


}

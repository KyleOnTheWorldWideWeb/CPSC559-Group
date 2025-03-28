package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.messaging.Roles;
import io.github.cpsc559.team16.common.messaging.ServerFailureMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BroadcastManager {

    /**
     * Broadcasts a server failure notification to all connected peer channels and chat server channels.
     * <p>
     * This method creates a {@code ServerFailureMessage} indicating that a server process has failed.
     * If the failed server's role is {@code Roles.CHATSERVER}, the failure message is created via
     * {@link ServerFailureMessage#chatServerFailed(Long, String, String, Long)}; otherwise, it is created via
     * {@link ServerFailureMessage#addrServerFailed(Long, String, String, Long)}.
     * </p>
     * <p>
     * The generated message is then serialized into JSON and sent to every channel found in the provided maps.
     * If an error occurs during serialization, the error is logged and an empty list is returned.
     * If an error occurs while sending to a channel, that channel is added to the list of failures.
     * </p>
     *
     * @param senderPID         the process ID of the server sending the failure notification
     * @param senderRole        the role of the sender (e.g., {@link Roles#PRIMARY} or {@link Roles#REPLICA})
     * @param failedPID         the process ID of the failed server process
     * @param failedServerRole  the role of the failed server process (e.g., {@link Roles#CHATSERVER} or an addressing server role)
     * @param peerChannels      a map of persistent peer channels (from SocketChannel to NIOMessageChannel)
     * @param chatServerChannels a map of persistent chat server channels (from SocketChannel to NIOMessageChannel)
     * @return a list of {@link NIOMessageChannel} instances for which sending the message failed; an empty list if all sends succeed
     */
    public static List<NIOMessageChannel> serverFailure(Long senderPID, String senderRole, Long failedPID, String failedServerRole,
                                                                 Map<SocketChannel, NIOMessageChannel> peerChannels,
                                                                 Map<SocketChannel, NIOMessageChannel> chatServerChannels) {
        List<NIOMessageChannel> failures = new ArrayList<>();
        ServerFailureMessage<Long> message;
        if (failedServerRole.equals(Roles.CHATSERVER)) {
            message = ServerFailureMessage.chatServerFailed(senderPID, senderRole, failedServerRole, failedPID);
        } else {    // It's an addressing server (REPLICA or PRIMARY)
            message = ServerFailureMessage.addrServerFailed(senderPID, senderRole, failedServerRole, failedPID);
        }
        String jsonMessage;
        try {
            jsonMessage = message.toJson();
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize ServerFailureMessage<" + message.getObjectType() + ">: " + e.getMessage());
            return failures;
        }
        for (NIOMessageChannel nioChannel : peerChannels.values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.println("Failed to send ServerFailureMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                failures.add(nioChannel);
            }
        }
        for (NIOMessageChannel nioChannel : chatServerChannels.values()) {
            try {
                nioChannel.sendMessage(jsonMessage);
            } catch (IOException ioe) {
                System.err.println("Failed to send ServerFailureMessage<" + message.getObjectType() + ">: " + ioe.getMessage());
                failures.add(nioChannel);
            }
        }
        return failures;
    }



}

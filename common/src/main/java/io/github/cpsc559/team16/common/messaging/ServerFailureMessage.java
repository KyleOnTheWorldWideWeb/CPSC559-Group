package io.github.cpsc559.team16.common.messaging;

public class ServerFailureMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs a new failed server message.
     *
     * @param objectType Describes what action is being acknowledged (e.g., "Registration", "Update").
     * @param senderPID The ID of the process sending the acknowledgment.
     * @param senderRole The role of the sender (e.g., PRIMARY, REPLICA, CHATSERVER).
     * @param targetRole The role of the process being acknowledged (e.g., CHATSERVER, REPLICA).
     * @param payload A short payload string. It can be anything you want, but you'll have to handle it appropriately.
     */
    public ServerFailureMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super(MessageTypes.SERVERFAILURE, objectType, senderPID, senderRole, targetRole, payload);
    }

    /**
     * Creates a notification message indicating that a ChatServer process has failed.
     * This notification is intended for any server in the network to inform others that a ChatServer
     * is no longer active. Recipients should terminate any connections (channels) with the failed process
     * and remove its record from their local registry.
     *
     * <p>
     * This factory method generates a {@code NotificationMessage} with the object type set to
     * {@link ObjectTypes#CHATSERVER_FAILURE}. The sender and receiver roles should be specified using the
     * allowed values defined in {@link Roles} (for example, {@code Roles.PRIMARY}, {@code Roles.REPLICA},
     * or {@code Roles.CHATSERVER}).
     * </p>
     *
     * @param senderPID    the process ID of the server sending this notification
     * @param senderRole   the role of the sender (for example, {@code Roles.PRIMARY})
     * @param receiverRole the role of the intended recipient (for example, {@code Roles.REPLICA})
     * @param failedPID    the process ID of the failed ChatServer process
     * @return a {@code ServerFailureMessage<Long>} encapsulating the failure event for a ChatServer
     */
    public static ServerFailureMessage<Long> chatServerFailed(long senderPID, String senderRole, String receiverRole, Long failedPID) {
        return new ServerFailureMessage<>(ObjectTypes.CHATSERVER_FAILURE, senderPID, senderRole, receiverRole, failedPID);
    }


    /**
     * Creates a notification message indicating that an AddressingServer process has failed.
     * This notification is intended for any server in the network to inform others that an AddressingServer
     * is no longer active. Recipients should terminate any connections (channels) with the failed process
     * and remove its record from their local registry.
     *
     * <p>
     * This factory method generates a {@code NotificationMessage} with the object type set to
     * {@link ObjectTypes#ADDRSERVER_FAILURE}. The sender and receiver roles should be specified using the
     * allowed values defined in {@link Roles} (for example, {@code Roles.PRIMARY}, {@code Roles.REPLICA},
     * or {@code Roles.CHATSERVER}).
     * </p>
     *
     * @param senderPID    the process ID of the server sending this notification
     * @param senderRole   the role of the sender (for example, {@code Roles.PRIMARY})
     * @param receiverRole the role of the intended recipient (for example, {@code Roles.REPLICA})
     * @param failedPID    the process ID of the failed AddressingServer process
     * @return a {@code ServerFailureMessage<Long>} encapsulating the failure event for an AddressingServer
     */
    public static ServerFailureMessage<Long> addrServerFailed(long senderPID, String senderRole, String receiverRole, Long failedPID) {
        return new ServerFailureMessage<>(ObjectTypes.ADDRSERVER_FAILURE,senderPID, senderRole, receiverRole, failedPID);
    }
}

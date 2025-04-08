package io.github.cpsc559.team16.common.messaging;


/**
 * A specialized message used to send notifications about events or updates within the distributed system.
 * <p>
 * This class extends {@link BaseAddrServerMessage} and provides a specific implementation for
 * messages of type {@code MessageTypes.NOTIFICATION}. It is used to notify other processes about
 * events such as client count changes, server failures, etc.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * {
 *   "msgType": "NOTIFICATION",
 *   "objectType": "CLIENT_COUNT",
 *   "senderPID": 101,
 *   "senderRole": "CHATSERVER",
 *   "targetRole": "PRIMARY",
 *   "payload": 45
 * }
 * </pre>
 *
 * @param <T> The type of data being sent in the payload (e.g., Integer for client count).
 */
public class NotificationMessage<T> extends BaseAddrServerMessage<T> {


    /**
     * Private constructor to prevent direct instantiation and enforce the use of factory methods.
     *
     * @param objectType The type of data being sent in the payload.
     * @param senderPID The process ID of the sender.
     * @param senderRole The role of the sender (e.g., CHATSERVER).
     * @param targetRole The intended recipient's role (e.g., PRIMARY).
     * @param payload The data being sent in the payload.
     */
    private NotificationMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super(0, MessageTypes.NOTIFICATION, objectType, senderPID, senderRole, targetRole, payload);
    }

    /**
     * Private constructor to prevent direct instantiation and enforce the use of factory methods.
     *
     * @param messageID   A globally unique identifier for the message. Use {@link MessageIDGenerator} for generation.
     * @param objectType The type of data being sent in the payload.
     * @param senderPID The process ID of the sender.
     * @param senderRole The role of the sender (e.g., CHATSERVER).
     * @param targetRole The intended recipient's role (e.g., PRIMARY).
     * @param payload The data being sent in the payload.
     */
    private NotificationMessage(long messageID, String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super(messageID, MessageTypes.NOTIFICATION, objectType, senderPID, senderRole, targetRole, payload);
    }


    /**
     * Factory method for creating a notification message for client count updates.
     * <p>
     * This method creates a notification message with a payload of the current client count.
     * It is typically used when a {@code CHATSERVER} notifies a {@code PRIMARY} server about
     * the current number of active clients.
     * </p>
     *
     * @param senderPID The process ID of the sender (usually a {@code CHATSERVER}).
     * @param currentClientCount The current count of clients connected to the server.
     * @return A {@code NotificationMessage} containing the client count.
     */
    public NotificationMessage<Integer> clientCountNotification(long senderPID, Integer currentClientCount) {
        return new NotificationMessage<>(ObjectTypes.CLIENT_COUNT, senderPID, Roles.CHATSERVER, Roles.PRIMARY, currentClientCount);
    }


}

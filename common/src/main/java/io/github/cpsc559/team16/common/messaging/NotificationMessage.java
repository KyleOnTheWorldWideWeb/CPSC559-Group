package io.github.cpsc559.team16.common.messaging;

/**
 * A standardized acknowledgment message used for confirming successful communication,
 * such as registration or message receipt.
 * <p>
 * The payload of an {@code AckMessage} is typically a string (e.g., "OK", "Registered", or an error reason).
 * </p>
 *
 * <pre>
 * Example:
 * {
 *   "msgType": "ACK",
 *   "objectType": "Registration",
 *   "senderPID": 101,
 *   "senderRole": "PRIMARY",
 *   "targetRole": "REPLICA",
 *   "payload": "Registered"
 * }
 * </pre>
 */
public class NotificationMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs a new acknowledgment message.
     *
     * @param objectType Describes what action is being acknowledged (e.g., "Registration", "Update").
     * @param senderPID The ID of the process sending the acknowledgment.
     * @param senderRole The role of the sender (e.g., PRIMARY, REPLICA, CHATSERVER).
     * @param targetRole The role of the process being acknowledged (e.g., CHATSERVER, REPLICA).
     * @param payload A short payload string. It can be anything you want, but you'll have to handle it appropriately.
     */
    public NotificationMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super(MessageTypes.NOTIFICATION, objectType, senderPID, senderRole, targetRole, payload);
    }




}
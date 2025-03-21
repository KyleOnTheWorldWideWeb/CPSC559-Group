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
public class AckMessage extends BaseAddrServerMessage<String> {

    /**
     * Constructs a new acknowledgment message.
     *
     * @param objectType Describes what action is being acknowledged (e.g., "Registration", "Update").
     * @param senderPID The ID of the process sending the acknowledgment.
     * @param senderRole The role of the sender (e.g., PRIMARY, REPLICA, CHATSERVER).
     * @param targetRole The role of the process being acknowledged (e.g., CHATSERVER, REPLICA).
     * @param message A short payload string, such as "OK", "Registered", or a custom reason.
     */
    public AckMessage(String objectType, long senderPID, String senderRole, String targetRole, String message) {
        super("ACK", objectType, senderPID, senderRole, targetRole, message);
    }

    /**
     * Creates a simple "OK" acknowledgment.
     *
     * @param objectType The context of the acknowledgment (e.g., "Registration", "Update").
     * @param senderPID The process ID of the sender.
     * @param senderRole The role of the sender.
     * @param targetRole The role of the intended recipient.
     * @return A basic {@code AckMessage} with payload "OK".
     */
    public static AckMessage ok(String objectType, long senderPID, String senderRole, String targetRole) {
        return new AckMessage(objectType, senderPID, senderRole, targetRole, "OK");
    }
}

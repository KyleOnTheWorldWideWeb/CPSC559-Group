package io.github.cpsc559.team16.common.messaging;

/**
 * A standardized acknowledgment message used for confirming successful
 * communication,
 * such as registration or message receipt.
 * <p>
 * The payload of an {@code AckMessage} is typically a string (e.g., "OK",
 * "Registered", or an error reason).
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
     * need to have a default constructor for deserialization to work
     */
    public AckMessage() {
        super(); // Make sure BaseAddrServerMessage also has a no-arg constructor
    }

    /**
     * Constructs a new acknowledgment message.
     *
     * @param objectType Describes what action is being acknowledged (e.g.,
     *                   "Registration", "Update").
     * @param senderPID  The ID of the process sending the acknowledgment.
     * @param senderRole The role of the sender (e.g., PRIMARY, REPLICA,
     *                   CHATSERVER).
     * @param targetRole The role of the process being acknowledged (e.g.,
     *                   CHATSERVER, REPLICA).
     * @param message    A short payload string. It can be anything you want, but
     *                   you'll have to handle it appropriately.
     */
    public AckMessage(String objectType, long senderPID, String senderRole, String targetRole, String message) {
        super(MessageTypes.ACK, objectType, senderPID, senderRole, targetRole, message);
    }

    /**
     * Creates a simple "OK" acknowledgment.
     *
     * @param objectType The context of the acknowledgment (e.g., "Registration",
     *                   "Update").
     * @param senderPID  The process ID of the sender.
     * @param senderRole The role of the sender.
     * @param targetRole The role of the intended recipient.
     * @return A basic {@code AckMessage} with payload "OK".
     */
    public static AckMessage ok(String objectType, long senderPID, String senderRole, String targetRole) {
        return new AckMessage(objectType, senderPID, senderRole, targetRole, "OK");
    }

    /**
     * Creates an ACK message from the PRIMARY server directed to a CLIENT.
     * <p>
     * This factory method generates an {@code AckMessage} where the sender is
     * identified as PRIMARY and the recipient as CLIENT.
     * The provided {@code ackType} is used as the message's object type (e.g.,
     * indicating the nature of the acknowledgment),
     * and the {@code chatServerHostAddress} serves as the payload, typically
     * representing the host address (and optionally port)
     * of the chat server.
     * </p>
     *
     * @param ackType               the acknowledgment type used as the object type
     *                              of the message (e.g., "HostAddress" or "NoHost")
     * @param senderPID             the process ID of the PRIMARY server sending
     *                              this message
     * @param chatServerHostAddress the host address (and optionally port) of the
     *                              chat server to be communicated to the client
     * @return an {@code AckMessage} constructed with the specified parameters,
     *         ready to be sent from the PRIMARY to the CLIENT
     */
    public static AckMessage primaryToClient(String ackType, long senderPID, String chatServerHostAddress) {
        return new AckMessage(ackType, senderPID, Roles.PRIMARY, Roles.CLIENT, chatServerHostAddress);
    }
}

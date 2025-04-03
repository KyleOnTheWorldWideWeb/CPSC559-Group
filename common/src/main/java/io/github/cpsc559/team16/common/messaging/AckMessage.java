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
public class AckMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs a new acknowledgment message.
     *
     * @param objectType Describes what action is being acknowledged (e.g., "Registration", "Update").
     * @param senderPID The ID of the process sending the acknowledgment.
     * @param senderRole The role of the sender (e.g., PRIMARY, REPLICA, CHATSERVER).
     * @param targetRole The role of the process being acknowledged (e.g., CHATSERVER, REPLICA).
     * @param payload A short payload string. It can be anything you want, but you'll have to handle it appropriately.
     */
    public AckMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super(MessageTypes.ACK, objectType, senderPID, senderRole, targetRole, payload, );
    }

    /**
     * Creates a simple "OK" acknowledgment.
     *
     * @param senderPID The process ID of the sender.
     * @param senderRole The role of the sender.
     * @param targetRole The role of the intended recipient.
     * @return A basic {@code AckMessage} with payload "OK".
     */
    public static AckMessage<String> ok(long senderPID, String senderRole, String targetRole) {
        return new AckMessage<>(AckObjectTypes.OK, senderPID, senderRole, targetRole, "OK");
    }

    /**
     * Creates an ACK message from the PRIMARY server directed to a CLIENT.
     * <p>
     * This factory method generates an {@code AckMessage} where the sender is identified as PRIMARY and the recipient as CLIENT.
     * The acknowledgment type is hardcoded as {@code AckObjectTypes.HOSTADDRESS}, and the payload represents the assigned host.
     * </p>
     *
     * @param senderPID             the process ID of the PRIMARY server sending this message
     * @param chatServerHostAddress the host address (and optionally port) of the chat server to be communicated to the client
     * @return an {@code AckMessage} constructed with the specified parameters, ready to be sent from the PRIMARY to the CLIENT
     */
    public static AckMessage<String> chatHostAddress(long senderPID, String chatServerHostAddress) {
        return new AckMessage<>(AckObjectTypes.HOSTADDRESS, senderPID, Roles.PRIMARY, Roles.CLIENT, chatServerHostAddress);
    }

    /**
     * Creates an ACK message from the PRIMARY server directed to a CLIENT.
     * <p>
     * This factory method generates an {@code AckMessage} where the sender is identified as PRIMARY and the recipient as CLIENT.
     * The acknowledgment type is hardcoded as {@code AckObjectTypes.NOHOST}, and the payload is a "womp womp" String.
     * </p>
     *
     * @param senderPID             the process ID of the PRIMARY server sending this message
     * @return an {@code AckMessage} constructed with the specified parameters, ready to be sent from the PRIMARY to the CLIENT
     */
    public static AckMessage<String> noChatHost(long senderPID) {
        return new AckMessage<>(AckObjectTypes.NOHOST, senderPID, Roles.PRIMARY, Roles.CLIENT, "404 ChatServer Not Found — they ghosted you.");
    }



    /**
     * Creates an ACK message from the PRIMARY server directed to a REPLICA.
     * <p>
     * This factory method generates an {@code AckMessage} where the sender is identified as PRIMARY and the recipient as REPLICA.
     * The acknowledgment type is hardcoded as {@code AckObjectTypes.REGISTERED}, and the payload contains the assigned PID.
     * </p>
     *
     * @param senderPID the process ID of the PRIMARY server sending this message
     * @param payload   the assigned PID to be sent as confirmation
     * @return an {@code AckMessage} constructed with the specified parameters, ready to be sent from the PRIMARY to the REPLICA
     */
    public static AckMessage<Long> replicaRegistered(long senderPID, Long payload) {
        return new AckMessage<>(AckObjectTypes.REGISTERED, senderPID, Roles.PRIMARY, Roles.REPLICA, payload);
    }

    /**
     * Creates an ACK message from the PRIMARY server directed to a REPLICA.
     * <p>
     * This factory method generates an {@code AckMessage} where the sender is identified as PRIMARY and the recipient as REPLICA.
     * The acknowledgment type is hardcoded as {@code AckObjectTypes.REGISTERED}, and the payload contains the assigned PID.
     * </p>
     *
     * @param senderPID the process ID of the PRIMARY server sending this message
     * @param payload   the assigned PID to be sent as confirmation
     * @return an {@code AckMessage} constructed with the specified parameters, ready to be sent from the PRIMARY to the REPLICA
     */
    public static AckMessage<Long> chatServerRegistered(long senderPID, Long payload) {
        return new AckMessage<>(AckObjectTypes.REGISTERED, senderPID, Roles.PRIMARY, Roles.CHATSERVER, payload);
    }

}

package io.github.cpsc559.team16.common.messaging;

/**
 * Represents an "UPDATE" type message used for sending updates to Addressing Servers,
 * Replicas, and Chat Servers.
 * <p>
 * This class simplifies the creation of update messages by pre-setting {@code msgType} to "UPDATE."
 * </p>
 *
 * @param <T> The type of data being sent as the payload (e.g., {@code ChatServerRecord}, {@code AddrServerRecord}).
 */
public class UpdateMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs an "UPDATE" message.
     * <p>
     * An example of a {@code ChatServer} sending a notification to a REPLICA that a failure of the primary
     * {@code AddressingServer} has occured is given below:
     * </p>
     * <pre>
     *   {
     *   "msgType": "UPDATE",
     *   "objectType": "ElectionMessage",
     *   "senderPID": 72,
     *   "senderRole": "CHATSERVER",
     *   "targetRole": "REPLICA",
     *   "payload": { `serialized ElectionMessage object` }
     *   }
     * </pre>
     *
     * @param objectType The type of data contained in the payload.
     * @param senderPID The process ID of the sender.
     * @param senderRole The role of the sender (PRIMARY, REPLICA, CHATSERVER).
     * @param targetRole The intended recipient's role.
     * @param payload The actual data being sent.
     *
     */
    public UpdateMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super("UPDATE", objectType, senderPID, senderRole, targetRole, payload);
    }


}

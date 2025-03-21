package io.github.cpsc559.team16.common.messaging;

/**
 * Represents an "REQUEST" type message used for sending REQUESTs to Addressing Servers,
 * Replicas, and Chat Servers.
 * <p>
 * This class simplifies the creation of REQUEST messages by pre-setting {@code msgType} to "REQUEST."
 * </p>
 *
 * @param <T> The type of data being sent as the payload (e.g., {@code ChatServerRecord}, {@code AddrServerRecord}).
 */
public class RequestMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs an "REQUEST" message.
     * <p>
     * An example of a {@code ChatServer} a primary {@code AddressingServer} a request for all the ChatServerRecord
     * records for the distributed system is given below:
     * </p>
     * <pre>
     *   {
     *   "msgType": "REQUEST",
     *   "objectType": "AllChatServerInfo",
     *   "senderPID": 72,
     *   "senderRole": "CHATSERVER",
     *   "targetRole": "PRIMARY",
     *   "payload": { null }
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
    public RequestMessage(String objectType, long senderPID, String senderRole, String targetRole, T payload) {
        super("REQUEST", objectType, senderPID, senderRole, targetRole, payload);
    }


}

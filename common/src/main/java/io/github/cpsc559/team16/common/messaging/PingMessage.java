package io.github.cpsc559.team16.common.messaging;

/**
 * A standardized ping message used to broadcase the availability of the primary {@link AddressingServer}.
 *
 * <p>
 * The ping is sent by the primary to all replicas, indicating that it is still alive.
 * </p>
 * 
 * <pre>
 * Example:
 * {
 *   "msgType": "PING",
 *   "objectType": "NONE",
 *   "senderPID": 11,
 *   "senderRole": "",
 *   "targetRole": "",
 *   "payload": ""
 * }
 * </pre>
 */
public class PingMessage extends BaseAddrServerMessage<String> {

    /**
     * Constructs a ping message, used by the primary to indicate that it is still alive.
     *
     * @param senderPID The ID of the process sending the ping.
     */
    public PingMessage(long senderPID) {
        super(MessageTypes.ELECTION, ObjectTypes.NONE, senderPID, "", "", "");
    }
}

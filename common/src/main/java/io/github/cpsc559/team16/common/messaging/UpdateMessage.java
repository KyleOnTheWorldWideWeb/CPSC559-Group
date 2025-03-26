package io.github.cpsc559.team16.common.messaging;

import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;

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
     *   "objectType": "Failure",
     *   "senderPID": 72,
     *   "senderRole": "CHATSERVER",
     *   "targetRole": "REPLICA",
     *   "payload": { `Long failedServerPID` }
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
    public UpdateMessage(String objectType, Long senderPID, String senderRole, String targetRole, T payload) {
        super(MessageTypes.UPDATE, objectType, senderPID, senderRole, targetRole, payload);
    }

    /**
     * Creates an {@code UpdateMessage} to send an {@link AddrServerRecord}
     * from the PRIMARY AddressingServer to a REPLICA.
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "AddrServerRecord"</li>
     *     <li><strong>senderRole:</strong> "PRIMARY"</li>
     *     <li><strong>targetRole:</strong> "REPLICA"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the PRIMARY server sending the update.
     * @param record    The {@link AddrServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<AddrServerRecord> asRecordPrimaryToReplica(Long senderPID, AddrServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.ADDR_SERVER_RECORD, senderPID, Roles.PRIMARY, Roles.REPLICA, record);
    }

    /**
     * Creates an {@code UpdateMessage} to send an {@link AddrServerRecord}
     * from the PRIMARY AddressingServer to a {@code ChatServer}
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "AddrServerRecord"</li>
     *     <li><strong>senderRole:</strong> "PRIMARY"</li>
     *     <li><strong>targetRole:</strong> "REPLICA"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the PRIMARY server sending the update.
     * @param record    The {@link AddrServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<AddrServerRecord> asRecordPrimaryToCS(Long senderPID, AddrServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.ADDR_SERVER_RECORD, senderPID, Roles.PRIMARY, Roles.CHATSERVER, record);
    }

    /**
     * <p>
     * For use by {@code AddressingServer} REPLICA's during failover of the Primary AddressingServer.
     * </p>
     *
     * Creates an {@code UpdateMessage} to send an {@link AddrServerRecord}
     * from the PRIMARY AddressingServer to a REPLICA.
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "AddrServerRecord"</li>
     *     <li><strong>senderRole:</strong> "PRIMARY"</li>
     *     <li><strong>targetRole:</strong> "REPLICA"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the PRIMARY server sending the update.
     * @param record    The {@link AddrServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<AddrServerRecord> asRecordReplicaToReplica(Long senderPID, AddrServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.ADDR_SERVER_RECORD, senderPID, Roles.REPLICA, Roles.REPLICA, record);
    }


    /**
     * Creates an {@code UpdateMessage} to send a {@link ChatServerRecord}
     * from the PRIMARY AddressingServer to a ChatServer in the Network.
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "ChatServerRecord"</li>
     *     <li><strong>senderRole:</strong> "CHATSERVER"</li>
     *     <li><strong>targetRole:</strong> "PRIMARY"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the ChatServer sending the update.
     * @param record    The {@link ChatServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<ChatServerRecord> csRecordPrimaryToCS(Long senderPID, ChatServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.CHAT_SERVER_RECORD, senderPID, Roles.CHATSERVER, Roles.PRIMARY, record);
    }

    /**
     * Creates an {@code UpdateMessage} to send a {@link ChatServerRecord}
     * from the PRIMARY AddressingServer to a ChatServer in the Network.
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "ChatServerRecord"</li>
     *     <li><strong>senderRole:</strong> "CHATSERVER"</li>
     *     <li><strong>targetRole:</strong> "PRIMARY"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the ChatServer sending the update.
     * @param record    The {@link ChatServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<ChatServerRecord> csRecordPrimaryToReplica(Long senderPID, ChatServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.CHAT_SERVER_RECORD, senderPID, Roles.CHATSERVER, Roles.PRIMARY, record);
    }

    /**
     * Creates an {@code UpdateMessage} to send a {@link ChatServerRecord}
     * from a ChatServer to the PRIMARY AddressingServer.
     *
     * <p>This message uses:
     * <ul>
     *     <li><strong>objectType:</strong> "ChatServerRecord"</li>
     *     <li><strong>senderRole:</strong> "CHATSERVER"</li>
     *     <li><strong>targetRole:</strong> "PRIMARY"</li>
     * </ul>
     * </p>
     *
     * @param senderPID The process ID of the ChatServer sending the update.
     * @param record    The {@link ChatServerRecord} payload to include.
     * @return An {@code UpdateMessage} with the provided record and fixed metadata.
     */
    public static UpdateMessage<ChatServerRecord> csRecordChatServerToPrimary(Long senderPID, ChatServerRecord record) {
        return new UpdateMessage<>(ObjectTypes.CHAT_SERVER_RECORD, senderPID, Roles.PRIMARY, Roles.CHATSERVER, record);
    }


}

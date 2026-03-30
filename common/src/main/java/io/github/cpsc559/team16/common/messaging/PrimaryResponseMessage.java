package io.github.cpsc559.team16.common.messaging;

import java.util.Set;

/**
 * Represents a response sent by the Primary Addressing Server to other nodes in the network.
 * <p>
 * This class encapsulates data requested by Chat Servers or Replicas, such as
 * process ID sets used for registry synchronization and stale record purging.
 * </p>
 *
 * @param <T> The type of the payload being returned (e.g., {@code Set<Long>}).
 */
public class PrimaryResponseMessage<T> extends BaseAddrServerMessage<T> {

    /**
     * Constructs a new PrimaryResponseMessage with a generated message ID.
     *
     * @param objectType   The type of data contained in the payload (from {@link ObjectTypes}).
     * @param senderPID    The PID of the Primary Addressing Server.
     * @param receiverRole The role of the recipient (e.g., Roles.REPLICA, Roles.CHATSERVER).
     * @param payload      The data being transmitted.
     */
    public PrimaryResponseMessage(String objectType, Long senderPID, String receiverRole, T payload) {
        super(0, MessageTypes.PRIMARY_RESPONSE, objectType, senderPID, Roles.PRIMARY, receiverRole, payload);
    }

    /**
     * Constructs a new PrimaryResponseMessage with a specific message ID.
     *
     * @param messageID    The unique identifier for this message.
     * @param objectType   The type of data contained in the payload.
     * @param senderPID    The PID of the Primary Addressing Server.
     * @param receiverRole The role of the recipient.
     * @param payload      The data being transmitted.
     */
    public PrimaryResponseMessage(long messageID, String objectType, Long senderPID, String receiverRole, T payload) {
        super(messageID, MessageTypes.PRIMARY_RESPONSE, objectType, senderPID, Roles.PRIMARY, receiverRole, payload);
    }

    /**
     * Creates a response containing the current set of active Addressing Server PIDs.
     * * @param messageID    The unique identifier for this message.
     * @param senderPID     The PID of the Primary Addressing Server.
     * @param receiverRole  The role of the recipient (REPLICA or CHATSERVER).
     * @param pids          The set of active Peer PIDs.
     * @return A PrimaryResponseMessage configured with the PID_SET object type.
     */
    public static PrimaryResponseMessage<Set<Long>> addrServerPidList(long messageID, Long senderPID, String receiverRole, Set<Long> pids) {
        return new PrimaryResponseMessage<>(messageID, ResponseObjectTypes.ALL_PEER_PIDS, senderPID, receiverRole, pids);
    }

    /**
     * Creates a response containing the current set of active Chat Server PIDs.
     * * @param messageID    The unique identifier for this message.
     * @param senderPID     The PID of the Primary Addressing Server.
     * @param receiverRole  The role of the recipient (REPLICA or CHATSERVER).
     * @param pids          The set of active Chat Server PIDs.
     * @return A PrimaryResponseMessage configured with the PID_SET object type.
     */
    public static PrimaryResponseMessage<Set<Long>> chatServerPidList(long messageID, Long senderPID, String receiverRole, Set<Long> pids) {
        return new PrimaryResponseMessage<>(messageID, ResponseObjectTypes.ALL_CS_PIDS, senderPID, receiverRole, pids);
    }
}
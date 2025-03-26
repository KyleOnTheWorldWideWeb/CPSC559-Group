package io.github.cpsc559.team16.common.messaging;

/**
 * Constants representing standardized object types used in message payloads.
 * These should match the values used in {@code BaseAddrServerMessage.objectType}
 * and must align with switch statements in deserialization and dispatcher logic.
 *
 * <p>
 *     <strong>NOTE:</strong> There are many times where the BaseAddrServerMessage class
 *     is used to "safe-cast" the payload into the object referenced in the ObjectType field.
 *     It is of paramount importance this feature is used with care.
 * </p>
 *
 */
public class ObjectTypes {
    /**
     * The object type for an AddressingServer record.
     */
    public static final String ADDR_SERVER_RECORD = "AddrServerRecord";

    /**
     * The object type for a ChatServer record.
     */
    public static final String CHAT_SERVER_RECORD = "ChatServerRecord";

    /**
     * The object type for representing client count values.
     */
    public static final String CLIENT_COUNT = "ClientCount";

    /**
     * This is used for inter-process communication for failure related messaging. It is used in combination
     * with {@link MessageTypes#UPDATE} to notify all servers (excluding the PRIMARY AddressingServer)
     * of a failed process - so that they can close any connections they have to that process, and remove the record
     * for that process from their registry.
     * <p>
     * <strong>NOTE:</strong> This is not the correct way of notifying the {@code PRIMARY AddressingServer}
     * of a failed server. That responsibility is handled by {@link MessageTypes#SERVERFAILURE}.
     * </p>
     */
    public static final String SERVER_FAILURE = "ServerFailure";

    /**
     * The object type for representing a vote in a leader election process.
     */
    public static final String ELECTION_VOTE = "ElectionVote";

    /**
     * The object type for representing a Long value in message payloads.
     */
    public static final String LONG = "Long";

    /**
     * The object type for representing a String value in message payloads.
     */
    public static final String STRING = "String";

    /**
     * The object type used when no specific object type is applicable.
     */
    public static final String NONE = "NONE";
}

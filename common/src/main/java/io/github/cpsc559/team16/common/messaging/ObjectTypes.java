package io.github.cpsc559.team16.common.messaging;

/**
 * Constants representing standardized object types used in message payloads.
 * These should match the values used in {@code BaseAddrServerMessage.objectType}
 * and must align with switch statements in deserialization and dispatcher logic.
 */
public class ObjectTypes {
    public static final String ADDR_SERVER_RECORD = "AddrServerRecord";
    public static final String CHAT_SERVER_RECORD = "ChatServerRecord";
    public static final String CLIENT_LOGIN_ATTEMPT = "ClientLoginAttempt";
    public static final String CLIENT_CONNECT_TOKEN = "ClientConnectToken";
    public static final String CLIENT_COUNT = "ClientCount";
    public static final String SERVER_FAILURE = "ServerFailure";
    public static final String ELECTION_VOTE = "ElectionVote";
    public static final String LONG = "Long";
    public static final String STRING = "String";
    public static final String NONE = "NONE";
}

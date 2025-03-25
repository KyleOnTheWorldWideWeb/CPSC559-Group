package io.github.cpsc559.team16.common.messaging;

/**
 * Constants representing standardized message types used in the distributed system.
 * <p>
 * These constants map to {@code BaseAddrServerMessage.msgType} and determine how a message
 * should be processed — whether it's a registration request, update, ping, etc.
 * </p>
 * <p>
 * Keeping these centralized ensures consistency across all components (e.g. dispatchers, message factories).
 * </p>
 */
public class MessageTypes {
    public static final String REGISTER = "REGISTER";
    public static final String UPDATE = "UPDATE";
    public static final String REQUEST = "REQUEST";
    public static final String PING = "PING";
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String ELECTION = "ELECTION";
    public static final String ACK = "ACK";
    public static final String CLIENTCOUNT = "RECORDUPDATE";
}

package io.github.cpsc559.team16.common.messaging;

/**
 * Constants representing sender and target roles used in structured network messages.
 * <p>
 * These values correspond to {@code senderRole} and {@code targetRole} fields
 * in {@link BaseAddrServerMessage} and determine the origin and intended recipient
 * of each message in the distributed system.
 * </p>
 */
public class Roles {
    public static final String PRIMARY = "PRIMARY";
    public static final String REPLICA = "REPLICA";
    public static final String CHATSERVER = "CHATSERVER";
    public static final String CLIENT = "CLIENT";
}

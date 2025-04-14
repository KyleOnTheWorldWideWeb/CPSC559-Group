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
    /**
     * Message type for registration requests. Used when a process is trying to register with the network.
     */
    public static final String REGISTER = "REGISTER";

    /**
     * Message type for update messages. Used when a process is sending an updated state or record.
     */
    public static final String UPDATE = "UPDATE";

    /**
     * Message type for request messages.
     * <p>
     * TODO - (Aidan) I put this in for the Client and the ChatServer. I don't actually use it,
     *  so you're free to set up the class and format as you see fit, and add the AddrServer
     *  handling of the message in AddrServerReadDispatcher.
     * </p>
     */
    public static final String REQUEST = "REQUEST";

    /**
     * Message type for ping messages, typically used to verify connectivity or for heartbeat purposes.
     */
    public static final String PING = "PING";

    /**
     * Message type for instructing a remote process that has been flagged as failed
     * to reinitialize (exit and restart the main event loop).
     * <p>Failed processes are disconnected from the network automatically, but this provides another layer of protection,
     * and provides the opportunity for re-integration in the network.</p>
     */
    public static final String REINITIALIZE = "REINITIALIZE";

    /**
     * Used to notify the receiver about a failed process in the distributed network.
     * <p>
     * The object type must be {@code ObjectTypes.LONG} and the payload must be the PID of the
     * failed process.
     * </p>
     */
    public static final String SERVERFAILURE = "SERVERFAILURE";

    /**
     * Message type for notification messages. These messages are used to signal various events or state changes
     * in the network that are not covered by other message types.
     */
    public static final String NOTIFICATION = "NOTIFICATION";

    /**
     * Message type used during leader election processes among the distributed servers.
     */
    public static final String ELECTION = "ELECTION";

    /**
     * Message type for acknowledgement messages. ACK messages are typically used to confirm receipt or successful processing
     * of a prior message (e.g., registration or update).
     */
    public static final String ACK = "ACK";

    // Preventing any possible instantiation of the utility class
    private MessageTypes() {}
}

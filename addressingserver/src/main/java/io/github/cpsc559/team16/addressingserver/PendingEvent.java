package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.NotificationMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * A {@code PendingEvent} represents a coordination checkpoint in the {@code AddressingServer} system.
 * <p>
 * It is used to track the delivery of a message requiring acknowledgment (ACK) from multiple recipients,
 * and to delay a response to the original requester until all acknowledgments are received.
 * </p>
 *
 * <p>
 * The same message is typically broadcast to all recipients, and this event stores their associated
 * {@link NIOMessageChannel} instances to allow for retry operations. The event can be retried a fixed
 * number of times if acknowledgments are not received.
 * </p>
 *
 * <p>
 * Once all recipients have acknowledged the update, the {@link #respondToRequester()} method is called
 * to deliver a deferred response message to the originator of the request and execute any registered
 * {@link CompletionCallback}.
 * </p>
 */
public class PendingEvent {

    /** The message to send back to the original requester after all ACKs have been received. */
    private final BaseAddrServerMessage<?> deferredResponseMessage;

    /** The broadcast message that all recipients must acknowledge. */
    private BaseAddrServerMessage<?> messageRequiringACK;


    /** Map of remaining recipients expected to send an ACK, keyed by their PID. */
    private Map<Long, NIOMessageChannel> pendingRecipients = new ConcurrentHashMap<>();

    /** The original requester’s communication channel. */
    private final NIOMessageChannel requestChannel;

    /** The message ID of the request message that triggered this PendingEvent to be created */
    private final Long requestMessageID;

    /** The action to take once all ACKs have been received and a response has been sent. */
    private final CompletionCallback onComplete;

    /** The number of times this event has attempted delivery. */
    private int numberOfIterations = 0;

    /** The maximum number of times the message should be resent if ACKs are not received. */
    private final int maxIterations;

    /** The time this {@code PendingEvent} was created (used for timeout tracking). */
    private final long creationTime;

    /**
     * Constructs a {@code PendingEvent} with a full set of parameters, including the original broadcast message.
     *
     * @param deferredResponseMessage the response to send once all ACKs are received
     * @param recipients              the initial map of expected recipient PIDs to their channels
     * @param messageRequiringACK     the message sent to all recipients
     * @param requestChannel          the channel to reply to once the event is complete
     * @param onComplete              an optional callback to invoke after response is sent
     * @param maxIterations           the maximum number of retry attempts before failure
     * @param requestMessageID        the message ID from the message that made the request causing this event to be created.
     */
    public PendingEvent(BaseAddrServerMessage<?> deferredResponseMessage,
                        Map<Long, NIOMessageChannel> recipients,
                        BaseAddrServerMessage<?> messageRequiringACK,
                        NIOMessageChannel requestChannel,
                        CompletionCallback onComplete,
                        int maxIterations, Long requestMessageID) {
        this.deferredResponseMessage = deferredResponseMessage;
        this.pendingRecipients = recipients;
        this.messageRequiringACK = messageRequiringACK;
        this.requestChannel = requestChannel;
        this.onComplete = onComplete;
        this.creationTime = System.currentTimeMillis();
        this.maxIterations = maxIterations;
        this.requestMessageID = requestMessageID;
    }

    /**
     * Constructs a {@code PendingEvent} without requiring a broadcast message.
     *
     * @param deferredResponseMessage the response to send once all ACKs are received
     * @param recipients              the initial map of expected recipient PIDs to their channels
     * @param requestChannel          the channel to reply to once the event is complete
     * @param onComplete              an optional callback to invoke after response is sent
     * @param maxIterations           the maximum number of retry attempts before failure
     * @param requestMessageID        the message ID from the message that made the request causing this event to be created.
     */
    public PendingEvent(BaseAddrServerMessage<?> deferredResponseMessage,
                        Map<Long, NIOMessageChannel> recipients,
                        NIOMessageChannel requestChannel, CompletionCallback onComplete, int maxIterations, Long requestMessageID) {
        this.deferredResponseMessage = deferredResponseMessage;
        this.pendingRecipients = recipients;
        this.requestChannel = requestChannel;
        this.onComplete = onComplete;
        this.creationTime = System.currentTimeMillis();
        this.maxIterations = maxIterations;
        this.requestMessageID = requestMessageID;
    }





    /**
     * Sends the final deferred response message to the requester and runs any follow-up actions.
     *
     * @throws IOException if sending the message fails
     */
    public void respondToRequester() throws IOException {
        try {
            String json = deferredResponseMessage.toJson();
            requestChannel.sendMessage(json);
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (JsonProcessingException j) {
            System.err.println("Failed to serialize PendingEvent: " + this.deferredResponseMessage);
        } catch (IOException e) {
            System.err.println("Failed to respond to requester with PID: " + requestChannel.getServerPID());
            throw e;
        }
    }

    /**
     * Sends a failure notification back to the original requester when a {@code PendingEvent}
     * cannot be successfully completed (e.g. due to missing ACKs after all retries).
     * <p>
     * This method constructs a {@link NotificationMessage} with an {@code ObjectType} of
     * {@code REQUEST_FAILURE}, using the original request's message ID as the payload.
     * The notification is then serialized to JSON and sent to the original requester's
     * {@link NIOMessageChannel}.
     * </p>
     *
     * <p>
     * If serialization fails, the error is logged but no exception is thrown.
     * If message sending fails, an {@link IOException} is thrown to indicate that the
     * requester's connection is broken and should be cleaned up.
     * </p>
     *
     * @throws IOException if sending the failure notification to the requester fails.
     */
    public void respondToRequesterFailure() throws IOException {
        try {
            NotificationMessage<Long> message =
                    NotificationMessage.requestFailedNotification(deferredResponseMessage.getSenderPID(), requestMessageID);
            String json = message.toJson();
            requestChannel.sendMessage(json);
        } catch (JsonProcessingException j) {
            System.err.println("Failed to serialize PendingEvent: " + this.deferredResponseMessage);
        } catch (IOException e) {
            System.err.println("Failed to respond to requester with PID: " + requestChannel.getServerPID());
            throw e;
        }
    }



    /** @return the channel used to reply to the original request initiator */
    public NIOMessageChannel getRequestChannel() {
        return requestChannel;
    }

    /** @return the creation timestamp (ms since epoch) of this event */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Updates the broadcast message being tracked for ACKs (useful in retry scenarios).
     *
     * @param msg the message to be sent again to non-responsive recipients
     */
    public void setMessageRequiringACK(BaseAddrServerMessage<?> msg) {
        this.messageRequiringACK = msg;
    }

    /** @return the current retry attempt count for this event */
    public int getIterationNumber() {
        return numberOfIterations;
    }

    /**
     * Removes a recipient from the pending list once its ACK has been received.
     *
     * @param pid the PID of the recipient to remove
     */
    public void removePendingRecipient(Long pid) {
        pendingRecipients.remove(pid);
    }

    /**
     * Checks whether all expected recipients have acknowledged the broadcast.
     *
     * @return {@code true} if all recipients have ACKed, {@code false} otherwise
     */
    public boolean isComplete() {
        return pendingRecipients.isEmpty();
    }

    public Map<Long, NIOMessageChannel> getPendingRecipients() {
        return pendingRecipients;
    }

    /**
     * Increments the retry counter and checks whether another attempt should be made.
     *
     * @return {@code true} if the event is still within the retry limit, {@code false} if retries are exhausted
     */
    public boolean incrementNumIterations() {
        return (++this.numberOfIterations <= maxIterations);
    }

    public void removeRecipientChannel(Long pid) {
        this.pendingRecipients.remove(pid);
    }

    public BaseAddrServerMessage<?> getMessageRequiringACK() { return this.messageRequiringACK; }
}


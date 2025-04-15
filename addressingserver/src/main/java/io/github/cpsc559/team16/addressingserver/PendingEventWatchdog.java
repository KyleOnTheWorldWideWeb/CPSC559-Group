package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code PendingEventWatchdog} is a background thread that continuously monitors
 * {@link PendingEvent} instances to ensure timely acknowledgments (ACKs) from recipients.
 * <p>
 * It checks for stalled events that have exceeded a timeout interval without receiving all required
 * ACKs. When a timeout occurs, it attempts to resend the message to the remaining recipients. Each
 * {@code PendingEvent} tracks its own retry attempts via an internal iteration counter.
 * <p>
 * If the maximum number of retry attempts is reached without receiving all ACKs, the watchdog will
 * invoke the {@link ConnectionCleanupManager} to handle unresponsive connections by removing the
 * offending process from the system.
 * <p>
 * This thread is designed to run continuously and can be gracefully stopped by setting its
 * {@code running} flag to {@code false} and calling {@link #join()} on the thread.
 */
public class PendingEventWatchdog extends Thread {

    /**
     * Reference to the set of currently tracked {@link PendingEvent}s.
     * The key is a unique event ID (could be a message ID or UUID).
     */
    private final ConcurrentMap<Long, PendingEvent> pendingEvents;

    /**
     * A utility responsible for removing failed ChatServer or AddressingServer processes.
     */
    private final ConnectionCleanupManager cleanupManager;

    /**
     * The maximum time (in milliseconds) to wait for an ACK before retrying
     * and sending the same message to unresponsive recipients.
     */
    // TODO - Ideally, this is dynamically calculated
    private final long retryTimeoutMillis = 2000;

    /**
     * The interval (in milliseconds) at which this watchdog checks all pending events.
     */
    private final long checkIntervalMillis;

    /**
     * Indicates whether the watchdog thread should continue running.
     */
    private volatile boolean running = true;

    /**
     * Constructs a {@code PendingEventWatchdog}.
     *
     * @param pendingEvents       the shared map of pending events to monitor
     * @param cleanupManager      the cleanup utility for unresponsive recipients
     * @param checkIntervalMillis how frequently to loop through and check all pending events
     */
    public PendingEventWatchdog(ConcurrentMap<Long, PendingEvent> pendingEvents,
                                ConnectionCleanupManager cleanupManager,
                                long checkIntervalMillis) {
        super("PendingEventWatchdog");
        this.pendingEvents = pendingEvents;
        this.cleanupManager = cleanupManager;
        this.checkIntervalMillis = checkIntervalMillis;
        this.setDaemon(true);
    }

    /**
     * Signals this thread to stop monitoring events.
     */
    public void shutdown() {
        this.running = false;
    }

    /**
     * Monitors pending events in a loop. On each iteration, the watchdog:
     * <ul>
     *   <li>Identifies events that have exceeded their retry timeout.</li>
     *   <li>Attempts to resend the original message to any unacknowledged recipients.</li>
     *   <li>Increments retry counters and removes recipients who consistently fail to respond.</li>
     *   <li>Cleans up recipients that exceed their retry threshold via {@code cleanupPersistentConnectionNIO()}.</li>
     *   <li>Completes events once all acknowledgments are received.</li>
     * </ul>
     */
    @Override
    public void run() {
        while (running) {
            // We want to use an iterator to avoid concurrent access violations since this is a thread.
            Iterator<Map.Entry<Long, PendingEvent>> iterator = pendingEvents.entrySet().iterator();

            while (iterator.hasNext()) {

                Map.Entry<Long, PendingEvent> entry = iterator.next();

                PendingEvent event = entry.getValue();

                if (event.isComplete()) {
                    iterator.remove();  // It's already handled in {@code ReplicaSyncCoordinator#processAck()}
                    continue;
                }

                if (System.currentTimeMillis() - event.getLastRetryTime() < retryTimeoutMillis) {
                    continue; // Still within timeout window, skip for now
                }

                // False is returned if the maximum number of (message retry) iterations has been reached.
                if (!event.incrementNumIterations()) {
                    System.err.printf("Watchdog max retries reached for event %d. Cleaning up unresponsive peer processes.%n", entry.getKey());
                    for (NIOMessageChannel delinquentChannel : event.getPendingRecipients().values()) {
                        if (delinquentChannel != null) {
                            cleanupManager.cleanupPersistentConnectionNIO(delinquentChannel, true);
                        }
                    }
                    // Respond to the request now that delinquent processes have been expunged from the network
                    try {
                        // I'm not sure why I thought this counted as a failure
                        // it doesn't - we just remove the non-ACKing processes and proceed
                        //event.respondToRequesterFailure();
                        event.respondToRequester();
                    } catch (IOException e) {
                        cleanupManager.cleanupPersistentConnectionNIO(event.getRequestChannel(), true);
                    }
                    iterator.remove(); // Remove the failed event from the list of pending events in ReplicaSyncCoordinator.
                    continue;
                }

                // Retry: resend message to remaining recipients. We use the same message ID, so their ACKs will still
                // be processed and linked to this event.
                Iterator<Map.Entry<Long, NIOMessageChannel>> recipientIterator = event.getPendingRecipients().entrySet().iterator();
                while (recipientIterator.hasNext()) {
                    event.updateLastRetryTime();
                    Map.Entry<Long, NIOMessageChannel> recipient = recipientIterator.next();
                    NIOMessageChannel unresponsiveChannel = recipient.getValue();
                    try {
                        unresponsiveChannel.sendMessage(event.getMessageRequiringACK().toJson());
                        System.out.printf("Watchdog resent message for event with message ID < %d > to PID < %d > (attempt %d)%n",
                                recipient.getKey(), unresponsiveChannel.getServerPID(), event.getIterationNumber());
                    } catch (JsonProcessingException e) {
                        System.err.println("Failed to serialize the retry message.");
                    } catch (IOException ioe) {
                        System.err.printf("Watchdog failed to resend to PID < %d >. Cleaning up and removing from pending list.%n",
                                unresponsiveChannel.getServerPID());
                        cleanupManager.cleanupPersistentConnectionNIO(unresponsiveChannel, true);
                        event.removePendingRecipient(unresponsiveChannel.getServerPID());
                    }
                }
            }
            try {
                Thread.sleep(checkIntervalMillis);
            } catch (InterruptedException ignored) {
                // Allow clean interruption
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.messaging.MessageIDGenerator;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The {@code ReplicaRequestCoordinator} is a background thread responsible for periodically sending
 * synchronization requests from a {@code REPLICA} AddressingServer to the {@code PRIMARY}.
 * <p>
 * It operates independently from the main event loop, and handles retrying until a valid connection
 * to the primary is established. Once connected, it uses a {@link ReplicaRequestManager} to issue
 * structured requests for system state updates (e.g., {@link io.github.cpsc559.team16.common.dto.ChatServerRecord}s,
 * {@link io.github.cpsc559.team16.common.dto.AddrServerRecord}s).
 * </p>
 * <p>
 * The thread is monitored in the event loop via {@code isAlive()}, and may be restarted
 * if it fails. It is also cleanly shut down during server termination.
 */
public class ReplicaRequestCoordinator extends Thread {

    /** Manager used to initiate sync requests to the primary AddressingServer. */
    private ReplicaRequestManager requestManager = null;

    /** A callback used to register the ReplicaRequestManager externally (e.g., in the AddressingServer). */
    private final Consumer<ReplicaRequestManager> onReady;

    /** This is set externally once registration is complete. */
    private volatile NIOMessageChannel primaryChannel;

    /** Generator used to produce unique message IDs for sync requests. */
    private final MessageIDGenerator messageIDGenerator;

    /** Flag to indicate if this thread should continue running. */
    private volatile boolean running = true;

    private final Supplier<NIOMessageChannel> primaryChannelSupplier;

    /** The network PID of the process that instantiated this thread */
    private final Long replicaPID;

    /**
     * Constructs a new {@code ReplicaRequestCoordinator} thread responsible for managing
     * periodic synchronization requests from a {@code REPLICA} to the {@code PRIMARY} AddressingServer.
     *
     * <p>This thread will periodically issue requests for {@link io.github.cpsc559.team16.common.dto.ChatServerRecord}s,
     * and every Nth cycle, it will also request {@link io.github.cpsc559.team16.common.dto.AddrServerRecord}s,
     * allowing for more frequent chat server syncing while avoiding redundant address server requests.</p>
     *
     * <p>The {@code ReplicaRequestManager} that handles the actual messaging logic is created during this thread's
     * execution. The provided {@code onReady} callback is invoked when the manager has been successfully instantiated,
     * allowing the outer system to reference it.</p>
     *
     * @param messageIDGenerator       the generator used to create globally unique message IDs for each request
     * @param onReady                  a {@code Consumer} callback used to expose the instantiated {@link ReplicaRequestManager}
     * @param primaryChannelSupplier   a {@code Supplier} that provides the active {@link NIOMessageChannel} connected to the PRIMARY
     */
    public ReplicaRequestCoordinator(MessageIDGenerator messageIDGenerator, Long replicaPID,
                                     Consumer<ReplicaRequestManager> onReady,
                                     Supplier<NIOMessageChannel> primaryChannelSupplier) {
        super("ReplicaRequestCoordinator");
        this.messageIDGenerator = messageIDGenerator;
        this.replicaPID = replicaPID;
        this.primaryChannelSupplier = primaryChannelSupplier;
        this.onReady = onReady;
        this.setDaemon(true);
    }


    /**
     * Signals the coordinator to stop running. Should be followed by {@code join()} for a clean shutdown.
     */
    public void shutdown() {
        this.running = false;
    }

    /**
     * The main loop for the {@code ReplicaRequestCoordinator}.
     * <p>
     * This method initializes a {@code ReplicaRequestManager}, publishes it via the consumer callback,
     * and enters a loop where sync requests are periodically sent to the primary. It attempts full sync
     * (AddrServerRecord) requests every 6 cycles, and ChatServer syncs on each loop.
     * </p>
     */
    @Override
    public void run() {
        while (running && requestManager == null) {
            NIOMessageChannel primaryChannel = primaryChannelSupplier.get();
            if (primaryChannel != null) {
                this.requestManager = new ReplicaRequestManager(messageIDGenerator, primaryChannel);
                this.onReady.accept(requestManager);
                break;
            }
            try {
                Thread.sleep(1000); // Try again shortly
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return; // Exit cleanly
            }
        }


        while (running) {
            try {
                if (requestManager.incrementSyncCounter()) {        // Update all records (Chat and Addr) every 6 cycles
                    requestManager.requestAllServerRecords(this.replicaPID);
                }
                else { requestManager.requestAllChatServerRecords(this.replicaPID);} // update only chat server records every cycle

                Thread.sleep(20000); // ~1 request every 20 seconds (can be adjusted)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("ReplicaRequestCoordinator encountered an error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

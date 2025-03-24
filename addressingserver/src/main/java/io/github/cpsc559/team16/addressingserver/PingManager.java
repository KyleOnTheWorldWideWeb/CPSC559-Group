package io.github.cpsc559.team16.addressingserver;

public class PingManager implements Runnable {

    /**
     * Flag used to mark whether the process should shutdown
     */
    private boolean shutdown = false;
    public void shutdown() {
        shutdown = true;
    }

    /**
     * The {@code LeaderElectionManager} instance used to initiate elections
     */
    private final LeaderElectionManager leaderElectionManager;

    /**
     * Flag used to mark whether a ping has been received (since its last reset)
     */
    private boolean receivedPing;

    /**
     * Timeout duration to wait for ping
     */
    private int pingTimeout = 3000; // Milliseconds
    public void setPingTimeout(int milliseconds) {
        pingTimeout = milliseconds;
    }

    public PingManager(LeaderElectionManager leaderElectionManager) {
        this.leaderElectionManager = leaderElectionManager;
        receivedPing = false;
    }

    /**
     * Notify this process that a ping has been received
     */
    public void ping() {
        receivedPing = true;
    }

    /**
     * Wait for timeout, then check if ping has been received.
     */
    public void awaitPing() {

        try {

            Thread.sleep(pingTimeout);

            if (!receivedPing) {
                leaderElectionManager.initiateElection();
            } else {
                receivedPing = false;
            }

        } catch (InterruptedException e) {
            System.err.println("Ping manager interrupted while awaiting ping.");
        }
    }

    public void run() {
        while (!shutdown) {
            awaitPing();
        }
    }
    
}

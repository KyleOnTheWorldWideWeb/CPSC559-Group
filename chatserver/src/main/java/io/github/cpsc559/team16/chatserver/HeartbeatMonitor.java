package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;
import java.util.Map;

/**
 * Monitors the heartbeat of connected peer servers and clients, ensuring that
 * inactive peers
 * are detected and disconnected after a specified number of missed {@code PONG}
 * responses.
 * <p>
 * This class implements the {@link Runnable} interface and runs in a separate
 * thread to periodically check
 * the activity of connected peers. It performs the following tasks:
 * </p>
 * <ul>
 * <li>Checks the time elapsed since the last activity of each peer.</li>
 * <li>If a peer is inactive beyond the configured {@link #IDLE_TIMEOUT}, it
 * sends a {@code PING} to check the peer's status.</li>
 * <li>If the peer is already awaiting a {@code PONG} response and the number of
 * missed pongs exceeds the {@link #MAX_MISSED} threshold,
 * the peer is considered unresponsive and is disconnected.</li>
 * </ul>
 *
 * <h3>Parameters:</h3>
 * <ul>
 * <li>{@code peerMap}: A map of peer IDs to their corresponding
 * {@link ConnectionContext}, which holds connection details.</li>
 * </ul>
 *
 * <h3>Debugging:</h3>
 * <ul>
 * <li>Various debug levels are used for monitoring the heartbeat process,
 * including details about peer inactivity and ping/pong exchanges.</li>
 * </ul>
 *
 * <h3>Key Methods:</h3>
 * <ul>
 * <li>{@link #run()} - The main loop that checks for peer activity, sends
 * heartbeats, and handles disconnections.</li>
 * </ul>
 * 
 * @see ConnectionContext for details on connection activity tracking
 * @see ChatServer#sendPing(ConnectionContext, SelectionKey) for sending PING
 *      messages
 */

public class HeartbeatMonitor implements Runnable {
    private static final long IDLE_TIMEOUT = 8_000; // 8 seconds
    private static final int MAX_MISSED = 3;

    private final Map<Integer, ConnectionContext> peerMap;

    public HeartbeatMonitor(Map<Integer, ConnectionContext> peerMap) {
        this.peerMap = peerMap;
    }

    public static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "1"));

    private static void debug(int level, String message) {
        if (level <= DEBUG_LEVEL) {
            String prefix = switch (level) {
                case 1 -> "[BASIC] ";
                case 2 -> "[NORMAL] ";
                case 3 -> "[DETAILED] ";
                case 4 -> "[LOW_LEVEL] ";
                case 5 -> "[EXTREME] ";
                default -> "[INFO] ";
            };
            System.out.println(prefix + "[HEARTBEAT] " + message);
        }
    }

    @Override
    public void run() {
        while (true) {
            debug(3, "Checking peers for heartbeat...");

            long now = System.currentTimeMillis();

            for (Map.Entry<Integer, ConnectionContext> entry : peerMap.entrySet()) {
                int peerID = entry.getKey();
                ConnectionContext ctx = entry.getValue();
                SelectionKey key = ctx.socketChannel.keyFor(ChatServer.getSelector());

                if (key == null || !key.isValid()) {
                    debug(3, "Skipping peer " + peerID + " — no valid SelectionKey.");
                    continue;
                }

                long timeSinceLastActivity = now - ctx.lastActivityTime;
                debug(4, "Peer " + peerID + " inactive for " + timeSinceLastActivity + "ms");

                if (timeSinceLastActivity > IDLE_TIMEOUT) {
                    if (ctx.awaitingPong) {
                        ctx.missedPongs++;
                        debug(2, "Peer " + peerID + " missed pong #" + ctx.missedPongs);

                        if (ctx.missedPongs >= MAX_MISSED) {
                            try {
                                debug(1, "Closing unresponsive peer " + peerID);

                                // Don't notify addressing server here, let ChatServer.closeConnection handle it

                                key.cancel();
                                ctx.socketChannel.close();
                                peerMap.remove(peerID);
                            } catch (Exception e) {
                                debug(1, "Error closing peer " + peerID + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                            continue;
                        }
                    } else {
                        debug(2, "Sending PING to peer " + peerID);
                        ChatServer.sendPing(ctx, key);
                    }
                }
            }

            try {
                Thread.sleep(5_000); // check every 5 seconds
            } catch (InterruptedException e) {
                debug(1, "HeartbeatMonitor interrupted.");
                return;
            }
        }
    }
}

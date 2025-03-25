package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;
import java.util.Map;

public class HeartbeatMonitor implements Runnable {
    private static final long IDLE_TIMEOUT = 5_000; // 5 seconds
    private static final int MAX_MISSED = 3;

    private final Map<Integer, ConnectionContext> peerMap;

    public HeartbeatMonitor(Map<Integer, ConnectionContext> peerMap) {
        this.peerMap = peerMap;
    }

    private static void debug(int level, String message) {
        if (level <= ChatServer.DEBUG_LEVEL) {
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
                Thread.sleep(2_000); // check every 2 seconds
            } catch (InterruptedException e) {
                debug(1, "HeartbeatMonitor interrupted.");
                return;
            }
        }
    }
}

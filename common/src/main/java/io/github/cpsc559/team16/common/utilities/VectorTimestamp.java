package io.github.cpsc559.team16.common.utilities;

import java.util.HashMap;
import java.util.Map;

public class VectorTimestamp {
    private final Map<Integer, Integer> timestamp;

    public VectorTimestamp() {
        this.timestamp = new HashMap<>();
    }

    public synchronized void increment(int serverId) {
        timestamp.put(serverId, timestamp.getOrDefault(serverId, 0) + 1);
    }

    public synchronized void update(VectorTimestamp other) {
        for (Map.Entry<Integer, Integer> entry : other.timestamp.entrySet()) {
            int serverId = entry.getKey();
            int otherClock = entry.getValue();
            int currentClock = timestamp.getOrDefault(serverId, 0);
            timestamp.put(serverId, Math.max(currentClock, otherClock));
        }
    }

    public synchronized Map<Integer, Integer> getTimestamp() {
        return new HashMap<>(timestamp);
    }

    @Override
    public synchronized String toString() {
        return timestamp.toString();
    }
}
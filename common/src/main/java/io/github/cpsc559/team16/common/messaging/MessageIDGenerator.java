package io.github.cpsc559.team16.common.messaging;

public class MessageIDGenerator {
    private long pid;
    private long lastTimestamp = -1;
    private int sequence = 0;

    public MessageIDGenerator() {
        this.pid = 0;
    }

    public void setPID(long pid) {
        if (pid < 0 || pid > 0xFFFFL) throw new IllegalArgumentException("PID out of range");
        this.pid = pid;
    }

    public synchronized long nextID() {
        long currentTime = System.currentTimeMillis() & 0xFFFFFFFFFFL; // 40 bits

        if (currentTime == lastTimestamp) {
            sequence = (sequence + 1) & 0xFF; // Wrap around at 255
            if (sequence == 0) {
                // Busy wait: wait for next millisecond
                while ((currentTime = System.currentTimeMillis() & 0xFFFFFFFFFFL) == lastTimestamp) {
                    Thread.yield();
                }
            }
        } else {
            sequence = 0;
            lastTimestamp = currentTime;
        }

        return (pid << 48) | (currentTime << 8) | sequence;
    }

    public static long extractPid(long id) {
        return (id >>> 48) & 0xFFFFL;
    }

    public static long extractTimestamp(long id) {
        return (id >>> 8) & 0xFFFFFFFFFFL;
    }

    public static int extractSequence(long id) {
        return (int)(id & 0xFF);
    }
}


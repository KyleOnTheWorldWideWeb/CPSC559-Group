package io.github.cpsc559.team16.chatserver;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class WriteUtils {
    public static void enqueueResponse(ConnectionContext ctx, SelectionKey key, String response) {
        synchronized (ctx.writeQueue) {
            System.out.println("Sending response " + response);
            ctx.writeQueue.add(ByteBuffer.wrap(response.getBytes()));
        }
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AddrServerPushManager {
    private final List<SocketChannel> replicaChannels;

    public AddrServerPushManager(List<SocketChannel> replicaChannels) {
        this.replicaChannels = replicaChannels;
    }

    /**
     * Pushes an update about a newly registered or updated chat server to all replicas.
     *
     * @param chatServerRecord The chat server information to be sent.
     */
    public void pushChatServerUpdate(ChatServerRecord chatServerRecord) {
        String updateMessage = formatUpdateMessage(chatServerRecord);
        ByteBuffer buffer = ByteBuffer.wrap(updateMessage.getBytes(StandardCharsets.UTF_8));

        for (SocketChannel replicaChannel : replicaChannels) {
            try {
                while (buffer.hasRemaining()) {
                    replicaChannel.write(buffer);
                }
                buffer.rewind();
            } catch (IOException e) {
                System.err.println("Failed to push update to replica: " + e.getMessage());
            }
        }
    }

    private String formatUpdateMessage(ChatServerRecord chatServerRecord) {
        return "UPDATE " + chatServerRecord.getPID() + " " + chatServerRecord.getHostAddress() + ":" + chatServerRecord.getClientPort();
    }
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerManager {

    private final Map<SocketChannel, NIOMessageChannel> chatServerChannels;

    public ChatServerManager() {
        this.chatServerChannels = new ConcurrentHashMap<>();
    }



    public void registerServer(SocketChannel socketChannel, NIOMessageChannel nioChannel, Long peerPID,
                               Long primaryPID, ChatServerRecord record) throws IOException {

    }
}

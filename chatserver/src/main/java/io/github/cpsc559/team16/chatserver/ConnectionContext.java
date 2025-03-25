package io.github.cpsc559.team16.chatserver;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

import io.github.cpsc559.team16.chatserver.ChatServer.ConnectionType;

public class ConnectionContext {

    public final SocketChannel socketChannel;
    public ConnectionType type;
    public ByteBuffer readBuffer = ByteBuffer.allocate(4096);
    public Queue<ByteBuffer> writeQueue = new LinkedList<>();
    public StringBuilder partialData = new StringBuilder();

    public int peerID = -1;
    public String username;

    public String host; // IP or hostname
    public int port; // Port used to connect

    public long lastActivityTime = System.currentTimeMillis();
    public boolean awaitingPong = false;
    public int missedPongs = 0;

    public ConnectionContext(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.lastActivityTime = System.currentTimeMillis();

    }
}
package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentConnectionManager {
    private final ConcurrentHashMap<SocketChannel, NIOMessageChannel> persistentConnections = new ConcurrentHashMap<>();
    private final int replicaPort;
    private final int chatServerPort;


    public PersistentConnectionManager(int replicaPort, int chatServerPort) {
        this.replicaPort = replicaPort;
        this.chatServerPort = chatServerPort;
    }

    /**
     * Adds a persistent connection with an existing SocketChannel and NIOMessageChannel.
     * <p>
     * This method is used when a persistent connection has already been established and
     * an NIOMessageChannel has been created externally. It ensures that the connection
     * is tracked and can be used for push updates.
     * </p>
     *
     * @param channel      The existing {@code SocketChannel} representing the connection.
     * @param nioChannel   The pre-initialized {@code NIOMessageChannel} for this connection.
     */
    public void addPersistentConnection(SocketChannel channel, NIOMessageChannel nioChannel) {
        if (channel == null || nioChannel == null) {
            throw new IllegalArgumentException("SocketChannel and NIOMessageChannel must not be null.");
        }
        persistentConnections.put(channel, nioChannel);
        System.out.println("Persistent connection added: " + channel);
    }


    /**
     * Registers a persistent connection if it belongs to a known port.
     */
    public void storeConnection(SocketChannel channel) {
        try {
            int port = ((InetSocketAddress) channel.getRemoteAddress()).getPort();
            if (port == replicaPort || port == chatServerPort) {
                persistentConnections.put(channel, new NIOMessageChannel(channel));
                System.out.println("Persistent connection registered: " + channel);
            }
        } catch (IOException e) {
            System.err.println("Failed to determine remote port: " + e.getMessage());
        }
    }

    public NIOMessageChannel getNIOChannel(SocketChannel channel) {
        return this.persistentConnections.get(channel);
    }

    /**
     * Removes a persistent connection.
     */
    public void removeConnection(SocketChannel channel) {
        persistentConnections.remove(channel);
    }

    /**
     * Checks if a given channel is a persistent connection.
     */
    public boolean isPersistent(SocketChannel channel) {
        return persistentConnections.containsKey(channel);
    }

    /**
     * Sends an update message to all persistent connections.
     */
    public void sendPushUpdate(String message) {
        for (NIOMessageChannel nioChannel : persistentConnections.values()) {
            try {
                nioChannel.sendMessage(message);
            } catch (IOException e) {
                System.err.println("Failed to send update: " + e.getMessage());
            }
        }
    }
}

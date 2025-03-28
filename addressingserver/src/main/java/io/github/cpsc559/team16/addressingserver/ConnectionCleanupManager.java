package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.exceptions.ConnectionClosedException;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ConnectionCleanupManager {

    /**
     * The process responsible for managing interactions between the Primary
     * {@code AddressingServer} and its replicas.
     */
    private final PeerManager peerManager;

    public PeerManager getPeerManager() {
        return peerManager;
    }

    /**
     * The process responsible for managing interactions between the
     * {@code AddressingServer} and {@code ChatServer}'s
     */
    private final ChatServerManager chatServerManager;

    public ChatServerManager getChatServerManager() {
        return chatServerManager;
    }

    public ConnectionCleanupManager(PeerManager peerManager,
                                    ChatServerManager chatServerManager) {
        this.peerManager = peerManager;
        this.chatServerManager = chatServerManager;
    }

    /**
     * Determines whether the specified {@link SocketChannel} is associated with a persistent server-to-server connection.
     * <p>
     * Persistent connections are long-lived channels used for internal communication between
     * {@code AddressingServer}s (peers) and {@code ChatServer}s. These are stored and tracked
     * using their respective manager classes.
     * </p>
     *
     * @param channel the {@code SocketChannel} to inspect.
     * @return {@code true} if the channel is known to be persistent (i.e., belongs to a peer or chat server), {@code false} otherwise.
     */
    public boolean isPersistentConnection(SocketChannel channel) {
        return peerManager.getChannels().containsKey(channel)
                || chatServerManager.getChannels().containsKey(channel);
    }

    /**
     * Retrieves the {@link NIOMessageChannel} wrapper for a known persistent connection.
     * <p>
     * This method searches the internal maps of both the {@code PeerManager} and {@code ChatServerManager}
     * to find the {@code NIOMessageChannel} corresponding to the provided {@link SocketChannel}.
     * </p>
     * <p>
     * If the channel is not found in either manager, the method returns {@code null}.
     * </p>
     *
     * @param channel the {@code SocketChannel} to look up.
     * @return the associated {@code NIOMessageChannel}, or {@code null} if not found.
     */
    public NIOMessageChannel getKnownPersistentChannel(SocketChannel channel) {
        NIOMessageChannel ch = peerManager.getChannels().get(channel);
        if (ch != null) return ch;
        return chatServerManager.getChannels().get(channel); // Will return null if it doesn't exist (which is what we want)
    }

    /**
     * Cleans up a persistent connection and deregisters it from the internal selector.
     * <p>
     * This method is triggered when a persistent connection is closed or encounters an unrecoverable I/O error.
     * It performs the following steps:
     * <ul>
     *     <li>Logs the reason for cleanup (remote disconnect or local I/O failure).</li>
     *     <li>Removes the connection from either the {@code PeerManager} or {@code ChatServerManager}.</li>
     *     <li>Cancels the selection key and closes the channel gracefully.</li>
     * </ul>
     * </p>
     *  @param channel the {@code SocketChannel} being cleaned up.
     *
     * @param cce {@code true} if the cleanup is due to a remote disconnect (i.e., {@link ConnectionClosedException}),
     *            {@code false} if due to a local I/O failure.
     */
    public void cleanupPersistentConnection(SocketChannel channel, Boolean cce) {
//        System.err.printf("Channel cleanup triggered for -> %s - due to -> (%s)\n",
//                channel,
//                cce ? "remote process disconnection." : "I/O failure."
//        );
        if (cce) {
            NIOMessageChannel ch = getKnownPersistentChannel(channel);
            if (ch != null) {
                Long pid = ch.getServerPID();
                if (chatServerManager.getChannels().containsKey(channel)) {
                    chatServerManager.removeRemoteProcess(channel);
                } else if (peerManager.getChannels().containsKey(channel)) {
                    peerManager.removeRemoteProcess(channel);
                }
            }
        }
        //key.cancel();  Keys for closed SocketChannels are canceled during the next selector.select event in the main event loop. No need to do that here.
        //key.cancel();
        try {
            channel.close();
        } catch (IOException ignored) {}  // if the channel is already closed, we don't need to do anything.
    }



}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.utilities.NetworkManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class AddrServerReadDispatcher implements NetworkManager.ReadDispatcher {
    private final AddressingServer server;

    public AddrServerReadDispatcher(AddressingServer server) {
        this.server = server;
    }


    @Override
    public void dispatch(SelectionKey key) throws IOException {
        if (server.getRole() == AddrServerConfig.ServerRole.BACKUP) {

        }
    }
//    @Override
//    public void dispatch(SelectionKey key) throws IOException {
//        // Get the channel associated with this key
//        SocketChannel channel = (SocketChannel) key.channel();
//        ByteBuffer buffer = ByteBuffer.allocate(1024);
//        int bytesRead = channel.read(buffer);
//
//        if (bytesRead == -1) {
//            // Remote closed connection; cancel key and close channel.
//            key.cancel();
//            channel.close();
//            System.out.println("Connection closed by remote host.");
//            return;
//        }
//
//        // Prepare buffer for reading and decode it to a String.
//        buffer.flip();
//        String jsonMessage = StandardCharsets.UTF_8.decode(buffer).toString();
//        System.out.println("Received update message: " + jsonMessage);
//
//        // Pass the JSON message to the server to handle the update (e.g., a ChatServerInfo update)
//        server.handleReplicaUpdate(jsonMessage);
//    }
}

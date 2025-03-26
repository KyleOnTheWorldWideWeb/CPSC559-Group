package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;
import io.github.cpsc559.team16.common.utilities.BaseMessage;

class AddressingServerHandler implements ConnectionHandler {
    public void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key) {
        System.out.println("[ADDR_SERVER] Received message: " + message);
    }
}

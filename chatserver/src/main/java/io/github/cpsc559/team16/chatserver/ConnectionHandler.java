package io.github.cpsc559.team16.chatserver;

import java.nio.channels.SelectionKey;

import io.github.cpsc559.team16.common.utilities.BaseMessage;

interface ConnectionHandler {
    void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key);
}
package io.github.cpsc559.team16.chatserver;

import io.github.cpsc559.team16.utilities.ProcessUtils;

public class ChatServer {
    public static void main(String[] args) {
        System.out.printf("Chat Server process\n\t-Main function executing..... PID: %d%n", ProcessUtils.getPid());
    }
}
package io.github.cpsc559.team16.chatserver;

public class ServerInfo {
    private String address;
    private int port;

    public ServerInfo(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}

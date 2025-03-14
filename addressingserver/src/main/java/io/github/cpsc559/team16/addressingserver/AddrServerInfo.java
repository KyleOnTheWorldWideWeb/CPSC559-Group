package io.github.cpsc559.team16.addressingserver;

public class AddrServerInfo extends ServerInfo {

    /**
     * The port used for connections with chat servers.
     */
    private final int chatServerPort;

    public int getChatServerPort() { return this.chatServerPort; }

    /**
     * Constructs a new {@code ChatServerInfo} instance with the specified parameters, a default
     * {@code ACTIVE status} and a starting {@code clientCount} of zero.
     *
     * @param serverID       The unique identifier (key) of the chat server. Needed for the HashMap of ChatServerInfo records kept by Addressing Servers.
     * @param hostAddress    The network (IP) address of the chat server.
     * @param clientPort     The port used for communication with client processes.
     * @param peerPort       The port used for peer-to-peer communication with other addressing servers.
     * @param chatServerPort The port used for communication with the chat servers
     *
     */
    public AddrServerInfo(Long serverID, String hostAddress,int clientPort, int peerPort, int chatServerPort) {
        super(serverID, hostAddress, peerPort, clientPort);
        this.chatServerPort = chatServerPort;

    }
}

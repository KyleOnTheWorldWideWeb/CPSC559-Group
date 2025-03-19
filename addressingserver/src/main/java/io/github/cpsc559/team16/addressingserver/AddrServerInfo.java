package io.github.cpsc559.team16.addressingserver;


public class AddrServerInfo extends ServerInfo {

    /**
     * The port used for connections with chat servers.
     */
    private final int chatServerPort;
    public int getChatServerPort() { return this.chatServerPort; }

    /**
     * AddressingServer processes are either:
     * <ul>
     *     <li>PRIMARY - the leader process in charge of coordinating connections in the network.</li>
     *     <li>BACKUP - a `Passive Replica` receiving and retrieving updates.</li>
     * </ul>
     */
    private AddrServerConfig.ServerRole role;
    public AddrServerConfig.ServerRole getRole() {
        return role;
    }


    /**
     * Constructs a new {@code ChatServerInfo} instance with the specified parameters, a default
     * {@code ACTIVE status} and a starting {@code clientCount} of zero.
     *
     * @param serverID       The unique identifier (key) of the chat server. Needed for the HashMap of ChatServerInfo records kept by Addressing Servers.
     * @param hostAddress    The network (IP) address of the chat server.
     * @param clientPort     The port used for communication with client processes.
     * @param peerPort       The port used for peer-to-peer communication with other addressing servers.
     * @param chatServerPort The port used for communication with the chat servers
     * @param role           The role of the {@code AddressingServer} process in the network.
     *
     * @see io.github.cpsc559.team16.addressingserver.AddrServerConfig for more details on AddressingServer Network configuration.
     */
    public AddrServerInfo(Long serverID, String hostAddress, int clientPort, int peerPort, int chatServerPort, AddrServerConfig.ServerRole role) {
        super(serverID, hostAddress, peerPort, clientPort);
        this.chatServerPort = chatServerPort;
        this.role = role;

    }
}

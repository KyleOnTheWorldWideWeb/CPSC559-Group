package io.github.cpsc559.team16.common.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddrServerRecord extends ServerRecord {

    /**
     * The port used for connections with chat servers.
     */
    private final int chatServerPort;


    /**
     * AddressingServer processes are either:
     * <ul>
     *     <li>PRIMARY - the leader process in charge of coordinating connections in the network.</li>
     *     <li>REPLICA - a `Passive Replica` receiving and retrieving updates.</li>
     * </ul>
     *
     */
    private final ServerRole role;



    /**
     * Constructs a new {@code AddrServerRecord} instance with the specified parameters, a default
     * {@code ACTIVE status} and a starting {@code clientCount} of zero.
     *
     * @param serverPID       The unique identifier (key) of the chat server. Needed for the HashMap of ChatServerRecord records kept by Addressing Servers.
     * @param hostAddress    The network (IP) address of the chat server.
     * @param clientPort     The port used for communication with client processes.
     * @param peerPort       The port used for peer-to-peer communication with other addressing servers.
     * @param chatServerPort The port used for communication with the chat servers
     * @param role           The role of the {@code AddressingServer} process in the network.
     *
     */
    @JsonCreator
    public AddrServerRecord(
            @JsonProperty("pid") Long serverPID,
            @JsonProperty("hostAddress") String hostAddress,
            @JsonProperty("clientPort") int clientPort,
            @JsonProperty("peerPort") int peerPort,
            @JsonProperty("chatServerPort") int chatServerPort,
            @JsonProperty("role") ServerRole role
    ) {
        super(serverPID, hostAddress, peerPort, clientPort);
        this.chatServerPort = chatServerPort;
        this.role = role;
    }




    // --- Getters ---

    public int getChatServerPort() {
        return chatServerPort;
    }

    public ServerRole getRole() {
        return role;
    }



}

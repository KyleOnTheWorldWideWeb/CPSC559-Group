package io.github.cpsc559.team16.common.dto;

public abstract class ServerRecord {

    /**
     * The unique identifier of this server.
     */
    protected Long pid;

    /**
     * The network address of this server.
     */
    protected String hostAddress;

    /**
     * The port used for client connections.
     */
    protected final int clientPort;

    /**
     * The port used for communication with the servers peers.
     */
    protected final int peerPort;

    /*
     * NOTE for team-members: I chose to make ports dynamic as using static port numbers
     * would mean that only one process could run on each network address. The solution to this would
     * be to create a different external port number for each container which then maps to the static internal
     * port number. This seemed like it would add complexity and room for error, and dynamic port allocation and
     * registration at startup has very little overhead.
     */


    public Long getPID() {
        return this.pid;
    }

    public String getHostAddress() {
        return this.hostAddress;
    }

    public int getPeerPort() {
        return this.peerPort;
    }

    public int getClientPort() {
        return this.clientPort;
    }

    /**
     * Constructs a new {@code ServerRecord} object with the specified parameters, a default
     *
     * @param processID   The unique identifier for this server process.
     * @param hostAddress The network address for the server.
     * @param peerPort    The port used for peer-to-peer communication with other processes.
     * @param clientPort  The port used for communication with client processes.
     */
    protected ServerRecord(Long processID, String hostAddress, int peerPort, int clientPort) {
        this.pid = processID;
        this.hostAddress = hostAddress;
        this.peerPort = peerPort;
        this.clientPort = clientPort;
    }

    // --- Setters ---

    public void setPID(Long pid) {
        this.pid = pid;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;


    }
}

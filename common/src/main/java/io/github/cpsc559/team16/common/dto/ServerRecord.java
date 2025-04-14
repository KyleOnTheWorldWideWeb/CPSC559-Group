package io.github.cpsc559.team16.common.dto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public abstract class ServerRecord {

    /**
     * The unique identifier of this server.
     */
    protected Long pid;

    /**
     * The network address of this server.
     */
    protected String hostAddress;

    protected String publicAddress;

    /**
     * The port used for client connections.
     */
    protected final int clientPort;

    /**
     * The port used for communication with the servers peers.
     */
    protected final int peerPort;

    /**
     * Constructs a new {@code ServerRecord} object with the specified parameters, a default
     *
     * @param processID   The unique identifier for this server process.
     * @param hostAddress The network address for the server.
     * @param publicAddress The network address of the machine running the server.
     * @param peerPort    The port used for peer-to-peer communication with other processes.
     * @param clientPort  The port used for communication with client processes.
     */
    protected ServerRecord(Long processID, String hostAddress, String publicAddress, int peerPort, int clientPort) {
        this.pid = processID;
        this.hostAddress = hostAddress;
        this.publicAddress = publicAddress;
        this.peerPort = peerPort;
        this.clientPort = clientPort;
    }

    /**
     * Updates the provided {@link ServerRecord} with runtime network information from the given socket connection and PID.
     * <p>
     * This method is typically called during server registration to ensure that the record accurately reflects
     * the process's actual host address and assigned PID. The host address is extracted directly from the
     * {@link SocketChannel}'s remote address to avoid relying on values sent by the remote process.
     * </p>
     *
     * @param socketChannel the socket representing the remote process's connection
     * @param record        the {@link ServerRecord} instance provided by the remote process (in the registration message).
     * @param pid           the process ID assigned to the remote process (by the primary addressing server).
     * @return the updated {@link ServerRecord} with corrected host address and assigned PID.
     * @throws IOException if the remote address cannot be resolved from the socket
     */
    public static <T extends ServerRecord> T updateAddressFromSocket(SocketChannel socketChannel, T record, Long pid) throws IOException {
        record.setPID(pid);
        InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        String host = remoteAddress.getAddress().getHostAddress();
        record.setHostAddress(host);
        return record;
    }


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

    public String getPublicAddress() {
        return this.publicAddress;
    }

    public int getPeerPort() {
        return this.peerPort;
    }

    public int getClientPort() {
        return this.clientPort;
    }

    // --- Setters ---

    public void setPID(Long pid) {
        this.pid = pid;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }
    public void setPublicAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }


}

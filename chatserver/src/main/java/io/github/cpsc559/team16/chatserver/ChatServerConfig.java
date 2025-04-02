package io.github.cpsc559.team16.chatserver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import io.github.cpsc559.team16.addressingserver.AddrServerConfig;

public class ChatServerConfig {
    
    private String getContainerIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filter out loopback and inactive interfaces
                if (iface.isLoopback() || !iface.isUp())
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Failed to get container IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }  

    /**
     * Each AddressingServer process has a distinct id amongst its peers.
     * {@code pid} is used as a 'tie-breaker' during leader elections.
     */
    private long serverID;

    /**
     * The network address of this Chat Server.
     * The Primary Addressing Server posts this address to the
     */
    private final String hostAddress;

    /**
     * The port used for client connections.
     * TODO - Assign dynamically at runtime for replication.
     * Clients use this port to connect and send messages.
     */
    private final int clientPort;

    /**
     * The port reserved for peer-to-peer communication amongst the
     * TODO - Assign dynamically at runtime for replication.
     * Primary Addressing Server and it's backups.
     */
    private final int addrServerPort;

    /**
     * The port used for communicating with Chat Servers.
     * This should be the port the chat server used to register itself with the Addressing Server.
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
    private final Boolean leader;


    /**
     * The configuration object for the Addressing Server.
     * <p>
     * This instance of {@link AddrServerConfig} is responsible for reading and storing
     * environment variables and other runtime settings required for initializing the server.
     * It provides access to essential network parameters such as:
     * <ul>
     *     <li>Host address</li>
     *     <li>Client port</li>
     *     <li>Replica port</li>
     *     <li>Chat server port</li>
     *     <li>Server role (PRIMARY or BACKUP)</li>
     *     <li>Server pid (Process ID)</li>
     * </ul></p>
     */
    public ChatServerConfig() {
        System.out.println("----> Chat Server environment variables:");
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));
        // String roleEnv = (System.getenv().getOrDefault("AS_ROLE", "BACKUP")).trim().toUpperCase();
        this.leader = Boolean.parseBoolean(System.getenv().getOrDefault("LEADER", "false"));
        this.hostAddress = getContainerIpAddress();
        this.clientPort = Integer.parseInt(System.getenv().get("CS_PORT"));
        this.addrServerPort = Integer.parseInt(System.getenv().get("CS_ADDRSERVER_PORT"));
        this.chatServerPort = Integer.parseInt(System.getenv().get("CS_PEER_PORT"));
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getClientPort() {
        return clientPort;
    }

    public int getaddrServerPort() {
        return addrServerPort;
    }
    
    public int getChatServerPort() {
        return chatServerPort;
    }
    
    public boolean isLeader() {
        return leader;
    }

    public Long getServerID() {
        return this.serverID;
    }

    public void setServerID(Long id){
        this.serverID = id;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chat Server Info {\n");
        sb.append("  serverID: ").append(serverID).append("\n");
        sb.append("  hostAddress: ").append(hostAddress).append("\n");
        sb.append("  clientPort: ").append(clientPort).append("\n");
        sb.append("  addrServerPort: ").append(addrServerPort).append("\n");
        sb.append("  chatServerPort: ").append(chatServerPort).append("\n");
        sb.append("  leader: ").append(leader).append("\n");
        sb.append("}");
        return sb.toString();
    }
    
}

package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.dto.ServerRole;

public class AddrServerConfig {

    /**
     * Each AddressingServer process has a distinct id amongst its peers.
     * {@code pid} is used as a 'tie-breaker' during leader elections.
     */
    private long pid;

    /**
     * The network address of this Addressing Server.
     * The Primary Addressing Server posts this address to the
     * A-record in the static DNS.
     * TODO - Assign dynamically at runtime for replication.
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
    private final int replicaPort;

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
    private ServerRole role;


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
    public AddrServerConfig() {
        System.out.println("----> Addressing server environment variables:");
        System.getenv().forEach((key, value) -> System.out.println(key + ": " + value));
        String roleEnv = System.getenv().getOrDefault("AS_ROLE", "BACKUP").trim().toUpperCase();
        this.role = roleEnv.equals("PRIMARY") ? ServerRole.PRIMARY : ServerRole.REPLICA;
        this.hostAddress = System.getenv().getOrDefault("HOST_ADDRESS", "255.255.0.2");
        this.clientPort = Integer.parseInt(System.getenv().get("AS_CLIENT_PORT"));
        this.replicaPort = Integer.parseInt(System.getenv().get("AS_REPLICA_PORT"));
        this.chatServerPort = Integer.parseInt(System.getenv().get("AS_CHATSERVER_PORT"));
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getClientPort() {
        return clientPort;
    }

    public int getReplicaPort() {
        return replicaPort;
    }

    public int getChatServerPort() {
        return chatServerPort;
    }



    /**
     * Changes the role of an AddressingServer in the DS.
     * <p>During failover, a new PRIMARY addressing server must
     * be elected and change its role to reflect its new status.</p>
     * @param role The type of ServerRole for this AddressingServer.
     */
    public void setRole(ServerRole role) {
        this.role = role;
    }

    /**
     * AddressingServer processes are either:
     * <ul>
     *     <li>PRIMARY - the leader process in charge of coordinating connections in the network.</li>
     *     <li>REPLICA - a `Passive Replica` receiving and retrieving updates.</li>
     * </ul>
     *
     */
    public ServerRole getRole() {
        return role;
    }

    public void setPID(Long pid) {
        this.pid = pid;
    }
    public Long getPID() {
        return this.pid;
    }



}

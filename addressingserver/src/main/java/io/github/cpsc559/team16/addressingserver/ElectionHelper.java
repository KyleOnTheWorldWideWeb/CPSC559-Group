package io.github.cpsc559.team16.addressingserver;


/* My idea with this class is that it will only be instantiated when the addressing server has failed, and will only exist
* until the election cycle has completed. Once the election is complete and a new Primary has been elected, the object will be destroyed.
*
* We can definitely do something else entirely, but I think it's good to destroy all information associated with a previous election.
*
*/


import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;

import java.io.IOException;

public final class ElectionHelper {


    /**
     * Computes the next available PID by scanning both registries.
     * Called during promotion to PRIMARY.
     *
     * @return the next safe PID to assign.
     */
//    private static long computeNextPID(AddressingServer server) {
//        long maxChatPID = server.getChatServerRegistry().getRecords().keySet().stream()
//                .mapToLong(Long::longValue)
//                .max()
//                .orElse(0L);
//
//        long maxAddrPID = server.getAddrServerRegistry().getRecords().keySet().stream()
//                .mapToLong(Long::longValue)
//                .max()
//                .orElse(0L);
//
//        return Math.max(maxChatPID, maxAddrPID) + 1;
//    }
//
//    private static void setPidCounter(AddressingServer server) {
//        long nextPID = computeNextPID(server);
//        server.setPidCounter(nextPID);
//        System.out.printf("Set PID counter to highest PID currently registered: %d%n", nextPID);
//    }

    /**
     * Handles full promotion steps for a replica becoming the new Primary.
     * @param server the AddressingServer instance being promoted.
     */
    public static void promoteSelf(AddressingServer server) {
        System.out.println("Promoting this addressing server REPLICA to PRIMARY...");
        // Update config to reflect PRIMARY status
        server.getConfig().setRole(ServerRole.PRIMARY);
        // Update the internal registry to reflect the new leadership status
        server.getAddrServerRegistry().getRecords().get(server.getConfig().getPID()).setRole(ServerRole.PRIMARY);
        // Set internal PID generator to ensure no active processes have their PID re-assigned.
        server.setPidCounterToNetworkMax();
        // Update server primary connection details.
        server.setPrimaryPeerPort(server.getConfig().getReplicaPort());
        server.setPrimaryHostAddress(server.getConfig().getHostAddress());
    }

    public static void promotePeer(AddressingServer server, AddrServerRecord record) {
        System.out.println("Promoting an separate network process from REPLICA to PRIMARY...");
        record.setRole(ServerRole.PRIMARY);
        server.setPrimaryPeerPort(record.getPeerPort());
        server.setPrimaryHostAddress(record.getHostAddress());
    }

}

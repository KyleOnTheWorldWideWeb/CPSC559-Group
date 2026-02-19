package io.github.cpsc559.team16.addressingserver;


/* My idea with this class is that it will only be instantiated when the addressing server has failed, and will only exist
* until the election cycle has completed. Once the election is complete and a new Primary has been elected, the object will be destroyed.
*
* We can definitely do something else entirely, but I think it's good to destroy all information associated with a previous election.
*
*/


import io.github.cpsc559.team16.common.dto.ServerRole;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;

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
        server.getConfig().setRole(ServerRole.PRIMARY);
        server.getAddrServerRegistry().getRecords().get(server.getConfig().getPID()).setRole(ServerRole.PRIMARY);
        server.setPidCounterToNetworkMax();
        // Update server primary connection details.
        // Pretty sure this is redundant, but I need to look at all of Coles code first.
        server.setPrimaryPeerPort(server.getConfig().getReplicaPort());
        server.setPrimaryHostAddress(server.getConfig().getHostAddress());

    }

    public static void promotePeer(AddressingServer server, AddrServerRecord record) {
        System.out.println("Promoting a separate addressing server REPLICA to PRIMARY...");
        record.setRole(ServerRole.PRIMARY);
        // Update internal address to reflect that of the new primary addressing server
        server.setPrimaryPeerPort(record.getPeerPort());
        server.setPrimaryHostAddress(record.getHostAddress());
        // We can extend this method later to do:
        // - broadcast UPDATE message "All your base are belong to us"

    }

}

package io.github.cpsc559.team16.addressingserver;


/* My idea with this class is that it will only be instantiated when the addressing server has failed, and will only exist
* until the election cycle has completed. Once the election is complete and a new Primary has been elected, the object will be destroyed.
*
* We can definitely do something else entirely, but I think it's good to destroy all information associated with a previous election.
*
*/

public class ElectionHelper {

    private AddressingServer server;

    public ElectionHelper(AddressingServer server) {

    }

    /**
     * Computes the next available PID by scanning both registries.
     * Called during promotion to PRIMARY.
     *
     * @return the next safe PID to assign.
     */
    public long computeNextPID() {
        long maxChatPID = server.getChatServerRegistry().getRecords().keySet().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        long maxAddrPID = server.getAddrServerRegistry().getRecords().keySet().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        return Math.max(maxChatPID, maxAddrPID) + 1;
    }

    /**
     * Handles full promotion steps for a replica becoming the new Primary.
     * @param server the AddressingServer instance being promoted.
     */
    public void promote(AddressingServer server) {
        System.out.println("Promoting this REPLICA to PRIMARY...");
        server.getConfig().setRole(io.github.cpsc559.team16.common.dto.ServerRole.PRIMARY);

        long nextPID = computeNextPID();
        server.setPidCounter(nextPID);
        System.out.printf("Set PID counter to %d%n", nextPID);

        // We can extend this method later to do:
        // - broadcast UPDATE message "All your base are belong to us"

    }
}

package io.github.cpsc559.team16.common.dto;

/**
 * Represents a login attempt for a client.
 * <p>
 * This class is used to store the username and password of a client login attempt.
 * </p>
 */
public class ClientLoginAttempt {

    /**
     * The username of this login attempt.
     */
    private final String username;

    /**
     * The password of this login attempt.
     */
    private final String password;

    /**
     * Constructs a new {@code ServerRecord} object with the specified parameters, a default
     *
     * @param processID   The unique identifier for this server process.
     * @param hostAddress The network address for the server.
     * @param peerPort    The port used for peer-to-peer communication with other processes.
     * @param clientPort  The port used for communication with client processes.
     */
    public ClientLoginAttempt(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // --- Getters ---

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}

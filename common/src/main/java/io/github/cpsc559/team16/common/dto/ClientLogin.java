package io.github.cpsc559.team16.common.dto;

/**
 * Represents a client login request containing a username and password.
 * Used for authentication purposes in the system.
 * <p>
 * This class is used to encapsulate the login credentials for a client in the system.
 * </p>
 */
public class ClientLogin {

    private final String username;
    private final String password;

    public ClientLogin(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "ClientLogin{" +
                "username='" + username + '\'' +
                ",password='" + password + '\'' +
                '}';
    }

    public ClientLogin fromString() {
        String[] parts = toString().split(",");
        String username = parts[0].split("=")[1].trim();
        String password = parts[1].split("=")[1].trim();
        return new ClientLogin(username, password);
    }
    
}

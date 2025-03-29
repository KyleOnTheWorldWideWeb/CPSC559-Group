package io.github.cpsc559.team16.common.dto;

public class ClientRecord {
    private final String username;
    private final String password;

    public ClientRecord(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
}

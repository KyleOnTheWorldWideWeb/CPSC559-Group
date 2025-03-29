package io.github.cpsc559.team16.common.dto;

public class ClientToken {
    private final String token;

    public ClientToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "ClientToken{" +
                "token='" + token + '\'' +
                '}';
    }
}

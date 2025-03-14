package io.github.cpsc559.team16.addressingserver;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class SessionManager {
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    public String generateSessionToken() {
        return UUID.randomUUID().toString();
    }

    public String createSession(String username) {
        if (sessions.containsKey(username)) {
            return null; // Username already in use
        }
        String token = generateSessionToken();
        sessions.put(username, token);
        return token;
    }

    public String getSessionToken(String username) {
        return sessions.get(username);
    }

    public void invalidateSession(String username) {
        sessions.remove(username);
    }

    public boolean isValidToken(String token) {
        return sessions.containsValue(token);
    }
}
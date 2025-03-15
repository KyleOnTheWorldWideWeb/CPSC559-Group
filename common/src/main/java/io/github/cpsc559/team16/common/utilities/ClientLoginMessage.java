package io.github.cpsc559.team16.common.utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** For serializing of client username.
 * 
 * @param username the client's username
 * @see ClientLoginMesage.toJson
 * @see ClientLoginMessage.fromJson
 */
public class ClientLoginMessage {
    private String username;
    private String sessionToken = null;

    public ClientLoginMessage() {
    }

    public ClientLoginMessage(String username) {
        this.username = username;
    }
    public ClientLoginMessage(String username, String sessionToken) {
        this.username = username;
        this.sessionToken = sessionToken;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionToken(){
        return sessionToken;

    }
    public void setSessionToken(String sessionToken){
        this.sessionToken = sessionToken;
    }

    /**
     * @param username
     * @return String Serialized Json Object
     */
    public static String toJson(String username) {
        try {
            ClientLoginMessage loginMessage = new ClientLoginMessage(username);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(loginMessage);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String toJson(String username, String sessionToken) {
        try {
            ClientLoginMessage loginMessage = new ClientLoginMessage(username, sessionToken);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(loginMessage);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Method to convert a serialized user login request back to a ClientLoginMessage
     * 
     * @param jsonMessage serialized ClientLoginMessage object
     * @return object ClientLoginMessage
     */
    public static ClientLoginMessage fromJson(String jsonMessage) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonMessage, ClientLoginMessage.class);
        } catch (JsonMappingException e) {
            e.printStackTrace();
            return null;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
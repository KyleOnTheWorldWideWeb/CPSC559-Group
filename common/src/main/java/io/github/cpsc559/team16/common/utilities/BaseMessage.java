package io.github.cpsc559.team16.common.utilities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.UUID;

public abstract class BaseMessage {
    private String sender;
    private String receiver;
    private String messageId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timeSent;

    protected String type;

    public BaseMessage() {
    }

    public BaseMessage(String sender, String receiver) {
        this.sender = sender;
        this.receiver = receiver;
        this.timeSent = new Date();
        this.messageId = UUID.randomUUID().toString();
        this.type = this.getClass().getSimpleName();

    }

    public String getMessageId() {
        return messageId;
    }

    public String getSender() {
        return sender;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Date getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(Date timeSent) {
        this.timeSent = timeSent;
    }

    public String toJson() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static <T extends BaseMessage> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            throw new JsonProcessingException("Input JSON is null or empty") {
            };
        }
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, clazz);
    }

    @Override
    public String toString() {
        return "BaseMessage{" +
                "sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", timeSent=" + timeSent +
                '}';
    }

    public static BaseMessage peekType(String json) {
        try {

            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            if (root.has("type")) {
                String type = root.get("type").asText();
                BaseMessage base = new BaseMessage() {
                }; // anonymous subclass
                base.setType(type);
                return base;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

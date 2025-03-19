package io.github.cpsc559.team16.addressingserver;

import io.github.cpsc559.team16.common.utilities.BaseMessage;

public class ServerUpdateMessage extends BaseMessage {
    private String msgType;  // e.g., "CHAT_SERVER_UPDATE"
    private String payload;  // JSON string representing the update

    public ServerUpdateMessage() {
        super();
    }

    public ServerUpdateMessage(String sender, String receiver, String msgType, String payload) {
        super(sender, receiver);
        this.msgType = msgType;
        this.payload = payload;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "ServerUpdateMessage{" +
                "sender='" + getSender() + '\'' +
                ", receiver='" + getReceiver() + '\'' +
                ", timeSent=" + getTimeSent() +
                ", msgType='" + msgType + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}

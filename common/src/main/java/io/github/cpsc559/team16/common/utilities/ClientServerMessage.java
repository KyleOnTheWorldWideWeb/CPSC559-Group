package io.github.cpsc559.team16.common.utilities;

public class ClientServerMessage extends BaseMessage {
    private int id;
    private String content;

    public ClientServerMessage() {
        super();
        this.content = "";

    }

    public ClientServerMessage(String sender, String receiver, int id, String content) {
        super(sender, receiver);
        this.id = id;
        this.content = (content != null) ? content : "";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "ClientServerMessage{" +
                "sender=" + getSender() +
                ", receiver=" + getReceiver() +
                ", id=" + id +
                ", timeSent=" + getTimeSent() +
                ", content='" + content + "'" +
                '}';
    }
}
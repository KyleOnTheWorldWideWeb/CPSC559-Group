package io.github.cpsc559.team16.utilities;

public class ClientServerMessage extends BaseMessage {
    private String content;

    public ClientServerMessage() {
        super();
    }

    public ClientServerMessage(String sender, String receiver, String content) {
        super(sender, receiver);
        this.content = content;
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
                "sender='" + getSender() + '\'' +
                ", receiver='" + getReceiver() + '\'' +
                ", timeSent=" + getTimeSent() +
                ", content='" + content + '\'' +
                '}';
    }
}

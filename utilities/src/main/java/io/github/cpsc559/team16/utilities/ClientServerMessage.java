package io.github.cpsc559.team16.utilities;

public class ClientServerMessage extends BaseMessage {
    private String content;
    private int clientCounter;

    public ClientServerMessage() {
        super();
    }

    public ClientServerMessage(String sender, String receiver, String content, int clientCounter) {
        super(sender, receiver);
        this.content = content;
        this.clientCounter = clientCounter;
    }

    public ClientServerMessage(String sender, String receiver, String content) {
        super(sender, receiver);
        this.content = content;
        this.clientCounter = 0;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getClientCounter() {
        return clientCounter;
    }

    public void setClientCounter(int clientCounter) {
        this.clientCounter = clientCounter;
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

package io.github.cpsc559.team16.common.utilities;

public class ServerServerMessage extends BaseMessage {
    private String content;
    private String command;

    public ServerServerMessage() {
        super();
    }

    public ServerServerMessage(String sender, String receiver,
                               String command,
                               String payload) {
        super(sender, receiver);
        this.command = command;

        this.content = payload;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }

    @Override
    public String toString() {
        return "ServerServerMessage{" +
                "sender='" + getSender() + '\'' +
                ", receiver='" + getReceiver() + '\'' +
                ", timeSent=" + getTimeSent() +
                ", content='" + content + '\'' +
                '}';
    }
}
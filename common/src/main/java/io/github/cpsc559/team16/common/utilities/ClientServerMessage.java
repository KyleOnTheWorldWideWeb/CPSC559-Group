package io.github.cpsc559.team16.common.utilities;

public class ClientServerMessage extends BaseMessage {
    private VectorTimestamp vectorTimestamp;
    private int id;
    private String content;
    private String command;

    public ClientServerMessage() {
        super();
        this.content = "";
        this.command = "CHAT";
    }
    public ClientServerMessage(String sender, String receiver, int id, String content) {
        super(sender, receiver);
        this.id = id;
        this.content = (content != null) ? content : "";
        this.command = "CHAT";
    }

    public ClientServerMessage(String sender, String receiver, int id, String content,VectorTimestamp vectorTimestamp) {
        super(sender, receiver);
        this.id = id;
        this.content = (content != null) ? content : "";
        this.command = "CHAT";
        this.vectorTimestamp = vectorTimestamp;
    }
    public VectorTimestamp getVectorTimestamp() {
        return vectorTimestamp;
    }

    public void setVectorTimestamp(VectorTimestamp vectorTimestamp) {
        this.vectorTimestamp = vectorTimestamp;
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

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    @Override
    public String toString() {
        return "ClientServerMessage{" +
                "sender=" + getSender() +
                ", receiver=" + getReceiver() +
                ", id=" + id +
                ", timeSent=" + getTimeSent() +
                ", command=" + command +
                ", content='" + content + "'" +
                '}';
    }
}

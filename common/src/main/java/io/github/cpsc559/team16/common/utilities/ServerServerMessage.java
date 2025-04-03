package io.github.cpsc559.team16.common.utilities; 

public class ServerServerMessage extends BaseMessage {
    private VectorTimestamp vectorTimestamp = new VectorTimestamp();
    private String content;
    private String command;

    public ServerServerMessage() {
        super();
    }

    public ServerServerMessage(String sender, String receiver,
            String command,
            String payload,
            VectorTimestamp vectorTimestamp) {
        super(sender, receiver);
        this.command = command;
        this.content = payload;
        this.vectorTimestamp = vectorTimestamp;
    }
    public VectorTimestamp getVectorTimestamp() {
        return vectorTimestamp;
    }

    public void setVectorTimestamp(VectorTimestamp vectorTimestamp) {
        this.vectorTimestamp = vectorTimestamp;
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
                ", Timestamp='" + getVectorTimestamp() + '\'' +
                '}';
    }
}
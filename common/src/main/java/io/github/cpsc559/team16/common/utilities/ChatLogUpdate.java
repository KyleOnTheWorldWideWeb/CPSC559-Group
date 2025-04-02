package io.github.cpsc559.team16.common.utilities;

import java.util.List;

public class ChatLogUpdate extends BaseMessage {
    private List<ClientServerMessage> chatLog;

    public ChatLogUpdate() {
        super();
    }

    public ChatLogUpdate(String sender, String receiver, List<ClientServerMessage> chatLog) {
        super(sender, receiver);
        this.chatLog = chatLog;
    }

    public List<ClientServerMessage> getChatLog() {
        return chatLog;
    }

    public void setChatLog(List<ClientServerMessage> chatLog) {
        this.chatLog = chatLog;
    }

    @Override
    public String toString() {
        return "ChatLogUpdate{" +
                "sender=" + getSender() +
                ", receiver=" + getReceiver() +
                ", chatLog=" + chatLog +
                '}';
    }
}

package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.dto.AddrServerRecord;
import io.github.cpsc559.team16.common.dto.ChatServerRecord;
import io.github.cpsc559.team16.common.dto.ServerRecord;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.messaging.MessageTypes;
import io.github.cpsc559.team16.common.messaging.ObjectTypes;
import io.github.cpsc559.team16.common.messaging.UpdateMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
public class PendingMessage<T> {
    public final BaseAddrServerMessage<T> message;
    public final Set<Long> pendingReplicaPIDs = new CopyOnWriteArraySet<>();
    public final NIOMessageChannel requestChannel;
    private final BroadcastManager broadcastManager;
    private final CompletionCallback onComplete;

    // Constructor without broadcast manager
    public PendingMessage(BaseAddrServerMessage<T> message, Set<Long> replicaPIDs, NIOMessageChannel requestChannel,  CompletionCallback onComplete) {
        this(message, replicaPIDs, requestChannel, null, onComplete);
    }

    // Overloaded constructor with broadcast manager
    public PendingMessage(BaseAddrServerMessage<T> message, Set<Long> replicaPIDs,
                          NIOMessageChannel requestChannel, BroadcastManager broadcastManager, CompletionCallback onComplete) {
        this.message = message;
        this.pendingReplicaPIDs.addAll(replicaPIDs);
        this.requestChannel = requestChannel;
        this.broadcastManager = broadcastManager;
        this.onComplete = onComplete;
    }

    public void respondToRequester() throws IOException {
        try {
            // If the broadcastManager was passed in, it's because we want to broadcast
            String json = message.toJson();
            requestChannel.sendMessage(json);
        } catch (JsonProcessingException j) {
            System.err.println("Failed to serialize PendingMessage: " + this.message);
        } catch (IOException e) {
            System.err.println("Failed to respond to requester for message: " + e.getMessage());
            throw e;
        }
    }
//
//    private void broadcast() {
//        if (broadcastManager == null) return;
//
//        switch (message.getObjectType()) {
//            case ObjectTypes.CHAT_SERVER_RECORD -> {
//                if (message instanceof UpdateMessage<?> updateMsg &&
//                        message.getPayload() instanceof ChatServerRecord) {
//                    @SuppressWarnings("unchecked")
//                    UpdateMessage<ChatServerRecord> casted = (UpdateMessage<ChatServerRecord>) updateMsg;
//                    broadcastManager.broadcastServerRecordToChatServers(casted);
//                }
//            }
//            case ObjectTypes.ADDR_SERVER_RECORD -> {
//                if (message instanceof UpdateMessage<?> updateMsg &&
//                        message.getPayload() instanceof AddrServerRecord) {
//                    @SuppressWarnings("unchecked")
//                    UpdateMessage<AddrServerRecord> casted = (UpdateMessage<AddrServerRecord>) updateMsg;
//                    broadcastManager.broadcastServerRecordToChatServers(casted);
//                }
//            }
//            default -> System.err.println("Unrecognized message object type: " + message.getObjectType());
//        }
//    }
//
//
//    private boolean shouldBroadcast() {
//        // Add your logic here for whether this message type warrants a broadcast
//        return message.getMsgType() == MessageTypes.UPDATE &&
//                message.getObjectType().equals("ChatServerRecord");
//    }

    public NIOMessageChannel getRequestChannel() {
        return requestChannel;
    }

    public void removePendingReplica(Long replicaPID) {
        pendingReplicaPIDs.remove(replicaPID);
    }

    public boolean isComplete() {
        return pendingReplicaPIDs.isEmpty();
    }
}


package io.github.cpsc559.team16.addressingserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.messaging.BaseAddrServerMessage;
import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
public class PendingEvent<T> {
    public final BaseAddrServerMessage<T> message;
    public final Set<Long> pendingReplicaPIDs;
    public final NIOMessageChannel requestChannel;
    private final BroadcastManager broadcastManager;
    private final CompletionCallback onComplete;

    //  TODO - idea! add flag to PendingEvent and set it for any event that is a "retry". If that goes stale, deny the request
    private final int iterationNumber;

    private final long creationTime; // Timestamp of creation

    // Constructor without broadcast manager
    public PendingEvent(BaseAddrServerMessage<T> message, Set<Long> replicaPIDs,
                        NIOMessageChannel requestChannel, CompletionCallback onComplete) {
        this.message = message;
        this.pendingReplicaPIDs = new CopyOnWriteArraySet<>(replicaPIDs);
        this.requestChannel = requestChannel;
        this.broadcastManager = null;
        this.onComplete = onComplete;
        this.creationTime = System.currentTimeMillis(); // Capture time of creation
        this.iterationNumber = 0;
    }

    // Overloaded constructor with broadcast manager
    public PendingEvent(BaseAddrServerMessage<T> message, Set<Long> replicaPIDs,
                        NIOMessageChannel requestChannel, BroadcastManager broadcastManager, short iterationNumber, CompletionCallback onComplete) {
        this.message = message;
        this.pendingReplicaPIDs = new CopyOnWriteArraySet<>(replicaPIDs);
        this.requestChannel = requestChannel;
        this.broadcastManager = broadcastManager;
        this.onComplete = onComplete;
        this.creationTime = System.currentTimeMillis(); // Capture time of creation
        this.iterationNumber = iterationNumber;
    }

    public void respondToRequester() throws IOException {
        try {
            // If the broadcastManager was passed in, it's because we want to broadcast
            String json = message.toJson();
            requestChannel.sendMessage(json);
            /*
             * This triggers an event that we declared earlier: consisting of any actions that needed to happen
             * after replicas synchronized their states so that a response could be given to the requester.
             */
            if (onComplete != null) {
                onComplete.run();
            }

        } catch (JsonProcessingException j) {
            System.err.println("Failed to serialize PendingEvent: " + this.message);
        } catch (IOException e) {
            System.err.println("Failed to respond to requester with PID: " + requestChannel.getServerPID());
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

    public long getCreationTime() {
        return creationTime;
    }

    public void removePendingReplica(Long replicaPID) {
        pendingReplicaPIDs.remove(replicaPID);
    }

    public boolean isComplete() {
        return pendingReplicaPIDs.isEmpty();
    }
}


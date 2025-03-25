package io.github.cpsc559.team16.chatserver;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ChatLog;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;

@SuppressWarnings("unused")
class ClientHandler implements ConnectionHandler {

    private static final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();
    private static final Set<String> registeredUsernames = ConcurrentHashMap.newKeySet();
    private static List<Integer> cachedClosestPeers = new ArrayList<>();
    private static long lastClosestPeersUpdate = 0;
    private static final long CACHE_TTL_MS = 10_000; // optional: 10s TTL before re-evaluating

    public static final int DEBUG_LEVEL = Integer.parseInt(System.getenv().getOrDefault("DEBUG_LEVEL", "5"));

    private static final int DEBUG_NONE = 0;
    private static final int DEBUG_BASIC = 1;
    private static final int DEBUG_NORMAL = 2;
    private static final int DEBUG_DETAILED = 3;
    private static final int DEBUG_LOW_LEVEL = 4;
    private static final int DEBUG_EXTREME = 5;

    private static void debug(int level, String message) {
        if (level <= DEBUG_LEVEL) {
            String prefix = switch (level) {
                case 1 -> "[BASIC] ";
                case 2 -> "[NORMAL] ";
                case 3 -> "[DETAILED] ";
                case 4 -> "[LOW_LEVEL] ";
                case 5 -> "[EXTREME] ";
                default -> "[INFO] ";
            };
            System.out.println(prefix + message);
        }
    }

    public void handle(BaseMessage message, ConnectionContext ctx, SelectionKey key) {
        if (!(message instanceof ClientServerMessage msg)) {
            System.err.println("Invalid message type for CLIENT");
            return;
        }

        // Handle REGISTER command
        if ("REGISTER".equalsIgnoreCase(msg.getCommand())) {
            String desiredUsername = msg.getSender().trim();
            try {
                if (desiredUsername.isEmpty()) {
                    sendError("server", "Username cannot be empty", ctx, key);
                    return;
                }

                if (registeredUsernames.contains(desiredUsername)) {
                    sendError("server", "Username already taken", ctx, key);
                    return;
                }

                ctx.username = desiredUsername;
                registeredUsernames.add(desiredUsername);

                debug(DEBUG_BASIC, "[REGISTER] User registered: " + desiredUsername);
                WriteUtils.enqueueResponse(ctx, key, msg.toJson() + "\n");
                key.selector().wakeup();
                return;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (ctx.type == ChatServer.ConnectionType.CLIENT && ctx.username == null) {
            try {
                sendError("server", "ERROR: Must register username first.\n", ctx, key);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return;
        }

        if (seenMessageIds.contains(msg.getMessageId())) {
            debug(DEBUG_NORMAL, "[CLIENT] Duplicate message skipped: " + msg.getMessageId());
            return;
        }

        debug(DEBUG_NORMAL, "[CLIENT] ID=" + msg.getId() + ", Content=" + msg.getContent());

        ChatLog chatlog = ChatServer.getChatLog();
        chatlog.appendMessage(msg);

        try {
            sendToAllClients(msg, key.selector());
            seenMessageIds.add(msg.getMessageId());
            gossipToClosestPeers(msg, 1);
        } catch (Exception e) {
            System.err.println("Failed to serialize response: " + e.getMessage());
        }
    }

    public static void sendToAllClients(ClientServerMessage msg, Selector selector) {
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid() || !(key.channel() instanceof SocketChannel))
                continue;

            ConnectionContext ctx = (ConnectionContext) key.attachment();
            if (ctx.type == ChatServer.ConnectionType.CLIENT) {
                try {
                    WriteUtils.enqueueResponse(ctx, key, msg.toJson() + "\n");
                    selector.wakeup();
                    debug(DEBUG_LOW_LEVEL, "Sent message to client: " + ctx.username);
                } catch (Exception e) {
                    System.err.println("Failed to send to client: " + e.getMessage());
                }
            }
        }
    }

    public static void unregisterUsername(String username) {
        registeredUsernames.remove(username);
        debug(DEBUG_BASIC, "[DISCONNECT] User unregistered: " + username);
    }

    private void sendError(String sender, String errorMessage, ConnectionContext ctx, SelectionKey key)
            throws JsonProcessingException {
        ClientServerMessage errorMsg = new ClientServerMessage(sender, "client", -1, "ERROR: " + errorMessage);
        errorMsg.setCommand("ERROR");
        WriteUtils.enqueueResponse(ctx, key, errorMsg.toJson() + "\n");
        key.selector().wakeup();
        debug(DEBUG_DETAILED, "Sent error to client: " + errorMessage);
    }

    public static void gossipToClosestPeers(ClientServerMessage msg, int n) {
        Selector selector = ChatServer.getSelector();
        Map<Integer, ConnectionContext> peers = ChatServer.getConnectedPeers();
        List<Integer> targets = getValidatedClosestPeers(n);

        debug(DEBUG_NORMAL, "[GOSSIP] Attempting to gossip to " + targets.size() + " closest peers.");
        int sent = 0;

        for (int pid : targets) {
            ConnectionContext ctx = peers.get(pid);
            if (ctx == null || ctx.type != ChatServer.ConnectionType.SERVER)
                continue;

            for (SelectionKey key : selector.keys()) {
                if (key.attachment() == ctx && key.channel().isOpen()) {
                    try {
                        WriteUtils.enqueueResponse(ctx, key, msg.toJson() + "\n");
                        selector.wakeup();
                        debug(DEBUG_LOW_LEVEL, "[GOSSIP] Sent message to peer PID=" + pid);
                        sent++;
                        break;
                    } catch (Exception e) {
                        System.err.println("Gossip failed to peer " + pid + ": " + e.getMessage());
                    }
                }
            }
        }

        debug(DEBUG_BASIC, "[GOSSIP] Sent to " + sent + " peers out of " + n + " requested.");
    }

    private static List<Integer> getValidatedClosestPeers(int n) {
        int selfPid = ChatServer.getID();
        Map<Integer, ConnectionContext> peers = ChatServer.getConnectedPeers();
        System.out.println("peers");
        System.out.println(peers);

        boolean needsRefresh = cachedClosestPeers.isEmpty()
                || cachedClosestPeers.size() < n
                || System.currentTimeMillis() - lastClosestPeersUpdate > CACHE_TTL_MS
                || !cachedClosestPeers.stream().allMatch(peers::containsKey);

        if (needsRefresh) {
            debug(DEBUG_DETAILED, "[GOSSIP] Refreshing cached closest peers list...");
            List<Integer> sortedPids = new ArrayList<>(peers.keySet());
            sortedPids.add(selfPid);
            sortedPids.sort(Integer::compareTo);

            int selfIndex = sortedPids.indexOf(selfPid);
            List<Integer> updatedList = new ArrayList<>();
            int total = sortedPids.size();

            for (int i = 1; i < total && updatedList.size() < n; i++) {
                int index = (selfIndex + i) % total;
                int pid = sortedPids.get(index);
                if (pid != selfPid && peers.containsKey(pid)) {
                    updatedList.add(pid);
                }
            }

            cachedClosestPeers = updatedList;
            lastClosestPeersUpdate = System.currentTimeMillis();
            debug(DEBUG_LOW_LEVEL, "[GOSSIP] Updated closest peers: " + cachedClosestPeers);
        } else {
            debug(DEBUG_EXTREME, "[GOSSIP] Using cached closest peers: " + cachedClosestPeers);
        }

        return cachedClosestPeers;
    }
}

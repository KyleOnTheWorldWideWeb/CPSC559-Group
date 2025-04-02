package io.github.cpsc559.team16.common.utilities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.json.*;

/**
 * Manages an append-only chat log for storing {@link ClientServerMessage}
 * objects.
 * <p>
 * Messages are stored in JSON format in a log file, and their positions
 * are indexed in an index file for fast retrieval and ordering without
 * modifying the log.
 * Duplicate messages are prevented using SHA-256 hashing.
 * </p>
 */
public class ChatLog {
    private final String logFile;
    private final String indexFile;
    private final Map<String, MessageMetadata> messageIndex;
    private final Set<String> messageHashes; // Set to track unique message hashes
    private final TreeMap<Long, List<String>> timestampIndex; // <- new TreeMap

    /**
     * Constructs a {@code ChatLog} instance and loads the existing index file.
     *
     * @param logFile   The path to the append-only message log file.
     * @param indexFile The path to the index file used for message ordering.
     */
    public ChatLog(String logFile, String indexFile) {
        this.logFile = logFile;
        this.indexFile = indexFile;
        this.messageIndex = new HashMap<>();
        this.messageHashes = new HashSet<>();
        this.timestampIndex = new TreeMap<>(); // <- initialize TreeMap
        ensureLogFileExists();
        loadIndexFile();
    }

    /**
     * Appends a {@link ClientServerMessage} to the log file and updates the index.
     * <p>
     * A SHA-256 hash is computed for the message to prevent duplicates.
     * If the message hash already exists in the index, the message is not appended
     * again.
     * </p>
     *
     * @param message The {@link ClientServerMessage} object to be stored.
     */
    public void appendMessage(ClientServerMessage message) {
        try {

            String messageId = message.getMessageId();
            if (messageId == null || messageId.isEmpty()) {
                System.err.println("Message ID is null or empty — cannot append.");
                return;
            }

            String messageJson = message.toJson();
            String messageHash = computeSHA256(messageJson);

            // Check if message already exists (duplicate check)
            if (messageHashes.contains(messageHash))
                return;

            try (RandomAccessFile logFileWriter = new RandomAccessFile(logFile, "rw")) {
                long position = logFileWriter.length();
                long timestamp = message.getTimeSent().getTime(); // Use original message timestamp

                logFileWriter.seek(position);
                logFileWriter.write((messageJson + "\n").getBytes(StandardCharsets.UTF_8));

                // Add to index and hash set
                messageIndex.put(messageId, new MessageMetadata(position, timestamp, messageHash));
                messageHashes.add(messageHash);

                timestampIndex.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(messageId);

                updateIndexFile();
            }

        } catch (IOException e) {
            System.err.println("Error writing to chat log: " + e.getMessage());
        }
    }

    /**
     * Displays messages in order based on their timestamps stored in the index.
     * <p>
     * Messages are retrieved from the log using their byte positions stored in the
     * index file,
     * ensuring efficient lookup and ordering.
     * </p>
     */
    public void displayMessagesInOrder() {
        try (RandomAccessFile logReader = new RandomAccessFile(logFile, "r")) {
            for (Map.Entry<Long, List<String>> entry : timestampIndex.entrySet()) {
                for (String messageId : entry.getValue()) {
                    MessageMetadata metadata = messageIndex.get(messageId);
                    try {
                        logReader.seek(metadata.position);
                        String messageJson = logReader.readLine();

                        if (messageJson == null || messageJson.isBlank())
                            continue;

                        ClientServerMessage message = BaseMessage.fromJson(messageJson, ClientServerMessage.class);
                        System.out.println("Message ID: " + messageId);
                        System.out.println("Timestamp : " + metadata.timestamp);
                        System.out.println("Contents  : " + message.toJson());
                        System.out.println("=".repeat(50));

                    } catch (IOException e) {
                        System.err.println("Failed to read message ID " + messageId + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error opening log file: " + e.getMessage());
        }
    }

    /**
     * Loads the message index from the index file into memory.
     * If the file is missing or empty, it initializes an empty index.
     */
    private void loadIndexFile() {
        try {
            File file = new File(indexFile);
            if (!file.exists() || file.length() == 0) {
                System.out.println("Index file missing or empty. Creating a new index.");
                saveEmptyIndex(); // Ensures a valid empty JSON file
                return;
            }

            String content = new String(Files.readAllBytes(Paths.get(indexFile)), StandardCharsets.UTF_8);
            JSONObject indexJson = new JSONObject(content);

            for (String key : indexJson.keySet()) {
                JSONObject metadata = indexJson.getJSONObject(key);
                long position = metadata.getLong("position");
                long timestamp = metadata.getLong("timestamp");
                String messageHash = metadata.getString("hash");

                messageIndex.put(key, new MessageMetadata(position, timestamp, messageHash));
                messageHashes.add(messageHash);
                timestampIndex.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(key);
            }
        } catch (IOException | JSONException e) {
            System.err.println("Error loading index file. Resetting index: " + e.getMessage());
            saveEmptyIndex(); // Reset to an empty JSON file
        }
    }

    /**
     * Saves an empty JSON object to the index file to prevent JSON parsing errors.
     */
    private void saveEmptyIndex() {
        try (FileWriter file = new FileWriter(indexFile)) {
            file.write("{}"); // Empty JSON object
            file.flush();
        } catch (IOException e) {
            System.err.println("Failed to create an empty index file: " + e.getMessage());
        }
    }

    /**
     * Updates the index file with the latest message positions, timestamps, and
     * hashes.
     * <p>
     * This method ensures that the index remains consistent and reflects the
     * current
     * state of the log file, allowing messages to be retrieved efficiently.
     * </p>
     */
    private void updateIndexFile() {
        try (FileWriter file = new FileWriter(indexFile)) {
            JSONObject indexJson = new JSONObject();
            for (Map.Entry<String, MessageMetadata> entry : messageIndex.entrySet()) {
                JSONObject metadata = new JSONObject();
                metadata.put("position", entry.getValue().position);
                metadata.put("timestamp", entry.getValue().timestamp);
                metadata.put("hash", entry.getValue().messageHash); // Store hash in index
                indexJson.put(entry.getKey(), metadata);
            }
            file.write(indexJson.toString(4));
        } catch (IOException e) {
            System.err.println("Error updating index file: " + e.getMessage());
        }
    }

    /**
     * Computes the SHA-256 hash of a given message string.
     *
     * @param message The message content to hash.
     * @return A hexadecimal string representing the SHA-256 hash of the message.
     */
    private static String computeSHA256(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public void merge(ChatLogUpdate chatLogUpdate) {
        if (chatLogUpdate == null || chatLogUpdate.getChatLog() == null) {
            System.err.println("Invalid ChatLogUpdate received.");
            return;
        }

        for (ClientServerMessage message : chatLogUpdate.getChatLog()) {
            appendMessage(message);
        }
        System.out.println("Chat log successfully merged.");
    }

    /**
     * Represents metadata for each message stored in the chat log.
     * <p>
     * This includes the byte position of the message in the log file, the
     * timestamp when the message was added, and the message hash for duplicate
     * detection.
     * </p>
     */
    private static class MessageMetadata {
        /** The byte position of the message in the log file. */
        long position;

        /** The timestamp when the message was appended. */
        long timestamp;

        /** The SHA-256 hash of the message content. */
        String messageHash;

        /**
         * Constructs a {@code MessageMetadata} object.
         *
         * @param position    The byte position in the log file.
         * @param timestamp   The message timestamp.
         * @param messageHash The hash of the message content.
         */
        MessageMetadata(long position, long timestamp, String messageHash) {
            this.position = position;
            this.timestamp = timestamp;
            this.messageHash = messageHash;
        }
    }

    /**
     * Loads an existing message log and validates it against the index file.
     * <p>
     * Ensures that all messages from `chatlog.log` are correctly indexed in
     * `index.json`.
     * If discrepancies are found (e.g., missing or extra entries in `index.json`),
     * they are corrected.
     * </p>
     */
    public void loadExistingLog() {
        try (RandomAccessFile logReader = new RandomAccessFile(logFile, "r")) {
            Map<String, MessageMetadata> rebuiltIndex = new HashMap<>();
            Set<String> rebuiltHashes = new HashSet<>();
            TreeMap<Long, List<String>> rebuiltTimeMap = new TreeMap<>();

            while (logReader.getFilePointer() < logReader.length()) {
                long entryPosition = logReader.getFilePointer();
                String messageJson = logReader.readLine();

                // Deserialize JSON to ClientServerMessage
                ClientServerMessage message = BaseMessage.fromJson(messageJson, ClientServerMessage.class);
                String messageHash = computeSHA256(message.toString());
                String messageId = message.getMessageId();
                long timestamp = message.getTimeSent().getTime();

                // Ensure index consistency
                if (!rebuiltHashes.contains(messageHash)) {
                    rebuiltIndex.put(messageId, new MessageMetadata(entryPosition, timestamp, messageHash));
                    rebuiltHashes.add(messageHash);
                    rebuiltTimeMap.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(messageId);
                }
            }

            // Replace existing index with rebuilt one
            messageIndex.clear();
            messageIndex.putAll(rebuiltIndex);
            messageHashes.clear();
            messageHashes.addAll(rebuiltHashes);
            timestampIndex.clear();
            timestampIndex.putAll(rebuiltTimeMap);

            updateIndexFile();
            System.out.println("Existing log loaded successfully and verified.");
        } catch (IOException e) {
            System.err.println("Error loading existing log: " + e.getMessage());
        }
    }

    /**
     * Ensures that the log file exists. If it does not exist, it creates an empty
     * file.
     */
    private void ensureLogFileExists() {
        try {
            File file = new File(logFile);
            File parentDir = file.getParentFile();
            if (parentDir != null)
                parentDir.mkdirs();
            if (!file.exists()) {
                if (file.createNewFile()) {
                    System.out.println("Created new chat log file: " + logFile);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to create chat log file: " + e.getMessage());
        }
    }

    public String getLastMessagesAsString() {
        return getLastMessagesAsString(50);
    }

    public String getLastMessagesAsString(int count) {
        StringBuilder sb = new StringBuilder();

        try (RandomAccessFile logReader = new RandomAccessFile(logFile, "r")) {
            // Flatten the TreeMap into a list of message IDs
            List<String> allMessageIds = new ArrayList<>();
            for (List<String> ids : timestampIndex.values()) {
                allMessageIds.addAll(ids);
            }

            // Get the last `count` message IDs
            int start = Math.max(0, allMessageIds.size() - count);
            List<String> targetMessageIds = allMessageIds.subList(start, allMessageIds.size());

            for (String messageId : targetMessageIds) {
                MessageMetadata metadata = messageIndex.get(messageId);
                if (metadata == null)
                    continue;

                logReader.seek(metadata.position);
                String messageJson = logReader.readLine();
                if (messageJson == null || messageJson.isBlank())
                    continue;

                sb.append(messageJson).append("\n");
            }

        } catch (IOException e) {
            System.err.println("Error reading recent messages: " + e.getMessage());
        }

        return sb.toString();
    }

}

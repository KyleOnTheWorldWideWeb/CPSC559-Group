package io.github.cpsc559.team16.common.utilities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.json.*;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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
        this.objectMapper = new ObjectMapper();
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
            String messageHash = computeSHA256(message.toString());

            // Check if message already exists (duplicate check)
            if (messageHashes.contains(messageHash)) {
                System.out.println("Duplicate message detected. Skipping addition.");
                return;
            }

            try (RandomAccessFile logFileWriter = new RandomAccessFile(logFile, "rw")) {
                logFileWriter.seek(logFileWriter.length()); // Move to the end of the file

                String messageId = UUID.randomUUID().toString();
                long position = logFileWriter.length();
                long timestamp = System.currentTimeMillis();

                String messageJson = objectMapper.writeValueAsString(message);
                logFileWriter.write((messageJson + "\n").getBytes(StandardCharsets.UTF_8));

                // Update index
                messageIndex.put(messageId, new MessageMetadata(position, timestamp, messageHash));
                messageHashes.add(messageHash);
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
            messageIndex.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().timestamp)) // Sort messages by timestamp
                    .forEach(entry -> {
                        try {
                            logReader.seek(entry.getValue().position);
                            String messageJson = logReader.readLine();
                            ClientServerMessage message = objectMapper.readValue(messageJson,
                                    ClientServerMessage.class);
                            System.out.println(message);
                        } catch (IOException e) {
                            System.err.println("Error reading message: " + e.getMessage());
                        }
                    });
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
                String messageHash = metadata.getString("hash");
                messageIndex.put(key,
                        new MessageMetadata(metadata.getLong("position"), metadata.getLong("timestamp"), messageHash));
                messageHashes.add(messageHash); // Store existing hashes
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

            while (logReader.getFilePointer() < logReader.length()) {
                long entryPosition = logReader.getFilePointer();
                String messageJson = logReader.readLine();

                // Deserialize JSON to ClientServerMessage
                ClientServerMessage message = objectMapper.readValue(messageJson, ClientServerMessage.class);
                String messageHash = computeSHA256(message.toString());

                // Ensure index consistency
                if (!rebuiltHashes.contains(messageHash)) {
                    String messageId = UUID.randomUUID().toString();
                    long timestamp = System.currentTimeMillis();

                    rebuiltIndex.put(messageId, new MessageMetadata(entryPosition, timestamp, messageHash));
                    rebuiltHashes.add(messageHash);
                }
            }

            // Replace existing index with rebuilt one
            messageIndex.clear();
            messageIndex.putAll(rebuiltIndex);
            messageHashes.clear();
            messageHashes.addAll(rebuiltHashes);

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

            // Ensure parent directories exist only if they are not null
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            // Ensure parent directories exist
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (created) {
                    System.out.println("Created new chat log file: " + logFile);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to create chat log file: " + e.getMessage());
        }
    }

}

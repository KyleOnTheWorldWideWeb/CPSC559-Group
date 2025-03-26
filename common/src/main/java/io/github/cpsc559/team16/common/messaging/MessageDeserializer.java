package io.github.cpsc559.team16.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cpsc559.team16.common.dto.*;


/**
 * Utility class for handling message deserialization.
 * <p>
 * This class provides static methods for dynamically determining message types
 * and properly deserializing incoming JSON messages.
 * </p>
 */
public class MessageDeserializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dynamically deserializes an incoming JSON message while considering whether a payload exists.
     *
     * @param json The raw JSON message received from the network.
     * @return A deserialized {@code BaseAddrServerMessage<?>} instance with the appropriate payload type.
     */
    public static BaseAddrServerMessage<?> deserializeMessage(String json) {
        try {
            // Step 1: Parse only the msgType and objectType fields
            JsonNode rootNode = objectMapper.readTree(json);
            String msgType = rootNode.has("msgType") ? rootNode.get("msgType").asText() : "UNKNOWN";
            String objectType = rootNode.has("objectType") ? rootNode.get("objectType").asText() : "NONE";

            // Step 2: Determine the correct payload class
            Class<?> payloadClass = ObjectTypes.getPayloadClass(objectType);

            // Step 3: Deserialize the full message with the correct payload type
            return BaseAddrServerMessage.fromJson(json, payloadClass);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to deserialize message: " + e.getMessage());
            return null;
        }
    }


}

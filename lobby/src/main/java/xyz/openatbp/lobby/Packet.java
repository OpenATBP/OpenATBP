package xyz.openatbp.lobby;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Packet {
    private String type = "";
    private JsonNode payload;

    public boolean receive(DataInputStream clientIn) {
        try {
            int textLength = clientIn.readChar(); // needs to be 2 bytes

            // this should catch 0 and -1 (end of stream)
            if (textLength > 2 && textLength <= 8192) {
                byte[] receivedBytes = new byte[textLength];
                clientIn.readFully(receivedBytes);
                String receivedMessage = new String(receivedBytes, StandardCharsets.UTF_8);

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(receivedMessage);
                if (jsonNode.has("req") || jsonNode.has("payload")) {
                    this.type = jsonNode.get("req").asText();
                    this.payload = jsonNode.get("payload");
                    return true;
                }
            }
        } catch (IOException e){
           System.out.println(e);
        }
        return false;
    }

    public boolean send(DataOutputStream clientOut) {
        ObjectMapper objectMapper = new ObjectMapper();
        String payloadString = null;
        try {
            payloadString = objectMapper.writeValueAsString(this.payload);
        } catch (JsonProcessingException e) {
            System.out.println(e);
            return false;
        }

        byte[] sentBytes = payloadString.getBytes(StandardCharsets.UTF_8);
        char sentTextLength = (char) sentBytes.length;

        try {
            clientOut.writeChar(sentTextLength);
            clientOut.write(sentBytes);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public String getType() {
        return type;
    }

    public JsonNode getPayload() {
        return payload;
    }
}

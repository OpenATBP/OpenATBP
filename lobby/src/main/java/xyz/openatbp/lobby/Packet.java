package xyz.openatbp.lobby;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Packet {
    private String type = "";
    private JsonNode payload;

    public boolean receive(DataInputStream clientIn) {
        try {
            int textLength = clientIn.readChar(); // needs to be 2 bytes

            // this should catch 0 and -1 (end of stream)
            if (textLength > 0 && textLength <= 8192) {
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

    public boolean send(DataOutputStream clientOut, String cmd, JsonNode pay) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode mes = objectMapper.createObjectNode();
        mes.set("payload",pay);
        mes.put("cmd",cmd);
        String payloadString = null;
        try {
            payloadString = objectMapper.writeValueAsString(mes);
            System.out.println(payloadString);
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

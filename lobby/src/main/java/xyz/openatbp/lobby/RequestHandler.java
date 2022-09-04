package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RequestHandler {
    private static ObjectMapper objectMapper = new ObjectMapper();
    public RequestHandler(){

    }

    public static JsonNode handleHandshake(JsonNode obj){
        ObjectNode objectNode = objectMapper.createObjectNode();

        objectNode.put("result",true);
        return objectNode;
    }

    public static JsonNode handleLogin(JsonNode obj){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("player",(float)obj.get("auth_id").asDouble());
        objectNode.put("teg_id",obj.get("teg_id").asText());
        objectNode.put("name", obj.get("name").asText());
        return objectNode;
    }

    public static JsonNode handleMatchFound(){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown",30);
        return objectNode;
    }

    public static JsonNode handleTeamUpdate(ArrayNode team){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("players",team);
        objectNode.put("team","BLUE");
        return objectNode;
    }

    public static JsonNode handleAvatarChange(JsonNode obj){
        ObjectNode objectNode = objectMapper.createObjectNode();
        ArrayNode playerObjArray = objectMapper.createArrayNode();
        ObjectNode playerObj = objectMapper.createObjectNode();
        playerObj.put("name", "Spooky Umbrella");
        playerObj.put("player", (float)0001);
        playerObj.put("teg_id", "SpookyUmbrella");
        playerObj.put("avatar", obj.get("name").asText());
        playerObj.put("is_ready",false);
        playerObjArray.add(playerObj);
        objectNode.set("players",playerObjArray);
        objectNode.put("team","BLUE");
        return objectNode;
    }

    public static JsonNode handleReady(){
        ObjectNode objectNode = objectMapper.createObjectNode();
        ArrayNode playerObjArray = objectMapper.createArrayNode();
        ObjectNode playerObj = objectMapper.createObjectNode();
        playerObj.put("name", "Spooky Umbrella");
        playerObj.put("player", (float)0001);
        playerObj.put("teg_id", "SpookyUmbrella");
        playerObj.put("avatar", "lich");
        playerObj.put("is_ready",true);
        playerObjArray.add(playerObj);
        objectNode.set("players",playerObjArray);
        objectNode.put("team","BLUE");
        return objectNode;
    }

    public static JsonNode handleQueueUpdate(int size){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("size", size);
        return objectNode;
    }

    public static JsonNode handleGameReady(){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown", 5);
        objectNode.put("ip", "127.0.0.1");
        objectNode.put("port", 9993);
        objectNode.put("policy_port", 843);
        objectNode.put("room_id", "notlobby");
        objectNode.put("team", "BLUE");
        objectNode.put("password", "abc123");
        return objectNode;
    }
}

package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RequestHandler {
    private static ObjectMapper objectMapper = new ObjectMapper();
    public RequestHandler(){

    }

    public static JsonNode handleHandshake(JsonNode obj){ //Gives success to client
        ObjectNode objectNode = objectMapper.createObjectNode();

        objectNode.put("result",true);
        return objectNode;
    }

    public static JsonNode handleLogin(JsonNode obj){ //Gives client login info when user logs in
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("player",(float)obj.get("auth_id").asDouble());
        objectNode.put("teg_id",obj.get("teg_id").asText());
        objectNode.put("name", obj.get("name").asText());
        return objectNode;
    }

    public static JsonNode handleMatchFound(){ //When a match is found, gives 30 seconds to players to select champs
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown",30);
        return objectNode;
    }

    public static JsonNode handleTeamUpdate(ArrayNode team){ //Updates a team
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("players",team);
        objectNode.put("team","BLUE"); //Hardcoded, will need to change when I can test more people
        return objectNode;
    }

    @Deprecated public static JsonNode handleAvatarChange(JsonNode obj){ //Hardcoded avatar change
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

    @Deprecated public static JsonNode handleReady(){ //Hardcoded ready up
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

    public static JsonNode handleQueueUpdate(int size){ //Updates the amount of players in a queue (GUI)
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("size", size);
        return objectNode;
    }

    public static JsonNode handleGameReady(){ //When the game is ready, sends players to the game server. (Hard coded)
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown", 5);
        objectNode.put("ip", "127.0.0.1");
        objectNode.put("port", 9933);
        objectNode.put("policy_port", 843);
        objectNode.put("room_id", "notlobby");
        objectNode.put("team", "BLUE");
        objectNode.put("password", "abc123");
        return objectNode;
    }

    public static JsonNode handleInvite(Queue q){ //Sends a player an invite with the inviter and queue information
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("name", q.getPartyLeader().getName());
        objectNode.put("player", q.getPartyLeader().getPid());
        objectNode.put("act", q.getType());
        objectNode.put("vs", q.isPvP());
        objectNode.put("team", q.getPartyLeader().getUsername());
        return objectNode;
    }

    public static JsonNode handleTeamJoin(Queue q){ //Joins a team when accepting an invite
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("players",q.getPlayerObjects());
        objectNode.put("team",q.getPartyLeader().getUsername());
        return objectNode;
    }

    public static JsonNode handleInviteAccept(){ //Tells the client that the invite was a success so it can switch to the right menu
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("result", "success");
        return objectNode;
    }

    public static JsonNode handleInviteDecline(String decliner){ // Tells the host that the player declined
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("player",decliner);
        return objectNode;
    }

    public static JsonNode handleDisband(){ //Disbands team with the reason disconnect
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("reason", "disconnect");
        return objectNode;
    }
}

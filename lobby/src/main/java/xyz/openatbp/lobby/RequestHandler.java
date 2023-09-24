package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RequestHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public RequestHandler(){

    }

    public static JsonNode handleHandshake(boolean successful){ //Gives success to client
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("result",successful);
        return objectNode;
    }

    public static JsonNode handleLogin(JsonNode obj, int guestNum){ //Gives client login info when user logs in
        ObjectNode objectNode = objectMapper.createObjectNode();
        if(obj.get("name").asText().contains("Guest")){
            System.out.println("Handling guest!");
            objectNode.put("player",(double)guestNum);
        }else{
            objectNode.put("player",(float)obj.get("auth_id").asDouble());
        }
        objectNode.put("teg_id",obj.get("teg_id").asText());
        objectNode.put("name", obj.get("name").asText().replace("%20"," "));
        return objectNode;
    }

    public static JsonNode handleMatchFound(){ //When a match is found, gives 30 seconds to players to select champs
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown",30);
        return objectNode;
    }

    public static JsonNode handleTeamUpdate(ArrayNode team, String teamStr){ //Updates a team
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.set("players",team);
        objectNode.put("team",teamStr);
        return objectNode;
    }

    public static JsonNode handleQueueUpdate(int size){ //Updates the amount of players in a queue (GUI)
        int newSize = Math.min(size, 3);
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("size", newSize);
        return objectNode;
    }

    public static JsonNode handleGameReady(Player partyLeader, String teamStr, String type){ //When the game is ready, sends players to the game server.
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("countdown", 5);
        objectNode.put("ip", Config.getString("sfs2x.ip"));
        objectNode.put("port", Config.getString("sfs2x.port"));
        objectNode.put("policy_port", Config.getString("sockpol.port"));
        String[] split = type.split("_");
        StringBuilder newString = new StringBuilder();
        if(partyLeader.getUsername().equalsIgnoreCase("guest")){
            newString.append("Guest").append(partyLeader.getPid());
        }else{
            for(int i = 0; i < partyLeader.getUsername().length(); i+=2){
                newString.append(partyLeader.getUsername().charAt(i));
            }
        }
        String roomId = split[split.length-1] + "_" + newString;
        objectNode.put("room_id", roomId);
        String team;
        //if(index<=2) team="0";
        //else team = "1";
        objectNode.put("team", teamStr); //Change based on players
        objectNode.put("password", "");
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

    public static JsonNode handleChatMessage(Player p, String message){
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("name",p.getName());
        objectNode.put("teg_id",p.getUsername());
        objectNode.put("message_id",Double.valueOf(message));
        return objectNode;
    }
}

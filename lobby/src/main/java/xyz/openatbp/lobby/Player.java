package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Player {

    private float pid;
    private String name;
    private String avatar;
    private String tegid;
    private boolean ready;
    private Socket conn;

    private static ObjectMapper objectMapper = new ObjectMapper();


    public Player(Socket conn, JsonNode playerObj){
        this.conn = conn;
        this.pid = (float)playerObj.get("auth_id").asDouble();
        this.name = playerObj.get("name").asText();
        this.avatar = "unassigned";
        this.tegid = playerObj.get("teg_id").asText();
        this.ready = false;
    }

    public JsonNode toObject(){
        ObjectNode playerObj = objectMapper.createObjectNode();
        playerObj.put("name",this.name);
        playerObj.put("player",this.pid);
        playerObj.put("teg_id",this.tegid);
        playerObj.put("avatar",this.avatar);
        playerObj.put("is_ready",this.ready);

        return playerObj;
    }

    public void setAvatar(JsonNode node){
        this.avatar = node.get("name").asText();
    }

    public void setReady(){
        this.ready = true;
    }

    public DataOutputStream getOutputStream(){
        try{
            return new DataOutputStream(this.conn.getOutputStream());
        }
        catch (IOException e){
            return null;
        }
    }

    public boolean isAddress(String address){
        return this.conn.getRemoteSocketAddress().toString().equals(address);
    }
}

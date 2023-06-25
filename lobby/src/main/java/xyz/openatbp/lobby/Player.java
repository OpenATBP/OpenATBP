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
    private String team;

    private static ObjectMapper objectMapper = new ObjectMapper();


    public Player(Socket conn, JsonNode playerObj){
        this.conn = conn;
        this.pid = (float)playerObj.get("auth_id").asDouble();
        this.name = playerObj.get("name").asText();
        this.avatar = "unassigned";
        this.tegid = playerObj.get("teg_id").asText();
        this.ready = false;
    }

    @Deprecated public JsonNode toObject(){ //Turns Player obj to Json Object
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
    } //Avatar setter

    public void setReady(){
        this.ready = true;
    } //Ready setter

    public DataOutputStream getOutputStream(){ //Gets output stream to send info to client
        try{
            return new DataOutputStream(this.conn.getOutputStream());
        }
        catch (IOException e){
            return null;
        }
    }

    public boolean isAddress(String address){ //Returns if address matches player's address
        return this.conn.getRemoteSocketAddress().toString().equals(address);
    }

    public String getUsername(){ //Returns TEGid
        return this.tegid;
    }

    public String getName() { //Returns display name
        return this.name;
    }

    public float getPid(){ //Returns authid
        return this.pid;
    }

    public String getAvatar(){ // Returns avatar
        return this.avatar;
    }

    public boolean isReady(){ // Returns ready status
        return this.ready;
    }

    public void leaveTeam(){
        this.avatar = "unassigned";
        this.ready = false;
        this.team = null;
    }
    public String getTeam(){
        return this.team;
    }

    public void setTeam(String team){
        this.team = team;
    }
}

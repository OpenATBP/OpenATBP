package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;

public class Queue {
    private ArrayList<Player> players;
    private String type;
    private boolean inGame;
    private boolean pvp;
    private static ObjectMapper objectMapper = new ObjectMapper();

    public Queue(){
        this.players = new ArrayList<Player>();
        this.inGame = false;
    }

    public Queue(ArrayList<Player> players, String type, boolean pvp){
        this.players = new ArrayList<Player>();
        this.inGame = false;
        for(int i = 0; i < players.size(); i++){
            this.players.add(players.get(i));
        }
        this.type = type;
        this.pvp = pvp;
    }

    public Queue(Player p, String type, boolean pvp){
        this.players = new ArrayList<Player>();
        this.players.add(p);
        this.inGame = false;
        this.type = type;
        this.pvp = pvp;
        System.out.println("New queue - Queue size: " + this.getSize());
    }

    public ArrayList<Player> getPlayers(){
        return this.players;
    }

    public boolean findPlayer(Player p){
        for(int i = 0; i < this.players.size(); i++){
            if(this.players.get(i).getUsername().equalsIgnoreCase(p.getUsername())){
                return true;
            }
        }
        return false;
    }

    public boolean findPlayer(String conn){
        for(int i = 0; i < this.players.size(); i++){
            if(this.players.get(i).isAddress(conn)) return true;
        }
        return false;
    }

    private int findPlayerIndex(Player p){
        for(int i = 0; i < this.players.size(); i++){
            if(this.players.get(i).getUsername().equalsIgnoreCase(p.getUsername())){
                return i;
            }
        }
        return -1;
    }

    public void addPlayer(Player p){
        this.players.add(p);
        System.out.println("Add player - Queue size: " + this.getSize());
        this.queueUpdate();
        if(players.size() == 2) this.queueFull();
    }

    public void removePlayer(Player p){
        this.players.remove(this.findPlayerIndex(p));
        if(players.size()>0 && !inGame) this.queueUpdate();
        else if(inGame) this.updateTeam();
    }

    public int getSize(){
        return this.players.size();
    }

    public void queueFull(){
        this.inGame = true;
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
            out.send(this.players.get(i).getOutputStream(),"match_found",RequestHandler.handleMatchFound());
        }
        this.updateTeam();
    }

    public ArrayNode getPlayerObjects(){
        ArrayNode playerObjs = objectMapper.createArrayNode();
        for(int i = 0; i < players.size(); i++){
            ObjectNode playerObj = objectMapper.createObjectNode();
            playerObj.put("name", players.get(i).getName());
            playerObj.put("player", players.get(i).getPid());
            playerObj.put("teg_id", players.get(i).getUsername());
            playerObj.put("avatar", players.get(i).getAvatar());
            playerObj.put("is_ready",players.get(i).isReady());
            playerObjs.add(playerObj);
        }
        return playerObjs;
    }

    private void updateTeam(){
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
            out.send(players.get(i).getOutputStream(),"team_update",RequestHandler.handleTeamUpdate(this.getPlayerObjects()));
        }
    }

    private void queueUpdate(){
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
            out.send(players.get(i).getOutputStream(), "queue_update", RequestHandler.handleQueueUpdate(this.players.size()));
        }
    }

    public String getType(){
        return type;
    }

    public boolean isPvP(){
        return pvp;
    }

    public void updatePlayer(Player p){
        int pIndex = this.findPlayerIndex(p);
        this.players.set(pIndex,p);
        this.updateTeam();
        if(this.getReadyPlayers() == this.players.size()){
            this.gameReady();
        }
    }

    private void gameReady(){
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
            out.send(players.get(i).getOutputStream(),"game_ready", RequestHandler.handleGameReady());
        }
    }

    private int getReadyPlayers(){
        int ready = 0;
        for(int i = 0; i < players.size(); i++){
            if(players.get(i).isReady()) ready++;
        }
        return ready;
    }

}

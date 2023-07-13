package xyz.openatbp.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;

public class Queue {
    public enum State{MATCHMAKING, CHAMP_SELECT, IN_GAME, TEAM_BUILDING};
    private ArrayList<Player> players;
    private String type;
    private State state;
    private boolean pvp;
    private static ObjectMapper objectMapper = new ObjectMapper();

    private boolean premade;
    private Player partyLeader;

    public Queue(ArrayList<Player> players, String type, boolean pvp){ //Called when a new queue is created by a team joining matchmaking
        this.players = new ArrayList<>();
        this.state = State.MATCHMAKING;
        this.players.addAll(players);
        this.type = type;
        this.pvp = pvp;
        this.premade = false;
        if(players.size() == 6){
            this.queueFull();
        }
    }

    public Queue(Player p, String type, boolean pvp){ //Called when a new queue is created by quick match
        this.players = new ArrayList<>();
        this.players.add(p);
        this.state = State.MATCHMAKING;
        this.type = type;
        this.pvp = pvp;
        this.premade = false;
        System.out.println("New queue - Queue size: " + this.getSize());
        if(type.equalsIgnoreCase("m_moba_practice")){ //Sends player to champ select if practice mode
            this.queueFull();
        }else if(type.equalsIgnoreCase("m_moba_tutorial")){
            this.queueFull();
        }else if(type.contains("1p")){
            this.queueFull();
        }
    }

    public Queue(Player p, String type, boolean pvp, boolean team){ //Called when a new team is created
        this.players = new ArrayList<>();
        this.players.add(p);
        this.state = State.MATCHMAKING;
        this.type = type;
        this.pvp = pvp;
        this.premade = team;
        if(this.premade) this.state = State.TEAM_BUILDING;
        this.partyLeader = p;
        System.out.println("New queue - Queue size: " + this.getSize());
    }

    public ArrayList<Player> getPlayers(){ //Returns list of players
        return this.players;
    }

    public boolean findPlayer(Player p){ //Returns if a player is in the queue or not based on Player object
        for (Player player : this.players) {
            if (player.getUsername().equalsIgnoreCase(p.getUsername())) {
                return true;
            }
        }
        return false;
    }

    public boolean findPlayer(String conn){ // Returns if a player is in the queue based on socket address
        for (Player player : this.players) {
            if (player.isAddress(conn)) return true;
        }
        return false;
    }

    private int findPlayerIndex(Player p){ // Returns index of player in arraylist
        for(int i = 0; i < this.players.size(); i++){
            if(this.players.get(i).getUsername().equalsIgnoreCase(p.getUsername()) && this.players.get(i).getPid() == p.getPid()){
                return i;
            }
        }
        return -1;
    }

    public void addPlayer(Player p){ //Adds a player object to the arraylist
        this.players.add(p);
        System.out.println("Add player - Queue size: " + this.getSize());
        if(!premade){ //If it's not team-building, updates the queue status to clients
            this.queueUpdate();
            if(players.size() == 6) this.queueFull(); //Queue is full
            else if(players.size() == 2 && this.type.contains("3p")) this.queueFull();
        }else{
            this.updatePremade(); //Updates premade team when user joins
        }
    }

    public void addPlayer(ArrayList<Player> p){ //Adds an array of players to the arraylist
        this.players.addAll(p);
        this.queueUpdate(); //Updates queue GUI
        if(this.players.size() == 2) this.queueFull(); //Goes to champ select when queue is full
    }

    public void removePlayer(Player p){ //Removes a player from the player arraylist
        this.players.remove(p);
        if(players.size()>0 && this.state == State.MATCHMAKING) this.queueUpdate(); //Updates queue if it's not premade or in game
        else if(this.state == State.TEAM_BUILDING) this.updateTeam(); //Updates pre-made team
        else if(this.state == State.CHAMP_SELECT){
            this.disbandTeam(); //Disbands the team if we're past matchmaking
        }
    }

    private void disbandTeam() { //WHAT THE CABBAGE?!
        for (Player player : players) {
            Packet out = new Packet();
            out.send(player.getOutputStream(), "team_disband", RequestHandler.handleDisband());
        }
    }

    public int getSize(){ //Returns amount of players
        return this.players.size();
    }

    public void queueFull(){ //Sends everyone to champ select
        this.state = State.CHAMP_SELECT;
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
           // if(i % 2 == 0) this.players.get(i).setTeam("BLUE");
            //else this.players.get(i).setTeam("PURPLE");
            if(i <= 2) this.players.get(i).setTeam("PURPLE");
            else this.players.get(i).setTeam("BLUE");
            out.send(this.players.get(i).getOutputStream(),"match_found",RequestHandler.handleMatchFound());
        }
        this.updateTeam();
    }

    public ArrayNode getPlayerObjects(){ //Returns all players in an array of json objects
        ArrayNode playerObjs = objectMapper.createArrayNode();
        for (Player player : players) {
            ObjectNode playerObj = objectMapper.createObjectNode();
            playerObj.put("name", player.getName());
            playerObj.put("player", player.getPid());
            playerObj.put("teg_id", player.getUsername());
            playerObj.put("avatar", player.getAvatar());
            playerObj.put("is_ready", player.isReady());
            playerObjs.add(playerObj);
        }
        return playerObjs;
    }

    public ArrayNode getPlayerObjects(String team){
        ArrayNode playerObjs = objectMapper.createArrayNode();
        for(Player p : this.players){
            if(p.getTeam() != null && p.getTeam().equalsIgnoreCase(team)){
                ObjectNode playerObj = objectMapper.createObjectNode();
                playerObj.put("name", p.getName());
                playerObj.put("player", p.getPid());
                playerObj.put("teg_id", p.getUsername());
                playerObj.put("avatar", p.getAvatar());
                playerObj.put("is_ready",p.isReady());
                playerObjs.add(playerObj);
            }
        }
        return playerObjs;
    }

    private void updateTeam(){ //Updates the client with all current team info
        for(Player p : this.players){
            Packet out = new Packet();
            out.send(p.getOutputStream(),"team_update",RequestHandler.handleTeamUpdate(this.getPlayerObjects(p.getTeam()),p.getTeam()));
        }
    }

    private void queueUpdate(){ //Updates the client with queue information
        for(int i = 0; i < players.size(); i++){
            Packet out = new Packet();
            out.send(players.get(i).getOutputStream(), "queue_update", RequestHandler.handleQueueUpdate(this.players.size())); //TODO: Giving null output stream
        }
    }

    public String getType(){ //Returns type of game
        return type;
    }

    public boolean isPvP(){ //Returns pvp/bots
        return pvp;
    }

    public void updatePlayer(Player p){ //Updates a player's info
        int pIndex = this.findPlayerIndex(p);
        this.players.set(pIndex,p);
        this.updateTeam();
        if(this.getReadyPlayers() == this.players.size()){
            this.gameReady();
        }
    }

    private void gameReady(){ //Sends all players into the game
        this.state = State.IN_GAME;
        for (Player player : players) {
            Packet out = new Packet();
            out.send(player.getOutputStream(), "game_ready", RequestHandler.handleGameReady(players.get(0), player.getTeam(), this.type));
        }
    }

    private int getReadyPlayers(){ //Returns amount of ready players
        int ready = 0;
        for (Player player : players) {
            if (player.isReady()) ready++;
        }
        return ready;
    }

    public Player getPartyLeader(){ //Gets the party leader when building a team
        if(premade) return this.partyLeader;
        else return null;
    }

    private void updatePremade(){ //Updates the client on a premade team
        for (Player player : players) {
            Packet out = new Packet();
            out.send(player.getOutputStream(), "team_update", RequestHandler.handleTeamJoin(this));
        }
    }

    public boolean isPremade(){ //Returns if queue is a premade team
        return premade;
    }

    public State getState(){
        return this.state;
    }

}

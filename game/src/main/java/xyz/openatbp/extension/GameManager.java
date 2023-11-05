package xyz.openatbp.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.RoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSRoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSVariableException;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GameManager {

    //bh1 = Blue Health 1 ph1 = Purple Health 1. Numbers refer to top,bottom,and outside respectively.
    public static final String[] SPAWNS = {"bh1","bh2","bh3","ph1","ph2","ph3","keeoth","ooze","hugwolf","gnomes","ironowls","grassbear"};
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void addPlayer(Room room, ATBPExtension parentExt){ //Sends player info to client
        System.out.println("Adding player!");
        for(User user : room.getUserList()){
            ISFSObject playerInfo = user.getVariable("player").getSFSObjectValue();
            int id = user.getId();
            String name = playerInfo.getUtfString("name");
            String champion = playerInfo.getUtfString("avatar");
            int team = playerInfo.getInt("team");
            String backpack = playerInfo.getUtfString("backpack");
            String tid = playerInfo.getUtfString("tegid");
            int elo = parentExt.getElo(tid);
            ExtensionCommands.addUser(parentExt,room,id,name,champion,team,tid,backpack,elo);

        }

    }

    public static void loadPlayers(Room room, ATBPExtension parentExt){ //Loads the map for everyone
        String groupID = room.getGroupId();
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            if(groupID.equals("Practice")){
                data.putUtfString("set","AT_1L_Arena");
            }else{
                data.putUtfString("set", "AT_2L_Arena");
            }
            int maxUsers = room.getMaxUsers();
            int userSize = room.getUserList().size();
            data.putUtfString("soundtrack", "music_main1");
            data.putInt("roomId", room.getId());
            data.putUtfString("roomName", room.getName());
            data.putInt("capacity", maxUsers);
            data.putInt("botCount", maxUsers-userSize);
            parentExt.send("cmd_load_room", data, u);
        }
    }

    public static boolean playersLoaded(ArrayList<User> users, int gameSize){
        int num = 0;
        for(User u : users){
            if(u.getProperty("joined") != null && (boolean)u.getProperty("joined")) num++;
        }
        return num == gameSize;
    }

    public static boolean playersReady(Room room){ //Checks if all clients are ready
        int ready = 0;
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for(int i = 0; i < users.size(); i++){
            System.out.println(users.get(i).getSession());
            if(users.get(i).getSession().getProperty("ready") == null) return false;
            if((boolean) users.get(i).getSession().getProperty("ready")) ready++;
        }
        return ready == users.size();
    }

    public static void sendAllUsers(ATBPExtension parentExt, ISFSObject data, String cmd, Room room){
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for(int i = 0; i < users.size(); i++){
            parentExt.send(cmd, data, users.get(i));
        }
    }

    public static void initializeGame(Room room, ATBPExtension parentExt) throws SFSVariableException {
        int blueNum = 0;
        int purpleNum = 0;
        initializeMap(room,parentExt);
        // ExtensionCommands.addUser(parentExt,room,101,"Fake user 1","finn",0,"finn",backpack,0);
        if(room.getUserList().size() < 6){
            ExtensionCommands.createActor(parentExt,room,"101","finn",new Point2D.Float((float) (MapData.PURPLE_SPAWNS[1].getX()*-1), (float) MapData.PURPLE_SPAWNS[1].getY()),0f,1);
            ExtensionCommands.createActor(parentExt,room,"102","jake",new Point2D.Float((float) (MapData.PURPLE_SPAWNS[2].getX()*-1),(float) MapData.PURPLE_SPAWNS[2].getY()),0f,1);
            ExtensionCommands.createActor(parentExt,room,"103","magicman",MapData.PURPLE_SPAWNS[1], 0f,0);
            ExtensionCommands.createActor(parentExt,room,"104","iceking",MapData.PURPLE_SPAWNS[2],0f,0);
        }

        for(User u : room.getUserList()){
            ISFSObject playerInfo = u.getVariable("player").getSFSObjectValue();
            int team = playerInfo.getInt("team");
            float px = 0f;
            float pz = 0f;
            if(team == 0){
                px = (float) MapData.PURPLE_SPAWNS[purpleNum].getX();
                pz = (float) MapData.PURPLE_SPAWNS[purpleNum].getY();
                purpleNum++;
            }
            if(team == 1){
                px = (float) MapData.PURPLE_SPAWNS[blueNum].getX()*-1;
                pz = (float) MapData.PURPLE_SPAWNS[blueNum].getY();
                blueNum++;
            }
            System.out.println("Original spawn is " + px + "," + pz);
            String id = String.valueOf(u.getId());
            String actor = playerInfo.getUtfString("avatar");
            Point2D location = new Point2D.Float(px,pz);
            ExtensionCommands.createActor(parentExt,room,id,actor,location,0f,team);

            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(u.getId()));
            int champMaxHealth = parentExt.getActorStats(playerInfo.getUtfString("avatar")).get("health").asInt();
            updateData.putInt("currentHealth", champMaxHealth);
            updateData.putInt("maxHealth", champMaxHealth);
            updateData.putDouble("pHealth", 1);
            updateData.putInt("xp", 0);
            updateData.putDouble("pLevel", 0);
            updateData.putInt("level", 1);
            updateData.putInt("availableSpellPoints", 1);
            updateData.putLong("timeSinceBasicAttack", 0);
            //SP_CATEGORY 1-5 TBD
            updateData.putInt("sp_category1", 0);
            updateData.putInt("sp_category2", 0);
            updateData.putInt("sp_category3" ,0);
            updateData.putInt("sp_category4", 0);
            updateData.putInt("sp_category5", 0);
            updateData.putInt("deaths", 0);
            updateData.putInt("assists", 0);
            JsonNode actorStats = parentExt.getActorStats(playerInfo.getUtfString("avatar"));
            for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                updateData.putDouble(k,actorStats.get(k).asDouble());
            }
            UserVariable userStat = new SFSUserVariable("stats",updateData);
            u.setVariable(userStat);
            ExtensionCommands.updateActorData(parentExt,room,updateData);
        }
        Point2D guardianLoc = MapData.getGuardianLocationData(0,room.getGroupId());
        Point2D guardianLoc2 = MapData.getGuardianLocationData(1,room.getGroupId());
        ExtensionCommands.moveActor(parentExt,room,"gumball0",guardianLoc,new Point2D.Float((float) (guardianLoc.getX()+1f), (float) guardianLoc.getY()),0.01f,true);
        ExtensionCommands.moveActor(parentExt,room,"gumball1",guardianLoc2,new Point2D.Float((float) (guardianLoc2.getX()-1f), (float) guardianLoc2.getY()),0.01f,true);
        try{ //Sets all the room variables once the game is about to begin
            setRoomVariables(room);
        }catch(SFSVariableException e){
            System.out.println(e);
        }

        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, u); //Starts the game for everyone
        }
        ExtensionCommands.playSound(parentExt,room,"music","music/music_main2",new Point2D.Float(0,0));
        ExtensionCommands.playSound(parentExt,room,"global","announcer/welcome",new Point2D.Float(0,0));
    }

    private static void setRoomVariables(Room room) throws SFSVariableException {
        ISFSObject spawnTimers = new SFSObject();
        for(String s : SPAWNS){ //Adds in spawn timers for all mobs/health. AKA time dead
            spawnTimers.putInt(s,0);
        }
        ISFSObject teamScore = new SFSObject();
        teamScore.putInt("blue",0);
        teamScore.putInt("purple",0);
        ISFSObject mapData = new SFSObject();
        mapData.putBool("blueUnlocked", false);
        mapData.putBool("purpleUnlocked", false);
        RoomVariable scoreVar = new SFSRoomVariable("score",teamScore);
        List<RoomVariable> variables = new ArrayList<>();
        RoomVariable spawnVar = new SFSRoomVariable("spawns",spawnTimers);
        RoomVariable mapVar = new SFSRoomVariable("map",mapData);
        variables.add(scoreVar);
        variables.add(spawnVar);
        variables.add(mapVar);
        room.setVariables(variables);

    }

    private static void initializeMap(Room room, ATBPExtension parentExt){
        String roomStr = room.getGroupId();
        ExtensionCommands.createActor(parentExt,room,MapData.getBaseActorData(0,roomStr));
        ExtensionCommands.createActor(parentExt,room,MapData.getBaseActorData(1,roomStr));

        spawnTowers(room,parentExt);
        spawnAltars(room,parentExt);
        spawnHealth(room,parentExt);

        ExtensionCommands.createActor(parentExt,room,MapData.getGuardianActorData(0,roomStr));
        ExtensionCommands.createActor(parentExt,room,MapData.getGuardianActorData(1,roomStr));

    }

    private static void spawnTowers(Room room, ATBPExtension parentExt){
        String roomStr = room.getGroupId();

        ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(0,1,roomStr));
        ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(0,2,roomStr));
        ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(1,1,roomStr));
        ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(1,2,roomStr));

        if(!roomStr.equalsIgnoreCase("practice")){
            ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(0,3,roomStr));
            ExtensionCommands.createActor(parentExt,room,MapData.getTowerActorData(1,3,roomStr));
        }
    }

    private static void spawnAltars(Room room, ATBPExtension parentExt){
        ExtensionCommands.createActor(parentExt,room,MapData.getAltarActorData(1,room.getGroupId()));
        ExtensionCommands.createActor(parentExt,room,MapData.getAltarActorData(2,room.getGroupId()));
        if(!room.getGroupId().equalsIgnoreCase("practice")){
            ExtensionCommands.createActor(parentExt,room,MapData.getAltarActorData(0,room.getGroupId()));
        }
    }

    private static void spawnHealth(Room room, ATBPExtension parentExt){
        if(room.getGroupId().equalsIgnoreCase("practice")){
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(0,room.getGroupId(),-1));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(1,room.getGroupId(),-1));
        }else{
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(0,room.getGroupId(),0));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(0,room.getGroupId(),1));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(0,room.getGroupId(),2));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(1,room.getGroupId(),0));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(1,room.getGroupId(),1));
            ExtensionCommands.createActor(parentExt,room,MapData.getHealthActorData(1,room.getGroupId(),2));
        }
    }

    public static JsonNode getTeamData(ATBPExtension parentExt, int team, Room room){
        final String[] STATS = {"damageDealtChamps","damageReceivedPhysical","damageReceivedSpell","spree","damageReceivedTotal","damageDealtSpell","score","timeDead","damageDealtTotal","damageDealtPhysical"};
        /*
        Stats:
            damageDealtChamps
            damageReceivedPhysical
            damageReceivedSpell
            name?
            spree
            deaths
            damageReceivedTotal
            assists
            jungleMobs?
            kills
            minions?
            damageDealtSpell
            score
            timeDead
            healthPickUps?
            playerName?
            damageDealtTotal
            damageDealtPhysical

         */
        ObjectNode node = objectMapper.createObjectNode();
        for(User u : room.getUserList()){
            UserActor ua = parentExt.getRoomHandler(room.getId()).getPlayer(String.valueOf(u.getId()));
            if(ua.getTeam() == team){
                ObjectNode player = objectMapper.createObjectNode();
                ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                player.put("id",u.getId());
                for(String s : STATS){
                    if(ua.hasGameStat(s)) player.put(s,ua.getGameStat(s));
                }
                player.put("name",playerVar.getUtfString("name"));
                player.put("kills", ua.getStat("kills"));
                player.put("deaths", ua.getStat("deaths"));
                player.put("assists", ua.getStat("assists"));
                player.put("playerName",ua.getAvatar());
                player.put("myElo",(double)playerVar.getInt("elo"));
                player.put("coins",100); //Just going to have this be a flat amount for now
                player.put("prestigePoints",10); //Just going to have this be a flat amount for now
                node.set(String.valueOf(u.getId()),player);
            }
        }
        return node;
    }

    public static JsonNode getGlobalTeamData(Room room){
        ObjectNode node = objectMapper.createObjectNode();
        for(User u : room.getUserList()){
                ObjectNode player = objectMapper.createObjectNode();
                ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
                ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                player.put("id",u.getId());
                player.put("name",playerVar.getUtfString("name"));
                int kills = 0;
                if(stats.getInt("kills") != null) kills = stats.getInt("kills");
                player.put("kills", kills);
                player.put("deaths", stats.getInt("deaths"));
                player.put("assists", stats.getInt("assists"));
                player.put("playerName",playerVar.getUtfString("avatar"));
                player.put("myElo",(double)playerVar.getInt("elo"));
                node.set(String.valueOf(u.getId()),player);
        }
        return node;
    }

}

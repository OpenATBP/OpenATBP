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

public class GameManager {

    //bh1 = Blue Health 1 ph1 = Purple Health 1. Numbers refer to top,bottom,and outside respectively.
    public static final String[] SPAWNS = {"bh1","bh2","bh3","ph1","ph2","ph3","keeoth","ooze","hugwolf","gnomes","owls","grassbear"};
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void addPlayer(ArrayList<User> users, ATBPExtension parentExt){ //Sends player info to client
        for(int i = 0; i < users.size(); i++){
            ISFSObject userData = new SFSObject();
            User player = users.get(i);
            ISFSObject playerInfo = player.getVariable("player").getSFSObjectValue();
            userData.putInt("id", player.getId());
            userData.putUtfString("name", playerInfo.getUtfString("name"));
            userData.putUtfString("champion", playerInfo.getUtfString("avatar"));
            userData.putInt("team", 0); //Change up team data
            userData.putUtfString("tid", playerInfo.getUtfString("tegid"));
            userData.putUtfString("backpack", playerInfo.getUtfString("backpack"));
            userData.putInt("elo", 1700); //Database
            for(int g = 0; g < users.size(); g++){
                parentExt.send("cmd_add_user",userData,users.get(g));
            }

        }
        if(users.size() < 3){ //Testing method just adds fake users to the game to run properly
            for(int i = 0; i < users.size(); i++){
                ISFSObject userData = new SFSObject();
                User player = users.get(i);
                userData.putInt("id", 100);
                userData.putUtfString("name", "Fake User");
                userData.putUtfString("champion", "magicman");
                userData.putInt("team", 0); //Change up team data
                userData.putUtfString("tid", "fakeuser");
                userData.putUtfString("backpack", "belt_champions");
                userData.putInt("elo", 1700); //Database
                parentExt.send("cmd_add_user",userData,player);
            }
        }

    }

    public static void loadPlayers(ArrayList<User> users, ATBPExtension parentExt, Room room){ //Loads the map for everyone
        String groupID = room.getGroupId();
        for(int i = 0; i < users.size(); i++){
            ISFSObject data = new SFSObject();
            if(groupID.equals("Practice")){
                data.putUtfString("set","AT_1L_Arena");
            }else{
                data.putUtfString("set", "AT_2L_Arena");
            }
            data.putUtfString("soundtrack", "music_main1");
            data.putInt("roomId", room.getId());
            data.putUtfString("roomName", room.getName());
            data.putInt("capacity", 2);
            data.putInt("botCount", 0);
            parentExt.send("cmd_load_room", data, users.get(i));
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

    public static void initializeGame(ArrayList<User> users, ATBPExtension parentExt) throws SFSVariableException {
        for(int i = 0; i < users.size(); i++){ //Initialize character
            User sender = users.get(i);
            initializeMap(sender,parentExt);
            ISFSObject actorData = new SFSObject();
            ISFSObject playerInfo = sender.getVariable("player").getSFSObjectValue();
            actorData.putUtfString("id", String.valueOf(sender.getId()));
            actorData.putUtfString("actor", playerInfo.getUtfString("avatar"));
            ISFSObject spawnPoint = new SFSObject();
            spawnPoint.putFloat("x", (float) 36.90);
            spawnPoint.putFloat("y", (float) 0);
            spawnPoint.putFloat("z", (float) 2.3);
            spawnPoint.putFloat("rotation", 0);
            actorData.putSFSObject("spawn_point", spawnPoint);
            sender.getVariable("location").getSFSObjectValue().putSFSObject("p1",spawnPoint);

            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(sender.getId()));
            System.out.println("Here!");
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
            sender.setVariable(userStat);
            for(int g = 0; g < users.size(); g++){ //Send characters
                User user = users.get(g);
                parentExt.send("cmd_create_actor", actorData, user);

                parentExt.send("cmd_update_actor_data", updateData, user);

            }
        }
        try{ //Sets all the room variables once the game is about to begin
            setRoomVariables(users.get(0).getLastJoinedRoom());
        }catch(SFSVariableException e){
            System.out.println(e);
        }

        for(int i = 0; i < users.size(); i++){
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, users.get(i)); //Starts the game for everyone
        }

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

    private static void initializeMap(User user, ATBPExtension parentExt){
        String room = user.getLastJoinedRoom().getGroupId();
        parentExt.send("cmd_create_actor",MapData.getBaseActorData(0,room),user);
        parentExt.send("cmd_create_actor",MapData.getBaseActorData(1,room),user);

        spawnTowers(user,parentExt);
        spawnAltars(user,parentExt,room);
        spawnHealth(user,parentExt,room);

        Point2D guardianLoc = new Point2D.Float(MapData.L2_GUARDIAN1_X*-1,MapData.L2_GUARDIAN1_Z);
        Point2D guardianLoc2 = new Point2D.Float(MapData.L2_GUARDIAN1_X,MapData.L2_GUARDIAN1_Z);
        parentExt.send("cmd_create_actor",MapData.getGuardianActorData(0,room),user);
        parentExt.send("cmd_create_actor",MapData.getGuardianActorData(1,room),user);
        ExtensionCommands.moveActor(parentExt,user,"gumball0",guardianLoc,new Point2D.Float((float) (guardianLoc.getX()+0.01f), (float) guardianLoc.getY()),0.01f,true);
        ExtensionCommands.moveActor(parentExt,user,"gumball1",guardianLoc2,new Point2D.Float((float) (guardianLoc2.getX()-0.01f), (float) guardianLoc2.getY()),0.01f,true);

    }

    private static void spawnTowers(User user, ATBPExtension parentExt){
        String room = user.getLastJoinedRoom().getGroupId();
        parentExt.send("cmd_create_actor",MapData.getTowerActorData(0,1,room),user);
        parentExt.send("cmd_create_actor",MapData.getTowerActorData(0,2,room),user);
        parentExt.send("cmd_create_actor",MapData.getTowerActorData(1,1,room),user);
        parentExt.send("cmd_create_actor",MapData.getTowerActorData(1,2,room),user);
        if(!room.equalsIgnoreCase("practice")){
            parentExt.send("cmd_create_actor",MapData.getTowerActorData(0,3,room),user);
            parentExt.send("cmd_create_actor",MapData.getTowerActorData(1,3,room),user);
        }
    }

    private static void spawnAltars(User user, ATBPExtension parentExt, String room){
        parentExt.send("cmd_create_actor",MapData.getAltarActorData(1,room),user);
        parentExt.send("cmd_create_actor",MapData.getAltarActorData(2,room),user);
        if(!room.equalsIgnoreCase("practice")){
            parentExt.send("cmd_create_actor",MapData.getAltarActorData(0,room),user);
        }
    }

    private static void spawnHealth(User user, ATBPExtension parentExt, String room){
        if(room.equalsIgnoreCase("practice")){
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(0,room,-1),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(1,room,-1),user);
        }else{
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(0,room,0),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(0,room,1),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(0,room,2),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(1,room,0),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(1,room,1),user);
            parentExt.send("cmd_create_actor",MapData.getHealthActorData(1,room,2),user);
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

    public static List<Monster> initializeCamps(ATBPExtension parentExt, Room room){
        List<Monster> monsters = new ArrayList<>(8);
        monsters.add(new Monster(parentExt,room,MapData.GRASS,"grassbear"));
        monsters.add(new Monster(parentExt,room,MapData.HUGWOLF,"hugwolf"));
        char[] chars = {'a','b','c'};
        for(int i = 0; i < MapData.OWLS.length; i++){
            String monsterName = "ironowl_"+chars[i];
            monsters.add(new Monster(parentExt,room,MapData.OWLS[i],monsterName));
        }
        for(int i = 0; i < MapData.GNOMES.length; i++){
            String monsterName = "gnome_"+chars[i];
            monsters.add(new Monster(parentExt,room,MapData.GNOMES[i],monsterName));
        }
        return monsters;
    }
}

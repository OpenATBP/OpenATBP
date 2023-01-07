package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.RoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSRoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSVariableException;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class GameManager {

    public static final String[] SPAWNS = {"bh1","bh2","bh3","ph1","ph2","ph3","keeoth","ooze","hugwolf","gnomes","owls","grassbear"};

    private void updateAllPlayers(ArrayList<User> users, String cmd, SFSObject data, ATBPExtension parentExt){
        for(int i = 0; i < users.size(); i++){
            parentExt.send(cmd, data, users.get(i));
        }
    }

    public static void addPlayer(ArrayList<User> users, ATBPExtension parentExt){
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
        if(users.size() > 1){
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

    public static void loadPlayers(ArrayList<User> users, ATBPExtension parentExt, Room room){
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
        return users.size() == gameSize;
    }

    public static boolean playersReady(Room room){
        int ready = 0;
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for(int i = 0; i < users.size(); i++){
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

    public static void initializeGame(ArrayList<User> users, ATBPExtension parentExt){
        for(int i = 0; i < users.size(); i++){ //Initialize character
            User sender = users.get(i);
            initializeMap(sender,parentExt);
            ISFSObject actorData = new SFSObject();
            ISFSObject playerInfo = sender.getVariable("player").getSFSObjectValue();
            actorData.putUtfString("id", String.valueOf(sender.getId()));
            actorData.putUtfString("actor", playerInfo.getUtfString("avatar"));
            ISFSObject spawnPoint = new SFSObject();
            spawnPoint.putFloat("x", (float) -1.5);
            spawnPoint.putFloat("y", (float) 0);
            spawnPoint.putFloat("z", (float) -11.7);
            spawnPoint.putFloat("rotation", 0);
            actorData.putSFSObject("spawn_point", spawnPoint);

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
            //SP_CATEGORY 1-5 TBD
            updateData.putInt("sp_category1", 0);
            updateData.putInt("sp_category2", 0);
            updateData.putInt("sp_category3" ,0);
            updateData.putInt("sp_category4", 0);
            updateData.putInt("sp_category5", 0);
            updateData.putInt("kills", 0);
            updateData.putInt("deaths", 0);
            updateData.putInt("assists", 0);
            for(int g = 0; g < users.size(); g++){ //Send characters
                User user = users.get(g);
                parentExt.send("cmd_create_actor", actorData, user);
                parentExt.send("cmd_update_actor_data", updateData, user);
            }
        }
        try{
            setRoomVariables(users.get(0).getLastJoinedRoom());
        }catch(SFSVariableException e){
            System.out.println(e);
        }

        for(int i = 0; i < users.size(); i++){
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, users.get(i));
        }

    }

    private static void setRoomVariables(Room room) throws SFSVariableException {
        ISFSObject spawnTimers = new SFSObject();
        for(String s : SPAWNS){
            spawnTimers.putInt(s,0);
        }
        RoomVariable spawnVar = new SFSRoomVariable("spawns",spawnTimers);
        room.setVariable(spawnVar);
    }

    private static void initializeMap(User user, ATBPExtension parentExt){
        String room = user.getLastJoinedRoom().getGroupId();
        parentExt.send("cmd_create_actor",MapData.getBaseActorData(0,room),user);
        parentExt.send("cmd_create_actor",MapData.getBaseActorData(1,room),user);

        spawnTowers(user,parentExt);
        spawnAltars(user,parentExt,room);
        spawnHealth(user,parentExt,room);

        parentExt.send("cmd_create_actor",MapData.getGuardianActorData(0,room),user);
        parentExt.send("cmd_create_actor",MapData.getGuardianActorData(1,room),user);

        ArrayList<Vector<Float>>[] points = parentExt.getColliders("practice");
        /*
        for(int i = 0; i < points.length; i++){
            ArrayList<Vector<Float>> collider = points[i];
            for(int j = 0; j < collider.size(); j++){
                Vector<Float> v = collider.get(j);
                float x = v.get(0);
                float z = v.get(1);
                ISFSObject base = new SFSObject();
                ISFSObject baseSpawn = new SFSObject();
                base.putUtfString("id","collider"+i+"_"+j);
                base.putUtfString("actor","gnome_a");
                baseSpawn.putFloat("x", x);
                baseSpawn.putFloat("y", (float) 0.0);
                baseSpawn.putFloat("z", z);
                base.putSFSObject("spawn_point", baseSpawn);
                base.putFloat("rotation", (float) 0.0);
                base.putInt("team", 2);
                parentExt.send("cmd_create_actor",base,user);
            }
        }

         */

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
}

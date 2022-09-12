package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class GameManager {

    private void updateAllPlayers(ArrayList<User> users, String cmd, SFSObject data, ATBPExtension parentExt){
        for(int i = 0; i < users.size(); i++){
            parentExt.send(cmd, data, users.get(i));
        }
    }

    public static void addPlayer(ArrayList<User> users, ATBPExtension parentExt){
        for(int i = 0; i < users.size(); i++){
            ISFSObject userData = new SFSObject();
            User player = users.get(i);
            userData.putInt("id", player.getId());
            userData.putUtfString("name", player.getVariable("name").getStringValue());
            userData.putUtfString("champion", player.getVariable("avatar").getStringValue());
            userData.putInt("team", 0); //Change up team data
            userData.putUtfString("tid", player.getVariable("tegid").getStringValue());
            userData.putUtfString("backpack", player.getVariable("backpack").getStringValue());
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
            ISFSObject actorData = new SFSObject();
            actorData.putUtfString("id", String.valueOf(sender.getId()));
            actorData.putUtfString("actor", sender.getVariable("avatar").getStringValue());
            ISFSObject spawnPoint = new SFSObject();
            spawnPoint.putFloat("x", (float) -1.5);
            spawnPoint.putFloat("y", (float) 0);
            spawnPoint.putFloat("z", (float) -11.7);
            spawnPoint.putFloat("rotation", 0);
            actorData.putSFSObject("spawn_point", spawnPoint);

            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(sender.getId()));
            int champMaxHealth = ChampionData.getMaxHealth(sender.getVariable("avatar").getStringValue());
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

        for(int i = 0; i < users.size(); i++){
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, users.get(i));
        }

    }
}

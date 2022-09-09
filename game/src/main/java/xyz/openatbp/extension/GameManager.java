package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

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

    public static void loadPlayers(ArrayList<User> users, ATBPExtension parentExt, Room room){
        for(int i = 0; i < users.size(); i++){
            ISFSObject data = new SFSObject();
            data.putUtfString("set","AT_1L_Arena");
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
}

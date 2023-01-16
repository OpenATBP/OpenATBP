package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.GameManager;

import java.util.ArrayList;

public class ClientReadyHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) { //Sent when client has loaded all assets
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        trace(params.getDump());
        int progress = params.getInt("progress");
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(sender.getId()));
        data.putInt("progress", progress);
        data.putBool("isReady", true);
        Room room = sender.getLastJoinedRoom();
        parentExt.send("cmd_client_ready", data, sender); //Handles progress bar
        GameManager.sendAllUsers(parentExt,data,"cmd_client_ready",room);
        if(progress == 100){
            sender.getSession().setProperty("ready", true);

            if(GameManager.playersReady(room)){ //If all players are ready, load everyone into the actual map
                parentExt.startScripts(room); //Starts the background scripts for the game
                try{
                    GameManager.initializeGame(room.getUserList(), parentExt); //Initializes the map for everyone
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

    }
}

package xyz.openatbp.extension.reqhandlers;

import java.util.List;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

public class ClientReadyHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Sent when client has loaded all assets
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        // trace(params.getDump());
        int progress = params.getInt("progress");
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(sender.getId()));
        data.putInt("progress", progress);
        data.putBool("isReady", progress == 100);
        Room room = sender.getLastJoinedRoom();
        parentExt.send("cmd_client_ready", data, sender); // Handles progress bar
        GameManager.sendAllUsers(parentExt, data, "cmd_client_ready", room);
        if (sender.getSession().getProperty("ready") != null) return;
        if (progress == 100) {
            sender.getSession().setProperty("ready", true);

            if (GameManager.playersReady(room) && (int) room.getProperty("state") == 1) {
                // If all players are ready, load everyone into the actual map

                // HANDLES BOT PROGRESS BAR
                List<ISFSObject> botProfiles = (List<ISFSObject>) room.getProperty("botProfiles");

                if (botProfiles != null) {
                    for (ISFSObject botProfile : botProfiles) {
                        ISFSObject botProgressBar = new SFSObject();
                        int botId = botProfile.getInt("botId");
                        botProgressBar.putUtfString("id", String.valueOf(botId));
                        botProgressBar.putInt("progress", 100);
                        botProgressBar.putBool("isReady", true);
                        GameManager.sendAllUsers(
                                parentExt, botProgressBar, "cmd_client_ready", room);
                    }
                }

                try {

                    GameManager.initializeGame(room, parentExt); // Initializes the map for everyone
                } catch (Exception e) { // TODO: Kick everyone out of game if this fails
                    e.printStackTrace();
                }
            }
        }
    }
}

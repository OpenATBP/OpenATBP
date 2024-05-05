package xyz.openatbp.extension.reqhandlers;

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

            if (GameManager.playersReady(room)
                    && (int) room.getProperty("state")
                            == 1) { // If all players are ready, load everyone into the actual map
                try {
                    GameManager.initializeGame(room, parentExt); // Initializes the map for everyone
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

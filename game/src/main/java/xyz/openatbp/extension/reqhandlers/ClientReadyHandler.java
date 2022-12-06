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
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        trace("Client is ready");
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(sender.getId()));
        data.putInt("progress", 100);
        data.putBool("isReady", true);
        parentExt.send("cmd_client_ready", data, sender);
        sender.getSession().setProperty("ready", true);

        Room room = sender.getLastJoinedRoom();

        if(GameManager.playersReady(room)){
            GameManager.initializeGame((ArrayList<User>) room.getUserList(), parentExt);
        }

    }
}

package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

import java.util.ArrayList;

public class JoinRoomEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) { //Initialize everything
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User sender = (User) event.getParameter(SFSEventParam.USER);
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        if(GameManager.playersLoaded(users, 2)){
            System.out.println("Last to join is " + sender.getName());
            GameManager.addPlayer(users,parentExt);
            GameManager.loadPlayers(users,parentExt,room);
        }

        System.out.println("Joined room!");


        for(int i = 0; i < users.size(); i++){
            ISFSObject readyData = new SFSObject();
            readyData.putUtfString("id", String.valueOf(sender.getId()));
            readyData.putInt("progress", 50);
            readyData.putBool("isReady", false);
            parentExt.send("cmd_client_ready", readyData, users.get(i));
        }
    }
}

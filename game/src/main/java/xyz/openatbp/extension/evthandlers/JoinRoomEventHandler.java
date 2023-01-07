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
        int maxPlayers = room.getMaxUsers();
        if(maxPlayers>1) maxPlayers = 1; //Remove after testing
        else if(maxPlayers < 0) maxPlayers = 1;
        if(GameManager.playersLoaded(users, maxPlayers)){ //If all players have loaded into the room
            System.out.println("Last to join is " + sender.getName());
            GameManager.addPlayer(users,parentExt); //Add users to the game
            GameManager.loadPlayers(users,parentExt,room); //Load the players into the map
        }

        System.out.println("Joined room!");
    }
}

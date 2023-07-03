package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import xyz.openatbp.extension.ATBPExtension;

public class UserDisconnect extends BaseServerEventHandler {

    @Override
    public void handleServerEvent(ISFSEvent event){
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        User user = (User) event.getParameter(SFSEventParam.USER);
        String roomID = user.getSession().getProperty("room_id").toString(); //Can probably find a better way to handle room names
        if(roomID.length() >= 10) roomID = roomID.substring(0,10);
        Room room = user.getZone().getRoomByName(roomID);
        parentExt.getRoomHandler(room.getId()).handlePlayerDC(user);
        if(room.isEmpty()){ //If there is no one left in the room, delete the room
            parentExt.getApi().removeRoom(room);
        }
    }
}

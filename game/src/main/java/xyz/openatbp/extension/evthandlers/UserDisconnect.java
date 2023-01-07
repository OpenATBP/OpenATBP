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
        String roomID = user.getSession().getProperty("room_id").toString();
        if(roomID.length() >= 10) roomID = roomID.substring(0,10);
        Room room = user.getZone().getRoomByName(roomID);
        if(room.isEmpty()){
            parentExt.getApi().removeRoom(room);
        }
    }
}

package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.exceptions.SFSJoinRoomException;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import xyz.openatbp.extension.ATBPExtension;

public class RoomCreatedEventHandler extends BaseServerEventHandler {

    @Override
    public void handleServerEvent(ISFSEvent event) throws SFSJoinRoomException {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User owner = room.getOwner();
        parentExt.getApi().joinRoom(owner, room);
    }
}

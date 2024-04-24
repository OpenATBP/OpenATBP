package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;

public class UserDisconnect extends BaseServerEventHandler {

    @Override
    public void handleServerEvent(ISFSEvent event) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        User user = (User) event.getParameter(SFSEventParam.USER);
        String roomID =
                user.getSession()
                        .getProperty("room_id")
                        .toString(); // Can probably find a better way to handle room names
        Room room = user.getZone().getRoomByName(roomID);
        RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
        int roomState = (int) room.getProperty("state");
        if (roomHandler != null) {
            roomHandler.handlePlayerDC(user);
        } else if (roomState < 2) {
            ExtensionCommands.abortGame(parentExt, room);
        }
        if (room.isEmpty()) { // If there is no one left in the room, delete the room
            parentExt.getApi().removeRoom(room);
        }
    }
}

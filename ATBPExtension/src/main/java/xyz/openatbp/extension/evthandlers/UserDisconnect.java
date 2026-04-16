package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;

public class UserDisconnect extends BaseServerEventHandler {

    @Override
    public void handleServerEvent(ISFSEvent event) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        User user = (User) event.getParameter(SFSEventParam.USER);

        Object roomIdProperty = user.getSession().getProperty("room_id");
        if (roomIdProperty == null) {
            Console.logWarning(user.getName() + " disconnected before room_id was set");
            return;
        }

        String roomID = roomIdProperty.toString();
        Room room = user.getZone().getRoomByName(roomID);

        if (room == null) {
            Console.logWarning(
                    user.getName() + " disconnected but room " + roomID + " no longer exists.");
            return;
        }

        Object stateProperty = room.getProperty("state");
        if (stateProperty == null) {
            Console.logWarning(
                    "Room " + roomID + " has null state during disconnect of " + user.getName());
            if (room.isEmpty()) {
                parentExt.getApi().removeRoom(room);
            }
            return;
        }

        int roomState = (int) stateProperty;
        RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());

        if (roomHandler != null) {
            roomHandler.handlePlayerDC(user);
        } else if (roomState < 2) {
            ExtensionCommands.abortGame(parentExt, room);
        }

        if (room.isEmpty()) {
            parentExt.getApi().removeRoom(room);
        }
    }
}

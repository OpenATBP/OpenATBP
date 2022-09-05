package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import xyz.openatbp.extension.ATBPExtension;

public class JoinRoomEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) { //Initialize everything
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User sender = (User) event.getParameter(SFSEventParam.USER);
        ISFSObject data = new SFSObject();
        data.putUtfString("set","AT_1L_Arena");
        data.putUtfString("soundtrack", "music_main1");
        data.putInt("roomId", room.getId());
        data.putUtfString("roomName", room.getName());
        data.putInt("capacity", 2);
        data.putInt("botCount", 0);
        parentExt.send("cmd_load_room", data, sender);

        ISFSObject readyData = new SFSObject();
        readyData.putUtfString("id", String.valueOf(sender.getId()));
        readyData.putInt("progress", 50);
        readyData.putBool("isReady", false);
        parentExt.send("cmd_client_ready", readyData, sender);


    }
}

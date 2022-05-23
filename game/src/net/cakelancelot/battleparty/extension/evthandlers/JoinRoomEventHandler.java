package net.cakelancelot.battleparty.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import net.cakelancelot.battleparty.extension.ATBPExtension;

public class JoinRoomEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User user = (User) event.getParameter(SFSEventParam.USER);

        ISFSObject data = new SFSObject();
        data.putUtfString("set", "m_moba_tutorial");
        data.putUtfString("soundtrack", "music_main1");
        data.putInt("roomId", room.getId());
        data.putUtfString("roomName", room.getName());
        data.putInt("capacity", 2);
        data.putInt("botCount", 1);

        parentExt.send("cmd_load_room", data, user);
    }
}

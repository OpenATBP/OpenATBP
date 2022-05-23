package net.cakelancelot.battleparty.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import net.cakelancelot.battleparty.extension.ATBPExtension;

public class JoinZoneEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        User user = (User) event.getParameter(SFSEventParam.USER);

        ISFSObject data = new SFSObject();
        data.putUtfString("set", "AT_2L_Arena");
        data.putUtfString("soundtrack", "music_main");
        data.putInt("roomId", 0);
        data.putUtfString("roomName", "notlobby");
        data.putInt("capacity", 2);
        data.putInt("botCount", 1);

        parentExt.send("cmd_load_room", data, user);

        ISFSObject data2 = new SFSObject();
        data2.putUtfString("id", String.valueOf(user.getId()));
        data2.putUtfString("actor", "finn");
        data2.putInt("team", 0);

        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) -1.5);
        spawnPoint.putFloat("y", (float) 0);
        spawnPoint.putFloat("z", (float) -11.7);
        spawnPoint.putFloat("rotation", 0);
        data2.putSFSObject("spawn_point", spawnPoint);

        parentExt.send("cmd_create_actor", data2, user);
    }
}

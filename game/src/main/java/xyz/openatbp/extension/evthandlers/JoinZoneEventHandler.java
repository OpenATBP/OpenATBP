package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.ISFSEventParam;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSArray;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSArray;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;
import xyz.openatbp.extension.ATBPExtension;

public class JoinZoneEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        User user = (User) event.getParameter(SFSEventParam.USER);
        trace("Joined zone! " + user.getDump());
        ISFSObject data = new SFSObject();

        parentExt.send("cmd_player_loaded", data, user);
    }
}

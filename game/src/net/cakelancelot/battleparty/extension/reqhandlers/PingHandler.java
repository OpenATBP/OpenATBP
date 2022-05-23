package net.cakelancelot.battleparty.extension.reqhandlers;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import net.cakelancelot.battleparty.extension.ATBPExtension;

public class PingHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(sender.getId()));
        data.putInt("msg_type", params.getInt("msg_type"));
        data.putFloat("x", params.getFloat("x"));
        data.putFloat("y", params.getFloat("y"));
        parentExt.send("cmd_mini_map_message", data, sender);
    }
}


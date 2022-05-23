package net.cakelancelot.battleparty.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import net.cakelancelot.battleparty.extension.ATBPExtension;

public class ClientReadyHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        ISFSObject data = new SFSObject();
        parentExt.send("cmd_match_starting", data, sender);
    }
}

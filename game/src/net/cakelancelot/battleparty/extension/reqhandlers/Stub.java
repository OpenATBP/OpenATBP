package net.cakelancelot.battleparty.extension.reqhandlers;

import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;

@MultiHandler
public class Stub extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        String command = params.getUtfString(SFSExtension.MULTIHANDLER_REQUEST_ID);
        trace(command + params.getDump());
    }
}

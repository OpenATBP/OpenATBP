package xyz.openatbp.extension.evthandlers;

import com.smartfoxserver.bitswarm.sessions.ISession;
import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSConstants;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;

public class UserLoginEventHandler extends BaseServerEventHandler {

    @Override
    public void handleServerEvent(ISFSEvent isfsEvent) {
        ISession session = (ISession) isfsEvent.getParameter(SFSEventParam.SESSION);

        ISFSObject loginData =
                (ISFSObject)
                        isfsEvent.getParameter(
                                SFSEventParam.LOGIN_IN_DATA); // Given from the Lobby Server
        trace(loginData.getDump());
        session.setProperty("name", loginData.getUtfString("name").toUpperCase());
        session.setProperty("tegid", loginData.getUtfString("tid"));
        session.setProperty("id", loginData.getUtfString("authid"));
        ISFSObject outData = (ISFSObject) isfsEvent.getParameter(SFSEventParam.LOGIN_OUT_DATA);
        outData.putUtfString(
                SFSConstants.NEW_LOGIN_NAME,
                loginData
                        .getUtfString("name")
                        .toUpperCase()); // Changes the default "Guest" name to player's name
    }
}

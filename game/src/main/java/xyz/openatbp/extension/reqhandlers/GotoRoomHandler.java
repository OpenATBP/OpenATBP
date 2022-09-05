package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.SFSRoom;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSJoinRoomException;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GotoRoomHandler extends BaseClientRequestHandler {

    @Override
    public void handleClientRequest(User sender, ISFSObject params){
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        trace(params.getDump());
        List<UserVariable> userVariables = new ArrayList<>();
        SFSUserVariable avatar = new SFSUserVariable("avatar", params.getUtfString("avatar"));
        SFSUserVariable belt = new SFSUserVariable("backpack", params.getUtfString("belt"));
        SFSUserVariable team = new SFSUserVariable("team", params.getUtfString("team"));
        SFSUserVariable name = new SFSUserVariable("name", sender.getSession().getProperty("name"));
        SFSUserVariable tid = new SFSUserVariable("tegid", sender.getSession().getProperty("tegid"));
        SFSUserVariable id = new SFSUserVariable("authid", sender.getSession().getProperty("id"));
        userVariables.add(avatar);
        userVariables.add(belt);
        userVariables.add(team);
        userVariables.add(name);
        userVariables.add(tid);
        parentExt.getApi().setUserVariables(sender, userVariables);

        Room requestedRoom = sender.getZone().getRoomByName(params.getUtfString("room_id"));
        ISFSObject userData = new SFSObject();
        int authID = Integer.parseInt(id.getStringValue());
        userData.putInt("id", sender.getId());
        userData.putUtfString("name", name.getStringValue());
        userData.putUtfString("champion", avatar.getStringValue());
        userData.putInt("team", 0); //Change up team data
        userData.putUtfString("tid", tid.getStringValue());
        userData.putUtfString("backpack", belt.getStringValue());
        userData.putInt("elo", 1700); //Database
        parentExt.send("cmd_add_user", userData, sender);
        requestedRoom.setPassword("");
        try {
            parentExt.getApi().joinRoom(sender, requestedRoom);
        } catch (SFSJoinRoomException e) {
            throw new RuntimeException(e);
        }
    }
}

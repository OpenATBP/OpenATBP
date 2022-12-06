package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.api.CreateRoomSettings;
import com.smartfoxserver.v2.config.ZoneSettings;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.SFSRoom;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSCreateRoomException;
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
        sender.getSession().setProperty("room_id", params.getUtfString("room_id"));
        userVariables.add(avatar);
        userVariables.add(belt);
        userVariables.add(team);
        userVariables.add(name);
        userVariables.add(tid);
        parentExt.getApi().setUserVariables(sender, userVariables);

        Room requestedRoom = sender.getZone().getRoomByName(params.getUtfString("room_id").substring(0,10));
        boolean createdRoom = false;
        if(requestedRoom == null){
            CreateRoomSettings settings = new CreateRoomSettings();
            settings.setName(params.getUtfString("room_id").substring(0,10));
            settings.setGame(true);
            if(params.getUtfString("room_id").contains("practice")){
                settings.setMaxUsers(1);
                settings.setGroupId("Practice");
            }else if(params.getUtfString("room_id").contains("pve")){
                settings.setMaxUsers(3);
                settings.setGroupId("PVE");
            }else{
                settings.setMaxUsers(6);
                settings.setGroupId("PVP");
            }
            try {
                requestedRoom = parentExt.getApi().createRoom(sender.getZone(), settings, sender);
                createdRoom = true;
            } catch (SFSCreateRoomException e) {
                throw new RuntimeException(e);
            }
        }
        requestedRoom.setPassword("");
        try {
            if(!createdRoom) parentExt.getApi().joinRoom(sender, requestedRoom);
        } catch (SFSJoinRoomException e) {
            throw new RuntimeException(e);
        }
    }
}

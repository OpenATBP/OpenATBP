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
    public void handleClientRequest(User sender, ISFSObject params){ //Called when player is trying to join a match
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        List<UserVariable> userVariables = new ArrayList<>();
        ISFSObject playerInfo = new SFSObject(); //Player info from champ select
        playerInfo.putUtfString("avatar",params.getUtfString("avatar"));
        playerInfo.putUtfString("backpack",params.getUtfString("belt"));
        playerInfo.putUtfString("team",params.getUtfString("team"));
        playerInfo.putUtfString("name", (String) sender.getSession().getProperty("name"));
        playerInfo.putUtfString("tegid", (String) sender.getSession().getProperty("tegid"));
        SFSUserVariable playerVar = new SFSUserVariable("player",playerInfo);
        ISFSObject location = new SFSObject(); //Will need to be changed when we get actual spawn points made
        location.putFloat("x",0);
        location.putFloat("z", 0);
        UserVariable locVar = new SFSUserVariable("location",location);
        sender.getSession().setProperty("room_id", params.getUtfString("room_id"));
        userVariables.add(playerVar);
        userVariables.add(locVar);
        parentExt.getApi().setUserVariables(sender, userVariables);
        String name = params.getUtfString("room_id");
        if(name.length() >= 10) name = name.substring(0,10);
        Room requestedRoom = sender.getZone().getRoomByName(name); //Tries to find existing room
        boolean createdRoom = false;
        if(requestedRoom == null){ //If the room is not created yet, create it.
            CreateRoomSettings settings = new CreateRoomSettings();
            settings.setName(name);
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
            if(!createdRoom) parentExt.getApi().joinRoom(sender, requestedRoom); //If you did not create the room, join the existing one.
        } catch (SFSJoinRoomException e) {
            throw new RuntimeException(e);
        }
    }
}

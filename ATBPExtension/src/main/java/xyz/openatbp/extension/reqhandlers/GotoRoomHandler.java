package xyz.openatbp.extension.reqhandlers;

import java.util.ArrayList;
import java.util.List;

import com.smartfoxserver.v2.api.CreateRoomSettings;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSCreateRoomException;
import com.smartfoxserver.v2.exceptions.SFSJoinRoomException;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.MapData;

public class GotoRoomHandler extends BaseClientRequestHandler {

    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Called when player is trying to join a match
        Console.debugLog("trace!");
        trace(params.getDump());
        int team = 1;
        if (params.getUtfString("team").equalsIgnoreCase("purple")) team = 0;
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        List<UserVariable> userVariables = new ArrayList<>();
        ISFSObject playerInfo = new SFSObject(); // Player info from champ select
        playerInfo.putUtfString("avatar", params.getUtfString("avatar"));
        playerInfo.putUtfString("backpack", params.getUtfString("belt"));
        playerInfo.putInt("team", team);
        playerInfo.putUtfString("name", (String) sender.getSession().getProperty("name"));
        playerInfo.putUtfString("tegid", (String) sender.getSession().getProperty("tegid"));
        playerInfo.putInt(
                "elo", parentExt.getElo((String) sender.getSession().getProperty("tegid")));
        playerInfo.putBool("isTournamentEligible", params.getBool("isTournamentEligible"));
        SFSUserVariable playerVar = new SFSUserVariable("player", playerInfo);
        ISFSObject location =
                new SFSObject(); // Will need to be changed when we get actual spawn points made
        String room_id = params.getUtfString("room_id");
        float practiceX = (float) MapData.L1_PURPLE_SPAWNS[0].getX();
        float practiceZ = (float) MapData.L1_PURPLE_SPAWNS[0].getY();
        float battleLabX = (float) MapData.L2_PURPLE_SPAWNS[0].getX();
        float battleLabZ = (float) MapData.L2_PURPLE_SPAWNS[0].getY();
        float x = room_id.contains("practice") ? practiceX : battleLabX;
        float z = room_id.contains("practice") ? practiceZ : battleLabZ;
        if (team == 1) x *= -1;
        location.putFloat("x", x);
        location.putFloat("z", z);
        ISFSObject p1 = new SFSObject();
        p1.putFloat("x", x);
        p1.putFloat("z", z);
        location.putSFSObject("p1", p1);
        location.putFloat("time", 0);
        location.putFloat("speed", 0);
        UserVariable locVar = new SFSUserVariable("location", location);
        ISFSObject actorInfo = new SFSObject();
        actorInfo.putBool("autoAttack", true);
        actorInfo.putBool("autoLevel", false);
        UserVariable actorVar = new SFSUserVariable("champion", actorInfo);
        sender.getSession().setProperty("room_id", params.getUtfString("room_id"));
        userVariables.add(playerVar);
        userVariables.add(locVar);
        userVariables.add(actorVar);
        parentExt.getApi().setUserVariables(sender, userVariables);
        String name = params.getUtfString("room_id");
        Room requestedRoom = sender.getZone().getRoomByName(name); // Tries to find existing room
        boolean createdRoom = false;
        if (requestedRoom == null) { // If the room is not created yet, create it.
            CreateRoomSettings settings = new CreateRoomSettings();
            settings.setName(name);
            settings.setGame(true);
            if (params.getUtfString("room_id").contains("pra")
                    || params.getUtfString("room_id").contains("tutorial")) {
                settings.setMaxUsers(1);
                settings.setGroupId("Practice");
            } else if (params.getUtfString("room_id").contains("custom")) {
                String[] roomIDSplit = params.getUtfString("room_id").split("_");
                int roomSize =
                        Integer.parseInt(roomIDSplit[roomIDSplit.length - 1].replace("p", ""));
                settings.setMaxUsers(roomSize);
                if (roomSize != 2) settings.setGroupId("PVE");
                else settings.setGroupId("Practice");
            } else if (params.getUtfString("room_id").contains("3p")) { // Bot game mode
                settings.setMaxUsers(2); // TODO: Testing value
                settings.setGroupId("PVE");
            } else if (params.getUtfString("room_id").contains("6p")) {
                settings.setMaxUsers(6);
                settings.setGroupId("PVP");
            } else {
                settings.setMaxUsers(1);
                settings.setGroupId("PVE");
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
            if (!createdRoom) {
                if ((int) requestedRoom.getProperty("state")
                        == 0) // TODO: Desync may cause this to be null, even when it shouldn't?
                parentExt
                            .getApi()
                            .joinRoom(
                                    sender,
                                    requestedRoom); // If you did not create the room, join the
                // existing one.
            }
        } catch (SFSJoinRoomException e) {
            throw new RuntimeException(e);
        }
    }
}

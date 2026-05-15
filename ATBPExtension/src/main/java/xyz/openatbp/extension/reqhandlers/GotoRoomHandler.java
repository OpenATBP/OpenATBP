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
import xyz.openatbp.extension.game.RoomGroup;

public class GotoRoomHandler extends BaseClientRequestHandler {

    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Called when player is trying to join a match
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
        ISFSObject location = new SFSObject();

        location.putFloat("x", 0f);
        location.putFloat("z", 0f);
        ISFSObject p1 = new SFSObject();
        p1.putFloat("x", 0f);
        p1.putFloat("z", 0f);
        location.putSFSObject("p1", p1);
        location.putFloat("time", 0f);
        location.putFloat("speed", 0f);
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
            String roomId = params.getUtfString("room_id");
            Console.debugLog("Room ID: " + roomId);

            if (roomId.contains("custom")) {
                String lastPart = roomId.split("_")[2];
                int roomSize = Integer.parseInt(lastPart.replace("p", ""));
                Console.debugLog("Room size: " + roomSize);
                settings.setMaxUsers(roomSize);

                if (roomSize > 4) {
                    settings.setGroupId(RoomGroup.CUSTOM_BATTLE_LAB.name());
                } else {
                    settings.setGroupId(RoomGroup.CUSTOM_CANDY_STREETS.name());
                }
            } else if (roomId.contains("tutorial")) {
                settings.setGroupId(RoomGroup.TUTORIAL.name());
                settings.setMaxUsers(1);
            } else if (roomId.contains("practice")) {
                settings.setGroupId(RoomGroup.PRACTICE.name());
                settings.setMaxUsers(1);

            } else if (roomId.contains("3p")) {
                // 3vs3 Bot game mode
                settings.setGroupId(RoomGroup.PVB.name());
                settings.setMaxUsers(3);

            } else if (roomId.contains("6p")) {
                settings.setGroupId(RoomGroup.RANKED.name());
                settings.setMaxUsers(6);

            } else {
                settings.setGroupId(RoomGroup.PVB.name());
                settings.setMaxUsers(1);
            }
            try {
                requestedRoom = parentExt.getApi().createRoom(sender.getZone(), settings, sender);
                createdRoom = true;
            } catch (SFSCreateRoomException e) { // TODO: Disconnect all players if this occurs
                throw new RuntimeException(e);
            }
        }
        requestedRoom.setPassword("");
        try {
            if (!createdRoom) {
                Object stateProperty = requestedRoom.getProperty("state");

                if (stateProperty == null) {
                    Console.debugLog(
                            "Room "
                                    + requestedRoom.getName()
                                    + " has null state, rejecting join for "
                                    + sender.getName());
                    return;
                }

                int state = (int) stateProperty;

                if (state == 0) {
                    parentExt.getApi().joinRoom(sender, requestedRoom);
                } else {
                    // Game already started, reject
                    Console.logWarning(
                            sender.getName()
                                    + " tried to join room "
                                    + requestedRoom.getName()
                                    + " but game already started (state="
                                    + state
                                    + ")");
                }
            }
        } catch (SFSJoinRoomException e) {
            Console.debugLog(
                    "SFSJoinRoomException for " + sender.getName() + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

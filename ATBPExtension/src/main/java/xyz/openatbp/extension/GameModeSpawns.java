package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;

public class GameModeSpawns {

    public static void spawnTowersForMode(Room room, ATBPExtension parentExt) {
        String groupId = room.getGroupId();

        switch (groupId) {
            case "Tutorial":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(0, 1, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(1, 4, groupId));
                break;

            case "Practice":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(0, 1, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(1, 4, groupId));

                ExtensionCommands.createActor(
                        parentExt, room, MapData.getBaseTowerActorData(0, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getBaseTowerActorData(1, groupId));
                break;

            case "PVE":
            case "PVP":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(0, 1, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(0, 2, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(1, 1, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getTowerActorData(1, 2, groupId));

                ExtensionCommands.createActor(
                        parentExt, room, MapData.getBaseTowerActorData(0, groupId));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getBaseTowerActorData(1, groupId));
                break;
        }
    }

    public static void spawnAltarsForMode(Room room, ATBPExtension parentExt) {
        String groupId = room.getGroupId();

        switch (groupId) {
            case "Tutorial":
            case "Practice":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getAltarActorData(0, room.getGroupId()));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getAltarActorData(1, room.getGroupId()));
                break;

            case "PVE":
            case "PVP":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getAltarActorData(0, room.getGroupId()));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getAltarActorData(1, room.getGroupId()));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getAltarActorData(2, room.getGroupId()));
                break;
        }
    }

    public static void spawnHealthForMode(Room room, ATBPExtension parentExt) {
        String groupId = room.getGroupId();

        switch (groupId) {
            case "Tutorial":
            case "Practice":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), -1));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), -1));
                break;

            case "PVE":
            case "PVP":
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 0));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 1));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(0, room.getGroupId(), 2));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 0));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 1));
                ExtensionCommands.createActor(
                        parentExt, room, MapData.getHealthActorData(1, room.getGroupId(), 2));
                break;
        }
    }
}

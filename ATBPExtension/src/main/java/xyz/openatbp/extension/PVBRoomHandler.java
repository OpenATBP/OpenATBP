package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.List;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.RoomGroup;
import xyz.openatbp.extension.game.actors.Bot;

public class PVBRoomHandler extends MainMapRoomHandler {
    public PVBRoomHandler(
            ATBPExtension parentExt, Room room, Point2D[] mapBoundary, List<Point2D[]> obstacles) {
        super(parentExt, room, mapBoundary, obstacles);

        RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());
        if (roomGroup == RoomGroup.PVB) {
            List<ISFSObject> botProfiles = (List<ISFSObject>) room.getProperty("botProfiles");

            if (botProfiles != null) {
                for (int i = 0; i < botProfiles.size(); i++) {
                    ISFSObject botProfile = botProfiles.get(i);

                    Bot b =
                            GameModeSpawns.createSpecificBot(
                                    parentExt,
                                    room,
                                    botProfile.getInt("botId"),
                                    botProfile.getUtfString("name"),
                                    botProfile.getUtfString("avatar"),
                                    botProfile.getInt("team"),
                                    botProfile.getUtfString("backpack"),
                                    GameMap.BATTLE_LAB);

                    if (b != null) {
                        bots.add(b);
                        endGameChampions.put(botProfile.getInt("botId"), b);
                    }
                }
            }
        }
    }
}

package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.actors.*;

public class MainMapRoomHandler extends RoomHandler {
    public MainMapRoomHandler(
            ATBPExtension parentExt, Room room, Point2D[] mapBoundary, List<Point2D[]> obstacles) {
        super(
                parentExt,
                room,
                GameManager.L2_SPAWNS,
                MapData.NORMAL_HP_SPAWN_RATE,
                mapBoundary,
                obstacles);
        baseTowers.add(new BaseTower(parentExt, room, "purple_tower3", 0));
        baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));

        HashMap<String, Point2D> towers0 = MapData.getMainMapTowerData(0);
        HashMap<String, Point2D> towers1 = MapData.getMainMapTowerData(1);

        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }
    }

    @Override
    public void handleMinionSpawns() {
        int minionWave = secondsRan / 30;
        if (minionWave != this.currentMinionWave) {
            int minionNum = secondsRan % 10;
            if (minionNum == 4) this.currentMinionWave = minionWave;
            if (minionNum <= 3) {
                this.addMinion(GameMap.BATTLE_LAB, 1, minionNum, minionWave, 0);
                this.addMinion(GameMap.BATTLE_LAB, 0, minionNum, minionWave, 0);
                this.addMinion(GameMap.BATTLE_LAB, 1, minionNum, minionWave, 1);
                this.addMinion(GameMap.BATTLE_LAB, 0, minionNum, minionWave, 1);

            } else if (minionNum == 4) {
                for (int i = 0; i < 2; i++) { // i = lane
                    for (int g = 0; g < 2; g++) {
                        if (!this.hasSuperMinion(i, g) && this.canSpawnSupers(g))
                            this.addMinion(GameMap.BATTLE_LAB, g, minionNum, minionWave, i);
                    }
                }
            }
        }
    }

    @Override
    public void handleAltars() {
        handleAltarsForMode(3);
    }

    @Override
    public Point2D getAltarLocation(int altar) {
        double altar_x = 0d;
        double altar_y = 0d;

        if (altar == 0) {
            altar_x = MapData.L2_TOP_ALTAR[0];
            altar_y = MapData.L2_TOP_ALTAR[1];
        } else if (altar == 2) {
            altar_x = MapData.L2_BOT_ALTAR[0];
            altar_y = MapData.L2_BOT_ALTAR[1];
        }
        return new Point2D.Double(altar_x, altar_y);
    }

    @Override
    public int getAltarStatus(Point2D location) {
        Point2D botAltar = new Point2D.Float(MapData.L2_BOT_ALTAR[0], MapData.L2_BOT_ALTAR[1]);
        Point2D midAltar = new Point2D.Float(0f, 0f);
        if (location.equals(botAltar)) return this.altarStatus[2];
        else if (location.equals(midAltar)) return this.altarStatus[1];
        else return this.altarStatus[0];
    }

    @Override
    public void handleAltarGameScore(int capturingTeam, int altarIndex) {
        if (ranked) {
            Point2D altarLocation = getAltarLocation(altarIndex);
            List<UserActor> userActorsInRadius =
                    Champion.getUserActorsInRadius(this, altarLocation, 2);

            List<UserActor> eligiblePlayers =
                    userActorsInRadius.stream()
                            .filter(ua -> ua.getTeam() == capturingTeam)
                            .filter(ua -> ua.getHealth() > 0)
                            .collect(Collectors.toList());

            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            for (UserActor ua : eligiblePlayers) {
                if (altarIndex == 1) {
                    ua.addGameStat("score", 15);
                } else {
                    ua.addGameStat("score", 10);
                }

                String tegID = (String) ua.getUser().getSession().getProperty("tegid");
                Bson filter = eq("user.TEGid", tegID);
                Bson updateOperation = Updates.inc("player.altars", 1);
                UpdateOptions options = new UpdateOptions().upsert(true);
                Console.debugLog(playerData.updateOne(filter, updateOperation, options));
            }
        }
    }

    @Override
    public Point2D getHealthLocation(int num) {
        float x = MapData.L2_BOT_BLUE_HEALTH[0];
        float z = MapData.L2_BOT_BLUE_HEALTH[1];
        switch (num) {
            case 0:
                z *= -1;
                break;
            case 2:
                x = MapData.L2_LEFT_HEALTH[0];
                z = MapData.L2_LEFT_HEALTH[1];
                break;
            case 3:
                x *= -1;
                z *= -1;
                break;
            case 4:
                x *= -1;
                break;
            case 5:
                x = MapData.L2_LEFT_HEALTH[0] * -1;
                z = MapData.L2_LEFT_HEALTH[1];
                break;
        }
        return new Point2D.Float(x, z);
    }

    @Override
    public HashMap<Integer, Point2D> getFountainsCenter() {
        Point2D blueCenter = new Point2D.Float(50.16f, 0f);
        Point2D purpleCenter = new Point2D.Float(-50.16f, 0f);

        HashMap<Integer, Point2D> centers = new HashMap<>();
        centers.put(0, purpleCenter);
        centers.put(1, blueCenter);
        return centers;
    }
}

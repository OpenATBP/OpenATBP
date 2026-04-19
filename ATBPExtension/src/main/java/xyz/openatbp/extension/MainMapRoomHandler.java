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
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;

public class MainMapRoomHandler extends RoomHandler {
    public static final int BASE_ACCOUNT_EXP_VALUE = 10;
    public static final int WINNER_ACCOUNT_EXP_INCREASE = 5;
    private HashMap<User, UserActor> dcPlayers = new HashMap<>();
    private final boolean IS_RANKED_MATCH = room.getGroupId().equals("RANKED");

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
        if (IS_RANKED_MATCH) {
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
    public void spawnMonster(String monster) {
        float x = 0;
        float z = 0;
        String avatar = monster;
        if (monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("ironowls")) {
            char[] abc = {'a', 'b', 'c'};
            for (int i = 0; i < 3; i++) {
                // Gnomes and owls have three different mobs so need to be spawned in
                // triplets
                if (monster.equalsIgnoreCase("gnomes")) {
                    avatar = "gnome_" + abc[i];
                    x = (float) MapData.L2_GNOMES[i].getX();
                    z = (float) MapData.L2_GNOMES[i].getY();

                } else {
                    avatar = "ironowl_" + abc[i];
                    x = (float) MapData.L2_OWLS[i].getX();
                    z = (float) MapData.L2_OWLS[i].getY();
                }

                String id = avatar + "_" + Math.random();

                // remove first for safety
                campMonsters.removeIf(m -> m.getId().equalsIgnoreCase(id));

                Point2D spawnLoc = new Point2D.Float(x, z);
                ExtensionCommands.createActor(parentExt, room, id, avatar, spawnLoc, 0f, 2);
                campMonsters.add(new Monster(parentExt, room, id, spawnLoc, avatar));
            }
        } else if (monster.length() > 3) {
            switch (monster) {
                case "hugwolf":
                    x = MapData.HUGWOLF[0];
                    z = MapData.HUGWOLF[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.HUGWOLF, avatar));
                    break;
                case "grassbear":
                    x = MapData.GRASSBEAR[0];
                    z = MapData.GRASSBEAR[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.GRASSBEAR, avatar));
                    break;
                case "keeoth":
                    x = MapData.L2_KEEOTH[0];
                    z = MapData.L2_KEEOTH[1];
                    campMonsters.add(new Keeoth(parentExt, room, MapData.L2_KEEOTH, avatar));
                    break;
                case "goomonster":
                    x = MapData.L2_GOOMONSTER[0];
                    z = MapData.L2_GOOMONSTER[1];
                    avatar = "goomonster";
                    campMonsters.add(
                            new GooMonster(parentExt, room, MapData.L2_GOOMONSTER, avatar));
                    break;
            }
            Point2D spawnLoc = new Point2D.Float(x, z);
            ExtensionCommands.createActor(
                    this.parentExt, this.room, avatar, avatar, spawnLoc, 0f, 2);
            ExtensionCommands.moveActor(
                    this.parentExt, this.room, avatar, spawnLoc, spawnLoc, 5f, false);
        }
    }

    private boolean tripletCampCleared(String monsterName) {
        List<Monster> triplet =
                getCampMonsters().stream()
                        .filter(m -> m.getId().contains(monsterName))
                        .collect(Collectors.toList());
        triplet.removeIf(Actor::isDead);
        return triplet.isEmpty();
    }

    @Override
    public void handleSpawnDeath(Actor a) {
        // Console.debugLog("The room has killed " + a.getId());
        String monster = a.getId().split("_")[0];

        for (String s : GameManager.L2_SPAWNS) {
            if (!s.contains(monster)) continue;

            if ((!s.contains("gnomes") && !s.contains("owls")) || tripletCampCleared(monster)) {
                room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                return;
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

    public List<Actor> getCompanions() {
        return this.companions;
    }

    public void addCompanion(Actor a) {
        this.companions.add(a);
    }

    public void removeCompanion(Actor a) {
        this.companions.remove(a);
    }

    public void printActors() {
        for (Actor a : this.getActors()) {
            Console.log(
                    "ROOM: "
                            + this.room.getName()
                            + " |  TYPE: "
                            + a.getActorType().toString()
                            + " | ID: "
                            + (a.getActorType() == ActorType.PLAYER
                                    ? a.getDisplayName()
                                    : a.getId())
                            + " | "
                            + a.getHealth());
        }
    }

    public void addProjectile(Projectile p) {
        this.activeProjectiles.add(p);
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

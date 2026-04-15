package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Random;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class BotMapConfig {
    public static final float MIN_DIFFERENCE = 0.1f;
    public static final float MAX_DIFFERENCE = 1f;
    public final Point2D respawnPoint;
    public final Point2D offenseAltar;
    public final Point2D defenseAltar;
    public final Point2D defenseAltar2;
    public final Point2D gnomesLocation;
    public final Point2D owlsLocation;
    public final Point2D grassBearLocation;
    public final Point2D hugWolfLocation;
    public final Point2D gooLocation;
    public final Point2D keeothLocation;
    public final HashMap<String, Point2D> hpPacks;
    public final Point2D[] enemyTowers;
    public final Point2D[] allyTowers;
    public final Point2D enemyNexus;
    public final Point2D allyNexus;
    public final Point2D[] topLanePath;
    public final Point2D[] botLanePath;
    public final Point2D[] midLanePath;

    public final RoomGroup roomGroup;

    public BotMapConfig(
            RoomGroup roomGroup,
            Point2D respawnPoint,
            Point2D offenseAltar,
            Point2D defenseAltar,
            Point2D defenseAltar2,
            Point2D gnomesLocation,
            Point2D owlsLocation,
            Point2D grassBearLocation,
            Point2D hugWolfLocation,
            Point2D gooLocation,
            Point2D keeothLocation,
            HashMap<String, Point2D> hpPacks,
            Point2D[] enemyTowers,
            Point2D[] allyTowers,
            Point2D enemyNexus,
            Point2D allyNexus,
            Point2D[] topLanePath,
            Point2D[] botLanePath,
            Point2D[] midLanePath) {
        this.roomGroup = roomGroup;
        this.respawnPoint = respawnPoint;
        this.offenseAltar = offenseAltar;
        this.defenseAltar = defenseAltar;
        this.defenseAltar2 = defenseAltar2;
        this.gnomesLocation = gnomesLocation;
        this.owlsLocation = owlsLocation;
        this.grassBearLocation = grassBearLocation;
        this.hugWolfLocation = hugWolfLocation;
        this.gooLocation = gooLocation;
        this.keeothLocation = keeothLocation;
        this.hpPacks = hpPacks;
        this.enemyTowers = enemyTowers;
        this.allyTowers = allyTowers;
        this.enemyNexus = enemyNexus;
        this.allyNexus = allyNexus;
        this.topLanePath = topLanePath;
        this.botLanePath = botLanePath;
        this.midLanePath = midLanePath;
    }

    public static BotMapConfig createMainMap(int team) {
        RoomGroup roomGroup = RoomGroup.PVB;
        float spawnX1 = (float) MapData.L2_PURPLE_SPAWNS[0].getX();
        float spawnX2 = (float) MapData.L2_PURPLE_SPAWNS[1].getX();

        float spawnY1 = (float) MapData.L2_PURPLE_SPAWNS[0].getY();
        float spawnY2 = (float) MapData.L2_PURPLE_SPAWNS[1].getY();

        Random rnd = new Random();
        float randomX = spawnX1 + rnd.nextFloat() * (spawnX2 - spawnX1);
        float randomY = spawnY1 + rnd.nextFloat() * (spawnY2 - spawnY1);

        if (team == 1) randomX *= -1;

        Point2D respawnPoint = new Point2D.Float(randomX, randomY);

        Point2D offenseAltar = new Point2D.Float(0, 0);
        Point2D defenseAltar = new Point2D.Float(MapData.L2_BOT_ALTAR[0], MapData.L2_BOT_ALTAR[1]);
        Point2D defenseAltar2 = new Point2D.Float(MapData.L2_TOP_ALTAR[0], MapData.L2_TOP_ALTAR[1]);

        Point2D gnomes = MapData.L2_GNOMES[0];
        Point2D owls = MapData.L2_OWLS[0];
        Point2D grassBear = new Point2D.Float(MapData.GRASSBEAR[0], MapData.GRASSBEAR[1]);
        Point2D hugWolf = new Point2D.Float(MapData.HUGWOLF[0], MapData.HUGWOLF[1]);
        Point2D gooMonster = new Point2D.Float(MapData.L2_GOOMONSTER[0], MapData.L2_GOOMONSTER[1]);
        Point2D keeoth = new Point2D.Float(MapData.L2_KEEOTH[0], MapData.L2_KEEOTH[1]);

        HashMap<String, Point2D> hpPacks = new HashMap<>();
        Point2D bh1 = MapData.L2_BLUE_HP_BASE_1;
        Point2D bh2 = MapData.L2_BLUE_HP_BASE_2;
        Point2D bh3 = MapData.L2_BLUE_HP_JG;

        Point2D ph1 = MapData.L2_PURPLE_HP_BASE_1;
        Point2D ph2 = MapData.L2_PURPLE_HP_BASE_2;
        Point2D ph3 = MapData.L2_PURPLE_HP_JG;

        if (team == 0) {
            hpPacks.put("ph1", ph1);
            hpPacks.put("ph2", ph2);
            hpPacks.put("ph3", ph3);
        } else {
            hpPacks.put("bh1", bh1);
            hpPacks.put("bh2", bh2);
            hpPacks.put("bh3", bh3);
        }

        Point2D[] enemyTowers = new Point2D[2];
        Point2D[] allyTowers = new Point2D[2];

        Point2D purpleTower1 =
                new Point2D.Float(MapData.L2_PURPLE_TOWER_1[0], MapData.L1_PURPLE_TOWER_1[1]);
        Point2D purpleTower2 =
                new Point2D.Float(MapData.L2_PURPLE_TOWER_2[0], MapData.L2_PURPLE_TOWER_2[1]);

        Point2D blueTower1 =
                new Point2D.Float(MapData.L2_BLUE_TOWER_1[0], MapData.L2_BLUE_TOWER_1[1]);
        Point2D blueTower2 =
                new Point2D.Float(MapData.L2_BLUE_TOWER_2[0], MapData.L2_BLUE_TOWER_2[1]);

        setTowers(
                team, enemyTowers, allyTowers, purpleTower1, purpleTower2, blueTower1, blueTower2);

        Point2D enemyNexus;
        Point2D allyNexus;

        Point2D purpleNexus =
                new Point2D.Float(MapData.L2_PURPLE_BASE[0], MapData.L2_PURPLE_BASE[1]);
        Point2D blueNexus = new Point2D.Float(MapData.L2_BLUE_BASE[0], MapData.L2_BLUE_BASE[1]);

        if (team == 0) {
            enemyNexus = blueNexus;
            allyNexus = purpleNexus;
        } else {
            enemyNexus = purpleNexus;
            allyNexus = blueNexus;
        }

        Point2D[] initialTopPath = getMainMapTopPath(team);
        Point2D[] initialBotLanePath = getMainMapBotLanePath(team);

        Point2D[] topLanePath =
                PathFinder.getRandomPointsFromList(initialTopPath, MIN_DIFFERENCE, MAX_DIFFERENCE);
        Point2D[] botLanePath =
                PathFinder.getRandomPointsFromList(
                        initialBotLanePath, MIN_DIFFERENCE, MAX_DIFFERENCE);

        return new BotMapConfig(
                roomGroup,
                respawnPoint,
                offenseAltar,
                defenseAltar,
                defenseAltar2,
                gnomes,
                owls,
                grassBear,
                hugWolf,
                gooMonster,
                keeoth,
                hpPacks,
                enemyTowers,
                allyTowers,
                enemyNexus,
                allyNexus,
                topLanePath,
                botLanePath,
                null);
    }

    private static void setTowers(
            int team,
            Point2D[] enemyTowers,
            Point2D[] allyTowers,
            Point2D purpleTower1,
            Point2D purpleTower2,
            Point2D blueTower1,
            Point2D blueTower2) {
        if (team == 0) {
            allyTowers[0] = purpleTower1;
            allyTowers[1] = purpleTower2;
            enemyTowers[0] = blueTower1;
            enemyTowers[1] = blueTower2;
        } else {
            allyTowers[0] = blueTower1;
            allyTowers[1] = blueTower2;
            enemyTowers[0] = purpleTower1;
            enemyTowers[1] = purpleTower2;
        }
    }

    public static BotMapConfig createPractice(int team) {
        RoomGroup roomGroup = RoomGroup.PRACTICE;
        Point2D respawnPoint = MapData.L1_PURPLE_SPAWNS[0];
        if (team == 1) {
            float x = (float) MapData.L1_PURPLE_SPAWNS[0].getX() * -1;
            float y = (float) MapData.L1_PURPLE_SPAWNS[0].getY();
            respawnPoint = new Point2D.Float(x, y);
        }

        Point2D offenseAltar = new Point2D.Float(0, MapData.L1_AALTAR_Z);
        Point2D defenseAltar = new Point2D.Float(0, MapData.L1_DALTAR_Z);

        Point2D gnomes = MapData.L1_GNOMES[0];
        Point2D owls = MapData.L1_OWLS[0];

        Point2D purpleHpPack =
                new Point2D.Float(MapData.L1_BLUE_HEALTH_X * -1, MapData.L1_BLUE_HEALTH_Z);
        Point2D blueHpPack = new Point2D.Float(MapData.L1_BLUE_HEALTH_X, MapData.L1_BLUE_HEALTH_Z);

        HashMap<String, Point2D> hpPacks = new HashMap<>();

        if (team == 0) hpPacks.put("ph1", purpleHpPack);
        else hpPacks.put("bh1", blueHpPack);

        Point2D[] enemyTowers = new Point2D[2];
        Point2D[] allyTowers = new Point2D[2];

        Point2D purpleTower1 =
                new Point2D.Float(MapData.L1_PURPLE_TOWER_0[0], MapData.L1_PURPLE_TOWER_0[1]);
        Point2D purpleTower2 =
                new Point2D.Float(MapData.L1_PURPLE_TOWER_1[0], MapData.L1_PURPLE_TOWER_1[1]);

        Point2D blueTower1 =
                new Point2D.Float(MapData.L1_BLUE_TOWER_3[0], MapData.L1_BLUE_TOWER_3[1]);
        Point2D blueTower2 =
                new Point2D.Float(MapData.L1_BLUE_TOWER_4[0], MapData.L1_BLUE_TOWER_4[1]);

        setTowers(
                team, allyTowers, enemyTowers, blueTower1, blueTower2, purpleTower1, purpleTower2);

        Point2D enemyNexus;
        Point2D allyNexus;

        Point2D purpleNexus =
                new Point2D.Float(MapData.L1_PURPLE_BASE[0], MapData.L1_PURPLE_BASE[1]);
        Point2D blueNexus = new Point2D.Float(MapData.L1_BLUE_BASE[0], MapData.L1_BLUE_BASE[1]);

        if (team == 0) {
            enemyNexus = blueNexus;
            allyNexus = purpleNexus;
        } else {
            enemyNexus = purpleNexus;
            allyNexus = blueNexus;
        }

        Point2D[] initialMidPath = getPracticeMidLanePath(team);
        Point2D[] midLanePath =
                PathFinder.getRandomPointsFromList(initialMidPath, MIN_DIFFERENCE, MAX_DIFFERENCE);

        return new BotMapConfig(
                roomGroup,
                respawnPoint,
                offenseAltar,
                defenseAltar,
                null,
                gnomes,
                owls,
                null,
                null,
                null,
                null,
                hpPacks,
                enemyTowers,
                allyTowers,
                enemyNexus,
                allyNexus,
                null,
                null,
                midLanePath);
    }

    public boolean isPractice() {
        return keeothLocation == null && defenseAltar2 == null;
    }

    public static Point2D[] getMainMapTopPath(int team) {
        int xChange = team == 0 ? 1 : -1;

        return new Point2D[] {
            new Point2D.Float(-43.652412f * xChange, -0.06321313f),
            new Point2D.Float(-41.70865f * xChange, -3.349341f),
            new Point2D.Float(-37.938713f * xChange, -5.166084f),
            new Point2D.Float(-33.641277f * xChange, -6.303121f),
            new Point2D.Float(-29.408997f * xChange, -7.9134545f),
            new Point2D.Float(-25.648817f * xChange, -9.275917f),
            new Point2D.Float(-24.106987f * xChange, -10.974808f),
            new Point2D.Float(-21.767729f * xChange, -13.343623f),
            new Point2D.Float(-19.00695f * xChange, -14.43825f),
            new Point2D.Float(-15.793901f * xChange, -15.339138f),
            new Point2D.Float(-12.487499f * xChange, -15.890108f),
            new Point2D.Float(-8.948458f * xChange, -16.421585f),
            new Point2D.Float(-5.6983647f * xChange, -16.370483f),
            new Point2D.Float(-2.362982f * xChange, -15.788475f),
            new Point2D.Float(-0.10641184f * xChange, -15.390881f),
            new Point2D.Float(2.8184867f * xChange, -15.410805f),
            new Point2D.Float(5.6353726f * xChange, -15.684006f),
            new Point2D.Float(8.367108f * xChange, -18.517727f),
            new Point2D.Float(11.3410015f * xChange, -18.62761f),
            new Point2D.Float(14.548857f * xChange, -18.902779f),
            new Point2D.Float(17.07646f * xChange, -18.168148f),
            new Point2D.Float(19.842928f * xChange, -18.024849f),
            new Point2D.Float(20.726416f * xChange, -16.111431f),
            new Point2D.Float(23.264925f * xChange, -11.387281f),
            new Point2D.Float(25.852283f * xChange, -9.008344f),
            new Point2D.Float(28.642298f * xChange, -8.000874f),
            new Point2D.Float(29.329967f * xChange, -5.865197f),
            new Point2D.Float(30.663874f * xChange, -4.242617f),
            new Point2D.Float(32.28788f * xChange, 1.1690414f),
            new Point2D.Float(35.160496f * xChange, 2.4829519f),
        };
    }

    public static Point2D[] getMainMapBotLanePath(int team) {
        int xChange = team == 0 ? 1 : -1;

        return new Point2D[] {
            new Point2D.Float(-43.652412f * xChange, -0.06321313f),
            new Point2D.Float(-41.70865f * xChange, 3.349341f),
            new Point2D.Float(-37.938713f * xChange, 5.166084f),
            new Point2D.Float(-33.641277f * xChange, 6.303121f),
            new Point2D.Float(-29.408997f * xChange, 7.9134545f),
            new Point2D.Float(-25.648817f * xChange, 9.275917f),
            new Point2D.Float(-24.106987f * xChange, 10.974808f),
            new Point2D.Float(-21.767729f * xChange, 13.343623f),
            new Point2D.Float(-19.00695f * xChange, 14.43825f),
            new Point2D.Float(-15.793901f * xChange, 15.339138f),
            new Point2D.Float(-12.487499f * xChange, 15.890108f),
            new Point2D.Float(-8.948458f * xChange, 16.421585f),
            new Point2D.Float(-5.6983647f * xChange, 16.370483f),
            new Point2D.Float(-2.362982f * xChange, 15.788475f),
            new Point2D.Float(-0.10641184f * xChange, 15.390881f),
            new Point2D.Float(2.8184867f * xChange, 15.410805f),
            new Point2D.Float(5.6353726f * xChange, 15.684006f),
            new Point2D.Float(8.367108f * xChange, 18.517727f),
            new Point2D.Float(11.3410015f * xChange, 18.62761f),
            new Point2D.Float(14.548857f * xChange, 18.902779f),
            new Point2D.Float(17.07646f * xChange, 18.168148f),
            new Point2D.Float(19.842928f * xChange, 18.024849f),
            new Point2D.Float(20.726416f * xChange, 16.111431f),
            new Point2D.Float(23.264925f * xChange, 11.387281f),
            new Point2D.Float(25.852283f * xChange, 9.008344f),
            new Point2D.Float(28.642298f * xChange, 8.000874f),
            new Point2D.Float(29.329967f * xChange, 5.865197f),
            new Point2D.Float(30.663874f * xChange, 4.242617f),
            new Point2D.Float(32.28788f * xChange, 1.1690414f),
            new Point2D.Float(35.160496f * xChange, 2.4829519f),
        };
    }

    public static Point2D[] getPracticeMidLanePath(int team) {
        int xChange = team == 0 ? 1 : -1;

        return new Point2D[] {
            new Point2D.Float(-49.906734f * xChange, 0.2753178f),
            new Point2D.Float(-47.775944f * xChange, 2.446918f),
            new Point2D.Float(-44.860126f * xChange, 4.041173f),
            new Point2D.Float(-40.97227f * xChange, 4.4682717f),
            new Point2D.Float(-37.12711f * xChange, 4.35965f),
            new Point2D.Float(-32.332226f * xChange, 4.3652153f),
            new Point2D.Float(-28.351206f * xChange, 3.4709952f),
            new Point2D.Float(-23.020746f * xChange, 2.0761473f),
            new Point2D.Float(-17.874735f * xChange, 2.6903737f),
            new Point2D.Float(-14.583063f * xChange, 2.7982206f),
            new Point2D.Float(-11.840126f * xChange, 1.6930435f),
            new Point2D.Float(-8.733493f * xChange, 1.6391969f),
            new Point2D.Float(-4.9007745f * xChange, 2.068954f),
            new Point2D.Float(-1.6867763f * xChange, 2.8675802f),
            new Point2D.Float(1.1455137f * xChange, 2.6341379f),
            new Point2D.Float(4.097055f * xChange, 2.318435f),
            new Point2D.Float(7.053345f * xChange, 1.2659782f),
            new Point2D.Float(9.826621f * xChange, 0.54846704f),
            new Point2D.Float(13.160222f * xChange, 2.560008f),
            new Point2D.Float(15.846022f * xChange, 3.0507753f),
            new Point2D.Float(18.823751f * xChange, 2.8287654f),
            new Point2D.Float(21.778996f * xChange, 0.9668913f),
            new Point2D.Float(24.551266f * xChange, 0.702901f),
            new Point2D.Float(27.888123f * xChange, 1.0106412f),
            new Point2D.Float(30.432793f * xChange, 2.403368f),
            new Point2D.Float(33.546814f * xChange, 2.4189591f),
            new Point2D.Float(35.663166f * xChange, 1.5347338f),
            new Point2D.Float(37.59569f * xChange, 2.5303378f),
            new Point2D.Float(40.613445f * xChange, 4.0108595f),
        };
    }

    public boolean hasGrassBear() {
        return grassBearLocation != null;
    }

    public boolean hasHugWolf() {
        return hugWolfLocation != null;
    }

    public boolean hasGooMonster() {
        return gooLocation != null;
    }

    public boolean hasKeeoth() {
        return keeothLocation != null;
    }

    public boolean hasDefenseAlter2() {
        return defenseAltar2 != null;
    }

    public static void displayLanePath(
            Point2D[] path, ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D p : path) {
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "skully",
                    id + Math.random(),
                    1000 * 60 * 15,
                    (float) p.getX(),
                    (float) p.getY(),
                    false,
                    team,
                    0f);
        }
    }
}

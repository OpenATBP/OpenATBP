package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.HashMap;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

// PURPLE NEGATIVE X, BLUE POSITIVE X

public class MapData {
    public static final float L1_TOWER_Z = (float) 0.55;
    public static final float L1_AALTAR_Z = (float) -9;
    public static final float L1_DALTAR_Z = (float) 9;
    public static final float L1_BLUE_HEALTH_X = (float) 23.3;
    public static final float L1_BLUE_HEALTH_Z = (float) 5.4;
    public static final float L1_GUARDIAN_X = (float) 57.8;
    public static final float L1_PURPLE_GUARDIAN_AREA_Z = (float) -0.5;
    public static final float L1_BLUE_GUARDIAN_AREA_Z = (float) 0;
    public static final float L1_P_GUARDIAN_MODEL_X = -57.8f;
    public static final float L1_B_GUARDIAN_MODEL_X = 57.8f;
    public static final float L1_GUARDIAN_MODEL_Z = -0.4f;
    public static final float L2_GUARDIAN1_X = (float) 52.00;
    public static final float L2_GUARDIAN1_Z = (float) 0;
    public static final float[] L2_TOP_ALTAR = {(float) -13.38, (float) -26.08};
    public static final float[] L2_LEFT_HEALTH = {(float) 12.50, (float) 0.05};
    public static final float[] L2_BOT_BLUE_HEALTH = {(float) 27.10, (float) 2.45};
    public static final float[] L2_BOT_ALTAR = {(float) 14.47, (float) 27.08};
    public static final float[] L2_GOOMONSTER = {(float) -0.6, (float) -26.1};
    public static final float[] L2_KEEOTH = {(float) 0, (float) 25};
    public static final float[] HUGWOLF = {(float) 7.50, (float) 8.50};
    public static final float[] GRASSBEAR = {(float) -9.20, (float) -8.60};

    public static final int NORMAL_HP_SPAWN_RATE = 60;
    public static final int ARAM_HP_SPAWN_RATE = 30;

    public static final Point2D[] L2_OWLS = {
        new Point2D.Double(8.3, -8.6), // 10 points owl
        new Point2D.Double(8.1, -7.6), // 5 points owl
        new Point2D.Double(9.3, -8.5) // 5 points owl
    };

    public static final Point2D[] L2_GNOMES = {
        new Point2D.Double(-9.2, 7.50), // red
        new Point2D.Double(-10.2, 7.40), // blue
        new Point2D.Double(-9, 6.5) // yellow
    };
    public static final Point2D[] L2_PURPLE_SPAWNS = {
        new Point2D.Double(-49.068882, -2.6318564), // top
        new Point2D.Double(-48.066704, -0.07888055), // mid
        new Point2D.Double(-49.068882, 2.6318564) // bot
    };
    public static final Point2D[] L1_PURPLE_SPAWNS = {
        new Point2D.Double(-53.975643, -0.124769), // mid
        new Point2D.Double(-54.975643, -2.67774485), // top
        new Point2D.Double(-54.975643, 2.67774485), // bot
    };
    public static final Point2D[] L1_OWLS = {
        new Point2D.Double(22.911673, -9.451605),
        new Point2D.Double(21.751673, -9.181605),
        new Point2D.Double(21.761673, -10.291605)
    };
    public static final Point2D[] L1_GNOMES = {
        new Point2D.Double(-22.411673, 10.451605),
        new Point2D.Double(-21.251673, 10.181605),
        new Point2D.Double(-21.261673, 11.291605)
    };
    public static final float[] L1_PURPLE_TOWER_0 = {(float) -31.8, (float) 0.55};
    public static final float[] L1_PURPLE_TOWER_1 = {(float) -16.05, (float) 0.55};
    public static final float[] L1_BLUE_TOWER_3 = {(float) 31.7, (float) 0.55};
    public static final float[] L1_BLUE_TOWER_4 = {(float) 16.5, (float) 0.55};
    public static final float[] L1_PURPLE_BASE = {(float) -42.0, (float) 0.6};
    public static final float[] L1_BLUE_BASE = {(float) 42.0, (float) 0.6};
    public static final float[] L2_PURPLE_BASE = {(float) -38.48, (float) 0.0};
    public static final float[] L2_BLUE_BASE = {(float) 38.48, (float) 0.0};
    public static final float[] L2_PURPLE_TOWER_1 = {(float) -26.40, (float) -12.19};
    public static final float[] L2_PURPLE_TOWER_2 = {(float) -26.36, (float) 11.36};
    public static final float[] L2_BLUE_TOWER_1 = {(float) 26.55, (float) -12.89};
    public static final float[] L2_BLUE_TOWER_2 = {(float) 26.41, (float) 11.41};
    public static final float[] L2_PURPLE_BASE_TOWER = {(float) -34.36, (float) 0.0};
    public static final float[] L2_BLUE_BASE_TOWER = {(float) 34.51, (float) 0.0};

    public static ISFSObject getBaseActorData(int team, String roomGroup) {
        float x;
        float z;
        if (roomGroup.equals("Practice")
                || roomGroup.equals("Tutorial")
                || roomGroup.equals("ARAM")) {
            if (team == 0) {
                x = L1_PURPLE_BASE[0];
                z = L1_PURPLE_BASE[1];
            } else {
                x = L1_BLUE_BASE[0];
                z = L1_BLUE_BASE[1];
            }
        } else {
            if (team == 0) {
                x = L2_PURPLE_BASE[0];
                z = L2_PURPLE_BASE[1];
            } else {
                x = L2_BLUE_BASE[0];
                z = L2_BLUE_BASE[1];
            }
        }
        return getBase(team, x, z);
    }

    private static ISFSObject getBase(int team, float x, float z) {
        String actor;
        if (team == 1) {
            actor = "base_blue";
        } else {
            actor = "base_purple";
        }
        ISFSObject base = new SFSObject();
        ISFSObject baseSpawn = new SFSObject();
        base.putUtfString("id", actor);
        base.putUtfString("actor", actor);
        baseSpawn.putFloat("x", x);
        baseSpawn.putFloat("y", (float) 0.0);
        baseSpawn.putFloat("z", z);
        base.putSFSObject("spawn_point", baseSpawn);
        base.putFloat("rotation", (float) 0.0);
        base.putInt("team", team);
        return base;
    }

    public static ISFSObject getBaseTowerActorData(int team, String roomGroup) {
        float x;
        float z;
        String id;
        String towerID = "tower" + (1 + team);
        if (roomGroup.equals("Tutorial")
                || roomGroup.equals("Practice")
                || roomGroup.equals("ARAM")) {
            if (team == 0) {
                x = L1_PURPLE_TOWER_0[0];
                id = "purple_tower0";
            } else {
                x = L1_BLUE_TOWER_3[0];
                id = "blue_tower3";
            }
            z = L1_PURPLE_TOWER_0[1];

        } else {
            if (team == 0) {
                x = L2_PURPLE_BASE_TOWER[0];
                id = "purple_tower3";
            } else {
                x = L2_BLUE_BASE_TOWER[0];
                id = "blue_tower3";
            }
            z = L2_PURPLE_BASE_TOWER[1];
        }
        ISFSObject baseTower = new SFSObject();
        ISFSObject baseTowerSpawn = new SFSObject();
        baseTower.putUtfString("id", id);
        baseTower.putUtfString("actor", towerID);
        baseTowerSpawn.putFloat("x", x);
        baseTowerSpawn.putFloat("y", (float) 0.0);
        baseTowerSpawn.putFloat("z", z);
        baseTower.putSFSObject("spawn_point", baseTowerSpawn);
        baseTower.putFloat("rotation", (float) 0.0);
        baseTower.putInt("team", team);
        return baseTower;
    }

    public static HashMap<String, Point2D> getBaseTowerData(int team, String room) {
        HashMap<String, Point2D> baseTowers = new HashMap<>(2);
        String id;
        float x;
        float z;
        if (room.equalsIgnoreCase("practice") || room.equalsIgnoreCase("ARAM")) {
            if (team == 0) {
                x = L1_PURPLE_TOWER_0[0];
                id = "purple_tower0";
            } else {
                x = L1_BLUE_TOWER_3[0];
                id = "blue_tower3";
            }
            z = L1_TOWER_Z;
        } else {
            if (team == 0) {
                x = L2_PURPLE_BASE_TOWER[0];
                id = "purple_base_tower";
            } else {
                id = "blue_base_tower";
                x = L2_BLUE_BASE_TOWER[0];
            }
            z = L2_PURPLE_BASE_TOWER[1];
        }
        Point2D location = new Point2D.Float(x, z);
        baseTowers.put(id, location);
        return baseTowers;
    }

    public static ISFSObject getTowerActorData(int team, int tower, String roomId) { //
        float x = 0;
        float z = 0;
        String id;
        String towerID = "tower" + (1 + team);
        if (roomId.equals("Practice") || roomId.equals("Tutorial") || roomId.equals("ARAM")) {
            switch (tower) {
                case 1:
                    x = L1_PURPLE_TOWER_1[0]; //
                    break;
                case 4:
                    x = L1_BLUE_TOWER_4[0];
                    break;
            }
            z = L1_TOWER_Z;
        } else {
            switch (tower) {
                case 1:
                    if (team == 0) {
                        x = L2_PURPLE_TOWER_1[0];
                        z = L2_PURPLE_TOWER_1[1];
                    } else {
                        x = L2_BLUE_TOWER_1[0];
                        z = L2_BLUE_TOWER_1[1];
                    }
                    break;
                case 2:
                    if (team == 0) {
                        x = L2_PURPLE_TOWER_2[0];
                        z = L2_PURPLE_TOWER_2[1];
                    } else {
                        x = L2_BLUE_TOWER_2[0];
                        z = L2_BLUE_TOWER_2[1];
                    }
                    break;
            }
        }
        if (team == 1) id = "blue_tower" + tower;
        else id = "purple_tower" + tower;
        ISFSObject towerObj = new SFSObject();
        ISFSObject towerSpawn = new SFSObject();
        towerObj.putUtfString("id", id);
        towerObj.putUtfString("actor", towerID);
        towerSpawn.putFloat("x", x);
        towerSpawn.putFloat("y", (float) 0.0);
        towerSpawn.putFloat("z", z);
        towerObj.putSFSObject("spawn_point", towerSpawn);
        towerObj.putFloat("rotation", (float) 0.0);
        towerObj.putInt("team", team);

        return towerObj;
    }

    public static HashMap<String, Point2D> getMainMapTowerData(int team) {
        HashMap<String, Point2D> towers = new HashMap<>();
        float x = 0;
        float z = 0;
        for (int towerNum = 1; towerNum < 3; towerNum++) {
            switch (towerNum) {
                case 1:
                    if (team == 0) {
                        x = L2_PURPLE_TOWER_1[0];
                        z = L2_PURPLE_TOWER_1[1];
                    } else {
                        x = L2_BLUE_TOWER_1[0];
                        z = L2_BLUE_TOWER_1[1];
                    }
                    break;
                case 2:
                    if (team == 0) {
                        x = L2_PURPLE_TOWER_2[0];
                        z = L2_PURPLE_TOWER_2[1];
                    } else {
                        x = L2_BLUE_TOWER_2[0];
                        z = L2_BLUE_TOWER_2[1];
                    }
                    break;
            }
            String id;
            if (team == 0) {
                id = "purple_tower" + towerNum;
            } else id = "blue_tower" + towerNum;
            Point2D location = new Point2D.Float(x, z);
            towers.put(id, location);
        }
        return towers;
    }

    public static HashMap<String, Point2D> getPTowerActorData(int team) {
        HashMap<String, Point2D> practiceTowers = new HashMap<>();
        float x;
        String id;
        if (team == 0) {
            x = L1_PURPLE_TOWER_1[0];
            id = "purple_tower1";
        } else {
            x = L1_BLUE_TOWER_4[0];
            id = "blue_tower4";
        }
        Point2D location = new Point2D.Float(x, L1_TOWER_Z);
        practiceTowers.put(id, location);
        return practiceTowers;
    }

    public static ISFSObject getAltarActorData(int type, String roomGroup) {
        float x = (float) 0.0;
        float z = (float) 0.0;
        String actorID = "altar_" + type;
        if (roomGroup.equals("Tutorial")
                || roomGroup.equals("Practice")
                || roomGroup.equals("ARAM")) {
            if (type == 0) {
                z = L1_DALTAR_Z;
                actorID = "altar_1";
            } else {
                z = L1_AALTAR_Z;
                actorID = "altar_2";
            }
        } else {
            if (type == 0) {
                x = L2_TOP_ALTAR[0];
                z = L2_TOP_ALTAR[1];
                actorID = "altar_1";
            } else if (type == 2) {
                x = L2_BOT_ALTAR[0];
                z = L2_BOT_ALTAR[1];
                actorID = "altar_1";
            } else {
                actorID = "altar_2";
            }
        }
        String actor = "altar_" + type;
        ISFSObject altar = new SFSObject();
        ISFSObject altarSpawn = new SFSObject();
        altar.putUtfString("id", actor);
        altar.putUtfString("actor", actorID);
        altarSpawn.putFloat("x", x);
        altarSpawn.putFloat("y", (float) 0.0);
        altarSpawn.putFloat("z", z);

        altar.putSFSObject("spawn_point", altarSpawn);
        altar.putFloat("rotation", (float) 0.0);
        altar.putInt("team", 2);
        return altar;
    }

    public static ISFSObject getHealthActorData(int team, String roomGroup, int type) {
        float x = 0;
        float z = 0;
        if (roomGroup.equals("Tutorial")
                || roomGroup.equals("Practice")
                || roomGroup.equals("ARAM")) {
            x = L1_BLUE_HEALTH_X;
            z = L1_BLUE_HEALTH_Z;
            if (team == 0) {
                x *= -1;
                z *= -1;
            }
        } else {
            if (type == 0) {
                x = L2_BOT_BLUE_HEALTH[0];
                z = L2_BOT_BLUE_HEALTH[1];
            } else if (type == 1) {
                x = L2_BOT_BLUE_HEALTH[0];
                z = L2_BOT_BLUE_HEALTH[1] * -1;
            } else if (type == 2) {
                x = L2_LEFT_HEALTH[0];
                z = L2_LEFT_HEALTH[1];
            }
            if (team == 0) {
                x *= -1;
            }
        }
        String id = "health" + team + type;
        ISFSObject health = new SFSObject();
        ISFSObject healthSpawn = new SFSObject();
        health.putUtfString("id", id);
        health.putUtfString("actor", "pickup_health_1");
        healthSpawn.putFloat("x", x);
        healthSpawn.putFloat("y", (float) 0.0);
        healthSpawn.putFloat("z", z);
        health.putSFSObject("spawn_point", healthSpawn);
        health.putFloat("rotation", (float) 0.0);
        health.putInt("team", team);
        return health;
    }

    public static ISFSObject getGuardianActorData(int team, String roomGroup) {
        float x = 0;
        float z = 0;
        if (roomGroup.equals("Tutorial") || roomGroup.equals("Practice")) {
            x = L1_GUARDIAN_X;
            z = team == 1 ? L1_BLUE_GUARDIAN_AREA_Z : L1_PURPLE_GUARDIAN_AREA_Z;
        } else {
            x = L2_GUARDIAN1_X;
            z = L2_GUARDIAN1_Z;
        }
        if (team == 0) x *= -1;
        ISFSObject guardian = new SFSObject();
        ISFSObject guardianSpawn = new SFSObject();
        guardian.putUtfString("id", "gumball" + team);
        guardian.putUtfString("actor", "gumball_guardian");
        guardianSpawn.putFloat("x", x);
        guardianSpawn.putFloat("y", 0f);
        guardianSpawn.putFloat("z", z);
        guardian.putSFSObject("spawn_point", guardianSpawn);
        float rotation = 0f;
        guardian.putFloat("rotation", rotation);
        guardian.putInt("team", team);
        return guardian;
    }

    public static Point2D getGuardianLocationData(int team, String roomGroup) {
        float x = 0;
        float z = 0;
        if (roomGroup.equals("Tutorial")
                || roomGroup.equals("Practice")
                || roomGroup.equals("ARAM")) {
            x = L1_GUARDIAN_X;
            z = team == 1 ? L1_BLUE_GUARDIAN_AREA_Z : L1_PURPLE_GUARDIAN_AREA_Z;
        } else {
            x = L2_GUARDIAN1_X;
            z = L2_GUARDIAN1_Z;
        }
        if (team == 0) x *= -1;
        return new Point2D.Float(x, z);
    }
}

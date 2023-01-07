package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import java.awt.geom.Point2D;
import java.util.Vector;

public class MapData {
    public static final float L1_BASE_X = (float) 42.0;
    public static final float L1_BASE_Z = (float) 0.6;
    public static final float L1_TOWER2_X = (float) 16.5;
    public static final float L1_TOWER_Z = (float) 0.6;
    public static final float L1_TOWER1_X = (float) 31.6;
    public static final float L1_AALTAR_Z = (float) -10.0;
    public static final float L1_DALTAR_Z = (float) 10.0;
    public static final float L1_BLUE_HEALTH_X = (float) 23.3;
    public static final float L1_BLUE_HEALTH_Z = (float) 5.4;
    public static final float L1_GUARDIAN_X = (float) 57.8;
    public static final float L1_GUARDIAN_Z = (float) -0.5;
    public static final float L1_KEEOTH_X = (float) -21.3;
    public static final float L1_KEEOTH_Z = (float) 11.6;
    public static final float L1_OOZE_X = (float) 22.6;
    public static final float L1_OOZE_Z = (float) -10.7;

    public static final float L2_BASE1_X = (float)38.48;
    public static final float L2_BASE1_Z = (float) 0.0;
    public static final float L2_TOWER2_X = (float) 34.51;
    public static final float L2_TOWER2_Z = (float) 0.0;
    public static final float L2_GUARDIAN1_X = (float) 52.68;
    public static final float L2_GUARDIAN1_Z = (float) -0.44;
    public static final float L2_TOP_TOWER1_X = (float) 26.55;
    public static final float L2_TOP_TOWER1_Z = (float) -12.74;
    public static final float[] L2_BOT_TOWER1 = {(float) 26.36, (float) 11.51};
    public static final float[] L2_TOP_ALTAR = {(float) -13.38, (float) -26.08};
    public static final float[] L2_OOZE = {(float) 0.54, (float) -27.42};
    public static final float[] L2_MID_ALTAR = {(float) 0.0, (float) 0.0};
    public static final float[] L2_LEFT_HEALTH = {(float) 14.10, (float) 0.0};
    public static final float[] L2_BOT_BLUE_HEALTH = {(float) 27.68, (float) 3.03};
    public static final float[] L2_BOT_ALTAR = {(float) 14.47, (float) 27.08};
    public static final float[] L2_KEEOTH = {(float) 0.26, (float) 25.82};

    public static final float[] HUGWOLF = {(float)8.15, (float)8.19};
    public static final Point2D[] OWLS = {new Point2D.Double(9.57,-8.95),new Point2D.Double(8.41,-8.68),new Point2D.Double(8.42,-9.79)};
    public static final float[] GRASS = {(float)-8.64, (float)-8.45};
    public static final Point2D[] GNOMES = {new Point2D.Double(-9.29,8.03),new Point2D.Double(-8.10,8.26), new Point2D.Double(-9.47,8.84)};

    public static ISFSObject getBaseActorData(int team, String room){
        float x = 0;
        float z = 0;
        if(room.equalsIgnoreCase("practice")){
            x = L1_BASE_X;
            z = L1_BASE_Z;
        }else{
            x = L2_BASE1_X;
            z = L2_BASE1_Z;
        }
        if(team == 1) x*=-1;
        String actor = "error";
        if(team == 0){
            actor = "base_blue";
        }else{
            actor = "base_purple";
        }
        ISFSObject base = new SFSObject();
        ISFSObject baseSpawn = new SFSObject();
        base.putUtfString("id",actor);
        base.putUtfString("actor",actor);
        baseSpawn.putFloat("x", x);
        baseSpawn.putFloat("y", (float) 0.0);
        baseSpawn.putFloat("z", z);
        base.putSFSObject("spawn_point", baseSpawn);
        base.putFloat("rotation", (float) 0.0);
        base.putInt("team", team);
        return base;
    }

    public static ISFSObject getTowerActorData(int team, int tower, String room){
        float x = 0;
        float z = 0;
        float towerDifference = 0;
        String towerID = "tower"+tower;
        if(room.equalsIgnoreCase("practice")){
            towerDifference = L1_TOWER1_X-L1_TOWER2_X;
            x = (L1_TOWER1_X-(towerDifference*(tower-1)));
            z = L1_TOWER_Z;
        }else{
            if(tower == 1){
                towerID = "tower2";
                x = L2_TOP_TOWER1_X;
                z = L2_TOP_TOWER1_Z;
            }else if(tower == 2){
                towerID = "tower2";
                x = L2_BOT_TOWER1[0];
                z = L2_BOT_TOWER1[1];
            }else if(tower == 3){
                towerID = "tower1";
                x = L2_TOWER2_X;
                z = L2_TOWER2_Z;
            }
        }
        if(team == 1) x*=-1;
        String id = "error";
        if(team == 0){
            id = "blue_tower"+tower;
        }else{
            id = "purple_tower"+tower;
        }
        ISFSObject towerObj = new SFSObject();
        ISFSObject towerSpawn = new SFSObject();
        towerObj.putUtfString("id",id);
        towerObj.putUtfString("actor",towerID);
        towerSpawn.putFloat("x", x);
        towerSpawn.putFloat("y", (float) 0.0);
        towerSpawn.putFloat("z", z);
        towerObj.putSFSObject("spawn_point", towerSpawn);
        towerObj.putFloat("rotation", (float) 0.0);
        towerObj.putInt("team", team);

        return towerObj;
    }

    public static ISFSObject getAltarActorData(int type, String room){
        float x = (float) 0.0;
        float z = (float) 0.0;
        String actorID = "altar_"+type;
        if(room.equalsIgnoreCase("practice")){
            if(type == 1){
                z = L1_DALTAR_Z;
            }else{
                z = L1_AALTAR_Z;
            }
        }else{
            if(type == 0){
                x = L2_TOP_ALTAR[0];
                z = L2_TOP_ALTAR[1];
                actorID = "altar_1";
            }else if(type == 2){
                x = L2_BOT_ALTAR[0];
                z = L2_BOT_ALTAR[1];
                actorID = "altar_1";
            }else{
                actorID = "altar_2";
            }
        }
        String actor = "altar_"+type;
        ISFSObject altar = new SFSObject();
        ISFSObject altarSpawn = new SFSObject();
        altar.putUtfString("id",actor);
        altar.putUtfString("actor",actorID);
        altarSpawn.putFloat("x", x);
        altarSpawn.putFloat("y", (float) 0.0);
        altarSpawn.putFloat("z", z);


        altar.putSFSObject("spawn_point", altarSpawn);
        altar.putFloat("rotation", (float) 0.0);
        altar.putInt("team", 2);
        return altar;
    }

    public static ISFSObject getHealthActorData(int team, String room, int type){
        float x = 0;
        float z = 0;
        if(room.equalsIgnoreCase("practice")){
            x = L1_BLUE_HEALTH_X;
            z = L1_BLUE_HEALTH_Z;
            if(team == 1){
                x*=-1;
                z*=-1;
            }
        }else{
            if(type == 0){
                x = L2_BOT_BLUE_HEALTH[0];
                z = L2_BOT_BLUE_HEALTH[1];
            }else if(type == 1){
                x = L2_BOT_BLUE_HEALTH[0];
                z = L2_BOT_BLUE_HEALTH[1]*-1;
            }else if(type == 2){
                x = L2_LEFT_HEALTH[0];
                z = L2_LEFT_HEALTH[1];
            }
            if(team == 1){
                x*=-1;
            }
        }
        String id = "health"+team+type;
        ISFSObject health = new SFSObject();
        ISFSObject healthSpawn = new SFSObject();
        health.putUtfString("id", id);
        health.putUtfString("actor","pickup_health_1");
        healthSpawn.putFloat("x",x);
        healthSpawn.putFloat("y", (float) 0.0);
        healthSpawn.putFloat("z", z);
        health.putSFSObject("spawn_point", healthSpawn);
        health.putFloat("rotation",(float) 0.0);
        health.putInt("team",team);
        return health;
    }

    public static ISFSObject getGuardianActorData(int team, String room){
        float x = 0;
        float z = 0;
        if(room.equalsIgnoreCase("practice")){
            x = L1_GUARDIAN_X;
            z = L1_GUARDIAN_Z;
        }else{
            x = L2_GUARDIAN1_X;
            z = L2_GUARDIAN1_Z;
        }
        if(team == 1) x*=-1;
        ISFSObject guardian = new SFSObject();
        ISFSObject guardianSpawn = new SFSObject();
        guardian.putUtfString("id","gumball"+team);
        guardian.putUtfString("actor","gumball_guardian");
        guardianSpawn.putFloat("x", x);
        guardianSpawn.putFloat("y", (float) 0.0);
        guardianSpawn.putFloat("z", z);
        guardian.putSFSObject("spawn_point", guardianSpawn);
        float rotation = (float) 0.5;
        if(team == 1) rotation = (float) -0.5;
        guardian.putFloat("rotation", rotation);
        guardian.putInt("team", team);
        return guardian;
    }

}

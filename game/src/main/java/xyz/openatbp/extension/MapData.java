package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

public class MapData {
    public static final float L1_BASE_X = (float) 42.0;
    public static final float L1_BASE_Z = (float) 0.6;
    public static final float L1_TOWER2_X = (float) 16.5;
    public static final float L1_TOWER_Z = (float) 0.6;
    public static final float L1_TOWER1_X = (float) 31.6;
    public static final float L1_AALTAR_X = (float) 0.0;
    public static final float L1_AALTAR_Z = (float) -10.0;
    public static final float L1_DALTAR_X = (float) 0.0;
    public static final float L1_DALTAR_Z = (float) 10.0;
    public static final float L1_BLUE_HEALTH_X = (float) 23.3;
    public static final float L1_BLUE_HEALTH_Z = (float) 5.4;
    public static final float L1_GUARDIAN_X = (float) 57.8;
    public static final float L1_GUARDIAN_Z = (float) -0.5;
    public static final float L1_KEEOTH_X = (float) -21.3;
    public static final float L1_KEEOTH_Z = (float) 11.6;
    public static final float L1_OOZE_X = (float) 22.6;
    public static final float L1_OOZE_Z = (float) -10.7;


    public static ISFSObject getBaseActorData(int team){
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
        float x = L1_BASE_X;
        if(team == 1) x*=-1;
        baseSpawn.putFloat("x", x);
        baseSpawn.putFloat("y", (float) 0.0);
        baseSpawn.putFloat("z", L1_BASE_Z);
        base.putSFSObject("spawn_point", baseSpawn);
        base.putFloat("rotation", (float) 0.0);
        base.putInt("team", team);
        return base;
    }

    public static ISFSObject getTowerActorData(int team, int tower){
        String id = "error";
        if(team == 0){
            id = "blue_tower"+tower;
        }else{
            id = "purple_tower"+tower;
        }
        float towerDifference = L1_TOWER1_X-L1_TOWER2_X;
        ISFSObject towerObj = new SFSObject();
        ISFSObject towerSpawn = new SFSObject();
        towerObj.putUtfString("id",id);
        towerObj.putUtfString("actor","tower"+tower);
        float x = (L1_TOWER1_X-(towerDifference*(tower-1)));
        if(team == 1) x*=-1;
        towerSpawn.putFloat("x", x);
        towerSpawn.putFloat("y", (float) 0.0);
        towerSpawn.putFloat("z", L1_TOWER_Z);
        towerObj.putSFSObject("spawn_point", towerSpawn);
        towerObj.putFloat("rotation", (float) 0.0);
        towerObj.putInt("team", team);

        if(team == 0){
            System.out.println(towerObj.getDump());
        }
        return towerObj;
    }

    public static ISFSObject getAltarActorData(int type){
        String actor = "altar_"+type;
        ISFSObject altar = new SFSObject();
        ISFSObject altarSpawn = new SFSObject();
        altar.putUtfString("id",actor);
        altar.putUtfString("actor",actor);
        altarSpawn.putFloat("x", (float) 0.0);
        altarSpawn.putFloat("y", (float) 0.0);
        if(type == 1){
            altarSpawn.putFloat("z", L1_DALTAR_Z);
        }else{
            altarSpawn.putFloat("z", L1_AALTAR_Z);
        }

        altar.putSFSObject("spawn_point", altarSpawn);
        altar.putFloat("rotation", (float) 0.0);
        altar.putInt("team", 2);
        return altar;
    }

    public static ISFSObject getHealthActorData(int team){
        String id = "health"+team;
        ISFSObject health = new SFSObject();
        ISFSObject healthSpawn = new SFSObject();
        health.putUtfString("id", id);
        health.putUtfString("actor","pickup_health_1");
        float x = L1_BLUE_HEALTH_X;
        float z = L1_BLUE_HEALTH_Z;
        if(team == 1){
            x*=-1;
            z*=-1;
        }
        healthSpawn.putFloat("x",x);
        healthSpawn.putFloat("y", (float) 0.0);
        healthSpawn.putFloat("z", z);
        health.putSFSObject("spawn_point", healthSpawn);
        health.putFloat("rotation",(float) 0.0);
        health.putInt("team",team);
        return health;
    }

    public static ISFSObject getGuardianActorData(int team){
        ISFSObject guardian = new SFSObject();
        ISFSObject guardianSpawn = new SFSObject();
        guardian.putUtfString("id","gumball"+team);
        guardian.putUtfString("actor","gumball_guardian");
        float x = L1_GUARDIAN_X;
        if(team == 1) x*=-1;
        guardianSpawn.putFloat("x", x);
        guardianSpawn.putFloat("y", (float) 0.0);
        guardianSpawn.putFloat("z", L1_GUARDIAN_Z);
        guardian.putSFSObject("spawn_point", guardianSpawn);
        float rotation = (float) 0.5;
        if(team == 1) rotation = (float) -0.5;
        guardian.putFloat("rotation", rotation);
        guardian.putInt("team", team);
        return guardian;
    }

    public static ISFSObject getKeeothActorData(){
        ISFSObject keeoth = new SFSObject();
        ISFSObject keeothSpawn = new SFSObject();
        keeoth.putUtfString("id","keeoth");
        keeoth.putUtfString("actor","keeoth");
        keeothSpawn.putFloat("x", L1_KEEOTH_X);
        keeothSpawn.putFloat("y", (float) 0.0);
        keeothSpawn.putFloat("z", L1_KEEOTH_Z);
        keeoth.putSFSObject("spawn_point", keeothSpawn);
        keeoth.putFloat("rotation", (float) 0.0);
        keeoth.putInt("team", 2);
        return keeoth;
    }

    public static ISFSObject getOozeActorData(){
        ISFSObject ooze = new SFSObject();
        ISFSObject oozeSpawn = new SFSObject();
        ooze.putUtfString("id","ooze_monster");
        ooze.putUtfString("actor","ooze_monster");
        oozeSpawn.putFloat("x", L1_OOZE_X);
        oozeSpawn.putFloat("y", (float) 0.0);
        oozeSpawn.putFloat("z", L1_OOZE_Z);
        ooze.putSFSObject("spawn_point", oozeSpawn);
        ooze.putFloat("rotation", (float) 0.0);
        ooze.putInt("team", 2);
        return ooze;
    }
}

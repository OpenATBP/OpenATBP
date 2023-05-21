package xyz.openatbp.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Point2D;
import java.util.ArrayList;

public class ExtensionCommands {
    /**
        @param id - ID of the actor
        @param bundle - Name of the asset bundle containing the effect
        @param duration - Duration of the effect in milliseconds
        @param fxId - ID of the specific effect (similar to a player id). This does not need to be pre-exisiting.
        @param parent - Is the effect attached to the actor?
        @param emit - ID of location effect is coming from (actorID would spawn where actor is).
        @param orient - Do you want the effect to be in the same direction as the actor?
        @param highlight - Should the effect be highlighted based on the team of the actor?
        @param team - Int value of the team such as blue(1) or purple(0)
     */
    public static void createActorFX(ATBPExtension parentExt, User u, String id, String bundle, int duration, String fxId, boolean parent, String emit, boolean orient, boolean highlight, int team){
        System.out.println("Creating actor FX!");
        ISFSObject data2 = new SFSObject();
        data2.putUtfString("id",id);
        data2.putUtfString("bundle",bundle);
        data2.putInt("duration",duration);
        data2.putUtfString("fx_id",fxId);
        data2.putBool("parent",parent);
        data2.putUtfString("emit",emit);
        data2.putBool("orient",orient);
        data2.putBool("highlight",highlight);
        data2.putInt("team",team);
        parentExt.send("cmd_create_actor_fx",data2,u);
    }

    public static void createActorFX(ATBPExtension parentExt, Room room, String id, String bundle, int duration, String fxId, boolean parent, String emit, boolean orient, boolean highlight, int team){
        for(User u : room.getUserList()){
            System.out.println("Creating actor FX!");
            ISFSObject data2 = new SFSObject();
            data2.putUtfString("id",id);
            data2.putUtfString("bundle",bundle);
            data2.putInt("duration",duration);
            data2.putUtfString("fx_id",fxId);
            data2.putBool("parent",parent);
            data2.putUtfString("emit",emit);
            data2.putBool("orient",orient);
            data2.putBool("highlight",highlight);
            data2.putInt("team",team);
            parentExt.send("cmd_create_actor_fx",data2,u);
        }
    }
    public static void updateActorData(ATBPExtension parentExt, User u, ISFSObject data){
        parentExt.send("cmd_update_actor_data",data,u);
    }

    /**
     *
     * @param id - ID of who is moving
     * @param p - Point2D object of where the actor is
     * @param d - Point2D object of where the actor is going
     * @param speed - Movement speed of actor
     * @param orient - Should the actor face in the direction of their path?
     */
    public static void moveActor(ATBPExtension parentExt, User u, String id, Point2D p, Point2D d, float speed, boolean orient){
        System.out.println(id + " moving!");
        ISFSObject data = new SFSObject();
        data.putUtfString("i",id);
        data.putFloat("px",(float)p.getX());
        data.putFloat("pz",(float)p.getY());
        data.putFloat("dx",(float) d.getX());
        data.putFloat("dz",(float) d.getY());
        data.putFloat("s",speed);
        data.putBool("o",orient);
        parentExt.send("cmd_move_actor",data,u);
    }

    /**
     *
     * @param id - String ID of actor being spawned
     * @param actor - Name of actor/avatar asset for the new actor
     * @param spawn - Point2D object of spawn point
     * @param rotation - Rotation float for how it should be facing
     * @param team - Team int value
     */
    public static void createActor(ATBPExtension parentExt, User u, String id, String actor, Point2D spawn, float rotation, int team){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("actor",actor);
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) spawn.getX());
        spawnPoint.putFloat("y",0f);
        spawnPoint.putFloat("z", (float) spawn.getY());
        data.putSFSObject("spawn_point",spawnPoint);
        data.putFloat("rotation",rotation);
        data.putInt("team",team);
        parentExt.send("cmd_create_actor",data,u);
    }

    public static void createActor(ATBPExtension parentExt, User u, ISFSObject data){
        parentExt.send("cmd_create_actor",data,u);
    }

    /**
     *
     * @param id - ID of who is attacking
     * @param target - ID of who is being attacked
     * @param x - X of player being attacked
     * @param z - Z position of player being attacked
     * @param crit - Does the attack crit?
     * @param orient - Should the player face the target?
     */
    public static void attackActor(ATBPExtension parentExt, User u, String id, String target, float x, float z, boolean crit, boolean orient){
        ISFSObject data = new SFSObject();
        data.putUtfString("id", id);
        data.putUtfString("target_id",target);
        data.putFloat("dest_x",x);
        data.putFloat("dest_y",0f);
        data.putFloat("dest_z",z);
        data.putUtfString("attack_type","basic");
        data.putBool("crit",crit);
        data.putBool("orient",orient);
        parentExt.send("cmd_attack_actor",data,u);
    }

    /**
     *
     * @param target - ID of the target of the damage
     * @param damage - Damage the target is taking
     */
    public static void damageActor(ATBPExtension parentExt, User u, String target, int damage){
        ISFSObject data = new SFSObject();
        data.putUtfString("target_id",target);
        data.putInt("damage",damage);
        parentExt.send("cmd_damage_actor",data,u);
    }

    /**
     *
     * @param fxName - Name of projectile being spawned
     * @param attackerId - ID of Champion spawning the projectile
     * @param targetId - ID of Champion getting hit by the projectile
     * @param emit - Emit location (like hand, leg, etc.)
     * @param hit - Target location
     * @param time - Time for projectile to be spawned
     */
    public static void createProjectileFX(ATBPExtension parentExt, User u, String fxName, String attackerId, String targetId, String emit, String hit, float time){
        ISFSObject data = new SFSObject();
        data.putUtfString("name", fxName);
        data.putUtfString("attacker", attackerId);
        data.putUtfString("target",targetId);
        data.putUtfString("emit",emit);
        data.putUtfString("hit",hit);
        data.putFloat("time",time);
        parentExt.send("cmd_create_projectile_fx",data,u);
    }

    /**
     *
     * @param actorId - ID of the actor targeting another actor
     * @param targetId - ID of the actor being targeted
     */
    public static void setTarget(ATBPExtension parentExt, User u, String actorId, String targetId){
        ISFSObject data = new SFSObject();
        data.putUtfString("actor_id",actorId);
        data.putUtfString("target_id",targetId);
        parentExt.send("cmd_set_target",data,u);
    }

    /**
     *
     * @param tower - Tower identifier to show what tower is being attacked.
     */
    public static void towerAttacked(ATBPExtension parentExt, User u, int tower){
        ISFSObject data = new SFSObject();
        data.putInt("tower",tower);
        parentExt.send("cmd_tower_under_attack",data,u);
    }

    /**
     *
     * @param tower - Tower identifier to show what tower is destroyed.
     */
    public static void towerDown(ATBPExtension parentExt, User u, int tower){
        ISFSObject data = new SFSObject();
        data.putInt("tower",tower);
        parentExt.send("cmd_tower_down",data,u);
    }

    /**
     *
     * @param id - ID of actor who is knocking out another actor
     * @param attackerId - ID of actor being knocked out
     * @param deathTime - Respawn time for the knocked out actor
     */
    public static void knockOutActor(ATBPExtension parentExt, User u, String id, String attackerId, int deathTime){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("attackerId",attackerId);
        data.putInt("deathTime",deathTime);
        parentExt.send("cmd_knockout_actor",data,u);
    }

    /**
     *
     * @param id - ID of actor being destroyed
     */
    public static void destroyActor(ATBPExtension parentExt, User u, String id){
        System.out.println("Destroying: " + id);
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        parentExt.send("cmd_destroy_actor",data,u);
    }

    /**
     *
     * @param id - ID of player receiving the effect?
     * @param bundle - Assetbundle of the effect
     * @param fxId - Special ID given to this specific effect
     * @param duration - Milliseconds of effect duration
     * @param x - X position of effect
     * @param z - Z position of effect
     * @param highlight - Should the effect be highlighted based on team?
     * @param team - Int value of team effect belongs to
     * @param rotation - Float y-rotation for effect
     */
    public static void createWorldFX(ATBPExtension parentExt, User u, String id, String bundle, String fxId, int duration, float x, float z, boolean highlight, int team, float rotation){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("bundle",bundle);
        data.putUtfString("fx_id",fxId);
        data.putInt("duration",duration);
        data.putFloat("x",x);
        data.putFloat("y",0f);
        data.putFloat("z",z);
        data.putBool("highlight",highlight);
        data.putInt("team",team);
        data.putFloat("yrot",rotation);
        parentExt.send("cmd_create_world_fx",data,u);
    }

    public static void createWorldFX(ATBPExtension parentExt, Room room, String id, String bundle, String fxId, int duration, float x, float z, boolean highlight, int team, float rotation){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id",id);
            data.putUtfString("bundle",bundle);
            data.putUtfString("fx_id",fxId);
            data.putInt("duration",duration);
            data.putFloat("x",x);
            data.putFloat("y",0f);
            data.putFloat("z",z);
            data.putBool("highlight",highlight);
            data.putInt("team",team);
            data.putFloat("yrot",rotation);
            parentExt.send("cmd_create_world_fx",data,u);
        }
    }

    /**
     *
     * @param teamA - New score for purple team
     * @param teamB - New score for blue team
     */
    public static void updateScores(ATBPExtension parentExt, User u, int teamA, int teamB){
        ISFSObject data = new SFSObject();
        data.putInt("teamA",teamA);
        data.putInt("teamB",teamB);
        parentExt.send("cmd_update_score",data,u);
    }

    /**
     *
     * @param id - ID of actor being changed
     * @param bundle - File name (minus extension) for asset bundle
     */
    public static void swapActorAsset(ATBPExtension parentExt, User u, String id, String bundle){
        ISFSObject data = new SFSObject();
        data.putUtfString("actor_id",id);
        data.putUtfString("bundle",bundle);
        parentExt.send("cmd_swap_asset",data,u);
    }

    public static void gameOver(ATBPExtension parentExt, User u, double winningTeam) throws JsonProcessingException {
        System.out.println("Calling game over!");
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();
        node.set("teamA", GameManager.getTeamData(0,u.getLastJoinedRoom()));

        node.set("teamB", GameManager.getTeamData(1,u.getLastJoinedRoom()));
        node.set("gloalTeamData", GameManager.getGlobalTeamData(u.getLastJoinedRoom()));
        node.put("winner",winningTeam);
        ISFSObject data = new SFSObject();
        String objectAsText;
        objectAsText = objectMapper.writeValueAsString(node);
        System.out.println(objectAsText);
        data.putUtfString("game_results",objectAsText);
        parentExt.send("cmd_game_over", data, u);
    }

    public static void removeFx(ATBPExtension parentExt, User u, String id){
        ISFSObject data = new SFSObject();
        data.putUtfString("fx_id",id);
        parentExt.send("cmd_remove_fx",data,u);
    }

    public static void removeFx(ATBPExtension parentExt, Room room, String id){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("fx_id",id);
            parentExt.send("cmd_remove_fx",data,u);
        }
    }

    public static void playSound(ATBPExtension parentExt, ArrayList<User> users, String name, Point2D source){
        for(User u : users){
            ISFSObject data = new SFSObject();
            data.putUtfString("name",name);
            data.putUtfString("id", String.valueOf(u.getId()));
            data.putFloat("x",(float)source.getX());
            data.putFloat("y",0f);
            data.putFloat("z",(float)source.getY());
            parentExt.send("cmd_play_sound",data,u);
        }
    }

    public static void playSound(ATBPExtension parentExt, User u, String name, Point2D source){
        ISFSObject data = new SFSObject();
        data.putUtfString("name",name);
        data.putUtfString("id", String.valueOf(u.getId()));
        data.putFloat("x",(float)source.getX());
        data.putFloat("y",0f);
        data.putFloat("z",(float)source.getY());
        parentExt.send("cmd_play_sound",data,u);
    }

    public static void respawnActor(ATBPExtension parentExt, User u, String id){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        parentExt.send("cmd_respawn_actor",data,u);
    }

    public static void actorAbilityResponse(ATBPExtension parentExt, User u, String id, boolean canCast, int cooldown, int gCooldown){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putBool("canCast",canCast);
        data.putInt("cooldown",5000);
        data.putInt("gCooldown",0);
        System.out.println(data.getDump());
        parentExt.send("cmd_actor_ability_response",data,u);
    }

    public static void brushChange(ATBPExtension parentExt, Room room, String id, int brushId){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putInt("brushId",brushId);
        for(User u : room.getUserList()){
            parentExt.send("cmd_brush_changed",data,u);
        }
    }

    public static void snapActor(ATBPExtension parentExt, Room room, String id, Point2D p, Point2D d, boolean orient){
        ISFSObject data = new SFSObject();
        data.putUtfString("i",id);
        data.putFloat("px", (float) p.getX());
        data.putFloat("pz",(float) p.getY());
        data.putFloat("dx", (float)d.getX());
        data.putFloat("dz", (float) d.getY());
        data.putBool("o",orient);
        for(User u : room.getUserList()){
            parentExt.send("cmd_snap_actor",data,u);
        }
    }

    public static void updateActorState(ATBPExtension parentExt, User u, String id, ActorState state, boolean enabled){
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(u.getId()));
        data.putInt("state",state.ordinal());
        data.putBool("enable", enabled);
        parentExt.send("cmd_update_actor_state",data,u);
    }

    public static void updateActorState(ATBPExtension parentExt, Room r, String id, ActorState state, boolean enabled){
        for(User u : r.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id", id);
            data.putInt("state",state.ordinal());
            data.putBool("enable", enabled);
            parentExt.send("cmd_update_actor_state",data,u);
        }
    }

    public static void createProjectile(ATBPExtension parentExt, Room room, UserActor owner, String id, Point2D loc, Point2D dest, float speed){
        for(User u : room.getUserList()){
            ExtensionCommands.createActor(parentExt,u, owner.getId() + id, id,loc,0f,owner.getTeam());
            ExtensionCommands.moveActor(parentExt,u, owner.getId()+id,loc,dest,speed,true);
        }
    }
}

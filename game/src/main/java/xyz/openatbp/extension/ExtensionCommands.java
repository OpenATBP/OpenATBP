package xyz.openatbp.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.actors.Actor;

import java.awt.geom.Point2D;
import java.util.HashMap;

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

    public static void createActorFX(ATBPExtension parentExt, Room room, String id, String bundle, int duration, String fxId, boolean parent, String emit, boolean orient, boolean highlight, int team){
        for(User u : room.getUserList()){
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
    public static void updateActorData(ATBPExtension parentExt, Room room, ISFSObject data){
        for(User u : room.getUserList()){
            parentExt.send("cmd_update_actor_data",data,u);
        }
    }

    public static void updateActorData(ATBPExtension parentExt, Room room, String id, HashMap<String, Double> values){
        String[] intVals = {"kills","assists","deaths","availableSpellPoints","sp_category", "level", "xp","currentHealth","maxHealth"};
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id",id);
        for(String k : values.keySet()){
            boolean dub = true;
            for(String i : intVals){
                if(k.contains(i)){
                    updateData.putInt(k, (int) Math.floor(values.get(k)));
                    dub = false;
                    break;
                }
            }
            if(dub) updateData.putDouble(k,values.get(k));
        }
        for(User u : room.getUserList()){
            parentExt.send("cmd_update_actor_data",updateData,u);
        }
    }

    public static void updateActorData(ATBPExtension parentExt, Room room, String id, String key, double value){
        try{
            String[] intVals = {"kills","assists","deaths","availableSpellPoints","sp_category", "level", "xp","currentHealth","maxHealth"};
            ISFSObject data = new SFSObject();
            data.putUtfString("id",id);
            boolean dub = true;
            for(String k : intVals){
                if(key.contains(k) || key.equalsIgnoreCase(k)){
                    int val = (int) Math.floor(value);
                    data.putInt(key, val);
                    dub = false;
                    break;
                }
            }
            if(dub) data.putDouble(key,value);
            for(User u : room.getUserList()){
                parentExt.send("cmd_update_actor_data",data,u);
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public static void updateActorData(ATBPExtension parentExt, Room room, String id, ISFSObject data){
        data.putUtfString("id",id);
        for(User u : room.getUserList()){
            parentExt.send("cmd_update_actor_data",data,u);
        }
    }

    /**
     *
     * @param id - ID of who is moving
     * @param p - Point2D object of where the actor is
     * @param d - Point2D object of where the actor is going
     * @param speed - Movement speed of actor
     * @param orient - Should the actor face in the direction of their path?
     */
    public static void moveActor(ATBPExtension parentExt, Room room, String id, Point2D p, Point2D d, float speed, boolean orient){
        //Console.debugLog(id + " is moving!");
        //if(id.contains("neptr")) System.out.println(id + ": (" + p.getX() + "," + p.getY() +") to (" + d.getX() + "," + d.getY() + ") at " + speed);
        float px = (float) p.getX();
        float pz = (float) p.getY();
        float dx = (float) d.getX();
        float dz = (float) d.getY();
        if(Float.isNaN(dx)) dx = px;
        if(Float.isNaN(dz)) dz = pz;
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("i",id);
            data.putFloat("px",(float)p.getX());
            data.putFloat("pz",(float)p.getY());
            data.putFloat("dx",dx + 0.1f);
            data.putFloat("dz",dz + 0.1f);
            data.putFloat("s",speed);
            data.putBool("o",orient);
            parentExt.send("cmd_move_actor",data,u);
        }
    }

    /**
     *
     * @param id - String ID of actor being spawned
     * @param actor - Name of actor/avatar asset for the new actor
     * @param spawn - Point2D object of spawn point
     * @param rotation - Rotation float for how it should be facing
     * @param team - Team int value
     */
    public static void createActor(ATBPExtension parentExt, Room room, String id, String actor, Point2D spawn, float rotation, int team){
        RoomHandler handler = parentExt.getRoomHandler(room.getId());
        if(handler != null && handler.hasActorId(id)) return;
        else if(handler != null) handler.addActorId(id);
        for(User u : room.getUserList()){
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
    }

    public static void createActor(ATBPExtension parentExt, Room room, ISFSObject data){
        RoomHandler handler = parentExt.getRoomHandler(room.getId());
        if(handler != null && handler.hasActorId(data.getUtfString("id"))) return;
        else if(handler != null) handler.addActorId(data.getUtfString("id"));
        for(User u : room.getUserList()){
            parentExt.send("cmd_create_actor",data,u);
        }
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
    public static void attackActor(ATBPExtension parentExt, Room room, String id, String target, float x, float z, boolean crit, boolean orient){
        for(User u : room.getUserList()){
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
    }

    /**
     *
     * @param target - ID of the target of the damage
     * @param damage - Damage the target is taking
     */
    public static void damageActor(ATBPExtension parentExt, Room room, String target, int damage){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("target_id",target);
            data.putInt("damage",damage);
            parentExt.send("cmd_damage_actor",data,u);
        }

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
    public static void createProjectileFX(ATBPExtension parentExt, Room room, String fxName, String attackerId, String targetId, String emit, String hit, float time){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("name", fxName);
            data.putUtfString("attacker", attackerId);
            data.putUtfString("target",targetId);
            data.putUtfString("emit",emit);
            data.putUtfString("hit",hit);
            data.putFloat("time",time);
            parentExt.send("cmd_create_projectile_fx",data,u);
        }
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
    public static void towerAttacked(ATBPExtension parentExt, Room room, int tower){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putInt("tower",tower);
            parentExt.send("cmd_tower_under_attack",data,u);
        }
    }

    /**
     *
     * @param tower - Tower identifier to show what tower is destroyed.
     */
    public static void towerDown(ATBPExtension parentExt, Room room, int tower){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putInt("tower",tower);
            parentExt.send("cmd_tower_down",data,u);
        }
    }

    /**
     *
     * @param id - ID of actor who is knocking out another actor
     * @param attackerId - ID of actor being knocked out
     * @param deathTime - Respawn time for the knocked out actor
     */
    public static void knockOutActor(ATBPExtension parentExt, Room room, String id, String attackerId, int deathTime){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id",id);
            data.putUtfString("attackerId",attackerId);
            data.putInt("deathTime",deathTime*1000);
            parentExt.send("cmd_knockout_actor",data,u);
        }
    }

    public static void knockOutActor(ATBPExtension parentExt, User u, String id, String attackerId, int deathTime){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("attackerId",attackerId);
        data.putInt("deathTime",deathTime*1000);
        parentExt.send("cmd_knockout_actor",data,u);
    }

    /**
     *
     * @param id - ID of actor being destroyed
     */
    public static void destroyActor(ATBPExtension parentExt, Room room, String id){
        RoomHandler roomHandler = parentExt.getRoomHandler(room.getId());
        if(roomHandler.hasDestroyedId(id)) return;
        System.out.println("Destroying: " + id);
        roomHandler.addDestroyedId(id);
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id",id);
            parentExt.send("cmd_destroy_actor",data,u);
        }
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
    public static void updateScores(ATBPExtension parentExt, Room room, int teamA, int teamB){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putInt("teamA",teamA);
            data.putInt("teamB",teamB);
            parentExt.send("cmd_update_score",data,u);
        }
    }

    /**
     *
     * @param id - ID of actor being changed
     * @param bundle - File name (minus extension) for asset bundle
     */
    public static void swapActorAsset(ATBPExtension parentExt, Room room, String id, String bundle){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("actor_id",id);
            data.putUtfString("bundle",bundle);
            parentExt.send("cmd_swap_asset",data,u);
        }

    }

    /**
     *
     * @param winningTeam - double value for what the winning team is
     * @throws JsonProcessingException
     */
    public static void gameOver(ATBPExtension parentExt, Room room, double winningTeam) throws JsonProcessingException {
        for(User u : room.getUserList()){
            System.out.println("Calling game over!");
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode node = objectMapper.createObjectNode();
            node.set("teamA", GameManager.getTeamData(parentExt,0,u.getLastJoinedRoom()));

            node.set("teamB", GameManager.getTeamData(parentExt,1,u.getLastJoinedRoom()));
            node.set("gloalTeamData", GameManager.getGlobalTeamData(u.getLastJoinedRoom()));
            node.put("winner",winningTeam);
            ISFSObject data = new SFSObject();
            String objectAsText;
            objectAsText = objectMapper.writeValueAsString(node);
            data.putUtfString("game_results",objectAsText);
            parentExt.send("cmd_game_over", data, u);
        }

    }

    /**
     *
     * @param id - ID of effect being removed
     */
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

    /**
     *
     * @param name - String of sound effect name
     * @param source - Point2D of where the sound is coming from
     * @param id - global; music; null; or player id
     */
    public static void playSound(ATBPExtension parentExt, Room room, String id, String name, Point2D source){
        if(id == null) id = "";
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("name",name);
            data.putUtfString("id", id);
            data.putFloat("x",(float)source.getX());
            data.putFloat("y",0f);
            data.putFloat("z",(float)source.getY());
            parentExt.send("cmd_play_sound",data,u);
        }
    }
    public static void playSound(ATBPExtension parentExt, User u, String id, String name, Point2D source){
        ISFSObject data = new SFSObject();
        data.putUtfString("name",name);
        data.putUtfString("id", id);
        data.putFloat("x",(float)source.getX());
        data.putFloat("y",0f);
        data.putFloat("z",(float)source.getY());
        parentExt.send("cmd_play_sound",data,u);
    }

    public static void playSound(ATBPExtension parentExt, User u, String id, String name){
        ISFSObject data = new SFSObject();
        data.putUtfString("name",name);
        data.putUtfString("id", id);
        data.putFloat("x",0f);
        data.putFloat("y",0f);
        data.putFloat("z",0f);
        parentExt.send("cmd_play_sound",data,u);
    }

    /**
     *
     * @param id - ID of player/actor respawning
     */
    public static void respawnActor(ATBPExtension parentExt, Room room, String id){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id",id);
            parentExt.send("cmd_respawn_actor",data,u);
        }
    }

    /**
     *
     * @param id - ID of actor using ability
     * @param canCast - Boolean if the actor can recast ability
     * @param cooldown - Cooldown of ability
     * @param gCooldown - Cooldown till player can use another ability
     */
    public static void actorAbilityResponse(ATBPExtension parentExt, User u, String id, boolean canCast, int cooldown, int gCooldown){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putBool("canCast",canCast);
        data.putInt("cooldown",cooldown);
        data.putInt("gCooldown",gCooldown);
        parentExt.send("cmd_actor_ability_response",data,u);
    }

    /**
     *
     * @param id - String id of actor being impacted
     * @param state - ActorState of what state is being affected
     * @param enabled - Boolean of whether the state is active or not
     */

    public static void updateActorState(ATBPExtension parentExt, Room r, String id, ActorState state, boolean enabled){
        for(User u : r.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("id", id);
            data.putInt("state",state.ordinal());
            data.putBool("enable", enabled);
            parentExt.send("cmd_update_actor_state",data,u);
        }
    }

    /**
     *
     * @param owner - UserActor of who owns the projectile
     * @param id - Projectile id
     * @param loc - Starting location of projectile
     * @param dest - Ending location of projectile
     * @param speed - Speed of projectile
     */
    public static void createProjectile(ATBPExtension parentExt, Room room, Actor owner, String id, Point2D loc, Point2D dest, float speed){
        ExtensionCommands.createActor(parentExt,room, owner.getId() + id, id,loc,0f,owner.getTeam());
        ExtensionCommands.moveActor(parentExt,room, owner.getId()+id,loc,dest,speed,true);
    }

    public static void createProjectile(ATBPExtension parentExt, Room room, Actor owner, String id, String projectile, Point2D loc, Point2D dest, float speed){
        ExtensionCommands.createActor(parentExt,room, id, projectile,loc,0f,owner.getTeam());
        ExtensionCommands.moveActor(parentExt,room, id,loc,dest,speed,true);
    }

    public static void snapActor(ATBPExtension parentExt, Room room, String id, Point2D location, Point2D dest, boolean orient){
        for(User u : room.getUserList()){
            ISFSObject data = new SFSObject();
            data.putUtfString("i",id);
            data.putFloat("px",(float)location.getX());
            data.putFloat("pz",(float)location.getY());
            data.putFloat("dx",(float)dest.getX());
            data.putFloat("dz",(float)dest.getY());
            data.putBool("o",orient);
            parentExt.send("cmd_snap_actor",data,u);
        }

    }

    public static void handleDeathRecap(ATBPExtension parentExt, User u, String id, String killerId, HashMap<Actor, ISFSObject> aggressors) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode data = mapper.createObjectNode();
        data.put("killerId",killerId);
        ObjectNode playerList = mapper.createObjectNode();
        for(Actor a : aggressors.keySet()){ //Loops through each player in the set
            ObjectNode playerObj = mapper.createObjectNode();
            playerObj.put("name",a.getDisplayName());
            playerObj.put("playerPortrait",a.getFrame());
            for(String k : aggressors.get(a).getKeys()){ //Loops through each different attack
                if(k.contains("attack")){
                    ISFSObject attackData = aggressors.get(a).getSFSObject(k);
                    ObjectNode attackObj = mapper.createObjectNode();
                    for(String key : attackData.getKeys()){ //Loops through the properties of one attack
                        if(key.contains("Damage")) attackObj.put(key,attackData.getInt(key));
                        else attackObj.put(key,attackData.getUtfString(key));
                    }
                    playerObj.set(k,attackObj);
                }
            }
            playerList.set(a.getId(),playerObj);
        }
        data.set("playerList",playerList);
        ObjectNode misc = mapper.createObjectNode();
        double totalDamage = 0;
        double totalPhysical = 0;
        double totalMagic = 0;
        for(Actor a : aggressors.keySet()){
            ISFSObject attackData = aggressors.get(a);
            for(String k : attackData.getKeys()){
                if(k.contains("attack")){
                    ISFSObject attack = attackData.getSFSObject(k);
                    totalDamage+= attack.getInt("atkDamage");
                    if(attack.getUtfString("atkType").equalsIgnoreCase("physical")) totalPhysical+=attack.getInt("atkDamage");
                    else totalMagic+=attack.getInt("atkDamage");
                }
            }
        }
        misc.put("totalDamage",totalDamage);
        misc.put("percentPhysical",(totalPhysical/totalDamage)*100);
        misc.put("percentSpell",(totalMagic/totalDamage)*100);
        misc.put("totalTime",10000);
        String tipType = "general";
        if(killerId.contains("tower")) tipType = "tower";
        else if(totalPhysical == 0 && Math.random() > 0.6) tipType = "spell";
        else if(totalMagic == 0 && Math.random() > 0.6) tipType = "physical_damage";
        misc.put("tip",parentExt.getRandomTip(tipType));
        data.set("misc",misc);
        ISFSObject sendData = new SFSObject();
        sendData.putUtfString("id",id);
        sendData.putUtfString("deathRecap",mapper.writeValueAsString(data));
        parentExt.send("cmd_death_recap",sendData,u);
    }

    public static void updateTime(ATBPExtension parentExt, Room room, int msRan){
        ISFSObject data = new SFSObject();
        data.putLong("time",msRan);
        for(User u : room.getUserList()){
            parentExt.send("cmd_update_time",data,u);
        }
    }

    public static void updateAltar(ATBPExtension parentExt, Room room, int altarNum, int team, boolean locked){
        ISFSObject data = new SFSObject();
        data.putInt("altar",altarNum);
        data.putInt("team",team);
        data.putBool("locked",locked);
        for(User u : room.getUserList()){
            parentExt.send("cmd_altar_update",data,u);
        }
    }

    public static void addStatusIcon(ATBPExtension parentExt, User u, String name, String desc, String icon, float duration){
        ISFSObject data = new SFSObject();
        data.putUtfString("name",name);
        data.putUtfString("desc",desc);
        data.putUtfString("icon",icon);
        data.putFloat("duration",duration);
        parentExt.send("cmd_add_status_icon",data,u);
    }

    public static void removeStatusIcon(ATBPExtension parentExt, User u, String name){
        ISFSObject data = new SFSObject();
        data.putUtfString("name",name);
        parentExt.send("cmd_remove_status_icon",data,u);
    }

    public static void scaleActor(ATBPExtension parentExt, Room room, String id, float scale){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putFloat("scale",scale);
        parentExt.send("cmd_scale_actor",data,room.getUserList());
    }

    public static void actorAnimate(ATBPExtension parentExt, Room room, String id, String animation, int duration, boolean loop){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("animation",animation);
        data.putInt("duration",duration);
        data.putBool("loop",loop);
        parentExt.send("cmd_actor_animate",data,room.getUserList());
    }

    public static void addUser(ATBPExtension parentExt, Room room, int id, String name, String champion, int team, String tid, String backpack, int elo){
        ISFSObject data = new SFSObject();
        data.putInt("id",id);
        data.putUtfString("name",name);
        data.putUtfString("champion",champion);
        data.putInt("team",team);
        data.putUtfString("tid",tid);
        data.putUtfString("backpack",backpack);
        data.putInt("elo",elo);
        parentExt.send("cmd_add_user",data,room.getUserList());
    }

    public static void abortGame(ATBPExtension parentExt, Room room){
        ISFSObject data = new SFSObject();
        parentExt.send("cmd_game_aborted",data,room.getUserList());
    }

    public static void changeBrush(ATBPExtension parentExt, Room room, String id, int brushId){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putInt("brushId",brushId);
        parentExt.send("cmd_brush_changed",data,room.getUserList());
    }

    public static void knockBackActor(ATBPExtension parentExt, Room room, String id, Point2D loc, Point2D dest, float speed, boolean orient){
        ISFSObject data = new SFSObject();
        data.putUtfString("i",id);
        data.putFloat("px",(float)loc.getX());
        data.putFloat("pz",(float)loc.getY());
        data.putFloat("dx",(float)dest.getX());
        data.putFloat("dz",(float)dest.getY());
        data.putFloat("s",speed);
        data.putBool("o",orient);
        parentExt.send("cmd_knockback_actor",data,room.getUserList());
    }

    public static void audioSting(ATBPExtension parentExt, Room room, String name){
        ISFSObject data = new SFSObject();
        data.putUtfString("name", name);
        parentExt.send("cmd_audio_sting",data,room.getUserList());
    }
}

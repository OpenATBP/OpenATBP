package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Tower extends Actor {
    private final int[] PURPLE_TOWER_NUM = {2,1,0};
    private final int[] BLUE_TOWER_NUM = {5,4,3};
    private long lastHit;
    private boolean destroyed = false;
    private Actor target;

    public Tower(ATBPExtension parentExt, Room room, String id, int team, Point2D location){
        this.currentHealth = 800;
        this.maxHealth = 800;
        this.location = location;
        this.id = id;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lastHit = 0;
        this.actorType = ActorType.TOWER;
        this.attackCooldown = 500;
        this.avatar = "tower1";
        if(team == 1) this.avatar = "tower2";
        this.displayName = parentExt.getDisplayName(this.avatar);
        this.stats = this.initializeStats();
        ExtensionCommands.createWorldFX(parentExt,room,this.id,"fx_target_ring_6",this.id+"_ring",15*60*1000,(float)this.location.getX(),(float)this.location.getY(),true,this.team,0f);
    }

    public Tower(ATBPExtension parentExt, Room room, int team){
        this.parentExt = parentExt;
        this.room = room;
        this.team = team;
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(this.destroyed) return true;
        if(this.target == null) damage*=0.25;
        else if(a.getActorType() == ActorType.MINION) damage*=0.5;
        this.changeHealth(this.getMitigatedDamage(damage,this.getAttackType(attackData),a)*-1);
        boolean notify = System.currentTimeMillis()-this.lastHit >= 1000*5;
        if(notify) ExtensionCommands.towerAttacked(parentExt,this.room,this.getTowerNum());
        if(this.currentHealth <= 0) this.die(a);
        if(notify) this.triggerNotification();
        return false;
    }

    @Override
    public void attack(Actor a) {
        this.attackCooldown = this.getPlayerStat("attackSpeed");
        String projectileName = "tower_projectile_blue";
        String effectName = "tower_shoot_blue";
        if(this.team == 0){
            projectileName = "tower_projectile_purple";
            effectName = "tower_shoot_purple";
        }
        ExtensionCommands.createProjectileFX(this.parentExt,this.room,projectileName,this.id,a.getId(),"emitNode","Bip01",0.6f);
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,effectName,600,this.id+"_attackFx",false,"emitNode",false,false,this.team);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,(int)this.getPlayerStat("attackDamage"),"basicAttack"),600, TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) {
        if(!this.destroyed){
            if(a.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) a;
                ua.addGameStat("towers",1);
            }
            this.destroyed = true;
            ExtensionCommands.towerDown(parentExt,this.room, this.getTowerNum());
            ExtensionCommands.knockOutActor(parentExt,this.room,this.id,a.getId(),100);
            ExtensionCommands.destroyActor(parentExt,this.room,this.id);
            for(User u : room.getUserList()){
                String actorId = "tower2a";
                if(this.getTowerNum() == 0 || this.getTowerNum() == 3 ){
                    actorId = "tower1a";
                }
                ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),actorId,this.id+"_destroyed",1000*60*15,(float)this.location.getX(),(float)this.location.getY(),false,this.team,0f);
                ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),"tower_destroyed_explosion",this.id+"_destroyed_explosion",1000,(float)this.location.getX(),(float)this.location.getY(),false,this.team,0f);
                ExtensionCommands.removeFx(parentExt,u,this.id+"_ring");
                ExtensionCommands.removeFx(parentExt,u,this.id+"_target");
                if(this.target != null && this.target.getActorType() == ActorType.PLAYER) ExtensionCommands.removeFx(parentExt,u,this.id+"_aggro");
                this.parentExt.getRoomHandler(this.room.getId()).addScore(null,a.getTeam(),50);
            }
            if(this.getTowerNum() == 0 || this.getTowerNum() == 3) parentExt.getRoomHandler(room.getId()).getOpposingTeamBase(this.team).unlock();
        }
    }

    @Override
    public void update(int msRan) {
        if(!this.destroyed){
            List<Actor> nearbyActors = Champion.getEnemyActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.team,this.location, (float) this.getPlayerStat("attackRange"));
            if(this.target == null){
                if(this.attackCooldown > this.getPlayerStat("attackSpeed")) this.reduceAttackCooldown();
                else this.attackCooldown = this.getPlayerStat("attackSpeed");
                boolean hasMinion = false;
                double distance = 1000;
                Actor potentialTarget = null;
                for(Actor a : nearbyActors){
                    if(hasMinion && a.getActorType() == ActorType.MINION){
                        if(a.getLocation().distance(this.location)<distance){//If minions exist in range, it only focuses on finding the closest minion
                            potentialTarget = a;
                            distance = a.getLocation().distance(this.location);
                        }
                    }else if(!hasMinion && a.getActorType() == ActorType.MINION){ //If minions have not been found yet but it just found one, sets the first target to be searched
                        hasMinion = true;
                        potentialTarget = a;
                        distance = a.getLocation().distance(this.location);
                    }else if(!hasMinion && a.getActorType() == ActorType.PLAYER){ //If potential target is a player and no minion has been found, starts processing closest player
                        if(a.getLocation().distance(this.location) < distance){
                            potentialTarget = a;
                            distance = a.getLocation().distance(this.location);
                        }
                    }
                }
                if(potentialTarget != null){
                    this.target = potentialTarget;
                    if(this.target.getActorType() == ActorType.PLAYER){
                        UserActor user = (UserActor) this.target;
                        this.targetPlayer(user);
                    }
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.target.getId(),"tower_current_target_indicator",10*60*1000,this.id+"_target",true,"displayBar",false,true,this.team);
                }
            }else{
                if(this.target.getHealth() <= 0){
                    this.resetTarget(this.target);
                    return;
                }
                if(this.attackCooldown > 0) this.reduceAttackCooldown();
                System.out.println("Targeting " + target.getId());
                if(nearbyActors.size() == 0){
                    if(this.target.getActorType() == ActorType.PLAYER){
                        UserActor ua = (UserActor) this.target;
                        ExtensionCommands.removeFx(this.parentExt,ua.getUser(),this.id+"_aggro");
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_target");
                    }
                    this.target = null;
                }
                else{
                    if(this.target.getLocation().distance(this.location) <= this.getPlayerStat("attackRange")){
                        if(this.canAttack()) this.attack(this.target);
                    }else this.resetTarget(this.target);
                }
            }
        }
    }

    public String getId(){
        return this.id;
    }
    public int getTowerNum(){ //Gets tower number for the client to process correctly
        /*
        0 - Purple Base Tower
        1 - Purple Bot Tower
        2 - Purple Top Tower
        3 - Blue Base Tower
        4 - Blue Bot Tower
        5 - Blue Top Tower
         */
        String[] towerIdComponents = this.id.split("_");
        if(towerIdComponents[0].equalsIgnoreCase("blue")){
            return BLUE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
        }else{
            return PURPLE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
        }
    }

    public void triggerNotification(){ //Resets the hit timer so players aren't spammed by the tower being attacked
        this.lastHit = System.currentTimeMillis();
    }

    private boolean canAttack(){
        return this.attackCooldown == 0;
    }

    public void resetTarget(Actor a){ //TODO: Does not always work
        if(a.getActorType() == ActorType.PLAYER){
            UserActor ua = (UserActor) a;
            ExtensionCommands.removeFx(this.parentExt,ua.getUser(),this.id+"_aggro");
        }
        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_target");
        this.target = null;
    }

    public void targetPlayer(UserActor user){
        ExtensionCommands.setTarget(this.parentExt,user.getUser(),this.id,user.getId());
        ExtensionCommands.createWorldFX(this.parentExt, user.getUser(),user.getId(),"tower_danger_alert",this.id+"_aggro",10*60*1000,(float) this.location.getX(),(float)this.location.getY(),true,this.team,0f);
        ExtensionCommands.playSound(this.parentExt,user.getUser(),"sfx_turret_has_you_targeted",this.location);
    }

}

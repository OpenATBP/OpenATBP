package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Neptr extends UserActor {
    private boolean passiveActive = false;
    private long passiveStart = 0;
    private Map<String, Point2D> mineLocations;
    private int mineNum = 0;
    private List<Actor> ultImpactedActors;
    private long ultDamageStartTime = 0;
    private boolean soundPlayed = false;
    private long lastMoveSoundPlayed = 0;
    public Neptr(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.mineLocations = new HashMap<>(3);
        this.ultImpactedActors = new ArrayList<>();
    }

    @Override
    public void setState(ActorState state, boolean enabled) {
        super.setState(state, enabled);
        if(state == ActorState.BRUSH && enabled){
            this.passiveStart = System.currentTimeMillis();
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"neptr_passive",500,this.id+"_passive"+Math.random(),true,"targetNode",true,false,this.team);
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_neptr_passive",this.location);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_neptr_passive",this.location);
            this.addEffect("speed",this.getStat("speed")*0.35d,3500,null,false);
            this.addEffect("attackSpeed",this.getStat("attackSpeed")*-0.25d,3500,null,false);
            if(this.passiveActive){
                ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
            }
            ExtensionCommands.addStatusIcon(this.parentExt,this.player,"passive","neptr_spell_4_short_description","icon_neptr_passive",3500f);
            this.passiveActive = true;
        }
    }

    @Override
    public void move(Point2D destination) {
        System.out.println("Moved!");
        if(this.isStopped()) ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_neptr_move_start",this.location);
        this.soundPlayed = false;
        super.move(destination);
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.passiveActive && System.currentTimeMillis() - this.passiveStart >= 3500){
            this.passiveActive = false;
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
        }
        if(this.isStopped() && !this.soundPlayed){
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_neptr_move_end",this.location);
            this.soundPlayed = true;
        }else if (!this.isStopped() && System.currentTimeMillis() - this.lastMoveSoundPlayed > 500){
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_neptr_move",this.location);
            this.lastMoveSoundPlayed = System.currentTimeMillis();
        }
        Set<String> keys = new HashSet<>(this.mineLocations.keySet());
        for(String k : keys){
            Point2D mine = this.mineLocations.get(k);
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),mine,2f)){
                if(this.isNonStructure(a)){
                    this.mineLocations.remove(k);
                    ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_neptr_mine_activate",mine);
                    Runnable mineExplosion = () -> {
                        ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_neptr_mine_explode",mine);
                        ExtensionCommands.removeFx(this.parentExt,this.room,k);
                        ExtensionCommands.removeFx(this.parentExt,this.room,k+"_ring");
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"neptr_mine_explode",k+"_explosion",500,(float)mine.getX(),(float)mine.getY(),false,this.team,0f);
                        for(Actor actor : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),mine,2f)){
                            if(this.isNonStructure(actor)){
                                JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell2");
                                actor.addToDamageQueue(this,this.getSpellDamage(spellData),spellData);
                                actor.addState(ActorState.SLOWED,0.4d,3000,null,false);
                            }
                        }
                    };
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(mineExplosion,500,TimeUnit.MILLISECONDS);
                    break;
                }
            }
        }

        List<Actor> impactedActors = new ArrayList<>(this.ultImpactedActors);
        if(impactedActors.size() > 1 && System.currentTimeMillis() - this.ultDamageStartTime < 3000){
            JsonNode attackData = this.parentExt.getAttackData(this.avatar,"spell3");
            for(Actor a : impactedActors){
                a.addToDamageQueue(this,this.getSpellDamage(attackData)/10d,attackData);
            }
        }
    }

    @Override
    public void fireProjectile(Projectile projectile, String id, Point2D dest, float range) {
        super.fireProjectile(projectile, id, dest, range);
        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_neptr_boommeringue",this.location);
        ExtensionCommands.createActorFX(this.parentExt,this.room, this.id+id, "neptr_pie_trail", (int) projectile.getEstimatedDuration()+1000,this.id+id+"_fx",true,"Bip001",true,true,this.team);

    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        this.stopMoving();
        switch(ability){
            case 1:
                this.canCast[0] = false;
                //ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_neptr_boommeringue",this.location);
                Line2D maxRangeLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest),8f);
                this.fireProjectile(new NeptrProjectile(this.parentExt,this,maxRangeLine,8f,0.5f,this.id+"projectile_neptr_boom_meringue"),"projectile_neptr_boom_meringue",maxRangeLine.getP2(),8f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new NeptrAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown, TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                if(this.mineLocations.size() == 3){
                    Set<String> keys = this.mineLocations.keySet();
                    int lowestNum = 10000;
                    for(String k : keys){
                        String numString = k.split("_")[1].replace("mine","");
                        System.out.println("String: " + numString);
                        int num = Integer.parseInt(numString);
                        if(num < lowestNum) lowestNum = num;
                    }
                    String id = this.id+"_mine"+lowestNum;
                    ExtensionCommands.removeFx(this.parentExt,this.room,id);
                    ExtensionCommands.removeFx(this.parentExt,this.room,id+"_ring");
                    this.mineLocations.remove(id);
                }
                String mineId = this.id+"_mine"+this.mineNum;
                this.mineNum++;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_neptr_mine",this.location);
                ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_neptr_mine_spawn",dest);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"neptr_pie_mine",mineId,1000*60*15,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_2",mineId+"_ring",1000*60*15,(float)dest.getX(),(float)dest.getY(),true,this.team,0f);
                this.mineLocations.put(mineId,dest);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new NeptrAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_neptr_ultimate",dest);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_neptr_locked_on",this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"neptr_ultimate",this.id+"_ult",500,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_3",this.id+"_ultRing",500,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new NeptrAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class NeptrAbilityHandler extends AbilityRunnable{

        public NeptrAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            ultDamageStartTime = System.currentTimeMillis();
            ultImpactedActors = new ArrayList<>();
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,3f)){
                if(isNonStructure(a)){
                    a.knockback(Neptr.this.location);
                    a.addState(ActorState.SILENCED,0d,1000,null,false);
                    ExtensionCommands.createActorFX(parentExt,room,a.getId(),"neptr_dot_poison",3000,a.getId()+"_neptrPoison",true,"Bip001",true,false,team);
                    ultImpactedActors.add(a);
                }
            }
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class NeptrProjectile extends Projectile {

        private boolean reversed = false;
        private boolean intermission = false;
        private Point2D lastNeptrLocation = null;
        private long lastMoved = 0;
        private Map<Actor, Long> hitBuffer;
        private double damageReduction = 0d;

        public NeptrProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.hitBuffer = new HashMap<>(3);
        }

        @Override
        public void update(RoomHandler roomHandler) {
            if(destroyed) return;
            Actor hitActor = this.checkPlayerCollision(roomHandler);
            if(hitActor != null){
                this.hit(hitActor);
            }
            if(this.intermission) return;
            if(this.lastNeptrLocation != null && Neptr.this.getLocation().distance(this.lastNeptrLocation) > 0.01){
                if(System.currentTimeMillis() - this.lastMoved >= 300) this.moveTowardsNeptr();
            }
            this.updateTimeTraveled();
            if(this.destination.distance(this.getLocation()) <= 0.01 || System.currentTimeMillis() - this.startTime > this.estimatedDuration){
                if(!this.reversed){
                    this.intermission = true;
                    Runnable handleIntermission = () -> {
                        //ExtensionCommands.playSound(this.parentExt,this.owner.getRoom(),this.id,"sfx_neptr_boommeringue",this.location);
                        this.intermission = false;
                        this.damageReduction = 0d;
                        this.moveTowardsNeptr();
                    };
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(handleIntermission,1,TimeUnit.SECONDS);
                }
                else this.destroy();
            }
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler){
            List<Actor> teammates = this.getTeammates(roomHandler);
            for(Actor a : roomHandler.getActors()){
                if(!this.hitBuffer.containsKey(a) || (this.hitBuffer.containsKey(a) && System.currentTimeMillis() - this.hitBuffer.get(a) >= 500)){
                    if((a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) && !teammates.contains(a)){ //TODO: Change to not hit teammates
                        double collisionRadius = parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                        if(a.getLocation().distance(location) <= hitbox + collisionRadius){
                            return a;
                        }
                    }
                }
            }
            return null;
        }

        private void moveTowardsNeptr(){
            this.lastMoved = System.currentTimeMillis();
            this.startingLocation = this.location;
            this.destination = Neptr.this.getLocation();
            this.lastNeptrLocation = this.destination;
            this.path = new Line2D.Float(this.startingLocation,this.destination);
            this.startTime = System.currentTimeMillis();
            this.estimatedDuration = (path.getP1().distance(path.getP2()) / speed)*1000f;
            if(!this.reversed){
                ExtensionCommands.removeFx(this.parentExt,this.owner.getRoom(), Neptr.this.id+this.id+"_fx");
                ExtensionCommands.createActorFX(this.parentExt,this.owner.getRoom(), this.id,"neptr_pie_trail",(int)this.estimatedDuration+1000,Neptr.this.id+this.id+"_fx",true,"",true,true,this.owner.getTeam());
            }
            System.out.println("Estimated Duration: " + this.estimatedDuration);
            System.out.println("Case 2 inside: " + (System.currentTimeMillis() - this.startTime > this.estimatedDuration));
            this.timeTraveled = 0f;
            ExtensionCommands.moveActor(this.parentExt,this.owner.getRoom(), this.id,this.location,this.destination,this.speed,true);
            this.reversed = true;
        }

        @Override
        protected void hit(Actor victim) {
            this.hitBuffer.put(victim,System.currentTimeMillis());
            JsonNode attackData = this.parentExt.getAttackData(Neptr.this.getAvatar(),"spell1");
            double damage = Neptr.this.getSpellDamage(attackData)*(1d-this.damageReduction);
            victim.addToDamageQueue(this.owner,damage,attackData);
            ExtensionCommands.playSound(this.parentExt,this.owner.getRoom(), victim.getId(), "akubat_projectileHit1", victim.getLocation());
            this.damageReduction+=0.15d;
            if(this.damageReduction > 1d) this.damageReduction = 1d;
        }
    }
}

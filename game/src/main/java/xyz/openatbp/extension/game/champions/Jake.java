package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import jdk.jshell.Snippet;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Jake extends UserActor {
    private long lastPassive = 0;
    private boolean ultActivated = false;
    private long lastStomped = 0;
    private boolean stompSoundChange = false;
    public Jake(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if(System.currentTimeMillis() - this.lastPassive >= 8000){
            JsonNode attackData = this.parentExt.getAttackData("jake","spell4");
            a.addToDamageQueue(this,getPlayerStat("attackDamage")*0.4d,attackData);
            a.addState(ActorState.SLOWED,0.5d,1500,null,false);
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"passive",true,8000,0);
            this.lastPassive = System.currentTimeMillis();
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if(this.ultActivated) ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_grow_fart",this.location);
    }

    @Override
    public boolean canAttack() {
        if(this.ultActivated) return false;
        return super.canAttack();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if(this.ultActivated){
            this.ultActivated = false;
            ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,this.getSkinAssetBundle());
        }
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.ultActivated && !this.isStopped()){
            if(System.currentTimeMillis() - this.lastStomped >= 500){
                this.lastStomped = System.currentTimeMillis();
                for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,2f)){
                    if(this.isNonStructure(a)){
                        JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell3");
                        double damage = (double)(getSpellDamage(spellData))/2d;
                        a.addToDamageQueue(this,(int)damage,spellData);
                    }
                }
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"jake_stomp_fx",250,this.id+"_stomp",true,"Bip001 Footsteps",false,false,this.team);
                this.stompSoundChange = !this.stompSoundChange;
                if(this.stompSoundChange) ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_grow_stomp1",this.location);
                else ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_grow_stomp",this.location);
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch(ability){
            case 1:
                this.stopMoving();
                this.canCast[0] = false;
                canMove = false;
                Runnable qDelay = () -> {
                    Actor closestTarget = null;
                    double closestDistance = 1000;
                    for(Actor a : Champion.getActorsAlongLine(this.parentExt.getRoomHandler(this.room.getId()),new Line2D.Float(this.location,dest),1.75d)){
                        if(a.getTeam() != this.team){
                            if(a.getLocation().distance(this.location) < closestDistance){
                                closestDistance = a.getLocation().distance(this.location);
                                closestTarget = a;
                            }
                        }
                    }
                    if(closestTarget != null){
                        double speed = 7d;
                        double time = (closestDistance / speed)*1000d;
                        this.stopMoving((int) (time));
                        Actor finalClosestTarget = closestTarget;
                        double finalClosestDistance = closestDistance;
                        Runnable delayedDamage = () -> {
                            double newTime = (finalClosestDistance / 15d)*1000d;
                            Runnable animationDelay = () -> {
                                ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell1c",250,false);
                            };
                            SmartFoxServer.getInstance().getTaskScheduler().schedule(animationDelay,(int)newTime,TimeUnit.MILLISECONDS);
                            ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell1b",500,false);
                            ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location, finalClosestTarget.getLocation(), 15f,true);
                            this.setLocation(finalClosestTarget.getLocation());
                            double percentage = finalClosestDistance/7d;
                            if(percentage < 0.5d) percentage = 0.5d;
                            double spellModifer = this.getPlayerStat("spellDamage")*percentage;
                            finalClosestTarget.addState(ActorState.STUNNED,0d,2000,null,false);
                            finalClosestTarget.addToDamageQueue(this,35+spellModifer,spellData);
                        };
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(delayedDamage,(int)time, TimeUnit.MILLISECONDS);
                    }
                };
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_stretch",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_jake_stretch",this.location);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(qDelay,300,TimeUnit.MILLISECONDS);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new JakeAbilityHandler(ability,spellData,cooldown,gCooldown,dest),1300,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.stopMoving(gCooldown);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_jake_ball",1000,this.id+"_ball",true,"displayBar",true,false,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"jake_dust_up",500,this.id+"_dust",false,"Bip001 Footsteps",false,false,this.team);
                for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,3f)){
                    if(this.isNonStructure(a)){
                        a.knockback(this.location);
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                    }
                }
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_ball",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_jake_ball",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new JakeAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                String skin = "";
                if(this.avatar.split("_").length > 2) skin = "_" + this.avatar.split("_")[2];
                ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,"jake"+skin+"_big");
                this.cleanseEffects();
                this.ultActivated = true;
                ExtensionCommands.createActorFX(parentExt,room,id,"statusEffect_immunity",5000,id+"_ultImmunity",true,"displayBar",false,false,team);
                this.addState(ActorState.CLEANSED,0d,5000,null,false);
                this.addState(ActorState.IMMUNITY,0d,5000,null,false);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_jake_grow",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_jake_grow",this.location);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new JakeAbilityHandler(ability,spellData,cooldown,gCooldown,dest),5000,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class JakeAbilityHandler extends AbilityRunnable{

        public JakeAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            canMove = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            ultActivated = false;
            ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
        }

        @Override
        protected void spellPassive() {

        }
    }
}

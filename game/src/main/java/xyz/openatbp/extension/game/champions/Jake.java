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
    private Map<Actor, Long> passiveVictims = new HashMap<>();
    public Jake(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if(this.passiveVictims.containsKey(a)){
            if(System.currentTimeMillis() - this.passiveVictims.get(a) >= 10000){
                JsonNode attackData = this.parentExt.getAttackData("jake","spell4");
                a.addToDamageQueue(this,getPlayerStat("attackDamage")*0.4d,attackData);
                a.addState(ActorState.SLOWED,0.5d,1500,null,false);
                this.passiveVictims.put(a,System.currentTimeMillis());
            }
        }else{
            JsonNode attackData = this.parentExt.getAttackData("jake","spell4");
            a.addToDamageQueue(this,getPlayerStat("attackDamage")*0.4d,attackData);
            a.addState(ActorState.SLOWED,0.5d,1500,null,false);
            this.passiveVictims.put(a,System.currentTimeMillis());
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
                            finalClosestTarget.addState(ActorState.STUNNED,0d,2000,null,false);
                            finalClosestTarget.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                        };
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(delayedDamage,(int)time, TimeUnit.MILLISECONDS);
                    }
                };
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(qDelay,300,TimeUnit.MILLISECONDS);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new JakeAbilityHandler(ability,spellData,cooldown,gCooldown,dest),1300,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.stopMoving(gCooldown);
                String skin = "";
                if(this.avatar.split("_").length > 2) skin = "_" + this.avatar.split("_")[2];
                //ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,"jake"+skin+"_dust_up");
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_jake_ball",gCooldown,this.id+"_ball",true,"Bip001 HeadNub",true,false,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new JakeAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 3:
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
            //ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
        }

        @Override
        protected void spellE() {

        }

        @Override
        protected void spellPassive() {

        }
    }
}

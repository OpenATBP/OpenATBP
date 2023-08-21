package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class PeppermintButler extends UserActor {
    private int timeStopped = 0;
    private boolean qActive = false;
    private boolean stopPassive = false;
    public PeppermintButler(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public double getPlayerStat(String stat) {
        if(stat.equalsIgnoreCase("healthRegen") && getState(ActorState.STEALTH)) return super.getPlayerStat("healthRegen") + (this.maxHealth*0.02d);
        else if(stat.equalsIgnoreCase("speed") && getState(ActorState.TRANSFORMED)) return super.getPlayerStat("speed") + (this.getStat("speed")*0.4d);
        else if(stat.equalsIgnoreCase("attackSpeed") && getState(ActorState.TRANSFORMED)) return super.getPlayerStat("attackSpeed") - (this.getStat("attackSpeed")*0.3d);
        else if(stat.equalsIgnoreCase("attackDamage") && getState(ActorState.TRANSFORMED)) return super.getPlayerStat("attackDamage") + (this.getStat("attackDamage")*0.3d);
        return super.getPlayerStat(stat);
    }

    @Override
    public void attack(Actor a){
        super.attack(a);
        this.stopPassive = true;
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"pepbut_punch_sparks",1000,this.id,true,"",true,false,this.team);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData){
        this.stopPassive = true;
        return super.damaged(a,damage,attackData);
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.isStopped() && !qActive && !stopPassive && !this.getState(ActorState.TRANSFORMED)){
            timeStopped+=100;
            if(this.timeStopped >= 1750 && !this.getState(ActorState.STEALTH)){
                this.setState(ActorState.STEALTH, true);
                ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"passive",500,false);
                Runnable delayAnimation = () -> {
                    if(this.timeStopped >= 1750){
                        ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"passive_idle",1000*60*15,true);
                        ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_pepbut_invis_hide",this.location);
                        this.setState(ActorState.REVEALED, false);
                        this.setState(ActorState.INVISIBLE, true);
                    }
                };
                SmartFoxServer.getInstance().getTaskScheduler().schedule(delayAnimation,500, TimeUnit.MILLISECONDS);
                this.updateStatMenu("healthRegen");
            }
        }else{
            this.timeStopped = 0;
            if(this.stopPassive) this.stopPassive = false;
            if(this.getState(ActorState.STEALTH)){
                ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"run",1,false);
                this.setState(ActorState.STEALTH, false);
                this.setState(ActorState.INVISIBLE, false);
                if(!this.getState(ActorState.BRUSH)) this.setState(ActorState.REVEALED, true);
                this.updateStatMenu("healthRegen");
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_pepbut_invis_reveal",this.location);
                ExtensionCommands.createActorFX(parentExt,room,id,"statusEffect_immunity",2000,id+"_Immunity",true,"displayBar",false,false,team);
            }
        }

        if(this.qActive){
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,3f)){
                if(this.isNonStructure(a)){
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell1");
                    double damage = this.getSpellDamage(spellData)/10d;
                    a.addToDamageQueue(this,damage,spellData);
                    a.addState(ActorState.BLINDED,0d,500,null,true);
                }
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        this.stopPassive = true;
        switch (ability){
            case 1:
                canCast[0] = false;
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_3",5000,this.id+"_qRing",true,"",true,true,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"pepbut_aoe",5000,this.id+"_aoe",true,"",true,false,this.team);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_pepbut_aoe",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                qActive = true;
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new PeppermintAbilityHandler(ability,spellData,cooldown,gCooldown,dest),5000,TimeUnit.MILLISECONDS);
                break;
            case 2:
                canCast[1] = false;
                this.stopMoving();
                this.setCanMove(false);
                Point2D dashLocation = Champion.getTeleportPoint(parentExt,player,this.location,dest);
                double time = dashLocation.distance(this.location)/DASH_SPEED;
                int runtime = (int)Math.floor(time*1000);
                String hohoVo = "vo/vo_pepbut_hoho";
                if(this.avatar.contains("zombie")) hohoVo = "vo/vo_pepbut_zombie_hoho";
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,hohoVo,this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_2.5",this.id+"_wRing",runtime,(float)dashLocation.getX(),(float)dashLocation.getY(),true,this.team,0f);
                Runnable animationDelay = () -> {
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_pepbut_dig",this.location);
                    ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell2b",runtime,true);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"pepbut_dig_rocks",runtime,this.id+"_digRocks",true,"",true,false,this.team);
                    ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,dashLocation, (float) DASH_SPEED,true);
                    this.setLocation(dashLocation);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new PeppermintAbilityHandler(ability,spellData,cooldown,gCooldown,dashLocation),runtime,TimeUnit.MILLISECONDS);
                };
                SmartFoxServer.getInstance().getTaskScheduler().schedule(animationDelay,500,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 3:
                canCast[2] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_pepbut_feral",this.location);
                Runnable delay = () -> {
                    this.attackCooldown = 0;
                    this.setState(ActorState.TRANSFORMED, true);
                    String[] statsToUpdate = {"speed","attackSpeed","attackDamage"};
                    this.updateStatMenu(statsToUpdate);
                    String hissVo = "vo/vo_pepbut_feral_hiss";
                    if(this.avatar.contains("zombie")) hissVo = "vo/vo_pepbut_zombie_feral_hiss";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,hissVo,this.location);
                    ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,"pepbut_feral");
                    //ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"pepbut_feral_eyes",7000,this.id+"_ultEyes",true,"cryAnimationExportNode",false,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_beast_crit_hand",7000,this.id+"ultHandL",true,"Bip001 L Hand",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_beast_crit_hand",7000,this.id+"ultHandR",true,"Bip001 R Hand",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"pepbut_feral_explosion",500,this.id+"_ultExplosion",false,"",false,false,this.team);
                    this.addState(ActorState.SILENCED,0d,7000,null,true);
                    if(this.qActive){
                        this.qActive = false;
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_qRing");
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_aoe");
                    }
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new PeppermintAbilityHandler(ability,spellData,cooldown,gCooldown,dest),7000,TimeUnit.MILLISECONDS);
                };
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(delay,castDelay,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class PeppermintAbilityHandler extends AbilityRunnable{

        public PeppermintAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            qActive = false;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            canMove = true;
            String beholdVo = "vo/vo_pepbut_behold";
            if(avatar.contains("zombie")) beholdVo = "vo/vo_pepbut_zombie_behold";
            ExtensionCommands.playSound(parentExt,room,id,"sfx_pepbut_dig_emerge",this.dest);
            ExtensionCommands.playSound(parentExt,room,id,beholdVo,location);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell2c",500,false);
            ExtensionCommands.createWorldFX(parentExt,room,id,"pepbut_dig_explode",id+"_wExplode",500,(float)this.dest.getX(),(float)this.dest.getY(),false,team,0f);
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),this.dest,2.5f)){
                if(isNonStructure(a)){
                    a.addToDamageQueue(PeppermintButler.this,getSpellDamage(spellData),spellData);
                    a.addState(ActorState.STUNNED,0d,1500,null,false);
                }
            }
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            setState(ActorState.TRANSFORMED, false);
            String[] statsToUpdate = {"speed","attackSpeed","attackDamage"};
            updateStatMenu(statsToUpdate);
            ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
        }

        @Override
        protected void spellPassive() {

        }
    }
}

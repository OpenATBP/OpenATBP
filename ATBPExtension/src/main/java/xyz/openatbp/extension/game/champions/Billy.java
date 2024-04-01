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

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Billy extends UserActor {
    private int passiveUses = 0;
    private Point2D ultLocation = null;
    private long ultStartTime = 0;
    private long lastUltTick = 0;
    private long finalPassiveStart = 0;
    private long lastSoundPlayed = 0;
    private Point2D ultLoc = null;
    private static final float Q_OFFSET_DISTANCE = 1.5f;
    private static final float Q_SPELL_RANGE = 4.5f;

    public Billy(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(this.ultLocation != null && System.currentTimeMillis() - this.ultStartTime < 4500 && System.currentTimeMillis() - this.lastUltTick >= 200){
            this.lastUltTick = System.currentTimeMillis();
            List<Actor> impactedActors = Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultLocation,2.25f).stream().filter(this::isNonStructure).collect(Collectors.toList());
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell3");
            for(Actor a : impactedActors){
                double damageReduction = 1-(0.15*impactedActors.size());
                if(damageReduction >= 0.7d) damageReduction = 0.7d;
                a.addToDamageQueue(this,(this.getSpellDamage(spellData)/5d)*damageReduction,spellData);
            }
        }else if(this.ultLocation != null && System.currentTimeMillis() - this.ultStartTime >= 4500){
            this.ultLocation = null;
        }
        if(System.currentTimeMillis() - finalPassiveStart >= 6000){
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"finalpassive");
        }
        if(this.ultLocation != null && System.currentTimeMillis() - this.ultStartTime < 4500 && System.currentTimeMillis() - lastSoundPlayed >= 300){
            ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_billy_nothung_pulse",this.ultLocation);
            lastSoundPlayed = System.currentTimeMillis();
        }
        if(this.ultLocation != null && System.currentTimeMillis() - this.ultStartTime < 4500){
            ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"billy_nothung_pulse",this.id+"_ultPulse",200,(float)this.ultLocation.getX(),(float)this.ultLocation.getY(),false,this.team,0f);
        }
    }

    private void usePassiveAbility(){
        this.passiveUses = 0;
        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_passive");
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        if(this.passiveUses < 3){
            if(this.passiveUses != 0) ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"p"+this.passiveUses);
            this.passiveUses++;
            if(this.passiveUses != 3){
                ExtensionCommands.addStatusIcon(this.parentExt,this.player,"p"+this.passiveUses,"billy_spell_4_short_description","icon_billy_p"+this.passiveUses,0f);
                if(this.passiveUses == 2) ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"billy_passive",1000*60*15,this.id+"_passive",true,"Bip001",true,false,this.team);
            }

        }
        switch(ability){
            case 1:
                this.canCast[0] = false;
                this.stopMoving();
                Path2D quadrangle = Champion.createRectangle(location,dest,Q_SPELL_RANGE, Q_OFFSET_DISTANCE);
                for(Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()){
                    if(a.getTeam() != this.team && quadrangle.contains(a.getLocation())){
                        if(isNonStructure(a)) a.knockback(this.location);
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                        if(this.passiveUses == 3) a.addState(ActorState.STUNNED,0d,2000,null,false);
                    }
                }
                if(this.passiveUses == 3) this.usePassiveAbility();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_billy_knock_back",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new BillyAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown, TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_billy_jump",this.location);
                Point2D ogLocation = this.location;
                Point2D finalDashPoint = this.dash(dest,true,14d);
                double time = ogLocation.distance(finalDashPoint)/14d;
                int wTime = (int) (time*1000);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"billy_dash_trail",(int)(time*1000),this.id+"_dash",true,"Bip001",true,false,this.team);
                ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell2",wTime,false);
                if(this.passiveUses == 3){
                    this.addEffect("attackSpeed",this.getStat("attackSpeed")*-0.7d,4000,null,false);
                    this.addEffect("speed",0.8d,6000,null,true);
                    this.usePassiveAbility();
                    ExtensionCommands.addStatusIcon(this.parentExt,this.player,"finalpassive","billy_spell_4_short_description","icon_billy_passive",6000);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"billy_crit_hands",4000,this.id+"_critHandsR",true,"Bip001 R Hand",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"billy_crit_hands",4000,this.id+"_critHandsL",true,"Bip001 L Hand",true,false,this.team);
                    finalPassiveStart = System.currentTimeMillis();
                }
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new BillyAbilityHandler(ability,spellData,cooldown,gCooldown,finalDashPoint),(int)(time*1000d),TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                this.stopMoving(castDelay);
                this.ultLoc = Champion.getAbilityLine(this.location,dest,5.5f).getP2();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_billy_nothung",this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"lemongrab_ground_aoe_target",this.id+"_billyUltTarget",1750,(float)ultLoc.getX(),(float)ultLoc.getY(),true,this.team,0f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new BillyAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class BillyAbilityHandler extends AbilityRunnable {

        public BillyAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            ExtensionCommands.playSound(parentExt,room,id,"sfx_billy_ground_pound_temp",dest);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell2a",500,false);
            ExtensionCommands.createActorFX(parentExt,room,id,"billy_ground_pound",500,id+"_qLand",false,"",false,false,team);
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,2f)){
                if(isNonStructure(a)){
                    a.addToDamageQueue(Billy.this,getSpellDamage(spellData),spellData);
                }
            }
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            ExtensionCommands.playSound(parentExt,room,"","sfx_billy_nothung_skyfall",ultLoc);
            int duration = 1000;
            if(passiveUses == 3){
                ultLocation = ultLoc;
                ultStartTime = System.currentTimeMillis();
                lastUltTick = System.currentTimeMillis();
                lastSoundPlayed = System.currentTimeMillis();
                duration = 4500;
                usePassiveAbility();
            }
            ExtensionCommands.createWorldFX(parentExt,room,id,"billy_nothung_skyfall",id+"_ult",duration,(float)ultLoc.getX(),(float)ultLoc.getY(),false,team,0f);
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),ultLoc,2.25f)){
                if(isNonStructure(a)){
                    a.addToDamageQueue(Billy.this,getSpellDamage(spellData),spellData);
                }
            }
        }

        @Override
        protected void spellPassive() {

        }
    }
}

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

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Lemongrab extends UserActor {

    private int unacceptableLevels = 0;
    private long lastHit = -1;
    private String lastIcon = "lemon0";

    public Lemongrab(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.addStatusIcon(parentExt,player,"lemon0","UNACCEPTABLE!!!!!","icon_lemongrab_passive",0f);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData){
        if(!this.dead && this.unacceptableLevels < 3 && System.currentTimeMillis() - lastHit >= 2000){
            this.unacceptableLevels++;
            String iconName = "lemon"+this.unacceptableLevels;
            ExtensionCommands.removeStatusIcon(parentExt,player,lastIcon);
            this.lastIcon = iconName;
            ExtensionCommands.addStatusIcon(parentExt,player,iconName,"UNACCEPTABLE!!!!!","icon_lemongrab_p"+this.unacceptableLevels,0f);
            this.lastHit = System.currentTimeMillis();
        }
        return super.damaged(a,damage,attackData);
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.unacceptableLevels > 0 && System.currentTimeMillis() - lastHit >= 6000){
            this.unacceptableLevels--;
            String iconName = "lemon"+this.unacceptableLevels;
            ExtensionCommands.removeStatusIcon(parentExt,player,lastIcon);
            this.lastIcon = iconName;
            if(this.unacceptableLevels != 0) ExtensionCommands.addStatusIcon(parentExt,player,iconName,"UNACCEPTABLE!!!!!","icon_lemongrab_p"+this.unacceptableLevels,0f);
            else ExtensionCommands.addStatusIcon(parentExt,player,iconName,"UNACCEPTABLE!!!!!","icon_lemongrab_passive"+this.unacceptableLevels,0f);
            this.lastHit = System.currentTimeMillis();
        }
    }

    @Override
    public void die(Actor a){
        this.unacceptableLevels = 0;
        super.die(a);
    }
    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        super.useAbility(ability,spellData,cooldown,gCooldown,castDelay,dest);
        switch(ability){
            case 1:
                this.canCast[0] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_lemongrab_sound_sword",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lemongrab_sound_sword",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"lemongrab_sonic_cone",castDelay,this.id+"_sonicCone",true,"Sword",true,false,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"lemongrab_sonic_sword_effect",castDelay,this.id+"_sonicSword",true,"Sword",true,false,this.team);
                Line2D ogLine = new Line2D.Float(this.location,dest);
                List<Actor> affectedActors = Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),Champion.getMaxRangeLine(ogLine,6f),4f);
                for(Actor a : affectedActors){
                    if(this.isNonStructure(a)){
                        a.addState(ActorState.SLOWED,0.4d,2500,null,false);
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                    }
                }
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LemonAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"lemongrab_ground_aoe_target",this.id+"wTarget",castDelay,(float)dest.getX(),(float)dest.getY(),true,this.team,0f);
                Runnable delayedJuice = () -> {
                    ExtensionCommands.createWorldFX(parentExt,room,id,"lemongrab_ground_juice_aoe",id+"_wJuice",2000,(float)dest.getX(),(float)dest.getY(),false,team,0f);
                    ExtensionCommands.playSound(parentExt,room,"","sfx_lemongrab_my_juice",dest);
                };
                SmartFoxServer.getInstance().getTaskScheduler().schedule(delayedJuice,1250,TimeUnit.MILLISECONDS);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lemongrab_my_juice",this.location);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LemonAbilityHandler(ability,spellData,cooldown,gCooldown,dest),2000,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_2.5",this.id+"_jailRing",castDelay,(float)dest.getX(),(float)dest.getY(),true,this.team,0f);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LemonAbilityHandler(ability,spellData,cooldown,gCooldown,dest),1500,TimeUnit.MILLISECONDS);
                String voiceLine = "lemongrab_dungeon_3hours";
                switch(this.unacceptableLevels){
                    case 1:
                        voiceLine = "lemongrab_dungeon_30days";
                        break;
                    case 2:
                        voiceLine = "lemongrab_dungeon_12years";
                        break;
                    case 3:
                        voiceLine = "lemongrab_dungeon_1myears";
                        break;
                }
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,voiceLine,this.location);
                break;
            case 4:
                break;
        }
    }

    public void attack(Actor a){
        this.handleAttack(a);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,(int)this.getPlayerStat("attackDamage"),"basicAttack"),250, TimeUnit.MILLISECONDS);
    }

    private class LemonAbilityHandler extends AbilityRunnable{

        public LemonAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            ExtensionCommands.createWorldFX(parentExt,room,id,"lemongrab_head_splash",id+"_wHead",500,(float)dest.getX(),(float)dest.getY(),false,team,0f);
            List<Actor> affectedActors = new ArrayList<>();
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,1f)){
                if(isNonStructure(a)){
                    affectedActors.add(a);
                    a.addToDamageQueue(Lemongrab.this,getSpellDamage(spellData),spellData);
                    a.addState(ActorState.BLINDED,0d,4000,null,false);
                    a.addState(ActorState.SILENCED,0d,2000,null,false);
                }
            }
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,2f)){
                if(isNonStructure(a) && !affectedActors.contains(a)){
                    double damage = 60d + (getPlayerStat("spellDamage")*0.4d);
                    a.addState(ActorState.BLINDED,0d,4000,null,false);
                    a.addToDamageQueue(Lemongrab.this,damage,spellData);
                }
            }
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            ExtensionCommands.createWorldFX(parentExt,room,id,"lemongrab_dungeon_hit",id+"_jailHit",500,(float)dest.getX(),(float)dest.getY(),false,team,0f);
            double damage = getSpellDamage(spellData);
            double duration = 2000d;
            damage*= (1d+(0.1d*unacceptableLevels));
            duration*= (1d+(0.1d*unacceptableLevels));
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,2.5f)){
                if(isNonStructure(a)){
                    a.addToDamageQueue(Lemongrab.this,damage,spellData);
                    a.addState(ActorState.STUNNED,0d,(int)duration,null,false);
                    ExtensionCommands.createActorFX(parentExt,room,a.getId(),"lemongrab_lemon_jail",(int)duration,a.getId()+"_jailed",true,"",true,false,team);
                }
            }
            unacceptableLevels = 0;
            ExtensionCommands.removeStatusIcon(parentExt,player,lastIcon);
            ExtensionCommands.addStatusIcon(parentExt,player,"lemon0","UNACCEPTABLE!!!","icon_lemongrab_passive",0f);
            lastIcon = "lemon0";
        }

        @Override
        protected void spellPassive() {

        }
    }
}

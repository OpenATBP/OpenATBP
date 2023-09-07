package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class Marceline extends UserActor {

    private int passiveHits = 0;
    private boolean healthRegenEffectActive = false;
    private boolean wActive = false;
    private Actor qVictim;
    private long qHit = -1;
    private long wStartTime = 0;
    private long regenSound = 0;
    public Marceline(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public double getPlayerStat(String stat){
        if(stat.equalsIgnoreCase("healthRegen")){
            if(this.getState(ActorState.TRANSFORMED)) return super.getPlayerStat(stat) * 1.5d;
            else return super.getPlayerStat(stat);
        }else return super.getPlayerStat(stat);
    }

    public void update(int msRan){
        super.update(msRan);
        if(this.currentHealth < this.maxHealth && !this.healthRegenEffectActive && this.states.get(ActorState.TRANSFORMED)){
            System.out.println("Starting marceline effect");
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_vampiric_healing",15*1000*60,this.id+"_batRegen",true,"",true,false,this.team);
            this.healthRegenEffectActive = true;
        }else if(healthRegenEffectActive && this.currentHealth == this.maxHealth){
            System.out.println("Ending marceline effect");
            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_batRegen");
            this.healthRegenEffectActive = false;
        }

        if(this.wActive && !this.getState(ActorState.TRANSFORMED)){
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,2f)){
                if(a.getTeam() != this.team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell2");
                    double damage = getSpellDamage(spellData)/10d;
                    a.addToDamageQueue(this,damage,spellData);
                }
            }
        }

        if(this.qVictim != null && System.currentTimeMillis() - this.qHit >= 450){
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell1");
            double damage = this.getSpellDamage(spellData)/3d;
            ExtensionCommands.playSound(this.parentExt,this.room,this.qVictim.getId(),"sfx_marceline_blood_hit",this.qVictim.getLocation());
            this.qVictim.addToDamageQueue(this,damage,spellData);
            this.qHit = System.currentTimeMillis();
        }
        if(wActive){
            if(System.currentTimeMillis() - wStartTime >= 4500){
                wActive = false;
            }
        }
        if(this.getState(ActorState.TRANSFORMED) && this.currentHealth < this.maxHealth && System.currentTimeMillis() - regenSound >= 3000){
            regenSound = System.currentTimeMillis();
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"marceline_regen_loop",this.location);
        }
    }

    @Override
    public void attack(Actor a){
        if(this.attackCooldown == 0){
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new MarcelineAttack(a,this.handleAttack(a)),350,TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void handleLifeSteal(){
        double damage = this.getPlayerStat("attackDamage");
        double lifesteal = this.getPlayerStat("lifeSteal")/100;
        if(this.passiveHits == 3){
            this.passiveHits = 0;
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_marceline_crit_fangs",this.location);
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_crit_fangs",1000,this.id+"_passiveHit",true,"Bip001 Head",false,false,this.team);
            lifesteal = 1d;
        }
        this.changeHealth((int)Math.round(damage*lifesteal));
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        switch(ability){
            case 1: //Q
                this.stopMoving(castDelay);
                this.canCast[0] = false;
                Line2D maxRangeLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest),7f);
                String projectileId = "projectile_marceline_dot";
                String projectileVoPrefix = (this.avatar.contains("marshall")) ? "marshall_lee_" : (this.avatar.contains("young")) ? "marceline_young_" : "marceline_";
                if(this.getState(ActorState.TRANSFORMED)){
                    projectileId = "projectile_marceline_root";
                    ExtensionCommands.playSound(parentExt,room,this.id,"vo/vo_"+projectileVoPrefix+"projectile_beast", this.location);
                }else{
                    ExtensionCommands.playSound(parentExt,room,this.id,"vo/vo_"+projectileVoPrefix+"projectile_human", this.location);
                }
                ExtensionCommands.playSound(this.parentExt,this.room,"","marceline_throw_projectile",this.location);
                this.fireProjectile(new MarcelineProjectile(this.parentExt,this,maxRangeLine,8f,0.5f,this.id+projectileId,this.getState(ActorState.TRANSFORMED)),projectileId, dest, 7f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MarcelineAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 2: //W
                this.canCast[1] = false;
                wActive = true;
                wStartTime = System.currentTimeMillis();
                String bloodMistVo = (this.avatar.contains("marshall")) ? "vo/vo_marshall_lee_blood_mist" : (this.avatar.contains("young")) ? "vo/vo_marceline_young_blood_mist" : "vo/vo_marceline_blood_mist";
                if(this.states.get(ActorState.TRANSFORMED)){
                    attackCooldown = 0;
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_marceline_beast_crit_activate",this.location);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_beast_crit_hand",4500,this.id+"_beastHands",true,"Bip001 R Hand",true,false,this.team);
                    this.addEffect("speed",this.getStat("speed")*0.4d,4500,null,false);
                }else{
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,bloodMistVo,this.location);
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_marceline_blood_mist",this.location);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_vamp_mark",4500,this.id+"_wBats",true,"",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_blood_mist",4500,this.id+"_mist",true,"",true,false,this.team);
                    this.addEffect("speed",this.getStat("speed")*0.3d,4500,null,false);
                }
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MarcelineAbilityHandler(ability,spellData,cooldown,gCooldown,dest),getReducedCooldown(cooldown),TimeUnit.MILLISECONDS);
                break;
            case 3: //E
                this.canCast[2] = false;
                this.stopMoving(castDelay);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_marceline_spell_casting",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_spell_casting",castDelay,this.id+"_transformCast",true,"",true,false,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_3",castDelay,this.id+"_transformRing",true,"",false,true,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MarcelineAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay, TimeUnit.MILLISECONDS);
                break;
            case 4: //Passive
                break;
        }
    }

    private class MarcelineAttack implements Runnable{

        Actor target;
        boolean crit;

        MarcelineAttack(Actor t, boolean crit){
            this.target = t;
            this.crit = crit;
        }
        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if(!getState((ActorState.TRANSFORMED)) && crit) damage*=2;
            if(wActive && getState(ActorState.TRANSFORMED)){
                if(crit) damage*=4;
                else damage*=2;
                double lifesteal = 1d;
                changeHealth((int)Math.round(damage*lifesteal));
            }
            if(!getState(ActorState.TRANSFORMED) && isNonStructure(this.target)) passiveHits++;
            if(wActive && getState(ActorState.TRANSFORMED)){
                System.out.println("Marceline hit with the W");
                wActive = false;
                ExtensionCommands.playSound(parentExt,room,id,"sfx_marceline_beast_crit_hit",location);
                ExtensionCommands.createActorFX(parentExt,room, target.getId(),"marceline_beast_crit_hit",500,target.getId()+"_marcelineHit",false,"targetNode",true,false,target.getTeam());
                ExtensionCommands.removeFx(parentExt,room,id+"_beastHands");
            }else{
                System.out.println("W active: " + wActive + " | " + getState(ActorState.TRANSFORMED));
            }
            new Champion.DelayedAttack(parentExt,Marceline.this,target,(int)damage,"basicAttack").run();
            canMove = true;
        }
    }

    private class MarcelineAbilityHandler extends AbilityRunnable {
        public MarcelineAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            wActive = false;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            wActive = false;
            if(getState(ActorState.TRANSFORMED)){
                ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
                setState(ActorState.TRANSFORMED, false);
                String morphHumanVo = (avatar.contains("marshall")) ? "vo/marshall_lee_morph_to_human" : "marceline_morph_to_human";
                ExtensionCommands.playSound(parentExt,room,id,morphHumanVo,location);
                ExtensionCommands.removeFx(parentExt,room,id+"_beastHands");
                if(healthRegenEffectActive){
                    ExtensionCommands.removeFx(parentExt,room,id+"_batRegen");
                    healthRegenEffectActive = false;
                }
                double currentAttackSpeed = getPlayerStat("attackSpeed");
                attackCooldown = 0d;
                Marceline.this.addEffect("attackSpeed",500-currentAttackSpeed,3000,null,false);

            }else{
                String morphBeastVo = (avatar.contains("marshall")) ? "vo/marshall_lee_morph_to_beast" : "marceline_morph_to_beast";
                ExtensionCommands.playSound(parentExt,room,id,morphBeastVo,location);
                ExtensionCommands.swapActorAsset(parentExt,room,id,"marceline_bat");
                passiveHits = 0;
                ExtensionCommands.createActorFX(parentExt,room,id,"statusEffect_immunity",2000,id+"_ultImmunity",true,"displayBar",false,false,team);
                Marceline.this.addState(ActorState.IMMUNITY,0d,2000,null,false);
                setState(ActorState.CLEANSED, true);
                Marceline.this.cleanseEffects();
                setState(ActorState.TRANSFORMED, true);
            }
            updateStatMenu("healthRegen");
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,3)){
                if(a.getTeam() != team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                    double damage = getSpellDamage(spellData);
                    a.addToDamageQueue(Marceline.this,damage,spellData);
                    if(!getState(ActorState.TRANSFORMED)){
                        a.handleCharm(Marceline.this,2000);
                    }else{
                        a.handleFear(Marceline.this,2000);
                    }
                }
            }
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class MarcelineProjectile extends Projectile{

        boolean transformed;
        public MarcelineProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id, boolean transformed) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.transformed = transformed;
        }

        @Override
        protected void hit(Actor victim) {
            if(transformed){
                victim.addState(ActorState.ROOTED,0d,3000,null,false);
            }else{
                qVictim = victim;
                qHit = System.currentTimeMillis();
                Runnable endVictim = () -> {qVictim = null; qHit = -1;};
                SmartFoxServer.getInstance().getTaskScheduler().schedule(endVictim,1500,TimeUnit.MILLISECONDS);
                victim.addState(ActorState.SLOWED,0.15d,1500,null,false);
            }
            JsonNode spellData = parentExt.getAttackData(avatar,"spell1");
            victim.addToDamageQueue(this.owner,getSpellDamage(spellData),spellData);
            ExtensionCommands.playSound(parentExt,room,"","sfx_marceline_blood_hit",this.location);
            this.destroy();
        }
    }
}

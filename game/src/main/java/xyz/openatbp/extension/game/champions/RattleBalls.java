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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RattleBalls extends UserActor {
    private boolean passiveActive = false;
    private int passiveHits = 0;
    private long passiveStart = 0;
    private long passiveCooldown = 0;
    private int qUse = 0;
    private boolean parryActive = false;
    private long parryCooldown = 0;
    private boolean ultActive = false;
    private long lastSoundPlayed = 0;
    public RattleBalls(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        System.out.println("Ability used: " + ability);
        if(!this.passiveActive && System.currentTimeMillis() - this.passiveCooldown >= 5000){
            this.passiveStart = System.currentTimeMillis();
            this.passiveActive = true;
            this.passiveHits = 2;
            ExtensionCommands.addStatusIcon(this.parentExt,this.player,"passive2","rattleballs_spell_4_short_description","icon_rattleballs_p2",3000);
        }
        switch(ability){
            case 1:
                this.canCast[0] = false;
                Point2D dashPoint = this.dash(dest,false,1f);
                double time = this.location.distance(dashPoint)/DASH_SPEED;
                if(this.qUse == 0){
                    String dashTrailFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_dash_trail" : "rattleballs_dash_trail";
                    String dashDustFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_dash_dust" : "rattleballs_dash_dust";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_counter_stance",this.location);
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_dash",this.location);
                    ExtensionCommands.actorAnimate(parentExt,room,id,"spell1a",(int)(time*1000),true);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,dashTrailFx,1000,this.id+"_q1Trail",true,"Bip001",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,dashDustFx,1000,this.id+"_q1Dust",true,"",true,false,this.team);
                    this.qUse++;
                }else{
                    this.qUse = 0;
                    this.parryActive = false;
                    String dashTrailFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_dash_trail" : "rattleballs_dash_trail";
                    String dashDustFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_dash_dust" : "rattleballs_dash_dust";
                    String dashSfx = (this.avatar.contains("spidotron")) ? "sfx_rattleballs_luchador_counter_stance" : "sfx_rattleballs_dash";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,dashSfx,this.location);
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_rattle_balls_2",this.location);
                    ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell1c",(int)(time*1000),false);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,dashTrailFx,1000,this.id+"_q2Trail",true,"Bip001",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,dashDustFx,1000,this.id+"_q2Dust",true,"Bip001 Footsteps",true,false,this.team);
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                }
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new RattleAbilityHandler(ability,spellData,cooldown,gCooldown,dashPoint),(int)(time*1000), TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                String pullSfx = (this.avatar.contains("spidotron")) ? "sfx_rattleballs_luchador_pull" : "sfx_rattleballs_pull";
                ExtensionCommands.playSound(parentExt,room,id,pullSfx,location);
                this.stopMoving(castDelay);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_5",castDelay,this.id+"_wCircle",false,"Bip001",false,true,this.team);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new RattleAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 3:
                if(!this.ultActive){
                    this.canCast[0] = false;
                    this.canCast[1] = false;
                    this.ultActive = true;
                    String spinSfx = (this.avatar.contains("spidotron")) ? "sfx_rattleballs_luchador_spin_cycle" : "sfx_rattleballs_spin_cycle";
                    String sparklesFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_sword_sparkles" : "rattleballs_sword_sparkles";
                    String swordSpinFX = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_sword_spin" : "rattleballs_sword_spin";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,spinSfx,this.location);
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_rattleballs_eggcelent_1",this.location);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_2",3500,this.id+"_ultRing",true,"Bip001",false,true,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,sparklesFx,3500,this.id+"_ultSparkles",true,"Bip001 Prop1",true,false,this.team);
                    ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell3a",3500,true);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,swordSpinFX,3500,this.id+"_ultSpin",true,"Bip001",false,false,this.team);
                    this.addEffect("speed",this.getStat("speed")*0.14d,3500,null,true);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new RattleAbilityHandler(ability,spellData,cooldown,gCooldown,dest),3500,TimeUnit.MILLISECONDS);
                }else{
                    this.canCast[0] = true;
                    this.canCast[1] = true;
                    this.canCast[2] = false;
                    this.ultActive = false;
                    ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultRing");
                    ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSpin");
                    ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSparkles");
                    ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"run",100,false);
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),0);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new RattleAbilityHandler(ability,spellData,cooldown,gCooldown,dest),250,TimeUnit.MILLISECONDS);
                }
                break;
        }
    }

    private void endPassive(){
        this.passiveActive = false;
        this.passiveCooldown = System.currentTimeMillis();
        if(this.passiveHits == 0) this.passiveHits++;
        ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"passive",true,5000,0);
        ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive"+this.passiveHits);
    }

    @Override
    public boolean canAttack() {
        if(this.ultActive) return false;
        return super.canAttack();
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(this.passiveActive && System.currentTimeMillis() - this.passiveStart >= 3000){
            System.out.println("Passive ending update");
            this.endPassive();
        }
        if(this.parryActive && System.currentTimeMillis() - this.parryCooldown >= 1500){
            this.canCast[0] = false;
            String spinSfx = (this.avatar.contains("spidotron")) ? "sfx_rattleballs_luchador_counter_attack" : "sfx_rattleballs_spin";
            String swordSpinFX = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_sword_spin" : "rattleballs_sword_spin";
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,spinSfx,this.location);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_rattle_balls_2",this.location);
            ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell3a",250,true);
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,swordSpinFX,250,this.id+"_parrySpin",true,"Bip001 Footsteps",false,false,this.team);
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"rattleballs_counter_trail",250,this.id+"_trail",true,"Bip001 Prop1",true,false,this.team);
            Runnable castDelay = () -> {this.canCast[0] = true;};
            SmartFoxServer.getInstance().getTaskScheduler().schedule(castDelay,500,TimeUnit.MILLISECONDS);
            this.parryActive = false;
            this.qUse = 0;
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(12000),0);
            List<Actor> affectedActors = Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,2f);
            affectedActors = affectedActors.stream().filter(this::isNonStructure).collect(Collectors.toList());
            if(affectedActors.size() > 0){
                //TODO: Make this more optimized
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"rattleballs_sword_sparkles",100,this.id+"_spark"+Math.random(),true,"Bip001",true,false,this.team);
            }
            for(Actor a : affectedActors){
                if(this.isNonStructure(a)){
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell1");
                    a.addToDamageQueue(this,getSpellDamage(spellData)+25,spellData);
                }
            }
        }
        if(this.ultActive){
            int soundCooldown = 450;
            if(System.currentTimeMillis() - lastSoundPlayed >= soundCooldown && !this.dead){
                lastSoundPlayed = System.currentTimeMillis();
                ExtensionCommands.playSound(this.parentExt, this.room, this.id, "sfx_rattleballs_counter_stance", this.location);
            }
        if(this.ultActive && this.dead){
            this.ultActive = false;
            this.canCast[0] = true;
            this.canCast[1] = true;
            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultRing");
            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSpin");
            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSparkles");
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(60000),0);
        }
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,2f)){
                if(this.isNonStructure(a)){
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell3");
                    a.addToDamageQueue(this,(double)getSpellDamage(spellData)/10d,spellData);
                }
            }
        }
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(this.parryActive && this.getAttackType(attackData) == AttackType.SPELL){
            this.parryActive = false;
            this.qUse = 0;
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(12000),0);
            ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"run",100,false);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_counter_fail",this.location);
        }
        if(this.parryActive){
            this.qUse = 0;
            this.parryActive = false;
            JsonNode basicAttackData = this.getParentExt().getAttackData(this.avatar,"basicAttack");
            a.addToDamageQueue(this,this.getPlayerStat("attackDamage")*2,basicAttackData);
            String counterSFx = (this.avatar.contains("spidotron")) ? "sfx_rattleballs_luchador_counter_attack_crit" : "sfx_rattleballs_counter_stance";
            String counterCritFx = (this.avatar.contains("spidotron")) ? "rattleballs_luchador_dash_hit" : "rattleballs_dash_hit";
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,counterSFx,this.location);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_rattle_balls_2",this.location);
            ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"crit",1000,true);
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"rattleballs_dash_hit",1000,this.id,true,"",true,false,this.team);
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(12000),0);
            return false;
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void handleLifeSteal() {
        if(this.passiveActive && passiveHits > 0){
            this.changeHealth((int)(this.getPlayerStat("attackDamage")*0.65d));
            this.passiveHits--;
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"rattleballs_regen",2000,this.id+"_passiveRegen",true,"Bip001 Spine1",true,false,this.team);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_rattleballs_regen",this.location);
            if(this.passiveHits <= 0){
                System.out.println("Passive ending here");
                this.endPassive();
            }else{
                ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive"+(this.passiveHits+1));
                ExtensionCommands.addStatusIcon(this.parentExt,this.player,"passive"+this.passiveHits,"rattleballs_spell_4_short_description","icon_rattleballs_p"+this.passiveHits,3000f-(System.currentTimeMillis()-this.passiveStart));
            }
        }
        else super.handleLifeSteal();
    }

    private class RattleAbilityHandler extends AbilityRunnable {

        public RattleAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            if(qUse == 1){
                parryActive = true;
                parryCooldown = System.currentTimeMillis();
                Runnable flipDelay = () -> {
                    if(parryActive){
                        stopMoving(1200);
                        ExtensionCommands.playSound(parentExt,room,id,"sfx_rattleballs_counter_stance",location);
                        ExtensionCommands.actorAnimate(parentExt,room,id,"spell1b",1500,true);
                        ExtensionCommands.createActorFX(parentExt,room,id,"rattleballs_counter_stance",1500,id+"_stance",true,"Bip001",true,false,team);
                    }
                };
                SmartFoxServer.getInstance().getTaskScheduler().schedule(flipDelay,400,TimeUnit.MILLISECONDS);
            }else{
                boolean sound = false;
                for(Actor a : Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),new Line2D.Float(location,dest),2d)){
                    if(!sound){
                        ExtensionCommands.playSound(parentExt,room,id,"sfx_rattleballs_dash_hit",location);
                        sound = true;
                    }
                    if(isNonStructure(a)){
                        ExtensionCommands.createActorFX(parentExt,room,a.getId(),"rattleballs_dash_hit",500,a.getId()+"_rattleHit",true,"Bip001",true,false,a.getTeam());
                        a.addToDamageQueue(RattleBalls.this,getSpellDamage(spellData),spellData);
                    }
                }
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            String pullFx = (avatar.contains("spidotron")) ? "rattleballs_luchador_pull" : "rattleballs_pull";
            ExtensionCommands.createActorFX(parentExt,room,id,pullFx,500,id+"_pull",true,"Bip001",true,false,team);
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),location,5f)){
                if(isNonStructure(a)){
                    a.pulled(location);
                    a.addToDamageQueue(RattleBalls.this,getSpellDamage(spellData),spellData);
                }
            }
        }

        @Override
        protected void spellE() {
            if(ultActive){
                canCast[0] = true;
                canCast[1] = true;
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,getReducedCooldown(cooldown),0);
            }else{
                canCast[2] = true;
            }
            ultActive = false;
        }

        @Override
        protected void spellPassive() {

        }
    }
}

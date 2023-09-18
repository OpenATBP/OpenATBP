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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BMO extends UserActor {
    private  int passiveStacks = 0;
    private boolean wActive = false;
    private long wStartTime = 0;
    private long lastWSound = 0;
    private boolean ultSlowActive = false;

    public BMO(User u, ATBPExtension parentExt){
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a){
        String projectileFx = (this.avatar.contains("noir")) ? "bmo_projectile_noire" : "bmo_projectile";
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new BMOPassive(a, this.handleAttack(a)), projectileFx), 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(wActive){
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,4f)){
                if(a.getTeam() != this.team && isNonStructure(a)){
                    JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                    a.addToDamageQueue(this, (double)getSpellDamage(spellData)/10d,spellData);
                    if(passiveStacks == 3) a.addState(ActorState.SLOWED,0.5d,2500,null,false);
                }
            }
            if(System.currentTimeMillis() - lastWSound >= 500){
                lastWSound = System.currentTimeMillis();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_bmo_pixels_shoot1",this.location);
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        switch(ability){
            case 1:
                this.canCast[0] = false;
                String cameraFx = (this.avatar.contains("noir")) ? "bmo_camera_noire" : "bmo_camera";
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,cameraFx,500,this.id+"_camera",true,"",true,false,this.team);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_bmo_camera",this.location);
                Line2D spellLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest), 6f);
                for(Actor a : Champion.getActorsAlongLine(this.parentExt.getRoomHandler(this.room.getId()),spellLine,4f)){
                    if(a.getTeam() != this.team && a.getActorType() != ActorType.BASE){
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                        a.addState(ActorState.BLINDED,0.5d,2500,null,false);
                        if(passiveStacks == 3) a.addState(ActorState.SLOWED,0d,2500,null,false);
                    }
                }
                if(passiveStacks == 3) usePassiveStacks();
                else addPasiveStacks();
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new BMOAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 2:
                if(!this.wActive){
                    this.setCanMove(false);
                    this.stopMoving();
                    this.canCast[0] = false;
                    this.canCast[1] = false;
                    this.canCast[2] = false;
                    this.wActive = true;
                    wStartTime = System.currentTimeMillis();
                    Runnable secondUseDelay = () -> this.canCast[1] = true;
                    String pixelsAoeFx = (this.avatar.contains("noir")) ? "bmo_pixels_aoe_noire" : "bmo_pixels_aoe";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_bmo_pixels_start",this.location);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_bmo_remote",3000,this.id+"_bmo_remote",true,"",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,pixelsAoeFx,3000,this.id+"_pixels_aoe",true,"",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_4",3000,this.id+"_target_ring_4.5",true,"",true,true,this.team);
                    ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell2",3000,true);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(secondUseDelay,500,TimeUnit.MILLISECONDS);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new BMOAbilityHandler(ability,spellData,cooldown,gCooldown,dest),3000,TimeUnit.MILLISECONDS);
                }else{
                    this.canCast[0] = true;
                    this.canCast[2] = true;
                    this.wActive = false;
                    this.wEnd(cooldown,gCooldown);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new BMOAbilityHandler(ability,spellData,cooldown,gCooldown,dest),250,TimeUnit.MILLISECONDS);
                }
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                if(passiveStacks == 3){
                    ultSlowActive = true;
                    passiveStacks = 0;
                }
                else addPasiveStacks();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_bmo_ultimate",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_bmo_ultimate",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new BMOAbilityHandler(ability,spellData,cooldown,gCooldown,dest), castDelay,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private void addPasiveStacks(){
        if(passiveStacks < 3){
            if(passiveStacks != 0) ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"bmoPassive"+passiveStacks);
            this.passiveStacks++;
            ExtensionCommands.addStatusIcon(this.parentExt,this.player,"bmoPassive"+passiveStacks,"bmo_spell_4_short_description","icon_bmo_p"+passiveStacks,0);
        }
        if(passiveStacks == 3){
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"vo/vo_bmo_passive_on",this.location);
            ExtensionCommands.playSound(this.parentExt,this.player,this.id,"sfx_bmo_passive",this.location);
            String passiveFx = (this.avatar.contains("noir")) ? "bmo_passive_noire" : "bmo_passive";
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,passiveFx,1000*60*15,this.id+"_bmo_p_fx",true,"",false,false,this.team);
        }
    }

    private void usePassiveStacks(){
        ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"bmoPassive"+passiveStacks);
        ExtensionCommands.removeFx(this.parentExt,this.player,this.id+"_bmo_p_fx");
        this.passiveStacks = 0;
    }

    private void wEnd(int cooldown, int gCooldown){
        canMove = true;
        for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,4f)){
            if(a.getTeam() != this.team && isNonStructure(a)){
                JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                long wDuration = System.currentTimeMillis() - wStartTime;
                double damageMultiplier = 1 + (wDuration / 10000d);
                a.addToDamageQueue(this,(this.getSpellDamage(spellData))*damageMultiplier,spellData);
                a.addState(ActorState.STUNNED,0d,1000,null,false);
            }
        }
        if(passiveStacks == 3) usePassiveStacks();
        else addPasiveStacks();
        String aoeExplodeFX = (this.avatar.contains("noir")) ? "bmo_pixels_aoe_explode_noire" : "bmo_pixels_aoe_explode";
        ExtensionCommands.removeFx(this.parentExt,this.player,this.id+"_pixels_aoe");
        ExtensionCommands.removeFx(this.parentExt,this.player,this.id+"_bmo_remote");
        ExtensionCommands.removeFx(this.parentExt,this.player,this.id+"_target_ring_4.5");
        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_bmo_yay",this.location);
        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_bmo_pixels_explode",this.location);
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,aoeExplodeFX,750,this.id+"_pixels_aoe_explode",true,"",false,false,this.team);
        ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
        ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"idle",100,false);
    }

    private class BMOAbilityHandler extends AbilityRunnable {

        public BMOAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest){
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ(){
            canCast[0] = true;

        }

        @Override
        protected void spellW(){
            if(wActive){
                canCast[0] = true;
                canCast[2] = true;
                wEnd(cooldown,gCooldown);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"w",true,getReducedCooldown(cooldown),250);
            }else{
                canCast[1] = true;
            }
            wActive = false;

        }

        @Override
        protected void spellE(){
            canCast[2] = true;
            Line2D fireLine = new Line2D.Float(location,dest);
            Line2D newLine = Champion.getMaxRangeLine(fireLine,16f);
            String ultProjectile = (avatar.contains("noir")) ? "projectile_bmo_bee_noire" : "projectile_bmo_bee";
            fireProjectile(new BMOUltProjectile(parentExt,BMO.this,newLine,5f,1.5f,id+ultProjectile),ultProjectile,newLine.getP2(),16f);
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class BMOUltProjectile extends Projectile {
        private List<Actor> victims;
        private double damageReduction = 0d;

        public BMOUltProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id){
            super(parentExt,owner,path,speed,hitboxRadius,id);
            this.victims = new ArrayList<>();
        }

        @Override
        protected void hit(Actor victim){
            this.victims.add(victim);
            JsonNode spellData = this.parentExt.getAttackData(BMO.this.avatar, "spell3");
            victim.addToDamageQueue(BMO.this, getSpellDamage(spellData)*(1-damageReduction),spellData);
            ExtensionCommands.playSound(parentExt,room,"","akubat_projectileHit1",victim.getLocation());
            if(ultSlowActive) victim.addState(ActorState.SLOWED,0.5d,2500,null,false);
            this.damageReduction+=0.3d;
            if(this.damageReduction > 0.9d) this.damageReduction = 0.9d;
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler){
            for(Actor a : roomHandler.getActors()){
                if(!this.victims.contains(a) && a.getTeam() != BMO.this.getTeam() && a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER && !a.getId().equalsIgnoreCase(BMO.this.id)){
                    double collisionRadius = parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                    if(a.getLocation().distance(location) <= hitbox + collisionRadius){
                        return a;
                    }
                }
            }
            return null;
        }

        public void destroy(){
            super.destroy();
            if(ultSlowActive){
                ExtensionCommands.removeStatusIcon(BMO.this.parentExt,BMO.this.player,"bmoPassive3");
                ultSlowActive = false;
            }
        }
    }

    private class BMOPassive implements Runnable{
        Actor target;
        boolean crit;

        BMOPassive(Actor a, boolean crit){
            this.target = a;
            this.crit = crit;
        }

        public void run(){
            double damage = BMO.this.getPlayerStat("attackDamage");
            if(crit) damage*=2;
            new Champion.DelayedAttack(parentExt,BMO.this,this.target,(int)damage,"basicAttack").run();
        }
    }
}

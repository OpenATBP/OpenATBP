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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Gunter extends UserActor{

    private boolean ultActivated = false;
    private Point2D ultPoint;
    public Gunter(User u, ATBPExtension parentExt){
        super(u,parentExt);
    }

    @Override
    public void update(int msRan){ //Add something so armor/mr isn't weird
        super.update(msRan);
        if(ultActivated && ultPoint != null){
            JsonNode spellData = parentExt.getAttackData(getAvatar(),"spell3");
            Line2D ogLine = new Line2D.Float(this.getRelativePoint(false),this.ultPoint);
            List<Actor> affectedActors = Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),Champion.getMaxRangeLine(ogLine,7f),4f);
            for(Actor a : affectedActors){
                if(a.getTeam() != this.team){
                    double damage = (double)getSpellDamage(spellData)/10;
                    a.addToDamageQueue(this,Math.round(damage),spellData);
                }
            }
        }
        if(ultActivated && this.hasInterrupingCC()){
            this.interruptE();
            this.ultPoint = null;
            this.ultActivated = false;
        }
    }
    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        super.useAbility(ability,spellData,cooldown,gCooldown,castDelay,dest);
        switch(ability){
            case 1:
                Point2D ogLocation = this.location;
                Point2D finalDastPoint = this.dash(dest,true,DASH_SPEED);
                double time = ogLocation.distance(finalDastPoint)/DASH_SPEED;
                int qTime = (int) (time*1000);
                ExtensionCommands.playSound(parentExt,this.room,this.id,"sfx_gunter_slide",this.location);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_slide_trail",qTime,this.id+"_gunterTrail",true,"Bip01",true,false,team);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_slide_snow",qTime,this.id+"_gunterTrail",true,"Bip01",true,false,team);
                ExtensionCommands.actorAnimate(parentExt,room,id,"spell1b",qTime,false);
                Runnable castReset = () -> {canCast[0] = true;};
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new GunterAbilityRunnable(ability,spellData,cooldown,gCooldown,finalDastPoint),qTime,TimeUnit.MILLISECONDS);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(castReset,250,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"q",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                break;
            case 2:
                Line2D maxRangeLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest),7f);
                ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_gunter_wing_it",this.location);
                this.fireProjectile(new BottleProjectile(this.parentExt,this,maxRangeLine,11f,0.5f,this.id+"projectile_gunter_bottle"),"projectile_gunter_bottle",dest,8f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"w",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new GunterAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 3: //TODO: Last left off - actually make this do damage
                this.ultPoint = dest;
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_powered_up",2500,this.id+"_gunterPower",true,"Bip01",true,false,team);
                ExtensionCommands.createActorFX(parentExt,room,this.id,"gunter_bottle_cone",2500,this.id+"gunterUlt",true,"Bip01",true,false,team);
                this.setCanMove(false);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new GunterAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),2500,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAnimate(parentExt,room,this.id,"spell3b",2500,true);
                ExtensionCommands.actorAbilityResponse(this.parentExt,player,"e",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                this.ultActivated = true;
                ExtensionCommands.playSound(parentExt,room,this.id,"sfx_gunter_bottles_ultimate",this.location);
                break;
        }
        this.canCast[ability-1] = false;
    }

    public void shatter(Actor a){
        ExtensionCommands.playSound(parentExt,room,"","sfx_gunter_slide_shatter",a.getLocation());
        ExtensionCommands.createWorldFX(parentExt,room,id,"gunter_belly_slide_bottles",a.getId()+"_shattered",1000,(float)a.getLocation().getX(),(float)a.getLocation().getY(),false,team,0f);
        for(Actor actor : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),a.getLocation(), 2f)){
            if(actor.getTeam() != this.team && !a.getId().equalsIgnoreCase(actor.getId())){
                JsonNode spellData = this.parentExt.getAttackData(this.getAvatar(),"spell4");
                actor.addToDamageQueue(this,getSpellDamage(spellData),spellData);
            }
        }
    }

    private void interruptE(){
        ExtensionCommands.removeFx(parentExt,this.room,this.id+"_gunterPower");
        ExtensionCommands.removeFx(parentExt,this.room,this.id+"gunterUlt");
        ExtensionCommands.actorAnimate(parentExt,this.room,this.id,"run",500,false);
        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_skill_interrupted",this.location);
    }

    @Override
    public boolean canUseAbility(int ability){
        if(ultActivated) return false;
        else return super.canUseAbility(ability);
    }

    @Override
    public boolean canAttack(){
        if(this.ultActivated) return false;
        else return super.canAttack();
    }

    @Override
    public boolean canMove(){
        if(this.ultActivated) return false;
        else return super.canMove();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if(this.ultActivated){
            this.ultActivated = false;
            this.ultPoint = null;
            ExtensionCommands.removeFx(parentExt,room,this.id+"_gunterPower");
            ExtensionCommands.removeFx(parentExt,room,this.id+"_gunterUlt");
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData){
        super.handleKill(a,attackData);
        System.out.println(attackData.toString());
        if(attackData.has("spellName") && attackData.get("spellName").asText().contains("spell_2")) this.shatter(a);
        else if(attackData.has("attackName") && attackData.get("attackName").asText().contains("Basic")) this.shatter(a);
    }

    private class GunterAbilityRunnable extends AbilityRunnable {

        public GunterAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            ExtensionCommands.createActorFX(parentExt,room,id,"gunter_belly_slide_bottles",1000,id+"_slide_bottles",false,"",false,false,team);
            ExtensionCommands.playSound(parentExt,room,id,"sfx_gunter_slide_shatter",location);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell1c",500,false);
            List<Actor> affectedActors = Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),location,2f);
            for(Actor a : affectedActors){
                if(a.getTeam() != team && isNonStructure(a)){
                    a.addToDamageQueue(Gunter.this,getSpellDamage(spellData),spellData);
                }
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            setCanMove(true);
            ultActivated = false;
            ultPoint = null;
        }
        @Override
        protected void spellPassive() {

        }
    }

    private class BottleProjectile extends Projectile {

        public BottleProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(),"spell2");
            victim.addToDamageQueue(Gunter.this,getSpellDamage(spellData),spellData);
            ExtensionCommands.playSound(parentExt,room,"","sfx_gunter_bottle_shatter",this.location);
            ExtensionCommands.createWorldFX(parentExt,room,this.id,"gunter_bottle_shatter",this.id+"_bottleShatter",1000,(float)this.location.getX(),(float)this.location.getY(),false,team,0f);
            destroy();
        }
    }
}

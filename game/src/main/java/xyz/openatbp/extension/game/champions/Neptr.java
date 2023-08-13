package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class Neptr extends UserActor {
    private boolean passiveActive = false;
    private long passiveStart = 0;
    public Neptr(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void setState(ActorState state, boolean enabled) {
        super.setState(state, enabled);
        if(state == ActorState.BRUSH && enabled){
            this.passiveStart = System.currentTimeMillis();
            this.addEffect("speed",this.getStat("speed")*0.35d,3500,"nepter_passive",true);
            this.addEffect("attackSpeed",this.getStat("attackSpeed")*-0.25d,3500,null,false);
            if(this.passiveActive){
                ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
            }
            ExtensionCommands.addStatusIcon(this.parentExt,this.player,"passive","neptr_spell_4_short_description","icon_neptr_passive",3500f);
            this.passiveActive = true;
        }
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.passiveActive && System.currentTimeMillis() - this.passiveStart >= 3500){
            this.passiveActive = false;
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch(ability){
            case 1:
                this.canCast[0] = false;
                Line2D maxRangeLine = Champion.getMaxRangeLine(new Line2D.Float(this.location,dest),8f);
                this.fireProjectile(new NeptrProjectile(this.parentExt,this,maxRangeLine,8f,0.5f,this.id+"projectile_neptr_boom_meringue",true),"projectile_neptr_boom_meringue",maxRangeLine.getP2(),8f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new NeptrAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown, TimeUnit.MILLISECONDS);
                break;
            case 2:
                break;
            case 3:
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

        }

        @Override
        protected void spellE() {

        }

        @Override
        protected void spellPassive() {

        }
    }

    private class NeptrProjectile extends Projectile {

        private boolean started;
        private double damageReduction = 0d;

        public NeptrProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id, boolean started) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.started = started;
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(owner.getAvatar(),"spell1");
            victim.addToDamageQueue(owner,getSpellDamage(spellData)*(1-damageReduction),spellData);
            damageReduction+=0.15d;
        }

        @Override
        public void destroy() {
            if(!started) super.destroy();
            else{
                Point2D ownerLocation = this.owner.getLocation();
                System.out.println("Owner x: " + ownerLocation.getX() + " y: " + ownerLocation.getY());
                System.out.println("My location x: " + this.location.getX() + " y: " + this.location.getY());
                this.startingLocation = this.location;
                this.destination = ownerLocation;
                this.path = new Line2D.Float(this.location,ownerLocation);
                this.startTime = System.currentTimeMillis();
                this.estimatedDuration = (path.getP1().distance(path.getP2()) / speed)*1000f;
                System.out.println("Moving: " + this.owner.getId()+this.id);
                ExtensionCommands.moveActor(this.parentExt,this.owner.getRoom(), this.id,this.location,this.destination,this.speed,true);
                this.started = false;
                this.timeTraveled = 0f;
                this.damageReduction = 0;
            }
        }
    }
}

package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
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
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"neptr_passive",500,this.id+"_passive"+Math.random(),true,"Bip001",true,false,this.team);
            this.addEffect("speed",this.getStat("speed")*0.35d,3500,null,false);
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
    public void fireProjectile(Projectile projectile, String id, Point2D dest, float range) {
        super.fireProjectile(projectile, id, dest, range);
        ExtensionCommands.createActorFX(this.parentExt,this.room, this.id+id, "neptr_pie_trail", (int) projectile.getEstimatedDuration()+1000,this.id+id+"_fx",true,"Bip001",true,true,this.team);

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
        private Point2D previousOwnerLocation = null;
        private static final boolean MOVEMENT_DEBUG = true;
        private long movementCooldown = 0;
        private boolean intermission = false;
        private Map<Actor, Long> attackBuffer;
        private String effectId;

        public NeptrProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id, boolean started) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            if(MOVEMENT_DEBUG) ExtensionCommands.createActor(this.parentExt,this.owner.getRoom(),this.id+"_movementDebug","creep1",this.location,0f,2);
            this.started = started;
            this.attackBuffer = new HashMap<>(3);
            this.effectId = this.id+"_fx";
        }

        @Override
        protected void hit(Actor victim) {
            if(!this.attackBuffer.containsKey(victim)){
                JsonNode spellData = parentExt.getAttackData(owner.getAvatar(),"spell1");
                victim.addToDamageQueue(owner,getSpellDamage(spellData)*(1-damageReduction),spellData);
                damageReduction+=0.15d;
                this.attackBuffer.put(victim,System.currentTimeMillis());
            }else{
                if(System.currentTimeMillis() - this.attackBuffer.get(victim) >= 500){
                    JsonNode spellData = parentExt.getAttackData(owner.getAvatar(),"spell1");
                    victim.addToDamageQueue(owner,getSpellDamage(spellData)*(1-damageReduction),spellData);
                    damageReduction+=0.15d;
                    this.attackBuffer.put(victim,System.currentTimeMillis());
                }
            }
        }

        @Override
        public void update(RoomHandler roomHandler) {
            super.update(roomHandler);
            if(this.destroyed) return;
            if(MOVEMENT_DEBUG){
                System.out.println("Projectile x: " + this.location.getX() + "," + this.location.getY());
                ExtensionCommands.moveActor(this.parentExt,this.owner.getRoom(), this.id+"_movementDebug",this.location,this.location,5f,false);
            }
            if(!this.started && !this.intermission && this.owner.getLocation().distance(this.previousOwnerLocation) > 0.1f){
                if(System.currentTimeMillis() - this.movementCooldown >= 250) this.moveTowardsNeptr();
            }
        }

        private void moveTowardsNeptr(){
            System.out.println("Triggered moving!");
            this.movementCooldown = System.currentTimeMillis();
            Point2D ownerLocation = this.owner.getLocation();
            this.startingLocation = this.location;
            this.destination = ownerLocation;
            this.path = new Line2D.Float(this.location,ownerLocation);
            this.startTime = System.currentTimeMillis();
            this.estimatedDuration = (path.getP1().distance(path.getP2()) / speed)*1000f;
            this.timeTraveled = 0f;
            this.previousOwnerLocation = ownerLocation;
            ExtensionCommands.moveActor(this.parentExt,this.owner.getRoom(), this.id,this.location,this.destination,this.speed,true);
            ExtensionCommands.removeFx(this.parentExt,this.owner.getRoom(),this.effectId);
            this.effectId = this.id+"_fx"+Math.random();
            ExtensionCommands.createActorFX(this.parentExt,this.owner.getRoom(), this.id, "neptr_pie_trail", (int) this.estimatedDuration,this.effectId,true,"Bip001",true,true,owner.getTeam());
        }

        @Override
        public void destroy() {
            if(!started){
                super.destroy();
                if(MOVEMENT_DEBUG) ExtensionCommands.destroyActor(this.parentExt,this.owner.getRoom(),this.id+"_movementDebug");
            }
            else if(!this.intermission){
                this.intermission = true;
                this.damageReduction = 0;
                Runnable intermissionRunnable = () -> {this.moveTowardsNeptr(); this.intermission = false; this.started = false;};
                SmartFoxServer.getInstance().getTaskScheduler().schedule(intermissionRunnable,1000,TimeUnit.MILLISECONDS);
            }
        }
    }
}

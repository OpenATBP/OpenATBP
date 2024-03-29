package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MovementManager;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MagicMan extends UserActor {

    private double estimatedQDuration = 0;
    private long qStartTime = 0;
    private long passiveIconStarted = 0;
    private boolean passiveActivated = false;
    private Point2D wLocation = null;
    private Point2D wDest = null;
    private double wMoveTime = 0d;
    private double wSpeed = 0d;
    private boolean ultStarted;
    private boolean interruptE = false;
    private ArrayList<Actor> playersHitBySnake = new ArrayList<>(3);

    public MagicMan(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        if(this.getState(ActorState.INVISIBLE)){
            this.setState(ActorState.INVISIBLE, false);
        }
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a,new MagicManPassive(a,this.handleAttack(a)),"magicman_projectile"),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setState(ActorState state, boolean enabled) {
        if(state == ActorState.REVEALED && enabled){
            if(this.wDest == null) super.setState(state, true);
        }
        else super.setState(state, enabled);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(System.currentTimeMillis() - passiveIconStarted >= 3000 && passiveActivated){
            this.passiveActivated = false;
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
        }

        if(this.getState(ActorState.INVISIBLE) && wLocation != null){
            this.wMoveTime+=0.1d;
        }else if(!this.getState(ActorState.INVISIBLE) && wLocation != null){
            Point2D explosionPoint = MovementManager.getRelativePoint(new Line2D.Float(this.wLocation,this.wDest),this.wSpeed,this.wMoveTime);
            ExtensionCommands.moveActor(this.parentExt,this.room,this.id+"_decoy",explosionPoint,explosionPoint,5f,false);
            ExtensionCommands.destroyActor(this.parentExt,this.room,this.id+"_decoy");
            ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"magicman_eat_it",this.id+"_eatIt",2000,(float)explosionPoint.getX(),(float) explosionPoint.getY(), false,this.team,0f);
            ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_magicman_decoy",explosionPoint);
            ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_magicman_decoy2",this.location);
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell2");
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),explosionPoint,2.5f)){
                if(this.isNonStructure(a)){
                    a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                }
            }
            this.wMoveTime = 0d;
            this.wLocation = null;
            this.wDest = null;
            this.wSpeed = 0d;
            this.canCast[1] = true;
            this.setState(ActorState.REVEALED, true);
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(25000),250);
        }
        if(this.ultStarted && this.hasUltInterruptingCC()){
            this.interruptE = true;
        }
        if(System.currentTimeMillis() - this.qStartTime > this.estimatedQDuration) this.playersHitBySnake.clear();
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch(ability){
            case 1:
                if(this.getState(ActorState.INVISIBLE)){
                    this.setState(ActorState.INVISIBLE, false);
                }
                this.canCast[0] = false;
                this.stopMoving(castDelay);
                this.qStartTime = System.currentTimeMillis();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_magicman_snakes",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_magicman_snakes",this.location);
                Point2D endPoint = Champion.getAbilityLine(this.location,dest,9.5f).getP2();
                this.estimatedQDuration = ((this.location.distance(endPoint)/7f))*1000f;
                double dx = endPoint.getX()-this.location.getX();
                double dy = endPoint.getY()-this.location.getY();
                double theta = Math.atan2(dy,dx);
                double dist = this.location.distance(endPoint);
                double theta2 = theta-(Math.PI/8);
                double theta3 = theta+(Math.PI/8);
                double x = this.location.getX();
                double y = this.location.getY();
                Point2D endPoint2 = new Point2D.Double(x+(dist*Math.cos(theta2)),y+(dist*Math.sin(theta2)));
                Point2D endPoint3 = new Point2D.Double(x+(dist*Math.cos(theta3)),y+(dist*Math.sin(theta3)));
                this.fireMMProjectile(new SnakeProjectile(this.parentExt,this,new Line2D.Float(this.location,endPoint),7f,0.25f,this.id+"projectile_magicman_snake1"),this.id+"projectile_magicman_snake1","projectile_magicman_snake",this.location,endPoint,9.5f);
                this.fireMMProjectile(new SnakeProjectile(this.parentExt,this,new Line2D.Float(this.location,endPoint2),7f,0.25f,this.id+"projectile_magicman_snake2"),this.id+"projectile_magicman_snake2","projectile_magicman_snake",this.location,endPoint2,9.5f);
                this.fireMMProjectile(new SnakeProjectile(this.parentExt,this,new Line2D.Float(this.location,endPoint3),7f,0.25f,this.id+"projectile_magicman_snake3"),this.id+"projectile_magicman_snake3","projectile_magicman_snake",this.location,endPoint3,9.5f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MagicManAbilityHandler(ability,spellData,cooldown,gCooldown+1000,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 2:
                if(this.getState(ActorState.INVISIBLE)){
                    this.setState(ActorState.INVISIBLE, false);
                }
                this.canCast[1] = false;
                if(this.wLocation == null){
                    Point2D dashPoint = MovementManager.getDashPoint(this,new Line2D.Float(this.location,dest));
                    if(this.location.distance(dashPoint) < 5) dashPoint = MovementManager.getDashPoint(this,Champion.getMaxRangeLine(new Line2D.Float(this.location,dashPoint),5f));
                    if(Double.isNaN(dashPoint.getY())) dashPoint = this.location;
                    this.addState(ActorState.INVISIBLE,0d,3000,null,false);
                    this.wLocation = new Point2D.Double(this.location.getX(),this.location.getY());
                    Point2D endLocation = Champion.getDistanceLine(this.movementLine,100f).getP2();
                    if(this.location.distance(endLocation) <= 1d) endLocation = this.location;
                    this.wDest = endLocation;
                    this.wSpeed = this.getPlayerStat("speed");
                    ISFSObject data = new SFSObject();
                    data.putInt("currentHealth", (int) this.currentHealth);
                    data.putInt("maxHealth",(int) this.maxHealth);
                    data.putDouble("pHealth",this.getPHealth());
                    ExtensionCommands.updateActorData(this.parentExt,this.room,this.id+"_decoy",data);
                    ExtensionCommands.snapActor(this.parentExt,this.room,this.id,this.location,dashPoint,true);
                    Point2D ogLocation = new Point2D.Double(this.location.getX(),this.location.getY());
                    System.out.println("DashX: " + dashPoint.getX() + " DashY: " + dashPoint.getY());
                    this.setLocation(dashPoint);
                    ExtensionCommands.createActor(this.parentExt,this.room,this.id+"_decoy",this.avatar+"_decoy",ogLocation,0f,this.team);
                    ExtensionCommands.moveActor(this.parentExt,this.room,this.id+"_decoy",ogLocation,endLocation, (float) this.getPlayerStat("speed"),true);
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,1000,0);
                    Runnable secondUseDelay = () -> this.canCast[1] = true;
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(secondUseDelay,1000,TimeUnit.MILLISECONDS);
                }else{
                    this.setState(ActorState.INVISIBLE, false);
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new MagicManAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                }
                break;
            case 3:
                this.canCast[2] = false;
                this.canMove = false;
                this.ultStarted = true;
                Point2D firstLocation = new Point2D.Double(this.location.getX(),this.location.getY());
                Point2D dashPoint = this.dash(dest,true,15d);
                double dashTime = dashPoint.distance(firstLocation)/15f;
                int timeMs = (int)(dashTime*1000d);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_magicman_explode_roll",this.location);
                ExtensionCommands.actorAnimate(this.parentExt,this.room,this.id,"spell3",timeMs,true);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MagicManAbilityHandler(ability,spellData,cooldown,gCooldown,dashPoint),timeMs,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private boolean hasUltInterruptingCC(){
        ActorState[] states = {ActorState.CHARMED, ActorState.FEARED};
        for(ActorState state : states){
            if(this.getState(state)) return true;
        }
        return false;
    }

    private class MagicManAbilityHandler extends AbilityRunnable {

        public MagicManAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            attackCooldown = 0;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            canMove = true;
            ultStarted = false;
            if(!interruptE){
                ExtensionCommands.actorAnimate(parentExt,room,id,"spell3b",500,false);
                ExtensionCommands.playSound(parentExt,room,id,"sfx_magicman_explode",location);
                ExtensionCommands.playSound(parentExt,room,id,"vo/vo_magicman_explosion",location);
                ExtensionCommands.createWorldFX(parentExt,room,id,"magicman_explosion",id+"_ultExplosion",1500,(float)location.getX(),(float)location.getY(),false,team,0f);
                double damageModifier = getPlayerStat("spellDamage")*0.001d;
                for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),location,4f)){
                    if(isNonStructure(a)){
                        double damage = (double)(a.getHealth()) * (0.35d+damageModifier);
                        a.addToDamageQueue(MagicMan.this,damage,spellData);
                        a.addEffect("armor",a.getStat("armor")*-0.3d,3000,null,false);
                        a.addState(ActorState.SLOWED,0.3d,3000,null,false);
                    }
                }
            } else {
                ExtensionCommands.playSound(parentExt,room,id,"sfx_skill_interrupted",location);
            }
            interruptE = false;
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class SnakeProjectile extends Projectile {

        public SnakeProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            victim.addState(ActorState.SILENCED,0d,1000,null,false);
            ExtensionCommands.createWorldFX(this.parentExt,this.owner.getRoom(), this.id,"magicman_snake_explosion",this.id+"_explosion",500,(float)this.location.getX(),(float)this.location.getY(),false,owner.getTeam(),0f);
            if(!playersHitBySnake.contains(victim)){
                playersHitBySnake.add(victim);
                JsonNode spellData = parentExt.getAttackData(MagicMan.this.avatar,"spell1");
                victim.addToDamageQueue(MagicMan.this,getSpellDamage(spellData),spellData);
            }
            this.destroy();
        }

        @Override
        public void destroy() {
            super.destroy();
        }
    }

    private class MagicManPassive implements Runnable {

        Actor target;
        boolean crit;

        MagicManPassive(Actor target, boolean crit){
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if(crit) damage*=2;
            new Champion.DelayedAttack(parentExt,MagicMan.this,target,(int)damage,"basicAttack").run();
            if(this.target.getActorType() == ActorType.PLAYER){
                addEffect("speed",getStat("speed")*0.2d,3000,null,false);
                if(!passiveActivated) ExtensionCommands.addStatusIcon(parentExt,player,"passive","magicman_spell_4_short_description","icon_magicman_passive",0f);
                passiveActivated = true;
                passiveIconStarted = System.currentTimeMillis();

            }
        }
    }
}

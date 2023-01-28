package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UserActor extends Actor {

    protected User player;
    protected boolean canMove = true;
    private Point2D destination;
    private Point2D originalLocation;
    private float timeTraveled = 0;
    protected double attackCooldown;
    protected Point2D lastTargetLocation;
    protected Actor target;
    protected ScheduledFuture<?> currentAutoAttack = null;
    protected Map<ActorState, Boolean> states;

    public UserActor(User u, ATBPExtension parentExt){
        this.parentExt = parentExt;
        this.id = String.valueOf(u.getId());
        this.team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
        player = u;
        this.avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x,z);
        this.speed = getStat("speed");
        this.attackCooldown = getStat("attackSpeed");
        this.currentHealth = u.getVariable("stats").getSFSObjectValue().getInt("currentHealth");
        this.maxHealth = u.getVariable("stats").getSFSObjectValue().getInt("maxHealth");
        states = new HashMap<>(ActorState.values().length);
        for(ActorState s : ActorState.values()){
            states.put(s, false);
        }
        this.room = u.getLastJoinedRoom();
        this.attackSpeed = getStat("attackSpeed");
        this.attackCooldown = 500;
        this.attackRange = getStat("attackRange");
    }

    public UserActor(){
    }

    protected Point2D getRelativePoint(boolean external){ //Gets player's current location based on time
        double currentTime = -1;
        if(external) currentTime = this.timeTraveled + 0.1;
        else currentTime = this.timeTraveled;
        Point2D rPoint = new Point2D.Float();
        if(this.destination == null) this.destination = this.location;
        float x2 = (float) this.destination.getX();
        float y2 = (float) this.destination.getY();
        if(this.originalLocation == null) this.originalLocation = this.location;
        float x1 = (float) this.originalLocation.getX();
        float y1 = (float) this.originalLocation.getY();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        if(dist == 0) return this.originalLocation;
        double time = dist/speed;
        if(currentTime>time) currentTime=time;
        double currentDist = speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        this.location = rPoint;
        return rPoint;
    }

    @Override
    public Room getRoom() {
        return player.getLastJoinedRoom();
    }

    public ISFSObject getStats(){
        return player.getVariable("stats").getSFSObjectValue();
    }

    public double getStat(String stat){
        return player.getVariable("stats").getSFSObjectValue().getDouble(stat);
    }

    public ISFSObject getSFSLocation(){
        return player.getVariable("location").getSFSObjectValue();
    }

    public void setPath(Point2D start, Point2D end){
        this.originalLocation = start;
        this.destination = end;
        this.timeTraveled = 0f;
    }

    public void updateMovementTime(){
        this.timeTraveled+=0.1f;
    }

    public void cancelAuto(){
        if(this.currentAutoAttack != null){
            System.out.println("Auto canceled!");
            this.currentAutoAttack.cancel(false);
            this.currentAutoAttack = null;
        }
    }

    public User getUser(){
        return this.player;
    }
    public Point2D getOriginalLocation(){
        return this.originalLocation;
    }

    @Override
    public Point2D getLocation(){
        return this.getRelativePoint(true);
    }

    public Point2D getCurrentLocation(){return this.location;}

    @Override
    public void setLocation(Point2D location){
        this.location = location;
        this.originalLocation = location;
        this.destination = location;
        this.timeTraveled = 0f;
    }

    @Override
    public boolean damaged(Actor a, int damage) {
        ExtensionCommands.damageActor(parentExt,player,this.id,damage);
        ISFSObject stats = this.getStats();
        this.currentHealth-=damage;
        if(this.currentHealth > 0){
            for(User u : room.getUserList()){
                ISFSObject updateData = new SFSObject();
                updateData.putUtfString("id", this.id);
                updateData.putInt("currentHealth", (int) currentHealth);
                updateData.putDouble("pHealth", getPHealth());
                updateData.putInt("maxHealth", (int) maxHealth);
                stats.putInt("currentHealth", (int) currentHealth);
                stats.putDouble("pHealth", getPHealth());
                ExtensionCommands.updateActorData(parentExt,u,updateData);
            }
            return false;
        }else{
            this.die(a);
            return true;
        }
    }

    public boolean canAttack(){
        return this.attackCooldown == 0;
    }

    @Override
    public void attack(Actor a) {
        if(attackCooldown == 0){
            for(User u : room.getUserList()){
                ExtensionCommands.attackActor(parentExt,u,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),false,true);
            }
            attackCooldown = attackSpeed;
        }
    }

    public void reduceAttackCooldown(){
        this.attackCooldown-=100;
    }

    public void resetAttack(){
        this.attackCooldown = 600;
    }

    @Override
    public void die(Actor a) {
        this.setHealth(0);
        ExtensionCommands.knockOutActor(parentExt,player, String.valueOf(player.getId()),a.getId(),10);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.RespawnCharacter(parentExt,player.getLastJoinedRoom(),String.valueOf(player.getId())),10, TimeUnit.SECONDS);
    }

    public void useAbility(int ability, ISFSObject abilityData){System.out.println("No character selected!");}
    public boolean canMove(){
        return canMove;
    }
    public void setCanMove(boolean canMove){
        this.canMove = canMove;
    }

    public void setState(ActorState state, boolean stateBool){
        states.put(state,stateBool);
        ExtensionCommands.updateActorState(parentExt,player,id,state,stateBool);
    }

    public void setState(ActorState[] states, boolean stateBool){
        for(ActorState s : states){
            this.states.put(s,stateBool);
            ExtensionCommands.updateActorState(parentExt,player,id,s,stateBool);
        }
    }

    public boolean isState(ActorState state){
        return this.states.get(state);
    }

    public boolean canUseAbility(){
        ActorState[] hinderingStates = {ActorState.POLYMORPH, ActorState.AIRBORNE, ActorState.CHARMED, ActorState.FEARED, ActorState.SILENCED, ActorState.STUNNED};
        for(ActorState s : hinderingStates){
            if(this.states.get(s)) return false;
        }
        return true;
    }

    public Line2D getMovementLine(){
        if(this.originalLocation != null && this.destination != null)  return new Line2D.Double(this.originalLocation,this.destination);
        else return new Line2D.Double(this.location,this.location);
    }

    public void setHealth(double health){
        this.currentHealth = health;
        ISFSObject stats = this.getStats();
        if(currentHealth>maxHealth) currentHealth = maxHealth;
        else if(currentHealth<0) currentHealth = 0;
        double pHealth = this.getPHealth();
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("maxHealth",(int)maxHealth);
        updateData.putInt("currentHealth",(int)currentHealth);
        updateData.putDouble("pHealth",pHealth);
        ExtensionCommands.updateActorData(parentExt,player,updateData);
        stats.putInt("currentHealth",(int)currentHealth);
        stats.putDouble("pHealth",pHealth);
    }

    public void stopMoving(int delay){
        this.stopMoving();
        this.canMove = false;
        if(delay > 0){
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),delay,TimeUnit.MILLISECONDS);
        }
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        MovementStopper(boolean move){
            this.move = move;
        }
        @Override
        public void run() {
            canMove = this.move;
        }
    }

    protected class RangedAttack implements Runnable {

        Actor target;
        Runnable attackRunnable;
        String projectile;

        RangedAttack(Actor target, Runnable attackRunnable, String projectile){
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
        }

        @Override
        public void run() {
            System.out.println("Running projectile!");
            for(User u : room.getUserList()){
                ExtensionCommands.createProjectileFX(parentExt,u,projectile,id,target.getId(),"Bip001","Bip001",0.5f);
            }
            SmartFoxServer.getInstance().getTaskScheduler().schedule(attackRunnable,500,TimeUnit.MILLISECONDS);
            currentAutoAttack = null;
        }
    }
}

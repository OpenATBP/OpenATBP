package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.reqhandlers.HitActorHandler;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UserActor extends Actor {

    protected User player;
    protected boolean canMove = true;
    protected Point2D destination;
    private Point2D originalLocation;
    private float timeTraveled = 0;
    protected double attackCooldown;
    protected Point2D lastTargetLocation;
    protected Actor target;
    protected ScheduledFuture<?> currentAutoAttack = null;
    protected boolean autoAttackEnabled = false;
    protected int xp = 0;

    //TODO: Add all stats into UserActor object instead of User Variables
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
        this.originalLocation = location;
        this.destination = location;
        this.speed = getStat("speed");
        this.attackCooldown = getStat("attackSpeed");
        this.currentHealth = u.getVariable("stats").getSFSObjectValue().getInt("currentHealth");
        this.maxHealth = u.getVariable("stats").getSFSObjectValue().getInt("maxHealth");
        this.room = u.getLastJoinedRoom();
        this.attackSpeed = getStat("attackSpeed");
        this.attackCooldown = 500;
        this.attackRange = getStat("attackRange");
        this.actorType = ActorType.PLAYER;
    }

    public UserActor(){
    }

    public void setAutoAttackEnabled(boolean enabled){
        this.autoAttackEnabled = enabled;
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

    public void setPath(Line2D path){
        this.originalLocation = path.getP1();
        this.destination = path.getP2();
        this.timeTraveled = 0f;
    }

    public void updateMovementTime(){
        this.timeTraveled+=0.1f;
    }

    public void cancelAuto(){
        this.target = null;
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

    public void autoAttack(Actor a){
        Point2D location = this.location;
        ExtensionCommands.moveActor(parentExt,this.player,this.id,location,location,3.75f,false);
        this.setLocation(location);
        this.setCanMove(false);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new HitActorHandler.MovementStopper(this),250,TimeUnit.MILLISECONDS);
        this.attack(a);
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

    @Override
    public void update(int msRan) {
        float x = (float) this.getOriginalLocation().getX();
        float z = (float) this.getOriginalLocation().getY();
        Point2D currentPoint = this.getLocation();
        if(currentPoint.getX() != x && currentPoint.getY() != z){
            this.updateMovementTime();
        }
        if(!this.canAttack()) this.reduceAttackCooldown();
        if(this.target != null && this.autoAttackEnabled){
            if(this.withinRange(target) && this.canAttack()){
                this.autoAttack(target);
                System.out.println("Auto attacking!");
            }else if(!this.withinRange(target)){
                int attackRange = parentExt.getActorStats(this.avatar).get("attackRange").asInt();
                Line2D movementLine = new Line2D.Float(currentPoint,target.getLocation());
                float targetDistance = (float)target.getLocation().distance(currentPoint)-attackRange;
                Line2D newPath = Champion.getDistanceLine(movementLine,targetDistance);
                if(newPath.getP2().distance(this.destination) > 0.1f){
                    System.out.println("Distance: " + newPath.getP2().distance(this.destination));
                    this.setPath(newPath);
                    ExtensionCommands.moveActor(parentExt,this.player, this.id,currentPoint, movementLine.getP2(), (float) this.speed,true);
                }
            }
        }else{
            if(this.target != null) System.out.println("Target: " + this.target.getId());
        }
        if(msRan % 1000 == 0){
            if(this.isState(ActorState.POLYMORPH)){
                for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,2)){
                    if(a.getTeam() != this.team){
                        System.out.println("Damaging: " + a.getAvatar());
                        a.damaged(this,50);
                    }
                }
            }
        }
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

    public void setTarget(Actor a){
        this.target = a;
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

    public void giveStatBuff(String stat, double value, int duration){
        ISFSObject stats = this.getStats();
        double currentStat = stats.getDouble(stat);
        stats.putDouble(stat,currentStat+value);
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id",this.id);
        updateData.putDouble(stat,currentStat+value);
        ExtensionCommands.updateActorData(parentExt,player,updateData);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new StatChanger(stat,value),duration,TimeUnit.MILLISECONDS);
    }

    public void addXP(int xp){
        if(this.level != 10){
            this.xp+=xp; //TODO: Backpack modifiers
            System.out.println("Current xp for " + this.id + ":" + this.xp);
            ExtensionCommands.updateActorData(this.parentExt,this.player,ChampionData.addXP(this,xp,this.parentExt));
            if(ChampionData.getXPLevel(this.xp) != this.level){
                this.level++;
            }
        }
    }

    public int getLevel(){
        return this.level;
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

    protected class StatChanger implements Runnable {
        double value;
        String stat;

        StatChanger(String stat, double value){
            this.value = value;
            this.stat = stat;
        }
        @Override
        public void run() {
            ISFSObject stats = getStats();
            double currentStat = stats.getDouble(stat);
            stats.putDouble(stat,currentStat-value);
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id",id);
            updateData.putDouble(stat,currentStat-value);
            ExtensionCommands.updateActorData(parentExt,player,updateData);
        }
    }
}

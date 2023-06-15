package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.reqhandlers.HitActorHandler;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UserActor extends Actor {

    protected User player;
    protected boolean canMove = true;
    protected Point2D destination;
    private Point2D originalLocation;
    private float timeTraveled = 0;
    protected double attackCooldown;
    protected Actor target;
    protected ScheduledFuture<?> currentAutoAttack = null;
    protected boolean autoAttackEnabled = false;
    protected int xp = 0;
    private int deathTime = 10;
    private boolean dead = false;
    protected Map<Actor,ISFSObject> aggressors = new HashMap<>();
    protected Map<String, Object> stats;

    //TODO: Add all stats into UserActor object instead of User Variables
    public UserActor(User u, ATBPExtension parentExt){
        this.parentExt = parentExt;
        this.id = String.valueOf(u.getId());
        this.team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
        player = u;
        this.avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        this.displayName = u.getVariable("player").getSFSObjectValue().getUtfString("name");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x,z);
        this.originalLocation = location;
        this.destination = location;
        this.stats = this.initializeStats();
        this.speed = (double)this.stats.get("speed");
        this.attackCooldown = (double)this.stats.get("attackSpeed");
        this.currentHealth = (double)this.stats.get("health");
        this.maxHealth = this.currentHealth;
        this.room = u.getLastJoinedRoom();
        this.attackSpeed = this.attackCooldown;
        this.attackRange = (double)this.stats.get("attackRange");
        this.actorType = ActorType.PLAYER;
    }

    public UserActor() {

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
    @Deprecated
    public boolean damaged(Actor a, int damage){
        System.out.println("Wrong damaged function being used!");
        return false;
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(this.dead) return true;
        ExtensionCommands.damageActor(parentExt,player,this.id,damage);
        this.processHitData(a,attackData,damage);
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
            this.dead = true;
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
            attackCooldown = (double) this.stats.get("attackSpeed");
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

    @Override
    public void die(Actor a) { //TODO: Last left off - handing death needs to be properly implemented
        this.setHealth(0);
        ExtensionCommands.knockOutActor(parentExt,player, String.valueOf(player.getId()),a.getId(),this.deathTime);
        try{
            ExtensionCommands.handleDeathRecap(parentExt,player,this.id,a.getId(), (HashMap<Actor, ISFSObject>) this.aggressors);
            this.increaseStat("deaths",1);
            if(a.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) a;
                ua.increaseStat("kills",1);
            }
            Set<String> assistIds = new HashSet<>(2);
            for(Actor actor : this.aggressors.keySet()){
                if(actor.getActorType() == ActorType.PLAYER && !actor.getId().equalsIgnoreCase(a.getId())){
                    UserActor ua = (UserActor) actor;
                    ua.increaseStat("assists",1);
                    assistIds.add(ua.getId());
                }
            }
            this.parentExt.getRoomHandler(this.room.getId()).handleAssistXP(a,assistIds,50);
        }catch(Exception e){
            e.printStackTrace();
        }
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.RespawnCharacter(this),this.deathTime, TimeUnit.SECONDS);
    }
/*
    Acceptable Keys:
    availableSpellPoints: Integer
    sp_category1
    sp_category2
    sp_category3
    sp_category4
    sp_category5
    kills
    deaths
    assists
    attackDamage
    attackSpeed
    armor
    speed
    spellResist
    spellDamage
    criticalChance
    criticalDamage*
    lifeSteal
    armorPenetration
    coolDownReduction
    spellVamp
    spellPenetration
    attackRange
    healthRegen
 */
    public void updateStat(String key, Object value){
        this.stats.put(key,value);
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,key,this.stats.get(key));
    }

    public void increaseStat(String key, int num){
        if(this.stats.get(key).getClass() == Integer.class) this.stats.put(key,(int)this.stats.get(key)+num);
        else if(this.stats.get(key).getClass() == Double.class) this.stats.put(key,(double)this.stats.get(key)+num);
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,key,this.stats.get(key));
    }

    @Override
    public void update(int msRan) {
        if(this.dead) return;
        float x = (float) this.getOriginalLocation().getX();
        float z = (float) this.getOriginalLocation().getY();
        Point2D currentPoint = this.getLocation();
        if(currentPoint.getX() != x && currentPoint.getY() != z){
            this.updateMovementTime();
        }
        if(!this.canAttack()) this.reduceAttackCooldown();
        if(this.target != null && this.target.getHealth() > 0 && this.autoAttackEnabled){
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
            if(this.target != null){
                System.out.println("Target: " + this.target.getId());
                if(this.target.getHealth() <= 0) this.target = null;
            }
        }
        if(msRan % 1000 == 0){
            int newDeath = 10+((msRan/1000)/60);
            if(newDeath != this.deathTime) this.deathTime = newDeath;
            if(this.isState(ActorState.POLYMORPH)){
                for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,2)){
                    if(a.getTeam() != this.team){
                        System.out.println("Damaging: " + a.getAvatar());
                        a.damaged(this,50);
                    }
                }
            }
            for(Actor a : this.aggressors.keySet()){
                ISFSObject damageData = this.aggressors.get(a);
                if(System.currentTimeMillis() > damageData.getLong("lastAttacked") + 10000) this.aggressors.remove(a);
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

    public void respawn(){
        Point2D respawnPoint = new Point2D.Float(0,0);
        if(this.team == 0) respawnPoint = MapData.PURPLE_SPAWNS[(int) (Math.random()*MapData.PURPLE_SPAWNS.length)];
        this.location = respawnPoint;
        this.originalLocation = respawnPoint;
        this.destination = respawnPoint;
        this.timeTraveled = 0f;
        this.setHealth(this.maxHealth);
        this.dead = false;
        for(User u : this.room.getUserList()){
            ExtensionCommands.snapActor(this.parentExt,u,this.id,this.location,respawnPoint,false);
            ExtensionCommands.respawnActor(this.parentExt,u,this.id);
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

    private void processHitData(Actor a, JsonNode attackData, int damage){
        String precursor = "attack";
        if(attackData.has("spellName")) precursor = "spell";
        if(this.aggressors.containsKey(a)){
            this.aggressors.get(a).putLong("lastAttacked",System.currentTimeMillis());
            ISFSObject currentAttackData = this.aggressors.get(a);
            int tries = 0;
            for(String k : currentAttackData.getKeys()){
                if(k.contains("attack")){
                    ISFSObject attack0 = currentAttackData.getSFSObject(k);
                    if(attackData.get(precursor+"Name").asText().equalsIgnoreCase(attack0.getUtfString("atkName"))){
                        attack0.putInt("atkDamage",attack0.getInt("atkDamage")+damage);
                        this.aggressors.get(a).putSFSObject(k,attack0);
                        return;
                    }else tries++;
                }
            }
            String attackNumber = "";
            if(tries == 0) attackNumber = "attack1";
            else if(tries == 1) attackNumber = "attack2";
            else if(tries == 2) attackNumber = "attack3";
            else{
                System.out.println("Fourth attack detected!");
            }
            ISFSObject attack1 = new SFSObject();
            attack1.putUtfString("atkName",attackData.get(precursor+"Name").asText());
            attack1.putInt("atkDamage",damage);
            String attackType = "physical";
            if(precursor.equalsIgnoreCase("spell")) attackType = "spell";
            attack1.putUtfString("atkType",attackType);
            attack1.putUtfString("atkIcon",attackData.get(precursor+"IconImage").asText());
            this.aggressors.get(a).putSFSObject(attackNumber,attack1);
        }else{
            ISFSObject playerData = new SFSObject();
            playerData.putLong("lastAttacked",System.currentTimeMillis());
            ISFSObject attackObj = new SFSObject();
            attackObj.putUtfString("atkName",attackData.get(precursor+"Name").asText());
            attackObj.putInt("atkDamage",damage);
            String attackType = "physical";
            if(precursor.equalsIgnoreCase("spell")) attackType = "spell";
            attackObj.putUtfString("atkType",attackType);
            attackObj.putUtfString("atkIcon",attackData.get(precursor+"IconImage").asText());
            playerData.putSFSObject("attack1",attackObj);
            this.aggressors.put(a,playerData);
        }
    }

    private HashMap<String, Object> initializeStats(){
        HashMap<String, Object> stats = new HashMap<>();
        stats.put("availableSpellPoints",1);
        for(int i = 1; i < 6; i++){
            stats.put("sp_category"+i,0);
        }
        stats.put("kills",0);
        stats.put("deaths",0);
        stats.put("assists",0);
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar);
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k,actorStats.get(k).asDouble());
        }
        return stats;
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

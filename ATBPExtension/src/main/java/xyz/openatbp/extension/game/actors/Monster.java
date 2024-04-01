package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Monster extends Actor {

    enum AggroState{PASSIVE, ATTACKED}
    enum MonsterType{SMALL,BIG}

    private AggroState state = AggroState.PASSIVE;
    private final Point2D startingLocation;
    private final MonsterType type;
    protected boolean dead = false;
    private static final boolean MOVEMENT_DEBUG = false;
    private boolean attackRangeOverride = false;
    private boolean headingBack = false;

    public Monster(ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName){
        this.startingLocation = new Point2D.Float(startingLocation[0],startingLocation[1]);
        this.type = MonsterType.BIG;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.stats = this.initializeStats();
        this.id = monsterName;
        this.maxHealth = this.stats.get("health");
        this.currentHealth = this.maxHealth;
        this.actorType = ActorType.MONSTER;
        this.displayName = parentExt.getDisplayName(monsterName);
        this.xpWorth = this.parentExt.getActorXP(this.id);
        if(MOVEMENT_DEBUG) ExtensionCommands.createActor(parentExt,room,id+"_test","creep",this.location,0f,2);
    }

    public Monster(ATBPExtension parentExt, Room room, Point2D startingLocation, String monsterName){
        this.startingLocation = startingLocation;
        this.type = MonsterType.SMALL;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.stats = this.initializeStats();
        this.id = monsterName;
        this.maxHealth = this.stats.get("health");
        this.currentHealth = this.maxHealth;
        this.actorType = ActorType.MONSTER;
        this.xpWorth = this.parentExt.getActorXP(this.id);
        this.displayName = parentExt.getDisplayName(monsterName);
    }
    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) { //Runs when taking damage
        try{
            if(this.dead) return true; //Prevents bugs that take place when taking damage (somehow from FP passive) after dying
            if(Champion.getUserActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,10f).isEmpty()) return false;
            if(a.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) a;
                if(ua.hasBackpackItem("junk_1_demon_blood_sword") && ua.getStat("sp_category1") > 0) damage*=1.3;
            }
            AttackType attackType = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage,attackType,a);
            if(a.getActorType() == ActorType.PLAYER) this.addDamageGameStat((UserActor) a,newDamage,attackType);
            boolean returnVal = super.damaged(a,newDamage,attackData);
            if(!this.headingBack && Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,10f).contains(a)){ //If being attacked while minding own business, it will target player
                state = AggroState.ATTACKED;
                target = findClosestAttacker();
                this.timeTraveled = 0f;
                this.moveTowardsActor();
                if(target != null && target.getActorType() == ActorType.PLAYER) ExtensionCommands.setTarget(parentExt,((UserActor)target).getUser(),this.id, target.getId());
                if(this.type == MonsterType.SMALL){ //Gets all mini monsters like gnomes and owls to all target player when one is hit
                    for(Monster m : parentExt.getRoomHandler(this.room.getId()).getCampMonsters(this.id)){
                        m.setAggroState(AggroState.ATTACKED,a);
                    }
                }
            }
            return returnVal;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }

    }
    
    public Actor findClosestAttacker(){
        List<Actor> actorsInCamp = Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),startingLocation,10f);
        HashMap<Actor, Double> actorLocations = new HashMap<>();
        for(Actor a : actorsInCamp){
            if(!Objects.equals(a.getId(), this.getId()) && a.getActorType() != ActorType.MONSTER && a.getActorType() != ActorType.MINION){
                double distance = a.getLocation().distance(this.location);
                actorLocations.put(a,distance);
            }
        }
        Actor clostestActor = null;
        double minDistance = Double.MAX_VALUE;
        for(Map.Entry<Actor, Double> entry : actorLocations.entrySet()){
            Actor actor = entry.getKey();
            double distance = entry.getValue();
            if(distance < minDistance){
                minDistance = distance;
                clostestActor = actor;
            }
        }
        return clostestActor;
    }

    public void setAggroState(AggroState state, Actor a){
        if(this.state == AggroState.ATTACKED && state == AggroState.PASSIVE){
            double closestDistance = 1000;
            UserActor closestPlayer = null;
            for(UserActor ua : parentExt.getRoomHandler(this.room.getId()).getPlayers()){
                if(ua.getLocation().distance(this.location) < closestDistance){
                    closestPlayer = ua;
                    closestDistance = ua.getLocation().distance(this.location);
                }
            }
            if(closestDistance <= 10){
                this.target = closestPlayer;
            }
        }else{
            this.state = state;
            if(state == AggroState.ATTACKED) this.target = a;
        }

    }
    @Override
    public boolean setTempStat(String stat, double delta) {
        boolean returnVal = super.setTempStat(stat,delta);
        if(stat.equalsIgnoreCase("speed")){
            if(movementLine != null){
                this.move(movementLine.getP2());
            }
        }
        return returnVal;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {

    }

    @Override
    public void knockback(Point2D source){
        super.knockback(source);
        this.attackRangeOverride = true;
    }

    @Override
    public void pulled(Point2D source){
        super.pulled(source);
        this.attackRangeOverride = true;
    }

    @Override
    public void attack(Actor a) { //TODO: Almost identical to minions - maybe move to Actor class?
        //Called when it is attacking a player
        this.stopMoving();
        this.canMove = false;
        if(this.attackCooldown == 0){
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            int attackDamage = (int) this.getPlayerStat("attackDamage");
            ExtensionCommands.attackActor(parentExt,this.room,this.id,a.getId(),(float) a.getLocation().getX(), (float) a.getLocation().getY(), false, true);
            if(this.getId().contains("gnome")) SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedRangedAttack(this,a),300,TimeUnit.MILLISECONDS);
            else SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,attackDamage,"basicAttack"),500, TimeUnit.MILLISECONDS); //Melee damage
        }
    }

    public void rangedAttack(Actor a){ //Called when ranged attacks take place to spawn projectile and deal damage after projectile hits
        String fxId = "gnome_projectile";
        int attackDamage = (int) this.getPlayerStat("attackDamage");
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(this.parentExt,this.room,fxId,this.id,a.getId(),"Bip001","Bip001",time);

        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,attackDamage,"basicAttack"),(int)(time*1000), TimeUnit.MILLISECONDS);
    }

    @Override
    public void setTarget(Actor a) {
        if(this.state == AggroState.PASSIVE) this.setAggroState(AggroState.ATTACKED,a);
        this.target = a;
        this.movementLine = new Line2D.Float(this.location,a.getLocation());
        this.timeTraveled = 0f;
        this.moveTowardsActor();
    }

    @Override
    public void die(Actor a) { //Called when monster dies
        Console.debugLog(this.id + " has died! " + this.dead);
        if(!this.dead){ //No double deaths
            this.dead = true;
            this.stopMoving();
            this.currentHealth = -1;
            RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
            int scoreValue = parentExt.getActorStats(this.id).get("valueScore").asInt();
            if(a.getActorType() == ActorType.PLAYER || a.getActorType() == ActorType.COMPANION){ //Adds score + party xp when killed by player
                UserActor ua = null;
                if(a.getActorType() == ActorType.COMPANION){
                    int team = 0;
                    if(a.getTeam() == 0) team = 1;
                    if(a.getId().contains("skully")) ua = this.parentExt.getRoomHandler(this.room.getId()).getEnemyChampion(team, "lich");
                    else if(a.getId().contains("turret")) ua = this.parentExt.getRoomHandler(this.room.getId()).getEnemyChampion(team, "princessbubblegum");
                    else if(a.getId().contains("mine")) ua = this.parentExt.getRoomHandler(this.room.getId()).getEnemyChampion(team, "neptr");
                }else ua = (UserActor) a;
                if(ua != null){
                    ua.addGameStat("jungleMobs",1);
                    if(ua.hasBackpackItem("junk_1_magic_nail") && ua.getStat("sp_category1") > 0) ua.addNailStacks(2);
                    roomHandler.addScore(ua,a.getTeam(),scoreValue);
                    //roomHandler.handleXPShare(ua,this.parentExt.getActorXP(this.id));
                    ExtensionCommands.knockOutActor(parentExt,this.room,this.id,ua.getId(),45);
                    ExtensionCommands.playSound(this.parentExt,ua.getUser(),ua.getId(),"sfx_gems_get",this.location);
                }
            }else{
                ExtensionCommands.knockOutActor(parentExt,this.room,this.id,a.getId(),45);
            }
            ExtensionCommands.destroyActor(parentExt,this.room, this.id);
            roomHandler.handleSpawnDeath(this);
        }
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        this.handleActiveEffects();
        if(this.dead) return;
        if(this.target != null && this.target.getState(ActorState.INVISIBLE)){
            this.state = AggroState.PASSIVE; //
            this.moveWithCollision(this.startingLocation);
            this.target = null;
        }
        if(this.headingBack && this.location.equals(startingLocation)){
            this.headingBack = false;
        }
        if(msRan % 1000*60 == 0){ //Every second it checks average player level and scales accordingly
            int averagePLevel = parentExt.getRoomHandler(this.room.getId()).getAveragePlayerLevel();
            if(averagePLevel != level){
                int levelDiff = averagePLevel-level;
                this.maxHealth+= parentExt.getHealthScaling(this.id)*levelDiff;
                this.level = averagePLevel;
                Champion.updateServerHealth(this.parentExt,this);
            }
        }if(this.movementLine != null) this.location = this.getRelativePoint(); //Sets location variable to its actual location using *math*
        else this.movementLine = new Line2D.Float(this.location,this.location);
        if(this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01d) this.timeTraveled+=0.1f;
        this.handlePathing();
        if(this.target != null && this.target.getHealth() <= 0) this.setAggroState(AggroState.PASSIVE,null);
        if(MOVEMENT_DEBUG && this.type == MonsterType.BIG) ExtensionCommands.moveActor(parentExt,room,this.id+"_test",this.location,this.location,5f,false);
        if(this.state == AggroState.PASSIVE){
            if(this.currentHealth < this.maxHealth){
                int value = (int)(this.getMaxHealth()*0.006);
                this.changeHealth(value);
            }
            if(this.isStopped() && this.location.distance(this.startingLocation) > 0.1d){
                this.move(this.startingLocation);
            }
        }else{ //Monster is pissed!!
            if((this.location.distance(this.startingLocation) >= 10 && !this.attackRangeOverride) || this.target != null && this.target.getHealth() <= 0){
                this.state = AggroState.PASSIVE; //Far from camp, heading back
                this.moveWithCollision(this.startingLocation);
                this.target = null;
                this.headingBack = true;
            }
            else if(this.target != null){ //Chasing player
                if(this.attackRangeOverride) this.attackRangeOverride = false;
                if(this.withinRange(this.target) && this.canAttack()){
                    this.attack(this.target);
                }else if(!this.withinRange(this.target) && this.canMove()){
                    this.moveTowardsActor();
                }else if(this.withinRange(this.target) && !this.states.get(ActorState.FEARED) && !this.states.get(ActorState.CHARMED)){
                    if(this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f) this.stopMoving();
                }
            }
        }
        if(this.attackCooldown > 0) this.reduceAttackCooldown();
    }

    @Override
    public boolean withinRange(Actor a){
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        if(this.movementLine == null) return this.location;
        if(this.timeTraveled == 0 && this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f) this.timeTraveled+=0.1f; //Prevents any stagnation in movement
        Point2D rPoint = new Point2D.Float();
        Point2D destination = movementLine.getP2();
        float x2 = (float) destination.getX();
        float y2 = (float) destination.getY();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/(float)this.getPlayerStat("speed");
        double currentTime = this.timeTraveled;
        if(currentTime>time) currentTime=time;
        double currentDist = (float)this.getPlayerStat("speed")*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        if(dist != 0) return rPoint;
        else return movementLine.getP1();
    }

    public void moveTowardsActor(){ //Moves towards a specific point. TODO: Have the path stop based on collision radius
        if(!this.canMove()) return;
        if(this.states.get(ActorState.FEARED)) return;
        if(this.movementLine == null){
            this.moveWithCollision(this.target.getLocation());
        }
        if(this.path == null){
            if(this.target != null && this.movementLine.getP2().distance(this.target.getLocation()) > 0.1f){
                this.moveWithCollision(this.target.getLocation());
            }
        }else{
            if(this.target != null && this.path.size() - this.pathIndex < 3 && this.path.get(this.path.size()-1).distance(this.target.getLocation()) > 0.1f){
                this.moveWithCollision(this.target.getLocation());
            }
        }
    }

    @Override
    public void moveWithCollision(Point2D dest){
        List<Point2D> path = new ArrayList<>();
        try {
            path = MovementManager.getPath(this.parentExt,this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap(),this.location,dest);
        }catch(Exception e) {
            Console.logWarning(this.id + " could not form a path.");
        }
        if(path.size() > 2){
            this.setPath(path);
        }else{
            Line2D testLine = new Line2D.Float(this.location,dest);
            Point2D newPoint = MovementManager.getPathIntersectionPoint(this.parentExt,this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap(),testLine);
            if(newPoint != null){
                this.move(newPoint);
            }else this.move(dest);
        }

    }
}

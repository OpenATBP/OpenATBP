package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class Monster extends Actor{

    enum AggroState{PASSIVE, ATTACKED};
    enum MonsterType{SMALL,BIG};

    private AggroState state = AggroState.PASSIVE;
    private final Point2D startingLocation;
    private UserActor target;
    private float travelTime;
    private final MonsterType type;
    private Line2D movementLine;
    private boolean dead = false;

    public Monster(ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName){ //TODO: Make all startingLocation(s) uniform
        this.startingLocation = new Point2D.Float(startingLocation[0],startingLocation[1]);
        this.type = MonsterType.BIG;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.id = monsterName;
        this.maxHealth = parentExt.getActorStats(monsterName).get("health").asInt();
        this.speed = parentExt.getActorStats(monsterName).get("speed").asDouble();
        this.currentHealth = this.maxHealth;
        this.attackRange = this.getStat("attackRange");
        this.actorType = ActorType.MONSTER;
        this.displayName = parentExt.getDisplayName(monsterName);
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
        this.id = monsterName;
        this.maxHealth = parentExt.getActorStats(monsterName).get("health").asInt();
        this.speed = parentExt.getActorStats(monsterName).get("speed").asDouble();
        this.currentHealth = this.maxHealth;
        this.attackRange = this.getStat("attackRange");
        this.actorType = ActorType.MONSTER;
        this.displayName = parentExt.getDisplayName(monsterName);
    }
    @Override
    public boolean damaged(Actor a, int damage) { //Runs when taking damage TODO: Implement armor
        if(this.dead) return true; //Prevents bugs that take place when taking damage (somehow from FP passive) after dying
        if(this.location.distance(this.startingLocation) > 0.01f && this.state == AggroState.PASSIVE){ //Prevents damage when walking back from being de-aggro
            if(a.getActorType() == ActorType.PLAYER){ //Plays attack-miss sound when trying to damage invulnerable monster
                UserActor player = (UserActor) a;
                ExtensionCommands.playSound(parentExt,player.getUser(),"sfx_attack_miss",this.location);
            }
            return false;
        }

        boolean returnVal = super.damaged(a,damage);
        if(this.state == AggroState.PASSIVE && this.target == null){ //If being attacked while minding own business, it will target player
            state = AggroState.ATTACKED;
            target = (UserActor) a;
            this.travelTime = 0f;
            this.moveTowardsActor(this.target.getLocation());
            ExtensionCommands.setTarget(parentExt,target.getUser(),this.id, target.getId());
            if(this.type == MonsterType.SMALL){ //Gets all mini monsters like gnomes and owls to all target player when one is hit
                for(Monster m : parentExt.getRoomHandler(this.room.getId()).getCampMonsters(this.id)){
                    m.setAggroState(AggroState.ATTACKED,a);
                }
            }
        }
        if(returnVal) this.die(a);
        return returnVal;
    }

    public void setAggroState(AggroState state, Actor a){
        this.state = state;
        if(state == AggroState.ATTACKED) this.target = (UserActor) a;
    }

    @Override
    public void attack(Actor a) { //TODO: Almost identical to minions - maybe move to Actor class?
        //Called when it is attacking a player
        this.stopMoving();
        this.canMove = false;
        if(this.attackCooldown == 0){
            this.attackCooldown = this.getStat("attackSpeed");
            int attackDamage = (int) this.getStat("attackDamage");
            for(User u : room.getUserList()){ //TODO: Add crit chance
                ExtensionCommands.attackActor(parentExt,u,this.id,a.getId(),(float) a.getLocation().getX(), (float) a.getLocation().getY(), false, true);
            }
            if(this.type == MonsterType.SMALL) SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedRangedAttack(this,a),300,TimeUnit.MILLISECONDS); // Small camps are ranged
            else SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,attackDamage,"basicAttack"),500, TimeUnit.MILLISECONDS); //Melee damage
        }else if(attackCooldown == 300){ //Deprecated (changed from internal delay of attacks to using task schedulers)
            reduceAttackCooldown();
        }else if(attackCooldown == 100 || attackCooldown == 200) reduceAttackCooldown(); //Deprecated (see above) im scared to remove for now
    }

    public void rangedAttack(Actor a){ //Called when ranged attacks take place to spawn projectile and deal damage after projectile hits
        String fxId = "gnome_projectile";
        int attackDamage = (int) this.getStat("attackDamage");
        for(User u : room.getUserList()){
            ExtensionCommands.createProjectileFX(this.parentExt,u,fxId,this.id,a.getId(),"Bip001","Bip001",0.5f);
        }
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,attackDamage,"basicAttack"),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) { //Called when monster dies
        if(!this.dead){ //No double deaths
            this.dead = true;
            this.currentHealth = -1;
            RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
            int scoreValue = parentExt.getActorStats(this.id).get("valueScore").asInt();
            for(User u : room.getUserList()){ //Handles death animation
                ExtensionCommands.knockOutActor(parentExt,u,this.id,a.getId(),45);
                ExtensionCommands.destroyActor(parentExt,u,this.id);
            }
            if(a.getActorType() == ActorType.PLAYER){ //Adds score + party xp when killed by player
                roomHandler.addScore(a.getTeam(),scoreValue);
                roomHandler.handleXPShare((UserActor)a,this.parentExt.getActorXP(this.id));
            }
            roomHandler.handleSpawnDeath(this);
        }
    }

    @Override
    public void update(int msRan) {
        if(msRan % 1000*60 == 0){ //Every second it checks average player level and scales accordingly
            int averagePLevel = parentExt.getRoomHandler(this.room.getId()).getAveragePlayerLevel();
            if(averagePLevel != level){
                int levelDiff = averagePLevel-level;
                this.maxHealth+= parentExt.getHealthScaling(this.id)*levelDiff;
                this.level = averagePLevel;
                Champion.updateServerHealth(this.parentExt,this);
            }
        }
        if(this.movementLine != null) this.location = this.getRelativePoint(); //Sets location variable to its actual location using *math*
        else this.movementLine = new Line2D.Float(this.location,this.location);
        if(this.state == AggroState.PASSIVE){
            if(this.currentHealth < this.maxHealth){
                this.changeHealth(25);
            }
            if(this.location.distance(this.startingLocation) >= 0.01){ //Still walking from being de-agro'd
                travelTime+=0.1f;
            }else{ //Standing still at camp
                travelTime = 0f;
                this.movementLine = null;
            }
        }else{ //Monster is pissed!!
            if(this.location.distance(this.startingLocation) >= 10 || this.target.getHealth() <= 0){
                this.state = AggroState.PASSIVE; //Far from camp, heading back
                this.move(this.startingLocation);
                this.target = null;
            }
            else if(this.target != null){ //Chasing player
                if(this.withinRange(this.target) && this.canAttack()){
                    this.attack(this.target);
                }else if(!this.withinRange(this.target) && this.canMove){
                    this.travelTime+=0.1f;
                    this.moveTowardsActor(this.target.getLocation());
                }else if(this.withinRange(this.target)){
                    if(this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f) this.stopMoving();
                }
            }
        }
        if(!this.canAttack()) reduceAttackCooldown();
    }

    @Override
    public boolean withinRange(Actor a){
        return a.getLocation().distance(this.location) <= this.attackRange;
    }
    @Override
    public void stopMoving(){
        super.stopMoving();
        this.movementLine = new Line2D.Float(this.location,this.location);
        this.travelTime = 0f;
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        if(this.travelTime == 0 && this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f) this.travelTime+=0.1f; //Prevents any stagnation in movement
        Point2D rPoint = new Point2D.Float();
        Point2D destination;
        if(this.state == AggroState.PASSIVE) destination = startingLocation;
        else destination = target.getLocation();
        float x2 = (float) destination.getX();
        float y2 = (float) destination.getY();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/(float)this.speed;
        double currentTime = this.travelTime;
        if(currentTime>time) currentTime=time;
        double currentDist = (float)this.speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        if(dist != 0) return rPoint;
        else return movementLine.getP1();
    }

    public void moveTowardsActor(Point2D dest){ //Moves towards a specific point. TODO: Have the path stop based on collision radius
        Line2D rawMovementLine = new Line2D.Double(this.location,this.target.getLocation());
       //int dist = (int) Math.floor(rawMovementLine.getP1().distance(rawMovementLine.getP2()) - this.attackRange);
       // Line2D newPath = Champion.getDistanceLine(rawMovementLine,dist);
        if(this.movementLine == null){
            this.movementLine = rawMovementLine;
            this.travelTime = 0f;
            for(User u : room.getUserList()){
                ExtensionCommands.moveActor(parentExt,u,this.id,this.location,movementLine.getP2(), (float) this.speed, true);
            }
        }
        if(this.movementLine.getP2().distance(rawMovementLine.getP2()) > 0.1f){
            this.movementLine = rawMovementLine;
            this.travelTime = 0f;
            for(User u : room.getUserList()){
                ExtensionCommands.moveActor(parentExt,u,this.id,this.location,movementLine.getP2(), (float) this.speed, true);
            }
        }
    }

    public void move(Point2D dest){
        this.movementLine = new Line2D.Float(this.location,dest);
        this.travelTime = 0f;
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,dest,(float)this.speed, true);
        }
    }

    public boolean canAttack(){
        return attackCooldown == 0;
    }
}

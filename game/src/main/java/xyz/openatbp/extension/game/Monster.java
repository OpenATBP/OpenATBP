package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

public class Monster extends Actor{

    enum AggroState{PASSIVE, ATTACKED};
    enum MonsterType{SMALL,BIG};

    private AggroState state = AggroState.PASSIVE;
    private Point2D startingLocation;
    private UserActor target;
    private float travelTime;
    private MonsterType type;

    public Monster(ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName){
        this.startingLocation = new Point2D.Float(startingLocation[0],startingLocation[1]);
        this.type = MonsterType.BIG;
        this.attackCooldown = 300;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.id = monsterName;
        this.maxHealth = parentExt.getActorStats(monsterName).get("health").asInt();
        this.speed = parentExt.getActorStats(monsterName).get("speed").asDouble();
        this.currentHealth = this.maxHealth;
    }

    public Monster(ATBPExtension parentExt, Room room, Point2D startingLocation, String monsterName){
        this.startingLocation = startingLocation;
        this.type = MonsterType.SMALL;
        this.attackCooldown = 300;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.id = monsterName;
        this.maxHealth = parentExt.getActorStats(monsterName).get("health").asInt();
        this.speed = parentExt.getActorStats(monsterName).get("speed").asDouble();
        this.currentHealth = this.maxHealth;
    }
    @Override
    public boolean damaged(Actor a, int damage) {
        boolean returnVal = super.damaged(a,damage);
        if(this.state == AggroState.PASSIVE){
            state = AggroState.ATTACKED;
            target = (UserActor) a;
        }
        if(returnVal) this.die(a);
        return returnVal;
    }

    @Override
    public void attack(Actor a) {

    }

    @Override
    public void die(Actor a) {
        RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
        int scoreValue = parentExt.getActorStats(this.id).get("valueScore").asInt();
        for(User u : room.getUserList()){
            ExtensionCommands.knockOutActor(parentExt,u,this.id,a.getId(),30);
            ExtensionCommands.destroyActor(parentExt,u,this.id);
        }
        roomHandler.addScore(a.getTeam(),scoreValue);
    }

    @Override
    public void update(int msRan) {
        if(this.state == AggroState.PASSIVE){
            if(this.location.distance(this.startingLocation) >= 0.01){ //Still walking from being de-agro'd
                travelTime+=0.1f;
            }else{ //Standing still at camp

            }
        }else{
            if(this.location.distance(this.startingLocation) >= 5) this.state = AggroState.PASSIVE; //Far from camp, heading back
            else{ //Chasing player

            }
        }
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        Point2D destination;
        if(this.state == AggroState.PASSIVE) destination = startingLocation;
        else destination = target.getLocation();
        float x2 = (float) destination.getX();
        float y2 = (float) destination.getY();
        float x1 = (float) location.getX();
        float y1 = (float) location.getY();
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
        else return location;
    }

    public void moveTowardsActor(ATBPExtension parentExt, Point2D dest){ //Moves towards a specific point. TODO: Have the path stop based on collision radius
        this.travelTime = 0.1f;
        this.location = getRelativePoint();
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,dest, (float) this.speed, true);
        }
    }

    public boolean withinAttackRange(Point2D p){ // Checks if point is within monster's attack range
        float range = 0.5f;
        if(this.type == MonsterType.BIG) range =1f;
        return this.getRelativePoint().distance(p)<=range;
    }
    public boolean canAttack(){
        return attackCooldown<=300;
    }
}

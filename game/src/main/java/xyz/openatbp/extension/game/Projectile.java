package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Projectile {

    private float timeTraveled = 0;
    private Point2D destination;
    protected Point2D location;
    private Point2D startingLocation;
    protected float speed;
    private UserActor owner;
    protected String id;
    private float hitbox;

    public Projectile(UserActor owner, Line2D path, float speed, float hitboxRadius, String id){
        this.owner = owner;
        this.startingLocation = path.getP1();
        this.destination = path.getP2();
        this.speed = speed;
        this.hitbox = hitboxRadius;
        this.location = path.getP1();
        this.id = id;
    }

    public Point2D getLocation(){ //Gets player's current location based on time
        double currentTime = this.timeTraveled;
        Point2D rPoint = new Point2D.Float();
        float x2 = (float) this.destination.getX();
        float y2 = (float) this.destination.getY();
        float x1 = (float) this.startingLocation.getX();
        float y1 = (float) this.startingLocation.getY();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        if(dist == 0) return this.startingLocation;
        double time = dist/speed;
        if(currentTime>time) currentTime=time;
        double currentDist = speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        this.location = rPoint;
        return rPoint;
    }

    public void updateTimeTraveled(){
        this.timeTraveled+=0.1f;
    }

    public UserActor checkPlayerCollision(RoomHandler roomHandler){
        Point2D testPoint = new Point2D.Float(-36.90f, 2.3f);
        if(location.distance(testPoint) <= hitbox + 0.3f){ // TODO: Change to account for individual hit boxes
            this.hit(new UserActor());
            return new UserActor();
        }
        for(UserActor u : roomHandler.getPlayers()){
            if(!getTeammates().contains(u)){ //TODO: Change to not hit teammates
                if(u.getLocation().distance(location) <= hitbox + 0.3f){
                    this.hit(u);
                    return u;
                }
            }
        }
        return null;
    }

    private List<UserActor> getTeammates(){
        List<UserActor> teammates = new ArrayList<>(3);
        for(UserActor u : owner.parentExt.getRoomHandler(owner.getRoom().getId()).getPlayers()){
            if(u.getTeam() == owner.getTeam()) teammates.add(u);
        }
        return teammates;
    }

    public void hit(UserActor victim){
        System.out.println("Base projectile called!");
    }

    public Point2D getDestination(){
        return this.destination;
    }

    public void destroy(){
        ExtensionCommands.destroyActor(owner.parentExt, owner.getUser(), this.id);
    }
}

package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public abstract class Projectile {

    protected float timeTraveled = 0;
    protected Point2D destination;
    protected Point2D location;
    protected Point2D startingLocation;
    protected float speed;
    protected UserActor owner;
    protected String id;
    protected float hitbox;
    protected ATBPExtension parentExt;
    protected boolean destroyed = false;
    protected Line2D path;
    protected long startTime;
    protected double estimatedDuration;

    public Projectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id){
        this.parentExt = parentExt;
        this.owner = owner;
        this.startingLocation = path.getP1();
        this.destination = path.getP2();
        this.speed = speed;
        this.hitbox = hitboxRadius;
        this.location = path.getP1();
        this.id = id;
        this.path = path;
        this.startTime = System.currentTimeMillis();
        this.estimatedDuration = (path.getP1().distance(path.getP2()) / speed)*1000f;
    }

    public Point2D getLocation(){ //Gets projectile's current location based on time
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

    public void update(RoomHandler roomHandler){
        if(destroyed) return;
        this.updateTimeTraveled();
        Actor hitActor = this.checkPlayerCollision(roomHandler);
        if(hitActor != null){
            this.hit(hitActor);
            return;
        }
        if(this.destination.distance(this.getLocation()) <= 0.01 || System.currentTimeMillis() - this.startTime > this.estimatedDuration){
            System.out.println("Projectile being destroyed in update!");
            this.destroy();
        }
    }

    public Actor checkPlayerCollision(RoomHandler roomHandler){
        List<Actor> teammates = this.getTeammates(roomHandler);
        for(Actor a : roomHandler.getActors()){
            if((a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) && !teammates.contains(a)){ //TODO: Change to not hit teammates
                double collisionRadius = parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                if(a.getLocation().distance(location) <= hitbox + collisionRadius){
                    return a;
                }
            }
        }
        return null;
    }

    protected List<Actor> getTeammates(RoomHandler roomHandler){
        List<Actor> teammates = new ArrayList<>(3);
        for(Actor a : roomHandler.getActors()){
            if((a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) && a.getTeam() == this.owner.getTeam()) teammates.add(a);
        }
        return teammates;
    }

    protected abstract void hit(Actor victim);

    public Point2D getDestination(){
        return this.destination;
    }

    public double getEstimatedDuration() {
        return estimatedDuration;
    }

    public void destroy(){
        System.out.println("Projectile: " + id + " is being destroyed! " + this.destroyed);
        if(!destroyed){
            ExtensionCommands.destroyActor(this.parentExt, owner.getRoom(), this.id);
        }
        this.destroyed = true;
    }

    public boolean isDestroyed(){
        return this.destroyed;
    }
}

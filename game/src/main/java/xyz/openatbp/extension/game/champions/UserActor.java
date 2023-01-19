package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.game.Actor;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class UserActor extends Actor {

    private User player;
    private Point2D destination;
    private Point2D originalLocation;
    private float timeTraveled = 0;

    public UserActor(User u){
        player = u;
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x,z);
        this.speed = getStat("speed");
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        Point2D rPoint = new Point2D.Float();
        float x2 = (float) this.destination.getX();
        float y2 = (float) this.destination.getY();
        float x1 = (float) this.originalLocation.getX();
        float y1 = (float) this.originalLocation.getY();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/speed;
        double currentTime = this.timeTraveled + 0.1;
        if(currentTime>time) currentTime=time;
        double currentDist = speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
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

    public User getUser(){
        return this.player;
    }
}

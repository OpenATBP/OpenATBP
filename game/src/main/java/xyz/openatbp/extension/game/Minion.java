package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class Minion {
    enum AggroState{MOVING, PLAYER, TOWER, MINION, BASE};
    private Point2D location;
    private int team;
    private AggroState state = AggroState.MOVING;
    private String target;
    private int health;
    private final double[] blueBotX = {35.17,26.00,21.69,16.70,3.44,-9.56,-21.20,-28.02,-33.11,-36.85};
    private final double[] blueBotY = {4.06,8.64,12.24,17.25,17.81,18.76,14.78,7.19,5.46,2.33};
    private String id;
    private Room room;
    private float travelTime;
    private Point2D desiredPath;
    private int pathIndex = 0;

    public Minion(String id, Room room, int team, float x, float z){
        this.id = id;
        this.team = team;
        this.location = new Point2D.Float(x,z);
        this.room = room;
    }

    public void move(ATBPExtension parentExt){
        if(this.pathIndex < blueBotX.length){
            Point2D destination = new Point2D.Float((float) blueBotX[this.pathIndex], (float) blueBotY[this.pathIndex]);
            System.out.println("Goto: " + destination.getX() + "," + destination.getY());
            for(User u : room.getUserList()){
                ExtensionCommands.moveActor(parentExt,u,this.id,this.location,destination,1.75f,true);
            }
            this.desiredPath = destination;
        }
    }

    public void checkNearbyEntities(){

    }

    public ISFSObject toSFSObject(){
        ISFSObject minion = new SFSObject();
        ISFSObject loc = new SFSObject();
        loc.putFloat("x",(float)this.location.getX());
        loc.putFloat("y",0);
        loc.putFloat("z",(float)this.location.getY());
        minion.putSFSObject("location",loc);
        minion.putInt("health",this.health);
        return minion;
    }

    public ISFSObject creationObject(){
        ISFSObject minion = new SFSObject();
        minion.putUtfString("id",this.id);
        minion.putUtfString("actor","creep1");
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) location.getX());
        spawnPoint.putFloat("y",0f);
        spawnPoint.putFloat("z", (float) location.getY());
        minion.putSFSObject("spawn_point",spawnPoint);
        minion.putFloat("rotation",0f);
        minion.putInt("team",this.team);
        return minion;
    }

    private boolean nearEntity(Point2D p){
        return this.location.distance(p)<=5;
    }

    private boolean withinAttackRange(Point2D p){
        return this.location.distance(p)<=1;
    }
    private int findPathIndex(){
        double shortestDistance = 100;
        int index = -1;
        if(desiredPath == null) index = 1;
        else{
            for(int i = 0; i < blueBotX.length; i++){
                Point2D pathPoint = new Point2D.Double(blueBotX[i],blueBotY[i]);
                // System.out.println("Index: " + i + " Distance: " + this.location.distance(pathPoint));
                if(Math.abs(pathPoint.distance(this.location)) < shortestDistance){
                    shortestDistance = pathPoint.distance(this.location);
                    index = i;
                }
            }
            if(Math.abs(shortestDistance) < 0.01 && index+1 != blueBotX.length){
                index++;
            }
        }
        return index;
    }

    public float getTravelTime(){
        return this.travelTime;
    }

    public void addTravelTime(float time){
        this.travelTime+=time;
    }

    public Point2D getDesiredPath(){
        return this.desiredPath;
    }
    
    public Point2D getLocation(){
        return this.location;
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        System.out.println("Location: " + location.getX() + "," + location.getY());
        Point2D rPoint = new Point2D.Float();
        float x2 = (float) desiredPath.getX();
        float y2 = (float) desiredPath.getY();
        float x1 = (float) location.getX();
        float y1 = (float) location.getY();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/1.75f;
        double currentTime = this.travelTime;
        if(currentTime>time) currentTime=time;
        double currentDist = 1.75f*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        System.out.println("Loc: " + rPoint.getX() + "," + rPoint.getY());
        System.out.println("Desired: " + desiredPath.getX() + "," + desiredPath.getY());
        if(dist != 0) return rPoint;
        else return location;
    }

    public void arrived(){
        this.travelTime = 0;
        this.location = this.desiredPath;
        this.pathIndex++;
    }

    public int getPathIndex(){
        return this.pathIndex;
    }
}

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
    private float attackCooldown = 200;

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

    public boolean nearEntity(Point2D p){
        return this.getRelativePoint().distance(p)<=5;
    }
    public boolean facingEntity(Point2D p){
        Point2D currentPoint = getRelativePoint();
        double deltaX = desiredPath.getX()-location.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p.getX()>currentPoint.getX()) return true;
        else if(deltaX<0 && p.getX()<currentPoint.getX()) return true;
        else return false;
    }

    public boolean withinAttackRange(Point2D p){
        return this.getRelativePoint().distance(p)<=1;
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
        //System.out.println("Location: " + location.getX() + "," + location.getY());
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
        //System.out.println("Loc: " + rPoint.getX() + "," + rPoint.getY());
        //System.out.println("Desired: " + desiredPath.getX() + "," + desiredPath.getY());
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
    public void setTarget(String id){
        this.target = id;
        if(id.contains("tower")) this.state = AggroState.TOWER;
        else if(id.contains("minion")) this.state = AggroState.MINION;
        else if(id.contains("base")) this.state = AggroState.BASE;
        else this.state = AggroState.PLAYER;
    }

    public int getState(){
        switch(this.state){
            case PLAYER:
                return 1;
            case MINION:
                return 2;
            case TOWER:
                return 3;
            case BASE:
                return 4;
            default:
                return 0;
        }
    }

    public void moveTowardsActor(ATBPExtension parentExt, Point2D dest){
        this.location = getRelativePoint();
        Line2D movementPath = new Line2D.Float(this.location,dest);
        Point2D[] allPoints = findAllPoints(movementPath);
        Point2D newDest = new Point2D.Float();
        for(Point2D p : allPoints){
            if(p.distance(dest) <= 0.5){
                newDest = p;
                break;
            }
        }
        this.travelTime = 0;
        this.desiredPath = newDest;
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,newDest,1.75f, true);
        }
    }

    public void setState(int state){
        switch(state){
            case 0:
                this.state = AggroState.MOVING;
                int index = findPathIndex();
                Point2D newDest = new Point2D.Float((float) this.blueBotX[index], (float) this.blueBotY[index]);
                this.travelTime = 0;
                this.desiredPath = newDest;
                this.pathIndex = index;
                break;
        }
    }

    public boolean canAttack(){
        return attackCooldown<=200;
    }

    public void reduceAttackCooldown(){
        attackCooldown-=100;
    }

    public String getId(){
        return this.id;
    }
    public void attack(ATBPExtension parentExt, Point2D playerPosition, String target){
        if(target.equalsIgnoreCase(this.target)){
            if(this.state == AggroState.PLAYER && attackCooldown == 0){
                attackCooldown = 1500;
                User player = room.getUserById(Integer.parseInt(this.target));
                Champion.attackChampion(parentExt,player,20);
            }else if(attackCooldown == 200){
                reduceAttackCooldown();
                for(User u : room.getUserList()){
                    ExtensionCommands.attackActor(parentExt,u,this.id,this.target, (float) playerPosition.getX(), (float) playerPosition.getY(),false,true);
                }
            }else if(attackCooldown == 100) reduceAttackCooldown();
        }

    }

    public void stopMoving(ATBPExtension parentExt){
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,getRelativePoint(),1.75f,false);
        }
    }

    private Point2D[] findAllPoints(Line2D line){ //Finds all points within a line
        int arrayLength = (int)(line.getP1().distance(line.getP2()))*30; //Longer movement have more precision when checking collisions
        if(arrayLength < 8) arrayLength = 8;
        Point2D[] points = new Point2D[arrayLength];
        float slope = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        float intercept = (float)(line.getP2().getY()-(slope*line.getP2().getX()));
        float distance = (float)(line.getX2()-line.getX1());
        int pValue = 0;
        for(int i = 0; i < points.length; i++){ //Finds the points on the line based on distance
            float x = (float)line.getP1().getX()+((distance/points.length)*i);
            float y = slope*x + intercept;
            Point2D point = new Point2D.Float(x,y);
            points[pValue] = point;
            pValue++;
        }
        return points;
    }

    public float getAttackCooldown(){
        return this.attackCooldown;
    }
}

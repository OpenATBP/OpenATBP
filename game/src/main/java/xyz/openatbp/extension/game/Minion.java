package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

//TODO: Add more accurate pathing by creating more points alongside the main ones already defined. Also add collision detection.
public class Minion {
    enum AggroState{MOVING, PLAYER, TOWER, MINION, BASE} //State in which the minion is targeting
    enum MinionType{RANGED, MELEE, SUPER} //Type of minion
    private Point2D location;
    private int team;
    private AggroState state = AggroState.MOVING;
    private MinionType type;
    private String target;
    private int health;
    private final double[] blueBotX = {36.90,26.00,21.69,16.70,3.44,-9.56,-21.20,-28.02,-33.11,-36.85}; //Path points from blue base to purple base
    private final double[] blueBotY = {2.31,8.64,12.24,17.25,17.81,18.76,14.78,7.19,5.46,2.33};
    private String id;
    private Room room;
    private float travelTime; //How long the minion has been traveling
    private Point2D desiredPath; //Where the minion is heading to
    private int pathIndex = 0; //What stage in their pathing to the other side are they in
    private float attackCooldown = 300; //Starts at 300 to account for the first animation
    private boolean attacking = false;
    private int lane;
    private int maxHealth;

    public Minion(Room room, int team, int type, int wave, int lane, int num){
        String typeString = "super";
        if(type == 0){
            typeString = "melee";
            this.type = MinionType.MELEE;
        }else if(type == 1){
            typeString = "ranged";
            this.type = MinionType.RANGED;
        }
        else{
            this.type = MinionType.SUPER;
        }
        this.id = team+"creep_"+lane+typeString+wave+num; //TODO: Account for multiple of the same minion in the same wave
        this.team = team;
        this.lane = lane;
        float x = (float) blueBotX[0]; //Bot Lane
        float y = (float) blueBotY[0];
        if(team == 0) x = (float) blueBotX[blueBotX.length-1];
        if(lane == 0){ //Top Lane

        }
        this.location = new Point2D.Float(x,y);
        this.room = room;
        if(team == 0) pathIndex = blueBotX.length-1;
    }

    public Minion(Room room, int team, int type, int wave, int lane){
        String typeString = "super";
        if(type == 0){
            typeString = "melee";
            this.type = MinionType.MELEE;
            this.maxHealth = 450;
        }else if(type == 1){
            typeString = "ranged";
            this.type = MinionType.RANGED;
            this.maxHealth = 350;
        }
        else{
            this.type = MinionType.SUPER;
            this.maxHealth = 500;
        }
        this.health = this.maxHealth;
        this.id = team+"creep_"+lane+typeString+wave; //TODO: Account for multiple of the same minion in the same wave
        this.team = team;
        this.lane = lane;
        float x = (float) blueBotX[0]; //Bot Lane
        float y = (float) blueBotY[0];
        if(team == 0) x = (float) blueBotX[blueBotX.length-1];
        if(lane == 0){ //Top Lane

        }
        this.location = new Point2D.Float(x,y);
        this.room = room;
        if(team == 0) pathIndex = blueBotX.length-1;
    }

    public void move(ATBPExtension parentExt){ // Moves the minion along the defined path
        if(this.pathIndex < blueBotX.length && this.pathIndex > -1){
            Point2D destination = new Point2D.Float((float) blueBotX[this.pathIndex], (float) blueBotY[this.pathIndex]);
            for(User u : room.getUserList()){
                ExtensionCommands.moveActor(parentExt,u,this.id,this.location,destination,1.75f,true);
            }
            this.desiredPath = destination;
        }
    }

    @Deprecated
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

    public ISFSObject creationObject(){ //Object fed to the extension command to spawn the minion
        ISFSObject minion = new SFSObject();
        String actorName = "creep";
        if(this.team == 1) actorName+="1";
        if(this.type != MinionType.MELEE) actorName+="_";
        minion.putUtfString("id",this.id);
        minion.putUtfString("actor",actorName + getType());
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) location.getX());
        spawnPoint.putFloat("y",0f);
        spawnPoint.putFloat("z", (float) location.getY());
        minion.putSFSObject("spawn_point",spawnPoint);
        minion.putFloat("rotation",0f);
        minion.putInt("team",this.team);
        return minion;
    }

    //Checks if point is within minion's aggro range
    public boolean nearEntity(Point2D p){
        return this.getRelativePoint().distance(p)<=5;
    }
    public boolean facingEntity(Point2D p){ // Returns true if the point is in the same direction as the minion is heading
        Point2D currentPoint = getRelativePoint();
        double deltaX = desiredPath.getX()-location.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p.getX()>currentPoint.getX()) return true;
        else if(deltaX<0 && p.getX()<currentPoint.getX()) return true;
        else return false;
    }

    public boolean withinAttackRange(Point2D p){ // Checks if point is within minion's attack range
        float range = 1.25f;
        if(this.type == MinionType.RANGED) range = 4f;
        else if(this.type == MinionType.SUPER) range = 1.5f;
        return this.getRelativePoint().distance(p)<=range;
    }
    private int findPathIndex(){ //Finds the nearest point along the defined path for the minion to travel to
        double shortestDistance = 100;
        int index = -1;
        if(desiredPath == null) index = 1;
        else{
            for(int i = 0; i < blueBotX.length; i++){
                Point2D pathPoint = new Point2D.Double(blueBotX[i],blueBotY[i]);
                if(Math.abs(pathPoint.distance(this.location)) < shortestDistance){
                    shortestDistance = pathPoint.distance(this.location);
                    index = i;
                }
            }
            if(Math.abs(shortestDistance) < 0.01 && ((this.team == 1 && index+1 != blueBotX.length) || (this.team == 0 && index-1 != 0))){
                if(this.team == 1) index++;
                else index--;
            }
        }
        return index;
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
        if(dist != 0) return rPoint;
        else return location;
    }

    public void arrived(){ //Ran when the minion arrives at a point on the path
        this.travelTime = 0;
        this.location = this.desiredPath;
        if(this.team == 1) this.pathIndex++;
        else this.pathIndex--;
    }

    public int getPathIndex(){
        return this.pathIndex;
    }

    public String getTarget(){
        return this.target;
    }
    public void setTarget(ATBPExtension parentExt, String id){ //Sets what the minion is targeting. Also changes state
        this.target = id;
        if(id.contains("tower")) this.state = AggroState.TOWER;
        else if(id.contains("creep")) this.state = AggroState.MINION;
        else if(id.contains("base")) this.state = AggroState.BASE;
        else this.state = AggroState.PLAYER;
        //this.stopMoving(parentExt);
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

    public void moveTowardsActor(ATBPExtension parentExt, Point2D dest){ //Moves towards a specific point. TODO: Have the path stop based on collision radius
        this.location = getRelativePoint();
        this.desiredPath = dest;
        this.travelTime = 0.1f;
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,dest,1.75f, true);
        }
    }

    public void setState(int state){ //Really only useful for setting to the movement state.
        switch(state){
            case 0:
                int index = findPathIndex();
                if(this.state != AggroState.PLAYER){
                    if(this.team == 0) index--;
                    else index++;
                    if(index < 0) index = 0;
                    if(index >= this.blueBotX.length) index = this.blueBotX.length-1;
                }
                this.state = AggroState.MOVING;
                Point2D newDest = new Point2D.Float((float) this.blueBotX[index], (float) this.blueBotY[index]);
                this.location = getRelativePoint();
                this.desiredPath = newDest;
                this.travelTime = 0;
                this.target = null;
                this.pathIndex = index;
                this.attacking = false;
                break;
            case 1:
                this.state = AggroState.PLAYER;
                break;
            case 2:
                this.state = AggroState.TOWER;
                break;
        }
    }

    public boolean canAttack(){
        return attackCooldown<=300;
    }

    public void reduceAttackCooldown(){
        attackCooldown-=100;
    }

    public String getId(){
        return this.id;
    }
    public void attack(ATBPExtension parentExt, Point2D playerPosition){ //Handles attacking players
            if(this.state == AggroState.PLAYER && attackCooldown == 0){
                int newCooldown = 1500;
                if(this.type == MinionType.RANGED) newCooldown = 2000;
                else if(this.type == MinionType.SUPER) newCooldown = 1000;
                this.attackCooldown = newCooldown;
                User player = room.getUserById(Integer.parseInt(this.target));
                if(this.type != MinionType.RANGED) Champion.attackChampion(parentExt,player,20);
                else Champion.rangedAttackChampion(parentExt,room,this.id,this.target,20);
            }else if(attackCooldown == 300){
                reduceAttackCooldown();
                for(User u : room.getUserList()){
                    ExtensionCommands.attackActor(parentExt,u,this.id,this.target, (float) playerPosition.getX(), (float) playerPosition.getY(),false,true);
                }
            }else if(attackCooldown == 100 || attackCooldown == 200) reduceAttackCooldown();
    }

    public void attack(ATBPExtension parentExt, Tower tower){ //Handles attacking towers
        if(this.state == AggroState.TOWER && attackCooldown == 0){
            int newCooldown = 1500;
            if(this.type == MinionType.RANGED) newCooldown = 2000;
            else if(this.type == MinionType.SUPER) newCooldown = 1000;
            this.attackCooldown = newCooldown;
            if(this.type != MinionType.RANGED) Champion.attackTower(parentExt,this.room,this.id,tower,150);
            else Champion.rangedAttackTower(parentExt,room,this.id,tower,150);
        }else if(attackCooldown == 300){
            reduceAttackCooldown();
            for(User u : room.getUserList()){
                ExtensionCommands.attackActor(parentExt,u,this.id,tower.getId(), (float) tower.getLocation().getX(), (float) tower.getLocation().getY(),false,true);
            }
        }else if(attackCooldown == 100 || attackCooldown == 200) reduceAttackCooldown();
    }

    public void attack(ATBPExtension parentExt, Minion m){
        if(this.state == AggroState.MINION && attackCooldown == 0){
            int newCooldown = 1500;
            if(this.type == MinionType.RANGED) newCooldown = 2000;
            else if(this.type == MinionType.SUPER) newCooldown = 1000;
            this.attackCooldown = newCooldown;
            if(this.type != MinionType.RANGED) Champion.attackMinion(parentExt,this.id,m,20);
            else Champion.rangedAttackMinion(parentExt,this.room,this.id,m,50);
        }else if(attackCooldown == 300){
            reduceAttackCooldown();
            for(User u : room.getUserList()){
                ExtensionCommands.attackActor(parentExt,u,this.id,m.getId(), (float) m.getLocation().getX(), (float) m.getLocation().getY(),false,true);
            }
        }else if(attackCooldown == 100 || attackCooldown == 200) reduceAttackCooldown();
    }

    public void stopMoving(ATBPExtension parentExt){ //Stops moving
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,getRelativePoint(),getRelativePoint(),1.75f,false);
        }
    }

    @Deprecated
    private Point2D[] findAllPoints(Line2D line){ //Finds all points within a line TODO: Can be used for collision detection & increasing path accuracy
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

    public String getType(){
        if(this.type == MinionType.MELEE) return "";
        else if(this.type == MinionType.RANGED) return "ranged";
        else return "super";
    }

    public int getTeam(){
        return this.team;
    }

    public boolean isAttacking(){
        return this.attacking;
    }

    public void setAttacking(boolean attacking){
        this.attacking = attacking;
    }

    public int getLane(){
        return this.lane;
    }

    public int getHealth(){
        return this.health;
    }

    public boolean damage(ATBPExtension parentExt,String attacker, int damage){
        this.health-=damage;
        if(this.health <= 0){
            System.out.println(this.id + ": I am dead!");
            for(User u : this.getRoomUsers()){
                Champion.handleMinionDeath(parentExt,u,attacker,this);
            }
            return true;
        }
        return false;
    }

    public int getMaxHealth(){
        return this.maxHealth;
    }

    public List<User> getRoomUsers(){
        return this.room.getUserList();
    }
}

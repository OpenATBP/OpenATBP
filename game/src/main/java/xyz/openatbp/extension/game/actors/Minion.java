package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//TODO: Add more accurate pathing by creating more points alongside the main ones already defined. Also add collision detection.
public class Minion extends Actor {
    enum AggroState{MOVING, PLAYER, TOWER, MINION, BASE} //State in which the minion is targeting
    enum MinionType{RANGED, MELEE, SUPER} //Type of minion
    private AggroState state = AggroState.MOVING;
    private MinionType type;
    private String target;
    private final double[] blueBotX = {36.90,26.00,21.69,16.70,3.44,-9.56,-21.20,-28.02,-33.11,-36.85}; //Path points from blue base to purple base
    private final double[] blueBotY = {2.31,8.64,12.24,17.25,17.81,18.76,14.78,7.19,5.46,2.33};
    private final double[] blueTopX = {36.68, 30.10, 21.46, 18.20, -5.26, -12.05, -24.69, -28.99, -35.67};
    private final double[] blueTopY = {-2.56, -7.81, -12.09, -16.31, -17.11, -17.96, -13.19, -7.50, -2.70};
    private float travelTime; //How long the minion has been traveling
    private int pathIndex = 0; //What stage in their pathing to the other side are they in
    private boolean attacking = false;
    private int lane;
    private boolean dead = false;
    private Map<String, Integer> aggressors;
    private Line2D movementLine;
    private int xpWorth = 5;

    public Minion(ATBPExtension parentExt, Room room, int team, int minionNum, int wave, int lane){ //TODO: Sometimes the first minion in a wave will spawn across the map?
        this.attackCooldown = 300;
        this.avatar = "creep"+team;
        String typeString = "super";
        if(minionNum <= 2){
            typeString = "melee"+minionNum;
            this.type = MinionType.MELEE;
            this.maxHealth = 450;
        }else if(minionNum <= 4){
            typeString = "ranged"+minionNum;
            this.avatar+="_ranged";
            this.type = MinionType.RANGED;
            this.maxHealth = 350;
        }
        else{
            this.type = MinionType.SUPER;
            this.avatar+="_super";
            this.maxHealth = 500;
        }
        this.displayName = parentExt.getDisplayName(this.avatar.replace("0",""));
        this.currentHealth = this.maxHealth;
        float x = (float) blueBotX[0]; //Bot Lane
        float y = (float) blueBotY[0];
        if(team == 0) x = (float) blueBotX[blueBotX.length-1];
        if(lane == 0){ //Top Lane
            x = (float) blueTopX[0];
            y = (float) blueTopY[0];
            if(team == 0){
                x = (float) blueTopX[blueTopX.length-1];
                y = (float) blueTopY[blueTopY.length-1];
            }
        }
        this.speed = 1.75f;
        this.location = new Point2D.Float(x,y);
        this.id = team+"creep_"+lane+typeString+wave;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lane = lane;
        this.actorType = ActorType.MINION;
        if(team == 0){
            if(lane == 0) pathIndex = blueTopX.length-1;
            else pathIndex = blueBotX.length-1;
        }
        this.movementLine = new Line2D.Float(this.location,this.location);
        aggressors = new HashMap<>(3);
        this.stats = this.initializeStats();
        ExtensionCommands.createActor(parentExt,this.room,this.creationObject());
        this.move(parentExt);
    }

    public void move(ATBPExtension parentExt){ // Moves the minion along the defined path
        Actor newTarget = this.getNewTarget();
        if(newTarget != null && this.movementLine != null){
            this.setTarget(newTarget);
            return;
        }
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY=blueTopY;
        }
        if(this.pathIndex < pathX.length && this.pathIndex > -1){
            Point2D destination = new Point2D.Float((float) pathX[this.pathIndex], (float) pathY[this.pathIndex]);
            ExtensionCommands.moveActor(parentExt,this.room,this.id,this.location,destination, (float) this.getPlayerStat("speed"),true);
            this.movementLine = new Line2D.Float(this.location,destination);
            this.travelTime = 0.1f;
        }
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
        return this.location.distance(p)<=5;
    }
    public boolean nearEntity(Point2D p, float radius){ return this.location.distance(p)<=5+radius; }
    public boolean facingEntity(Point2D p){ // Returns true if the point is in the same direction as the minion is heading
        //TODO: Some minions don't attack others attacking the base when they spawn
        double deltaX = movementLine.getX2()-location.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p.getX()>this.location.getX()) return true;
        else return deltaX < 0 && p.getX() < this.location.getX();
    }

    public boolean withinAttackRange(Point2D p){ // Checks if point is within minion's attack range
        double range = this.getPlayerStat("attackRange");
        return this.location.distance(p)<=range;
    }

    public boolean withinAttackRange(Point2D p, float radius){
        double range = this.getPlayerStat("attackRange");
        range+=radius;
        return this.location.distance(p)<=range;
    }
    private int findPathIndex(){ //Finds the nearest point along the defined path for the minion to travel to
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY = blueTopY;
        }
        double shortestDistance = 100;
        int index = -1;
        if(movementLine == null) index = 1;
        else{
            for(int i = 0; i < pathX.length; i++){
                Point2D pathPoint = new Point2D.Double(pathX[i],pathY[i]);
                if(Math.abs(pathPoint.distance(this.location)) < shortestDistance){
                    shortestDistance = pathPoint.distance(this.location);
                    index = i;
                }
            }
            if(Math.abs(shortestDistance) < 0.01 && ((this.team == 0 && index+1 != pathX.length) || (this.team == 1 && index-1 != 0))){
                if(this.team == 1) index++;
                else index--;
            }
        }
        return index;
    }

    public void addTravelTime(float time){
        this.travelTime+=time;
    }
    
    public Point2D getLocation(){
        return this.location;
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        if(this.movementLine == null) return this.location;
        float x2 = (float) this.movementLine.getX2();
        float y2 = (float) this.movementLine.getY2();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/(float)this.getPlayerStat("speed");
        double currentTime = this.travelTime;
        if(currentTime>time) currentTime=time;
        double currentDist = (float)this.getPlayerStat("speed")*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        if(dist != 0) return rPoint;
        else return location;
    }

    public void arrived(){ //Ran when the minion arrives at a point on the path
        this.travelTime = 0;
        this.location = this.movementLine.getP2();
        if(this.team == 1) this.pathIndex++;
        else this.pathIndex--;
    }

    public int getPathIndex(){
        return this.pathIndex;
    }

    public String getTarget(){
        return this.target;
    }
    public void setTarget(Actor a){ //Sets what the minion is targeting. Also changes state
        this.target = a.getId();
        if(a.getActorType() == ActorType.TOWER) this.state = AggroState.TOWER;
        else if(a.getActorType() == ActorType.MINION || a.getActorType() == ActorType.COMPANION) this.state = AggroState.MINION;
        else if(a.getActorType() == ActorType.BASE) this.state = AggroState.BASE;
        else if(a.getActorType() == ActorType.PLAYER) this.state = AggroState.PLAYER;
        //this.moveTowardsActor(parentExt,parentExt.getRoomHandler(room.getId()).getActor(id).getLocation());
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
        this.movementLine = new Line2D.Float(this.location,dest);
        this.travelTime = 0.1f;
        ExtensionCommands.moveActor(parentExt,this.room,this.id,this.location,dest,1.75f, true);
    }

    public void setState(int state){ //Really only useful for setting to the movement state.
        double[] pathX;
        double[] pathY;
        if(this.lane != 0){
            pathX = blueBotX;
            pathY = blueBotY;
        }else{
            pathX = blueTopX;
            pathY = blueTopY;
        }
        switch(state){
            case 0:
                int index = findPathIndex();
                if(this.state != AggroState.PLAYER){
                    if(this.team == 0) index--;
                    else index++;
                    if(index < 0) index = 0;
                    if(index >= pathX.length) index = pathX.length-1;
                }
                this.state = AggroState.MOVING;
                Point2D newDest = new Point2D.Float((float) pathX[index], (float) pathY[index]);
                this.movementLine = new Line2D.Float(this.location,newDest);
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

    @Override
    public boolean setTempStat(String stat, double delta) {
        boolean returnVal = super.setTempStat(stat,delta);
        if(stat.equalsIgnoreCase("speed")){
            if(movementLine != null){
                movementLine.setLine(this.location, movementLine.getP2());
                this.travelTime = 0f;
                ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,movementLine.getP2(),(float)this.getPlayerStat("speed"),true);

            }
        }
        return returnVal;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        Actor target = this.getNewTarget();
        if(target != null) this.setTarget(target);
    }

    @Override
    public void attack(Actor a){
        if(attackCooldown == 0 && this.state != AggroState.MOVING){
            this.stopMoving(parentExt);
            int damage = (int) this.getPlayerStat("attackDamage");
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            ExtensionCommands.attackActor(parentExt,this.room,this.id,a.getId(),(float) a.getLocation().getX(), (float) a.getLocation().getY(), false, true);

            if(this.type == MinionType.RANGED){
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedRangedAttack(this,a),300, TimeUnit.MILLISECONDS);
            }else{
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,damage,"basicAttack"),300,TimeUnit.MILLISECONDS);
            }
        }
    }
    @Override
    public void rangedAttack(Actor a){
        String fxId = "minion_projectile_";
        if(this.team == 0) fxId+="purple";
        else fxId+="blue";
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(parentExt,this.room,fxId,this.getId(),a.getId(),"Bip001","Bip001",time);

        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,(int)this.getPlayerStat("attackDamage"),"basicAttack"),(int)time*1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) { //TODO: Actor voice line plays after each kill (last hit or not)
        System.out.println(this.id + " has died! " + this.dead);
        this.currentHealth = 0;
        if(this.dead) return;
        this.stopMoving();
        this.dead = true;
        if(a.getActorType() == ActorType.PLAYER){
            UserActor ua = (UserActor) a;
            ua.addGameStat("minions",1);
        }
        this.parentExt.getRoomHandler(this.room.getId()).handleAssistXP(a,aggressors.keySet(), this.xpWorth);
        ExtensionCommands.knockOutActor(parentExt,this.room,this.id,a.getId(),30);
        ExtensionCommands.destroyActor(parentExt,this.room,this.id);
        if(a.getActorType() == ActorType.PLAYER){
            UserActor ua = (UserActor) a;
            if(ua.hasBackpackItem("junk_1_magic_nail") && ua.getStat("sp_category1") > 0) ua.addNailStacks(2);
            this.parentExt.getRoomHandler(this.room.getId()).addScore(ua,a.getTeam(),1);
        }
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        if(this.dead) return;
        this.location = getRelativePoint();
        if(this.attackCooldown > 0) this.reduceAttackCooldown();
        RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
        if(msRan % 1000 == 0){
            for(String k : aggressors.keySet()){
                if(aggressors.get(k) == 10) aggressors.remove(k);
                else aggressors.put(k,aggressors.get(k)+1);
            }
            int xp = 5 + ((msRan/1000)/60);
            if(this.type == MinionType.SUPER) xp+=5;
            if(xpWorth != xp) xpWorth = xp;
        }
        switch(this.getState()){ //TODO: Rework minion targeting
            case 0: // MOVING
                if(this.movementLine == null){
                    this.move(this.parentExt);
                    break;
                }
                if(this.getState() == 0 && (this.getPathIndex() < 10 & this.getPathIndex()>= 0) && this.movementLine.getP2() != null && ((Math.abs(this.movementLine.getP2().distance(this.location)) < 0.2) || Double.isNaN(this.movementLine.getP2().getX()))){
                    this.arrived();
                    this.move(parentExt);
                }else{
                    this.addTravelTime(0.1f);
                }
                Actor newTarget = this.getNewTarget();
                if(newTarget != null) this.setTarget(newTarget);
                break;
            case 1: //PLAYER TARGET TODO: Ranged minions still move towards you when in range for some reason
                UserActor target = roomHandler.getPlayer(this.getTarget());
                Point2D currentPoint = target.getLocation();
                int health = target.getHealth();
                if(!this.nearEntity(currentPoint) || health <= 0){ //Resets the minion's movement if it loses the target
                    this.setState(0);
                    this.move(parentExt);
                }else{
                    if (this.withinAttackRange(currentPoint) && this.canAttack()) this.attack(target);
                    else this.moveTowardsActor(parentExt,currentPoint);
                }
                break;
            case 2: // MINION TARGET
                Actor targetMinion = roomHandler.getActor(this.target);
                if(targetMinion != null && this.withinAttackRange(targetMinion.getLocation())){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetMinion.getHealth() > 0){
                            this.attack(targetMinion);
                        }else{ //Handles tower death and resets minion on tower kill
                            this.setState(0);
                            this.move(parentExt);
                            this.travelTime+=0.1f;
                        }
                    }
                }else if(targetMinion != null){
                    this.moveTowardsActor(parentExt,targetMinion.getLocation()); //TODO: Optimize so it's not sending a lot of commands
                }else{
                    this.setState(0);
                    this.move(parentExt);
                    this.travelTime+=0.1f;
                }
                break;
            case 3: // TOWER TARGET
                Tower targetTower = roomHandler.findTower(this.getTarget());
                if(targetTower != null && this.withinAttackRange(targetTower.getLocation())){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetTower.getHealth() > 0){
                            this.attack(targetTower);
                        }else{ //Handles tower death and resets minion on tower kill
                            if(targetTower.getTowerNum() == 0 || targetTower.getTowerNum() == 3) roomHandler.getOpposingTeamBase(this.team).unlock();
                            this.setState(0);
                            this.move(parentExt);
                            this.travelTime+=0.1f;
                            break;
                        }
                    }
                }else if(targetTower != null){
                    this.addTravelTime(0.1f);
                    //m.moveTowardsActor(parentExt,targetTower.getLocation());
                }else{
                    this.setState(0);
                    this.move(parentExt);
                    break;
                }
                break;
            case 4: // BASE TARGET
                Base targetBase = roomHandler.getOpposingTeamBase(this.getTeam());
                if(targetBase != null && this.withinAttackRange(targetBase.getLocation(),1.8f)){
                    if(this.canAttack()){
                        if(!this.isAttacking()){
                            this.setAttacking(true);
                        }
                        if(targetBase.getHealth() > 0){
                            this.attack(targetBase);
                        }
                    }
                }else if(targetBase != null){
                    this.addTravelTime(0.1f);
                }
                break;
        }
    }

    public void stopMoving(ATBPExtension parentExt){ //Stops moving
        this.travelTime = 0f;
        this.movementLine = new Line2D.Float(this.location,this.location);
        ExtensionCommands.moveActor(parentExt,this.room,this.id,this.location,this.location,1.75f,false);
    }

    public String getType(){
        if(this.type == MinionType.MELEE) return "";
        else if(this.type == MinionType.RANGED) return "ranged";
        else return "super";
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try{
            if(this.dead) return true;
            if(a.getActorType() == ActorType.PLAYER){
                aggressors.put(a.getId(),0);
            }
            if(a.getActorType() == ActorType.TOWER){
                if(this.type == MinionType.SUPER) damage = (int) Math.round(damage*0.25);
                else damage = (int) Math.round(damage*0.75);
            }
            AttackType type = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage,type,a);
            if(a.getActorType() == ActorType.PLAYER) this.addDamageGameStat((UserActor) a,newDamage,type);
            this.changeHealth(newDamage*-1);
            //Minion dies
            return currentHealth <= 0;
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }

    }

    public boolean isAttacking(){
        return this.attacking;
    }

    @Override
    public String getAvatar(){
        return this.avatar.replace("0","");
    }

    public void setAttacking(boolean attacking){
        this.attacking = attacking;
    }

    public int getLane(){
        return this.lane;
    }

    public List<User> getRoomUsers(){
        return this.room.getUserList();
    }

    public Actor getNewTarget(){ //TODO: Make it so minion gets the closest target
        RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getId());
        Base opposingBase = roomHandler.getOpposingTeamBase(this.team);
        if(opposingBase.isUnlocked() && this.nearEntity(opposingBase.getLocation(),1.8f)){
            if(this.getTarget() == null){
                this.moveTowardsActor(parentExt,opposingBase.getLocation());
                return opposingBase;
            }
        }
        for(Minion minion : roomHandler.getMinions()){ // Check other minions
            if(this.getTeam() != minion.getTeam() && this.getLane() == minion.getLane()){
                if(this.nearEntity(minion.getLocation()) && this.facingEntity(minion.getLocation())){
                    if(this.getTarget() == null){
                        return minion;
                    }
                }
            }
        }
        for(Actor c : roomHandler.getCompanions()){
            if(this.getTeam() != c.getTeam() && this.nearEntity(c.getLocation())){
                this.moveTowardsActor(parentExt,c.getLocation());
                return c;
            }
        }
        for(Tower t: roomHandler.getTowers()){
            if(t.getTeam() != this.getTeam() && this.nearEntity(t.getLocation())){ //Minion prioritizes towers over players
                this.moveTowardsActor(parentExt,t.getLocation());
                return t;
            }
        }
        for(UserActor u : roomHandler.getPlayers()){
            int health = u.getHealth();
            int userTeam = u.getTeam();
            Point2D currentPoint = u.getCurrentLocation();
            if(this.getTeam() != userTeam && health > 0 && (!u.getState(ActorState.INVISIBLE) && u.getState(ActorState.REVEALED)) && this.nearEntity(currentPoint) && this.facingEntity(currentPoint)){
                if(this.getTarget() == null){ //This will probably always be true.
                    ExtensionCommands.setTarget(parentExt,u.getUser(),this.getId(), u.getId());
                    return u;
                }
            }
        }
        return null;
    }
    protected HashMap<String, Double> initializeStats(){
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar.replace("0",""));
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k,actorStats.get(k).asDouble());
        }
        return stats;
    }

    @Override
    public String getPortrait(){
        return this.getAvatar();
    }
}

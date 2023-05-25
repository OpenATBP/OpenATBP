package xyz.openatbp.extension.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.FlamePrincess;
import xyz.openatbp.extension.game.champions.Lich;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class Champion {


    public static void giveBuff(ATBPExtension parentExt,User u, Buff buff){
        String stat = null;
        float duration = 0;
        double value = 0;
        boolean icon = false;
        String effect = null;
        String iconString = null;
        switch(buff){
            case HEALTH_PACK:
                stat = "healthRegen";
                value = 20;
                duration = 5f;
                effect = "fx_health_regen";
                break;
            case ATTACK_ALTAR:
                break;
            case POLYMORPH:
                double currentSpeed = u.getVariable("stats").getSFSObjectValue().getDouble("speed");
                value = currentSpeed*-0.15f;
                stat = "speed";
                icon = true;
                effect = "flambit_aoe";
                duration = 3f;
                ExtensionCommands.swapActorAsset(parentExt,u, String.valueOf(u.getId()),"flambit");
                break;
        }
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        if(stat != null && stats.getDouble(stat) != null){
            double newStat = stats.getDouble(stat) + value;
            stats.putDouble(stat,newStat);
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(u.getId()));
            updateData.putDouble(stat,newStat);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
            int interval = (int) Math.floor(duration*1000);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new BuffHandler(parentExt,u,buff,stat,value,icon),interval,TimeUnit.MILLISECONDS);
            int team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
            ExtensionCommands.createActorFX(parentExt,u.getLastJoinedRoom(),String.valueOf(u.getId()),effect,interval,effect+u.getId(),true,"",false,false,team);
        }
    }

    public static void updateHealth(ATBPExtension parentExt, User u, int health){
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        double currentHealth = stats.getInt("currentHealth");
        double maxHealth = stats.getInt("maxHealth");
        currentHealth+=health;
        if(currentHealth>maxHealth) currentHealth = maxHealth;
        double pHealth = currentHealth/maxHealth;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", String.valueOf(u.getId()));
        updateData.putInt("maxHealth",(int)maxHealth);
        updateData.putInt("currentHealth",(int)currentHealth);
        updateData.putDouble("pHealth",pHealth);
        ExtensionCommands.updateActorData(parentExt,u,updateData);
        stats.putInt("currentHealth",(int)currentHealth);
        stats.putDouble("pHealth",pHealth);
    }

    public static void updateServerHealth(ATBPExtension parentExt, Actor a){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",a.getId());
        data.putInt("maxHealth",a.getMaxHealth());
        data.putInt("currentHealth",a.getHealth());
        data.putDouble("pHealth",a.getPHealth());
        for(User u : a.getRoom().getUserList()){
            ExtensionCommands.updateActorData(parentExt,u,data);
        }
    }

    public static UserActor getCharacterClass(User u, ATBPExtension parentExt){
        String avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        String character = avatar.split("_")[0];
        switch(character){
            case "flame":
                return new FlamePrincess(u,parentExt);
            case "lich":
                return new Lich(u,parentExt);
        }
        return new UserActor(u, parentExt);
    }

    public static JsonNode getSpellData(ATBPExtension parentExt, String avatar, int spell){
        JsonNode actorDef = parentExt.getDefintion(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell"+spell);
    }

    public static Point2D getDashPoint(ATBPExtension parentExt, UserActor player, Point2D dest){
        String room = player.getUser().getLastJoinedRoom().getGroupId();
        Line2D movementLine = new Line2D.Float(player.getLocation(),dest);
        ArrayList<Vector<Float>>[] colliders = parentExt.getColliders(room); //Gets all collision object vertices
        ArrayList<Path2D> mapPaths = parentExt.getMapPaths(room); //Gets all created paths for the collision objects
        for(int i = 0; i < mapPaths.size(); i++){
            if(mapPaths.get(i).contains(movementLine.getP2())){
                ArrayList<Vector<Float>> collider = colliders[i];
                for(int g = 0; g < collider.size(); g++){ //Check all vertices in the collider

                    Vector<Float> v = collider.get(g);
                    Vector<Float> v2;
                    if(g+1 == collider.size()){ //If it's the final vertex, loop to the beginning
                        v2 = collider.get(0);
                    }else{
                        v2 = collider.get(g+1);
                    }


                    Line2D colliderLine = new Line2D.Float(v.get(0),v.get(1),v2.get(0),v2.get(1)); //Draws a line segment for the sides of the collider
                    if(movementLine.intersectsLine(colliderLine)){ //If the player movement intersects a side
                        Line2D newMovementLine = new Line2D.Float(movementLine.getP1(),getIntersectionPoint(movementLine,colliderLine));
                        return collidePlayer(newMovementLine,mapPaths.get(i));
                    }
                }
            }
        }
        return dest;
    }

    private static Point2D collidePlayer(Line2D movementLine, Path2D collider){
        Point2D[] points = findAllPoints(movementLine);
        Point2D p = movementLine.getP1();
        for(int i = points.length-2; i>0; i--){ //Searchs all points in the movement line to see how close it can move without crashing into the collider
            Point2D p2 = new Point2D.Double(points[i].getX(),points[i].getY());
            Line2D line = new Line2D.Double(movementLine.getP1(),p2);
            if(collider.intersects(line.getBounds())){
                System.out.println("Intersects!");
                p = p2;
                break;
            }else{
                System.out.println("Does not intersect!");
            }
        }
        return p;
    }

    public static Point2D getIntersectionPoint(Line2D line, Line2D line2){ //Finds the intersection of two lines
        float slope1 = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        float slope2 = (float)((line2.getP2().getY() - line2.getP1().getY())/(line2.getP2().getX()-line2.getP1().getX()));
        float intercept1 = (float)(line.getP2().getY()-(slope1*line.getP2().getX()));
        float intercept2 = (float)(line2.getP2().getY()-(slope2*line2.getP2().getX()));
        float x = (intercept2-intercept1)/(slope1-slope2);
        float y = slope1 * ((intercept2-intercept1)/(slope1-slope2)) + intercept1;
        if(Float.isNaN(x) || Float.isNaN(y)) return line.getP1();
        return new Point2D.Float(x,y);
    }

    private static Point2D[] findAllPoints(Line2D line){ //Finds all points within a line
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

    public static List<Actor> getUsersInRadius(RoomHandler room, Point2D center, float radius){
        List<Actor> actors = room.getActors();
        List<Actor> affectedActors = new ArrayList<>(actors.size());
        Ellipse2D circle = new Ellipse2D.Double(center.getX()-radius,center.getY()-radius,radius*2,radius*2);
        System.out.println("Circle center: " + circle.getCenterX() + "," + circle.getCenterY());
        for(Actor a : actors){
            Point2D location = a.getLocation();
            if(circle.contains(location)) affectedActors.add(a);
        }
        return affectedActors;
    }

    public static UserActor getUserInLine(RoomHandler room, List<UserActor> exemptedUsers, Line2D line){
        UserActor hitActor = null;
        double closestDistance = 100;
        for(UserActor u : room.getPlayers()){
            if(!exemptedUsers.contains(u)){
                if(line.intersectsLine(u.getMovementLine())){
                    if(u.getLocation().distance(line.getP1()) < closestDistance){
                        closestDistance = u.getLocation().distance(line.getP1());
                        hitActor = u;
                    }
                }
            }
        }
        if(hitActor != null){
            Point2D intersectionPoint = getIntersectionPoint(line,hitActor.getMovementLine());
        }
        return hitActor;
    }

    public static Line2D getMaxRangeLine(Line2D projectileLine, float spellRange){
        float remainingRange = (float) (spellRange-projectileLine.getP1().distance(projectileLine.getP2()));
        if(projectileLine.getP1().distance(projectileLine.getP2()) >= spellRange-0.01) return projectileLine;
        float slope = (float)((projectileLine.getP2().getY() - projectileLine.getP1().getY())/(projectileLine.getP2().getX()-projectileLine.getP1().getX()));
        float intercept = (float)(projectileLine.getP2().getY()-(slope*projectileLine.getP2().getX()));
        float deltaX = (float) (projectileLine.getX2()-projectileLine.getX1());
        float x = (float)projectileLine.getP2().getX()+(remainingRange);
        if (deltaX < 0) x = (float)projectileLine.getX2()-remainingRange;
        float y = slope*x + intercept;
        Point2D newPoint = new Point2D.Float(x,y);
        return new Line2D.Float(projectileLine.getP1(),newPoint);
    }

    public static Line2D getDistanceLine(Line2D movementLine, float distance){
        float slope = (float)((movementLine.getP2().getY() - movementLine.getP1().getY())/(movementLine.getP2().getX()-movementLine.getP1().getX()));
        float intercept = (float)(movementLine.getP2().getY()-(slope*movementLine.getP2().getX()));
        float deltaX = (float) (movementLine.getX2()-movementLine.getX1());
        float x = -1;
        if(distance > 0){
            x = (float)movementLine.getP1().getX()+(distance);
            if (deltaX < 0) x = (float)movementLine.getX1()-distance;
        }else if(distance < 0){
            x = (float)movementLine.getX2()+distance;
            if(deltaX < 0) x = (float)movementLine.getX2()-distance;
        }
        float y = slope*x + intercept;
        Point2D newPoint = new Point2D.Float(x,y);
        return new Line2D.Float(movementLine.getP1(),newPoint);
    }

    public static HashMap<ActorState, Boolean> getBlankStates(){
        HashMap<ActorState, Boolean> states = new HashMap<>(ActorState.values().length);
        for(ActorState s : ActorState.values()){
            states.put(s,false);
        }
        return states;
    }

    public static class DelayedAttack implements Runnable{

        Actor attacker;
        Actor target;
        int damage;

        public DelayedAttack(Actor attacker, Actor target, int damage){
            this.attacker = attacker;
            this.target = target;
            this.damage = damage;
        }

        @Override
        public void run() {
            target.damaged(attacker,damage);
            if(attacker.getActorType() == ActorType.MONSTER && !attacker.getId().contains("_")) attacker.setCanMove(true);
        }
    }

    public static class DelayedRangedAttack implements Runnable {
        Actor attacker;
        Actor target;
        int damage;

        DelayedRangedAttack(Actor a, Actor t){
            this.attacker = a;
            this.target = t;
        }
        @Override
        public void run() {
            attacker.rangedAttack(target);
            attacker.setCanMove(true);
        }
    }
    public static class RespawnCharacter implements  Runnable {

        String id;
        ATBPExtension parentExt;
        Room room;

        public RespawnCharacter(ATBPExtension parentExt, Room room, String id){
            this.parentExt = parentExt;
            this.room = room;
            this.id = id;
        }
        @Override
        public void run() {
            for(User u : room.getUserList()){
                ExtensionCommands.respawnActor(parentExt,u,id);
            }
        }
    }
    public static class EffectHandler implements Runnable {
        Actor actor;
        ActorState state;
        double originalStat;
        EffectHandler(Actor a, ActorState state, double originalStat){
            this.actor = a;
            this.state = state;
            this.originalStat = originalStat;
        }
        @Override
        public void run() {
            switch(state){
                case SLOWED:
                    this.actor.setSpeed((float) originalStat);
                    break;
            }
            this.actor.setState(this.state,false);
        }

        public double getOriginalStat(){
            return this.originalStat;
        }
    }
}

class BuffHandler implements Runnable {

    User u;
    Buff buff;
    String buffName;
    double value;
    boolean icon;
    ATBPExtension parentExt;

    BuffHandler(User u, Buff buff, String buffName, double value, boolean icon){
        this.u = u;
        this.buffName = buffName;
        this.value = value;
        this.icon = icon;
        this.buff = buff;
    }

    BuffHandler(ATBPExtension parentExt,User u, Buff buff, String buffName, double value, boolean icon){
        this.u = u;
        this.buffName = buffName;
        this.value = value;
        this.icon = icon;
        this.buff = buff;
        this.parentExt = parentExt;
    }

    @Override
    public void run() {
        double currentStat = u.getVariable("stats").getSFSObjectValue().getDouble(buffName);
        u.getVariable("stats").getSFSObjectValue().putDouble(buffName,currentStat-value);
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(u.getId()));
        data.putDouble(buffName,currentStat-value);
        ExtensionCommands.updateActorData(parentExt,u,data);
        if(icon){

        }
        if(buff == Buff.POLYMORPH){
            ExtensionCommands.swapActorAsset(parentExt,u, String.valueOf(u.getId()),u.getVariable("player").getSFSObjectValue().getUtfString("avatar"));
            parentExt.getRoomHandler(u.getLastJoinedRoom().getId()).getPlayer(String.valueOf(u.getId())).setState(ActorState.POLYMORPH,false);
        }
    }
}


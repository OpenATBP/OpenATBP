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
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Tower;
import xyz.openatbp.extension.game.champions.FlamePrincess;
import xyz.openatbp.extension.game.champions.Gunter;
import xyz.openatbp.extension.game.champions.Lich;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class Champion {
    public static void updateServerHealth(ATBPExtension parentExt, Actor a){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",a.getId());
        data.putInt("maxHealth",a.getMaxHealth());
        data.putInt("currentHealth",a.getHealth());
        data.putDouble("pHealth",a.getPHealth());
        ExtensionCommands.updateActorData(parentExt,a.getRoom(),data);
    }

    public static UserActor getCharacterClass(User u, ATBPExtension parentExt){
        String avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        String character = avatar.split("_")[0];
        switch(character){
            case "flame":
                return new FlamePrincess(u,parentExt);
            case "lich":
                return new Lich(u,parentExt);
            case "gunter":
                return new Gunter(u,parentExt);
        }
        return new UserActor(u, parentExt);
    }

    public static JsonNode getSpellData(ATBPExtension parentExt, String avatar, int spell){
        JsonNode actorDef = parentExt.getDefinition(avatar);
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

    public static List<Actor> getActorsInRadius(RoomHandler room, Point2D center, float radius){
        List<Actor> actors = room.getActors();
        List<Actor> affectedActors = new ArrayList<>(actors.size());
        Ellipse2D circle = new Ellipse2D.Double(center.getX()-radius,center.getY()-radius,radius*2,radius*2);
        for(Actor a : actors){
            Point2D location = a.getLocation();
            if(circle.contains(location)) affectedActors.add(a);
        }
        return affectedActors;
    }

    public static List<Actor> getEnemyActorsInRadius(RoomHandler room, int team, Point2D center, float radius){
        List<Actor> actors = room.getActors();
        List<Actor> affectedActors = new ArrayList<>(actors.size());
        Ellipse2D circle = new Ellipse2D.Double(center.getX()-radius,center.getY()-radius,radius*2,radius*2);
        for(Actor a : actors){
            if(a.getTeam() != team && a.getHealth() > 0){
                Point2D location = a.getLocation();
                if(circle.contains(location)) affectedActors.add(a);
            }
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

    public static List<Actor> getUsersInBox(RoomHandler room, Point2D start, double width, double height){
        Rectangle2D box = new Rectangle2D.Double(start.getX()+(width/2),start.getY(),width,height);
        List<Actor> affectedActors = new ArrayList<>();
        for(Actor a : room.getActors()){
            if(box.contains(a.getLocation())) affectedActors.add(a);
        }
        return affectedActors;
    }

    public static List<Actor> getActorsAlongLine(RoomHandler room, Line2D line, double range){
        Point2D[] allPoints = findAllPoints(line);
        List<Actor> affectedActors = new ArrayList<>();
        for(Actor a : room.getActors()){
            for(Point2D p : allPoints){
                if(a.getLocation().distance(p) <= range){
                    affectedActors.add(a);
                    break;
                }
            }
        }
        return affectedActors;
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

    public static Line2D extendLine(Line2D projectileLine, float distance){
        float slope = (float)((projectileLine.getP2().getY() - projectileLine.getP1().getY())/(projectileLine.getP2().getX()-projectileLine.getP1().getX()));
        float intercept = (float)(projectileLine.getP2().getY()-(slope*projectileLine.getP2().getX()));
        float deltaX = (float) (projectileLine.getX2()-projectileLine.getX1());
        float x = (float)projectileLine.getP2().getX()+(distance);
        if (deltaX < 0) x = (float)projectileLine.getX2()-distance;
        float y = slope*x + intercept;
        Point2D newPoint = new Point2D.Float(x,y);
        return new Line2D.Float(projectileLine.getP2(),newPoint);
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

    public static List<Actor> getActorsWithinCone(RoomHandler roomHandler, Point2D tip, Point2D direction, float length, float width){ //TODO: Needs to be more accurate
        Point2D midpoint = Champion.getDistanceLine(new Line2D.Float(tip,direction),length).getP2();
        Point2D directPoint = new Point2D.Double((tip.getX()-midpoint.getX())/Math.abs(tip.getX()-midpoint.getX()),(tip.getY()-midpoint.getY())/Math.abs(tip.getY()-midpoint.getY()));
        Point2D orthogonalPoint = new Point2D.Double(directPoint.getY()*-1,directPoint.getX());
        Point2D bPoint = new Point2D.Double((width/2)*orthogonalPoint.getX() + midpoint.getX(), (width/2)*orthogonalPoint.getY() + midpoint.getY());
        Point2D cPoint = new Point2D.Double(((width*-1)/2)*orthogonalPoint.getX() + midpoint.getX(), ((width*-1)/2)*orthogonalPoint.getY() + midpoint.getY());
        ATBPExtension parentExt = roomHandler.getActors().get(0).getParentExt();
        Room room = roomHandler.getActors().get(0).getRoom();
        List<Actor> affectedUsers = new ArrayList<>();
        for(Actor a : roomHandler.getActors()){
            if(isPointInsideTriangle(tip,bPoint,cPoint,a.getLocation())) affectedUsers.add(a);
        }
        return affectedUsers;
    }

    private static boolean isPointInsideTriangle(Point2D vertex1, Point2D vertex2, Point2D vertex3, Point2D point) {
        double areaOfTriangle = calculateTriangleArea(vertex1, vertex2, vertex3);
        double area1 = calculateTriangleArea(point, vertex2, vertex3);
        double area2 = calculateTriangleArea(vertex1, point, vertex3);
        double area3 = calculateTriangleArea(vertex1, vertex2, point);

        return areaOfTriangle == area1 + area2 + area3;
    }

    private static double calculateTriangleArea(Point2D vertex1, Point2D vertex2, Point2D vertex3) {
        return Math.abs((vertex1.getX() * (vertex2.getY() - vertex3.getY()) +
                vertex2.getX() * (vertex3.getY() - vertex1.getY()) +
                vertex3.getX() * (vertex1.getY() - vertex2.getY())) / 2.0);
    }


    public static class DelayedAttack implements Runnable{

        Actor attacker;
        Actor target;
        int damage;
        ATBPExtension parentExt;
        String attack;

        public DelayedAttack(ATBPExtension parentExt, Actor attacker, Actor target, int damage, String attack){
            this.attacker = attacker;
            this.target = target;
            this.damage = damage;
            this.parentExt = parentExt;
            this.attack = attack;
        }

        @Override
        public void run() {
            JsonNode attackData;
            if(this.attacker.getActorType() == ActorType.MINION) attackData = this.parentExt.getAttackData(this.attacker.getAvatar().replace("0",""),this.attack);
            else attackData = this.parentExt.getAttackData(this.attacker.getAvatar(),this.attack);
            if(this.attacker.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) this.attacker;
                if(ua.hasBackpackItem("junk_1_numb_chucks") && ua.getStat("sp_category1") > 0){
                    if(!this.target.hasTempStat("attackSpeed")) this.target.handleEffect("attackSpeed",this.target.getPlayerStat("attackSpeed")*-0.1,3000,"numb_chucks");
                }else if(ua.hasBackpackItem("junk_4_grob_gob_glob_grod") && ua.getStat("sp_category4") > 0){
                    if(!this.target.hasTempStat("spellDamage")) this.target.handleEffect("spellDamage",this.target.getPlayerStat("spellDamage")*-0.1,3000,"grob_gob");
                }
            }
            if(this.target.getActorType() == ActorType.PLAYER){
                UserActor user = (UserActor) this.target;
                if(user.damaged(attacker,damage,attackData) && this.attacker.getActorType() == ActorType.TOWER){
                    Tower t = (Tower) attacker;
                    t.resetTarget(target);
                }
            }
            else if(target.damaged(attacker,damage,attackData) && this.attacker.getActorType() == ActorType.TOWER){
                Tower t = (Tower) attacker;
                t.resetTarget(target);
            }
            if(attacker.getActorType() == ActorType.MONSTER && !attacker.getId().contains("_")) attacker.setCanMove(true);
        }
    }

    public static class DelayedRangedAttack implements Runnable {
        Actor attacker;
        Actor target;

        public DelayedRangedAttack(Actor a, Actor t){
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

        UserActor deadActor;

        public RespawnCharacter(UserActor a){
            this.deadActor = a;
        }
        @Override
        public void run() {
            deadActor.respawn();
        }
    }
    public static class FinalBuffHandler implements Runnable {

        String buff;
        int duration = 0;
        double delta;
        double modifiedDelta = 0;
        Actor a;
        long started;
        String fxName;
        boolean isState = false;

        public FinalBuffHandler(Actor a, ActorState state, double delta){
            this.a = a;
            this.delta = delta;
            this.buff = state.name().toLowerCase();
            this.isState = true;
            this.started = System.currentTimeMillis();
            a.setBuffHandler(buff,this);
        }

        public FinalBuffHandler(Actor a, String buff, double delta){
            this.a = a;
            this.buff = buff;
            this.delta = delta;
            this.started = System.currentTimeMillis();
            a.setBuffHandler(buff,this);
        }

        public FinalBuffHandler(Actor a, String buff, double delta, String fxName){
            System.out.println("Created buff handler with effects!");
            this.a = a;
            this.buff = buff;
            this.delta = delta;
            this.fxName = fxName;
            this.started = System.currentTimeMillis();
            a.setBuffHandler(buff,this);
        }

        @Override
        public void run() {
            if(!this.isState){
                if(this.duration > 0){
                    int runTime = (int) Math.floor(duration - (System.currentTimeMillis()-started));
                    double statChange = this.getDelta();
                    if(modifiedDelta != 0 && modifiedDelta > delta){
                        a.setTempStat(buff, delta*-1);
                        statChange = modifiedDelta - delta;
                    }
                    if(this.fxName != null){
                        ExtensionCommands.createActorFX(a.getParentExt(),a.getRoom(),a.getId(),fxName,runTime,a.getId()+"_"+fxName,true,"Bip01",true,true,a.getTeam());
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinalBuffHandler(a,buff,statChange,fxName),runTime,TimeUnit.MILLISECONDS);
                    }else{
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinalBuffHandler(a,buff,statChange),runTime,TimeUnit.MILLISECONDS);
                    }
                }else{
                    System.out.println("Buff ended");
                    a.setTempStat(buff,delta*-1);
                    a.removeBuffHandler(this.buff);
                }
                if(this.fxName != null) this.handleIcons();
            }else{
                switch(this.buff){
                    case "polymorph":
                        a.setState(ActorState.POLYMORPH,false);
                        ExtensionCommands.swapActorAsset(a.getParentExt(),a.getRoom(),a.getId(),a.getAvatar());
                        a.setTempStat("speed",delta*-1);
                        break;
                }
            }
        }

        public void extendBuff(int duration){
            this.started = System.currentTimeMillis();
            this.duration+= duration;
        }

        public void setDuration(int duration){
            this.started = System.currentTimeMillis();
            this.duration = duration;
        }
        public void setDelta(double delta){
            this.modifiedDelta = delta;
        }

        public int getDuration(){
            return this.duration;
        }

        public double getDelta(){
            if(this.modifiedDelta != 0) return modifiedDelta;
            else return this.delta;
        }

        private void handleIcons(){
            if(this.fxName.contains("altar")){
                UserActor ua = (UserActor) this.a;
                String altarType = this.fxName.split("_")[2];
                ExtensionCommands.removeStatusIcon(ua.getParentExt(),ua.getUser(),"altar_buff_"+altarType);
            }
        }
    }

}



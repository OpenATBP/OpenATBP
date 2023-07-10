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
import xyz.openatbp.extension.game.champions.BubbleGum;
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
            case "princessbubblegum":
                return new BubbleGum(u,parentExt);
        }
        return new UserActor(u, parentExt);
    }

    public static JsonNode getSpellData(ATBPExtension parentExt, String avatar, int spell){
        JsonNode actorDef = parentExt.getDefinition(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell"+spell);
    }

    public static Point2D getDashPoint(ATBPExtension parentExt, Actor actor, Point2D dest){
        String room = actor.getRoom().getGroupId();
        Line2D movementLine = new Line2D.Float(actor.getLocation(),dest);
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
        for(Actor a : actors){
            Point2D location = a.getLocation();
            if(location.distance(center) <= radius/2) affectedActors.add(a);
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
                if(a.getLocation().distance(p) <= range && facingEntity(line.getP1(),line.getP2())){
                    affectedActors.add(a);
                    break;
                }
            }
        }
        return affectedActors;
    }

    private static boolean facingEntity(Point2D p1, Point2D p2){ // Returns true if the point is in the same direction
        double deltaX = p2.getX()-p1.getX();
        //Negative = left Positive = right
        if(Double.isNaN(deltaX)) return false;
        if(deltaX>0 && p2.getX()>p1.getX()) return true;
        else return deltaX < 0 && p2.getX() < p1.getX();
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
        double angle = Math.atan2(projectileLine.getY2() - projectileLine.getY1(),projectileLine.getX2() - projectileLine.getX1());
        double extendedX = projectileLine.getX2() + distance * Math.cos(angle);
        double extendedY = projectileLine.getY2() + distance * Math.sin(angle);
        return new Line2D.Double(projectileLine.getP1(),new Point2D.Double(extendedX,extendedY));
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

    public static Point2D getTeleportPoint(ATBPExtension parentExt, User user, Point2D location, Point2D dest){
        ArrayList<Path2D> colliderPaths = parentExt.getMapPaths("main");
        for(int i = 0; i < colliderPaths.size(); i++){
            if(colliderPaths.get(i).contains(dest)){
                System.out.println("Point clashes at i: " + i);
                Path2D path = colliderPaths.get(i);
                Rectangle2D bounds = path.getBounds2D();
                Point2D topRight = new Point2D.Double(bounds.getMaxX(),bounds.getMaxY());
                Point2D topLeft = new Point2D.Double(bounds.getMinX(),bounds.getMaxY());
                Point2D bottomLeft = new Point2D.Double(bounds.getMinX(),bounds.getMinY());
                Point2D bottomRight = new Point2D.Double(bounds.getMaxX(),bounds.getMinY());

                double closestDistance = 1000d;
                Point2D closestPoint = new Point2D.Double(location.getX(),location.getY());

                for(double g = bottomLeft.getY(); g < topLeft.getY(); g+=(topLeft.getY()/10)){
                    Point2D testPoint = new Point2D.Double(topLeft.getX(),g);
                    if(testPoint.distance(dest) < closestDistance){
                        closestDistance = testPoint.distance(dest);
                        closestPoint = testPoint;
                    }
                }

                for(double g = bottomRight.getY(); g < topRight.getY(); g+=(topRight.getY()/10)){
                    Point2D testPoint = new Point2D.Double(topRight.getX(),g);
                    if(testPoint.distance(dest) < closestDistance){
                        closestDistance = testPoint.distance(dest);
                        closestPoint = testPoint;
                    }
                }

                return closestPoint;
            }
        }
        return dest;
    }

    public static HashMap<ActorState, Boolean> getBlankStates(){
        HashMap<ActorState, Boolean> states = new HashMap<>(ActorState.values().length);
        for(ActorState s : ActorState.values()){
            states.put(s,false);
        }
        return states;
    }

    public static Line2D getColliderLine(ATBPExtension parentExt, Room room, Line2D movementLine){
        boolean intersects = false;
        int mapPathIndex = -1;
        float closestDistance = 100000;
        ArrayList<Vector<Float>>[] colliders = parentExt.getColliders(room.getGroupId()); //Gets all collision object vertices
        ArrayList<Path2D> mapPaths = parentExt.getMapPaths(room.getGroupId()); //Gets all created paths for the collision objects
        Point2D intersectionPoint = new Point2D.Float(-1,-1);
        for(int i = 0; i < mapPaths.size(); i++){ //Search through all colliders
            if(mapPaths.get(i).intersects(movementLine.getBounds())){ //If the player's movement intersects a collider
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
                        intersects = true;
                        Point2D intPoint = getIntersectionPoint(movementLine,colliderLine);
                        float dist = (float)movementLine.getP1().distance(intPoint);
                        if(dist<closestDistance){ //If the player intersects two objects, this chooses the closest one.
                            mapPathIndex = i;
                            closestDistance = dist;
                            intersectionPoint = intPoint;
                        }

                    }
                }
            }
        }
        float destx = (float)movementLine.getX2();
        float destz = (float)movementLine.getY2();
        if(intersects){ //If the player hits an object, find where they should end up
            Point2D finalPoint = collidePlayer(new Line2D.Double(movementLine.getX1(),movementLine.getY1(),intersectionPoint.getX(),intersectionPoint.getY()),mapPaths.get(mapPathIndex));
            destx = (float)finalPoint.getX();
            destz = (float)finalPoint.getY();
        }
        Point2D finalPoint = new Point2D.Float(destx,destz);
        return new Line2D.Float(movementLine.getP1(),finalPoint);
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
            if(this.target.getHealth() <= 0) return;
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
                if(this.attack.contains("basic")) ua.handleLifeSteal();
                else if(this.attack.contains("spell")) ua.handleSpellVamp(this.damage);
            }
            /*
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

             */
            if(attacker.getActorType() == ActorType.MONSTER && !attacker.getId().contains("_")) attacker.setCanMove(true);
            this.target.addToDamageQueue(this.attacker,this.damage,attackData);
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
        boolean cancelled = false;

        public FinalBuffHandler(Actor a, ActorState state, double delta){
            this.a = a;
            this.delta = delta;
            this.buff = state.name().toLowerCase();
            this.isState = true;
            this.started = System.currentTimeMillis();
        }

        public FinalBuffHandler(Actor a, ActorState state, double delta, String fxName){
            this.a = a;
            this.delta = delta;
            this.buff = state.name().toLowerCase();
            this.isState = true;
            this.fxName = fxName;
            this.started = System.currentTimeMillis();
        }

        public FinalBuffHandler(Actor a, String buff, double delta){
            this.a = a;
            this.buff = buff;
            this.delta = delta;
            this.started = System.currentTimeMillis();
        }

        public FinalBuffHandler(Actor a, String buff, double delta, String fxName){
            System.out.println("Created buff handler with effects!");
            this.a = a;
            this.buff = buff;
            this.delta = delta;
            this.fxName = fxName;
            this.started = System.currentTimeMillis();
        }

        @Override
        public void run() {
            if(this.cancelled){
                return;
            }
            if(!this.isState){
                if(this.duration > 0){
                    System.out.println("Remaining duration = " + this.duration + " for " + a.getId());
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
                System.out.println("Buff is a state!");
                switch(this.buff){
                    case "polymorph":
                        this.handleBuffRun("speed",ActorState.POLYMORPH);
                        if(duration == 0) ExtensionCommands.swapActorAsset(a.getParentExt(),a.getRoom(),a.getId(),a.getAvatar());
                        break;
                    case "slowed":
                        this.handleBuffRun("speed", ActorState.SLOWED);
                        break;
                    case "charmed":
                        a.setState(ActorState.CHARMED,false);
                        a.removeBuffHandler(this.buff);
                        break;
                    default:
                        a.setState(ActorState.valueOf(this.buff.toUpperCase()),false);
                        a.removeBuffHandler(this.buff);
                        break;
                }
            }
        }

        private void handleBuffRun(String stat, ActorState state){
            if(duration > 0){
                int runTime = (int) Math.floor(duration - (System.currentTimeMillis()-started));
                double statChange = this.getDelta();
                if(modifiedDelta != 0 && modifiedDelta > delta){
                    a.setTempStat(stat, delta*-1);
                    statChange = modifiedDelta - delta;
                }
                System.out.println("State Buff running for " + runTime);
                if(this.fxName != null){
                    ExtensionCommands.createActorFX(a.getParentExt(),a.getRoom(),a.getId(),fxName,runTime,a.getId()+"_"+fxName,true,"Bip01",true,true,a.getTeam());
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinalBuffHandler(a,state,statChange,fxName),runTime,TimeUnit.MILLISECONDS);
                }else{
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinalBuffHandler(a,state,statChange),runTime,TimeUnit.MILLISECONDS);
                }
            }else{
                System.out.println("State Buff ended");
                a.setTempStat(stat,delta*-1);
                a.setState(state,false);
                a.removeBuffHandler(this.buff);
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
            System.out.println("Handling icons: " + this.fxName);
            if(this.fxName.contains("altar")){
                UserActor ua = (UserActor) this.a;
                String altarType = this.fxName.split("_")[0];
                if(altarType.equalsIgnoreCase("attack")) altarType = "offense";
                ExtensionCommands.removeStatusIcon(ua.getParentExt(),ua.getUser(),"altar_buff_"+altarType);
            }else if(this.fxName.contains("goo")){
                if(this.a.getActorType() == ActorType.PLAYER){
                    UserActor ua = (UserActor) this.a;
                    ExtensionCommands.removeStatusIcon(ua.getParentExt(),ua.getUser(),"Ooze Buff");
                }
            }else if(this.fxName.contains("keeoth")){
                if(this.a.getActorType() == ActorType.PLAYER){
                    UserActor ua = (UserActor) this.a;
                    ExtensionCommands.removeStatusIcon(ua.getParentExt(),ua.getUser(),"Keeoth Buff");
                }
            }
        }

        public void cancel(double delta){
            this.cancelled = true;
            if(this.fxName != null){
                this.handleIcons();
                ExtensionCommands.removeFx(a.getParentExt(), a.getRoom(), a.getId()+"_"+fxName);
            }
            if(this.isState){
                if(this.buff.equalsIgnoreCase("polymorph")){
                    ExtensionCommands.swapActorAsset(a.getParentExt(),a.getRoom(),a.getId(),a.getAvatar());
                    a.setState(ActorState.POLYMORPH,false);
                    a.setTempStat("speed",delta*-1);
                }else if(this.buff.equalsIgnoreCase("slowed")) {
                    a.setState(ActorState.SLOWED, false);
                    a.setTempStat("speed",delta*-1);
                }else{
                    a.setState(ActorState.valueOf(this.buff),false);
                }
            }else{
                a.setTempStat(this.buff,delta*-1);
            }
        }

    }

}



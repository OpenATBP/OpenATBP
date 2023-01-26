package xyz.openatbp.extension.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.SFSUser;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.champions.FlamePrincess;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class Champion {

    @Deprecated
    public static void attackChampion(ATBPExtension parentExt, User player, String attacker, int damage){ //Used for melee attacks TODO: Move over to one function as this likely does not work with multiplayer
        ExtensionCommands.damageActor(parentExt,player, String.valueOf(player.getId()),damage);
        ISFSObject stats = player.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(player.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,player,updateData);
        }else{
            handleDeath(parentExt,player,attacker); //TODO: Implement player death
        }

    }

    public static void attackChampion(ATBPExtension parentExt, User u, User target, String attacker, int damage){ //Used for ranged attacks
        ExtensionCommands.damageActor(parentExt,u, String.valueOf(target.getId()),damage);
        ISFSObject stats = target.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(target.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }else{
            handleDeath(parentExt,u,attacker);
        }
    }

    public static void attackMinion(ATBPExtension parentExt, String attacker, Minion m, int damage){
        System.out.println(attacker + " attacking " + m.getId() + "!");
        float currentHealth = m.getHealth()-damage;
        if(m.damage(parentExt,attacker,damage)){ //Minion dies
            System.out.println("Minion dead!");
        }else{
            float maxHealth = m.getMaxHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id",m.getId());
            updateData.putInt("currentHealth",(int) currentHealth);
            updateData.putDouble("pHealth",pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            for(User u : m.getRoomUsers()){
                ExtensionCommands.updateActorData(parentExt,u,updateData);
            }
        }
    }


    //TODO: Implement player death
    private static void handleDeath(ATBPExtension parentExt, User player, String attacker){
        Champion.setHealth(parentExt,player,0);
        ExtensionCommands.knockOutActor(parentExt,player, String.valueOf(player.getId()),attacker,10);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RespawnCharacter(parentExt,player.getLastJoinedRoom(),String.valueOf(player.getId())),10,TimeUnit.SECONDS);
    }

    public static void rangedAttackChampion(ATBPExtension parentExt, Room room, String attacker, String target, int damage){
        //Handles the damage after the projectile animation is finished
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,target),500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){ //Skins don't have their own projectile effect so we have to pull from the base
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            //TODO: Make more accurate emit & hit locations
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,target,"Bip001","Bip001",(float)0.5);
        }
    }

    public static void attackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles attacking the tower
        boolean towerDown = tower.damage(damage); // Returns true if tower is destroyed
        boolean notify = System.currentTimeMillis()-tower.getLastHit() >= 1000*5; //Returns true if we should notify players of a tower being hit
        for(User u : room.getUserList()){
            if(notify) ExtensionCommands.towerAttacked(parentExt,u, tower.getTowerNum());
            if(towerDown){ // Tower is dead
                handleTowerDeath(parentExt,u,tower,attacker);
            }
            float maxHealth = tower.getMaxHealth();
            float currentHealth = tower.getHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", tower.getId());
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        if(notify) tower.triggerNotification(); //Resets tower notification time
    }

    public static void attackBase(ATBPExtension parentExt, Room room, String attacker, Base base, int damage){
        boolean gameEnded = base.damage(damage);
        for(User u : room.getUserList()){
            if(gameEnded){ //Handle end of game
                double oppositeTeam = 0;
                if(base.getTeam() == 0) oppositeTeam = 1;
                System.out.println("Game ended!");
                try{
                    ExtensionCommands.gameOver(parentExt,u,oppositeTeam);
                }catch(Exception e){
                    e.printStackTrace();
                    System.out.println(e);
                }
            }
            float maxHealth = Base.MAX_HEALTH;
            float currentHealth = base.getHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", base.getId());
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
    }

    private static void handleTowerDeath(ATBPExtension parentExt, User u, Tower tower, String attacker){ //Handles tower death
        ExtensionCommands.towerDown(parentExt,u, tower.getTowerNum());
        ExtensionCommands.knockOutActor(parentExt,u,tower.getId(),attacker,100);
        if(!tower.isDestroyed()){
            tower.destroy();
            ExtensionCommands.destroyActor(parentExt,u,tower.getId());
        }
        String actorId = "tower2a";
        if(tower.getTowerNum() == 0 || tower.getTowerNum() == 3 ){
            actorId = "tower1a";
        }
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),actorId,tower.getId()+"_destroyed",1000*60*15,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),"tower_destroyed_explosion",tower.getId()+"_destroyed_explosion",1000,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        Room room = u.getLastJoinedRoom();
        ISFSObject scoreObj = room.getVariable("score").getSFSObjectValue();
        int teamA = scoreObj.getInt("purple");
        int teamB = scoreObj.getInt("blue");
        if(tower.getTeam() == 0) teamB+=50;
        else teamA+=50;
        scoreObj.putInt("purple",teamA);
        scoreObj.putInt("blue",teamB);
        ExtensionCommands.updateScores(parentExt,u,teamA,teamB);
    }

    public static void rangedAttackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles ranged attacks against tower
        //Schedules damage after projectile hits target
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,tower),500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,tower.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void rangedAttackMinion(ATBPExtension parentExt, Room room, String attacker, Minion m, int damage){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,m), 500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,m.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void rangedAttackBase(ATBPExtension parentExt, Room room, String attacker, Base base, int damage){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,base), 500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,base.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void handleMinionDeath(ATBPExtension parentExt, User u, String attacker, Minion m){
        System.out.println("Dying!");
        ExtensionCommands.knockOutActor(parentExt,u,m.getId(),attacker,0);
        ExtensionCommands.destroyActor(parentExt,u,m.getId());
        if(!attacker.contains("creep") & !attacker.contains("tower")){ // Attacker is a player

        }
    }

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

    public static void setHealth(ATBPExtension parentExt, User u, int health){
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        double currentHealth = health;
        double maxHealth = stats.getInt("maxHealth");
        if(currentHealth>maxHealth) currentHealth = maxHealth;
        else if(currentHealth<0) currentHealth = 0;
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

    public static UserActor getCharacterClass(User u, ATBPExtension parentExt){
        String avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        String character = avatar.split("_")[0];
        switch(character){
            case "flame":
                return new FlamePrincess(u,parentExt);
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

    public static List<UserActor> getUsersInRadius(RoomHandler room, Point2D center, float radius){
        List<UserActor> allUsers = room.getPlayers();
        List<UserActor> affectedUsers = new ArrayList<>(allUsers.size());
        Ellipse2D circle = new Ellipse2D.Double(center.getX()-radius,center.getY()-radius,radius*2,radius*2);
        System.out.println("Circle center: " + circle.getCenterX() + "," + circle.getCenterY());
        for(UserActor user : allUsers){
            Point2D location = user.getLocation();
            if(circle.contains(location)) affectedUsers.add(user);
        }
        return affectedUsers;
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
}

class RangedAttack implements Runnable{ //Handles damage from ranged attacks
    ATBPExtension parentExt;
    Room room;
    int damage;
    String target;
    Tower tower;
    String attacker;
    Minion minion;
    Base base;

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, String target){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = target;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Tower tower){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = tower.getId();
        this.tower = tower;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Minion m){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = m.getId();
        this.minion = m;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Base b){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = b.getId();
        this.base = b;
        this.attacker = attacker;
    }
    @Override
    public void run() {
        if(target.contains("tower") && tower != null){
            Champion.attackTower(parentExt,room,attacker,tower,damage);
        }else if(target.contains("creep") && minion != null){
            Champion.attackMinion(parentExt,attacker,minion,damage);
        }else if(target.contains("base") && base != null){
            Champion.attackBase(parentExt,room,attacker,base,damage);
        }else{
            User p = room.getUserById(Integer.parseInt(target));
            for(User u : room.getUserList()){
                Champion.attackChampion(parentExt,u,p,attacker,damage);
            }
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

class RespawnCharacter implements  Runnable {

    String id;
    ATBPExtension parentExt;
    Room room;

    RespawnCharacter(ATBPExtension parentExt, Room room, String id){
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

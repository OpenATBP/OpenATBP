package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.champions.Fionna;
import xyz.openatbp.extension.reqhandlers.HitActorHandler;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UserActor extends Actor {

    protected User player;
    protected boolean autoAttackEnabled = false;
    protected int xp = 0;
    private int deathTime = 10;
    protected boolean dead = false;
    private long timeKilled;
    protected Map<Actor,ISFSObject> aggressors = new HashMap<>();
    protected String backpack;
    protected int futureCrystalTimer = 240;
    protected int nailDamage = 0;
    protected int moonTimer = 120;
    protected boolean moonActivated = false;
    protected Map<String,Double> endGameStats = new HashMap<>();
    protected int killingSpree = 0;
    protected int multiKill = 0;
    protected long lastKilled = System.currentTimeMillis();
    protected int dcBuff = 0;
    protected boolean[] canCast = {true,true,true};
    protected Map<String, ScheduledFuture<?>> iconHandlers = new HashMap<>();
    protected int idleTime = 0;
    protected static final double DASH_SPEED = 20d;
    private static final boolean MOVEMENT_DEBUG = false;
    private static final boolean INVINCIBLE_DEBUG = false;
    private static final boolean ABILITY_DEBUG = false;

    //TODO: Add all stats into UserActor object instead of User Variables
    public UserActor(User u, ATBPExtension parentExt){
        this.parentExt = parentExt;
        this.id = String.valueOf(u.getId());
        this.team = u.getVariable("player").getSFSObjectValue().getInt("team");
        player = u;
        this.avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        this.displayName = u.getVariable("player").getSFSObjectValue().getUtfString("name");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x,z);
        this.movementLine = new Line2D.Float(this.location,this.location);
        this.stats = this.initializeStats();
        this.attackCooldown = this.stats.get("attackSpeed");
        this.currentHealth = this.stats.get("health");
        this.maxHealth = this.currentHealth;
        this.room = u.getLastJoinedRoom();
        this.actorType = ActorType.PLAYER;
        this.backpack = u.getVariable("player").getSFSObjectValue().getUtfString("backpack");
        if(MOVEMENT_DEBUG) ExtensionCommands.createActor(this.parentExt,this.room,this.id+"_movementDebug","creep1",this.location,0f,2);
    }

    public void setAutoAttackEnabled(boolean enabled){
        this.autoAttackEnabled = enabled;
    }

    protected Point2D getRelativePoint(boolean external){ //Gets player's current location based on time
        double currentTime;
        if(external) currentTime = this.timeTraveled + 0.1;
        else currentTime = this.timeTraveled;
        Point2D rPoint = new Point2D.Float();
        if(this.movementLine == null) this.movementLine = new Line2D.Float(this.location,this.location);
        float x2 = (float) this.movementLine.getX2();
        float y2 = (float) this.movementLine.getY2();
        float x1 = (float) this.movementLine.getX1();
        float y1 = (float) this.movementLine.getY1();
        double dist = this.movementLine.getP1().distance(this.movementLine.getP2());
        if(dist == 0) return this.movementLine.getP1();
        double speed = this.getPlayerStat("speed");
        double time = dist/speed;
        if(currentTime>time) currentTime=time;
        double currentDist = speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        if(x >= 52) x = 52;
        else if(x <= -52) x = -52;
        if(y >= 30) y = 30;
        else if(y <= -30) y = -30;
        rPoint.setLocation(x,y);
        this.location = rPoint;
        if(x >= 52 || x <= -52 || y >= 30 || y <= -30) this.stopMoving();
        return rPoint;
    }

    @Override
    public Room getRoom() {
        return this.room;
    }

    public Map<String, Double> getStats(){
        return this.stats;
    }

    public double getStat(String stat){
        return this.stats.get(stat);
    }


    @Override
    public boolean setTempStat(String stat, double delta){
        boolean returnVal = super.setTempStat(stat,delta);
        if(stat.contains("speed") && this.canMove){
            this.move(movementLine.getP2());
        }else if(stat.contains("healthRegen")){
            if(this.hasTempStat("healthRegen") && this.getTempStat("healthRegen") <= 0) this.tempStats.remove("healthRegen");
        }
        parentExt.trace("Temp Stat for " + stat + " set to " + delta);
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,stat,this.getPlayerStat(stat));
        return returnVal;
    }

    public void setPath(Point2D start, Point2D end){
        this.movementLine = new Line2D.Float(start,end);
        this.timeTraveled = 0f;
    }

    public void setPath(Line2D path){
        this.movementLine = path;
        this.timeTraveled = 0f;
    }

    public void updateMovementTime(){
        this.timeTraveled+=0.1f;
    }

    public User getUser(){
        return this.player;
    }

    public void move(ISFSObject params, Point2D destination){
        Point2D orig = new Point2D.Float(params.getFloat("orig_x"),params.getFloat("orig_z"));
        this.location = orig;
        this.movementLine = new Line2D.Float(orig,destination);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,destination, (float) this.getPlayerStat("speed"),params.getBool("orient"));
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try{
            if(INVINCIBLE_DEBUG) return false;
            if(this.dead) return true;
            if(a.getActorType() == ActorType.TOWER){
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_turret_shot_hits_you",this.location);
            }
            Actor.AttackType type = this.getAttackType(attackData);
            if(this.states.get(ActorState.BRUSH)){
                Runnable runnable = () -> UserActor.this.setState(ActorState.REVEALED,false);
                this.setState(ActorState.REVEALED,true);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(runnable,3000,TimeUnit.MILLISECONDS);
            }
            if(!moonActivated && this.hasBackpackItem("junk_3_battle_moon") && this.getStat("sp_category3") > 0 && type == AttackType.PHYSICAL){
                int timer = (int) (130 - (10*getPlayerStat("sp_category3")));
                if(moonTimer > timer){
                    this.moonActivated = true;
                    int duration = (int) (3 + (1*getPlayerStat("sp_category3")));
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new BattleMoon(),duration,TimeUnit.SECONDS);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_junk_battle_moon",duration*1000,this.id+"_battleMoon",true,"Bip01 Head",false,false,this.team);
                    ArrayList<User> users = new ArrayList<>();
                    users.add(this.player);
                    if(a.getActorType() == ActorType.PLAYER){
                        UserActor ua = (UserActor) a;
                        users.add(ua.getUser());
                    }
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_junk_battle_moon",this.getRelativePoint(false));
                    return false;
                }
            }else if(moonActivated && type == AttackType.PHYSICAL){
                ArrayList<User> users = new ArrayList<>();
                users.add(this.player);
                if(a.getActorType() == ActorType.PLAYER){
                    UserActor ua = (UserActor) a;
                    users.add(ua.getUser());
                }
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_junk_battle_moon",this.getRelativePoint(false));
                return false;
            }
            int newDamage = this.getMitigatedDamage(damage,type,a);
            if(a.getActorType() == ActorType.PLAYER) this.addDamageGameStat((UserActor) a, newDamage,type);
            this.handleDamageTakenStat(type,newDamage);
            ExtensionCommands.damageActor(parentExt,this.room,this.id,newDamage);
            this.processHitData(a,attackData,newDamage);
            if(this.hasTempStat("healthRegen")) this.tempStats.remove("healthRegen");
            this.changeHealth(newDamage*-1);
            if(this.currentHealth > 0) return false;
            else{
                if(this.getClass() == Fionna.class){
                    Fionna f = (Fionna) this;
                    if(f.ultActivated()){
                        this.setHealth(1, (int) this.maxHealth);
                        return false;
                    }
                }
                if(this.hasBackpackItem("junk_4_future_crystal") && this.getStat("sp_category4") > 0){
                    double points = (int) this.getStat("sp_category4");
                    int timer = (int) (250 - (10*points));
                    if(this.futureCrystalTimer >= timer){
                        double healthPerc = (5+(5*points))/100;
                        double health = Math.floor(this.maxHealth*healthPerc);
                        this.changeHealth((int) health);
                        this.futureCrystalTimer = 0;
                        return false;
                    }
                }
                return true;
            }
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }

    }

    public double getAttackCooldown(){
        return this.attackCooldown;
    }

    @Override
    public void attack(Actor a) {
        if(this.attackCooldown == 0){
            double critChance = this.getPlayerStat("criticalChance")/100d;
            double random = Math.random();
            boolean crit = random < critChance;
            System.out.println("Crit: " + critChance + " vs " + random + " | " + crit);
            ExtensionCommands.attackActor(parentExt,room,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),crit,true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            double damage = this.getPlayerStat("attackDamage");
            if(crit) damage*=2;
            Champion.DelayedAttack delayedAttack = new Champion.DelayedAttack(parentExt,this,a,(int)damage,"basicAttack");
            try{
                String projectileFx = this.parentExt.getActorData(this.getAvatar()).get("scriptData").get("projectileAsset").asText();
                if(projectileFx != null && projectileFx.length() > 0 && !parentExt.getActorData(this.avatar).get("attackType").asText().equalsIgnoreCase("MELEE")) SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a,delayedAttack,projectileFx),500,TimeUnit.MILLISECONDS);
                else delayedAttack.run();
            }catch(NullPointerException e){
                delayedAttack.run();
            }
        }
    }

    public Point2D dash(Point2D dest, boolean noClip){
        Point2D dashPoint = MovementManager.getDashPoint(this,new Line2D.Float(this.location,dest));
        if(dashPoint == null) dashPoint = this.location;
        System.out.println("Dash: " + dashPoint);
        if(MOVEMENT_DEBUG) ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"gnome_a",this.id+"_test"+Math.random(),5000,(float)dashPoint.getX(),(float)dashPoint.getY(),false,0,0f);
        //if(noClip) dashPoint = Champion.getTeleportPoint(this.parentExt,this.player,this.location,dest);
        double time = dashPoint.distance(this.location)/DASH_SPEED;
        System.out.println("Time stopped: " + time);
        this.stopMoving((int)(time*1000d));
        ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,dashPoint, (float) DASH_SPEED,true);
        this.setLocation(dashPoint);
        this.target = null;
        return dashPoint;
    }

    public Point2D fpDash(Point2D dest){
        Point2D dashPoint = MovementManager.getDashPoint(this,new Line2D.Float(this.location,dest));
        double time = dashPoint.distance(this.location)/16d;
        this.stopMoving((int)(time*1000d));
        ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,dashPoint, (float) 16d,true);
        this.setLocation(dashPoint);
        this.target = null;
        return dashPoint;
    }

    protected boolean handleAttack(Actor a){ //To be used if you're not using the standard DelayedAttack Runnable
        if(this.attackCooldown == 0){
            double critChance = this.getPlayerStat("criticalChance")/100d;
            double random = Math.random();
            boolean crit = random < critChance;
            System.out.println("Crit: " + critChance + " vs " + random + " | " + crit);
            ExtensionCommands.attackActor(parentExt,room,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),crit,true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            return crit;
        }
        return false;
    }

    public void autoAttack(Actor a){
        this.stopMoving(250);
        this.attack(a);
    }

    public void reduceAttackCooldown(){
        this.attackCooldown-=100;
        if(this.attackCooldown < 0) this.attackCooldown = 0;
    }

    protected boolean isNonStructure(Actor a){
        return a.getTeam() != this.team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE;
    }

    @Override
    public void die(Actor a) {
        System.out.println(this.id + " has died! " + this.dead);
        try{
            if(this.dead) return;
            this.dead = true;
            this.timeKilled = System.currentTimeMillis();
            this.canMove = false;
            this.stopMoving();
            this.setHealth(0, (int) this.maxHealth);
            this.target = null;
            this.killingSpree = 0;
            ExtensionCommands.knockOutActor(parentExt,room, String.valueOf(player.getId()),a.getId(),this.deathTime);
            if(this.nailDamage > 0) this.nailDamage/=2;
            try{
                ExtensionCommands.handleDeathRecap(parentExt,player,this.id,a.getId(), (HashMap<Actor, ISFSObject>) this.aggressors);
                this.increaseStat("deaths",1);
                if(this.hasGameStat("spree")){
                    if(this.killingSpree > this.getGameStat("spree")) this.endGameStats.put("spree", (double) this.killingSpree);
                    this.killingSpree = 0;
                }
                if(a.getActorType() == ActorType.PLAYER){
                    UserActor ua = (UserActor) a;
                    ua.increaseStat("kills",1);
                    if(ua.hasBackpackItem("junk_1_magic_nail") && ua.getStat("sp_category1") > 0) ua.addNailStacks(5);
                    this.parentExt.getRoomHandler(this.room.getId()).addScore(ua,ua.getTeam(),25);
                }else{
                    for(UserActor ua : this.parentExt.getRoomHandler(this.room.getId()).getPlayers()){
                        String sound = "announcer/you_are_defeated";
                        if(ua.getTeam() == this.team && !ua.getId().equalsIgnoreCase(this.id)) sound = "announcer/ally_defeated";
                        else if(ua.getTeam() != this.team) sound = "announcer/enemy_defeated";
                        ExtensionCommands.playSound(parentExt,ua.getUser(),"global",sound,new Point2D.Float(0,0));
                    }
                }
                Set<UserActor> assistIds = new HashSet<>(2);
                for(Actor actor : this.aggressors.keySet()){
                    if(actor.getActorType() == ActorType.PLAYER && !actor.getId().equalsIgnoreCase(a.getId())){
                        UserActor ua = (UserActor) actor;
                        ua.increaseStat("assists",1);
                        assistIds.add(ua);
                    }
                }
                //Set<String> buffKeys = this.activeBuffs.keySet();
                this.parentExt.getRoomHandler(this.room.getId()).handleAssistXP(a,assistIds,50); //TODO: Not sure if this is the correct xp value
            }catch(Exception e){
                e.printStackTrace();
            }
            this.addGameStat("timeDead",this.deathTime);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.RespawnCharacter(this),this.deathTime, TimeUnit.SECONDS);
        }catch(Exception e){
            e.printStackTrace();
        }

    }
/*
    Acceptable Keys:
    availableSpellPoints: Integer
    sp_category1
    sp_category2
    sp_category3
    sp_category4
    sp_category5
    kills
    deaths
    assists
    attackDamage
    attackSpeed
    armor
    speed
    spellResist
    spellDamage
    criticalChance
    criticalDamage*
    lifeSteal
    armorPenetration
    coolDownReduction
    spellVamp
    spellPenetration
    attackRange
    healthRegen
 */
    public void updateStat(String key, double value){
        this.stats.put(key,value);
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,key,this.getPlayerStat(key));
    }

    public void increaseStat(String key, double num){
        if(key.equalsIgnoreCase("kills")){
            this.killingSpree+=num;
            this.multiKill++;
            this.lastKilled = System.currentTimeMillis();
                for(UserActor ua : this.parentExt.getRoomHandler(this.room.getId()).getPlayers()){
                    if(ua.getTeam() == this.team){
                        boolean ally = !ua.getId().equalsIgnoreCase(this.id);
                        String sound = ChampionData.getKOSoundEffect(false,ally,this.multiKill,this.killingSpree);
                        ExtensionCommands.playSound(parentExt,ua.getUser(),"global",sound,new Point2D.Float(0,0));
                    }else{
                        String sound = ChampionData.getKOSoundEffect(true,false,this.multiKill,this.killingSpree);
                        ExtensionCommands.playSound(parentExt,ua.getUser(),"global",sound,new Point2D.Float(0,0));
                    }
                }
        }
        this.stats.put(key,this.stats.get(key)+num);
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,key,this.getPlayerStat(key));
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        this.handleActiveEffects();
        if(this.dead){
            if(this.currentHealth > 0 && System.currentTimeMillis() > this.timeKilled + (deathTime* 1500L)) this.respawn();
            else return;
        }
        if(!this.isStopped()){
            this.updateMovementTime();
        }
        this.location = this.getRelativePoint(false);
        if(this.path != null && this.location.distance(this.movementLine.getP2()) <= 0.9d){
            if(this.pathIndex+1 != this.path.size()){
                this.pathIndex++;
                if(MovementManager.insideAnyObstacle(this.parentExt,this.path.get(this.pathIndex))) this.moveWithCollision(this.path.get(this.pathIndex));
                else this.move(this.path.get(this.pathIndex));
            }else{
                this.path = null;
            }
        }
        if(MOVEMENT_DEBUG) ExtensionCommands.moveActor(this.parentExt,this.room,this.id+"_movementDebug",this.location,this.location,5f,false);
        if(this.location.distance(this.movementLine.getP2()) <= 0.01f){
            this.idleTime+=100;
        }
        boolean insideBrush = false;
        for(Path2D brush : this.parentExt.getBrushPaths()){
            if(brush.contains(this.location)){
                insideBrush = true;
                break;
            }
        }
        if(insideBrush){
            if(!this.states.get(ActorState.BRUSH)){
                ExtensionCommands.changeBrush(parentExt,room,this.id, parentExt.getBrushNum(this.location));
                this.setState(ActorState.BRUSH, true);
                this.setState(ActorState.REVEALED, false);
            }
        }else{
            if(this.states.get(ActorState.BRUSH)){
                this.setState(ActorState.BRUSH, false);
                this.setState(ActorState.REVEALED, true);
                ExtensionCommands.changeBrush(parentExt,room,this.id, -1);
            }
        }
        if(this.attackCooldown > 0) this.reduceAttackCooldown();
        if(this.target != null && this.target.getHealth() > 0){
            if(this.withinRange(target) && this.canAttack()){
                this.autoAttack(target);
            }else if(!this.withinRange(target) && this.canMove()){
                double attackRange = this.getPlayerStat("attackRange");
                Line2D movementLine = new Line2D.Float(this.location,target.getLocation());
                //float targetDistance = (float)(target.getLocation().distance(currentPoint)-attackRange);
                //Line2D newPath = Champion.getDistanceLine(movementLine,targetDistance);
                Line2D finalPath = MovementManager.getColliderLine(parentExt,room,movementLine);
                if(finalPath.getP2().distance(this.movementLine.getP2()) > 0.1f){
                    this.move(finalPath.getP2());
                }
            }
        }else{
            if(this.target != null){
                if(this.target.getHealth() <= 0){
                    this.target = null;
                }
            }else if(this.autoAttackEnabled && idleTime > 2000){
                Actor closestTarget = null;
                double closestDistance = 1000;
                for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(room.getId()),this.location,this.parentExt.getActorStats(this.avatar).get("aggroRange").asInt())){
                    if(a.getTeam() != this.team && a.getLocation().distance(this.location) < closestDistance){
                        closestDistance = a.getLocation().distance(this.location);
                        closestTarget = a;
                    }
                }
                this.idleTime = 0;
                this.target = closestTarget;
            }
        }
        if(msRan % 1000 == 0){
            this.futureCrystalTimer++;
            this.moonTimer++;

            //TODO: Move health regen to separate function
            if((this.currentHealth < this.maxHealth && this.aggressors.isEmpty()) || this.getPlayerStat("healthRegen") < 0){
                double healthRegen = this.getPlayerStat("healthRegen");
                if(this.currentHealth + healthRegen <= 0) healthRegen = (this.currentHealth-1)*-1;
                this.changeHealth((int)healthRegen);
            }
            int newDeath = 10+((msRan/1000)/60);
            if(newDeath != this.deathTime) this.deathTime = newDeath;
            if(this.isState(ActorState.POLYMORPH)){
                for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,2)){
                    if(a.getTeam() != this.team){
                        UserActor enemyFP = this.parentExt.getRoomHandler(this.room.getId()).getEnemyCharacter("flame",this.team);
                        JsonNode attackData = this.parentExt.getAttackData("flame","spell2");
                        int damage = (int) (50 + (enemyFP.getPlayerStat("spellDamage"))*0.3);
                        if(a.getActorType() != ActorType.PLAYER && a.getActorType() != ActorType.TOWER) a.addToDamageQueue(enemyFP,damage,attackData);
                        else if(a.getActorType() == ActorType.PLAYER){
                            UserActor ua = (UserActor) a;
                            ua.addToDamageQueue(enemyFP,damage,attackData);
                        }
                    }
                }
            }
            List<Actor> actorsToRemove = new ArrayList<Actor>(this.aggressors.keySet().size());
            for(Actor a : this.aggressors.keySet()){
                ISFSObject damageData = this.aggressors.get(a);
                if(System.currentTimeMillis() > damageData.getLong("lastAttacked") + 3000) actorsToRemove.add(a);
            }
            for(Actor a : actorsToRemove){
                this.aggressors.remove(a);
            }
            if(System.currentTimeMillis() - this.lastKilled >= 10000){
                if(this.multiKill != 0){
                    if(this.hasGameStat("largestMulti")){
                        double largestMulti = this.getGameStat("largestMulti");
                        if(this.multiKill > largestMulti) this.setGameStat("largestMulti",this.multiKill);
                    }else this.setGameStat("largestMulti",this.multiKill);
                    this.multiKill = 0;
                }
            }
        }
    }

    public void resetIdleTime(){
        this.idleTime = 0;
    }

    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        if(gCooldown > 0){
            this.stopMoving(gCooldown);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),castDelay,TimeUnit.MILLISECONDS);
        }else{
            this.stopMoving();
        }
        if(this.getClass() == UserActor.class){
            String abilityString = "q";
            int abilityIndex = 0;
            if(ability == 2){
                abilityString = "w";
                abilityIndex = 1;
            }
            else if(ability == 3){
                abilityString = "e";
                abilityIndex = 2;
            }
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.getUser(),abilityString,this.canCast[abilityIndex],getReducedCooldown(cooldown),gCooldown);
            if(this.canCast[abilityIndex]){
                this.canCast[abilityIndex] = false;
                int finalAbilityIndex = abilityIndex;
                Runnable castReset = () -> {canCast[finalAbilityIndex] = true;};
                SmartFoxServer.getInstance().getTaskScheduler().schedule(castReset,getReducedCooldown(cooldown),TimeUnit.MILLISECONDS);
            }
        }
    }
    public void setCanMove(boolean canMove){
        this.canMove = canMove;
        if(this.canMove && this.states.get(ActorState.CHARMED)) this.move(this.movementLine.getP2());
    }

    public void resetTarget(){
        this.target = null;
    }

    public void setState(ActorState[] states, boolean stateBool){
        for(ActorState s : states){
            this.states.put(s,stateBool);
            ExtensionCommands.updateActorState(parentExt,this.room,id,s,stateBool);
        }
    }

    public void setTarget(Actor a){
        this.target = a;
        if(this.states.get(ActorState.CHARMED)){
            this.setPath(getRelativePoint(false),a.getLocation());
            if(this.canMove) this.move(this.movementLine.getP2());
        }
    }

    public boolean isState(ActorState state){
        return this.states.get(state);
    }

    public boolean canUseAbility(int ability){
        ActorState[] hinderingStates = {ActorState.POLYMORPH, ActorState.AIRBORNE, ActorState.CHARMED, ActorState.FEARED, ActorState.SILENCED, ActorState.STUNNED};
        for(ActorState s : hinderingStates){
            if(this.states.get(s)) return false;
        }
        return this.canCast[ability-1];
    }

    public Line2D getMovementLine(){
        return this.movementLine;
    }

    public void stopMoving(int delay){
        this.stopMoving();
        this.canMove = false;
        if(delay > 0){
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),delay,TimeUnit.MILLISECONDS);
        }else this.canMove = true;
    }

    public void respawn(){
        this.canMove = true;
        this.setHealth((int) this.maxHealth, (int) this.maxHealth);
        int teamNumber = parentExt.getRoomHandler(this.room.getId()).getTeamNumber(this.id,this.team);
        Point2D respawnPoint = MapData.PURPLE_SPAWNS[teamNumber];
        if(this.team == 1 && respawnPoint.getX() < 0) respawnPoint = new Point2D.Double(respawnPoint.getX()*-1,respawnPoint.getY());
        System.out.println("Respawning at: " + respawnPoint.getX() + "," + respawnPoint.getY() + " for team " + this.team);
        this.location = respawnPoint;
        this.movementLine = new Line2D.Float(respawnPoint,respawnPoint);
        this.timeTraveled = 0f;
        this.dead = false;
        this.removeEffects();
        ExtensionCommands.snapActor(this.parentExt,this.room,this.id,this.location,this.location,false);
        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx/sfx_champion_respawn",this.location);
        ExtensionCommands.respawnActor(this.parentExt,this.room,this.id);
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"statusEffect_speed",5000,this.id+"_respawnSpeed",true,"Bip01 Footsteps",true,false,this.team);
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"champion_respawn_effect",1000,this.id+"_respawn",true,"Bip001",false,false,this.team);
    }

    public void addXP(int xp){
        if(this.level != 10){
            if(this.hasBackpackItem("junk_5_glasses_of_nerdicon") && this.getStat("sp_category5") > 0){
                double multiplier = 1+((5*this.getStat("sp_category5"))/100);
                xp*=multiplier;
            }
            this.xp+=xp; //TODO: Backpack modifiers
            HashMap<String, Double> updateData = new HashMap<>(3);
            updateData.put("xp", (double) this.xp);
            int level = ChampionData.getXPLevel(this.xp);
            if(level != this.level){
                this.level = level;
                updateData.put("level", (double) this.level);
                ExtensionCommands.playSound(parentExt,this.player,this.id,"sfx_level_up_beam");
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"level_up_beam",1000,this.id+"_levelUpBeam",true,"",true,false,this.team);
                ChampionData.levelUpCharacter(this.parentExt,this);

            }
            updateData.put("pLevel",this.getPLevel());
            ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,updateData);
        }
    }

    public int getLevel(){
        return this.level;
    }

    public double getPLevel(){
        if(this.level == 10) return 0d;
        double lastLevelXP = ChampionData.getLevelXP(this.level-1);
        double currentLevelXP = ChampionData.getLevelXP(this.level);
        double delta = currentLevelXP - lastLevelXP;
        return (this.xp-lastLevelXP)/delta;
    }

    private void processHitData(Actor a, JsonNode attackData, int damage){
        String precursor = "attack";
        if(attackData.has("spellName")) precursor = "spell";
        if(this.aggressors.containsKey(a)){
            this.aggressors.get(a).putLong("lastAttacked",System.currentTimeMillis());
            ISFSObject currentAttackData = this.aggressors.get(a);
            int tries = 0;
            for(String k : currentAttackData.getKeys()){
                if(k.contains("attack")){
                    ISFSObject attack0 = currentAttackData.getSFSObject(k);
                    if(attackData.get(precursor+"Name").asText().equalsIgnoreCase(attack0.getUtfString("atkName"))){
                        attack0.putInt("atkDamage",attack0.getInt("atkDamage")+damage);
                        this.aggressors.get(a).putSFSObject(k,attack0);
                        return;
                    }else tries++;
                }
            }
            String attackNumber = "";
            if(tries == 0) attackNumber = "attack1";
            else if(tries == 1) attackNumber = "attack2";
            else if(tries == 2) attackNumber = "attack3";
            else{
                System.out.println("Fourth attack detected!");
            }
            ISFSObject attack1 = new SFSObject();
            attack1.putUtfString("atkName",attackData.get(precursor+"Name").asText());
            attack1.putInt("atkDamage",damage);
            String attackType = "physical";
            if(precursor.equalsIgnoreCase("spell")) attackType = "spell";
            attack1.putUtfString("atkType",attackType);
            attack1.putUtfString("atkIcon",attackData.get(precursor+"IconImage").asText());
            this.aggressors.get(a).putSFSObject(attackNumber,attack1);
        }else{
            ISFSObject playerData = new SFSObject();
            playerData.putLong("lastAttacked",System.currentTimeMillis());
            ISFSObject attackObj = new SFSObject();
            attackObj.putUtfString("atkName",attackData.get(precursor+"Name").asText());
            attackObj.putInt("atkDamage",damage);
            String attackType = "physical";
            if(precursor.equalsIgnoreCase("spell")) attackType = "spell";
            attackObj.putUtfString("atkType",attackType);
            attackObj.putUtfString("atkIcon",attackData.get(precursor+"IconImage").asText());
            playerData.putSFSObject("attack1",attackObj);
            this.aggressors.put(a,playerData);
        }
    }

    protected HashMap<String, Double> initializeStats(){
        HashMap<String, Double> stats = new HashMap<>();
        stats.put("availableSpellPoints",1d);
        for(int i = 1; i < 6; i++){
            stats.put("sp_category"+i,0d);
        }
        stats.put("kills",0d);
        stats.put("deaths",0d);
        stats.put("assists",0d);
        JsonNode actorStats = this.parentExt.getActorStats(this.getAvatar());
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k,actorStats.get(k).asDouble());
        }
        return stats;
    }

    public String getBackpack(){
        return this.backpack;
    }

    public boolean hasBackpackItem(String item){
        String[] items = ChampionData.getBackpackInventory(this.parentExt,this.backpack);
        for(String i : items){
            if(i.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

    protected int getReducedCooldown(double cooldown){
        if(ABILITY_DEBUG) return 0;
        double cooldownReduction = this.getPlayerStat("coolDownReduction");
        double ratio = 1-(cooldownReduction/100);
        return (int) Math.round(cooldown*ratio);
    }

    public void handleSpellVamp(double damage){
        double percentage = this.getPlayerStat("spellVamp")/100;
        int healing = (int) Math.round(damage*percentage);
        this.changeHealth(healing);
    }

    public void handleLifeSteal(){
        double damage = this.getPlayerStat("attackDamage");
        double lifesteal = this.getPlayerStat("lifeSteal")/100;
        this.changeHealth((int)Math.round(damage*lifesteal));
    }

    public void addNailStacks(int damage){
        this.nailDamage+=damage;
        if(this.nailDamage>25*level) this.nailDamage = 25*level;
    }
    @Override
    public double getPlayerStat(String stat){
        if(stat.equalsIgnoreCase("attackDamage")){
            if(this.dcBuff == 2) return (super.getPlayerStat(stat) + this.nailDamage) * 1.2f;
            return super.getPlayerStat(stat) + this.nailDamage;
        }else if(stat.equalsIgnoreCase("armor")){
            if(this.dcBuff >= 1) return super.getPlayerStat(stat) * 1.2f;
        }else if(stat.equalsIgnoreCase("spellResist")){
            if(this.dcBuff >= 1) return super.getPlayerStat(stat) * 1.2f;
        }else if(stat.equalsIgnoreCase("speed")){
            if(this.dcBuff >= 1) return super.getPlayerStat(stat) * 1.15f;
        }else if(stat.equalsIgnoreCase("spellDamage")){
            if(this.dcBuff == 2) return super.getPlayerStat(stat) * 1.2f;
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {

    }

    public boolean getState(ActorState state){
        return this.states.get(state);
    }

    public void addGameStat(String stat, double value){
        if(this.endGameStats.containsKey(stat)) this.endGameStats.put(stat,this.endGameStats.get(stat)+value);
        else this.setGameStat(stat,value);
    }

    public void setGameStat(String stat, double value){
        this.endGameStats.put(stat,value);
    }

    public void addDamageGameStat(UserActor ua, double value, AttackType type){
        super.addDamageGameStat(ua,value,type);
        ua.addGameStat("damageDealtChamps",value);
    }

    public void handleDamageTakenStat(AttackType type, double value){
        this.addGameStat("damageReceivedTotal",value);
        if(type == AttackType.PHYSICAL) this.addGameStat("damageReceivedPhysical",value);
        else this.addGameStat("damageReceivedSpell",value);
    }

    public double getGameStat(String stat){
        return this.endGameStats.get(stat);
    }

    public boolean hasGameStat(String stat){
        return this.endGameStats.containsKey(stat);
    }

    public int getSpellDamage(JsonNode attackData){
        try{
            return (int)Math.round(attackData.get("damage").asDouble() + (this.getPlayerStat("spellDamage")*attackData.get("damageRatio").asDouble()));
        }catch(Exception e){
            e.printStackTrace();
            return attackData.get("damage").asInt();
        }
    }

    public void fireProjectile(Projectile projectile, String id, Point2D dest, float range){
        double x = dest.getX();
        double z = dest.getY();
        Point2D endLocation = new Point2D.Double(x,z);
        Line2D skillShotLine = new Line2D.Float(this.location,endLocation);
        Line2D maxRangeLine = Champion.getMaxRangeLine(skillShotLine,range);
        double speed = parentExt.getActorStats(id).get("speed").asDouble();
        ExtensionCommands.createProjectile(parentExt,this.room,this,id, maxRangeLine.getP1(), maxRangeLine.getP2(), (float)speed);
        this.parentExt.getRoomHandler(this.room.getId()).addProjectile(projectile);
    }

    public void fireProjectile(Projectile projectile, String id, String projectileId, Point2D dest, float range){
        double x = dest.getX();
        double z = dest.getY();
        Point2D endLocation = new Point2D.Double(x,z);
        Line2D skillShotLine = new Line2D.Float(this.location,endLocation);
        Line2D maxRangeLine = Champion.getMaxRangeLine(skillShotLine,range);
        double speed = parentExt.getActorStats(projectileId).get("speed").asDouble();
        ExtensionCommands.createProjectile(parentExt,room,this,id,projectileId, maxRangeLine.getP1(), maxRangeLine.getP2(), (float)speed);
        this.parentExt.getRoomHandler(this.room.getId()).addProjectile(projectile);
    }

    public void handleDCBuff(int teamSizeDiff, boolean removeSecondBuff){
        String[] stats = {"armor","spellResist","speed"};
        String[] stats2 = {"attackDamage","spellDamage"};
        if(removeSecondBuff){
            this.dcBuff = 1;
            ExtensionCommands.updateActorData(parentExt,room,id,getPlayerStats(stats2));
            ExtensionCommands.removeStatusIcon(parentExt,player,"DC Buff #2");
            ExtensionCommands.removeFx(parentExt,room,id+"_dcbuff2");
            return;
        }
        switch (teamSizeDiff){
            case 0:
                this.dcBuff = 0;
                ExtensionCommands.updateActorData(parentExt,room,id,getPlayerStats(stats));
                ExtensionCommands.removeStatusIcon(parentExt,player,"DC Buff #1");
                ExtensionCommands.removeFx(parentExt,room,id+"_dcbuff1");
                break;
            case 1:
            case -1:
                this.dcBuff = 1;
                ExtensionCommands.updateActorData(parentExt,room,id,getPlayerStats(stats));
                ExtensionCommands.addStatusIcon(parentExt,player,"DC Buff #1","Some coward left the battle! Here's something to help even the playing field!", "icon_parity",0);
                ExtensionCommands.createActorFX(parentExt,room,id,"disconnect_buff_duo",1000*15*60,id+"_dcbuff1",true,"",false,false,team);
                break;
            case 2:
            case -2:
                this.dcBuff = 2;
                ExtensionCommands.updateActorData(parentExt,room,id,getPlayerStats(stats2));
                ExtensionCommands.addStatusIcon(parentExt,player,"DC Buff #2","You're the last one left, finish the mission", "icon_parity2",0);
                ExtensionCommands.createActorFX(parentExt,room,id,"disconnect_buff_solo",1000*15*60,id+"_dcbuff2",true,"",false,false,team);
                break;
        }
    }

    private HashMap<String, Double> getPlayerStats(String[] stats){
        HashMap<String, Double> playerStats = new HashMap<>(stats.length);
        for(String s : stats){
            playerStats.put(s,this.getPlayerStat(s));
        }
        return playerStats;
    }

    protected void updateStatMenu(String stat){
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,stat,this.getPlayerStat(stat));
    }

    protected void updateStatMenu(String[] stats){
        for(String s : stats){
            ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,s,this.getPlayerStat(s));
        }
    }

    public void cleanseEffects(){
        ActorState[] cleansedStats = {ActorState.SLOWED,ActorState.STUNNED, ActorState.STUNNED, ActorState.CHARMED, ActorState.FEARED, ActorState.BLINDED, ActorState.ROOTED, ActorState.CLEANSED};
        this.setState(cleansedStats,false);
        if(this.activeBuffs.containsKey("SLOWED")){
            this.setTempStat("speed",this.getTempStat("speed")*-1);
            this.activeBuffs.remove("SLOWED");
        }
    }

    public void destroy(){
        this.dead = true;
        ExtensionCommands.destroyActor(this.parentExt,this.room,this.id);
    }

    public void clearIconHandlers(){
        Set<String> iconNames = new HashSet<>(this.iconHandlers.keySet());
        for(String i : iconNames){
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,i);
            this.iconHandlers.get(i).cancel(true);
        }
        this.iconHandlers = new HashMap<>();
    }

    @Override
    public void removeEffects(){
        super.removeEffects();
        this.clearIconHandlers();
    }

    public void addIconHandler(String iconName, ScheduledFuture<?> handler){
        this.iconHandlers.put(iconName,handler);
    }

    public void removeIconHandler(String iconName){
        this.iconHandlers.remove(iconName);
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        public MovementStopper(boolean move){
            this.move = move;
        }
        @Override
        public void run() {
            canMove = this.move;
        }
    }

    protected class RangedAttack implements Runnable {

        Actor target;
        Runnable attackRunnable;
        String projectile;
        String emitNode;

        public RangedAttack(Actor target, Runnable attackRunnable, String projectile){
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
        }

        public RangedAttack(Actor target, Runnable attackRunnable, String projectile, String emitNode){
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
            this.emitNode = emitNode;
        }

        @Override
        public void run() {
            String emit = "Bip01";
            if(this.emitNode != null) emit = this.emitNode;
            float time = (float) (target.getLocation().distance(location) / 10f);
            ExtensionCommands.createProjectileFX(parentExt,room,projectile,id,target.getId(),emit,"targetNode",time);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(attackRunnable,(int)(time*1000),TimeUnit.MILLISECONDS);
        }
    }

    protected class BattleMoon implements Runnable {

        @Override
        public void run() {
            moonActivated = false;
            moonTimer = 0;
        }
    }
}

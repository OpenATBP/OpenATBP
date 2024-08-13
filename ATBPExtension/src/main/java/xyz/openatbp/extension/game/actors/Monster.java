package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

public class Monster extends Actor {

    enum AggroState {
        PASSIVE,
        ATTACKED
    }

    enum MonsterType {
        SMALL,
        BIG
    }

    private AggroState state = AggroState.PASSIVE;
    private final Point2D startingLocation;
    private final MonsterType type;
    private static boolean movementDebug = false;
    private boolean attackRangeOverride = false;
    private boolean headingBack = false;

    public Monster(
            ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName) {
        this.startingLocation = new Point2D.Float(startingLocation[0], startingLocation[1]);
        this.type = MonsterType.BIG;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.stats = this.initializeStats();
        this.id = monsterName;
        this.maxHealth = this.stats.get("health");
        this.currentHealth = this.maxHealth;
        this.actorType = ActorType.MONSTER;
        this.displayName = parentExt.getDisplayName(monsterName);
        this.xpWorth = this.parentExt.getActorXP(this.id);
        Properties props = parentExt.getConfigProperties();
        movementDebug = Boolean.parseBoolean(props.getProperty("movementDebug", "false"));
        if (movementDebug)
            ExtensionCommands.createActor(
                    parentExt, room, id + "_test", "creep", this.location, 0f, 2);
        this.updateMaxHealth();
    }

    public Monster(
            ATBPExtension parentExt, Room room, Point2D startingLocation, String monsterName) {
        this.startingLocation = startingLocation;
        this.type = MonsterType.SMALL;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.stats = this.initializeStats();
        this.id = monsterName;
        this.maxHealth = this.stats.get("health");
        this.currentHealth = this.maxHealth;
        this.actorType = ActorType.MONSTER;
        this.xpWorth = this.parentExt.getActorXP(this.id);
        this.displayName = parentExt.getDisplayName(monsterName);
        this.updateMaxHealth();
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) { // Runs when taking damage
        try {
            if (this.dead)
                return true; // Prevents bugs that take place when taking damage (somehow from FP
            // passive)
            // after dying
            if (Champion.getUserActorsInRadius(
                                    this.parentExt.getRoomHandler(this.room.getName()),
                                    this.location,
                                    10f)
                            .isEmpty()
                    && this.state == AggroState.PASSIVE) return false;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                if (ua.hasBackpackItem("junk_1_demon_blood_sword")
                        && ua.getStat("sp_category1") > 0) damage *= 1.3;
            }
            AttackType attackType = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage, attackType, a);
            if (a.getActorType() == ActorType.PLAYER)
                this.addDamageGameStat((UserActor) a, newDamage, attackType);
            boolean returnVal = super.damaged(a, newDamage, attackData);
            if (!this.headingBack && isProperActor(a)) { // attacks the nearest attacker
                state = AggroState.ATTACKED;
                if (!this.getState(ActorState.CHARMED) && this.target == null
                        || a.getLocation().distance(this.getLocation())
                                < this.target.getLocation().distance(this.location)) {
                    this.target = a;
                }
                if (this.target != null && !this.withinRange(this.target)) {
                    this.moveTowardsActor();
                }

                if (target != null && target.getActorType() == ActorType.PLAYER)
                    ExtensionCommands.setTarget(
                            parentExt, ((UserActor) target).getUser(), this.id, target.getId());
                if (this.type
                        == MonsterType
                                .SMALL) { // Gets all mini monsters like gnomes and owls to all
                    // target player when
                    // one is hit
                    for (Monster m :
                            parentExt
                                    .getRoomHandler(this.room.getName())
                                    .getCampMonsters(this.id)) {
                        m.setAggroState(AggroState.ATTACKED, a);
                    }
                }
            }
            return returnVal;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void handleCharm(UserActor charmer, int duration) {
        this.addState(ActorState.CHARMED, 0d, duration);
        if (charmer != null) {
            this.target = charmer;
            this.charmer = charmer;
        }
    }

    public void updateMaxHealth() {
        int averagePLevel = parentExt.getRoomHandler(this.room.getName()).getAveragePlayerLevel();
        if (averagePLevel != level) {
            int levelDiff = averagePLevel - level;
            this.maxHealth += parentExt.getHealthScaling(this.id) * levelDiff;
            this.level = averagePLevel;
            Champion.updateServerHealth(this.parentExt, this);
        }
    }

    public boolean isProperActor(Actor a) {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        List<Actor> actors = Champion.getActorsInRadius(handler, this.location, 10f);
        return actors.contains(a)
                && a.getActorType() != ActorType.MINION
                && a.getActorType() != ActorType.MONSTER;
    }

    public void setAggroState(AggroState state, Actor a) {
        if (this.state == AggroState.ATTACKED && state == AggroState.PASSIVE) {
            double closestDistance = 1000;
            UserActor closestPlayer = null;
            for (UserActor ua : parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                if (ua.getLocation().distance(this.location) < closestDistance) {
                    closestPlayer = ua;
                    closestDistance = ua.getLocation().distance(this.location);
                }
            }
            if (closestDistance <= 10) {
                this.target = closestPlayer;
            }
        } else {
            this.state = state;
            if (state == AggroState.ATTACKED) this.target = a;
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public void knockback(Point2D source, float distance) {
        super.knockback(source, distance);
        this.attackRangeOverride = true;
    }

    @Override
    public void handlePull(Point2D source, double pullDistance) {
        super.handlePull(source, pullDistance);
        this.attackRangeOverride = true;
    }

    @Override
    public void attack(Actor a) { // TODO: Almost identical to minions - maybe move to Actor class?
        // Called when it is attacking a player
        this.stopMoving();
        this.canMove = false;
        if (this.attackCooldown == 0) {
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            int attackDamage = (int) this.getPlayerStat("attackDamage");
            ExtensionCommands.attackActor(
                    parentExt,
                    this.room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    false,
                    true);
            if (this.getId().contains("gnome"))
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new Champion.DelayedRangedAttack(this, a),
                                300,
                                TimeUnit.MILLISECONDS);
            else
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new Champion.DelayedAttack(
                                        this.parentExt, this, a, attackDamage, "basicAttack"),
                                500,
                                TimeUnit.MILLISECONDS); // Melee damage
        }
    }

    public void rangedAttack(
            Actor a) { // Called when ranged attacks take place to spawn projectile and deal
        // damage after
        // projectile hits
        String fxId = "gnome_projectile";
        int attackDamage = (int) this.getPlayerStat("attackDamage");
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(
                this.parentExt, this.room, fxId, this.id, a.getId(), "Bip001", "Bip001", time);

        parentExt
                .getTaskScheduler()
                .schedule(
                        new Champion.DelayedAttack(
                                this.parentExt, this, a, attackDamage, "basicAttack"),
                        (int) (time * 1000),
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void setTarget(Actor a) {
        if (this.state == AggroState.PASSIVE) this.setAggroState(AggroState.ATTACKED, a);
        this.target = a;
        this.movementLine = new Line2D.Float(this.location, a.getLocation());
        this.timeTraveled = 0f;
        this.moveTowardsActor();
    }

    @Override
    public void die(Actor a) { // Called when monster dies
        Console.debugLog(this.id + " has died! " + this.dead);
        if (!this.dead) { // No double deaths
            this.dead = true;
            if (!this.getState(ActorState.AIRBORNE)) this.stopMoving();
            this.currentHealth = -1;
            RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getName());
            int scoreValue = parentExt.getActorStats(this.id).get("valueScore").asInt();
            if (a.getActorType() == ActorType.PLAYER
                    || a.getActorType()
                            == ActorType.COMPANION) { // Adds score + party xp when killed by player
                UserActor ua = null;
                if (a.getActorType() == ActorType.COMPANION) {
                    if (a.getId().contains("skully"))
                        ua =
                                this.parentExt
                                        .getRoomHandler(this.room.getName())
                                        .getEnemyChampion(a.getTeam(), "lich");
                    else if (a.getId().contains("turret"))
                        ua =
                                this.parentExt
                                        .getRoomHandler(this.room.getName())
                                        .getEnemyChampion(a.getTeam(), "princessbubblegum");
                    else if (a.getId().contains("mine"))
                        ua =
                                this.parentExt
                                        .getRoomHandler(this.room.getName())
                                        .getEnemyChampion(a.getTeam(), "neptr");
                } else ua = (UserActor) a;
                if (ua != null) {
                    ua.addGameStat("jungleMobs", 1);
                    roomHandler.addScore(ua, a.getTeam(), scoreValue);
                    // roomHandler.handleXPShare(ua,this.parentExt.getActorXP(this.id));
                    ExtensionCommands.knockOutActor(parentExt, this.room, this.id, ua.getId(), 45);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            ua.getUser(),
                            ua.getId(),
                            "sfx_gems_get",
                            this.location);
                }
            } else {
                ExtensionCommands.knockOutActor(parentExt, this.room, this.id, a.getId(), 45);
            }
            ExtensionCommands.destroyActor(parentExt, this.room, this.id);
            roomHandler.handleSpawnDeath(this);
        }
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        this.handleActiveEffects();
        if (this.dead) return;
        if (this.target != null && (this.target.getState(ActorState.INVISIBLE))) {
            this.state = AggroState.PASSIVE;
            this.moveWithCollision(this.startingLocation);
            this.target = null;
        }
        if (this.headingBack && this.location.distance(startingLocation) <= 1f) {
            this.headingBack = false;
        }
        if (this.getState(ActorState.CHARMED) && this.charmer != null) {
            moveTowardsCharmer(charmer);
        }

        if (msRan % 1000 * 60
                == 0) { // Every second it checks average player level and scales accordingly
            this.updateMaxHealth();
        }
        if (this.movementLine != null)
            this.location =
                    this.getRelativePoint(); // Sets location variable to its actual location
        // using *math*
        else this.movementLine = new Line2D.Float(this.location, this.location);
        if (this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01d)
            this.timeTraveled += 0.1f;
        this.handlePathing();
        if (this.target != null && this.target.getHealth() <= 0)
            this.setAggroState(AggroState.PASSIVE, null);
        if (movementDebug && this.type == MonsterType.BIG)
            ExtensionCommands.moveActor(
                    parentExt, room, this.id + "_test", this.location, this.location, 5f, false);
        if (this.state == AggroState.PASSIVE) {
            if (this.currentHealth < this.maxHealth) {
                int value = (int) (this.getMaxHealth() * 0.006);
                this.changeHealth(value);
            }
            if (this.isStopped() && this.location.distance(this.startingLocation) > 0.1d) {
                this.move(this.startingLocation);
            }
        } else { // Monster is pissed!!
            if ((this.location.distance(this.startingLocation) >= 10 && !this.attackRangeOverride)
                    || (this.target != null && this.target.getHealth() <= 0)) {
                this.state = AggroState.PASSIVE; // Far from camp, heading back
                this.moveWithCollision(this.startingLocation);
                this.target = null;
                this.headingBack = true;
            } else if (this.target != null) { // Chasing player
                if (this.attackRangeOverride) this.attackRangeOverride = false;
                if (this.withinRange(this.target) && this.canAttack()) {
                    this.attack(this.target);
                } else if (!this.withinRange(this.target) && this.canMove()) {
                    this.moveTowardsActor();
                } else if (this.withinRange(this.target)
                        && !this.states.get(ActorState.FEARED)
                        && !this.states.get(ActorState.CHARMED)) {
                    if (this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f)
                        this.stopMoving();
                }
            }
        }
        if (this.attackCooldown > 0) this.reduceAttackCooldown();
    }

    @Override
    public boolean canMove() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.AIRBORNE) {
                if (this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    @Override
    public boolean withinRange(Actor a) {
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public Point2D getRelativePoint() { // Gets player's current location based on time
        if (this.movementLine == null) return this.location;
        if (this.timeTraveled == 0
                && this.movementLine.getP1().distance(this.movementLine.getP2()) > 0.01f)
            this.timeTraveled += 0.1f; // Prevents any stagnation in movement
        Point2D rPoint = new Point2D.Float();
        Point2D destination = movementLine.getP2();
        float x2 = (float) destination.getX();
        float y2 = (float) destination.getY();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        Line2D movementLine = new Line2D.Double(x1, y1, x2, y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist / (float) this.getPlayerStat("speed");
        double currentTime = this.timeTraveled;
        if (currentTime > time) currentTime = time;
        double currentDist = (float) this.getPlayerStat("speed") * currentTime;
        float x = (float) (x1 + (currentDist / dist) * (x2 - x1));
        float y = (float) (y1 + (currentDist / dist) * (y2 - y1));
        rPoint.setLocation(x, y);
        if (dist != 0) return rPoint;
        else return movementLine.getP1();
    }

    public void
            moveTowardsActor() { // Moves towards a specific point. TODO: Have the path stop based
        // on
        // collision radius
        if (!this.canMove()) return;
        if (this.states.get(ActorState.FEARED)) return;
        if (this.movementLine == null) {
            this.moveWithCollision(this.target.getLocation());
        }
        if (this.path == null) {
            if (this.target != null
                    && this.movementLine.getP2().distance(this.target.getLocation()) > 0.1f) {
                this.moveWithCollision(this.target.getLocation());
            }
        } else {
            if (this.target != null
                    && this.path.size() - this.pathIndex < 3
                    && this.path.get(this.path.size() - 1).distance(this.target.getLocation())
                            > 0.1f) {
                this.moveWithCollision(this.target.getLocation());
            }
        }
    }
}

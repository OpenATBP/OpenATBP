package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class Minion extends Actor {

    private final double[] blueBotX = {
        36.90, 26.00, 21.69, 16.70, 3.44, -9.56, -21.20, -28.02, -33.11, -36.85
    }; // Path points from blue base to purple base
    private final double[] blueBotY = {
        2.31, 8.64, 12.24, 17.25, 17.81, 18.76, 14.78, 7.19, 5.46, 2.33
    };
    private final double[] blueTopX = {
        36.68, 30.10, 21.46, 18.20, -5.26, -12.05, -24.69, -28.99, -35.67
    };
    private final double[] blueTopY = {
        -2.56, -7.81, -12.09, -16.31, -17.11, -17.96, -13.19, -7.50, -2.70
    };

    private final double[] practiceX = {
        38.00, 33.93, 30, 20.68, 12.76, 7.38, -4.87, -15.79, -24.00, -33.49, -38.77
    };
    private final double[] practiceY = {
        0.76, 0.53, -1.5, -1.46, -1.43, 0.1, 0.1, -0.95, -0.79, -0.67, 0.06
    };

    public enum MinionType {
        RANGED,
        MELEE,
        SUPER
    } // Type of minion

    public enum State {
        IDLE,
        MOVING,
        TARGETING,
        ATTACKING
    }

    private MinionType type;

    private int lane;
    private int mainPathIndex = 0;
    private Map<UserActor, Integer> aggressors;
    private final boolean MOVEMENT_DEBUG = false;
    private State state;

    public Minion(ATBPExtension parentExt, Room room, int team, int minionNum, int wave, int lane) {
        this.avatar = "creep" + team;
        this.state = State.IDLE;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        String typeString = "super";
        if (minionNum <= 1) {
            typeString = "melee" + minionNum;
            this.type = MinionType.MELEE;
            this.maxHealth = 450;
        } else if (minionNum <= 3) {
            typeString = "ranged" + minionNum;
            this.avatar += "_ranged";
            this.type = MinionType.RANGED;
            this.maxHealth = 350;
        } else {
            this.type = MinionType.SUPER;
            this.avatar += "_super";
            this.maxHealth = 500;
        }
        this.displayName = parentExt.getDisplayName(this.getAvatar());
        this.currentHealth = this.maxHealth;
        float x = (float) blueBotX[0]; // Bot Lane
        float y = (float) blueBotY[0];
        if (team == 0) x = (float) blueBotX[blueBotX.length - 1];
        if (lane == 0) { // Top Lane
            x = (float) blueTopX[0];
            y = (float) blueTopY[0];
            if (team == 0) {
                x = (float) blueTopX[blueTopX.length - 1];
                y = (float) blueTopY[blueTopY.length - 1];
            }
        }
        if (this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
            if (team == 1) {
                x = (float) practiceX[0];
                y = (float) practiceY[0];
            } else {
                x = (float) practiceX[practiceX.length - 1];
                y = (float) practiceY[practiceY.length - 1];
            }
        }
        this.location = new Point2D.Float(x, y);
        this.id = team + "creep_" + lane + typeString + wave;
        this.lane = lane;
        this.actorType = ActorType.MINION;
        if (team == 0) {
            if (lane == 0) mainPathIndex = blueTopX.length - 1;
            else mainPathIndex = blueBotX.length - 1;
        }
        if (this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
            if (team == 0) mainPathIndex = practiceX.length - 1;
        }
        this.movementLine = new Line2D.Float(this.location, this.location);
        aggressors = new HashMap<>(3);
        this.stats = this.initializeStats();
        ExtensionCommands.createActor(
                parentExt, room, this.id, this.getAvatar(), this.location, 0f, this.team);
        if (MOVEMENT_DEBUG)
            ExtensionCommands.createActor(
                    parentExt, room, this.id + "_test", "gnome_a", this.location, 0f, this.team);
        this.attackCooldown = this.getPlayerStat("attackSpeed");
        this.xpWorth = 5;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public void attack(Actor a) {
        this.stopMoving();
        this.canMove = false;
        this.attackCooldown = this.getPlayerStat("attackSpeed");
        ExtensionCommands.attackActor(
                this.parentExt,
                this.room,
                this.id,
                a.getId(),
                (float) a.getLocation().getX(),
                (float) a.getLocation().getY(),
                false,
                true);
        if (this.type == MinionType.RANGED)
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            new Champion.DelayedRangedAttack(this, a), 500, TimeUnit.MILLISECONDS);
        else
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            new Champion.DelayedAttack(
                                    parentExt,
                                    this,
                                    a,
                                    (int) this.getPlayerStat("attackDamage"),
                                    "basicAttack"),
                            500,
                            TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) {
        this.currentHealth = 0;
        if (this.dead) return;
        if (!this.getState(ActorState.AIRBORNE)) this.stopMoving();
        this.dead = true;
        if (a.getActorType() == ActorType.PLAYER || a.getActorType() == ActorType.COMPANION) {
            UserActor ua = null;
            if (a.getActorType() == ActorType.COMPANION) {
                if (a.getId().contains("skully"))
                    ua =
                            this.parentExt
                                    .getRoomHandler(this.room.getId())
                                    .getEnemyChampion(this.team, "lich");
                else if (a.getId().contains("turret"))
                    ua =
                            this.parentExt
                                    .getRoomHandler(this.room.getId())
                                    .getEnemyChampion(this.team, "princessbubblegum");
                else if (a.getId().contains("mine"))
                    ua =
                            this.parentExt
                                    .getRoomHandler(this.room.getId())
                                    .getEnemyChampion(this.team, "neptr");
            } else ua = (UserActor) a;
            if (ua != null) {
                ua.addGameStat("minions", 1);
                if (ua.hasBackpackItem("junk_1_magic_nail") && ua.getStat("sp_category1") > 0)
                    ua.addNailStacks(2);
                this.parentExt.getRoomHandler(this.room.getId()).addScore(ua, a.getTeam(), 1);
                ExtensionCommands.knockOutActor(parentExt, this.room, this.id, ua.getId(), 30);
                ExtensionCommands.playSound(
                        this.parentExt, ua.getUser(), ua.getId(), "sfx_gems_get", this.location);
            }
        } else {
            ExtensionCommands.knockOutActor(parentExt, this.room, this.id, a.getId(), 30);
            for (UserActor user :
                    Champion.getUserActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getId()), this.location, 10f)) {
                if (user.getTeam() != this.team)
                    user.addXP((int) Math.floor((double) this.xpWorth / 2d));
            }
        }
        ExtensionCommands.destroyActor(parentExt, this.room, this.id);
        // this.parentExt.getRoomHandler(this.room.getId()).handleAssistXP(a,aggressors.keySet(),
        // this.xpWorth);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try {
            if (this.dead) return true;
            if (a.getActorType() == ActorType.PLAYER) {
                aggressors.put((UserActor) a, 0);
            }
            if (a.getActorType() == ActorType.TOWER) {
                if (this.type == MinionType.SUPER) damage = (int) Math.round(damage * 0.25);
                else damage = (int) Math.round(damage * 0.75);
            }
            AttackType type = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage, type, a);
            if (a.getActorType() == ActorType.PLAYER)
                this.addDamageGameStat((UserActor) a, newDamage, type);
            this.changeHealth(newDamage * -1);
            // Minion dies
            return currentHealth <= 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isInvisible(Actor a) {
        ActorState[] states = {ActorState.INVISIBLE, ActorState.BRUSH};
        for (ActorState state : states) {
            if (a.getState(state)) return true;
        }
        return false;
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        this.handleActiveEffects();
        if (this.dead) return;
        if (this.target != null && isInvisible(target)) this.target = null;

        if (msRan % 1000 == 0) {
            /*
            for(UserActor k : aggressors.keySet()){
                if(aggressors.get(k) == 10) aggressors.remove(k);
                else aggressors.put(k,aggressors.get(k)+1);
            }

             */
            int xp = 5 + ((msRan / 1000) / 60);
            if (this.type == MinionType.SUPER) xp += 5;
            if (xpWorth != xp) xpWorth = xp;
        }

        if (!this.isStopped()) this.timeTraveled += 0.1f;
        this.location =
                MovementManager.getRelativePoint(
                        this.movementLine, this.getSpeed(), this.timeTraveled);
        this.handlePathing();
        Minion conflictingMinion = this.isInsideMinion();
        if (conflictingMinion != null && this.state != State.ATTACKING && this.target != null) {
            Line2D line = new Line2D.Float(conflictingMinion.getLocation(), this.location);
            Line2D extendedLine = MovementManager.extendLine(line, 10f);
            Point2D snapLocation = extendedLine.getP2();
            Point2D finalLocation =
                    MovementManager.getStoppingPoint(snapLocation, this.target.getLocation(), 0.5f);
            if (!this.withinRange(this.target)) this.moveWithCollision(finalLocation);
            // if(this.target != null) this.moveTowardsTarget();
        }
        if (this.attackCooldown > 0) this.reduceAttackCooldown();
        if (MOVEMENT_DEBUG)
            ExtensionCommands.moveActor(
                    parentExt, room, id + "_test", this.location, this.location, 5f, false);

        switch (this.state) {
            case IDLE:
                // Console.logWarning(this.id + " is idle!");
                this.mainPathIndex = this.findPathIndex();
                this.moveWithCollision(this.getPathPoint());
                this.state = State.MOVING;
                break;
            case MOVING:
                if (this.location.distance(this.getPathPoint()) <= 0.1d) {
                    this.moveAlongPath();
                    return;
                } else if (this.isStopped()) {
                    // this.canMove = true;
                    this.moveWithCollision(this.getPathPoint());
                    return;
                }
                Actor potentialTarget = this.searchForTarget();
                if (potentialTarget != null) {
                    this.setTarget(potentialTarget);
                    this.state = State.TARGETING;
                }
                break;
            case TARGETING:
                if (this.target == null) {
                    this.state = State.IDLE;
                    return;
                }
                if (this.withinAggroRange(this.target.getLocation())
                        && this.target.getHealth() > 0) {
                    if (this.withinRange(this.target) && conflictingMinion == null) {
                        this.state = State.ATTACKING;
                    } else if (conflictingMinion == null) {
                        if (this.target.getLocation().distance(this.movementLine.getP2()) > 0.1)
                            this.moveTowardsTarget();
                    }
                } else {
                    this.resetTarget();
                }
                break;
            case ATTACKING:
                if (this.target == null) {
                    this.state = State.IDLE;
                    return;
                }
                if (this.withinRange(this.target) && this.canAttack()) {
                    this.attack(this.target);
                } else if (!this.withinRange(this.target) || this.target.getHealth() <= 0) {
                    Actor target = this.searchForTarget();
                    if (target != null) {
                        this.state = State.TARGETING;
                        this.setTarget(target);
                    } else {
                        this.resetTarget();
                    }
                } else if (this.withinRange(this.target) && !this.isStopped()) {
                    this.stopMoving();
                }
                break;
        }
    }

    @Override
    public void moveWithCollision(Point2D dest) {
        List<Point2D> path = new ArrayList<>();
        try {
            path =
                    MovementManager.getPath(
                            this.parentExt.getRoomHandler(this.room.getId()), this.location, dest);
        } catch (Exception e) {
            Console.logWarning(this.id + " could not form a path.");
        }
        if (path != null && path.size() > 2) {
            this.setPath(path);
        } else {
            Line2D testLine = new Line2D.Float(this.location, dest);
            Point2D newPoint =
                    MovementManager.getPathIntersectionPoint(
                            this.parentExt,
                            this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap(),
                            testLine);
            if (newPoint != null) {
                this.move(newPoint);
            } else this.move(dest);
        }
    }

    @Override
    public void setTarget(Actor a) {
        this.target = a;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            ExtensionCommands.setTarget(parentExt, ua.getUser(), this.id, ua.getId());
        }
        this.moveTowardsTarget();
    }

    @Override
    public void rangedAttack(Actor a) {
        String fxId = "minion_projectile_";
        if (this.team == 0) fxId += "purple";
        else fxId += "blue";
        double time = a.getLocation().distance(this.location) / 20d;
        ExtensionCommands.createProjectileFX(
                this.parentExt, this.room, fxId, this.id, a.getId(), "emitNode", "", (float) time);
        SmartFoxServer.getInstance()
                .getTaskScheduler()
                .schedule(
                        new Champion.DelayedAttack(
                                parentExt,
                                this,
                                a,
                                (int) this.getPlayerStat("attackDamage"),
                                "basicAttack"),
                        (int) (time * 1000),
                        TimeUnit.MILLISECONDS);
    }

    public void resetTarget() {
        this.state = State.IDLE;
        this.target = null;
    }

    public int getLane() {
        return this.lane;
    }

    public MinionType getType() {
        return this.type;
    }

    private Point2D getPathPoint() {
        double x;
        double y;
        if (!this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
            if (this.lane == 0) {
                x = blueTopX[this.mainPathIndex];
                y = blueTopY[this.mainPathIndex];
            } else {
                x = blueBotX[this.mainPathIndex];
                y = blueBotY[this.mainPathIndex];
            }
        } else {
            x = practiceX[this.mainPathIndex];
            y = practiceY[this.mainPathIndex];
        }

        return new Point2D.Double(x, y);
    }

    private Point2D getPathPoint(int mainPathIndex) {
        double x;
        double y;
        if (!this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
            if (this.lane == 0) {
                x = blueTopX[mainPathIndex];
                y = blueTopY[mainPathIndex];
            } else {
                x = blueBotX[mainPathIndex];
                y = blueBotY[mainPathIndex];
            }
        } else {
            x = practiceX[mainPathIndex];
            y = practiceY[mainPathIndex];
        }

        return new Point2D.Double(x, y);
    }

    private void moveAlongPath() {
        if (this.team == 1) this.mainPathIndex++;
        else this.mainPathIndex--;
        if (this.mainPathIndex < 0) this.mainPathIndex = 0;
        else {
            if (!this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
                if (this.lane == 0 && this.mainPathIndex == blueTopX.length) this.mainPathIndex--;
                else if (this.lane == 1 && this.mainPathIndex == blueBotX.length)
                    this.mainPathIndex--;
            } else {
                if (this.mainPathIndex == practiceX.length) this.mainPathIndex--;
            }
        }
        this.move(this.getPathPoint());
        this.timeTraveled = 0.1f;
    }

    @Override
    public String getAvatar() {
        return this.avatar.replace("0", "");
    }

    @Override
    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar.replace("0", ""));
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    @Override
    public String getPortrait() {
        return this.getAvatar();
    }

    private boolean withinAggroRange(Point2D p) {
        return p.distance(this.location) <= 5;
    }

    private boolean facingEntity(
            Point2D p) { // Returns true if the point is in the same direction as the minion is
        // heading
        // TODO: Some minions don't attack others attacking the base when they spawn
        double deltaX = movementLine.getX2() - location.getX();
        // Negative = left Positive = right
        if (Double.isNaN(deltaX)) return false;
        if (deltaX > 0 && p.getX() > this.location.getX()) return true;
        else return deltaX < 0 && p.getX() < this.location.getX();
    }

    private boolean facingEntity(
            Line2D line,
            Point2D p) { // Returns true if the point is in the same direction as the minion is
        // heading
        // TODO: Some minions don't attack others attacking the base when they spawn
        double deltaX = line.getX2() - line.getX1();
        // Negative = left Positive = right
        if (Double.isNaN(deltaX)) return false;
        if (deltaX > 0 && p.getX() > line.getX1()) return true;
        else return deltaX < 0 && p.getX() < line.getX1();
    }

    @Override
    public boolean setTempStat(String stat, double delta) {
        boolean returnVal = super.setTempStat(stat, delta);
        if (stat.equalsIgnoreCase("speed")) {
            if (movementLine != null) {
                this.move(movementLine.getP2());
            }
        }
        return returnVal;
    }

    public boolean isInvisOrInBrush(Actor a) {
        ActorState[] states = {ActorState.INVISIBLE, ActorState.BRUSH};
        for (ActorState state : states) {
            if (a.getState(state)) return true;
        }
        return false;
    }

    private Actor searchForTarget() {
        Actor closestActor = null;
        Actor closestNonUser = null;
        double distance = 1000f;
        double distanceNonUser = 1000f;
        for (Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()) {
            if (a.getTeam() != this.team
                    && isNotAMonster(a)
                    && !a.getAvatar().equalsIgnoreCase("neptr_mine")
                    && !a.getId().contains("decoy")
                    && !isInvisOrInBrush(a)
                    && this.withinAggroRange(a.getLocation())) {
                if (a.getActorType() == ActorType.PLAYER && this.facingEntity(a.getLocation())) {
                    UserActor ua = (UserActor) a;
                    if (ua.getState(ActorState.REVEALED) && !ua.getState(ActorState.BRUSH)) {
                        if (ua.getLocation().distance(this.location) < distance) {
                            distance = ua.getLocation().distance(this.location);
                            closestActor = ua;
                        }
                    }
                } else {
                    // Console.debugLog(this.id +": Targeting " + a.getId() + " at dist " +
                    // a.getLocation().distance(this.location));
                    if (a.getLocation().distance(this.location) < distanceNonUser) {
                        if (a.getActorType() != ActorType.BASE) {
                            closestNonUser = a;
                            distanceNonUser = a.getLocation().distance(this.location);
                        } else {
                            Base b = (Base) a;
                            if (b.isUnlocked()) {
                                closestNonUser = a;
                                distanceNonUser = a.getLocation().distance(this.location);
                            }
                        }
                    }
                }
            }
        }
        if (closestNonUser != null) return closestNonUser;
        else return closestActor;
    }

    private void moveTowardsTarget() {
        // if (this.type == MinionType.MELEE) Console.debugLog(this.id + " is moving towards
        // target");
        if (!this.withinRange(this.target)) this.moveWithCollision(this.target.getLocation());
    }

    private Minion isInsideMinion() {
        for (Minion m :
                this.parentExt.getRoomHandler(this.room.getId()).getMinions(this.team, this.lane)) {
            if (!m.getId().equalsIgnoreCase(this.id)
                    && m.getLocation().distance(this.location) <= 0.45d) return m;
        }
        return null;
    }

    private int
            findPathIndex() { // Finds the nearest point along the defined path for the minion to
        // travel
        // to
        double[] pathX;
        double[] pathY;
        if (this.lane != 0) {
            pathX = blueBotX;
            pathY = blueBotY;
        } else {
            pathX = blueTopX;
            pathY = blueTopY;
        }
        if (this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap()) {
            pathX = practiceX;
            pathY = practiceY;
        }
        double shortestDistance = 100;
        int index = -1;
        Line2D testLine;
        if (this.movementLine == null || this.isStopped()) {
            int p2 = blueBotX.length - 1;
            if (lane == 0) p2 = blueTopX.length - 1;
            if (team == 0) {
                p2 = 0;
            } else if (this.parentExt.getRoomHandler(this.room.getId()).isPracticeMap())
                p2 = practiceX.length - 1;
            testLine = new Line2D.Float(this.location, this.getPathPoint(p2));
        } else testLine = new Line2D.Float(this.location, this.movementLine.getP2());
        for (int i = 0; i < pathX.length; i++) {
            Point2D pathPoint = new Point2D.Double(pathX[i], pathY[i]);
            if (this.facingEntity(testLine, pathPoint)) {
                if (Math.abs(this.location.distance(pathPoint)) < shortestDistance) {
                    shortestDistance = Math.abs(this.location.distance(pathPoint));
                    index = i;
                }
            }
        }
        if (Math.abs(shortestDistance) < 0.01
                && ((this.team == 0 && index + 1 != pathX.length)
                        || (this.team == 1 && index - 1 != 0))) {
            if (this.team == 1) index++;
            else index--;
        }
        if (index == -1) {
            this.movementLine = null;
            return this.findPathIndex();
        }
        return index;
    }
}

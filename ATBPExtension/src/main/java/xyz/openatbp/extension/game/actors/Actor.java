package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.Tower.TOWER_ATTACK_RANGE;
import static xyz.openatbp.extension.game.actors.UserActor.*;
import static xyz.openatbp.extension.game.effects.EffectManager.FEAR_MOVING_DISTANCE;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.IceKing;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.EffectManager;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public abstract class Actor {

    public static final float CHARM_MIN_DISTANCE = 2f;
    public static final int TELEPORT_SPEED = 100;
    public static final int CYCLOPS_REGEN_DURATION = 60000;
    public static final int CHAMPION_KILL_POINTS = 25;
    public static final int TOWER_KILL_POINTS = 100;
    private static final int HEALTH_PACK_REGEN = 15;
    public static final int BASIC_ATTACK_DELAY = 500;
    public final int BRUSH_PENALTY_DUR = 3000;

    private static final String DC_BUFF_TIER1_ID = "dc_buff_tier1";
    private static final String DC_BUFF_TIER2_ID = "dc_buff_tier2";
    private static final int DC_BUFF_DURATION = 1000 * 15 * 60;

    private static final float DC_AD_BUFF = 0.2f;
    private static final float DC_ARMOR_BUFF = 0.2f;
    private static final float DC_SPELL_RESIST_BUFF = 0.2f;
    private static final float DC_SPEED_BUFF = 0.15f;
    private static final float DC_PD_BUFF = 0.2f;

    public enum AttackType {
        PHYSICAL,
        SPELL
    }

    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected boolean dead = false;
    protected String id;
    protected Room room;
    protected int team;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected int level = 1;
    protected boolean canMove = true;
    protected double attackCooldown;
    protected ActorType actorType;
    protected String displayName = "FuzyBDragon";
    protected Map<String, Double> stats;
    protected List<ISFSObject> damageQueue = new ArrayList<>();
    protected Actor target;
    protected List<Point2D> path;
    protected int xpWorth;
    protected String bundle;
    protected Map<Actor, ISFSObject> aggressors = new HashMap<>();
    protected Map<String, Double> endGameStats = new HashMap<>();
    protected Actor charmer;
    protected Point2D fearMovePoint = null;
    protected Actor fearer;
    protected Point2D moveStartPoint;
    protected Point2D moveDestination;
    protected List<Point2D> movePointsToDest;
    protected int movePointsIndex = 0;
    protected long elapsedMoveTimeMs;
    protected long totalMoveTimeMs;
    protected int visualTargetIndex = 0;
    protected boolean isAutoAttacking = false;
    protected boolean isMoving = false;
    protected float forcedMoveSpeed = 3;
    protected boolean pickedUpHealthPack = false;
    protected Long healthPackRegenStart;
    protected boolean hasKeeothBuff = false;
    protected boolean hasGooBuff = false;
    protected EffectManager effectManager = new EffectManager(this);
    protected boolean hasCustomSwapFromPoly = false;
    protected boolean hasCustomSwapToPoly = false;
    protected boolean towerFocused = false;
    protected long lastGrobDeviceProc = 0L;
    protected boolean grobShieldActive = false;
    protected DashContext activeDash = null;
    protected MovementState movementState = MovementState.IDLE;
    protected int dashGeneration = 0;
    protected long brushPenaltyTime = 0L;
    protected boolean isInsideBrush = false;
    protected float lastSyncedMoveSpeed = -1;
    protected long lastKilled = System.currentTimeMillis();
    protected boolean shouldTriggerAnnouncer = false;
    protected int killingSpree = 0;
    protected int multiKill = 0;
    protected List<Actor> killedChampions = new ArrayList<>();
    private Point2D lastCheckedLocation = null;
    private int stuckTicks = 0;

    public Actor getTarget() {
        return this.target;
    }

    public List<Actor> getKilledChampions() {
        return this.killedChampions;
    }

    public boolean getShouldTriggerAnnouncer() {
        return shouldTriggerAnnouncer;
    }

    public void setShouldTriggerAnnouncer(boolean announced) {
        this.shouldTriggerAnnouncer = announced;
    }

    public int getMultiKill() {
        return multiKill;
    }

    public int getKillingSpree() {
        return killingSpree;
    }

    public long getLastKilled() {
        return lastKilled;
    }

    public void setLastKilled(Long time) {
        this.lastKilled = time;
    }

    public boolean isTowerFocused() {
        return this.towerFocused;
    }

    public void setTowerFocused(boolean focused) {
        this.towerFocused = focused;
    }

    public double getGameStat(String stat) {
        return this.endGameStats.get(stat);
    }

    public boolean hasGameStat(String stat) {
        return this.endGameStats.containsKey(stat);
    }

    public void addGameStat(String stat, double value) {
        if (endGameStats.containsKey(stat)) endGameStats.put(stat, endGameStats.get(stat) + value);
        else setGameStat(stat, value);
    }

    public void setGameStat(String stat, double value) {
        this.endGameStats.put(stat, value);
    }

    public boolean isInsideBrush() {
        return isInsideBrush;
    }

    public void setInsideBrush(boolean isInsideBrush) {
        this.isInsideBrush = isInsideBrush;
    }

    public float getEffectiveMoveSpeed() {
        switch (movementState) {
            case DASHING:
            case LEAPING:
            case KNOCKBACK:
            case PULLED:
                return forcedMoveSpeed;
            default:
                return (float) getPlayerStat("speed");
        }
    }

    public int getLevel() {
        return this.level;
    }

    public MovementState getMovementState() {
        return movementState;
    }

    public void setMovementState(MovementState movementState) {
        this.movementState = movementState;
    }

    public void setCharmer(Actor charmer) {
        this.charmer = charmer;
    }

    public Actor getCharmer() {
        return this.charmer;
    }

    public void setHasKeeothBuff(boolean hasBuff) {
        this.hasKeeothBuff = hasBuff;
    }

    public void setHasGooBuff(boolean hasBuff) {
        this.hasGooBuff = hasBuff;
    }

    public void setFearer(Actor fearer) {
        this.fearer = fearer;
    }

    public Actor getFearer() {
        return this.fearer;
    }

    public boolean getGrobShieldActive() {
        return this.grobShieldActive;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public double getPHealth() {
        return currentHealth / maxHealth;
    }

    public int getHealth() {
        return (int) currentHealth;
    }

    public int getMaxHealth() {
        return (int) maxHealth;
    }

    public Point2D getLocation() {
        return this.location;
    }

    public String getId() {
        return this.id;
    }

    public int getTeam() {
        return this.team;
    }

    public Map<String, Double> getStats() {
        return this.stats == null ? initializeStats() : this.stats;
    }

    public int getOppositeTeam() {
        if (this.getTeam() == 1) return 0;
        else return 1;
    }

    public void setLocation(Point2D location) {
        this.location = location;
    }

    public String getAvatar() {
        return this.avatar;
    }

    public ActorType getActorType() {
        return this.actorType;
    }

    public void reduceAttackCooldown() {
        this.attackCooldown -= 100;
    }

    public void handleDamageTakenStat(AttackType type, double value) {
        addGameStat("damageReceivedTotal", value);
        if (type == AttackType.PHYSICAL) addGameStat("damageReceivedPhysical", value);
        else addGameStat("damageReceivedSpell", value);
    }

    public void addDamageGameStat(double value, AttackType type) {
        addGameStat("damageDealtTotal", value);
        if (type == AttackType.PHYSICAL) addGameStat("damageDealtPhysical", value);
        else addGameStat("damageDealtSpell", value);
    }

    public void increaseStat(String key, double num) {
        // Console.debugLog("Increasing " + key + " by " + num);
        stats.put(key, stats.get(key) + num);
        double statValue;

        if (key.equals("kills") || key.equals("deaths") || key.equals("assists")) {
            statValue = stats.get(key);
        } else {
            statValue = getPlayerStat(key);
        }
        ExtensionCommands.updateActorData(parentExt, room, id, key, statValue);
    }

    public Actor getRealKiller(Actor a) {
        if (!(a instanceof UserActor || a instanceof Bot)) {
            long lastAttacked = -1;
            Actor playerOrBot = null;
            for (int i = 0; i < aggressors.size(); i++) {
                Actor attacker = (Actor) aggressors.keySet().toArray()[i];
                if (attacker instanceof UserActor || attacker instanceof Bot) {
                    long attacked = aggressors.get(attacker).getLong("lastAttacked");
                    if (lastAttacked == -1 || lastAttacked < attacked) {
                        lastAttacked = attacked;
                        playerOrBot = attacker;
                    }
                }
            }
            if (playerOrBot != null) return playerOrBot;
        }
        return a;
    }

    // EFFECTS AND STATS
    public void onStateChange(ActorState state, boolean enabled) {}

    public void customSwapToPoly() {
        effectManager.handleSwapToPoly();
    }

    public void customSwapFromPoly() {}

    public boolean hasCustomSwapToPoly() {
        return hasCustomSwapToPoly;
    }

    public boolean hasCustomSwapFromPoly() {
        return hasCustomSwapFromPoly;
    }

    public boolean hasMovementCC() {
        ActorState[] cc = {
            ActorState.STUNNED, ActorState.ROOTED, ActorState.FEARED, ActorState.CHARMED
        };
        for (ActorState effect : cc) {
            if (effectManager.hasState(effect)) return true;
        }
        return false;
    }

    public boolean hasAttackCC() {
        ActorState[] cc = {
            ActorState.STUNNED, ActorState.CHARMED, ActorState.FEARED, ActorState.BLINDED
        };
        for (ActorState effect : cc) {
            if (effectManager.hasState(effect)) return true;
        }
        return false;
    }

    public double getPlayerStat(String stat) {
        double currentStat = this.stats.get(stat);
        if (stat.equals("kills") || stat.equals("deaths") || stat.equals("assists"))
            return currentStat;

        if (effectManager.isActorIgnored(this)) return currentStat;

        if (effectManager.getTempStat(stat) < 0) {
            return 0; // Stat will never drop below 0
        }

        if (stat.equals("attackSpeed")) {
            if (effectManager.getTempStat(stat) < BASIC_ATTACK_DELAY) return BASIC_ATTACK_DELAY;
        }
        return effectManager.getTempStat(stat);
    }

    public double getStat(String stat) {
        return this.stats.get(stat);
    }

    public void setStat(String key, double value) {
        this.stats.put(key, value);
    }

    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar);
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    protected HashMap<String, Double> initializeChampStats() {
        HashMap<String, Double> stats = new HashMap<>();
        stats.put("availableSpellPoints", 1d);
        for (int i = 1; i < 6; i++) {
            stats.put("sp_category" + i, 0d);
        }
        stats.put("kills", 0d);
        stats.put("deaths", 0d);
        stats.put("assists", 0d);
        JsonNode actorStats = this.parentExt.getActorStats(this.getAvatar());
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    public boolean canAttack() {
        for (ActorState s : effectManager.getStates().keySet()) {
            if (s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED
                    || s == ActorState.POLYMORPH) {
                if (effectManager.hasState(s)) return false;
            }
        }
        if (movementState == MovementState.DASHING
                || movementState == MovementState.LEAPING
                || movementState == MovementState.KNOCKBACK
                || movementState == MovementState.PULLED) return false;

        if (this.attackCooldown < 0) this.attackCooldown = 0;
        return this.attackCooldown == 0;
    }

    public boolean isNotLeaping() {
        return !(movementState == MovementState.LEAPING);
    }

    // MOVEMENT
    public boolean canMove() {
        for (ActorState s : effectManager.getStates().keySet()) {
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED) {
                if (effectManager.hasState(s)) return false;
            }
        }
        if (movementState == MovementState.DASHING
                || movementState == MovementState.LEAPING
                || movementState == MovementState.KNOCKBACK
                || movementState == MovementState.PULLED) return false;

        if (isAutoAttacking) return false;
        return this.canMove;
    }

    public boolean canUseMovementAbility() {
        return !isAutoAttacking && !hasMovementCC() && movementState == MovementState.IDLE;
    }

    public boolean hasInterrupingCC() {
        ActorState[] states = {
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.POLYMORPH,
            ActorState.STUNNED,
            ActorState.SILENCED
        };
        for (ActorState state : states) {
            if (effectManager.hasState(state)) return true;
        }
        return false;
    }

    public boolean hasDashAttackInterruptCC() {
        ActorState[] states = {
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.POLYMORPH,
            ActorState.FEARED,
            ActorState.SILENCED,
        };
        for (ActorState state : states) {
            if (effectManager.hasState(state)) return true;
        }
        return false;
    }

    public void setCanMove(boolean move) {
        this.canMove = move;
    }

    public void stopMoving() {
        if (movementState == MovementState.KNOCKBACK || movementState == MovementState.PULLED)
            return;

        isMoving = false;
        moveStartPoint = location;
        moveDestination = location;
        movePointsToDest = new ArrayList<>();
        movePointsIndex = 0;
        elapsedMoveTimeMs = 0;
        totalMoveTimeMs = 0;
        visualTargetIndex = 0;
        lastSyncedMoveSpeed = -1f;

        ExtensionCommands.moveActor(
                parentExt, room, id, location, location, getEffectiveMoveSpeed(), false);
    }

    protected boolean isStopped() {
        return !isMoving;
    }

    public void startDash(DashContext ctx) {
        activeDash = ctx;
        movementState = ctx.isLeap() ? MovementState.LEAPING : MovementState.DASHING;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());

        Point2D dashEndPoint;
        switch (movementState) {
            case LEAPING:
                dashEndPoint =
                        rh.getPathFinder()
                                .getNonObstaclePointOrIntersection(location, ctx.getDest());
                break;

            default:
                dashEndPoint = rh.getPathFinder().getIntersectionPoint(location, ctx.getDest());
                break;
        }

        activeDash.setDest(dashEndPoint);

        int timeMs = (int) ((location.distance(dashEndPoint) / ctx.getSpeed()) * 1000);
        forcedMoveSpeed = ctx.getSpeed();

        final int generation = ++dashGeneration;
        scheduleTask(
                () -> {
                    if (dashGeneration == generation) completeDash();
                },
                timeMs);

        startMoveTo(dashEndPoint, true);
    }

    public void completeDash() {
        if (activeDash == null) return;
        DashContext ctx = activeDash;
        activeDash = null;
        movementState = MovementState.IDLE;
        if (ctx.getOnEnd() != null) ctx.getOnEnd().run();
    }

    public void interruptDash(boolean triggeredByRoot) {
        if (activeDash == null || movementState == MovementState.LEAPING) return;
        dashGeneration++; // invalidates the scheduled completeDash
        DashContext ctx = activeDash;
        activeDash = null;
        movementState = MovementState.IDLE;
        stopMoving();

        if (triggeredByRoot && ctx.getTriggerEndEffectOnRoot()) {
            if (ctx.getOnEnd() != null) ctx.getOnEnd().run();
        } else {
            if (ctx.getOnInterrupt() != null) ctx.getOnInterrupt().run();
        }
    }

    public void teleport(Point2D destination) {
        stopMoving();
        setLocation(destination);
        ExtensionCommands.moveActor(parentExt, room, id, location, location, TELEPORT_SPEED, true);
    }

    public void handleKnockback(Point2D source, float distance, float speed) {
        if (effectManager.hasState(ActorState.IMMUNITY)) {
            return;
        }

        double distToAttacker = location.distance(source);
        float dist = (float) (distance + distToAttacker);
        Point2D knockbackDest = Champion.createLineTowards(source, location, dist).getP2();

        if (activeDash != null) {
            if (activeDash.canBeRedirected()) {
                activeDash.setDest(knockbackDest);
                startMoveTo(knockbackDest, true);
            } else {
                interruptDash(false);
            }
            return;
        }

        applyForcedMovement(knockbackDest, MovementState.KNOCKBACK, speed);
    }

    public void handlePull(Point2D source, float distance, float speed) {
        if (effectManager.hasState(ActorState.IMMUNITY)) {
            return;
        }
        // opposite of knockback — line goes FROM actor TOWARD source
        Point2D pullDest = Champion.createLineTowards(location, source, distance).getP2();

        // pull always cancels dashes, no redirect
        if (activeDash != null) {
            interruptDash(false);
        }

        applyForcedMovement(pullDest, MovementState.PULLED, speed);
    }

    private void applyForcedMovement(Point2D dest, MovementState state, float speed) {
        float finalDist = (float) location.distance(dest);
        int time = (int) ((finalDist / speed) * 1000);

        movementState = state;
        forcedMoveSpeed = speed;
        final int generation = ++dashGeneration;

        scheduleTask(
                () -> {
                    if (dashGeneration == generation) {
                        movementState = MovementState.IDLE;
                    }
                },
                time);

        startMoveTo(dest, true);
    }

    public void handleCharmMovement() {
        if (isDead()) return;
        if (effectManager.hasState(ActorState.CHARMED)
                && location.distance(charmer.getLocation()) > CHARM_MIN_DISTANCE) {
            startMoveTo(charmer.getLocation(), true);
        } else if (effectManager.hasState(ActorState.CHARMED)) {
            stopMoving();
        }
    }

    public void handleFear(Actor fearer) {
        if (isDead()) return;
        if (fearMovePoint == null) {
            Point2D fearerLoc = fearer.getLocation();
            Point2D stopPoint =
                    Champion.createLineTowards(location, fearerLoc, FEAR_MOVING_DISTANCE).getP2();

            double dx = stopPoint.getX() - location.getX();
            double dy = stopPoint.getY() - location.getY();

            fearMovePoint = new Point2D.Double(location.getX() + dy, location.getY() - dx);
        }

        if (fearMovePoint.distance(location) > 0.1) {
            startMoveTo(fearMovePoint, true);
        }
    }

    public void playInterruptSoundAndIdle() {
        ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
    }

    public void stopMoving(int delay) {
        this.stopMoving();
        this.canMove = false;
        if (delay > 0) {
            parentExt
                    .getTaskScheduler()
                    .schedule(new MovementStopper(true), delay, TimeUnit.MILLISECONDS);
        } else this.canMove = true;
    }

    public void resyncMovementSpeed() {
        if (!isMoving || movementState != MovementState.IDLE) return;

        float speed = getEffectiveMoveSpeed();

        if (Math.abs(speed - lastSyncedMoveSpeed) < 0.001f) return;
        lastSyncedMoveSpeed = speed;

        double remainingDist = location.distance(moveDestination);
        double totalDist = moveStartPoint.distance(moveDestination);

        if (totalDist <= 0.001) return;

        double progress = 1.0 - (remainingDist / totalDist);
        totalMoveTimeMs = Math.max(1, (long) ((totalDist / speed) * 1000.0));
        elapsedMoveTimeMs = (long) (progress * totalMoveTimeMs);

        lastSyncedMoveSpeed = getEffectiveMoveSpeed();

        ExtensionCommands.moveActor(
                parentExt,
                room,
                id,
                location,
                movePointsToDest.get(visualTargetIndex),
                speed,
                true);
    }

    public boolean isChampion() {
        return this instanceof Bot || this instanceof UserActor;
    }

    public void startMoveTo(Point2D endPoint, boolean forcedMovement) {
        if (isAutoAttacking && !forcedMovement) return;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        PathFinder pF = rh.getPathFinder();

        movePointsToDest = pF.getMovePointsToDest(location, endPoint);

        if (movementState == MovementState.LEAPING
                && !movePointsToDest.isEmpty()
                && activeDash != null) {
            movePointsToDest.clear();
            movePointsToDest.add(activeDash.getDest());
        }

        /*if (movePointsToDest.size() > 1) {
            for (Point2D p : movePointsToDest) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "gnome_a",
                        id + Math.random(),
                        5000,
                        (float) p.getX(),
                        (float) p.getY(),
                        false,
                        team,
                        0f);
            }

        } else if (movePointsToDest.size() == 1) {
            Point2D p = movePointsToDest.get(0);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "candy_caster",
                    id + Math.random(),
                    2000,
                    (float) p.getX(),
                    (float) p.getY(),
                    false,
                    team,
                    0f);
        }*/

        movePointsIndex = 0;
        elapsedMoveTimeMs = 0;

        if (movePointsToDest.isEmpty()) {
            isMoving = false;
            return;
        }

        // skip points sitting right on top of us
        while (movePointsIndex < movePointsToDest.size()
                && location.distance(movePointsToDest.get(movePointsIndex)) < 0.001) {
            movePointsIndex++;
        }
        if (movePointsIndex >= movePointsToDest.size()) {
            isMoving = false;
            return;
        }

        moveStartPoint = location;
        moveDestination = movePointsToDest.get(movePointsIndex);

        float speed = getEffectiveMoveSpeed();
        double distance = location.distance(moveDestination);
        totalMoveTimeMs = (long) (Math.max(1, (distance / speed) * 1000.0));

        isMoving = true;

        // tell the client to go to roughly collinear point
        // instead of the immediate next waypoint
        visualTargetIndex = findVisualTargetIndex();
        ExtensionCommands.moveActor(
                parentExt,
                room,
                id,
                location,
                movePointsToDest.get(visualTargetIndex),
                speed,
                true);
    }

    public void handleMovementUpdate() {
        if (!isMoving) return;

        elapsedMoveTimeMs += 100;

        // consume one or more segments per tick — carry overflow forward
        // so short segments never cause a one-tick stall
        while (elapsedMoveTimeMs >= totalMoveTimeMs) {
            long overflow = elapsedMoveTimeMs - totalMoveTimeMs;
            location = moveDestination;

            if (movePointsToDest == null || movePointsIndex + 1 >= movePointsToDest.size()) {
                isMoving = false;
                elapsedMoveTimeMs = 0;
                return;
            }

            movePointsIndex++;
            moveStartPoint = location;
            moveDestination = movePointsToDest.get(movePointsIndex);
            double dist = location.distance(moveDestination);

            while (dist <= 0.001 && movePointsIndex + 1 < movePointsToDest.size()) {
                movePointsIndex++;
                location = moveDestination;
                moveStartPoint = location;
                moveDestination = movePointsToDest.get(movePointsIndex);
                dist = location.distance(moveDestination);
            }
            if (dist <= 0.001) {
                isMoving = false;
                elapsedMoveTimeMs = 0;
                return;
            }

            float speed = getEffectiveMoveSpeed();
            totalMoveTimeMs = Math.max(1, (long) ((dist / speed) * 1000.0));
            elapsedMoveTimeMs = overflow;

            // moved past the point the client was aiming at
            // pick a new visual target and redirect the client
            if (movePointsIndex > visualTargetIndex) {
                visualTargetIndex = findVisualTargetIndex();
                ExtensionCommands.moveActor(
                        parentExt,
                        room,
                        id,
                        location,
                        movePointsToDest.get(visualTargetIndex),
                        speed,
                        true);
            }
        }

        // interpolate within the current segment

        double progress = Math.min(1.0, (double) elapsedMoveTimeMs / totalMoveTimeMs);
        double x =
                moveStartPoint.getX() + (moveDestination.getX() - moveStartPoint.getX()) * progress;
        double y =
                moveStartPoint.getY() + (moveDestination.getY() - moveStartPoint.getY()) * progress;
        location = new Point2D.Float((float) x, (float) y);

        if (activeDash != null && activeDash.getOnTick() != null) {
            activeDash.getOnTick().run();
        }
    }

    private int findVisualTargetIndex() {
        if (movePointsToDest == null || movePointsIndex >= movePointsToDest.size() - 1) {
            return movePointsToDest.size() - 1;
        }

        double totalAngle = 0;
        int best = movePointsIndex;

        for (int i = movePointsIndex; i < movePointsToDest.size() - 1; i++) {
            Point2D a = (i == movePointsIndex) ? location : movePointsToDest.get(i - 1);
            Point2D b = movePointsToDest.get(i);
            Point2D c = movePointsToDest.get(i + 1);

            double a1 = Math.atan2(b.getY() - a.getY(), b.getX() - a.getX());
            double a2 = Math.atan2(c.getY() - b.getY(), c.getX() - b.getX());
            double diff = Math.abs(a2 - a1);
            if (diff > Math.PI) diff = 2.0 * Math.PI - diff;

            totalAngle += diff;
            if (totalAngle > Math.toRadians(10)) {
                return i; // turn happens here — visual target stops at this point
            }
            best = i + 1;
        }
        return best; // remaining path is roughly straight
    }

    protected void handleAutoUnstuck() {
        if (isMoving) {
            if (lastCheckedLocation != null && location.distance(lastCheckedLocation) < 0.05) {
                stuckTicks++;
                if (stuckTicks >= 3) { // stuck for 1.5 seconds while moving
                    Console.logWarning("Bot " + id + " appears stuck, unstucking");
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());
                    PathFinder pF = rh.getPathFinder();

                    if (!pF.isPointInsideObstacle(location) && pF.isPointInsideMap(location)) {
                        return;
                    }

                    Console.logWarning("id: " + id + " is inside obstacle or outside the map!");

                    // spiral search for nearest point that is in map, not in obstacle, and 0.5+
                    // units from any edge
                    double[] angles = new double[16];
                    for (int i = 0; i < 16; i++) angles[i] = i * (Math.PI * 2 / 16);

                    for (double r = 0.5; r <= 5.0; r += 0.5) {
                        for (double angle : angles) {
                            Point2D candidate =
                                    new Point2D.Double(
                                            location.getX() + r * Math.cos(angle),
                                            location.getY() + r * Math.sin(angle));
                            if (pF.isPointInsideMap(candidate)
                                    && !pF.isPointInsideObstacle(candidate)) {
                                Console.debugLog("Unstuck teleport dist=" + r);
                                teleport(candidate);
                                return;
                            }
                        }
                    }
                    Console.logWarning("Failed to unstuck: " + id);
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastCheckedLocation = new Point2D.Double(location.getX(), location.getY());
    }

    public void addTier1DCBuff() {
        effectManager.addEffect(
                DC_BUFF_TIER1_ID,
                "armor",
                DC_ARMOR_BUFF - 1,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                DC_BUFF_DURATION);

        effectManager.addEffect(
                DC_BUFF_TIER1_ID,
                "spellResist",
                DC_SPELL_RESIST_BUFF,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                DC_BUFF_DURATION);

        effectManager.addEffect(
                DC_BUFF_TIER1_ID,
                "speed",
                DC_SPEED_BUFF,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                DC_BUFF_DURATION);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "disconnect_buff_duo",
                DC_BUFF_DURATION,
                id + "_dcbuff1",
                true,
                "",
                false,
                false,
                team);
    }

    public void addTier2DCBuff() {
        effectManager.addEffect(
                DC_BUFF_TIER2_ID,
                "attackDamage",
                DC_AD_BUFF,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                DC_BUFF_DURATION);

        effectManager.addEffect(
                DC_BUFF_TIER2_ID,
                "spellDamage",
                DC_PD_BUFF,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                DC_BUFF_DURATION);

        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "disconnect_buff_solo",
                DC_BUFF_DURATION,
                id + "_dcbuff2",
                true,
                "",
                false,
                false,
                team);
    }

    public void applyDCBuff(int tier) {
        if (tier == 1) {
            addTier1DCBuff();

        } else if (tier == 2) {
            addTier2DCBuff();
        }
    }

    public void removeTier1DCBuff() {
        effectManager.removeAllEffectsById(DC_BUFF_TIER1_ID);
        ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff1");
    }

    public void removeTier2DCBuff() {
        effectManager.removeAllEffectsById(DC_BUFF_TIER2_ID);
        ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff2");
    }

    public void removeDCBuff(int tier) {
        if (tier == 1) {
            removeTier1DCBuff();
        } else if (tier == 2) {
            removeTier2DCBuff();
        }
    }

    public boolean isInAliveTowerRange(Actor actor, boolean allyTower) {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());

        List<Actor> towers = new ArrayList<>(rh.getTowers());
        towers.addAll(rh.getBaseTowers());

        int teamToCheck = allyTower ? actor.getOppositeTeam() : getTeam();

        towers.removeIf(t -> t.getHealth() <= 0 || t.isDead() || t.getTeam() == teamToCheck);

        for (Actor tower : towers) {
            if (actor.getLocation().distance(tower.getLocation()) <= TOWER_ATTACK_RANGE)
                return true;
        }
        return false;
    }

    protected void processHitData(Actor a, JsonNode attackData, int damage) {
        if (a.getId().contains("turret"))
            a =
                    parentExt
                            .getRoomHandler(this.room.getName())
                            .getEnemyChampion(this.team, "princessbubblegum");
        if (a.getId().contains("skully"))
            a = parentExt.getRoomHandler(this.room.getName()).getEnemyChampion(this.team, "lich");
        String precursor = "attack";
        if (attackData.has("spellName")) precursor = "spell";
        if (aggressors.containsKey(a)) {
            aggressors.get(a).putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject currentAttackData = aggressors.get(a);
            int tries = 0;
            for (String k : currentAttackData.getKeys()) {
                if (k.contains("attack")) {
                    ISFSObject attack0 = currentAttackData.getSFSObject(k);
                    if (attackData
                            .get(precursor + "Name")
                            .asText()
                            .equalsIgnoreCase(attack0.getUtfString("atkName"))) {
                        attack0.putInt("atkDamage", attack0.getInt("atkDamage") + damage);
                        aggressors.get(a).putSFSObject(k, attack0);
                        return;
                    } else tries++;
                }
            }
            String attackNumber = "";
            if (tries == 0) attackNumber = "attack1";
            else if (tries == 1) attackNumber = "attack2";
            else if (tries == 2) attackNumber = "attack3";
            ISFSObject attack1 = new SFSObject();
            attack1.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attack1.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attack1.putUtfString("atkType", attackType);
            attack1.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            aggressors.get(a).putSFSObject(attackNumber, attack1);
        } else {
            ISFSObject playerData = new SFSObject();
            playerData.putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject attackObj = new SFSObject();
            attackObj.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attackObj.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attackObj.putUtfString("atkType", attackType);
            attackObj.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            playerData.putSFSObject("attack1", attackObj);
            aggressors.put(a, playerData);
        }
    }

    public boolean isRegularAttack(JsonNode attackData) {
        if (attackData.has("spellName")
                && attackData.get("spellName").asText().equalsIgnoreCase("rattleballs_spell_1_name")
                && attackData.has("counterAttack")) {
            return false;
        }
        String[] spellNames = {"princess_bubblegum_spell_2_name", "lich_spell_4_name"};
        for (String name : spellNames) {
            if (attackData.has("spellName")
                    && attackData.get("spellName").asText().equalsIgnoreCase(name)) return false;
        }
        return true;
    }

    public boolean withinRange(Actor a) {
        if (a == null) return false;
        if (a.getActorType() == ActorType.BASE)
            return a.getLocation().distance(this.location) - 1.5f
                    <= this.getPlayerStat("attackRange");
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public int getXPWorth() {
        return this.xpWorth;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public abstract void handleKill(Actor a, JsonNode attackData);

    public void handleElectrodeGun(Actor attacker, JsonNode attackData) {
        AttackType type = getAttackType(attackData);
        if (attacker instanceof UserActor && type == AttackType.SPELL) {
            UserActor ua = (UserActor) attacker;
            int gunLevel = ChampionData.getJunkLevel(ua, "junk_2_electrode_gun");
            if (gunLevel > 0) {
                String desc =
                        "Abilities grant a stack on hit (3s CD). At 3 stacks, next ability stuns the first champion hit for 0.5/1/1.5/2s.";
                String name = "icon_electrode_gun_";
                if (ua.eGunStacks == 3) {
                    int stunDuration = 500 * gunLevel;

                    String stateId = attacker.getId() + "electrode_gun_stun";
                    effectManager.addState(ActorState.STUNNED, stateId, 0, stunDuration);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "magicman_snake_explosion",
                            1000,
                            id + "_eGunProc",
                            true,
                            "",
                            false,
                            false,
                            team);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "electrode_gun_effect", location);

                    ExtensionCommands.removeStatusIcon(
                            ua.getParentExt(), ua.player, name + ua.eGunStacks);
                    ExtensionCommands.removeFx(ua.parentExt, ua.room, ua.getId() + "_eGunBuff");

                    ua.eGunStacks = 0;
                    ExtensionCommands.addStatusIcon(
                            ua.getParentExt(),
                            ua.player,
                            name + ua.eGunStacks,
                            desc,
                            name + ua.eGunStacks,
                            E_GUN_STACK_CD);

                } else if (System.currentTimeMillis() - ua.lastEGunStack >= E_GUN_STACK_CD) {
                    ua.lastEGunStack = System.currentTimeMillis();

                    ExtensionCommands.removeStatusIcon(
                            ua.getParentExt(), ua.player, name + ua.eGunStacks);

                    ua.eGunStacks++;

                    int duration = ua.eGunStacks != 3 ? E_GUN_STACK_CD : 0;

                    ExtensionCommands.addStatusIcon(
                            ua.getParentExt(),
                            ua.player,
                            name + ua.eGunStacks,
                            desc,
                            name + ua.eGunStacks,
                            duration);

                    if (ua.eGunStacks == 3) {
                        ExtensionCommands.createActorFX(
                                ua.parentExt,
                                ua.room,
                                ua.id,
                                "electrode_gun_buff",
                                1000 * 60 * 15,
                                ua.getId() + "_eGunBuff",
                                true,
                                "",
                                false,
                                false,
                                ua.team);
                    }
                }
            }
        }
    }

    protected void handleMagicCube(UserActor attacker) {
        double cubeEffect = ChampionData.getCustomJunkStat(attacker, "junk_4_antimagic_cube");

        if (cubeEffect != -1) {
            Map<Actor, Long> procs = attacker.getMagicCubeProcs();
            if (procs.containsKey(this)) {
                long lastProc = procs.get(this);
                long timeSinceLastProc = System.currentTimeMillis() - lastProc;
                if (timeSinceLastProc >= MAGIC_CUBE_CD) {
                    applyMagicCubeDebuff(attacker, cubeEffect);
                }
            } else {
                applyMagicCubeDebuff(attacker, cubeEffect);
            }
        }
    }

    private void applyMagicCubeDebuff(UserActor attacker, double debuffValue) {
        effectManager.addEffect(
                this.id + "_magicCubeDebuff",
                "spellDamage",
                debuffValue,
                ModifierType.ADDITIVE,
                ModifierIntent.DEBUFF,
                MAGIC_CUBE_DEBUFF_DURATION);
        attacker.getMagicCubeProcs().put(this, System.currentTimeMillis());

        if (this instanceof UserActor) {
            UserActor ua = (UserActor) this;
            String desc =
                    "You spell damage is reduced by "
                            + (int) (debuffValue)
                            + " for "
                            + (MAGIC_CUBE_DEBUFF_DURATION / 1000)
                            + " seconds.";

            Champion.handleStatusIcon(
                    parentExt, ua, "junk_4_antimagic_cube", desc, MAGIC_CUBE_DEBUFF_DURATION);
        }
    }

    public void disableKeeothBuff() {
        setHasKeeothBuff(false);
        effectManager.removeAllEffectsById(id + "_keeoth_buff_ad_vamp");
        effectManager.removeAllEffectsById(id + "_keeoth_buff_ap_vamp");

        ExtensionCommands.removeFx(
                this.parentExt, this.room, this.getId() + "_" + "jungle_buff_keeoth");
    }

    public void disableGooBuff() {
        setHasGooBuff(false);
        effectManager.removeAllEffectsById(id + "_goo_buff");

        ExtensionCommands.removeFx(
                this.parentExt, this.room, this.getId() + "_" + "jungle_buff_goo");
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (a.getClass() == IceKing.class && this.hasMovementCC()) damage *= 1.1;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;

            if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                    && getAttackType(attackData) == AttackType.SPELL) {
                if (ua.getLocation().distance(this.location) < 2d) {
                    damage +=
                            (damage * ChampionData.getCustomJunkStat(ua, "junk_2_peppermint_tank"));
                    Console.debugLog("Increased damage from peppermint tank.");
                }
            }
        }

        this.currentHealth -= damage;
        if (this.currentHealth <= 0) this.currentHealth = 0;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) this.currentHealth);
        updateData.putDouble("pHealth", this.getPHealth());
        updateData.putInt("maxHealth", (int) this.maxHealth);
        ExtensionCommands.updateActorData(parentExt, this.room, updateData);
        return this.currentHealth <= 0;
    }

    public void addToDamageQueue(
            Actor attacker, double damage, JsonNode attackData, boolean dotDamage) {
        if (this.currentHealth <= 0) return;

        damage = handleLichHand(attacker, attackData, damage);

        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker instanceof UserActor
                && getAttackType(attackData) == AttackType.SPELL
                && getActorType() != ActorType.TOWER
                && getActorType() != ActorType.BASE
                && !attackData.get("spellName").asText().equalsIgnoreCase("flame cloak")) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }

        handleSai(attacker, attackData);
    }

    public double handleLichHand(Actor attacker, JsonNode attackData, double damage) {
        if (attacker instanceof UserActor) {
            UserActor ua = (UserActor) attacker;
            boolean leveledHand = ChampionData.getJunkLevel(ua, "junk_2_lich_hand") > 0;

            if (leveledHand && getAttackType(attackData) == AttackType.SPELL) {
                int lichHandStacks = ua.getLichHandStacks();
                Actor lichHandTarget = ua.getLichHandTarget();
                Long lastStackTime = ua.getLastLichHandStack();

                int MAX_STACKS = 3;
                int STACK_COOLDOWN = 1000;

                boolean cdEnded = System.currentTimeMillis() - lastStackTime >= STACK_COOLDOWN;
                double damageMultiplier = 0;

                if (lichHandTarget != null && lichHandTarget.equals(this)) {
                    damageMultiplier = lichHandStacks * 0.1;

                    if (lichHandStacks < MAX_STACKS && cdEnded) {
                        ua.setLichHandStacks(lichHandStacks + 1);
                        ua.setLastLichHandStack(System.currentTimeMillis());
                    }

                } else {
                    ua.setLichHandStacks(1);
                    ua.setLastLichHandStack(System.currentTimeMillis());
                }

                Console.debugLog("damage multi: " + damageMultiplier);

                damage = damage * (1 + damageMultiplier);
                ua.setLichHandTarget(this);
            }
        }
        return damage;
    }

    public void handleSai(Actor attacker, JsonNode attackData) {
        if (attacker instanceof UserActor && getAttackType(attackData) == AttackType.PHYSICAL) {

            UserActor ua = (UserActor) attacker;
            if (ua.hasBackpackItem("junk_1_sai")) {
                int critChance = (int) ua.getPlayerStat("criticalChance");
                Long lastSaiProc = ua.getLastSaiProcTime();

                Random random = new Random();
                int randomNumber = random.nextInt(100);
                boolean proc = randomNumber < critChance;

                int SAI_CD = UserActor.SAI_PROC_COOLDOWN;

                if (proc && System.currentTimeMillis() - lastSaiProc >= SAI_CD) {
                    ua.setLastSaiProcTime(System.currentTimeMillis());
                    double delta = ((double) critChance / 100.0);

                    effectManager.addEffect(
                            this.id + "_saiProc",
                            "armor",
                            delta,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.DEBUFF,
                            SAI_CD);
                }
            }
        }
    }

    public void addToDamageQueue(
            Actor attacker,
            double damage,
            JsonNode attackData,
            boolean dotDamage,
            String debugString) {
        if (this.currentHealth <= 0) return;
        if (attacker.getActorType() == ActorType.PLAYER)
            Console.debugLog(
                    attacker.getDisplayName()
                            + " is adding damage to "
                            + this.id
                            + " at "
                            + System.currentTimeMillis()
                            + " with "
                            + debugString);
        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker.getActorType() == ActorType.PLAYER
                && this.getAttackType(attackData) == AttackType.SPELL
                && this.getActorType() != ActorType.TOWER
                && this.getActorType() != ActorType.BASE) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }
    }

    public void handleDamageQueue() {
        List<ISFSObject> queue = new ArrayList<>(this.damageQueue);
        this.damageQueue = new ArrayList<>();
        if (this.currentHealth <= 0 || this.dead) {
            return;
        }
        for (ISFSObject data : queue) {
            Actor damager = (Actor) data.getClass("attacker");
            double damage = data.getDouble("damage");
            JsonNode attackData = (JsonNode) data.getClass("attackData");
            if (this.damaged(damager, (int) damage, attackData)) {
                if (damager.getId().contains("turret") || damager.getId().contains("skully")) {
                    int enemyTeam = damager.getTeam() == 0 ? 1 : 0;
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());

                    if (damager.getId().contains("turret")) {
                        damager = rh.getEnemyChampion(enemyTeam, "princessbubblegum");
                    } else if (damager.getId().contains("skully")) {
                        damager = rh.getEnemyChampion(enemyTeam, "lich");
                    }
                }
                damager.handleKill(this, attackData);
                this.die(damager);
                return;
            }
        }
    }

    public abstract void attack(Actor a);

    public abstract void die(Actor a);

    public abstract void update(int msRan);

    public void rangedAttack(Actor a) {
        Console.debugLog(this.id + " is using an undefined method.");
    }

    public Room getRoom() {
        return this.room;
    }

    public void preventStealth() {
        Console.debugLog("Prevent stealth");
        brushPenaltyTime = System.currentTimeMillis();
        if (effectManager.hasState(ActorState.INVISIBLE)) {
            effectManager.setState(ActorState.INVISIBLE, false);
        }
    }

    public boolean canApplyBrushInvis() {
        return System.currentTimeMillis() - brushPenaltyTime >= BRUSH_PENALTY_DUR;
    }

    public boolean canRemoveBrushInvis() {
        return !effectManager.hasState(ActorState.STEALTH);
    }

    public void handleBrush() {
        // Console.debugLog("IS INVISIBLE: " + effectManager.hasState(ActorState.INVISIBLE));
        RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());
        GameMap gameMap = GameManager.getMap(roomGroup);

        ArrayList<Path2D> brushPaths = parentExt.getBrushPaths(gameMap);
        boolean insideBrush = false;

        for (Path2D path : brushPaths) {
            if (path.contains(location)) {
                insideBrush = true;
                break;
            }
        }

        // Console.debugLog("Brush: " + insideBrush);

        if (!isInsideBrush && insideBrush) {
            setInsideBrush(true);

            if (!effectManager.hasState(ActorState.INVISIBLE) && canApplyBrushInvis()) {
                effectManager.setState(ActorState.INVISIBLE, true);
            }

            int brushId = parentExt.getBrushNum(location, brushPaths);
            ExtensionCommands.changeBrush(parentExt, room, id, brushId);
        }

        if (isInsideBrush && !insideBrush) {
            setInsideBrush(false);
            ExtensionCommands.changeBrush(parentExt, room, id, -1);

            if (canRemoveBrushInvis()) {
                effectManager.setState(ActorState.INVISIBLE, false);
            }
        }
    }

    public boolean isInvisible() {
        return effectManager.hasState(ActorState.INVISIBLE);
    }

    public void changeHealth(int delta) {
        ISFSObject data = new SFSObject();
        this.currentHealth += delta;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public void handleStructureRegen(
            Long lastAction, int TIME_REQUIRED_TO_REGEN, float REGEN_VALUE) {
        if (System.currentTimeMillis() - lastAction >= TIME_REQUIRED_TO_REGEN
                && getHealth() != maxHealth) {
            int delta = (int) (getMaxHealth() * REGEN_VALUE);
            changeHealth(delta);
        }
    }

    public void heal(int delta) {
        this.changeHealth(delta);
    }

    public void setHealth(int currentHealth, int maxHealth) {
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        ISFSObject data = new SFSObject();
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        data.putInt("health", (int) this.maxHealth);
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public void applyStopMovingDuringAttack() {
        if (parentExt.getActorData(this.getAvatar()).has("attackType")) {
            stopMoving();
            isAutoAttacking = true;
            Runnable resetIsAttacking = () -> this.isAutoAttacking = false;
            scheduleTask(resetIsAttacking, BASIC_ATTACK_DELAY);
        }
    }

    public int getMitigatedDamage(double rawDamage, AttackType attackType, Actor attacker) {
        try {
            double armor =
                    this.getPlayerStat("armor")
                            * (1 - (attacker.getPlayerStat("armorPenetration") / 100));
            double spellResist =
                    this.getPlayerStat("spellResist")
                            * (1 - (attacker.getPlayerStat("spellPenetration") / 100));
            if (armor < 0) armor = 0;
            if (spellResist < 0) spellResist = 0;
            if (armor > 65) armor = 65;
            if (spellResist > 65) spellResist = 65;
            double modifier;
            if (attackType == AttackType.PHYSICAL) {
                modifier = (100 - armor) / 100d; // Max Armor 80
            } else modifier = (100 - spellResist) / 100d; // Max Shields 70
            return (int) Math.round(rawDamage * modifier);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public double getCollisionRadius() {
        JsonNode data = parentExt.getActorData(avatar);
        if (data == null) return 0.5;
        JsonNode collisionRadius = data.get("collisionRadius");
        if (collisionRadius == null) return 0.5;
        return collisionRadius.asDouble();
    }

    protected AttackType getAttackType(JsonNode attackData) {
        if (attackData.has("spellType")) return AttackType.SPELL;
        String type = attackData.get("attackType").asText();
        if (type.equalsIgnoreCase("physical")) return AttackType.PHYSICAL;
        else return AttackType.SPELL;
    }

    public ATBPExtension getParentExt() {
        return this.parentExt;
    }

    public boolean isDead() {
        return this.dead;
    }

    public String getFrame() {
        if (this.getActorType() == ActorType.PLAYER) {
            String[] frameComponents = this.getAvatar().split("_");
            if (frameComponents.length > 1) {
                return frameComponents[0];
            } else {
                return this.getAvatar();
            }
        } else {
            return this.getAvatar();
        }
    }

    public String getSkinAssetBundle() {
        return this.parentExt.getActorData(this.avatar).get("assetBundle").asText();
    }

    public abstract void setTarget(Actor a);

    protected class RangedAttack implements Runnable {

        Actor target;
        Runnable attackRunnable;
        String projectile;
        String emitNode;

        public RangedAttack(Actor target, Runnable attackRunnable, String projectile) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
        }

        public RangedAttack(
                Actor target, Runnable attackRunnable, String projectile, String emitNode) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
            this.emitNode = emitNode;
        }

        @Override
        public void run() {
            String emit = "Bip01";
            if (this.emitNode != null) emit = this.emitNode;
            float time = (float) (target.getLocation().distance(location) / 10f);
            ExtensionCommands.createProjectileFX(
                    parentExt, room, projectile, id, target.getId(), emit, "targetNode", time);
            parentExt
                    .getTaskScheduler()
                    .schedule(attackRunnable, (int) (time * 1000), TimeUnit.MILLISECONDS);
        }
    }

    public void fireProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);

        double speed = projectile.getSpeed();

        ExtensionCommands.createProjectile(
                parentExt,
                this.room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);

        this.parentExt.getRoomHandler(this.room.getName()).addProjectile(projectile);
    }

    public String getChampionName(String avatar) {
        String[] avatarComponents = avatar.split("_");
        if (avatarComponents.length > 1) {
            return avatarComponents[0];
        } else {
            return avatar;
        }
    }

    public void basicAttackReset() {
        attackCooldown = 500;
    }

    public void removeCyclopsHealing() {
        pickedUpHealthPack = false;
        effectManager.removeAllEffectsById(id + "_CYCLOPS_REGEN");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "healthPackFX");
    }

    public void handleCyclopsHealing() {
        if (this.getHealth() != this.maxHealth) {
            this.heal((int) (this.getMaxHealth() * 0.15d));

            if (!effectManager.hasEffect(id + "_CYCLOPS_REGEN")) {
                effectManager.addEffect(
                        id + "_CYCLOPS_REGEN",
                        "healthRegen",
                        HEALTH_PACK_REGEN,
                        ModifierType.ADDITIVE,
                        ModifierIntent.BUFF,
                        CYCLOPS_REGEN_DURATION);

                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.getId(),
                        "fx_health_regen",
                        CYCLOPS_REGEN_DURATION,
                        this.id + "healthPackFX",
                        true,
                        "",
                        false,
                        false,
                        this.getTeam());
                this.pickedUpHealthPack = true;
                this.healthPackRegenStart = System.currentTimeMillis();
            }

            if (pickedUpHealthPack
                    && System.currentTimeMillis() - healthPackRegenStart
                            >= CYCLOPS_REGEN_DURATION) {
                removeCyclopsHealing();
            }
        }
    }

    public void scheduleTask(Runnable task, int timeMs) {
        parentExt.getTaskScheduler().schedule(task, timeMs, TimeUnit.MILLISECONDS);
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        public MovementStopper(boolean move) {
            this.move = move;
        }

        @Override
        public void run() {
            canMove = this.move;
        }
    }
}

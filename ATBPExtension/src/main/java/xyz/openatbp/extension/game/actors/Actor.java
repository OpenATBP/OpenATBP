package xyz.openatbp.extension.game.actors;

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
    protected boolean towerAggroCompanion = false;
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

    protected static final int BASIC_ATTACK_DELAY = 500;

    protected EffectManager effectManager = new EffectManager(this);

    protected boolean customPolySwap = false;

    protected long lastGrobDeviceProc = 0L;
    protected boolean grobShieldActive = false;

    protected DashContext activeDash = null;

    protected MovementState movementState = MovementState.IDLE;

    protected int dashGeneration = 0;

    protected long brushPenaltyTime = 0L;

    public final int BRUSH_PENALTY_DUR = 3000;

    protected boolean isInsideBrush = false;

    protected float lastSyncedMoveSpeed = -1;

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

    // EFFECTS AND STATS
    public void onStateChange(ActorState state, boolean enabled) {}

    public void customSwapToPoly() {}

    public void customSwapFromPoly() {}

    public boolean hasCustomPolySwap() {
        return customPolySwap;
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

    public void addDamageGameStat(UserActor ua, double value, AttackType type) {
        ua.addGameStat("damageDealtTotal", value);
        if (type == AttackType.PHYSICAL) ua.addGameStat("damageDealtPhysical", value);
        else ua.addGameStat("damageDealtSpell", value);
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

        startMoveTo(dashEndPoint);
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
        Point2D knockbackDest = Champion.getAbilityLine(source, location, dist).getP2();

        if (activeDash != null) {
            if (activeDash.canBeRedirected()) {
                activeDash.setDest(knockbackDest);
                startMoveTo(knockbackDest);
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
        Point2D pullDest = Champion.getAbilityLine(location, source, distance).getP2();

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

        startMoveTo(dest);
    }

    public void handleCharmMovement() {
        if (effectManager.hasState(ActorState.CHARMED)
                && location.distance(charmer.getLocation()) > CHARM_MIN_DISTANCE) {
            startMoveTo(charmer.getLocation());
        } else if (effectManager.hasState(ActorState.CHARMED)) {
            stopMoving();
        }
    }

    public void handleFear(Actor fearer) {
        if (fearMovePoint == null) {
            Point2D fearerLoc = fearer.getLocation();
            Point2D stopPoint =
                    Champion.getAbilityLine(location, fearerLoc, FEAR_MOVING_DISTANCE).getP2();

            double dx = stopPoint.getX() - location.getX();
            double dy = stopPoint.getY() - location.getY();

            fearMovePoint = new Point2D.Double(location.getX() + dy, location.getY() - dx);
        }

        if (fearMovePoint.distance(location) > 0.1) {
            startMoveTo(fearMovePoint);
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

    public void startMoveTo(Point2D endPoint) {
        if (isAutoAttacking) return;
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

            // skip zero length segments immediately
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
                            + debuffValue
                            + " for "
                            + MAGIC_CUBE_DEBUFF_DURATION
                            + ".";

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
        if (this.parentExt.getActorData(this.getAvatar()).has("attackType")) {
            this.stopMoving();
            this.isAutoAttacking = true;
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

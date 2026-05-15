package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.effects.ActorState;

public class Minion extends Actor {

    public static final float AGGRO_RANGE = 5f;
    public static final int MAX_CHASE_TIME = 1500;
    public static final float XP_RADIUS = 10f;
    public static final float COLLISION_RADIUS = 0.5f;
    public static final double MINIONS_SEPARATION = 0.15f;

    List<Point2D> blueBotLanePoints =
            List.of(
                    new Point2D.Float(36.90f, 2.31f),
                    new Point2D.Float(26.00f, 8.64f),
                    new Point2D.Float(21.69f, 12.24f),
                    new Point2D.Float(16.70f, 17.25f),
                    new Point2D.Float(3.44f, 17.81f),
                    new Point2D.Float(-9.56f, 18.76f),
                    new Point2D.Float(-21.20f, 14.78f),
                    new Point2D.Float(-28.02f, 7.19f),
                    new Point2D.Float(-33.11f, 5.46f),
                    new Point2D.Float(-36.85f, 2.33f));

    List<Point2D> blueTopLanePoints =
            List.of(
                    new Point2D.Float(36.68f, -2.56f),
                    new Point2D.Float(30.10f, -7.81f),
                    new Point2D.Float(21.46f, -12.09f),
                    new Point2D.Float(18.20f, -16.31f),
                    new Point2D.Float(-5.26f, -17.11f),
                    new Point2D.Float(-12.05f, -17.96f),
                    new Point2D.Float(-24.69f, -13.19f),
                    new Point2D.Float(-28.99f, -7.50f),
                    new Point2D.Float(-35.67f, -2.70f));

    List<Point2D> purpleBotLanePoints =
            List.of(
                    new Point2D.Float(-36.90f, 2.31f),
                    new Point2D.Float(-26.00f, 8.64f),
                    new Point2D.Float(-21.69f, 12.24f),
                    new Point2D.Float(-16.70f, 17.25f),
                    new Point2D.Float(-3.44f, 17.81f),
                    new Point2D.Float(9.56f, 18.76f),
                    new Point2D.Float(21.20f, 14.78f),
                    new Point2D.Float(28.02f, 7.19f),
                    new Point2D.Float(33.11f, 5.46f),
                    new Point2D.Float(36.85f, 2.33f));

    List<Point2D> purpleTopLanePoints =
            List.of(
                    new Point2D.Float(-36.68f, -2.56f),
                    new Point2D.Float(-30.10f, -7.81f),
                    new Point2D.Float(-21.46f, -12.09f),
                    new Point2D.Float(-18.20f, -16.31f),
                    new Point2D.Float(5.26f, -17.11f),
                    new Point2D.Float(12.05f, -17.96f),
                    new Point2D.Float(24.69f, -13.19f),
                    new Point2D.Float(28.99f, -7.50f),
                    new Point2D.Float(35.67f, -2.70f));

    List<Point2D> practiceBlueLanePoints =
            List.of(
                    new Point2D.Float(38.00f, 0.76f),
                    new Point2D.Float(33.93f, 0.53f),
                    new Point2D.Float(30.00f, -1.50f),
                    new Point2D.Float(20.68f, -1.46f),
                    new Point2D.Float(12.76f, -1.43f),
                    new Point2D.Float(7.38f, 0.10f),
                    new Point2D.Float(-4.87f, 0.10f),
                    new Point2D.Float(-15.79f, -0.95f),
                    new Point2D.Float(-24.00f, -0.79f),
                    new Point2D.Float(-33.49f, -0.67f),
                    new Point2D.Float(-38.77f, 0.06f));

    List<Point2D> practicePurpleLanePoints =
            List.of(
                    new Point2D.Float(-38.00f, 0.76f),
                    new Point2D.Float(-33.93f, 0.53f),
                    new Point2D.Float(-30.00f, -1.50f),
                    new Point2D.Float(-20.68f, -1.46f),
                    new Point2D.Float(-12.76f, -1.43f),
                    new Point2D.Float(-7.38f, 0.10f),
                    new Point2D.Float(4.87f, 0.10f),
                    new Point2D.Float(15.79f, -0.95f),
                    new Point2D.Float(24.00f, -0.79f),
                    new Point2D.Float(33.49f, -0.67f),
                    new Point2D.Float(38.77f, 0.06f));

    public enum MinionType {
        RANGED,
        MELEE,
        SUPER
    } // Type of minion

    public enum MinionState {
        PUSHING,
        ATTACKING,
        STACKED
    }

    private MinionType type;
    private final GameMap map;

    private int lane;
    private Map<UserActor, Integer> aggressors;
    private static boolean movementDebug = false;
    private long chaseTime = 0L;

    public Minion(
            ATBPExtension parentExt,
            Room room,
            GameMap gameMap,
            int team,
            int minionNum,
            int wave,
            int lane) {
        this.map = gameMap;
        this.avatar = "creep" + team;
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
        this.id = team + "creep_" + lane + typeString + wave + "_" + Math.random();
        this.lane = lane;
        this.actorType = ActorType.MINION;

        aggressors = new HashMap<>(3);
        this.stats = initializeStats();

        location = getLanePoints().get(0);
        ExtensionCommands.createActor(parentExt, room, id, getAvatar(), location, 0f, team);

        Properties props = parentExt.getConfigProperties();
        movementDebug = Boolean.parseBoolean(props.getProperty("movementDebug", "false"));
        if (movementDebug) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "_test", "gnome_a", location, 0f, team);
        }

        this.attackCooldown = this.getPlayerStat("attackSpeed");
        this.xpWorth = 7;

        forcedMoveSpeed = (float) getPlayerStat("speed");
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

        if (this.type == MinionType.RANGED) {
            Champion.DelayedRangedAttack at = new Champion.DelayedRangedAttack(this, a);
            scheduleTask(at, BASIC_ATTACK_DELAY);
        } else {
            double ad = getPlayerStat("attackDamage");
            String attack = "basicAttack";
            Champion.DelayedAttack at =
                    new Champion.DelayedAttack(parentExt, this, a, (int) ad, attack);

            scheduleTask(at, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void die(Actor a) {
        this.currentHealth = 0;
        if (this.dead) return;

        if (movementState != MovementState.KNOCKBACK && movementState != MovementState.PULLED) {
            stopMoving();
        }

        if (a instanceof Bot) {
            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            rh.addScore(a, a.getTeam(), 1);
        }

        this.dead = true;
        if (a.getActorType() == ActorType.PLAYER || a.getActorType() == ActorType.COMPANION) {
            UserActor ua = null;
            if (a.getActorType() == ActorType.COMPANION) {

                if (a.getId().contains("skully")) {
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());
                    ua = rh.getEnemyChampion(team, "lich");
                } else if (a.getId().contains("turret")) {
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());
                    ua = rh.getEnemyChampion(team, "princessbubblegum");
                } else if (a.getId().contains("mine")) {
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());
                    ua = rh.getEnemyChampion(team, "neptr");
                }
            } else ua = (UserActor) a;
            if (ua != null) {
                ua.addGameStat("minions", 1);
                this.parentExt.getRoomHandler(this.room.getName()).addScore(ua, a.getTeam(), 1);
                ExtensionCommands.knockOutActor(parentExt, this.room, this.id, ua.getId(), 30);
                ExtensionCommands.playSound(
                        this.parentExt, ua.getUser(), ua.getId(), "sfx_gems_get", this.location);
            }
        } else {
            ExtensionCommands.knockOutActor(parentExt, this.room, this.id, a.getId(), 30);

            RoomHandler rh = parentExt.getRoomHandler(room.getName());

            List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, XP_RADIUS);
            enemies.removeIf(e -> !(e instanceof UserActor || e instanceof Bot));

            for (Actor actor : enemies) {

                if (actor instanceof UserActor) {
                    UserActor ua = (UserActor) actor;
                    ua.addXP(xpWorth);
                } else if (actor instanceof Bot) {
                    Bot b = (Bot) actor;
                    b.addBotExp(xpWorth);
                }
            }
        }
        ExtensionCommands.destroyActor(parentExt, this.room, this.id);
        // this.parentExt.getRoomHandler(this.room.getName()).handleAssistXP(a,aggressors.keySet(),
        // this.xpWorth);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try {
            if (this.dead) return true;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                aggressors.put(ua, 0);
                if (ChampionData.getJunkLevel(ua, "junk_1_grape_juice_sword") > 0) {
                    double junkStat =
                            ChampionData.getCustomJunkStat(ua, "junk_1_grape_juice_sword");
                    double grapeDmg = damage * junkStat;
                    damage += grapeDmg;
                }
                if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                        && getAttackType(attackData) == AttackType.SPELL) {
                    if (ua.getLocation().distance(this.location) < 2d) {
                        double junkStat =
                                ChampionData.getCustomJunkStat(ua, "junk_2_peppermint_tank");
                        double pepDmg = damage * junkStat;
                        damage += pepDmg;
                        Console.debugLog("Increased damage from peppermint tank.");
                    }
                }
                // this.handleElectrodeGun(ua, a, damage, attackData);
            }
            if (a.getActorType() == ActorType.TOWER) {
                if (this.type == MinionType.SUPER) damage = (int) Math.round(damage * 0.25);
                else damage = (int) Math.round(damage * 0.75);
            }
            AttackType type = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage, type, a);
            if (a instanceof UserActor || a instanceof Bot) a.addDamageGameStat(newDamage, type);
            this.changeHealth(newDamage * -1);
            // Minion dies
            return currentHealth <= 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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

    private MinionState evaluateMinionState() {
        // CHASING AND ATTACKING
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemiesInAggro =
                Champion.getEnemyActorsInRadius(rh, team, location, AGGRO_RANGE);

        enemiesInAggro.removeIf(
                a ->
                        !(a instanceof UserActor
                                || a instanceof Bot
                                || a instanceof Tower
                                || a instanceof Base
                                || a instanceof Minion));
        enemiesInAggro.removeIf(Actor::isDead);

        // if enemies are in aggro range, attack best target
        if (!enemiesInAggro.isEmpty()) {
            // no target, pick a new one
            if (target == null) {
                Actor potentialTarget = getBestTarget(enemiesInAggro);
                if (potentialTarget != null) { // safety check
                    chaseTime = System.currentTimeMillis();
                    setTarget(potentialTarget);
                } else {
                    // should never happen
                    return MinionState.PUSHING;
                }
            }
            // chasing too long, pick a new target
            else if (System.currentTimeMillis() - chaseTime >= MAX_CHASE_TIME) {
                Actor potentialTarget = getBestTarget(enemiesInAggro);
                chaseTime = System.currentTimeMillis();

                if (potentialTarget != target) {
                    setTarget(getBestTarget(enemiesInAggro));
                } else if (enemiesInAggro.size() == 1) {
                    return MinionState.PUSHING;
                }
            }

            // Run separation as a side effect, still return ATTACKING
            List<Minion> nearbyMinions = new ArrayList<>(rh.getMinions());
            nearbyMinions.removeIf(
                    a ->
                            a == this
                                    || a.getLocation().distance(location) > COLLISION_RADIUS
                                    || a.getHealth() <= 0);

            if (!nearbyMinions.isEmpty()) {
                preventMinionStacking(nearbyMinions); // side effect, no state change
            }

            // return attack state, if not chasing too long, minion will attack the current target
            return MinionState.ATTACKING;
        }
        // the default state is PUSHING
        return MinionState.PUSHING;
    }

    private void executeMinionState(MinionState state) {
        switch (state) {
            case ATTACKING:
                if (target != null) {
                    double dist = location.distance(target.getLocation());
                    float attackRange = (float) getPlayerStat("attackRange");
                    if (dist <= attackRange && attackCooldown == 0) {
                        attack(target);

                    } else if (dist > attackRange && canMove()) {
                        startMoveTo(target.getLocation(), false);
                    }
                }
                break;

            case PUSHING:
                if (!isMoving) {
                    // NO !isMoving condition here makes the visual model desync with server
                    // location
                    // isMoving is essentially the answer to "has the client already been given a
                    // movement command that it's still executing?
                    Point2D laneDestination = getLaneDestination();
                    if (laneDestination != null && canMove()) {
                        startMoveTo(laneDestination, false);
                    }
                }
                break;
        }
    }

    private void preventMinionStacking(List<Minion> nearbyMinions) {
        if (nearbyMinions.isEmpty()) return;

        double avgX = 0, avgY = 0;
        for (Minion m : nearbyMinions) {
            avgX += m.getLocation().getX();
            avgY += m.getLocation().getY();
        }
        avgX /= nearbyMinions.size();
        avgY /= nearbyMinions.size();

        double dx = location.getX() - avgX;
        double dy = location.getY() - avgY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 0.001) {
            Random random = new Random();
            double angle = random.nextDouble() * 2 * Math.PI;
            dx = Math.cos(angle);
            dy = Math.sin(angle);
            dist = 1.0;
        }

        // Normalize separation direction
        dx /= dist;
        dy /= dist;

        float newX = (float) (location.getX() + dx * MINIONS_SEPARATION);
        float newY = (float) (location.getY() + dy * MINIONS_SEPARATION);

        // If we have a target, clamp the separation point to stay within attack range
        if (target != null) {
            float attackRange = (float) getPlayerStat("attackRange");
            double toTargetX = target.getLocation().getX() - newX;
            double toTargetY = target.getLocation().getY() - newY;
            double distToTarget = Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);

            if (distToTarget > attackRange) {
                // Pull the separation point back towards target so we stay in range
                double pullX = toTargetX / distToTarget;
                double pullY = toTargetY / distToTarget;
                newX = (float) (newX + pullX * (distToTarget - attackRange + 0.1f));
                newY = (float) (newY + pullY * (distToTarget - attackRange + 0.1f));
            }
        }
        if (canMove()) {
            startMoveTo(new Point2D.Float(newX, newY), false);
        }
    }

    private Actor getBestTarget(List<Actor> enemiesInAggro) {
        double distanceNonChampion = 1000, distanceChampion = 10000;
        Actor nonChampionTarget = null, championTarget = null;

        enemiesInAggro.removeIf(
                a ->
                        a.getAvatar().equals("neptr_mine")
                                || a.getAvatar().equals("choosegoose_chest"));

        for (Actor e : enemiesInAggro) {
            if (e instanceof UserActor || e instanceof Bot) {
                if (!isInvisible(e)) {
                    double distance = e.getLocation().distance(location);
                    if (distance < distanceChampion) {
                        distanceChampion = distance;
                        championTarget = e;
                    }
                }
            } else if (e instanceof Minion || e instanceof Tower || e instanceof Base) {
                double distance = e.getLocation().distance(location);
                if (distance < distanceNonChampion) {
                    distanceNonChampion = distance;
                    nonChampionTarget = e;
                }
            }
        }
        return nonChampionTarget != null ? nonChampionTarget : championTarget;
    }

    @Override
    public void update(int msRan) {
        effectManager.handleEffectsUpdate();
        this.handleDamageQueue();
        handleMovementUpdate();
        handleCharmMovement();

        MinionState state = evaluateMinionState();
        if (state != null) executeMinionState(state);

        if (dead) return;
        if ((target != null && isInvisible(target))
                || (target != null && target.getHealth() <= 0)) {
            target = null;
        }

        if (attackCooldown > 0) this.reduceAttackCooldown();
        if (attackCooldown < 0) attackCooldown = 0;

        if (msRan % 1000 == 0) {
            int xp = 5 + ((msRan / 1000) / 60);
            if (xpWorth != xp) xpWorth = xp;
        }

        if (movementDebug)
            ExtensionCommands.moveActor(
                    parentExt, room, id + "_test", this.location, this.location, 5f, false);
    }

    @Override
    public void setTarget(Actor a) {
        if (a == null) return;
        this.target = a;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            ExtensionCommands.setTarget(parentExt, ua.getUser(), this.id, ua.getId());
        }
    }

    @Override
    public void rangedAttack(Actor a) {
        String fxId = "minion_projectile_";
        if (this.team == 0) fxId += "purple";
        else fxId += "blue";
        double time = a.getLocation().distance(this.location) / 20d;
        ExtensionCommands.createProjectileFX(
                this.parentExt, this.room, fxId, this.id, a.getId(), "emitNode", "", (float) time);
        int ad = (int) getPlayerStat("attackDamage");

        Champion.DelayedAttack at =
                new Champion.DelayedAttack(parentExt, this, a, ad, "basicAttack");
        int timeMS = (int) (time * 1000);
        scheduleTask(at, timeMS);
    }

    @Override
    public double getPlayerStat(String stat) {
        int activeDCBuff = this.parentExt.getRoomHandler(this.room.getName()).getDcWeight();
        boolean hasGrapeJuice = false;
        for (UserActor ua :
                Champion.getUserActorsInRadius(
                        this.parentExt.getRoomHandler(this.room.getName()), this.location, 7f)) {
            if (ua.getTeam() == this.team
                    && ChampionData.getCustomJunkStat(ua, "junk_1_grape_juice_sword") > 0) {
                hasGrapeJuice = true;
                break;
            }
        }
        int dcBuff = 0;
        if (activeDCBuff > 0 && this.team == 1) dcBuff = activeDCBuff;
        else if (activeDCBuff < 0 && this.team == 0) dcBuff = Math.abs(activeDCBuff);
        if (stat.equalsIgnoreCase("attackDamage")) {
            double attackDamage = super.getPlayerStat(stat);
            if (dcBuff == 2) attackDamage *= 1.2f;
            if (hasGrapeJuice) attackDamage *= 1.15d;
            return attackDamage;
        } else if (stat.equalsIgnoreCase("armor")) {
            double armor = super.getPlayerStat(stat);
            if (dcBuff >= 1) armor *= 1.2f;
            if (hasGrapeJuice) armor *= 1.5f;
            return armor;
        } else if (stat.equalsIgnoreCase("spellResist")) {
            double mr = super.getPlayerStat(stat);
            if (dcBuff >= 1) mr *= 1.2f;
            if (hasGrapeJuice) mr *= 1.5f;
            return mr;
        } else if (stat.equalsIgnoreCase("speed")) {
            double speed = super.getPlayerStat(stat);
            if (dcBuff >= 1) speed *= 1.15f;
            if (hasGrapeJuice) speed += 0.3f;
            return speed;
        } else if (stat.equalsIgnoreCase("spellDamage")) {
            if (dcBuff == 2) return super.getPlayerStat(stat) * 1.2f;
        }
        return super.getPlayerStat(stat);
    }

    public boolean isInvisible(Actor a) {
        return a.getEffectManager().hasState(ActorState.INVISIBLE);
    }

    public int getLane() {
        return this.lane;
    }

    public MinionType getType() {
        return this.type;
    }

    private Point2D getLaneDestination() {
        List<Point2D> lanePoints = getLanePoints();
        if (lanePoints == null || lanePoints.isEmpty()) return null;

        double minDistance = Double.MAX_VALUE;
        int closestIndex = 0;

        for (int i = 0; i < lanePoints.size(); i++) {
            double distance = lanePoints.get(i).distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = i;
            }
        }
        // Return the next waypoint ahead, or stay at the last one
        int targetIndex = Math.min(closestIndex + 1, lanePoints.size() - 1);
        return lanePoints.get(targetIndex);
    }

    private List<Point2D> getLanePoints() {
        List<Point2D> lanePoints;

        if (map == GameMap.BATTLE_LAB) {
            if (lane == 0 && team == 0) lanePoints = purpleTopLanePoints;
            else if (lane == 0 && team == 1) lanePoints = blueTopLanePoints;
            else if (lane == 1 && team == 0) lanePoints = purpleBotLanePoints;
            else lanePoints = blueBotLanePoints;
        } else {
            lanePoints = team == 0 ? practicePurpleLanePoints : practiceBlueLanePoints;
        }
        return lanePoints;
    }
}

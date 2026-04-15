package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST;
import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST_MS;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public abstract class Bot extends Actor {
    private static final boolean MOVEMENT_DEBUG = false;
    public static final int CYCLOPS_DURATION = 60000;
    private static final float TOWER_RANGE = 6f;
    public static final int INT = 15;
    public static final int HP_PACK_REGEN = INT;
    public static final double LOW_HP_PERCENTAGE_ACTION = 0.3;
    private static final int FOUNTAIN_HEAL = 250;

    protected final boolean testing = false;

    protected int deathTime = testing ? 1 : 10;
    protected int level = 1;
    protected int xp = 0;

    protected UserActor enemy;
    protected Long enemyDmgTime = 0L;
    protected HashMap<Actor, Long> agressors = new HashMap<>();

    protected static final int BASIC_ATTACK_DELAY = 500;
    protected int qCooldownMs;
    protected int wCooldownMs;
    protected int eCooldownMs;

    protected int qGCooldownMs;
    protected int wGCooldownMs;
    protected int eGCooldownMs;

    protected int qCastDelayMS;
    protected int wCastDelayMS;
    protected int eCastDelayMS;

    protected Long lastQUse = 0L;
    protected Long lastWUse = 0L;
    protected Long lastEUse = 0L;

    protected int globalCooldown = 0;

    protected UserActor lastPlayerAttacker = null;
    protected Long lastPlayerAttackTime = 0L;
    protected Long lastAttackedByMinion = 0L;
    protected Long lastAttackedByTower = 0L;

    protected enum BotState {
        RETREATING, // go to hp packs or return to base
        FLEEING, // tower/minions are attacking the bot
        FIGHTING, // attack enemies
        ALTAR, // focus on capturing an altar
        JUNGLING, // attack jungle camp
        PUSHING, // push lane
    }

    protected enum BotRole {
        FIGHTER,
        LANE_PUSHER,
        JUNGLER,
    }

    protected Point2D altarToCapture;
    protected Point2D[] lanePath;
    protected BotMapConfig mapConfig;

    protected boolean isForcedMoving() {
        return movementState == MovementState.KNOCKBACK || movementState == MovementState.PULLED;
    }

    public Bot(
            ATBPExtension parentExt,
            Room room,
            String avatar,
            String displayName,
            int team,
            BotMapConfig mapConfig) {
        this.room = room;
        this.parentExt = parentExt;
        this.mapConfig = mapConfig;
        this.location = mapConfig.respawnPoint;
        this.avatar = avatar;
        this.displayName = displayName;
        this.id = "bot_" + avatar + "_" + team + "_" + Math.random();
        this.team = team;
        this.actorType = ActorType.COMPANION;
        this.stats = initializeStats();
        this.displayName = avatar.toUpperCase() + " BOT";
        this.xpWorth = 25;

        if (GameManager.getMap(mapConfig.roomGroup) == GameMap.CANDY_STREETS) {
            this.lanePath = mapConfig.midLanePath;
        }

        ExtensionCommands.createActor(parentExt, room, id, avatar, location, 0f, team);

        if (MOVEMENT_DEBUG) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "moveDebug", "creep1", location, 0f, 1);
        }
        levelUpStats();
        simulateBackpackLevelUp("belt_champions");
    }

    protected BotRole getBotRole() {
        return BotRole.FIGHTER; // default
    }

    @Override
    public void die(Actor a) {
        if (dead) return;
        dead = true;
        target = null;
        setCanMove(false);
        setInsideBrush(false);
        setHealth(0, getMaxHealth());

        Actor realKiller = a;

        if (a.getActorType() != ActorType.PLAYER && !agressors.isEmpty()) {
            for (Actor aggressor : agressors.keySet()) {
                if (System.currentTimeMillis() - agressors.get(aggressor) <= 10000
                        && aggressor instanceof UserActor) {
                    realKiller = aggressor;
                    UserActor player = (UserActor) realKiller;
                    player.setLastKilled(System.currentTimeMillis());
                }
            }
        }

        if (movementState != MovementState.KNOCKBACK && movementState != MovementState.PULLED) {
            stopMoving();
        }

        ExtensionCommands.knockOutActor(parentExt, room, id, realKiller.getId(), deathTime);

        Runnable respawn = this::respawn;
        parentExt.getTaskScheduler().schedule(respawn, deathTime, TimeUnit.SECONDS);

        if (realKiller.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) realKiller;
            killer.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(killer, killer.getTeam(), 25);
            killer.addXP(this.getXPWorth());
        }
    }

    protected int getSpellDamage(JsonNode attackData) {
        try {
            double dmg = attackData.get("damage").asDouble();
            double spellDMG = getPlayerStat("spellDamage");
            double dmgRatio = attackData.get("damageRatio").asDouble();

            return (int) Math.round(dmg + (spellDMG * dmgRatio));
        } catch (Exception e) {
            e.printStackTrace();
            return attackData.get("damage").asInt();
        }
    }

    protected void increaseXp(int xpValue) {
        xp += xpValue;
    }

    protected boolean isNonStructureEnemy(Actor a) {
        return (a.getTeam() != team
                && a.getActorType() != ActorType.BASE
                && a.getActorType() != ActorType.TOWER);
    }

    private void setBackpackStat(int itemNum, int value) {
        String stat = "";
        switch (itemNum) {
            case 0:
                stat = "attackDamage";
                break;
            case 1:
                stat = "spellDamage";
                break;
            case 2:
                stat = "armor";
                break;
            case 3:
                stat = "spellResist";
                break;
            case 4:
                stat = "health";
                break;
        }

        try {
            if (!stat.equals("health")) {
                double valueToSet = getStat(stat) + value;
                setStat(stat, valueToSet);
            } else {
                int newMaxHealth = getMaxHealth() + value;
                setHealth(getHealth(), newMaxHealth);
            }

        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void levelUpCooldowns() {
        int lv1Q;
        int lv1W;
        int lv1E;

        int qPerLv;
        int wPerLv;
        int ePerLv;

        switch (avatar) {
            case "finn":
                lv1Q = 10000;
                lv1W = 12000;
                lv1E = 40000;

                qPerLv = 200;
                wPerLv = 200;
                ePerLv = 1400;
                break;

            case "iceking":
                lv1Q = 10000;
                lv1W = 12000;
                lv1E = 70000;

                qPerLv = 300;
                wPerLv = 400;
                ePerLv = 2000;
                break;

            case "lemongrab":
                lv1Q = 10000;
                lv1W = 12000;
                lv1E = 45000;

                qPerLv = 200;
                wPerLv = 200;
                ePerLv = 1000;
                break;

            case "jake":
                lv1Q = 12000;
                lv1W = 14000;
                lv1E = 60000;

                qPerLv = 400;
                wPerLv = 200;
                ePerLv = 1500;
                break;
            default:
                return;
        }

        qCooldownMs = lv1Q - ((level - 1) * qPerLv);
        wCooldownMs = lv1W - ((level - 1) * wPerLv);
        eCooldownMs = lv1E - ((level - 1) * ePerLv);
    }

    public void logCooldowns() {
        Console.log("Q: " + qCooldownMs + " W: " + wCooldownMs + " E: " + eCooldownMs);
    }

    public boolean defaultAbilityCheck(int abilityNum) {
        return timeOk(abilityNum)
                && !hasInterrupingCC()
                && !isAutoAttacking
                && movementState == MovementState.IDLE;
    }

    public void simulateBackpackLevelUp(String bag) {
        // TODO: REMOVE THIS IF TAB VIEW AND END GAME SUMMARY ARE IMPLEMENTED - USE USER BAG LEVEL
        // UP METHODS
        int focusItem = -1;
        int secondaryItem = -1;
        int lastItem = -1;

        switch (avatar) {
            case "finn":
                focusItem = 0;
                secondaryItem = 2;
                lastItem = 4;
                break;

            case "iceking":
                focusItem = 1;
                secondaryItem = 0;
                lastItem = 2;
                break;

            case "jake":
                focusItem = 2;
                secondaryItem = 3;
                lastItem = 4;
                break;

            case "lemongrab":
                focusItem = 2;
                secondaryItem = 1;
                lastItem = 3;
        }

        HashMap<Integer, Integer[]> beltChampions = new HashMap<>();
        beltChampions.put(1, new Integer[] {15, 20, 10, 10, 100});
        beltChampions.put(2, new Integer[] {15, 30, 15, 10, 125});
        beltChampions.put(3, new Integer[] {30, 50, 15, 10, 125});
        beltChampions.put(4, new Integer[] {40, 100, 20, 20, 150});

        switch (bag) {
            case "belt_champions":
            default:
                switch (level) {
                    case 1:
                    case 2:
                        setBackpackStat(focusItem, beltChampions.get(level)[focusItem]);
                        break;

                    case 5:
                        setBackpackStat(focusItem, beltChampions.get(3)[focusItem]);
                        break;
                    case 7:
                        setBackpackStat(focusItem, beltChampions.get(4)[focusItem]);
                        break;

                    case 3:
                        setBackpackStat(secondaryItem, beltChampions.get(1)[focusItem]);
                        break;

                    case 4:
                        setBackpackStat(secondaryItem, beltChampions.get(2)[focusItem]);
                        break;
                    case 6:
                        setBackpackStat(secondaryItem, beltChampions.get(3)[focusItem]);
                        break;
                    case 8:
                        setBackpackStat(secondaryItem, beltChampions.get(4)[focusItem]);
                        break;

                    case 9:
                        setBackpackStat(lastItem, beltChampions.get(1)[focusItem]);
                        break;
                    case 10:
                        setBackpackStat(lastItem, beltChampions.get(2)[focusItem]);
                        break;
                }
        }
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        agressors.put(a, System.currentTimeMillis());

        handleElectrodeGun(a, attackData);
        if (a instanceof UserActor) {
            UserActor ua = (UserActor) a;
            ua.preventStealth();
        }

        if (a.getActorType() == ActorType.PLAYER && getAttackType(attackData) == AttackType.SPELL) {
            handleMagicCube((UserActor) a);
        }

        if (a instanceof UserActor) {
            UserActor ua = (UserActor) a;
            ua.checkTowerAggro(ua);

            lastPlayerAttacker = ua;
            lastPlayerAttackTime = System.currentTimeMillis();
        }

        if (a.getActorType() == ActorType.MINION) {
            lastAttackedByMinion = System.currentTimeMillis();
        }
        if (a instanceof Tower) {
            lastAttackedByTower = System.currentTimeMillis();
        }

        if (a.equals(enemy) && location.distance(a.getLocation()) <= 8) {
            enemyDmgTime = System.currentTimeMillis();
        }

        if (pickedUpHealthPack) {
            removeCyclopsHealing();
        }
        return super.damaged(a, damage, attackData);
    }

    public boolean timeOk(int ability) {
        long lastUse;
        long cd;
        switch (ability) {
            case 1:
                lastUse = lastQUse;
                cd = qCooldownMs;
                break;
            case 2:
                lastUse = lastWUse;
                cd = wCooldownMs;
                break;
            case 3:
                lastUse = lastEUse;
                cd = eCooldownMs;
                break;
            default:
                return false;
        }
        return globalCooldown <= 0 && System.currentTimeMillis() - lastUse >= cd;
    }

    private Actor getClosestActor(List<Actor> actors, boolean playerFocus) {
        Actor closestActor = null;
        Actor closestPlayer = null;
        double minActorDistance = 10000;
        double minPlayerDistance = 10000;

        for (Actor a : actors) {
            double distanceToActor = a.getLocation().distance(location);

            if (distanceToActor < minActorDistance) {
                minActorDistance = distanceToActor;
                closestActor = a;
            }

            if (a instanceof UserActor && distanceToActor < minPlayerDistance) {
                minPlayerDistance = distanceToActor;
                closestPlayer = a;
            }

            if (playerFocus && closestPlayer != null) {
                return closestPlayer;
            }
        }
        return closestActor;
    }

    private Actor getClosestMonster(List<Monster> monsters) {
        double minDistance = 10000;
        Actor target = null;

        for (Actor a : monsters) {
            double distance = a.getLocation().distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                target = a;
            }
        }
        return target;
    }

    private boolean canWinFight() {
        // Can win vs champion?
        if (System.currentTimeMillis() - lastPlayerAttackTime <= 2000) {
            boolean isUnderAnyTower = false;
            for (Point2D allyTowerLocation : mapConfig.allyTowers) {
                if (location.distance(allyTowerLocation) <= TOWER_RANGE / 2.0) {
                    isUnderAnyTower = true;
                    break;
                }
            }

            if (isUnderAnyTower
                    && lastPlayerAttacker != null
                    && lastPlayerAttacker.getLocation().distance(location) <= 3
                    && !lastPlayerAttacker.isDead()) {
                return true;
            }

            if (System.currentTimeMillis() - lastEUse >= eCooldownMs
                    && lastPlayerAttacker != null
                    && !lastPlayerAttacker.isDead()
                    && lastPlayerAttacker.getPHealth() <= getPHealth()) {
                return true;
            }

            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, 6f);
            enemies.removeIf(Actor::isInvisible);
            enemies.removeIf(a -> !(a instanceof UserActor));
            if (System.currentTimeMillis() - lastQUse >= qCooldownMs
                    && System.currentTimeMillis() - lastWUse >= wCooldownMs
                    && enemies.size() == 1
                    && enemies.get(0) != null
                    && enemies.get(0).getPHealth() <= getPHealth()) {
                return true;
            }
        }
        return false;
    }

    private void setClosestLanePath(Point2D locationToCheck) {
        GameMap gameMap = GameManager.getMap(mapConfig.roomGroup);
        if (gameMap == GameMap.CANDY_STREETS) return; // PRACTICE MODE HAS ONLY ONE LANE

        double minDistanceTop = 10000;
        for (Point2D p : mapConfig.topLanePath) {
            double distance = p.distance(locationToCheck);
            if (distance < minDistanceTop) {
                minDistanceTop = distance;
            }
        }

        double minDistanceBot = 10000;
        for (Point2D p : mapConfig.botLanePath) {
            double distance = p.distance(locationToCheck);
            if (distance < minDistanceBot) {
                minDistanceBot = distance;
            }
        }
        lanePath = minDistanceTop < minDistanceBot ? mapConfig.topLanePath : mapConfig.botLanePath;
    }

    private int getClosestWaypointIndex(Point2D locationToCheck) {
        double minDist = 10000;
        int closest = 0;

        setClosestLanePath(locationToCheck);
        for (int i = 0; i < lanePath.length; i++) {
            double dist = locationToCheck.distance(lanePath[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    protected boolean canPushToPoint(Point2D closestAllyMinion, Point2D nextPushPoint) {
        if (team == 0) return nextPushPoint.getX() <= closestAllyMinion.getX();
        else return nextPushPoint.getX() >= closestAllyMinion.getX();
    }

    protected Point2D getNextPushWaypoint() {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> actors = rh.getActors();
        actors.removeIf(a -> a.getTeam() != team || !(a instanceof Minion));

        Actor closestMinion = getClosestActor(actors, false);
        if (closestMinion == null) return null;

        Point2D closestMinionP = getClosestActor(actors, false).getLocation();

        int current = getClosestWaypointIndex(closestMinionP);
        if (current < lanePath.length - 1) {
            Point2D nextLanePoint = lanePath[current + 1];
            return canPushToPoint(closestMinionP, nextLanePoint)
                    ? nextLanePoint
                    : lanePath[current];
        }
        return lanePath[current]; // already at end
    }

    protected Point2D getNextFleeWaypoint() {
        int current = getClosestWaypointIndex(location);
        if (current > 0) {
            return lanePath[current - 1];
        }
        return mapConfig.respawnPoint; // already at start, go home
    }

    protected boolean isEnemyProtectedByTower(Actor a) {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() == team);
        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bT -> bT.getTeam() == team);
        towers.addAll(baseTowers);

        return towers.stream()
                .anyMatch(t -> t.getLocation().distance(a.getLocation()) <= TOWER_RANGE);
    }

    boolean tryJungle(RoomHandler rh) {
        // ATTACK JUNGLE CAMPS
        // TODO: Add Keeoth and Goomonster attack action, change keeoth and goo to be able to apply
        // TODO: buff to bot
        List<Actor> allies = rh.getActorsInRadius(location, 4f);
        allies.removeIf(a -> !(a instanceof Bot) || a == this || a.getTeam() != team);

        if ((level >= 3 && getPHealth() >= 0.4)
                || (level == 2 && allies.size() == 1 && getPLevel() >= 0.4)
                || (allies.size() == 2 && getPLevel() >= 0.4)) {
            List<Monster> jungleMonsters = rh.getCampMonsters();
            jungleMonsters.removeIf(jm -> jm instanceof Keeoth || jm instanceof GooMonster);

            this.target = getClosestMonster(jungleMonsters);
            return true;
        }
        return false;
    }

    boolean tryClosestEnemy(RoomHandler rh) {
        // ATTACK NEARBY ENEMIES (NON MONSTERS)
        List<Actor> nearbyEnemies =
                Champion.getEnemyActorsInRadius(rh, team, location, TOWER_RANGE);

        nearbyEnemies.removeIf(a -> a instanceof Monster);
        nearbyEnemies.removeIf(a -> isEnemyProtectedByTower(a) && a instanceof UserActor);
        nearbyEnemies.removeIf(Actor::isInvisible);

        if (!nearbyEnemies.isEmpty()) {
            this.target = getClosestActor(nearbyEnemies, true);
            return true;
        }
        return false;
    }

    boolean tryPushLanes(RoomHandler rh) {
        List<Minion> allyMinions =
                rh.getMinions().stream()
                        .filter(m -> m.getTeam() == team)
                        .collect(Collectors.toList());

        if (!allyMinions.isEmpty()) {
            // PUSH LANES
            return true;
        }
        return false;
    }

    boolean tryMidAltar(RoomHandler rh) {
        // CAPTURE MID ALTAR
        int midAltarStatus = rh.getAltarStatus(mapConfig.offenseAltar);
        if (midAltarStatus != 10) { // mid altar can be captured
            altarToCapture = mapConfig.offenseAltar;
            return true;
        }
        return false;
    }

    protected BotState evaluateBotState() {
        if (getHealth() <= 0 || isDead()) return null;

        // LOW HP
        if (getPHealth() <= LOW_HP_PERCENTAGE_ACTION) {
            if (canWinFight() && lastPlayerAttacker != null && !lastPlayerAttacker.isInvisible()) {
                this.target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
            return BotState.RETREATING;
        }

        // Attacked by tower or minions
        if (System.currentTimeMillis() - lastAttackedByTower <= 2000
                || (System.currentTimeMillis() - lastAttackedByMinion <= 1000
                        && level < 3
                        && getPLevel() < 0.4)) {
            return BotState.FLEEING;
        }

        // PLAYER ATTACKED THE BOT
        if (lastPlayerAttacker != null) {
            boolean wasAttackedRecently = System.currentTimeMillis() - lastPlayerAttackTime <= 2000;
            if (wasAttackedRecently
                    && !isEnemyProtectedByTower(lastPlayerAttacker)
                    && !lastPlayerAttacker.isInvisible()) {
                target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
        }

        // DEFEND NEXUS
        RoomHandler rh = parentExt.getRoomHandler(room.getName());

        List<Actor> enemies =
                Champion.getEnemyActorsInRadius(rh, team, mapConfig.allyNexus, TOWER_RANGE);
        enemies.removeIf(Actor::isInvisible);

        if (!enemies.isEmpty()) {
            List<BaseTower> baseTowers = rh.getBaseTowers();
            baseTowers.removeIf(bT -> bT.getTeam() != team);
            if (baseTowers.isEmpty()) { // enemies can attack nexus, should defend
                this.target = getClosestActor(enemies, true);
                return BotState.FIGHTING;
            }
        }

        // DEFEND BASE TOWER
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() != team);

        if ((mapConfig.isPractice() && towers.isEmpty())
                || !mapConfig.isPractice() && towers.size() < 2) {
            List<BaseTower> baseTowers = rh.getBaseTowers();
            baseTowers.removeIf(bT -> bT.getTeam() != team);

            if (!baseTowers.isEmpty()) { // check if base tower is alive
                BaseTower bT = baseTowers.get(0);

                List<Actor> enemiesBaseTower =
                        Champion.getEnemyActorsInRadius(rh, team, bT.location, TOWER_RANGE);

                enemiesBaseTower.removeIf(Actor::isInvisible);

                if (!enemiesBaseTower.isEmpty()) { // someone is attacking the base tower, defend it
                    this.target = getClosestActor(enemiesBaseTower, true);
                    return BotState.FIGHTING;
                }
            }
        }

        // DEFEND TOWERS
        if (!towers.isEmpty()) {
            for (Tower t : towers) {
                List<Actor> enemiesUnderTower =
                        Champion.getEnemyActorsInRadius(rh, team, t.location, TOWER_RANGE);
                enemiesUnderTower.removeIf(Actor::isInvisible);
                if (!enemiesUnderTower.isEmpty()) {
                    this.target = getClosestActor(enemiesUnderTower, true);
                    return BotState.FIGHTING;
                }
            }
        }

        if (tryClosestEnemy(rh)) return BotState.FIGHTING;
        if (tryMidAltar(rh)) return BotState.ALTAR;
        if (tryPushLanes(rh)) return BotState.PUSHING;
        if (tryJungle(rh)) return BotState.JUNGLING;

        // CAPTURE DEFENSE ALTARS
        Point2D[] defenseAltars = new Point2D[2];
        defenseAltars[0] = mapConfig.defenseAltar;

        if (mapConfig.hasDefenseAlter2()) defenseAltars[1] = mapConfig.defenseAltar2;

        for (Point2D defenseAltar : defenseAltars) {
            int status = rh.getAltarStatus(defenseAltar);
            if (status != 10) {
                altarToCapture = defenseAltar;
                return BotState.ALTAR;
            }
        }

        return BotState.FLEEING;
    }

    protected void executeBotState(BotState stateToExecute, int msRan) {
        // ALL startMoveTo called in update() need to check for !isMoving to not cause desync
        // between visual model and server location

        if (isForcedMoving()) return;

        switch (stateToExecute) {
            case FIGHTING:
            case JUNGLING:
                handleFightingAbilities();
                attemptAttack(target);
                break;
            case RETREATING:
                handleRetreatAbilities();

                List<Point2D> validPacks = new ArrayList<>();
                for (String s : mapConfig.hpPacks.keySet()) {
                    int healthPackStatus = room.getVariable("spawns").getSFSObjectValue().getInt(s);
                    if (healthPackStatus == 61) validPacks.add(mapConfig.hpPacks.get(s));
                }

                Point2D closestPack = null;
                double minDistance = 10000;
                for (Point2D pack : validPacks) {
                    double distance = pack.distance(location);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestPack = pack;
                    }
                }

                if (closestPack != null && canMove()) startMoveTo(closestPack);
                else if (canMove()) startMoveTo(mapConfig.respawnPoint);

                break;
            case FLEEING:
                handleRetreatAbilities();

                Point2D fleePoint;

                if (msRan < 1000 * 60) {
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());
                    int allyTowerNum = mapConfig.allyTowers.length;
                    Random random = new Random();
                    int randomIndex = random.nextInt(allyTowerNum);

                    Point2D randomTowerPoint = mapConfig.allyTowers[randomIndex];

                    fleePoint = rh.getPathFinder().getStoppingPoint(location, randomTowerPoint, 2);

                } else {
                    fleePoint = getNextFleeWaypoint();
                }

                if (!isMoving && canMove()) {
                    startMoveTo(fleePoint);
                }
                break;
            case ALTAR:
                if (altarToCapture != null && !isMoving && canMove()) {
                    startMoveTo(altarToCapture);
                }
                break;
            case PUSHING:
                Point2D nextPushPoint = getNextPushWaypoint();
                if (nextPushPoint != null && !isMoving && canMove()) {
                    startMoveTo(nextPushPoint);
                }
                break;
        }
    }

    public abstract void handleFightingAbilities();

    public abstract void handleRetreatAbilities();

    @Override
    public void update(int msRan) {
        effectManager.handleEffectsUpdate();
        if (dead) return;

        if (globalCooldown > 0) globalCooldown -= 100;
        if (globalCooldown <= 0) globalCooldown = 0;
        if (attackCooldown > 0) attackCooldown -= 100;

        handleDamageQueue();
        handleMovementUpdate();
        handleCharmMovement();
        handleBrush();

        if (pickedUpHealthPack && getHealth() == getMaxHealth()) {
            removeCyclopsHealing();
        }

        if (MOVEMENT_DEBUG)
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id + "moveDebug",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    false);

        handleRespawnTimer(msRan);
        handleFountainRegen(msRan);

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // BOT ACTIONS
        BotState botState = evaluateBotState();
        if (botState != null) {
            executeBotState(botState, msRan);
        }
    }

    private void handleFountainRegen(int msRan) {
        if (msRan % 500 == 0) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            Point2D blueFountain = handler.getFountainsCenter().get(1);
            if (location.distance(blueFountain) <= TOWER_RANGE && getHealth() != getMaxHealth()) {
                changeHealth(FOUNTAIN_HEAL);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "fx_health_regen",
                        3000,
                        id + "_fountainRegen",
                        true,
                        "Bip01",
                        false,
                        false,
                        team);
            }
        }
    }

    private void handleRespawnTimer(int msRan) {
        if (msRan % 1000 == 0) {
            if (!testing) {
                int newDeath = 10 + ((msRan / 1000) / 60);
                if (newDeath != deathTime) deathTime = newDeath;
                if (currentHealth < maxHealth) regenHealth();
            }
        }
    }

    protected void faceTarget(Actor target) {
        if (target != null) {
            Point2D rotationPoint =
                    Champion.getAbilityLine(location, target.getLocation(), 0.75f).getP2();

            setLocation(rotationPoint);

            ExtensionCommands.moveActor(
                    parentExt, room, id, location, location, (float) getPlayerStat("speed"), true);
            stopMoving();
        }
    }

    @Override
    public boolean canMove() {
        if (isAutoAttacking || isDead()) return false;
        return super.canMove();
    }

    protected void regenHealth() {
        double healthRegen = getPlayerStat("healthRegen");
        if (currentHealth + healthRegen <= 0) healthRegen = (currentHealth - 1) * -1;
        changeHealth((int) healthRegen);
    }

    protected void attemptAttack(Actor target) {
        if (target != null) {
            if (target.isInvisible()) {
                this.target = null;
                return;
            }

            if (isForcedMoving()) return;

            this.target = target;
            if (!withinRange(target) && canMove()) {
                startMoveTo(target.getLocation());
            } else if (withinRange(target)) {
                if (!isStopped()) stopMoving();
                if (canAttack()) attack(target);
            }
        }
    }

    public void respawn() {
        setLocation(mapConfig.respawnPoint);
        setCanMove(true);
        setHealth((int) maxHealth, (int) maxHealth);
        dead = false;
        isAutoAttacking = false;
        effectManager.removeEffects();
        agressors.clear();

        Console.debugLog("Respawning " + id + " at " + location);

        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);

        effectManager.addEffect(
                this.getId() + "_respawn_speed_boost",
                "speed",
                RESPAWN_SPEED_BOOST,
                ModifierType.ADDITIVE,
                ModifierIntent.BUFF,
                RESPAWN_SPEED_BOOST_MS,
                "statusEffect_speed",
                id + "statusEffect_speed",
                "targetNode");

        ExtensionCommands.respawnActor(parentExt, room, id);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "champion_respawn_effect",
                1000,
                id + "_respawn",
                true,
                "Bip001",
                false,
                false,
                team);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (level != 10) {
            xp += a.getXPWorth();
            checkLevelUp();
        }
    }

    public double getPLevel() {
        if (level == 10) return 0d;
        double lastLevelXP = ChampionData.getLevelXP(level - 1);
        double currentLevelXP = ChampionData.getLevelXP(level);
        double delta = currentLevelXP - lastLevelXP;
        return (xp - lastLevelXP) / delta;
    }

    private void handlePassiveXP() {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        int enemyLevel = 0;
        int count = 0;

        for (UserActor ua : handler.getPlayers()) {
            if (ua.getTeam() != team) {
                count++;
                enemyLevel = ua.getLevel();
            }
        }

        int extraXp = 0;
        if (count != 0) {
            float averageLevel = (float) (enemyLevel / count);
            extraXp = (int) (2 * (averageLevel - this.level));
        }
        if (extraXp < 0) extraXp = 0;
        this.xp += 2 + extraXp;
    }

    private void checkLevelUp() {
        int level = ChampionData.getXPLevel(xp);
        if (level != this.level) {
            this.level = level;
            // Console.debugLog("level up");

            HashMap<String, Double> updateData = new HashMap<>(3);
            updateData.put("level", (double) level);
            updateData.put("xp", (double) xp);
            updateData.put("pLevel", getPLevel());

            ExtensionCommands.updateActorData(parentExt, room, id, updateData);

            levelUpStats();
            simulateBackpackLevelUp("belt_champions");
            levelUpCooldowns();
            // logCooldowns();

            ExtensionCommands.playSound(parentExt, room, id, "sfx_level_up_beam", location);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "level_up_beam",
                    1000,
                    id + "_levelUpBeam",
                    true,
                    "",
                    true,
                    false,
                    team);
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            applyStopMovingDuringAttack();
            double critChance = this.getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    crit,
                    true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            double damage = this.getPlayerStat("attackDamage");
            if (crit) {
                damage *= this.getPlayerStat("criticalDamage");
            }
            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, (int) damage, "basicAttack");
            try {
                String projectileFx =
                        this.parentExt
                                .getActorData(this.getAvatar())
                                .get("scriptData")
                                .get("projectileAsset")
                                .asText();
                if (projectileFx != null
                        && !projectileFx.isEmpty()
                        && !parentExt
                                .getActorData(this.avatar)
                                .get("attackType")
                                .asText()
                                .equalsIgnoreCase("MELEE")) {
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new RangedAttack(a, delayedAttack, projectileFx),
                                    BASIC_ATTACK_DELAY,
                                    TimeUnit.MILLISECONDS);
                } else {
                    parentExt
                            .getTaskScheduler()
                            .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
                }

            } catch (NullPointerException e) {
                // e.printStackTrace();
                parentExt
                        .getTaskScheduler()
                        .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
            }
        }
    }

    protected boolean handleAttack(Actor a) {
        if (this.attackCooldown == 0) {
            double critChance = this.getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;

            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    crit,
                    true);

            this.attackCooldown = this.getPlayerStat("attackSpeed");
            return crit;
        }
        return false;
    }

    @Override
    public void setTarget(Actor a) {}

    public abstract boolean canUseQ();

    public abstract boolean canUseW();

    public abstract boolean canUseE();

    public abstract void useQ(Point2D destination);

    public abstract void useW(Point2D destination);

    public abstract void useE(Point2D destination);

    public abstract void levelUpStats();
}

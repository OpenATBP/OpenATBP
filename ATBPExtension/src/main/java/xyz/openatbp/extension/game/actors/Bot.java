package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST;
import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST_MS;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public abstract class Bot extends Actor {
    private static boolean MOVEMENT_DEBUG = false;
    private static final float TOWER_ATTACK_RANGE = 6f;
    private static final int FOUNTAIN_HEAL = 250;

    protected final boolean testing = false;

    protected int deathTime = testing ? 1 : 10;
    protected int level = 1;
    protected int xp = 0;

    protected UserActor enemy;
    protected Long enemyDmgTime = 0L;

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

    private Point2D allyTowerReturnPoint = null;

    protected enum BotState {
        RETREATING, // go to hp packs or return to base
        FLEEING, // tower/minions are attacking the bot
        FIGHTING, // attack enemies
        ALTAR, // focus on capturing an altar
        JUNGLING, // attack jungle camp
        PUSHING, // push lane
        ALLY_TOWER
    }

    protected enum BotRole {
        FIGHTER,
        LANE_PUSHER,
        JUNGLER,
    }

    protected BotRole botRole = BotRole.FIGHTER;

    protected Point2D altarToCapture;
    protected Point2D[] lanePath;
    protected BotMapConfig mapConfig;

    protected double lowHpActionPHealth = 0.3;

    protected int canWinUnderTowerLvDif = -3;
    protected int canWinEReadyLvDif = -1;
    protected int canWinQWReadyLvDif = 0;

    protected int soloJungleLv;
    protected double soloJunglePHealth;
    protected int duoJungleLv = 2;
    protected double duoJunglePHealth;
    protected double trioJunglePHeath;
    protected int closestPlayerLvDif;

    protected float fleeMinionsAttackedPHpPerLv;
    protected float defAltarCaptureActionDist;
    protected double playerAttackedLvDif;

    private long lastChampionKill = 0L;
    private int[] spCategoryPoints = {0, 0, 0, 0, 0};
    private final String backpack = "belt_champions";

    protected boolean isForcedMoving() {
        return movementState == MovementState.KNOCKBACK || movementState == MovementState.PULLED;
    }

    public Bot(
            ATBPExtension parentExt,
            Room room,
            int botId,
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
        this.id = String.valueOf(botId); // same convention as User and UserActor
        this.team = team;
        this.actorType = ActorType.COMPANION;
        this.stats = initializeChampStats();
        this.displayName = avatar.toUpperCase() + " BOT";
        this.xpWorth = 25;

        Properties props = parentExt.getConfigProperties();
        MOVEMENT_DEBUG = Boolean.parseBoolean(props.getProperty("movementDebug", "false"));

        if (GameManager.getMap(mapConfig.roomGroup) == GameMap.CANDY_STREETS) {
            this.lanePath = mapConfig.midLanePath;
        }

        ExtensionCommands.createActor(parentExt, room, id, avatar, location, 0f, team);

        if (MOVEMENT_DEBUG) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "moveDebug", "creep1", location, 0f, 1);
        }
        levelUpStats();
        simulateBackpackLevelUp();

        endGameStats.put("spree", 0d);
        endGameStats.put("largestMulti", 0d);
    }

    protected boolean fleeOnMinionsAttacked() {
        if (level == 1) return true;

        float basePHealth = 0.6f;
        return getPHealth() <= basePHealth - (level * fleeMinionsAttackedPHpPerLv);
    }

    @Override
    public void die(Actor a) {
        if (dead) return;
        dead = true;
        setCanMove(false);
        setInsideBrush(false);
        setHealth(0, (int) maxHealth);
        target = null;
        multiKill = 0;
        killingSpree = 0;
        increaseStat("deaths", 1);

        Actor realKiller = getRealKiller(a);

        if (movementState != MovementState.KNOCKBACK && movementState != MovementState.PULLED) {
            stopMoving();
        }

        ExtensionCommands.knockOutActor(parentExt, room, id, realKiller.getId(), deathTime);

        Runnable respawn = this::respawn;
        parentExt.getTaskScheduler().schedule(respawn, deathTime, TimeUnit.SECONDS);

        if (realKiller instanceof UserActor || realKiller instanceof Bot) {
            realKiller.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(realKiller, realKiller.getTeam(), CHAMPION_KILL_POINTS);

            if (realKiller instanceof UserActor) {
                UserActor player = (UserActor) realKiller;
                player.addXP(getXPWorth());
            } else {
                Bot bot = (Bot) realKiller;
                bot.addBotExp(getXPWorth());
            }
        }

        double timeDead = this.deathTime * 1000; // needs to be converted to ms for the client
        this.addGameStat("timeDead", timeDead);
    }

    public void addBotExp(int xpWorth) {
        int level = this.level;
        xp += xpWorth;
        checkLevelUp();

        if (level != this.level) {
            HashMap<String, Double> updateData = new HashMap<>(1);
            updateData.put("level", (double) this.level);
            ExtensionCommands.updateActorData(parentExt, room, id, updateData);
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

    protected boolean isNonStructureEnemy(Actor a) {
        return (a.getTeam() != team
                && a.getActorType() != ActorType.BASE
                && a.getActorType() != ActorType.TOWER);
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

    protected boolean defaultAbilityCheck(int abilityNum) {
        return timeOk(abilityNum)
                && !hasInterrupingCC()
                && !isAutoAttacking
                && movementState == MovementState.IDLE;
    }

    public void simulateBackpackLevelUp() {

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
        beltChampions.put(1, new Integer[] {15, 20, 10, 10, 100}); // first upgrades
        beltChampions.put(2, new Integer[] {15, 30, 15, 10, 125}); // second upgrades
        beltChampions.put(3, new Integer[] {30, 50, 15, 10, 125}); // third upgrades
        beltChampions.put(4, new Integer[] {40, 100, 20, 20, 150}); // fourth upgrades

        int bagCategoryUpgraded = 0;

        switch (level) {
            case 1:
            case 2:
            case 5:
            case 7:
                bagCategoryUpgraded = focusItem;
                spCategoryPoints[focusItem] += 1;
                break;
            case 3:
            case 4:
            case 6:
            case 8:
                bagCategoryUpgraded = secondaryItem;
                spCategoryPoints[secondaryItem] += 1;
                break;
            case 9:
            case 10:
                bagCategoryUpgraded = lastItem;
                spCategoryPoints[lastItem] += 1;
                break;
        }

        String stat = getUpgradedStatString(bagCategoryUpgraded);

        int upgradeValue =
                beltChampions.get(spCategoryPoints[bagCategoryUpgraded])[bagCategoryUpgraded];

        if (stat.equals("health")) {
            setHealth(getHealth(), getMaxHealth() + upgradeValue);
        } else {
            setStat(stat, getStat(stat) + upgradeValue);
        }

        ISFSObject bagUpdateData = new SFSObject();
        bagUpdateData.putInt(
                "sp_category" + (bagCategoryUpgraded + 1), spCategoryPoints[bagCategoryUpgraded]);
        bagUpdateData.putInt("availableSpellPoints", 0);
        bagUpdateData.putUtfString("id", id);

        Console.log("Bot bag update data: " + bagUpdateData.getDump());
        ExtensionCommands.updateActorData(parentExt, room, bagUpdateData);
    }

    private String getUpgradedStatString(int bagCategoryUpgraded) {
        String stat = "";
        switch (bagCategoryUpgraded) {
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
        return stat;
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        handleElectrodeGun(a, attackData);

        AttackType type = getAttackType(attackData);
        handleDamageTakenStat(type, damage);

        if (a instanceof UserActor || a instanceof Bot) {
            a.addDamageGameStat(damage, type);
            a.addGameStat("damageDealtChamps", damage);

            if (a.isInAliveTowerRange(a, false)) a.setTowerFocused(true);
        }

        if (a.getActorType() == ActorType.COMPANION && !(a instanceof Bot)) {
            if (a.isInAliveTowerRange(a, false)) a.setTowerFocused(true);
        }

        if (a instanceof UserActor) {
            UserActor ua = (UserActor) a;
            ua.preventStealth();
        }

        if (a instanceof UserActor && type == AttackType.SPELL) {
            handleMagicCube((UserActor) a);
        }

        if (a instanceof UserActor) {

            lastPlayerAttacker = (UserActor) a;
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
        // Can win the fight with champion?
        if (System.currentTimeMillis() - lastPlayerAttackTime <= 2000) {
            boolean isUnderAnyTower = false;
            for (Point2D allyTowerLocation : mapConfig.allyTowers) {
                if (location.distance(allyTowerLocation) <= TOWER_ATTACK_RANGE / 2.0) {
                    isUnderAnyTower = true;
                    break;
                }
            }

            if (isUnderAnyTower
                    && lastPlayerAttacker != null
                    && lastPlayerAttacker.getLocation().distance(location) <= 3
                    && !lastPlayerAttacker.isDead()) {
                return (level - lastPlayerAttacker.getLevel()) >= canWinUnderTowerLvDif;
            }

            if (System.currentTimeMillis() - lastEUse >= eCooldownMs
                    && lastPlayerAttacker != null
                    && !lastPlayerAttacker.isDead()
                    && lastPlayerAttacker.getPHealth() <= getPHealth()) {
                return (level - lastPlayerAttacker.getLevel()) >= canWinEReadyLvDif;
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
                return (level - lastPlayerAttacker.getLevel()) >= canWinQWReadyLvDif;
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
                .anyMatch(t -> t.getLocation().distance(a.getLocation()) <= TOWER_ATTACK_RANGE);
    }

    private boolean canJungle(RoomHandler rh) {
        List<Actor> allies = rh.getActorsInRadius(location, 5f);
        allies.removeIf(
                a ->
                        a == this
                                || a.getTeam() != team
                                || !(a instanceof Bot || a instanceof UserActor));

        return ((level >= soloJungleLv && getPHealth() >= soloJunglePHealth)
                || (!allies.isEmpty() && getPHealth() >= duoJunglePHealth)
                || (allies.size() >= 2 && getPHealth() >= trioJunglePHeath));
    }

    private boolean tryJungle(RoomHandler rh) {
        // ATTACK JUNGLE CAMPS
        // TODO: Add Keeoth and Goomonster attack action, change keeoth and goo to be able to apply
        // TODO: buff to bot
        if (canJungle(rh)) {
            List<Monster> jungleMonsters = rh.getCampMonsters();
            jungleMonsters.removeIf(jm -> jm instanceof Keeoth || jm instanceof GooMonster);

            this.target = getClosestMonster(jungleMonsters);
            return true;
        }
        return false;
    }

    private boolean tryClosestEnemy(RoomHandler rh) {
        List<Actor> nearbyEnemies =
                Champion.getEnemyActorsInRadius(rh, team, location, TOWER_ATTACK_RANGE);

        if (!canJungle(rh)) nearbyEnemies.removeIf(e -> e instanceof Monster);

        List<Actor> potentialTowerTankers = getPotentialTowerTankers(rh);
        boolean noTankers = potentialTowerTankers.isEmpty();

        nearbyEnemies.removeIf(Actor::isInvisible);
        nearbyEnemies.removeIf(a -> isEnemyProtectedByTower(a) && a instanceof UserActor);
        nearbyEnemies.removeIf(a -> isInAliveTowerRange(a, true) && noTankers);

        if (!nearbyEnemies.isEmpty()) {
            this.target = getClosestActor(nearbyEnemies, true);
            if (target instanceof UserActor) {
                return level - target.getLevel() >= closestPlayerLvDif;
            }
            return true;
        }
        return false;
    }

    private boolean tryPushLanes(RoomHandler rh) {
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

    private boolean tryMidAltar(RoomHandler rh) {
        // CAPTURE MID ALTAR
        int midAltarStatus = rh.getAltarStatus(mapConfig.offenseAltar);
        if (midAltarStatus != 10) { // mid altar can be captured
            altarToCapture = mapConfig.offenseAltar;
            return true;
        }
        return false;
    }

    private boolean tryDefenseAltars(RoomHandler rh) {
        List<Point2D> defenseAltars = new ArrayList<>();
        defenseAltars.add(mapConfig.defenseAltar);
        if (mapConfig.hasDefenseAltar2()) defenseAltars.add(mapConfig.defenseAltar2);

        for (Point2D defenseAltar : defenseAltars) {
            if (location.distance(defenseAltar) <= defAltarCaptureActionDist) {
                int status = rh.getAltarStatus(defenseAltar);
                if (status != 10) {
                    altarToCapture = defenseAltar;
                    return true;
                }
            }
        }

        return false;
    }

    private List<Actor> getPotentialTowerTankers(RoomHandler rh) {
        List<Actor> potentialTowerTankers = new ArrayList<>(rh.getActors());

        potentialTowerTankers.removeIf(
                a ->
                        a.getActorType() == ActorType.TOWER
                                || a.getActorType() == ActorType.BASE
                                || a instanceof UserActor
                                || a instanceof Bot
                                || a.getTeam() != team
                                || !isInAliveTowerRange(a, false));
        return potentialTowerTankers;
    }

    protected boolean shouldAttackPlayer(UserActor ua) {
        return !ua.isDead()
                && !ua.isInvisible()
                && !isEnemyProtectedByTower(ua)
                && level - ua.getLevel() >= playerAttackedLvDif;
    }

    protected BotState evaluateBotState() {
        if (getHealth() <= 0 || isDead()) return null;

        // LOW HP
        if (getPHealth() <= lowHpActionPHealth) {
            if (canWinFight() && lastPlayerAttacker != null && !lastPlayerAttacker.isInvisible()) {
                this.target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
            return BotState.RETREATING;
        }

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> potentialTowerTankers = getPotentialTowerTankers(rh);

        // RETREAT FROM TOWER PUSH IF ONLY ONE MINION/COMPANION UNDER ENEMY TOWER
        if (isInAliveTowerRange(this, false) && potentialTowerTankers.size() <= 1) {
            return BotState.FLEEING;
        }

        // Attacked by tower or minions
        if (System.currentTimeMillis() - lastAttackedByTower <= 2000
                || (System.currentTimeMillis() - lastAttackedByMinion <= 1000
                        && fleeOnMinionsAttacked())) {
            return BotState.FLEEING;
        }

        // PLAYER ATTACKED THE BOT
        if (lastPlayerAttacker != null) {
            boolean wasAttackedRecently = System.currentTimeMillis() - lastPlayerAttackTime <= 2000;
            if (wasAttackedRecently && shouldAttackPlayer(lastPlayerAttacker)) {
                target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
        }

        // DEFEND NEXUS
        List<Actor> enemies =
                Champion.getEnemyActorsInRadius(rh, team, mapConfig.allyNexus, TOWER_ATTACK_RANGE);
        enemies.removeIf(Actor::isInvisible);

        if (!enemies.isEmpty()) {
            List<BaseTower> baseTowers = rh.getBaseTowers();
            baseTowers.removeIf(bT -> bT.getTeam() != team);
            if (baseTowers.isEmpty()) {
                // enemies can attack nexus, should defend
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
                        Champion.getEnemyActorsInRadius(rh, team, bT.location, TOWER_ATTACK_RANGE);

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
                        Champion.getEnemyActorsInRadius(rh, team, t.location, TOWER_ATTACK_RANGE);
                enemiesUnderTower.removeIf(Actor::isInvisible);
                if (!enemiesUnderTower.isEmpty()) {
                    this.target = getClosestActor(enemiesUnderTower, true);
                    return BotState.FIGHTING;
                }
            }
        }

        if (botRole == BotRole.FIGHTER) {
            if (tryClosestEnemy(rh)) return BotState.FIGHTING;
            if (tryMidAltar(rh)) return BotState.ALTAR;
            if (tryDefenseAltars(rh)) return BotState.ALTAR;
            if (tryJungle(rh)) return BotState.JUNGLING;
            if (tryPushLanes(rh)) return BotState.PUSHING;
        }

        if (botRole == BotRole.LANE_PUSHER) {
            if (tryClosestEnemy(rh)) return BotState.FIGHTING;
            if (tryMidAltar(rh)) return BotState.ALTAR;
            if (tryDefenseAltars(rh)) return BotState.ALTAR;
            if (tryPushLanes(rh)) return BotState.PUSHING;
            if (tryJungle(rh)) return BotState.JUNGLING;
        }

        if (botRole == BotRole.JUNGLER) {
            if (tryJungle(rh)) return BotState.JUNGLING;
            if (tryClosestEnemy(rh)) return BotState.FIGHTING;
            if (tryMidAltar(rh)) return BotState.ALTAR;
            if (tryDefenseAltars(rh)) return BotState.ALTAR;
            if (tryPushLanes(rh)) return BotState.PUSHING;
        }

        if (!towers.isEmpty()) {
            List<Actor> processList = new ArrayList<>(towers);
            Actor closestTower = getClosestActor(processList, false);

            if (closestTower != null) {
                Point2D initialDest = closestTower.getLocation();
                allyTowerReturnPoint =
                        rh.getPathFinder().getStoppingPoint(location, initialDest, 2);
                return BotState.ALLY_TOWER;
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

                if (closestPack != null && canMove()) startMoveTo(closestPack, false);
                else if (canMove()) startMoveTo(mapConfig.respawnPoint, false);

                break;
            case FLEEING:
                handleRetreatAbilities();
                Point2D fleePoint = getNextFleeWaypoint();

                if (!isMoving && canMove() && fleePoint != null) {
                    startMoveTo(fleePoint, false);
                }
                break;
            case ALTAR:
                if (altarToCapture != null && !isMoving && canMove()) {
                    startMoveTo(altarToCapture, false);
                }
                break;
            case PUSHING:
                Point2D nextPushPoint = getNextPushWaypoint();
                if (nextPushPoint != null && !isMoving && canMove()) {
                    startMoveTo(nextPushPoint, false);
                }
                break;

            case ALLY_TOWER:
                if (allyTowerReturnPoint != null && !isMoving && canMove()) {
                    if (location.distance(allyTowerReturnPoint) > 0.01f) {
                        startMoveTo(allyTowerReturnPoint, false);
                    }
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
        if (msRan == 2000) {
            // updates first upgraded item for the tab view
            int upgradedCategory = 0;
            for (int i = 0; i < spCategoryPoints.length; i++) {
                if (spCategoryPoints[i] > 0) upgradedCategory = i;
            }

            SFSObject bagData = new SFSObject();
            bagData.putInt(
                    "sp_category" + (upgradedCategory + 1), spCategoryPoints[upgradedCategory]);
            bagData.putUtfString("id", id);
            ExtensionCommands.updateActorData(parentExt, room, bagData);
        }

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

        if (msRan % 500 == 0) {
            handleFountainRegen();
            handleAutoUnstuck();
        }

        handleRespawnTimer(msRan);

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // BOT ACTIONS
        BotState botState = evaluateBotState();
        if (botState != null) {
            executeBotState(botState, msRan);
        }
    }

    private void handleFountainRegen() {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        Point2D blueFountain = handler.getFountainsCenter().get(1);
        if (location.distance(blueFountain) <= TOWER_ATTACK_RANGE
                && getHealth() != getMaxHealth()) {
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
                    Champion.createLineTowards(location, target.getLocation(), 0.75f).getP2();

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
                startMoveTo(target.getLocation(), false);
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
            addBotExp(a.getXPWorth());
            checkLevelUp();
        }

        if (a instanceof Bot || a instanceof UserActor) {
            shouldTriggerAnnouncer = true;
            killedChampions.add(a);
            killingSpree++;

            if (hasGameStat("spree")) {
                double spree = getGameStat("spree");
                if (killingSpree > spree) setGameStat("spree", killingSpree);
            }

            if (System.currentTimeMillis() - lastChampionKill <= 10000) {
                multiKill++;
                double largestMulti = getGameStat("largestMulti");
                if (multiKill > largestMulti) setGameStat("largestMulti", multiKill);
            }
            lastChampionKill = System.currentTimeMillis();
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
        addBotExp(2 + extraXp);
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
            simulateBackpackLevelUp();
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

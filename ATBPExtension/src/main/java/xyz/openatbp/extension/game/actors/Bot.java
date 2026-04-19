package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.Tower.TOWER_ATTACK_RANGE;
import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST;
import static xyz.openatbp.extension.game.actors.UserActor.RESPAWN_SPEED_BOOST_MS;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public abstract class Bot extends Actor {
    private static boolean MOVEMENT_DEBUG = false;
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

    protected enum BotAction {
        RETREATING,
        FLEEING,
        FIGHTING,
        ALTAR,
        JUNGLING,
        PUSHING,
        ALLY_TOWER
    }

    private enum FightContext {
        LOW_HP_RETALIATE,
        AGGRO_ENGAGE,
        DAMAGED_BY_CHAMPION
    }

    protected BotAction currentAction = BotAction.FIGHTING;

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

    protected float aggroRange;

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
    protected double junglingAlliesRadius;

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

        for (Actor actor : this.aggressors.keySet()) {
            if (actor.getActorType() == ActorType.PLAYER
                    && !actor.getId().equalsIgnoreCase(realKiller.getId())) {
                UserActor ua = (UserActor) actor;
                ua.addXP(getXPWorth());
                if (ChampionData.getJunkLevel(ua, "junk_5_ghost_pouch") > 0) {
                    ua.useGhostPouch();
                }
                ua.increaseStat("assists", 1);
            }

            if (actor instanceof Bot && !actor.getId().equalsIgnoreCase(realKiller.getId())) {
                Bot b = (Bot) actor;
                b.increaseStat("assists", 1);
                b.addBotExp(getXPWorth());
            }
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
        processHitData(a, attackData, damage);
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

    private Actor getBestTarget(List<Actor> enemies) {
        if (enemies.isEmpty()) return null;

        List<Actor> enemyChampions = new ArrayList<>(enemies);
        enemyChampions.removeIf(e -> !(e instanceof UserActor || e instanceof Bot));

        if (!enemyChampions.isEmpty()) {
            Actor lowestPHealthChampion = null;
            float lowestPHealth = 1;

            for (Actor a : enemyChampions) {
                float pHealth = (float) a.getPHealth();

                if (pHealth < lowestPHealth) {
                    lowestPHealth = pHealth;
                    lowestPHealthChampion = a;
                }
            }
            return lowestPHealthChampion;
        }

        Actor closestActor = null;
        float minDistance = 10000;

        for (Actor a : enemies) {
            float dist = (float) a.getLocation().distance(location);
            if (dist < minDistance) {
                minDistance = dist;
                closestActor = a;
            }
        }
        return closestActor;
    }

    private boolean canWinFight(RoomHandler rh, List<Actor> enemies, FightContext context) {
        switch (context) {
            case LOW_HP_RETALIATE:
                return canWinFightLowHp(rh, enemies);
            case AGGRO_ENGAGE:
                return canWinFightAggroEngage(rh, enemies);
            case DAMAGED_BY_CHAMPION:
                return canWinFightDamagedByChampion(rh, enemies);
        }
        return false;
    }

    private float processFightAbilities(float currentScore, float qOrWUpScore, float eUpScore) {
        if (timeOk(1) || timeOk(2)) currentScore += qOrWUpScore;
        if (timeOk(3)) currentScore += eUpScore;
        return currentScore;
    }

    private float processFightLvAndHp(
            float currentScore, List<Actor> enemyChampions, List<Actor> allyChampions) {

        for (Actor enemy : enemyChampions) {
            currentScore += (1f - (float) enemy.getPHealth()); // 0 to +1 per enemy
        }

        // Your own HP disadvantage — you're already low
        currentScore -= (1f - (float) getPHealth()); // penalty for being low

        // Level advantage/disadvantage vs each enemy champion
        for (Actor enemy : enemyChampions) {
            currentScore += (level - enemy.getLevel()) * 0.25f;
        }
        return currentScore;
    }

    private float processFightTeamDiff(
            List<Actor> allyChampions, List<Actor> enemyChampions, float currentScore) {
        return currentScore + (allyChampions.size() - enemyChampions.size());
    }

    private float processTowerDive(
            float currentScore, List<Actor> enemyChampions, List<Actor> allyChampions) {
        for (Actor ally : allyChampions) {
            currentScore += (float) (ally.getPHealth() * 0.5f);
        }
        for (Actor enemy : enemyChampions) {
            currentScore -= (float) (enemy.getPHealth() * 0.5f);
            currentScore += (level - enemy.getLevel()) * 0.3f;
        }
        return currentScore;
    }

    private float processFightTower(
            float currentScore,
            List<Actor> enemyChampions,
            List<Actor> allyChampions,
            float towerScore) {
        for (Point2D allyTowerLocation : mapConfig.allyTowers) {
            for (Actor enemy : enemyChampions) {
                if (enemy.getLocation().distance(allyTowerLocation) <= TOWER_ATTACK_RANGE) {
                    currentScore += towerScore;

                    if (isEnemyProtectedByTower(enemy)
                            && location.distance(enemy.getLocation()) <= TOWER_ATTACK_RANGE) {
                        currentScore =
                                processTowerDive(currentScore, enemyChampions, allyChampions);
                    }
                }
            }
        }
        return currentScore;
    }

    private List<Actor> getEnemyChampionsFromEnemies(List<Actor> enemies) {
        List<Actor> enemyChampions = new ArrayList<>(enemies);
        enemyChampions.removeIf(e -> !(e instanceof UserActor || e instanceof Bot));
        return enemyChampions;
    }

    private List<Actor> getAllyChampions(RoomHandler rh) {
        List<Actor> allies = Champion.getActorsInRadius(rh, location, aggroRange);
        allies.remove(this);
        allies.removeIf(a -> a.getTeam() != team);
        return allies;
    }

    private boolean canWinFightLowHp(RoomHandler rh, List<Actor> enemies) {
        float score = 0f;
        score -= 2f;

        List<Actor> allies = getAllyChampions(rh);
        List<Actor> enemyChampions = getEnemyChampionsFromEnemies(enemies);

        score = processFightAbilities(score, 0.25f, 0.75f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);
        score = processFightTower(score, enemyChampions, allies, .5f);
        return score >= 0;
    }

    private boolean canWinFightAggroEngage(RoomHandler rh, List<Actor> enemies) {
        float score = 0f;

        List<Actor> allies = getAllyChampions(rh);
        List<Actor> enemyChampions = getEnemyChampionsFromEnemies(enemies);

        score = processFightAbilities(score, 0.5f, 1f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);
        score = processFightTower(score, enemyChampions, allies, 1f);
        return score >= 0;
    }

    private boolean canWinFightDamagedByChampion(RoomHandler rh, List<Actor> enemies) {
        float score = 0f;
        score -= 0.5f;

        List<Actor> allies = getAllyChampions(rh);
        List<Actor> enemyChampions = getEnemyChampionsFromEnemies(enemies);

        score = processFightAbilities(score, 0.35f, 0.75f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);
        score = processFightTower(score, enemyChampions, allies, 0.75f);
        return score >= 0;
    }

    private boolean lowHp() {
        return getPHealth() <= lowHpActionPHealth;
    }

    private boolean fleeFromEnemyTower(RoomHandler rh) {
        List<Actor> potentialTowerTankers = getPotentialTowerTankers(rh);
        return isInAliveTowerRange(this, false) && potentialTowerTankers.size() <= 1;
    }

    private boolean damagedByMinions() {
        for (Actor a : aggressors.keySet()) {
            if (a instanceof Minion
                    && System.currentTimeMillis() - aggressors.get(a).getLong("lastAttacked")
                            <= 200) return true;
        }
        return false;
    }

    private boolean damagedByChampion() {
        return lastPlayerAttacker != null;
    }

    private boolean allyNexusUnderAttack(RoomHandler rh) {
        List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, mapConfig.allyNexus, 10f);

        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bT -> bT.getTeam() != team);

        return !enemies.isEmpty() && baseTowers.isEmpty();
    }

    private boolean allyBaseTowerUnderAttack(RoomHandler rh) {
        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bt -> bt.getTeam() != team);

        if (baseTowers.isEmpty()) return false;

        Point2D towerLoc = baseTowers.get(0).getLocation();

        List<Actor> enemies =
                Champion.getEnemyActorsInRadius(rh, team, towerLoc, TOWER_ATTACK_RANGE);
        return !enemies.isEmpty();
    }

    private boolean anyAllyTowerUnderAttack(RoomHandler rh) {
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() != team);

        for (Tower t : towers) {
            List<Actor> enemies =
                    Champion.getEnemyActorsInRadius(rh, team, t.getLocation(), TOWER_ATTACK_RANGE);
            if (!enemies.isEmpty()) return true;
        }
        return false;
    }

    private boolean midAltarNotCaptured(RoomHandler rh) {
        return rh.getAltarStatus(mapConfig.offenseAltar) != 10;
    }

    private boolean enemyInAggroRange(RoomHandler rh) {
        return !Champion.getEnemyActorsInRadius(rh, team, location, aggroRange).isEmpty();
    }

    private boolean allyCampsAlive(RoomHandler rh) {
        List<Monster> allyCamps = rh.getCampMonsters();
        if (allyCamps.isEmpty()) return false;
        allyCamps.removeIf(
                m ->
                        m.getTeam() != team
                                || m.getId().contains("keeoth")
                                || m.getId().contains("goomonster"));

        return !allyCamps.isEmpty();
    }

    private boolean enemyCampsAlive(RoomHandler rh) {
        List<Monster> enemyCamps = rh.getCampMonsters();
        if (enemyCamps.isEmpty()) return false;
        enemyCamps.removeIf(
                m ->
                        m.getTeam() == team
                                || m.getId().contains("keeoth")
                                || m.getId().contains("goomonster"));

        return !enemyCamps.isEmpty();
    }

    private boolean gooAlive(RoomHandler rh) {
        List<Monster> monsters = rh.getCampMonsters();
        if (monsters.isEmpty()) return false;
        return monsters.stream().anyMatch(m -> m.getId().contains("goomonster"));
    }

    private boolean keeothAlive(RoomHandler rh) {
        List<Monster> monsters = rh.getCampMonsters();
        if (monsters.isEmpty()) return false;
        return monsters.stream().anyMatch(m -> m.getId().contains("keeoth"));
    }

    private boolean anyDefenseAltarNotCaptured(RoomHandler rh) {
        List<Point2D> defAltars = new ArrayList<>();
        defAltars.add(mapConfig.defenseAltar);
        if (mapConfig.defenseAltar2 != null) defAltars.add(mapConfig.defenseAltar2);

        for (Point2D altarLocation : defAltars) {
            if (rh.getAltarStatus(altarLocation) != 10) return true;
        }
        return false;
    }

    private List<Actor> getEnemies(RoomHandler rh, float distance) {
        return Champion.getEnemyActorsInRadius(rh, team, location, distance);
    }

    private boolean retaliateOnChampionAttack(RoomHandler rh, List<Actor> enemies) {
        return lastPlayerAttacker != null
                && System.currentTimeMillis() - lastPlayerAttackTime <= 2000
                && canWinFight(rh, enemies, FightContext.DAMAGED_BY_CHAMPION)
                && shouldAttackPlayer(lastPlayerAttacker);
    }

    private BotAction fallbackToAllyTower(RoomHandler rh) {
        List<Actor> towers = new ArrayList<>(rh.getTowers());
        towers.addAll(rh.getBaseTowers());
        towers.removeIf(t -> t.getTeam() != team);

        if (towers.isEmpty()) return null;

        float minDistance = 10000;
        Actor closestTower = null;

        for (Actor a : towers) {
            List<Actor> towerEnemies =
                    Champion.getEnemyActorsInRadius(rh, team, a.getLocation(), TOWER_ATTACK_RANGE);
            List<Actor> towerEnemyChampions = getEnemyChampionsFromEnemies(towerEnemies);

            if (towerEnemyChampions.isEmpty()) {
                float distance = (float) location.distance(a.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestTower = a;
                }
            }
        }
        if (closestTower != null) {
            allyTowerReturnPoint = closestTower.getLocation();
        } else {
            allyTowerReturnPoint = towers.get(0).getLocation();
        }
        return BotAction.ALLY_TOWER;
    }

    protected BotAction evaluateBotAction() {
        if (getHealth() <= 0 || isDead()) return null;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemies = getEnemies(rh, aggroRange);

        // LOW HP
        if (lowHp()) {
            if (!enemies.isEmpty()) {
                if (canWinFight(rh, enemies, FightContext.LOW_HP_RETALIATE)) {
                    target = getBestTarget(enemies);
                    return BotAction.FIGHTING;
                }
            }
            return BotAction.RETREATING;
        }

        // FLEE
        if (fleeFromEnemyTower(rh)) return BotAction.FLEEING;
        if (damagedByMinions() && fleeOnMinionsAttacked()) return BotAction.FLEEING;

        // RETALIATE
        if (retaliateOnChampionAttack(rh, enemies)) {
            target = lastPlayerAttacker;
            return BotAction.FIGHTING;
        }

        // DEFEND STRUCTURES
        if (allyNexusUnderAttack(rh)) {
            List<Actor> nexusEnemies =
                    Champion.getEnemyActorsInRadius(
                            rh, team, mapConfig.allyNexus, TOWER_ATTACK_RANGE);
            target = getBestTarget(nexusEnemies);
            return BotAction.FIGHTING;
        }

        if (allyBaseTowerUnderAttack(rh)) {
            BaseTower baseTower =
                    rh.getBaseTowers().stream()
                            .filter(t -> t.getTeam() == team)
                            .findFirst()
                            .orElse(null);
            if (baseTower != null) {
                List<Actor> baseTowerEnemies =
                        Champion.getEnemyActorsInRadius(
                                rh, team, baseTower.getLocation(), TOWER_ATTACK_RANGE);
                target = getBestTarget(baseTowerEnemies);
                return BotAction.FIGHTING;
            }
        }

        if (anyAllyTowerUnderAttack(rh)) {
            List<Tower> towers = rh.getTowers();
            towers.removeIf(t -> t.getTeam() != team);

            if (!towers.isEmpty()) {
                for (Tower t : towers) {
                    List<Actor> towerEnemies =
                            Champion.getEnemyActorsInRadius(
                                    rh, team, t.getLocation(), TOWER_ATTACK_RANGE);

                    if (!towerEnemies.isEmpty()) {
                        target = getBestTarget(towerEnemies);
                        return BotAction.FIGHTING;
                    }
                }
            }
        }

        // MID ALTAR
        if (midAltarNotCaptured(rh)) {
            List<Actor> altarEnemies =
                    Champion.getEnemyActorsInRadius(rh, team, mapConfig.offenseAltar, aggroRange);
            if (canWinFight(rh, altarEnemies, FightContext.AGGRO_ENGAGE)) {
                return BotAction.ALTAR;
            }
        }

        BotAction roleAction = evaluateRoleActions(rh, enemies);
        if (roleAction != null) return roleAction;

        if (anyDefenseAltarNotCaptured(rh) && shouldCaptureDefenseAltars(rh))
            return BotAction.ALTAR;

        return fallbackToAllyTower(rh);
    }

    private boolean shouldJungle(RoomHandler rh) {
        List<Actor> allies = rh.getActorsInRadius(location, (float) junglingAlliesRadius);
        allies.removeIf(
                a ->
                        a == this
                                || a.getTeam() != team
                                || !(a instanceof Bot || a instanceof UserActor));

        if (!allies.isEmpty()) {
            for (Actor a : allies) {
                if (a instanceof Bot) {
                    Bot b = (Bot) a;
                    if (b.currentAction != BotAction.JUNGLING) return false;
                }
            }
        }

        return ((level >= soloJungleLv && getPHealth() >= soloJunglePHealth)
                || (!allies.isEmpty() && getPHealth() >= duoJunglePHealth)
                || (allies.size() >= 2 && getPHealth() >= trioJunglePHeath));
    }

    private boolean shouldAttackClosestEnemy(RoomHandler rh, List<Actor> enemies) {
        return canWinFight(rh, enemies, FightContext.AGGRO_ENGAGE);
    }

    private boolean shouldPushLanes(RoomHandler rh) {
        List<Minion> minions = rh.getMinions();
        minions.removeIf(m -> m.getTeam() != team);

        return !minions.isEmpty();
    }

    private boolean shouldCaptureDefenseAltars(RoomHandler rh) {
        List<Point2D> defAltars = new ArrayList<>();
        defAltars.add(mapConfig.defenseAltar);
        if (mapConfig.defenseAltar2 != null) defAltars.add(mapConfig.defenseAltar2);

        for (Point2D defAltarLoc : defAltars) {
            if (rh.getAltarStatus(defAltarLoc) != 10
                    && defAltarLoc.distance(location) <= defAltarCaptureActionDist) {
                altarToCapture = defAltarLoc;
                return true;
            }
        }
        return false;
    }

    private BotAction evaluateRoleActions(RoomHandler rh, List<Actor> enemies) {
        switch (botRole) {
            case FIGHTER:
                if (enemyInAggroRange(rh) && shouldAttackClosestEnemy(rh, enemies)) {
                    target = getBestTarget(enemies);
                    return BotAction.FIGHTING;
                }
                if (shouldJungle(rh) && (allyCampsAlive(rh) || enemyCampsAlive(rh))) {
                    List<Monster> monsters = rh.getCampMonsters();
                    monsters.removeIf(
                            m -> m.getId().contains("goo") || m.getId().contains("keeoth"));
                    if (!monsters.isEmpty()) {
                        target = getClosestMonster(monsters);
                        return BotAction.JUNGLING;
                    }
                }

                if (shouldPushLanes(rh)) return BotAction.PUSHING;
                break;
            case LANE_PUSHER:
                if (shouldPushLanes(rh)) return BotAction.PUSHING;

                if (enemyInAggroRange(rh) && shouldAttackClosestEnemy(rh, enemies)) {
                    target = getBestTarget(enemies);
                    return BotAction.FIGHTING;
                }
                if (shouldJungle(rh) && (allyCampsAlive(rh) || enemyCampsAlive(rh))) {
                    List<Monster> monsters = rh.getCampMonsters();
                    monsters.removeIf(
                            m -> m.getId().contains("goo") || m.getId().contains("keeoth"));
                    if (!monsters.isEmpty()) {
                        target = getClosestMonster(monsters);
                        return BotAction.JUNGLING;
                    }
                }
                break;
            case JUNGLER:
                if (shouldJungle(rh) && (allyCampsAlive(rh) || enemyCampsAlive(rh))) {
                    List<Monster> monsters = rh.getCampMonsters();
                    monsters.removeIf(
                            m -> m.getId().contains("goo") || m.getId().contains("keeoth"));
                    if (!monsters.isEmpty()) {
                        target = getClosestMonster(monsters);
                        return BotAction.JUNGLING;
                    }
                }

                if (enemyInAggroRange(rh) && shouldAttackClosestEnemy(rh, enemies)) {
                    target = getBestTarget(enemies);
                    return BotAction.FIGHTING;
                }

                if (shouldPushLanes(rh)) return BotAction.PUSHING;
        }
        return null;
    }

    protected void executeBotAction(BotAction stateToExecute) {
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
        currentAction = evaluateBotAction();
        if (currentAction != null) executeBotAction(currentAction);

        List<Actor> actorsToRemove = new ArrayList<>(this.aggressors.size());
        for (Actor a : this.aggressors.keySet()) {
            ISFSObject damageData = this.aggressors.get(a);
            if (System.currentTimeMillis() > damageData.getLong("lastAttacked") + 5000)
                actorsToRemove.add(a);
        }
        for (Actor a : actorsToRemove) {
            this.aggressors.remove(a);
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

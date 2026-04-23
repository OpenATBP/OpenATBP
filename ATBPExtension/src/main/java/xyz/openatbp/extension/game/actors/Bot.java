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
    private static boolean BOT_RESPAWN_DEBUG = false;
    private static final int FOUNTAIN_HEAL = 250;

    protected int deathTime;
    protected int level = 1;
    protected int xp = 0;

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

    private enum AltarType {
        MID,
        SIDE
    }

    protected BotRole botRole = BotRole.FIGHTER;

    protected HashMap<AltarType, Point2D> altarToCapture;
    protected Point2D[] lanePath;
    protected BotMapConfig mapConfig;

    private long lastChampionKill = 0L;
    private int[] spCategoryPoints = {0, 0, 0, 0, 0};
    private final String backpack = "belt_champions";

    private float aggroRange;
    private long lastTargetedByTower = 0L;
    private long lastAttackedByMinions = 0L;

    private boolean commitSideAltar = false;
    private boolean commitJungleCamp = false;

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
        BOT_RESPAWN_DEBUG = Boolean.parseBoolean(props.getProperty("botRespawnDebug", "false"));

        this.deathTime = BOT_RESPAWN_DEBUG ? 1 : 10;
        this.aggroRange = 5f;

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

    public abstract boolean canUseQ();

    public abstract boolean canUseW();

    public abstract boolean canUseE();

    public abstract void useQ(Point2D destination);

    public abstract void useW(Point2D destination);

    public abstract void useE(Point2D destination);

    public abstract void levelUpStats();

    @Override
    public void update(int msRan) {
        effectManager.handleEffectsUpdate();
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

        handleDamagedByMinions();
        handleTargetedByTower();
        handleJungleCommitReset();

        if (pickedUpHealthPack && getHealth() == getMaxHealth()) {
            removeCyclopsHealing();
        }

        if (altarToCapture != null) {
            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            Iterator<Map.Entry<AltarType, Point2D>> iterator = altarToCapture.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<AltarType, Point2D> entry = iterator.next();

                if (entry.getKey() == AltarType.SIDE) {
                    if (rh.getAltarStatus(entry.getValue()) == 10) {
                        iterator.remove();
                        commitSideAltar = false;
                    }
                }
            }
        }

        if (dead) return;

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
        if (!isDead()) {
            currentAction = evaluateBotAction();
            if (currentAction != null) {
                executeBotAction(currentAction);
            } else if (isMoving) stopMoving();
        }

        if (currentAction != null) {
            // Console.debugLog("Avatar: " + getAvatar() + " current action: " +
            // currentAction.name());
        }

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

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            preventStealth();
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

    @Override
    public void setTarget(Actor a) {}

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
            lastPlayerAttacker = ua;
            lastPlayerAttackTime = System.currentTimeMillis();
        }

        if (a instanceof UserActor && type == AttackType.SPELL) {
            handleMagicCube((UserActor) a);
        }

        if (pickedUpHealthPack) {
            removeCyclopsHealing();
        }
        processHitData(a, attackData, damage);
        preventStealth();
        return super.damaged(a, damage, attackData);
    }

    protected boolean fleeOnMinionsAttacked() {
        float hpScore = (float) getPHealth();
        float armorScore = (float) Math.min(getPlayerStat("armor") / 100f, 0.3f);
        float levelScore = level / 40f;

        float score = hpScore + armorScore + levelScore;
        return score < 1.0f;
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

    private void handleJungleCommitReset() {
        if (!(target instanceof Monster)) return;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        String id = target.getId();
        boolean shouldReset;

        // Check for triplet camps first
        if (id.contains("owl")) {
            shouldReset = rh.tripletCampCleared("owl");
        } else if (id.contains("gnome")) {
            shouldReset = rh.tripletCampCleared("gnome");
        } else {
            // Standard health check for everything else
            shouldReset = target.getHealth() <= 0;
        }

        if (shouldReset) {
            commitJungleCamp = false;
            target = null;
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
        // Console.log("Q: " + qCooldownMs + " W: " + wCooldownMs + " E: " + eCooldownMs);
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

        // .log("Bot bag update data: " + bagUpdateData.getDump());
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
        /*if (team == 0) return nextPushPoint.getX() <= closestAllyMinion.getX();
        else return nextPushPoint.getX() >= closestAllyMinion.getX();*/
        return closestAllyMinion.distance(nextPushPoint) <= aggroRange;
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

    protected boolean isPointInTowerRadius(Point2D point, int team) {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() != team);
        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bT -> bT.getTeam() != team);
        towers.addAll(baseTowers);

        for (Tower t : towers) {
            if (t.getLocation().distance(point) <= TOWER_ATTACK_RANGE) return true;
        }
        return false;
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

        currentScore -= (1f - (float) getPHealth());

        for (Actor enemy : enemyChampions) {
            currentScore += (level - enemy.getLevel()) * 0.25f;
        }
        return currentScore;
    }

    private float processFightTeamDiff(
            List<Actor> allyChampions, List<Actor> enemyChampions, float currentScore) {
        return currentScore + (allyChampions.size() - enemyChampions.size());
    }

    private List<Actor> filterEnemiesForChampions(List<Actor> enemies) {
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
        List<Actor> enemyChampions = filterEnemiesForChampions(enemies);

        score = processFightAbilities(score, 0.25f, 0.75f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);

        // Console.debugLog("Can win fight low hp: " + (score >= 0));
        return score >= 0;
    }

    private boolean canWinFightAggroEngage(RoomHandler rh, List<Actor> enemies) {
        float score = 0f;

        List<Actor> allies = getAllyChampions(rh);
        List<Actor> enemyChampions = filterEnemiesForChampions(enemies);

        score = processFightAbilities(score, 0.5f, 1f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);

        // Console.debugLog("Can win fight aggro engage: " + (score >= 0));
        return score >= 0;
    }

    private boolean canWinFightDamagedByChampion(RoomHandler rh, List<Actor> enemies) {
        float score = 0f;
        score -= 0.5f;

        List<Actor> allies = getAllyChampions(rh);
        List<Actor> enemyChampions = filterEnemiesForChampions(enemies);

        score = processFightAbilities(score, 0.35f, 0.75f);
        score = processFightLvAndHp(score, enemyChampions, allies);
        score = processFightTeamDiff(allies, enemyChampions, score);

        // Console.debugLog("Can win fight damaged by champion: " + (score >= 0));
        return score >= 0;
    }

    private boolean lowHp() {
        return getPHealth() <= 0.3;
    }

    private void handleTargetedByTower() {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> towers = new ArrayList<>(rh.getTowers());
        towers.addAll(rh.getBaseTowers());
        towers.removeIf(t -> t.getTeam() == team);

        for (Actor a : towers) {
            if (a.getTarget() != null && a.getTarget() == this) {
                lastTargetedByTower = System.currentTimeMillis();
            }
        }
    }

    private boolean handleDamagedByMinions() {
        for (Actor a : aggressors.keySet()) {
            if (a instanceof Minion) {
                lastAttackedByMinions = System.currentTimeMillis();
            }
        }
        return false;
    }

    private boolean damagedByChampion() {
        return lastPlayerAttacker != null
                && (System.currentTimeMillis() - lastPlayerAttackTime <= 5000);
    }

    private boolean allyNexusUnderAttack(RoomHandler rh) {
        List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, mapConfig.allyNexus, 10f);

        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bT -> bT.getTeam() != team);

        if (!enemies.isEmpty() && !baseTowers.isEmpty()) {
            target = getClosestActor(enemies, true);
            return true;
        }
        return false;
    }

    private boolean allyBaseTowerUnderAttack(RoomHandler rh) {
        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bt -> bt.getTeam() != team);

        if (baseTowers.isEmpty()) return false;

        Point2D towerLoc = baseTowers.get(0).getLocation();

        List<Actor> enemies =
                Champion.getEnemyActorsInRadius(rh, team, towerLoc, TOWER_ATTACK_RANGE);

        if (!enemies.isEmpty()) {
            target = getClosestActor(enemies, true);
            return true;
        }
        return false;
    }

    private boolean anyAllyTowerUnderAttack(RoomHandler rh) {
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() != team);

        for (Tower t : towers) {
            List<Actor> enemies =
                    Champion.getEnemyActorsInRadius(rh, team, t.getLocation(), TOWER_ATTACK_RANGE);
            if (!enemies.isEmpty()) {
                target = getClosestActor(enemies, true);
                return true;
            }
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

    private List<Actor> getEnemies(RoomHandler rh, float distance) {
        return Champion.getEnemyActorsInRadius(rh, team, location, distance);
    }

    private BotAction handleDamagedByChampion(RoomHandler rh, List<Actor> enemies) {
        if (!damagedByChampion()) return null;
        Actor potentialTarget;
        boolean canWin = canWinFight(rh, enemies, FightContext.DAMAGED_BY_CHAMPION);

        if (canWin || getPHealth() > 0.4) {
            if (lastPlayerAttacker != null
                    && !lastPlayerAttacker.isDead()
                    && !lastPlayerAttacker.isInvisible()) {
                potentialTarget = lastPlayerAttacker;
            } else {
                potentialTarget = getClosestActor(enemies, true);
            }
            if (potentialTarget != null) {
                target = potentialTarget;
                return BotAction.FIGHTING;
            } else return null;
        } else {
            return BotAction.FLEEING;
        }
    }

    private boolean canAttackGnomes(RoomHandler rh) {
        if (GameManager.getMap(mapConfig.roomGroup) == GameMap.CANDY_STREETS) {
            List<Monster> gnomes = rh.getCampMonsters();

            gnomes.removeIf(m -> !m.getId().contains("gnome"));
            if (gnomes.isEmpty()) return false;

            Monster gnome = gnomes.get(0);
            List<Actor> towers = Champion.getActorsInRadius(rh, gnome.getLocation(), 20f);
            towers.removeIf(a -> !(a instanceof Tower));
            towers.removeIf(t -> t instanceof BaseTower);
            return towers.isEmpty();
        }
        return true;
    }

    private boolean canSurviveJungle() {
        float hpScore = (float) getPHealth(); // 0.0 to 1.0
        float armorScore = (float) Math.min(getPlayerStat("armor") / 100f, 0.4f);
        float levelScore = level / 20f;

        float jungleScore = hpScore + armorScore + levelScore;

        // At a 1.0 threshold:
        // - A Level 1 bot with 0 armor needs 95% HP to jungle.
        // - A Level 5 bot with 20 armor needs 55% HP to jungle.
        // - A Level 10 bot with 40 armor only needs 10% HP to jungle.
        return jungleScore > 1.0f;
    }

    private BotAction evaluateBotAction() {
        if (getHealth() <= 0 || isDead()) return null;

        altarToCapture = new HashMap<>();

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemies = getEnemies(rh, aggroRange);
        enemies.removeIf(Actor::isInvisible);

        // LOW HP
        if (lowHp()) {
            if (!enemies.isEmpty()
                    && lastPlayerAttacker != null
                    && canWinFight(rh, enemies, FightContext.LOW_HP_RETALIATE)) {
                target = getClosestActor(enemies, true);
                return BotAction.FIGHTING;
            }
            if (commitSideAltar) commitSideAltar = false;
            if (commitJungleCamp) commitJungleCamp = false;
            return BotAction.RETREATING;
        }

        // FLEE
        if (!commitSideAltar && !commitJungleCamp) {
            boolean recentTower = System.currentTimeMillis() - lastTargetedByTower <= 2000;
            boolean recentMinion = System.currentTimeMillis() - lastAttackedByMinions <= 1000;
            if (recentTower || (recentMinion && fleeOnMinionsAttacked()) || retreatFromSiege(rh)) {
                return BotAction.FLEEING;
            }
        }

        // RETALIATE
        BotAction damagedAction = handleDamagedByChampion(rh, enemies);
        if (damagedAction != null) {
            commitSideAltar = false;
            commitJungleCamp = false;
            return damagedAction;
        }

        // DEFEND STRUCTURES
        if (!commitSideAltar && !commitJungleCamp) {
            if (allyNexusUnderAttack(rh)
                    || allyBaseTowerUnderAttack(rh)
                    || anyAllyTowerUnderAttack(rh)) {
                return BotAction.FIGHTING;
            }
        }

        // MID ALTAR
        if (midAltarNotCaptured(rh) && !commitSideAltar && !commitJungleCamp) {
            List<Actor> altarEnemies =
                    Champion.getEnemyActorsInRadius(rh, team, mapConfig.offenseAltar, aggroRange);
            if (canWinFight(rh, altarEnemies, FightContext.AGGRO_ENGAGE)) {
                altarToCapture.put(AltarType.MID, mapConfig.offenseAltar);
                return BotAction.ALTAR;
            }
        }

        // ATTACK ENEMY CHAMPIONS IN RANGE
        if (!commitSideAltar && !commitJungleCamp) {
            List<Actor> enemyChamps = new ArrayList<>(enemies);
            enemyChamps.removeIf(e -> !e.isChampion());
            if (!enemyChamps.isEmpty() && canWinFight(rh, enemyChamps, FightContext.AGGRO_ENGAGE)) {

                target = getClosestActor(enemyChamps, true);
                // Console.debugLog("ATTACK ENEMY CHAMPION: " + target);
                return BotAction.FIGHTING;
            }
        }

        // ATTACK MONSTERS
        if (level < 10) {
            if ((commitJungleCamp && target instanceof Monster)
                    || (tryJungle(rh) && !commitSideAltar)) {
                commitJungleCamp = true;
                return BotAction.JUNGLING;
            }
        }

        // PUSH LANE
        if (!commitSideAltar) {
            List<Minion> minions = rh.getMinions();
            List<Actor> allyMinions = new ArrayList<>(minions);
            List<Actor> enemyMinions = new ArrayList<>(minions);

            allyMinions.removeIf(m -> m.getTeam() != team);
            enemyMinions.removeIf(m -> m.getTeam() == team);

            if (tryAttackStructures(rh, allyMinions, enemies)) {
                return BotAction.FIGHTING;
            }

            // FARM MINIONS
            if (!enemyMinions.isEmpty()) {
                Actor closestEnemyMinion = getClosestActor(enemyMinions, false);
                if (closestEnemyMinion != null) {
                    // Console.debugLog("CLOSEST ENEMY MINION IS NOT NULL");

                    if (!isPointInTowerRadius(location, team) && withinRange(closestEnemyMinion)) {
                        target = closestEnemyMinion;
                        return BotAction.FIGHTING;
                    }

                    Point2D minionLocation = closestEnemyMinion.getLocation();
                    boolean towerCheck =
                            isPointInTowerRadius(minionLocation, closestEnemyMinion.getTeam());

                    boolean safe = true;

                    if (towerCheck) {
                        // Console.debugLog("CLOSEST ENEMY MINION IN ENEMY TOWER RANGE!");
                        safe = false;
                        Actor tower = getEnemyTower(rh, closestEnemyMinion.getLocation());

                        if (tower != null) {
                            for (Actor a : allyMinions) {
                                if (a.getLocation().distance(tower.getLocation())
                                        <= TOWER_ATTACK_RANGE) safe = true;
                            }
                        }
                    }
                    if (safe) {
                        target = closestEnemyMinion;
                        return BotAction.FIGHTING;
                    }
                }
            }
        }

        // DEFENSE ALTARS
        if (canCaptureSideAltar(rh)) {
            return BotAction.ALTAR;
        }

        return null;
    }

    private boolean retreatFromSiege(RoomHandler rh) {
        if (target != null) {
            if (target.getActorType() == ActorType.TOWER) {
                List<Actor> allyMinions =
                        Champion.getActorsInRadius(rh, target.getLocation(), TOWER_ATTACK_RANGE);
                allyMinions.removeIf(a -> !(a instanceof Minion));
                allyMinions.removeIf(a -> a.getTeam() != team);

                if (getPlayerStat("attackRange") < 3) return allyMinions.size() == 1;
                else if (allyMinions.size() == 1) {
                    Actor allyMinion = allyMinions.get(0);
                    return allyMinion.getPHealth() < 0.5;
                }
            }
        }
        return false;
    }

    private boolean tryAttackStructures(
            RoomHandler rh, List<Actor> allyMinions, List<Actor> enemies) {

        List<Actor> enemyTowers = new ArrayList<>(rh.getTowers());
        enemyTowers.removeIf(t -> t.getTeam() == team);

        List<Actor> enemyBaseTowers = new ArrayList<>(rh.getBaseTowers());
        enemyBaseTowers.removeIf(bt -> bt.getTeam() == team);

        Actor closestAllyMinion = getClosestActor(allyMinions, false);
        Point2D closestAllyMinionPoint = null;

        if (closestAllyMinion != null) closestAllyMinionPoint = closestAllyMinion.getLocation();

        for (Actor a : enemies) {
            if (a instanceof Base && enemyBaseTowers.isEmpty()) {
                target = a;
                return true;
            } else if (a instanceof BaseTower
                    && enemyTowers.isEmpty()
                    && closestAllyMinionPoint != null
                    && canPushToPoint(closestAllyMinionPoint, a.getLocation())) {
                target = a;
                return true;
            } else if (a.getActorType() == ActorType.TOWER
                    && !(a instanceof BaseTower)
                    && closestAllyMinionPoint != null
                    && canPushToPoint(closestAllyMinionPoint, a.getLocation())) {
                target = a;
                return true;
            }
        }
        return false;
    }

    private boolean tryJungle(RoomHandler rh) {
        List<Actor> allies = rh.getActorsInRadius(location, 4);

        allies.removeIf(a -> !a.isChampion() || a.getTeam() != team);
        allies.remove(this);

        for (Actor a : allies) {
            if (a.getTarget() != null && a.getTarget() instanceof Monster) {
                target = a.getTarget();
                return true;
            }
        }

        if (canSurviveJungle()) {
            List<Monster> monsters = rh.getCampMonsters();
            monsters.removeIf(m -> m.getHealth() <= 0 || m.isDead());
            if (!canFightGoo(rh)) monsters.removeIf(m -> m.getId().contains("goo"));
            if (!canfightKeeoth()) monsters.removeIf(m -> m.getId().contains("keeoth"));

            Actor closestMonster = getClosestActor(new ArrayList<>(monsters), false);
            if (closestMonster != null) {
                boolean isGnome = closestMonster.getId().contains("gnome");
                if (!isGnome || canAttackGnomes(rh)) {
                    target = closestMonster;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canFightGoo(RoomHandler rh) {
        List<Actor> allies = rh.getChampions();
        allies.removeIf(a -> a.getTeam() != team);
        allies.remove(this);

        int help = 0;

        for (Actor a : allies) {
            if (a.getLocation().distance(location) <= aggroRange) help++;
        }
        return getLevel() > 5 && help == 2;
    }

    private boolean canfightKeeoth() {
        // TODO: implement keeoth
        return false;
    }

    private boolean canCaptureSideAltar(RoomHandler rh) {
        List<Point2D> defAltars = new ArrayList<>();
        defAltars.add(mapConfig.defenseAltar);
        if (mapConfig.defenseAltar2 != null) defAltars.add(mapConfig.defenseAltar2);

        for (Point2D sideAltarLocation : defAltars) {
            if (rh.getAltarStatus(sideAltarLocation) != 10
                    && location.distance(sideAltarLocation) <= 20f) {
                if (location.distance(sideAltarLocation) <= aggroRange) {
                    commitSideAltar = true;
                }
                altarToCapture.put(AltarType.SIDE, sideAltarLocation);
                return true;
            }
        }
        return false;
    }

    private Actor getEnemyTower(RoomHandler rh, Point2D enemyMinion) {
        List<Actor> enemiesInTower =
                Champion.getEnemyActorsInRadius(rh, team, enemyMinion, TOWER_ATTACK_RANGE);
        return enemiesInTower.stream().filter(e -> e instanceof Tower).findFirst().orElse(null);
    }

    private void move(Point2D dest) {
        if (moveDestination != dest) startMoveTo(dest, false);
    }

    protected void executeBotAction(BotAction stateToExecute) {
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

                if (canMove() && fleePoint != null) {
                    move(fleePoint);
                }
                break;
            case ALTAR:
                if (!altarToCapture.isEmpty() && canMove()) {
                    Point2D movePoint = null;
                    for (Point2D p : altarToCapture.values()) {
                        movePoint = p;
                        break;
                    }
                    if (movePoint != null) move(movePoint);
                }
                break;
            case PUSHING:
                Point2D nextPushPoint = getNextPushWaypoint();
                if (nextPushPoint != null && canMove()) {
                    move(nextPushPoint);
                }
                break;
        }
    }

    public abstract void handleFightingAbilities();

    public abstract void handleRetreatAbilities();

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
            if (!BOT_RESPAWN_DEBUG) {
                int newDeath = 10 + ((msRan / 1000) / 60);
                if (newDeath != deathTime) deathTime = newDeath;
                if (currentHealth < maxHealth) regenHealth();
            }
        }
    }

    protected void faceTarget(Actor target) {
        // TODO: this is problematic, bots get stuck in obstacles too often
        /*if (target != null) {
            Point2D rotationPoint =
                    Champion.createLineTowards(location, target.getLocation(), 0.75f).getP2();

            setLocation(rotationPoint);

            ExtensionCommands.moveActor(
                    parentExt, room, id, location, location, (float) getPlayerStat("speed"), true);
            stopMoving();
        }*/
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
            if (target.isInvisible() || target.isDead() || target.getHealth() <= 0) {
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
        stopMoving();
        setLocation(mapConfig.respawnPoint);
        setCanMove(true);
        setHealth((int) maxHealth, (int) maxHealth);
        dead = false;
        isAutoAttacking = false;
        effectManager.removeEffects();
        movementState = MovementState.IDLE;
        lastPlayerAttacker = null;

        commitSideAltar = false;
        commitJungleCamp = false;

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
}

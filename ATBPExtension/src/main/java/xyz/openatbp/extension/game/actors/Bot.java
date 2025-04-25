package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.UserActor.BASIC_ATTACK_DELAY;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.extensions.ExtensionLogLevel;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.SkinData;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class Bot extends Actor {
    private static final boolean MOVEMENT_DEBUG = false;
    public static final int CYCLOPS_DURATION = 60000;
    public static final double PASSIVE_DURATION = 5000;
    public static final float W_OFFSET_DISTANCE = 1.25f;
    public static final int DASH_SPEED = 20;
    public static final int E_SELF_ROOT_DURATION = 1200;
    public static final int E_DURATION = 5000;
    public static final int E_ROOT_DURATION = 2000;
    public static final double POLYMORPH_SLOW_VALUE = 0.3d;
    private static final float TOWER_RANGE = 6f;
    private static final Point2D firstPoint = new Point2D.Float(15, 0);
    private static final int Q_DURATION = 3000;
    private final Point2D spawnPoint;
    private static final int E_CAST_DELAY = 500;
    private static final int POLYMORPH_DURATION = 3000;
    private static final int FOUNTAIN_HEAL = 250;

    private int deathTime = 10;
    private int level = 1;
    private int xp = 0;
    private boolean isAutoAttacking = false;
    private boolean isDashing = false;
    private Long lastAttackedByMinion = 0L;
    private Long lastAttackedByTower = 0L;
    private Actor enemyTower = null;
    private Actor enemyBaseTower = null;
    private boolean wentToStartPoint = false;
    private boolean pickedUpHealthPack = false;
    private Long healthPackTime = 0L;
    private Long lastQUse = 0L;
    private Long lastWUse = 0L;
    private Long lastEUse = 0L;
    private boolean qActive = false;
    private int furyStacks = 0;
    private Actor furyTarget = null;
    private Long passiveStart = 0L;
    private boolean isCastingUlt = false;
    private boolean ultActivated = false;
    private Long eStartTime = 0L;
    private float ultX;
    private float ultY;
    private Path2D finnUltRing = null;
    private Line2D[] wallLines;
    private boolean[] wallsActivated = {false, false, false, false}; // NORTH, EAST, SOUTH, WEST
    private Long lastPolymorphTime = 0L;
    private boolean isPolymorphed = false;
    private UserActor enemy;
    private Long enemyDmgTime = 0L;
    private HashMap<Actor, Long> agressors = new HashMap<>();

    public Bot(ATBPExtension parentExt, Room room, String avatar, int team, Point2D spawnPoint) {
        this.room = room;
        this.parentExt = parentExt;
        this.currentHealth = 500;
        this.maxHealth = 500;
        this.location = spawnPoint;
        this.avatar = avatar;
        this.id = avatar + "_" + team;
        this.team = team;
        this.movementLine = new Line2D.Float(this.location, this.location);
        this.actorType = ActorType.COMPANION;
        this.stats = initializeStats();
        this.spawnPoint = spawnPoint;
        this.displayName = "FINN BOT";

        Runnable create =
                () ->
                        ExtensionCommands.createActor(
                                parentExt, room, id, avatar, location, 0f, team);
        parentExt.getTaskScheduler().schedule(create, 200, TimeUnit.MILLISECONDS);

        if (MOVEMENT_DEBUG) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "moveDebug", "creep1", location, 0f, 1);
        }
        levelUpStats();
    }

    @Override
    public void die(Actor a) {
        dead = true;
        currentHealth = 0;
        canMove = false;

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

        if (isPolymorphed) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }

        if (qActive) {
            handleQDeath();
        }

        if (furyTarget != null) {
            ExtensionCommands.removeFx(parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
        }

        furyStacks = 0;
        furyTarget = null;

        if (!getState(ActorState.AIRBORNE)) stopMoving();
        ExtensionCommands.knockOutActor(parentExt, room, id, realKiller.getId(), deathTime);

        Runnable respawn = this::respawn;
        parentExt.getTaskScheduler().schedule(respawn, deathTime, TimeUnit.SECONDS);

        if (realKiller.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) realKiller;
            killer.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(killer, killer.getTeam(), 25);
            killer.addXP(100);
        }
    }

    private int getSpellDamage(JsonNode attackData) {
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

    private boolean isNonStructure(Actor a) {
        return (a.getTeam() != team
                && a.getActorType() != ActorType.BASE
                && a.getActorType() != ActorType.TOWER);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        agressors.put(a, System.currentTimeMillis());
        if (a.equals(enemy) && location.distance(a.getLocation()) <= 8) {
            enemyDmgTime = System.currentTimeMillis();
        }

        if (pickedUpHealthPack) {
            pickedUpHealthPack = false;
            setStat("healthRegen", getStat("healthRegen") - 15);
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        }
        if (a.getActorType() == ActorType.MINION) {
            lastAttackedByMinion = System.currentTimeMillis();
        }
        if (a instanceof Tower) {
            lastAttackedByTower = System.currentTimeMillis();
        }

        if (attackData.has("spellName")
                && attackData.get("spellName").asText().equals("flame_spell_2_name")) {
            lastPolymorphTime = System.currentTimeMillis();
            isPolymorphed = true;
            addState(ActorState.SLOWED, POLYMORPH_SLOW_VALUE, POLYMORPH_DURATION);

            ExtensionCommands.swapActorAsset(parentExt, room, id, "flambit");
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "statusEffect_polymorph",
                    1000,
                    id + "_statusEffect_polymorph",
                    true,
                    "",
                    true,
                    false,
                    team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "flambit_aoe",
                    POLYMORPH_DURATION,
                    id + "_flambit_aoe",
                    true,
                    "",
                    true,
                    false,
                    team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fx_target_ring_2",
                    POLYMORPH_DURATION,
                    id + "_flambit_ring_",
                    true,
                    "",
                    true,
                    true,
                    getOppositeTeam());
        }

        return super.damaged(a, damage, attackData);
    }

    @Override
    public void update(int msRan) {
        if (dead) return;
        handleDamageQueue();
        handleActiveEffects();

        if (msRan == 3000) {
            enemy = parentExt.getRoomHandler(room.getName()).getPlayers().get(0);
        }

        if (attackCooldown > 0) attackCooldown -= 100;

        if (isPolymorphed && System.currentTimeMillis() - lastPolymorphTime >= POLYMORPH_DURATION) {
            isPolymorphed = false;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }

        if (!isStopped() && canMove()) timeTraveled += 0.1f;
        location =
                MovementManager.getRelativePoint(
                        movementLine, getPlayerStat("speed"), timeTraveled);
        handlePathing();
        if (MOVEMENT_DEBUG)
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id + "moveDebug",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    false);

        if (qActive && System.currentTimeMillis() - lastQUse >= Q_DURATION) {
            qActive = false;
        }
        if (furyStacks > 0) {
            if (System.currentTimeMillis() - passiveStart >= PASSIVE_DURATION) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
            if (furyTarget.getHealth() <= 0) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
        }

        if (ultActivated && System.currentTimeMillis() - eStartTime >= E_DURATION) {
            wallLines = null;
            wallsActivated = new boolean[] {false, false, false, false};
            ultActivated = false;
            finnUltRing = null;
        }

        if (ultActivated && wallLines != null) {
            for (int i = 0; i < wallLines.length; i++) {
                if (wallsActivated[i]) {
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    List<Actor> nonStructureEnemies = handler.getNonStructureEnemies(team);
                    for (Actor a : nonStructureEnemies) {
                        if (wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            wallsActivated[i] = false;
                            JsonNode spellData = parentExt.getAttackData("finn", "spell3");
                            a.addState(ActorState.ROOTED, 0d, E_ROOT_DURATION);
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                            String direction = "north";
                            if (i == 1) direction = "east";
                            else if (i == 2) direction = "south";
                            else if (i == 3) direction = "west";

                            String wallDestroyedFX = SkinData.getFinnEDestroyFX(avatar, direction);
                            String wallDestroyedSFX = SkinData.getFinnEDestroySFX(avatar);
                            ExtensionCommands.removeFx(
                                    parentExt, room, id + "_" + direction + "Wall");
                            ExtensionCommands.createWorldFX(
                                    parentExt,
                                    room,
                                    id,
                                    wallDestroyedFX,
                                    id + "_wallDestroy_" + direction,
                                    1000,
                                    ultX,
                                    ultY,
                                    false,
                                    team,
                                    180f);
                            ExtensionCommands.playSound(
                                    parentExt, room, id, wallDestroyedSFX, location);
                            break;
                        }
                    }
                }
            }
        }

        if (msRan % 1000 == 0) {
            int newDeath = 10 + ((msRan / 1000) / 60);
            if (newDeath != deathTime) deathTime = newDeath;
            if (currentHealth < maxHealth) regenHealth();
        }

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

        for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
            if (!ua.getId().equalsIgnoreCase(id)) {
                if (ua.getLocation().distance(location) <= 5f)
                    ua.setGlassesBuff(
                            ChampionData.getCustomJunkStat(ua, "junk_2_simon_petrikovs_glasses"),
                            ua);
                else ua.setGlassesBuff(-1d, ua);
            }
        }

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // bot actions
        RoomHandler handler = parentExt.getRoomHandler(room.getName());

        float topAltarY = MapData.L1_AALTAR_Z;
        float botAltarY = MapData.L1_DALTAR_Z;

        Point2D topAltarLocation = new Point2D.Float(0, topAltarY);
        Point2D botAltarLocation = new Point2D.Float(0, botAltarY);

        int topStatus = handler.getAltarStatus(topAltarLocation);
        int botStatus = handler.getAltarStatus(botAltarLocation);

        List<Actor> enemyActorsInRadius =
                Champion.getEnemyActorsInRadius(handler, team, location, 5f);

        if (pickedUpHealthPack && System.currentTimeMillis() - healthPackTime >= CYCLOPS_DURATION) {
            pickedUpHealthPack = false;
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        }

        if (getPHealth() < 0.6
                && room.getVariable("spawns").getSFSObjectValue().getInt("bh1") == 91) {
            Console.debugLog("Health pack");
            Point2D bh1 = new Point2D.Float(MapData.L1_BLUE_HEALTH_X, MapData.L1_BLUE_HEALTH_Z);
            moveWithCollision(bh1);
            return;
        }

        if (getPHealth() < 0.2) {
            Console.debugLog("Return to base");
            moveWithCollision(spawnPoint);
            return;
        }

        if (System.currentTimeMillis() - enemyDmgTime <= 5000
                && !enemy.isDead()
                && shouldAttackTarget(enemy)) {
            Console.debugLog("Attack Player");
            attemptAttack(enemy);
            return;
        }

        if (location.distance(firstPoint) <= 0.5) wentToStartPoint = true;

        if (!wentToStartPoint) {
            moveWithCollision(firstPoint);
            return;
        }

        if (System.currentTimeMillis() - lastAttackedByMinion <= 1000
                || System.currentTimeMillis() - lastAttackedByTower <= 1000) {
            run();
            return;
        }

        for (Actor a : enemyActorsInRadius) {
            if (a instanceof UserActor) {

                if (shouldAttackTarget(a) && a.getLocation().distance(location) < 5) {
                    if (canUseW(a)) useW(a);
                    if (canUseE(a)) useE();
                    Console.debugLog("Attack Player");
                    attemptAttack(a);
                    return;
                } else {
                    break;
                }
            }
        }

        if (topStatus == 10 && botStatus == 10) {
            Console.debugLog("Altars captured, attack something");
            List<Actor> actors = handler.getActors();
            List<Actor> enemies =
                    actors.stream().filter(a -> a.getTeam() != team).collect(Collectors.toList());

            List<Actor> owls = new ArrayList<>();
            for (Actor a : enemies) {
                if (a.getActorType() == ActorType.MONSTER
                        && a.getHealth() > 0
                        && a.getId().contains("owl")) {
                    owls.add(a);
                }
            }

            if (!owls.isEmpty() && shouldAttackJungleCamp(true)) {
                Console.debugLog("Attack Owls");
                attackClosestActor(owls);
                return;
            }

            List<Actor> gnomes = new ArrayList<>();
            for (Actor actor : enemies) {
                if (actor.getActorType() == ActorType.MONSTER
                        && actor.getHealth() > 0
                        && actor.getId().contains("gnome")) {
                    gnomes.add(actor);
                }
            }

            if (!gnomes.isEmpty() && shouldAttackJungleCamp(false)) {
                Console.debugLog("Attack Gnomes");
                attackClosestActor(gnomes);
                return;
            }

            enemies.removeIf(a -> a instanceof Monster);

            Console.debugLog("Attack closest enemy");
            attackClosestActor(enemies);

        } else if (topStatus != 10) {
            Console.debugLog("Top altar");
            moveWithCollision(topAltarLocation);
        } else {
            Console.debugLog("Bot altar");
            moveWithCollision(botAltarLocation);
        }
    }

    private boolean canUseQ() {
        int cd = 10000; // constant value for now
        return System.currentTimeMillis() - lastQUse >= cd
                && !isDashing
                && !isCastingUlt
                && !isPolymorphed;
    }

    private void useQ() {
        lastQUse = System.currentTimeMillis();
        attackCooldown = 0;
        qActive = true;

        addEffect("attackSpeed", getStat("attackSpeed") * -0.2, Q_DURATION);
        addEffect("armor", getStat("armor") * 0.15, Q_DURATION);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "finn_shieldShimmer",
                Q_DURATION,
                id + "_shield",
                true,
                "Bip001 Pelvis",
                true,
                false,
                team);

        ExtensionCommands.playSound(parentExt, room, id, "sfx_finn_shield", location);
    }

    private boolean canUseW(Actor target) {
        if (target == null) return false;
        int cd = 12000;
        Point2D tLocation = target.getLocation();
        boolean tInRange = tLocation.distance(location) < 4;

        return System.currentTimeMillis() - lastWUse >= cd
                && tInRange
                && !isCastingUlt
                && !isPolymorphed;
    }

    private void useW(Actor target) {
        if (target == null) return;
        Point2D targetLocation = target.getLocation();
        Line2D abilityLine = Champion.getAbilityLine(location, targetLocation, 5f);
        Point2D dest = abilityLine.getP2();

        if (!MovementManager.insideAnyObstacle(parentExt, true, dest)) {
            isDashing = true;
            lastWUse = System.currentTimeMillis();
            Point2D ogLocation = location;
            float W_SPELL_RANGE = 5;

            Path2D quadrangle =
                    Champion.createRectangle(location, dest, W_SPELL_RANGE, W_OFFSET_DISTANCE);

            double time = ogLocation.distance(dest) / DASH_SPEED;
            int wTime = (int) (time * 1000);

            ExtensionCommands.moveActor(parentExt, room, id, location, dest, DASH_SPEED, true);
            setLocation(dest);

            ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", wTime - 50, false);

            Runnable endDash =
                    () -> {
                        isDashing = false;
                        ExtensionCommands.actorAnimate(parentExt, room, id, "idle", wTime, false);
                    };
            parentExt.getTaskScheduler().schedule(endDash, wTime, TimeUnit.MILLISECONDS);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "finn_dash_fx",
                    wTime,
                    id + "finnWTrail",
                    true,
                    "",
                    true,
                    false,
                    team);

            ExtensionCommands.playSound(parentExt, room, id, "sfx_finn_dash_attack", location);

            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            JsonNode spellData = parentExt.getAttackData("finn", "spell2");

            List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(team, quadrangle);
            if (!actorsInPolygon.isEmpty()) {
                for (Actor a : actorsInPolygon) {
                    if (isStructure(a)) {
                        int damage = getSpellDamage(spellData);
                        a.addToDamageQueue(this, damage, spellData, false);
                    } else {
                        int damage = (int) (handlePassive(a, getSpellDamage(spellData)));
                        a.addToDamageQueue(this, damage, spellData, false);
                        passiveStart = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    private boolean canUseE(Actor a) {
        if (a == null) return false;
        Point2D tLocation = a.getLocation();
        int cd = 30000;
        return System.currentTimeMillis() - lastEUse >= cd
                && tLocation.distance(location) < 3.5
                && a.getActorType() == ActorType.PLAYER
                && a.getPHealth() < 0.4
                && !isPolymorphed;
    }

    private void useE() {
        isCastingUlt = true;
        lastEUse = System.currentTimeMillis();
        stopMoving();
        Runnable enableActions = () -> isCastingUlt = false;
        parentExt
                .getTaskScheduler()
                .schedule(enableActions, E_SELF_ROOT_DURATION, TimeUnit.MILLISECONDS);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell3", E_SELF_ROOT_DURATION, false);

        Runnable cast =
                () -> {
                    try {
                        ultActivated = true;
                        eStartTime = System.currentTimeMillis();
                        double widthHalf = 3.675d;
                        Point2D p1 =
                                new Point2D.Double(
                                        location.getX() - widthHalf, location.getY() + widthHalf);
                        Point2D p2 =
                                new Point2D.Double(
                                        location.getX() + widthHalf, location.getY() + widthHalf);
                        Point2D p3 =
                                new Point2D.Double(
                                        location.getX() - widthHalf, location.getY() - widthHalf);
                        Point2D p4 =
                                new Point2D.Double(
                                        location.getX() + widthHalf, location.getY() - widthHalf);
                        ultX = (float) location.getX();
                        ultY = (float) location.getY();
                        finnUltRing = new Path2D.Float();
                        finnUltRing.moveTo(p2.getX(), p2.getY());
                        finnUltRing.lineTo(p4.getX(), p4.getY());
                        finnUltRing.lineTo(p3.getX(), p3.getY());
                        finnUltRing.lineTo(p1.getX(), p1.getY());

                        String[] directions = {"north", "east", "south", "west"};
                        String wallDropSFX = SkinData.getFinnEWallDropSFX(avatar);
                        String cornerSwordsFX = SkinData.getFinnECornerSwordsFX(avatar);

                        for (String direction : directions) {
                            ExtensionCommands.createWorldFX(
                                    parentExt,
                                    room,
                                    id,
                                    "finn_wall_" + direction,
                                    id + "_" + direction + "Wall",
                                    E_DURATION,
                                    ultX,
                                    ultY,
                                    false,
                                    team,
                                    180f);
                        }
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                id,
                                "fx_target_square_4.5",
                                E_DURATION,
                                id + "_eSquare",
                                false,
                                "",
                                false,
                                true,
                                this.team);

                        ExtensionCommands.playSound(parentExt, room, id, wallDropSFX, location);
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                cornerSwordsFX,
                                id + "_p1Sword",
                                E_DURATION,
                                ultX,
                                ultY,
                                false,
                                team,
                                0f);
                        Line2D northWall = new Line2D.Float(p4, p3);
                        Line2D eastWall = new Line2D.Float(p3, p1);
                        Line2D southWall = new Line2D.Float(p2, p1);
                        Line2D westWall = new Line2D.Float(p4, p2);
                        wallLines = new Line2D[] {northWall, eastWall, southWall, westWall};
                        wallsActivated = new boolean[] {true, true, true, true};
                    } catch (Throwable t) {
                        parentExt.trace(ExtensionLogLevel.ERROR, "Error with casting E: " + id);
                        ultActivated = false;
                        wallLines = null;
                        wallsActivated = new boolean[] {false, false, false, false};
                        finnUltRing = null;
                        isCastingUlt = false; // Ensure this is also reset if cast fails
                    }
                };
        parentExt.getTaskScheduler().schedule(cast, E_CAST_DELAY, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean canMove() {
        if (isDashing || isAutoAttacking || isCastingUlt) return false;
        return super.canMove();
    }

    @Override
    public boolean canAttack() {
        if (isDashing || isCastingUlt || isPolymorphed) return false;
        return super.canAttack();
    }

    private boolean isStructure(Actor a) {
        return a.getActorType() == ActorType.TOWER || a.getActorType() == ActorType.BASE;
    }

    private void handleQDeath() {
        qActive = false;
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        for (Actor actor : Champion.getActorsInRadius(handler, this.location, 2f)) {
            if (actor.getTeam() != team
                    && actor.getActorType() != ActorType.BASE
                    && actor.getActorType() != ActorType.TOWER) {
                JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                actor.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
            }
        }

        ExtensionCommands.removeFx(parentExt, room, id + "_shield");
        ExtensionCommands.playSound(parentExt, room, id, "sfx_finn_shield_shatter", location);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "finn_shieldShatter",
                1000,
                id + "_qShatter",
                true,
                "",
                true,
                false,
                team);
    }

    protected double handlePassive(Actor target, double damage) {
        if (furyTarget != null) {
            if (furyTarget.getId().equalsIgnoreCase(target.getId())) {
                damage *= (1 + (0.2 * furyStacks));
                if (furyStacks < 3) {
                    if (furyStacks > 0)
                        ExtensionCommands.removeFx(
                                parentExt, room, target.getId() + "_mark" + furyStacks);
                    furyStacks++;
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            target.getId(),
                            "fx_mark" + furyStacks,
                            1000 * 15 * 60,
                            target.getId() + "_mark" + furyStacks,
                            true,
                            "",
                            true,
                            false,
                            target.getTeam());
                } else {
                    furyStacks = 0;
                    ExtensionCommands.removeFx(parentExt, room, target.getId() + "_mark3");
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            target.getId(),
                            "fx_mark4",
                            500,
                            target.getId() + "_mark4",
                            true,
                            "",
                            true,
                            false,
                            target.getTeam());
                    if (qActive) {
                        RoomHandler handler = parentExt.getRoomHandler(room.getName());
                        for (Actor actor : Champion.getActorsInRadius(handler, location, 2f)) {
                            if (isNonStructure(actor)) {
                                JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                                actor.addToDamageQueue(
                                        this, getSpellDamage(spellData), spellData, false);
                            }
                        }
                        qActive = false;

                        ExtensionCommands.removeFx(parentExt, room, id + "_shield");

                        ExtensionCommands.playSound(
                                parentExt, room, id, "sfx_finn_shield_shatter", location);
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                id,
                                "finn_shieldShatter",
                                1000,
                                id + "_qShatter",
                                true,
                                "",
                                true,
                                false,
                                team);
                    }
                }
            } else {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "fx_mark1",
                        1000 * 15 * 60,
                        target.getId() + "_mark1",
                        true,
                        "",
                        true,
                        false,
                        target.getTeam());
                furyTarget = target;
                furyStacks = 1;
            }
        } else {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    target.getId(),
                    "fx_mark1",
                    1000 * 15 * 60,
                    target.getId() + "_mark1",
                    true,
                    "",
                    true,
                    false,
                    target.getTeam());
            furyTarget = target;
            furyStacks = 1;
        }
        return damage;
    }

    private void attackClosestActor(List<Actor> targets) {
        double distance = 10000;
        Actor target = null;
        for (Actor a : targets) {
            if (a.getLocation().distance(location) < distance && shouldAttackTarget(a)) {
                distance = a.getLocation().distance(location);
                target = a;
            }
        }
        attemptAttack(target);
    }

    private void regenHealth() {
        double healthRegen = getPlayerStat("healthRegen");
        if (currentHealth + healthRegen <= 0) healthRegen = (currentHealth - 1) * -1;
        changeHealth((int) healthRegen);
    }

    public void handleCyclopsHealing() {
        pickedUpHealthPack = true;
        heal((int) (getMaxHealth() * 0.15));
        healthPackTime = System.currentTimeMillis();
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fx_health_regen",
                CYCLOPS_DURATION,
                id + "healthPackFX",
                true,
                "",
                false,
                false,
                getTeam());
        setStat("healthRegen", getStat("healthRegen") + 15);
    }

    private void run() {
        Console.debugLog("Run");
        Point2D runPoint = new Point2D.Float((float) location.getX() + 5, (float) location.getY());
        moveWithCollision(runPoint);
    }

    private boolean shouldAttackJungleCamp(boolean owls) {
        if (level > 2 && level < 6 && getPLevel() > 0.6 && owls
                || level > 5 && getPLevel() > 0.2 && owls) {
            return true;
        }
        if (level > 4 && getPLevel() > 0.4 && enemyTower.isDead() && !owls) return true;

        return false;
    }

    private boolean shouldAttackTarget(Actor a) {
        float towerY = MapData.L1_TOWER_Z;
        float purpleTower0X = MapData.L1_PURPLE_TOWER_0[0];
        float purpleTower1X = MapData.L1_PURPLE_TOWER_1[0];

        Point2D purpleTower0Location = new Point2D.Float(purpleTower0X, towerY);
        Point2D purpleTower1Location = new Point2D.Float(purpleTower1X, towerY);

        RoomHandler handler = parentExt.getRoomHandler(room.getName());

        List<Tower> towers = handler.getTowers();
        List<BaseTower> baseTowers = handler.getBaseTowers();

        if (enemyTower == null) enemyTower = towers.get(0);
        if (enemyBaseTower == null) enemyBaseTower = baseTowers.get(0);

        List<Actor> actorsInRadiusTower1 =
                Champion.getActorsInRadius(handler, purpleTower1Location, 6f);
        List<Actor> allyMinionsTower1 = new ArrayList<>();
        for (Actor actor : actorsInRadiusTower1) {
            if (actor.getTeam() == team
                    && actor.getActorType() == ActorType.MINION
                    && actor.getHealth() > 0) {
                allyMinionsTower1.add(actor);
            }
        }

        List<Actor> actorsInRadiusTower0 =
                Champion.getActorsInRadius(handler, purpleTower0Location, 6f);
        List<Actor> allyMinionsTower0 = new ArrayList<>();
        for (Actor actor : actorsInRadiusTower0) {
            if (actor.getTeam() == team
                    && actor.getActorType() == ActorType.MINION
                    && actor.getHealth() > 0) {
                allyMinionsTower0.add(actor);
            }
        }

        double dT1 = a.getLocation().distance(purpleTower1Location);
        double dT0 = a.getLocation().distance(purpleTower0Location);

        if (dT1 <= TOWER_RANGE && allyMinionsTower1.isEmpty() && !enemyTower.isDead()) {
            return false;

        } else if (dT0 <= TOWER_RANGE && allyMinionsTower0.isEmpty() && !enemyBaseTower.isDead()) {
            return false;

        } else if ((dT1 <= TOWER_RANGE && !enemyTower.isDead()
                        || dT0 <= TOWER_RANGE && !enemyBaseTower.isDead())
                && a.getActorType() == ActorType.PLAYER) {
            return false;
        }
        if (allyMinionsTower1.isEmpty()
                && (float) a.getLocation().getX() < -10f
                && !enemyTower.isDead()) {
            return false;
        } else if (allyMinionsTower0.isEmpty()
                && (float) a.getLocation().getX() < -26
                && !enemyBaseTower.isDead()) {
            return false;
        }
        return true;
    }

    private void attemptAttack(Actor target) {
        if (target != null) {
            if (!withinRange(target) && canMove()) {
                moveWithCollision(target.getLocation());
            } else if (withinRange(target)) {
                if (!isStopped()) stopMoving();
                if (canAttack()) attack(target);
            }
        }
    }

    private void levelUpStats() {
        switch (level) {
            case 1:
                setStat("attackDamage", 70);
                setStat("spellDamage", 17);
                setStat("armor", 21);
                setStat("spellResist", 11);
                setStat("attackSpeed", 1450);
                setStat("health", 550);
                setStat("healthRegen", 3);
                maxHealth = 550;
                break;
            case 2:
                setStat("attackDamage", 90);
                setStat("spellDamage", 19);
                setStat("armor", 22);
                setStat("spellResist", 12);
                setStat("attackSpeed", 1400);
                setStat("health", 600);
                setStat("healthRegen", 4);
                maxHealth = 600;
                break;
            case 3:
                setStat("attackDamage", 95);
                setStat("spellDamage", 21);
                setStat("armor", 33);
                setStat("spellResist", 13);
                setStat("attackSpeed", 1350);
                setStat("health", 650);
                setStat("healthRegen", 5);
                maxHealth = 650;
                break;
            case 4:
                setStat("attackDamage", 100);
                setStat("spellDamage", 23);
                setStat("armor", 49);
                setStat("spellResist", 14);
                setStat("attackSpeed", 1300);
                setStat("health", 700);
                setStat("healthRegen", 6);
                maxHealth = 700;
                break;
            case 5:
                setStat("attackDamage", 135);
                setStat("spellDamage", 25);
                setStat("armor", 50);
                setStat("spellResist", 15);
                setStat("attackSpeed", 1250);
                setStat("health", 750);
                setStat("healthRegen", 7);
                maxHealth = 750;
                break;
            case 6:
                setStat("attackDamage", 140);
                setStat("spellDamage", 27);
                setStat("armor", 51);
                setStat("spellResist", 26);
                setStat("attackSpeed", 1200);
                setStat("health", 800);
                setStat("healthRegen", 8);
                maxHealth = 800;
                break;
            case 7:
                setStat("attackDamage", 185);
                setStat("spellDamage", 29);
                setStat("armor", 52);
                setStat("spellResist", 27);
                setStat("attackSpeed", 1150);
                setStat("health", 850);
                setStat("healthRegen", 9);
                maxHealth = 850;
                break;
            case 8:
                setStat("attackDamage", 190);
                setStat("spellDamage", 31);
                setStat("armor", 78);
                setStat("spellResist", 28);
                setStat("attackSpeed", 1100);
                setStat("health", 900);
                setStat("healthRegen", 10);
                maxHealth = 900;
                break;
            case 9:
                setStat("attackDamage", 195);
                setStat("spellDamage", 33);
                setStat("armor", 129);
                setStat("spellResist", 29);
                setStat("attackSpeed", 1050);
                setStat("health", 950);
                setStat("healthRegen", 11);
                maxHealth = 950;
                break;
            case 10:
                setStat("attackDamage", 200);
                setStat("spellDamage", 35);
                setStat("armor", 130);
                setStat("spellResist", 45);
                setStat("attackSpeed", 1000);
                setStat("health", 1000);
                setStat("healthRegen", 12);
                maxHealth = 1000;
                break;
        }
    }

    public void respawn() {
        dead = false;
        canMove = true;
        setHealth((int) maxHealth, (int) maxHealth);
        setLocation(spawnPoint);
        removeEffects();
        agressors.clear();
        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);
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

        ExtensionCommands.respawnActor(parentExt, room, id);
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
        UserActor player = handler.getPlayers().get(0);

        if (player != null) {
            int playerLevel = player.getLevel();
            int botLevel = this.level;

            int additionalXP = 2;
            additionalXP *= (botLevel - playerLevel);
            if (additionalXP < 0) {
                additionalXP = 0;
            }
            int totalXPToAdd = 2 + additionalXP;
            xp += totalXPToAdd;
            checkLevelUp();
        }
    }

    private void checkLevelUp() {
        int level = ChampionData.getXPLevel(xp);
        if (level != this.level) {
            this.level = level;
            Console.debugLog("level up");

            HashMap<String, Double> updateData = new HashMap<>(3);
            updateData.put("level", (double) level);
            updateData.put("xp", (double) xp);
            updateData.put("pLevel", getPLevel());

            ExtensionCommands.updateActorData(parentExt, room, id, updateData);
            levelUpStats();
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
            this.applyStopMovingDuringAttack();
            PassiveAttack passiveAttack = new PassiveAttack(a, handleAttack(a));
            parentExt.getTaskScheduler().schedule(passiveAttack, 500, TimeUnit.MILLISECONDS);
            passiveStart = System.currentTimeMillis();
            if (canUseQ()) {
                useQ();
            }
        }
    }

    private class PassiveAttack implements Runnable {

        Actor target;
        boolean crit;

        PassiveAttack(Actor t, boolean crit) {
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (this.crit) {
                damage *= 2;
            }
            if (target.getActorType() != ActorType.TOWER && target.getActorType() != ActorType.BASE)
                damage = handlePassive(target, damage);
            new Champion.DelayedAttack(parentExt, Bot.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }

    protected boolean handleAttack(
            Actor a) { // To be used if you're not using the standard DelayedAttack Runnable
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
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            return crit;
        }
        return false;
    }

    private void applyStopMovingDuringAttack() {
        stopMoving();
        isAutoAttacking = true;
        Runnable resetIsAttacking = () -> isAutoAttacking = false;
        parentExt
                .getTaskScheduler()
                .schedule(resetIsAttacking, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setTarget(Actor a) {}
}

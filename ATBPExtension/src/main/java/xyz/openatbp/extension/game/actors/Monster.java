package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.MovementState;
import xyz.openatbp.extension.game.effects.ActorState;

public class Monster extends Actor {

    public static final float JG_MONSTER_AGGRO_RANGE = 10f;

    enum AggroState {
        PASSIVE,
        ATTACKED
    }

    enum MonsterType {
        SMALL,
        BIG
    }

    enum BuffType {
        GNOME,
        WOLF,
        BEAR,
        OWL,
        NONE
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

    public Monster(
            ATBPExtension parentExt,
            Room room,
            Point2D startingLocation,
            String monsterName,
            String id) {
        this.startingLocation = startingLocation;
        this.type = MonsterType.SMALL;
        this.attackCooldown = 0;
        this.parentExt = parentExt;
        this.room = room;
        this.location = this.startingLocation;
        this.team = 2;
        this.avatar = monsterName;
        this.stats = this.initializeStats();
        this.id = id;
        this.maxHealth = this.stats.get("health");
        this.currentHealth = this.maxHealth;
        this.actorType = ActorType.MONSTER;
        this.xpWorth = this.parentExt.getActorXP(this.avatar);
        this.displayName = parentExt.getDisplayName(monsterName);
        this.updateMaxHealth();
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) { // Runs when taking damage
        try {
            if (this.dead) return true;
            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            List<Actor> enemies =
                    Champion.getEnemyActorsInRadius(rh, team, location, JG_MONSTER_AGGRO_RANGE);
            enemies.removeIf(e -> !(e instanceof UserActor || e instanceof Bot));

            if (enemies.isEmpty() && state == AggroState.PASSIVE) return false;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                if (ChampionData.getJunkLevel(ua, "junk_1_demon_blood_sword") > 0) {
                    double junkData =
                            ChampionData.getCustomJunkStat(ua, "junk_1_demon_blood_sword");
                    damage *= (int) (1 + junkData);
                }
            }
            AttackType attackType = this.getAttackType(attackData);
            int newDamage = this.getMitigatedDamage(damage, attackType, a);
            if (a instanceof UserActor || a instanceof Bot) {
                a.addDamageGameStat(newDamage, attackType);
            }
            boolean returnVal = super.damaged(a, newDamage, attackData);
            if (!this.headingBack && isProperActor(a)) { // attacks the nearest attacker
                state = AggroState.ATTACKED;
                if (!effectManager.hasState(ActorState.CHARMED) && this.target == null
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

    public void updateMaxHealth() {
        int averagePLevel = parentExt.getRoomHandler(this.room.getName()).getAveragePlayerLevel();
        if (averagePLevel != level) {
            int levelDiff = averagePLevel - level;
            this.maxHealth += parentExt.getHealthScaling(this.avatar) * levelDiff;
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
    public void handleKnockback(Point2D source, float distance, float speed) {
        super.handleKnockback(source, distance, speed);
        this.attackRangeOverride = true;
    }

    @Override
    public void attack(Actor a) { // TODO: Almost identical to minions - maybe move to Actor class?
        // Called when it is attacking a player
        if (this.attackCooldown == 0) {
            this.stopMoving();
            this.canMove = false;
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
        this.moveTowardsActor();
    }

    @Override
    public void die(Actor a) { // Called when monster dies
        Console.debugLog(this.id + " has died! " + this.dead);
        if (!this.dead) { // No double deaths
            this.dead = true;

            if (movementState != MovementState.KNOCKBACK && movementState != MovementState.PULLED) {
                stopMoving();
            }

            this.currentHealth = -1;
            RoomHandler roomHandler = parentExt.getRoomHandler(this.room.getName());
            int scoreValue = parentExt.getActorStats(this.avatar).get("valueScore").asInt();
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
        effectManager.handleEffectsUpdate();
        if (this.dead) return;

        handleMovementUpdate();
        handleCharmMovement();

        if (this.target != null
                && (this.target.getEffectManager().hasState(ActorState.INVISIBLE))) {
            this.state = AggroState.PASSIVE;
            startMoveTo(startingLocation);
            this.target = null;
        }
        if (this.headingBack && this.location.distance(startingLocation) <= 1f) {
            this.headingBack = false;
        }

        if (msRan % 1000 * 60
                == 0) { // Every second it checks average player level and scales accordingly
            this.updateMaxHealth();
        }
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
                startMoveTo(startingLocation);
                Console.debugLog(
                        "MONSTER this.isStopped() && this.location.distance(this.startingLocation) > 0.1d");
            }
        } else { // Monster is pissed!!
            if ((this.location.distance(this.startingLocation) >= 10 && !this.attackRangeOverride)
                    || (this.target != null && this.target.getHealth() <= 0)) {
                this.state = AggroState.PASSIVE; // Far from camp, heading back
                startMoveTo(startingLocation);
                this.target = null;
                this.headingBack = true;
            } else if (this.target != null) { // Chasing player
                if (this.attackRangeOverride) this.attackRangeOverride = false;
                if (this.withinRange(this.target) && this.canAttack()) {
                    this.attack(this.target);
                } else if (!this.withinRange(this.target) && this.canMove()) {
                    this.moveTowardsActor();
                } else if (this.withinRange(this.target)
                        // TODO: refactor to work with new movement system
                        && !effectManager.hasState(ActorState.FEARED)
                        && !effectManager.hasState(ActorState.CHARMED)) {
                    if (location.distance(target.getLocation())
                            < getPlayerStat("attackRange") - 0.5f) this.stopMoving();
                }
            }
        }
        if (this.attackCooldown > 0) this.reduceAttackCooldown();
    }

    @Override
    public boolean canMove() {
        for (ActorState s : effectManager.getStates().keySet()) {
            if (s == ActorState.ROOTED || s == ActorState.STUNNED || s == ActorState.FEARED) {
                if (effectManager.hasState(s)) return false;
            }
        }
        return this.canMove;
    }

    @Override
    public boolean withinRange(Actor a) {
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public void
            moveTowardsActor() { // Moves towards a specific point. TODO: Have the path stop based
        // on
        // collision radius
        if (!this.canMove()) return;
        if (effectManager.hasState(ActorState.FEARED)) return;
        if (this.target == null) return;

        startMoveTo(target.getLocation());

        /*if (this.path == null) {
            if (this.target != null
                    && this.movementLine.getP2().distance(this.target.getLocation()) > 0.1f) {
                startMoveTo(target.getLocation());
            }
        } else {
            if (this.target != null
                    && this.path.size() - this.pathIndex < 3
                    && this.path.get(this.path.size() - 1).distance(this.target.getLocation())
                            > 0.1f) {
                startMoveTo(target.getLocation());
            }
        }*/
    }

    public BuffType getBuffType() {
        if (this.avatar.equalsIgnoreCase("gnome_a")) return BuffType.GNOME;
        if (this.avatar.equalsIgnoreCase("grassbear")) return BuffType.BEAR;
        if (this.avatar.equalsIgnoreCase("hugwolf")) return BuffType.WOLF;
        if (this.avatar.equalsIgnoreCase("ironowl_a")) return BuffType.OWL;
        return BuffType.NONE;
    }

    public String getBuffDescription() {
        switch (this.getBuffType()) {
            case OWL:
            case GNOME:
                return "Increased AD by 15 and AP by 40!";
            case BEAR:
            case WOLF:
                return "Increased armor by 5 and shields by 5!";
        }
        return "invalid buff";
    }
}

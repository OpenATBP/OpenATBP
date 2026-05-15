package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.RoomGroup;

public class Tower extends Actor {
    private static final float DAMAGE_REDUCTION_NO_MINIONS = 0.85f;
    private static final int TOWER_NORMAL_ATTACK_SPEED = 2000;
    private static final int TOWER_ATTACK_SPEED_FIRST_SHOT = 1000;
    public static final float TOWER_ATTACK_RANGE = 6;

    private final int[] PURPLE_TOWER_NUM = {2, 1};
    private final int[] BLUE_TOWER_NUM = {5, 4};
    private long lastHit;
    private boolean destroyed = false;
    protected long lastMissSoundTime = 0;
    protected long lastSpellDeniedTime = 0;
    private List<Actor> actorsTargeted;
    private boolean reduceDmgTaken = false;

    public Tower(ATBPExtension parentExt, Room room, String id, int team, Point2D location) {
        this.currentHealth = 800;
        this.maxHealth = 800;
        this.location = location;
        this.id = id;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lastHit = 0;
        this.actorType = ActorType.TOWER;
        this.attackCooldown = 1000;
        this.avatar = "tower1";
        if (team == 1) this.avatar = "tower2";
        this.displayName = parentExt.getDisplayName(this.avatar);
        this.stats = this.initializeStats();
        this.actorsTargeted = new ArrayList<>();
        this.xpWorth = 15;
        ExtensionCommands.createWorldFX(
                parentExt,
                room,
                this.id,
                "fx_target_ring_6",
                this.id + "_ring",
                15 * 60 * 1000,
                (float) this.location.getX(),
                (float) this.location.getY(),
                true,
                this.team,
                0f);

        if (room.getGroupId().equals(RoomGroup.TUTORIAL.name())) {
            setStat("attackDamage", 50);
            this.currentHealth = 200;
            this.maxHealth = 200;
        }
    }

    public Tower(ATBPExtension parentExt, Room room, int team) {
        this.parentExt = parentExt;
        this.room = room;
        this.team = team;
        this.actorsTargeted = new ArrayList<>();
        this.actorType = ActorType.TOWER;
    }

    public Tower(ATBPExtension parentExt, Room room, String id, int team) {
        this.currentHealth = 800;
        this.maxHealth = 800;
        this.id = id;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lastHit = 0;
        this.actorType = ActorType.TOWER;
        this.attackCooldown = 1000;
        this.avatar = "tower1";
        if (team == 1) this.avatar = "tower2";
        this.displayName = parentExt.getDisplayName(this.avatar);
        this.stats = this.initializeStats();
        this.xpWorth = 15;
        this.actorsTargeted = new ArrayList<>();
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (this.destroyed) return true;
        if (this.target == null) {
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                if (System.currentTimeMillis() - this.lastMissSoundTime >= 1500
                        && getAttackType(attackData) == AttackType.PHYSICAL) {
                    lastMissSoundTime = System.currentTimeMillis();
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), ua.getId(), "sfx_attack_miss");
                } else if (System.currentTimeMillis() - this.lastSpellDeniedTime >= 1500) {
                    lastSpellDeniedTime = System.currentTimeMillis();
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), ua.getId(), "sfx_tower_no_damage_taken");
                }
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "tower_no_damage_taken",
                        500,
                        id + "_noDamage",
                        true,
                        "",
                        true,
                        false,
                        this.team);
            }
            return false;
        } else if (a.getActorType() == ActorType.MINION) damage *= 0.5;

        if (reduceDmgTaken) damage *= (1 - DAMAGE_REDUCTION_NO_MINIONS);

        this.changeHealth(this.getMitigatedDamage(damage, this.getAttackType(attackData), a) * -1);
        boolean notify = System.currentTimeMillis() - this.lastHit >= 1000 * 5;
        if (notify) ExtensionCommands.towerAttacked(parentExt, this.room, this.getTowerNum());
        if (notify) this.triggerNotification();
        return this.currentHealth <= 0;
    }

    @Override
    public void attack(Actor a) {
        attackCooldown = TOWER_NORMAL_ATTACK_SPEED;
        String projectileName = "tower_projectile_blue";
        String effectName = "tower_shoot_blue";
        if (this.team == 0) {
            projectileName = "tower_projectile_purple";
            effectName = "tower_shoot_purple";
        }
        float time = (float) (a.getLocation().distance(this.location) / 6f);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_turret_shoots_at_you", this.location);
        ExtensionCommands.createProjectileFX(
                this.parentExt,
                this.room,
                projectileName,
                this.id,
                a.getId(),
                "emitNode",
                "Bip01",
                time);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                effectName,
                600,
                this.id + "_attackFx",
                false,
                "emitNode",
                false,
                false,
                this.team);
        parentExt
                .getTaskScheduler()
                .schedule(
                        new Champion.DelayedAttack(
                                this.parentExt,
                                this,
                                a,
                                (int) this.getPlayerStat("attackDamage"),
                                "basicAttack"),
                        (int) (time * 1000),
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) {
        this.currentHealth = 0;
        if (!destroyed) {
            this.destroyed = true;
            this.dead = true;
            ExtensionCommands.towerDown(parentExt, this.room, this.getTowerNum());
            ExtensionCommands.knockOutActor(parentExt, this.room, this.id, a.getId(), 100);
            ExtensionCommands.destroyActor(parentExt, this.room, this.id);
            for (User u : room.getUserList()) {
                String actorId = "tower2a";
                if (this.team == 0) actorId = "tower1a";
                ExtensionCommands.createWorldFX(
                        parentExt,
                        u,
                        String.valueOf(u.getId()),
                        actorId,
                        this.id + "_destroyed",
                        1000 * 60 * 15,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        false,
                        this.team,
                        0f);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        u,
                        String.valueOf(u.getId()),
                        "tower_destroyed_explosion",
                        this.id + "_destroyed_explosion",
                        2000,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        false,
                        this.team,
                        0f);

                ExtensionCommands.removeFx(parentExt, room, id + "_ring");
                removeTargetIndicatorFX(a);

                if (this.target != null && this.target.getActorType() == ActorType.PLAYER)
                    ExtensionCommands.removeFx(parentExt, u, this.id + "_aggro");
            }
            for (UserActor ua : this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                String s = getTowerDownSound(ua);
                ExtensionCommands.playSound(parentExt, ua.getUser(), "global", "announcer/" + s);
            }

            Actor earner = null;

            if (a instanceof UserActor || a instanceof Bot) {
                earner = a;
                a.addGameStat("towers", 1);
            }

            parentExt
                    .getRoomHandler(this.room.getName())
                    .addScore(earner, a.getTeam(), TOWER_KILL_POINTS);
        }
    }

    protected String getTowerDownSound(UserActor ua) {
        return ua.getTeam() == this.team ? "your_tower_down" : "you_destroyed_tower";
    }

    private boolean isChampion(Actor a) {
        return a instanceof UserActor || a instanceof Bot;
    }

    private boolean isCompanion(Actor a) {
        return a.getActorType() == ActorType.COMPANION && !(a instanceof Bot);
    }

    private void clearTowerFocus(Actor a) {
        if (a.isTowerFocused()) a.setTowerFocused(false);
    }

    private Actor getBestTarget(List<Actor> actors) {
        float minDistActor = 100000;
        Actor closestActorTarget = null;

        float minDistChampion = 100000;
        Actor closestChampionTarget = null;

        float minDistCompanion = 100000;
        Actor closestCompanionTarget = null;

        for (Actor a : actors) {
            if (isChampion(a)) {
                float dist = (float) location.distance(a.getLocation());
                if (dist < minDistChampion) {
                    minDistChampion = dist;
                    closestChampionTarget = a;
                }
            }

            if (isCompanion(a)) {
                float dist = (float) location.distance(a.getLocation());
                if (dist < minDistCompanion) {
                    minDistCompanion = dist;
                    closestCompanionTarget = a;
                }
            }

            if (!isChampion(a) && !isCompanion(a)) {
                float dist = (float) location.distance(a.getLocation());
                if (dist < minDistActor) {
                    minDistActor = dist;
                    closestActorTarget = a;
                }
            }
        }
        return closestActorTarget != null
                ? closestActorTarget
                : closestCompanionTarget != null ? closestCompanionTarget : closestChampionTarget;
    }

    private Actor getFocusTarget(List<Actor> actors) {
        for (Actor a : actors) {
            if (a.isTowerFocused() && isChampion(a)) {
                return a;
            }

            if (a.isTowerFocused() && isCompanion(a)) {
                return a;
            }
        }
        return null;
    }

    private boolean isInRange(Actor a) {
        return a.getLocation().distance(location) <= TOWER_ATTACK_RANGE;
    }

    private void handleIndicatorFXRemoval() {
        if (actorsTargeted.isEmpty()) return;

        Iterator<Actor> iterator = actorsTargeted.iterator();
        while (iterator.hasNext()) {
            Actor a = iterator.next();
            if (!isInRange(a)) {

                if (a instanceof UserActor) {
                    UserActor ua = (UserActor) a;
                    removePlayerFx(ua);
                    clearTowerFocus(ua);
                }
                removeTargetIndicatorFX(a);
                iterator.remove();
            }
        }
    }

    private void removePlayerFx(UserActor ua) {
        ExtensionCommands.removeFx(parentExt, ua.getUser(), id + "_aggro");
    }

    private void removeTargetIndicatorFX(Actor a) {
        ExtensionCommands.removeFx(parentExt, room, id + "_tower_current_target_indicator");
    }

    @Override
    public void update(int msRan) {
        if (destroyed) return;
        handleDamageQueue();
        handleIndicatorFXRemoval();

        RoomHandler rh = parentExt.getRoomHandler(room.getName());

        List<Actor> enemiesInRadius = Champion.getActorsInRadius(rh, location, TOWER_ATTACK_RANGE);
        enemiesInRadius.removeIf(a -> a.getTeam() == team);
        enemiesInRadius.removeIf(a -> a instanceof Monster);

        if (enemiesInRadius.isEmpty()) {
            if (attackCooldown != TOWER_ATTACK_SPEED_FIRST_SHOT) {
                attackCooldown = TOWER_ATTACK_SPEED_FIRST_SHOT;
                target = null;
            }
            return;
        }

        if (attackCooldown > 0) attackCooldown -= 100;
        if (attackCooldown < 0) attackCooldown = 0;

        List<Actor> enemyMinions = new ArrayList<>(enemiesInRadius);
        enemyMinions.removeIf(e -> !(e instanceof Minion));

        this.reduceDmgTaken = enemyMinions.isEmpty();

        Actor potentialTarget;
        Actor focusTarget = getFocusTarget(enemiesInRadius);

        if (focusTarget != null) potentialTarget = focusTarget;
        else potentialTarget = getBestTarget(enemiesInRadius);

        if (potentialTarget == null) return;

        boolean targetChanged = target == null || !target.equals(potentialTarget);

        this.target = potentialTarget;

        if (target instanceof UserActor && !actorsTargeted.contains(target)) {
            targetPlayer((UserActor) target);
        }

        actorsTargeted.add(target);

        if (targetChanged) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    target.getId(),
                    "tower_current_target_indicator",
                    10 * 60 * 1000,
                    id + "_tower_current_target_indicator",
                    true,
                    "displayBar",
                    false,
                    true,
                    team);
        }

        if (canAttack(target)) attack(target);
    }

    @Override
    public void setTarget(Actor a) {}

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        actorsTargeted.remove(a);

        if ((isChampion(a) || isCompanion(a))) {
            clearTowerFocus(a);
        }

        this.target = null;
    }

    public int getTowerNum() { // Gets tower number for the client to process correctly
        /*
        Main map
        0 - Purple Base Tower
        1 - Purple Bot Tower
        2 - Purple Top Tower
        3 - Blue Base Tower
        4 - Blue Bot Tower
        5 - Blue Top Tower

        Practice map
        0 - Purple Base Tower
        1 - Purple First Tower
        3 - Blue Base Tower
        4 - Blue First Tower
         */
        if (!this.id.contains("gumball")) {
            String[] towerIdComponents = this.id.split("_");
            String roomGroup = room.getGroupId();
            GameMap gameMap = GameManager.getMap(GameManager.getRoomGroupEnum(roomGroup));

            if (gameMap == GameMap.BATTLE_LAB) {
                if (towerIdComponents[0].contains("blue")) {
                    return BLUE_TOWER_NUM[
                            (Integer.parseInt(towerIdComponents[1].replace("tower", ""))) - 1];
                } else {
                    return PURPLE_TOWER_NUM[
                            (Integer.parseInt(towerIdComponents[1].replace("tower", ""))) - 1];
                }
            } else {
                return Integer.parseInt(towerIdComponents[1].replace("tower", ""));
            }
        }
        return 0;
    }

    public void
            triggerNotification() { // Resets the hit timer so players aren't spammed by the tower
        // being
        // attacked
        this.lastHit = System.currentTimeMillis();
    }

    public boolean canAttack(Actor target) {
        return this.attackCooldown == 0 && isInRange(target) && target.getHealth() > 0;
    }

    public void targetPlayer(UserActor user) {
        ExtensionCommands.createWorldFX(
                parentExt,
                user.getUser(),
                user.getId(),
                "tower_danger_alert",
                this.id + "_aggro",
                10 * 60 * 1000,
                (float) location.getX(),
                (float) location.getY(),
                true,
                team,
                0f);
        ExtensionCommands.playSound(
                parentExt, user.getUser(), user.getId(), "sfx_turret_has_you_targeted", location);
    }
}

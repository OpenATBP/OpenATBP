package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

public class Tower extends Actor {
    private final int[] PURPLE_TOWER_NUM = {2, 1};
    private final int[] BLUE_TOWER_NUM = {5, 4};
    private long lastHit;
    private boolean destroyed = false;
    protected long lastMissSoundTime = 0;
    protected long lastSpellDeniedTime = 0;
    protected List<Actor> nearbyActors;
    private boolean isFocusingPlayer = false;
    private boolean isFocusingCompanion = false;
    private int numberOfAttacks = 0;

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
    }

    public Tower(ATBPExtension parentExt, Room room, int team) {
        this.parentExt = parentExt;
        this.room = room;
        this.team = team;
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (this.destroyed) return true;
        if (this.target == null && nearbyActors.isEmpty()) {
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                if (System.currentTimeMillis() - this.lastMissSoundTime >= 1500
                        && getAttackType(attackData) == AttackType.PHYSICAL) {
                    this.lastMissSoundTime = System.currentTimeMillis();
                    ExtensionCommands.playSound(
                            this.parentExt, ua.getUser(), ua.getId(), "sfx_attack_miss");
                } else if (System.currentTimeMillis() - this.lastSpellDeniedTime >= 1500) {
                    this.lastSpellDeniedTime = System.currentTimeMillis();
                    ExtensionCommands.playSound(
                            this.parentExt, ua.getUser(), ua.getId(), "sfx_tower_no_damage_taken");
                }
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "tower_no_damage_taken",
                        500,
                        this.id + "_noDamage",
                        true,
                        "",
                        true,
                        false,
                        this.team);
            }
            return false;
        } else if (a.getActorType() == ActorType.MINION) damage *= 0.5;
        this.changeHealth(this.getMitigatedDamage(damage, this.getAttackType(attackData), a) * -1);
        boolean notify = System.currentTimeMillis() - this.lastHit >= 1000 * 5;
        if (notify) ExtensionCommands.towerAttacked(parentExt, this.room, this.getTowerNum());
        if (notify) this.triggerNotification();
        return this.currentHealth <= 0;
    }

    @Override
    public void attack(Actor a) {
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
        if (!this.destroyed) {
            this.destroyed = true;
            this.dead = true;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                ua.addGameStat("towers", 1);
            }
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
                        1000,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        false,
                        this.team,
                        0f);
                ExtensionCommands.removeFx(parentExt, u, this.id + "_ring");
                ExtensionCommands.removeFx(parentExt, u, this.id + "_target");
                if (this.target != null && this.target.getActorType() == ActorType.PLAYER)
                    ExtensionCommands.removeFx(parentExt, u, this.id + "_aggro");
            }
            this.parentExt.getRoomHandler(this.room.getName()).addScore(null, a.getTeam(), 50);
            if (this.getTowerNum() == 0 || this.getTowerNum() == 3) {
                for (UserActor ua :
                        this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                    if (ua.getTeam() == this.team) {
                        ExtensionCommands.playSound(
                                parentExt, ua.getUser(), "global", "announcer/base_tower_down");
                    } else {
                        ExtensionCommands.playSound(
                                parentExt, ua.getUser(), "global", "announcer/you_destroyed_tower");
                    }
                }
            } else {
                for (UserActor ua :
                        this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                    if (ua.getTeam() == this.team) {
                        ExtensionCommands.playSound(
                                parentExt, ua.getUser(), "global", "announcer/your_tower_down");
                    } else {
                        ExtensionCommands.playSound(
                                parentExt, ua.getUser(), "global", "announcer/you_destroyed_tower");
                    }
                }
            }
        }
    }

    public List<UserActor> getUserActorsInTowerRadius() {
        ArrayList<UserActor> players =
                this.parentExt.getRoomHandler(this.room.getName()).getPlayers();
        ArrayList<UserActor> playersInRadius = new ArrayList<>();
        for (UserActor ua : players) {
            if (ua.location.distance(this.location) <= (float) this.getPlayerStat("attackRange"))
                playersInRadius.add(ua);
        }
        return playersInRadius;
    }

    @Override
    public void update(int msRan) {
        try {
            if (!this.destroyed) {
                this.handleDamageQueue();
                if (this.destroyed) return;
                nearbyActors =
                        Champion.getEnemyActorsInRadius(
                                this.parentExt.getRoomHandler(this.room.getName()),
                                this.team,
                                this.location,
                                (float) this.getPlayerStat("attackRange"));
                if (nearbyActors.isEmpty() && this.attackCooldown != 1000) {
                    if (numberOfAttacks != 0) this.numberOfAttacks = 0;
                    this.attackCooldown = 1000;
                }
                if (this.target == null) {
                    boolean hasMinion = false;
                    double distance = 1000;
                    Actor potentialTarget = null;
                    for (Actor a : nearbyActors) {
                        if (hasMinion && a.getActorType() == ActorType.MINION) {
                            if (a.getLocation().distance(this.location)
                                    < distance) { // If minions exist in range, it only focuses on
                                // finding the closest
                                // minion
                                potentialTarget = a;
                                distance = a.getLocation().distance(this.location);
                            }
                        } else if (!hasMinion
                                && (a.getActorType() == ActorType.MINION
                                        || a.getActorType()
                                                == ActorType
                                                        .COMPANION)) { // If minions have not been
                            // found yet but it just
                            // found
                            // one, sets the first target to be searched
                            hasMinion = true;
                            potentialTarget = a;
                            distance = a.getLocation().distance(this.location);
                        } else if (!hasMinion
                                && a.getActorType()
                                        == ActorType
                                                .PLAYER) { // If potential target is a player and no
                            // minion has been found,
                            // starts processing closest player
                            if (a.getLocation().distance(this.location) < distance) {
                                potentialTarget = a;
                                distance = a.getLocation().distance(this.location);
                            }
                        }
                    }
                    if (potentialTarget != null) {
                        this.target = potentialTarget;
                        if (this.target.getActorType() == ActorType.PLAYER) {
                            UserActor user = (UserActor) this.target;
                            this.targetPlayer(user);
                        }
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.target.getId(),
                                "tower_current_target_indicator",
                                10 * 60 * 1000,
                                this.id + "_target",
                                true,
                                "displayBar",
                                false,
                                true,
                                this.team);
                    }
                } else {
                    if (this.target.getHealth() <= 0) {
                        if (this.target.getActorType() == ActorType.COMPANION
                                && isFocusingCompanion) isFocusingCompanion = false;
                        if (this.target.getActorType() == ActorType.PLAYER && isFocusingPlayer)
                            isFocusingPlayer = false;
                        this.resetTarget(this.target);
                        return;
                    }
                    if (!isFocusingCompanion && !isFocusingPlayer) {
                        for (Actor a :
                                Champion.getActorsInRadius(
                                        this.parentExt.getRoomHandler(this.room.getName()),
                                        this.location,
                                        (float) this.getPlayerStat("attackRange"))) {
                            if (a.getActorType() == ActorType.COMPANION
                                    && a.getTeam() != this.team
                                    && a.towerAggroCompanion
                                    && a.getHealth() > 0
                                    && this.target != null
                                    && this.target.getId().equalsIgnoreCase(a.getId())) {
                                this.target = a;
                                ExtensionCommands.removeFx(
                                        this.parentExt, this.room, this.id + "_target");
                                ExtensionCommands.createActorFX(
                                        this.parentExt,
                                        this.room,
                                        this.target.getId(),
                                        "tower_current_target_indicator",
                                        10 * 60 * 1000,
                                        this.id + "_target",
                                        true,
                                        "displayBar",
                                        false,
                                        true,
                                        this.team);
                                this.isFocusingCompanion = true;
                            }
                        }
                    }
                    if (!isFocusingPlayer && !isFocusingCompanion) {
                        for (UserActor ua : getUserActorsInTowerRadius()) {
                            if (ua.getActorType() == ActorType.PLAYER
                                    && ua.getHealth() > 0
                                    && this.target != null
                                    && !this.target.getId().equalsIgnoreCase(ua.getId())) {
                                if (ua.getTeam() != this.team && ua.changeTowerAggro) {
                                    this.target = ua;
                                    this.targetPlayer(ua);
                                    ExtensionCommands.removeFx(
                                            this.parentExt, this.room, this.id + "_target");
                                    ExtensionCommands.createActorFX(
                                            this.parentExt,
                                            this.room,
                                            this.target.getId(),
                                            "tower_current_target_indicator",
                                            10 * 60 * 1000,
                                            this.id + "_target",
                                            true,
                                            "displayBar",
                                            false,
                                            true,
                                            this.team);
                                    this.isFocusingPlayer = true;
                                }
                            }
                        }
                    }
                    if (this.attackCooldown > 0) this.reduceAttackCooldown();
                    if (nearbyActors.isEmpty()) {
                        if (this.target != null && this.target.getActorType() == ActorType.PLAYER) {
                            UserActor ua = (UserActor) this.target;
                            ExtensionCommands.removeFx(
                                    this.parentExt, ua.getUser(), this.id + "_aggro");
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_target");
                        }
                        this.target = null;
                    } else {
                        if (this.target != null
                                && this.target.getLocation().distance(this.location)
                                        <= this.getPlayerStat("attackRange")) {
                            if (this.canAttack()) {
                                this.attack(this.target);
                                this.numberOfAttacks++;
                                if (this.numberOfAttacks > 0) this.attackCooldown = 2000;
                            }
                        } else {
                            if (this.target != null) this.resetTarget(this.target);
                            this.isFocusingPlayer = false;
                            this.isFocusingCompanion = false;
                        }
                    }
                }
                List<Actor> minionsNearby = new ArrayList<>();
                if (!nearbyActors.isEmpty()) {
                    for (Actor a : nearbyActors) {
                        if (a.getActorType() == ActorType.MINION && a.getTeam() != this.team)
                            minionsNearby.add(a);
                    }
                    if (!minionsNearby.isEmpty()) {
                        this.setStat("armor", 75);
                        this.setStat("spellResist", 100);
                    }
                } else {
                    this.setStat("armor", 600);
                    this.setStat("spellResist", 800);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setTarget(Actor a) {
        if (this.target != null) this.resetTarget(this.target);
        this.target = a;
    }

    public String getId() {
        return this.id;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (a.getActorType() == ActorType.COMPANION && isFocusingCompanion)
            isFocusingCompanion = false;
        if (a.getActorType() == ActorType.PLAYER && isFocusingPlayer) isFocusingPlayer = false;
        this.resetTarget(a);
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
            if (!room.getGroupId().equalsIgnoreCase("practice")) {
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
        /*String[] towerIdComponents = this.id.split("_");
        if(!room.getGroupId().equalsIgnoreCase("practice")){
            if(towerIdComponents[0].equalsIgnoreCase("blue")){
                return BLUE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
            }else{
                return PURPLE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
            }
        }*/
        return 0;
    }

    public void
            triggerNotification() { // Resets the hit timer so players aren't spammed by the tower
        // being
        // attacked
        this.lastHit = System.currentTimeMillis();
    }

    public boolean canAttack() {
        return this.attackCooldown == 0;
    }

    public void resetTarget(Actor a) { // TODO: Does not always work
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            ExtensionCommands.removeFx(this.parentExt, ua.getUser(), this.id + "_aggro");
        }
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target");
        this.target = null;
    }

    public void targetPlayer(UserActor user) {
        ExtensionCommands.setTarget(this.parentExt, user.getUser(), this.id, user.getId());
        ExtensionCommands.createWorldFX(
                this.parentExt,
                user.getUser(),
                user.getId(),
                "tower_danger_alert",
                this.id + "_aggro",
                10 * 60 * 1000,
                (float) this.location.getX(),
                (float) this.location.getY(),
                true,
                this.team,
                0f);
        ExtensionCommands.playSound(
                this.parentExt,
                user.getUser(),
                user.getId(),
                "sfx_turret_has_you_targeted",
                this.location);
    }
}

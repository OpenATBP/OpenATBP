package xyz.openatbp.extension.game.champions;

import static xyz.openatbp.extension.game.champions.Lich.Skully.SKULLY_DAMAGE_PERCENT;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class Lich extends UserActor {
    private static final int PASSIVE_DURATION = 20000;
    private static final int PASSIVE_COOLDOWN = 40000;
    private static final int Q_SPEED_DURATION = 6000;
    private static final double Q_SPEED_PERCENT = 0.25d;
    private static final int Q_DURATION = 6000;
    private static final int Q_SLOW_DURATION = 1500;
    private static final double Q_SLOW_PERCENT = 0.3d;
    private static final int W_CHARM_DURATION = 2000;
    private static final int E_DURATION = 5000;
    private static final int E_TICK_COOLDOWN = 500;
    public static final int E_G_COOLDOWN = 250;

    private Skully skully;
    private long lastSkullySpawn;
    private boolean qActivated = false;
    private long lastQPointTime = 0;
    private HashMap<Point2D, Long> qPoints;
    private Point2D eLocation;
    private int eUses = 0;
    private boolean eActive = false;
    private long eStartTime = 0;
    private long lastETickTime = 0;

    private Map<Actor, Long> actorsWithQSlow = new HashMap<>();

    public Lich(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        lastSkullySpawn = 0;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.skully != null) skully.update(msRan);
        if (this.qActivated) {
            if (!qPoints.isEmpty()) {
                long time = System.currentTimeMillis();
                qPoints.entrySet().removeIf(entry -> time - entry.getValue() >= 4000);
            }
            if (System.currentTimeMillis() - lastQPointTime >= 200) {
                lastQPointTime = System.currentTimeMillis();
                qPoints.put(this.location, lastQPointTime);
            }
            ArrayList<Actor> qVictimsThisLoop = new ArrayList<>();
            Iterator<Map.Entry<Point2D, Long>> iterator = qPoints.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Point2D, Long> entry = iterator.next();
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> enemiesInRadius =
                        Champion.getEnemyActorsInRadius(handler, team, entry.getKey(), 1.5f);
                for (Actor a : enemiesInRadius) {
                    if (!qVictimsThisLoop.contains(a)) {
                        if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                            qVictimsThisLoop.add(a);
                            JsonNode spelldata = getSpellData(1);
                            double damage = (double) getSpellDamage(spelldata, false) / 10;
                            a.addToDamageQueue(this, damage, spelldata, true);
                        }

                        if (isNeitherStructureNorAlly(a)) {
                            applySlow(a);
                        }
                    }
                }
            }
        }

        if (this.eActive && this.eLocation != null) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actors = Champion.getActorsInRadius(handler, eLocation, 3f);
            if (!actors.isEmpty()
                    && System.currentTimeMillis() - lastETickTime >= E_TICK_COOLDOWN) {

                lastETickTime = System.currentTimeMillis();

                JsonNode spellData = this.parentExt.getAttackData(this.getAvatar(), "spell3");
                double damage = getSpellDamage(spellData, false);
                boolean hit = false;

                for (Actor a : actors) {
                    if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                        hit = true;
                        a.addToDamageQueue(this, damage / 2, spellData, true);
                    }
                }

                if (hit) {
                    String eTickSound = "sfx_lich_charm_shot_hit";
                    ExtensionCommands.playSound(parentExt, room, "", eTickSound, eLocation);
                }
            }
        }
        if (this.eActive && System.currentTimeMillis() - this.eStartTime >= E_DURATION) {
            this.eActive = false;
            this.eLocation = null;
            ExtensionCommands.removeStatusIcon(parentExt, player, "ultDuration");
            Runnable handleECooldown =
                    () -> {
                        this.canCast[2] = true;
                        this.eUses = 0;
                    };
            int baseECooldown = ChampionData.getBaseAbilityCooldown(this, 3);
            int delay = getReducedCooldown(baseECooldown);

            scheduleTask(handleECooldown, delay);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "e", true, delay, E_G_COOLDOWN);
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            String projectile = "lich_projectile";
            PassiveAttack passiveAttack = new PassiveAttack(this, a, this.handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectile);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        // if(this.skully != null) this.setSkullyTarget(a);
        if (this.skully != null) this.skully.die(this.skully);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        boolean returnVal = super.damaged(a, damage, attackData);
        if (!returnVal && this.skully != null && this.skully.getTarget() == null)
            this.setSkullyTarget(a);
        return returnVal;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (this.skully != null) this.skully.die(this.skully);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (attackData.has("spellName")) {
            String sn = attackData.get("spellName").asText();
            if (sn.contains("1") || sn.contains("4")) {
                this.increaseStat("spellDamage", 1);
            }
        }
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        if (skully == null && System.currentTimeMillis() - lastSkullySpawn > PASSIVE_COOLDOWN) {
            this.spawnSkully(this.location);
        }
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                int delay = 0;
                try {
                    effectManager.addEffect(
                            this.id + "_lich_q_speed",
                            "speed",
                            Q_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            Q_SPEED_DURATION);

                    qActivated = true;
                    qPoints = new HashMap<>();
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "lichking_deathmist",
                            Q_DURATION,
                            this.id + "_lichTrail",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_lich_trail", this.location);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "vo/vo_lich_trail", this.location);
                    delay = getReducedCooldown(cooldown);
                    TrailHandler trailHandler = new TrailHandler();
                    scheduleTask(trailHandler, Q_DURATION);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "q", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    this.stopMoving();
                    ExtensionCommands.playSound(
                            parentExt, room, this.id, "sfx_lich_charm_shot", this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_lich_charm_shot",
                            this.location);

                    Line2D abilityLine = Champion.getAbilityLine(this.location, dest, 8f);
                    this.fireProjectile(
                            new LichWProjectile(
                                    parentExt,
                                    this,
                                    abilityLine,
                                    9f,
                                    0.5f,
                                    "projectile_lich_charm"),
                            this.location,
                            dest,
                            8f);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);

                break;
            case 3:
                this.canCast[2] = false;
                this.eUses++;
                if (this.eUses == 1) {
                    stopMoving(castDelay);
                    this.eLocation = dest;
                    ExtensionCommands.playSound(
                            parentExt, room, this.id, "sfx_lich_death_pool", eLocation);
                    ExtensionCommands.playSound(parentExt, room, this.id, "sfx_lich_well", dest);
                    ExtensionCommands.playSound(parentExt, room, this.id, "vo/vo_lich_well", dest);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_2.5",
                            this.id + "_lichUltRing",
                            E_DURATION + castDelay,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "e", true, castDelay, 0);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            castDelay);
                } else if (this.eUses == 2) {
                    if (eLocation != null) {
                        ExtensionCommands.actorAnimate(
                                this.parentExt, this.room, this.id, "idle", 100, false);

                        RoomHandler rh = parentExt.getRoomHandler(room.getName());
                        PathFinder pf = rh.getPathFinder();
                        Point2D teleportPoint =
                                pf.getNonObstaclePointOrIntersection(location, eLocation);
                        teleport(eLocation);

                        if (skully != null) {
                            skully.setLocation(teleportPoint);
                            skully.teleport(teleportPoint);
                        }

                        ExtensionCommands.removeStatusIcon(parentExt, player, "ultDuration");
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                this.id,
                                "lich_teleport",
                                1000,
                                this.id + "_lichTeleport",
                                true,
                                "Bip01",
                                true,
                                false,
                                team);
                    }
                }
                break;
        }
    }

    private void spawnSkully(Point2D location) {
        this.skully = new Skully(location);
        this.parentExt.getRoomHandler(this.room.getName()).addCompanion(this.skully);
        this.lastSkullySpawn = System.currentTimeMillis();
        ExtensionCommands.addStatusIcon(
                this.parentExt,
                this.player,
                "icon_lich_passive",
                "lich_spell_4_short_description",
                "icon_lich_passive",
                PASSIVE_DURATION);
        ExtensionCommands.actorAbilityResponse(
                parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "lich_skeleton_poof",
                1000,
                this.id + "_skeleton_poof",
                false,
                "",
                false,
                false,
                this.team);
    }

    public void setSkullyTarget(Actor a) {
        if (this.skully != null) this.skully.setTarget(a);
    }

    private void handleSkullyDeath() {
        this.skully = null;
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "icon_lich_passive");
    }

    private class TrailHandler implements Runnable {
        @Override
        public void run() {
            qActivated = false;
            qPoints.clear();
        }
    }

    private void applySlow(Actor a) {
        long lastProc = actorsWithQSlow.getOrDefault(a, -1L);

        if (lastProc == -1
                || System.currentTimeMillis() - lastProc > Q_SLOW_DURATION && a.isNotLeaping()) {
            a.getEffectManager()
                    .addState(
                            ActorState.SLOWED,
                            id + "_lich_q_slow",
                            Q_SLOW_PERCENT,
                            Q_SLOW_DURATION);
            actorsWithQSlow.put(a, System.currentTimeMillis());
        }
    }

    private LichAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new LichAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class LichAbilityRunnable extends AbilityRunnable {

        public LichAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            if (eUses == 1) {
                canCast[2] = true;
                eActive = true;
                eStartTime = System.currentTimeMillis();
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "lich_death_puddle",
                        id + "_lichPool",
                        E_DURATION,
                        (float) eLocation.getX(),
                        (float) eLocation.getY(),
                        false,
                        team,
                        0f);
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "ultDuration",
                        "lich_spell_3_description",
                        "icon_lich_s3",
                        E_DURATION);
            }
        }

        @Override
        protected void spellPassive() {}
    }

    protected class Skully extends Actor {
        protected static final int SKULLY_LIFE_SPAN = 20000;
        protected static final int MAX_SKULLY_SEPARATION = 9;
        protected static final double MIN_SKULLY_SEPARATION = 2;
        protected static final double SKULLY_DAMAGE_PERCENT = 0.4;

        private long spawnTimeStamp;
        private boolean dead = false;
        private boolean isAutoAttacking = false;

        Skully(Point2D spawnLocation) {
            this.room = Lich.this.room;
            this.parentExt = Lich.this.parentExt;
            this.currentHealth = 500;
            this.maxHealth = 500;
            this.location = spawnLocation;
            this.avatar = "skully";
            this.id = "skully_" + Lich.this.id;
            this.team = Lich.this.team;
            this.spawnTimeStamp = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            ExtensionCommands.createActor(
                    parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
            if (movementDebug)
                ExtensionCommands.createActor(
                        parentExt,
                        room,
                        id + "_movementDebug",
                        "gnome_b",
                        this.location,
                        0f,
                        this.team);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {
            Lich.this.increaseStat("spellDamage", 1);
            this.resetTarget();
        }

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return super.damaged(a, damage, attackData);
        }

        @Override
        public void attack(Actor a) {
            isAutoAttacking = true;
            Runnable resetIsAutoAttacking = () -> isAutoAttacking = false;
            scheduleTask(resetIsAutoAttacking, BASIC_ATTACK_DELAY);
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    false,
                    true);
            PassiveAttack passiveAttack = new PassiveAttack(this, a, false);
            scheduleTask(passiveAttack, BASIC_ATTACK_DELAY);
            this.attackCooldown = 1000;
        }

        @Override
        public void die(Actor a) {
            this.dead = true;
            this.currentHealth = 0;

            if (movementState != MovementState.KNOCKBACK && movementState != MovementState.PULLED) {
                stopMoving();
            }

            ExtensionCommands.destroyActor(parentExt, room, this.id);
            this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this);
            Lich.this.handleSkullyDeath();
        }

        @Override
        public void update(int msRan) {
            effectManager.handleEffectsUpdate();
            this.handleDamageQueue();
            if (this.dead) return;

            handleMovementUpdate();

            if (System.currentTimeMillis() - spawnTimeStamp >= SKULLY_LIFE_SPAN) {
                this.die(this);
                return;
            }
            if (this.attackCooldown > 0) this.attackCooldown -= 100;
            if (movementDebug)
                ExtensionCommands.moveActor(
                        this.parentExt,
                        this.room,
                        this.id + "_movementDebug",
                        this.location,
                        this.location,
                        2f,
                        false);
            if (this.target == null) { // Should follow Lich around
                if (location.distance(Lich.this.location) > MIN_SKULLY_SEPARATION) {
                    startMoveTo(Lich.this.location);
                } else if (isMoving) {
                    stopMoving();
                }
            } else {
                if (this.target.getHealth() <= 0) this.resetTarget();
                else {
                    if (this.location.distance(Lich.this.location) > MAX_SKULLY_SEPARATION) {
                        this.target = null;
                        startMoveTo(Lich.this.location);
                        return;
                    }
                    if (!this.withinRange(this.target)
                            && location.distance(Lich.this.location) > MIN_SKULLY_SEPARATION
                            && !this.isAutoAttacking) {
                        startMoveTo(Lich.this.location);
                    } else if (this.withinRange(this.target)) {
                        if (!this.isStopped()) this.stopMoving();
                        if (this.canAttack()) this.attack(this.target);
                    }
                }
            }
        }

        public void setTarget(Actor a) {
            if (this.target == a) return;
            this.target = a;
            startMoveTo(a.getLocation());
        }

        public void resetTarget() {
            this.target = null;
            List<Actor> nearbyActors =
                    Champion.getEnemyActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.team,
                            this.location,
                            4f);
            Actor highestPriorityActor = null;
            double actorDistance = 100d;
            for (Actor a : nearbyActors) {
                if (highestPriorityActor == null) {
                    highestPriorityActor = a;
                    actorDistance = a.getLocation().distance(this.location);
                } else {
                    if (a.getActorType() == ActorType.PLAYER) {
                        if (highestPriorityActor.getActorType() != ActorType.PLAYER) {
                            highestPriorityActor = a;
                            actorDistance = a.getLocation().distance(this.location);
                        } else if (actorDistance > a.getLocation().distance(this.location)) {
                            highestPriorityActor = a;
                            actorDistance = a.getLocation().distance(this.location);
                        }
                    } else {
                        if (actorDistance > a.getLocation().distance(this.location)
                                && highestPriorityActor.getActorType() != ActorType.PLAYER) {
                            highestPriorityActor = a;
                            actorDistance = a.getLocation().distance(this.location);
                        }
                    }
                }
            }
            if (highestPriorityActor != null) this.setTarget(highestPriorityActor);
            else {
                if (this.location.distance(Lich.this.location) > 2.5d) {
                    startMoveTo(Lich.this.location);
                }
            }
        }

        public Actor getTarget() {
            return this.target;
        }
    }

    private class PassiveAttack implements Runnable {

        Actor attacker;
        Actor target;
        boolean crit;

        PassiveAttack(Actor attacker, Actor target, boolean crit) {
            this.attacker = attacker;
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            if (attacker.getClass() == Lich.class) {
                double damage = this.attacker.getPlayerStat("attackDamage");
                if (crit) {
                    damage *= 1.25;
                    damage = handleGrassSwordProc(damage);
                }
                new Champion.DelayedAttack(parentExt, attacker, target, (int) damage, "basicAttack")
                        .run();
                Lich.this.setSkullyTarget(this.target);
            } else if (attacker.getClass() == Skully.class) {
                double damage =
                        10d + (Lich.this.getPlayerStat("spellDamage") * SKULLY_DAMAGE_PERCENT);
                new Champion.DelayedAttack(
                                parentExt, attacker, target, (int) damage, "skullyAttack")
                        .run();
                if (isNeitherStructureNorAlly(target)) Lich.this.handleLifeSteal();
            }
        }
    }

    public class LichWProjectile extends Projectile {

        public LichWProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float offsetDistance,
                String id) {
            super(parentExt, owner, path, speed, offsetDistance, offsetDistance, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(), "spell2");
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_lich_charm_shot_hit", victim.getLocation());
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    this.id,
                    "lich_charm_explosion",
                    id + "_charmExplosion",
                    1000,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    team,
                    0f);

            double dmg = getSpellDamage(spellData, true);

            victim.addToDamageQueue(Lich.this, dmg, spellData, false);
            victim.setCharmer(Lich.this);
            victim.getEffectManager()
                    .addState(ActorState.CHARMED, id + "_lich_w_charm", 0, W_CHARM_DURATION);
            destroy();
        }
    }
}

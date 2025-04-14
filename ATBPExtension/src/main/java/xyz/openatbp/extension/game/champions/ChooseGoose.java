package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class ChooseGoose extends UserActor {
    private static final int PASSIVE_COOLDOWN = 20000;
    public static final int CHEST_DURATION = 10000;
    public static final int PASSIVE_BUFF_DURATION = 6000;
    public static final double PASSIVE_SPEED_VALUE = 0.2;
    public static final double PASSIVE_ARMOR_VALUE = 0.2;
    public static final double Q_AS_BUFF_VALUE = -0.2;
    public static final int Q_DURATION = 5000;
    private static final int Q_TICK_FREQUENCY = 1000;
    private static final int Q_TICK_DMG_DELAY = 250;
    private static final int Q_DOT_DURATION = 3000;
    private static final double Q_AD_BUFF_VALUE = 0.1d;
    public static final double W_JUMP_SPEED = 14d;
    public static final double E_SLOW_VALUE = 0.4d;
    public static final int E_SLOW_DURATION = 2000;
    public static final int E_BUFF_DURATION = 2000;
    public static final double E_ARMOR_BUFF_VALUE = 0.2;

    private Chest chest = null;
    private Long lastChestSpawn = 0L;
    private boolean qActive = false;
    private Long lastQTick = System.currentTimeMillis();
    private final HashMap<Actor, Long> qDOTActors = new HashMap<>();
    private boolean jumpActive = false;
    private boolean isEActive = false;
    private Long eStartTime;
    private boolean interruptE = false;
    private Point2D eProjectileDestination;
    private JsonNode eSpellData;
    private boolean eProjectileFired = false;

    public ChooseGoose(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (chest != null) {
            chest.update(msRan);
        }

        if (!qDOTActors.isEmpty()) {
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            int damage = getSpellDamage(spellData, false);

            Iterator<Map.Entry<Actor, Long>> iterator = qDOTActors.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Actor, Long> entry = iterator.next();
                Actor a = entry.getKey();
                Long bleedingStartTime = entry.getValue();

                if (System.currentTimeMillis() - lastQTick >= Q_TICK_FREQUENCY) {
                    lastQTick = System.currentTimeMillis();
                    Champion.DelayedAttack dA =
                            new Champion.DelayedAttack(
                                    parentExt, this, a, damage, "spell1"); // to sync with neptr FX
                    scheduleTask(dA, Q_TICK_DMG_DELAY);

                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            a.getId(),
                            "neptr_passive",
                            1000,
                            a.getId() + Math.random(),
                            false,
                            "",
                            true,
                            false,
                            a.getTeam());
                }

                if (System.currentTimeMillis() - bleedingStartTime >= Q_DOT_DURATION
                        || a.getHealth() <= 0) {
                    iterator.remove();
                }
            }
        }

        if (isEActive && System.currentTimeMillis() - eStartTime <= 500 && hasInterrupingCC()
                || isEActive && getHealth() <= 0) {
            interruptE = true;
            isEActive = false;

            if (!this.getState(ActorState.STUNNED)
                    && !getState(ActorState.AIRBORNE)
                    && getHealth() > 0) {
                canMove = true;
            }
            ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
            ExtensionCommands.swapActorAsset(parentExt, room, id, "choosegoose");

            if (getHealth() > 0) {
                ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 1, false);
            }
            ExtensionCommands.removeFx(parentExt, room, id + "_eVFX");
        }

        if (isEActive
                && System.currentTimeMillis() - eStartTime >= 500
                && !interruptE
                && !eProjectileFired) {
            eProjectileFired = true;
            fireProjectile(eProjectileDestination, eSpellData);
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
        switch (ability) {
            case 1:
                canCast[0] = false;
                qActive = true;
                qDOTActors.clear();
                stopMoving();

                if (!isAutoAttacking) {
                    ExtensionCommands.actorAnimate(parentExt, room, id, "spell1a", 400, false);
                }

                double asDelta = this.getStat("attackSpeed") * Q_AS_BUFF_VALUE;
                double adDelta = this.getStat("attackDamage") * Q_AD_BUFF_VALUE;

                this.addEffect("attackSpeed", asDelta, Q_DURATION);
                this.addEffect("attackDamage", adDelta, Q_DURATION);

                String q_sfx = "sfx_choosegoose_q_activation";
                ExtensionCommands.playSound(parentExt, room, id, q_sfx, location);

                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "iceKing_spellCasting_hand",
                        Q_DURATION,
                        id + "_rHand",
                        true,
                        "mixamorig:RightHand",
                        true,
                        false,
                        team);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "iceKing_spellCasting_hand",
                        Q_DURATION,
                        id + "_lHand",
                        true,
                        "mixamorig:LeftHand",
                        true,
                        false,
                        team);
                Champion.handleStatusIcon(
                        parentExt,
                        this,
                        "icon_choosegoose_s1",
                        "choosegoose_spell_1_description",
                        Q_DURATION);

                int dur = parentExt.getAttackData(avatar, "spell1").get("spellDuration").asInt();
                scheduleTask(
                        abilityRunnable(
                                ability, spellData, getReducedCooldown(cooldown), gCooldown, dest),
                        dur);
                break;
            case 2:
                canCast[1] = false;
                jumpActive = true;

                Point2D ogLocation = location;
                Point2D dPoint = dash(dest, true, W_JUMP_SPEED);
                double time = ogLocation.distance(dPoint) / W_JUMP_SPEED;
                int wTime = (int) (time * 1000);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell2a", wTime, false);
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_choosegoose_w_jump", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "neptr_pie_trail",
                        wTime,
                        id + "_wTrail",
                        true,
                        "mixamorig:Spine",
                        false,
                        false,
                        team);

                int cd = getReducedCooldown(cooldown);
                ExtensionCommands.actorAbilityResponse(parentExt, player, "w", true, cd, gCooldown);
                scheduleTask(abilityRunnable(ability, spellData, wTime, gCooldown, dPoint), wTime);
                break;
            case 3:
                canCast[2] = false;
                eStartTime = System.currentTimeMillis();
                isEActive = true;

                eProjectileDestination = dest;
                eSpellData = spellData;

                stopMoving(1050);
                ExtensionCommands.swapActorAsset(parentExt, room, id, "choosegoose_axe");

                Runnable doAnimation =
                        () -> {
                            ExtensionCommands.actorAnimate(
                                    parentExt, room, id, "spell3", 1000, false);

                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    id,
                                    "tower_shoot_purple",
                                    2000,
                                    id + "_eVFX",
                                    true,
                                    "mixamorig:RightHand",
                                    false,
                                    false,
                                    team);

                            ExtensionCommands.playSound(
                                    parentExt, room, id, "sfx_choosegoose_e_projectile", location);
                        };
                scheduleTask(doAnimation, 150);
                scheduleTask(
                        abilityRunnable(
                                ability, spellData, getReducedCooldown(cooldown), gCooldown, dest),
                        1050);
                break;
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (jumpActive || isEActive) return false;
        else return super.canUseAbility(ability);
    }

    @Override
    public boolean canAttack() {
        if (isEActive) return false;
        return super.canAttack();
    }

    @Override
    public void attack(Actor a) {
        if (attackCooldown == 0) {
            applyStopMovingDuringAttack();
            BasicAttack basicAttack = new BasicAttack(a, handleAttack(a));
            scheduleTask(basicAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (attackData.has("spellName")) {
            String sn = attackData.get("spellName").asText();
            if (sn.contains("spell_2")) {
                canCast[1] = true;
                ExtensionCommands.actorAbilityResponse(parentExt, player, "w", true, 0, 0);
            }
        }
        if (a.getActorType() == ActorType.PLAYER) spawnChest((UserActor) a);
        super.handleKill(a, attackData);
    }

    @Override
    public void increaseStat(String key, double num) {
        super.increaseStat(key, num);
        if (key.equalsIgnoreCase("assists")) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<UserActor> players = handler.getPlayers();
            List<UserActor> enemies =
                    players.stream()
                            .filter(player -> player.getTeam() != this.team)
                            .collect(Collectors.toList());

            long timeDead = 0L;
            UserActor enemyWhoDied = null;
            for (UserActor enemy : enemies) { // find the most recently killed enemy
                if (enemy.getLastKilled() > timeDead) {
                    timeDead = enemy.getLastKilled();
                    enemyWhoDied = enemy;
                }
            }
            if (enemyWhoDied != null) spawnChest(enemyWhoDied);
        }
    }

    private void spawnChest(UserActor enemy) {
        if (System.currentTimeMillis() - lastChestSpawn >= PASSIVE_COOLDOWN) {
            lastChestSpawn = System.currentTimeMillis();

            Point2D enemyLocation = enemy.getLocation();
            Random random = new Random();
            double minRadius = 1; // Minimum distance from enemy
            double maxRadius = 2; // Maximum distance from enemy
            double randomAngle = Math.random() * 2 * Math.PI;
            double randomDistance = minRadius + (random.nextDouble() * (maxRadius - minRadius));

            Point2D randomLocation =
                    new Point2D.Double(
                            enemyLocation.getX() + (randomDistance * Math.cos(randomAngle)),
                            enemyLocation.getY() + (randomDistance * Math.sin(randomAngle)));

            Point2D chestPoint =
                    MovementManager.getDashPoint(
                            enemy, new Line2D.Float(enemyLocation, randomLocation));

            chest = new Chest(chestPoint, getOppositeTeam());
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            handler.addCompanion(chest);
            ExtensionCommands.playSound(
                    parentExt, room, id, "sfx_choosegoose_chest_spawn", chestPoint);
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
        }
    }

    private void fireProjectile(Point2D dest, JsonNode spellData) {
        Line2D line = Champion.getAbilityLine(location, dest, 7f);
        GooseProjectile projectile =
                new GooseProjectile(
                        parentExt, this, line, 11f, 0.5f, "projectile_choosegoose", spellData);
        fireProjectile(projectile, location, dest, 7f);

        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "cb_lance_hitspark",
                2000,
                id + "eFiredVFX",
                true,
                "",
                false,
                false,
                team);
    }

    private ChooseGooseAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new ChooseGooseAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class ChooseGooseAbilityRunnable extends AbilityRunnable {

        public ChooseGooseAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int cd = getReducedCooldown(cooldown);
            Runnable enableQCasting = () -> canCast[0] = true;
            scheduleTask(enableQCasting, cd);

            qActive = false;
            ExtensionCommands.actorAbilityResponse(parentExt, player, "q", true, cd, gCooldown);
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            scheduleTask(enableWCasting, getReducedCooldown(cooldown));
            jumpActive = false;

            ExtensionCommands.actorAnimate(parentExt, room, id, "spell2b", 500, false);
            ExtensionCommands.playSound(parentExt, room, id, "sfx_choosegoose_w_impact", location);

            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "finn_dash_whirlwind_fx",
                    id + "_dashImpactFX" + Math.random(),
                    2000,
                    (float) location.getX(),
                    (float) location.getY(),
                    false,
                    team,
                    0f);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "fx_target_ring_2",
                    id + "_dashImpactRing" + Math.random(),
                    500,
                    (float) location.getX(),
                    (float) location.getY(),
                    true,
                    team,
                    0f);

            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actors2 = Champion.getActorsInRadius(handler, location, 2f);
            List<Actor> actors1 = Champion.getActorsInRadius(handler, location, 1f);

            actors2.removeAll(actors1);

            double damageR2 = getSpellDamage(spellData, false);
            double damageR1 = damageR2 + 30;

            for (Actor a : actors2) {
                if (isNonStructure(a)) {
                    a.addToDamageQueue(ChooseGoose.this, damageR2, spellData, false);
                }
            }

            for (Actor a : actors1) {
                if (isNonStructure(a)) {
                    a.addToDamageQueue(ChooseGoose.this, damageR1, spellData, false);
                }
            }
        }

        @Override
        protected void spellE() {
            int cd = getReducedCooldown(cooldown);
            Runnable enableECasting = () -> canCast[2] = true;
            scheduleTask(enableECasting, cd);

            ExtensionCommands.actorAbilityResponse(parentExt, player, "e", true, cd, gCooldown);

            ExtensionCommands.swapActorAsset(parentExt, room, id, "choosegoose");
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 1, false);
            isEActive = false;
            eProjectileFired = false;
        }

        @Override
        protected void spellPassive() {}
    }

    private class Chest extends Actor {

        private final long timeOfBirth;

        Chest(Point2D location, int team) {
            this.room = ChooseGoose.this.room;
            this.parentExt = ChooseGoose.this.parentExt;
            this.currentHealth = 1;
            this.maxHealth = this.currentHealth;
            this.location = location;
            this.avatar = "choosegoose_chest";
            this.id = "chest" + "_" + ChooseGoose.this.id;
            this.team = team;
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();

            ExtensionCommands.createActor(parentExt, room, id, avatar, location, 0f, team);

            Runnable creationDelay =
                    () -> {
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "billy_passive",
                                id + "_chestFX",
                                CHEST_DURATION,
                                (float) location.getX(),
                                (float) location.getY(),
                                false,
                                team,
                                0f);
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "fx_aggrorange_2",
                                id + "_chestRing",
                                CHEST_DURATION,
                                (float) location.getX(),
                                (float) location.getY(),
                                true,
                                team,
                                0f);

                        Point2D gooseLocation = ChooseGoose.this.location;
                        if (gooseLocation.distance(location) < 10) {
                            String sound = "vo/vo_choosegoose_auto_attack_1";
                            ExtensionCommands.playSound(parentExt, room, id, sound, gooseLocation);
                        }
                    };
            int delay = 150;
            scheduleTask(creationDelay, delay);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {}

        @Override
        public void attack(Actor a) {}

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            if (attackData.has("spellType")) return false;
            return super.damaged(a, damage, attackData);
        }

        @Override
        public void die(Actor a) {
            dead = true;
            currentHealth = 0;

            int duration = 800;
            Runnable removeFX =
                    () -> {
                        ExtensionCommands.removeFx(parentExt, room, id + "_chestFX");
                        ExtensionCommands.removeFx(parentExt, room, id + "_chestRing");
                    };
            scheduleTask(removeFX, duration);

            if (a != this && a.getActorType() == ActorType.PLAYER) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        ChooseGoose.this.id,
                        "choosegoose_chest_open",
                        id + "_chest_open",
                        duration,
                        (float) location.getX(),
                        (float) location.getY(),
                        false,
                        team,
                        0f);
                ExtensionCommands.playSound(
                        parentExt,
                        room,
                        ChooseGoose.this.id,
                        "sfx_choosegoose_chest_open",
                        location);

                double speedDelta = a.getPlayerStat("speed") * PASSIVE_SPEED_VALUE;
                double armorDelta = a.getPlayerStat("armor") * PASSIVE_ARMOR_VALUE;

                a.addEffect("speed", speedDelta, PASSIVE_BUFF_DURATION);
                a.addEffect("armor", armorDelta, PASSIVE_BUFF_DURATION, "disconnect_buff_solo", "");
                UserActor ua = (UserActor) a;
                Champion.handleStatusIcon(
                        parentExt,
                        ua,
                        "icon_choosegoose_passive",
                        "choosegoose_spell_4_description",
                        PASSIVE_BUFF_DURATION);
            }
            ExtensionCommands.destroyActor(parentExt, room, id);
            parentExt.getRoomHandler(room.getName()).removeCompanion(this);
        }

        @Override
        public void update(int msRan) {
            handleDamageQueue();
            if (dead) return;
            if (System.currentTimeMillis() - timeOfBirth >= CHEST_DURATION) {
                die(this);
            }
        }

        @Override
        public void setTarget(Actor a) {}
    }

    public class GooseProjectile extends Projectile {
        private final JsonNode spellData;

        public GooseProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String projectileAsset,
                JsonNode spellData) {
            super(parentExt, owner, path, speed, hitboxRadius, projectileAsset);
            this.spellData = spellData;
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            List<Actor> nonStructureEnemies = roomHandler.getNonStructureEnemies(owner.getTeam());
            for (Actor a : nonStructureEnemies) {
                double collisionRadius =
                        parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                if (a.getLocation().distance(location) <= hitbox + collisionRadius
                        && isProperTarget(a)) {
                    return a;
                }
            }
            return null;
        }

        private boolean isProperTarget(Actor a) {
            return a.getActorType() == ActorType.PLAYER
                    || a.getAvatar().equals("keeoth")
                    || a.getAvatar().equals("goomonster")
                    || a.getAvatar().equals("ooze_monster");
        }

        @Override
        protected void hit(Actor victim) {
            victim.addToDamageQueue(
                    ChooseGoose.this, getSpellDamage(spellData, true), spellData, false);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    victim.getId(),
                    "rattleballs_dash_hit",
                    2000,
                    victim.getId() + "goose_projectile_VFX",
                    true,
                    "",
                    false,
                    false,
                    victim.getTeam());

            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    room,
                    id,
                    "hunson_projectile_explode",
                    id + "_destroyed",
                    1000,
                    (float) victim.getLocation().getX(),
                    (float) victim.getLocation().getY(),
                    false,
                    team,
                    0f);

            ExtensionCommands.playSound(
                    parentExt,
                    room,
                    victim.getId(),
                    "sfx_choosegoose_e_explosion",
                    victim.getLocation());

            victim.addState(ActorState.SLOWED, E_SLOW_VALUE, E_SLOW_DURATION);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    ChooseGoose.this.id,
                    "jake_shield",
                    E_BUFF_DURATION,
                    ChooseGoose.this.id + "_eShield",
                    true,
                    "mixamorig:Spine",
                    false,
                    false,
                    team);

            double eArmorDelta = ChooseGoose.this.getPlayerStat("armor") * E_ARMOR_BUFF_VALUE;

            ChooseGoose.this.addState(ActorState.IMMUNITY, 0, E_BUFF_DURATION);
            ChooseGoose.this.addEffect("armor", eArmorDelta, E_BUFF_DURATION);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    ChooseGoose.this.id,
                    "statusEffect_immunity",
                    E_BUFF_DURATION,
                    ChooseGoose.this.id + "_ultImmunity",
                    true,
                    "displayBar",
                    false,
                    false,
                    team);
            destroy();
        }
    }

    private class BasicAttack implements Runnable {

        Actor target;
        boolean crit;

        BasicAttack(Actor t, boolean crit) {
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (this.crit) {
                damage *= 2;
                damage = handleGrassSwordProc(damage);
            }
            if (qActive) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_choosegoose_q_hit", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "magicman_snake_explosion",
                        1000,
                        target.getId() + Math.random(),
                        false,
                        "",
                        true,
                        false,
                        target.getTeam());
                if (!qDOTActors.containsKey(target) && isNonStructure(target)) {
                    qDOTActors.put(target, System.currentTimeMillis());
                }
            }
            new Champion.DelayedAttack(
                            parentExt, ChooseGoose.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

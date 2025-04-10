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
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class ChooseGoose extends UserActor {
    private static final int PASSIVE_COOLDOWN = 20000;
    public static final int CHEST_CD = 20000;
    public static final int CHEST_DURATION = 10000;
    public static final int Q_AS_BUFF_DURATION = 5000;
    public static final double Q_AS_BUFF_VALUE = -0.2;
    public static final int Q_DURATION = 5000;
    private static final int Q_TICK_FREQUENCY = 1000;
    private static final int Q_TICK_DMG_DELAY = 250;
    private static final int Q_DOT_DURATION = 3000;
    public static final double W_JUMP_SPEED = 14d;
    public static final double PASSIVE_SPEED_VALUE = 0.2;
    public static final double PASSIVE_ARMOR_VALUE = 0.2;
    public static final int PASSIVE_BUFF_DURATION = 6000;

    private Chest chest = null;
    private Long lastChestSpawn = 0L;
    private boolean qActive = false;
    private Long lastQTick = System.currentTimeMillis();
    private final HashMap<Actor, Long> qDOTActors = new HashMap<>();
    private boolean jumpActive = false;

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
                stopMoving();

                if (!isAutoAttacking) {
                    ExtensionCommands.actorAnimate(parentExt, room, id, "spell1a", 400, false);
                }

                double asDelta = this.getStat("attackSpeed") * Q_AS_BUFF_VALUE;
                this.addEffect("attackSpeed", asDelta, Q_AS_BUFF_DURATION);

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
                Champion.handleStatusIcon(parentExt, this, "icon_choosegoose_s1", "", Q_DURATION);

                int dur = parentExt.getAttackData(avatar, "spell1").get("spellDuration").asInt();
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), dur);
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
                /*canCast[2] = false;
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);*/
                break;
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (jumpActive) return false;
        else return super.canUseAbility(ability);
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
        if (System.currentTimeMillis() - lastChestSpawn >= CHEST_CD) {
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
            ExtensionCommands.playSound(parentExt, room, id, "sfx_choosegoose_e_spawn", chestPoint);
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "passive", true, PASSIVE_COOLDOWN, 0);
        }
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
            qDOTActors.clear();
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
            double damageR1 = getSpellDamage(spellData, false) * 2;

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
        protected void spellE() {}

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
                        parentExt, ua, "icon_choosegoose_passive", "", PASSIVE_BUFF_DURATION);
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

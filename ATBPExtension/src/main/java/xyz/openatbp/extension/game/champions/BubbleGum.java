package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class BubbleGum extends UserActor {
    private static final String[] PASSIVE_NAMES = {
        "icon_pb_p0", "icon_pb_p1", "icon_pb_p2", "icon_pb_p3"
    };
    private static final float PASSIVE_ATTACKSPEED_VALUE = 0.2f;
    private static final int PASSIVE_RECHARGE_TIME = 10000;
    private static final int PASSIVE_EFFECT_DURATION = 5000;
    private static final int Q_CAST_DELAY = 750;
    private static final int Q_DURATION = 3000;
    private static final int Q_SLOW_DURATION = 2000;
    private static final float Q_SLOW_VALUE = 0.3f;
    private static final int Q_SPEED_DURATION = 2000;
    private static final float Q_SPEED_VALUE = 0.4f;
    private static final int E_DURATION = 4000;
    private static final int E_SECOND_USE_DELAY = 750;
    private int passiveAmmunition = 3;
    private long passiveTimeStamp = 0;
    private boolean potionActivated = false;
    private Point2D potionLocation;
    private long potionSpawn;
    private List<Turret> turrets;
    private int turretNum = 0;
    private int eUses = 0;
    private Point2D bombLocation;
    private long bombPlaceTime = 0;
    private Map<String, Integer> passiveVictims = new HashMap<>();

    public BubbleGum(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.turrets = new ArrayList<>(2);
        ExtensionCommands.addStatusIcon(
                parentExt,
                player,
                "Sticky Sweet",
                "princess_bubblegum_spell_4_short_description",
                "icon_pb_p3",
                0);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.passiveAmmunition < 3
                && System.currentTimeMillis() - this.passiveTimeStamp >= PASSIVE_RECHARGE_TIME) {
            this.passiveTimeStamp = System.currentTimeMillis();
            this.passiveAmmunition++;
            handlePassiveStatusIcons(passiveAmmunition);
        }
        if (potionActivated) {
            if (System.currentTimeMillis() - potionSpawn >= Q_DURATION) {
                potionActivated = false;
                potionSpawn = -1;
                potionLocation = null;
            } else {
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> affectedActors =
                        Champion.getActorsInRadius(handler, potionLocation, 2f);
                for (Actor a : affectedActors) {
                    if (a.getTeam() != this.team) {
                        JsonNode spellData =
                                this.parentExt.getAttackData("princessbubblegum", "spell1");
                        double damage = this.getSpellDamage(spellData, false) / 10f;
                        a.addToDamageQueue(this, damage, spellData, true);
                        if (isNonStructure(a)) {
                            a.addState(ActorState.SLOWED, Q_SLOW_VALUE, Q_SLOW_DURATION);
                        }

                    } else if (a.getId().equalsIgnoreCase(this.id)) {
                        this.addEffect(
                                "speed", this.getStat("speed") * Q_SPEED_VALUE, Q_SPEED_DURATION);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "statusEffect_speed",
                                Q_SPEED_DURATION,
                                this.id + "_pbQSpeed",
                                true,
                                "Bip01 Footsteps",
                                true,
                                false,
                                this.team);
                    }
                }
            }
        }
        ArrayList<Turret> turrets =
                new ArrayList<>(this.turrets); // To remove concurrent exceptions
        for (Turret t : turrets) {
            t.update(msRan);
        }
        if (this.bombLocation != null
                && System.currentTimeMillis() - this.bombPlaceTime >= E_DURATION) {
            int baseUltCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
            this.useBomb(getReducedCooldown(baseUltCooldown), 250);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        int turretSize = this.turrets.size();
        for (int i = 0; i < turretSize; i++) {
            this.turrets.get(i).die(BubbleGum.this);
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            String projectile = "bubblegum_projectile";
            String emit = SkinData.getBubbleGumBasicAttackEmit(avatar);
            PassiveAttack passiveAttack = new PassiveAttack(this, a, this.handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectile, emit);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        List<Turret> aliveTurrets = new ArrayList<>(this.turrets);
        for (Turret t : aliveTurrets) {
            t.die(a);
        }
        this.turrets = new ArrayList<>();
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
            case 1: // Q
                this.canCast[0] = false;
                try {
                    this.stopMoving();
                    String potionVo = SkinData.getBubbleGumQVO(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, potionVo, this.location);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_2",
                            id + "_potionArea",
                            Q_DURATION + castDelay,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "q", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 2: // W
                this.canCast[1] = false;
                try {
                    this.stopMoving();
                    String turretVo = SkinData.getBubbleGumWVO(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, turretVo, this.location);
                    this.spawnTurret(dest);
                    ExtensionCommands.actorAbilityResponse(
                            parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 3: // E
                this.canCast[2] = false;
                this.eUses++;
                if (this.eUses == 1) {
                    this.bombPlaceTime = System.currentTimeMillis();
                    this.bombLocation = dest;
                    this.stopMoving();
                    String bombVo = SkinData.getBubbleGumEVO(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, bombVo, this.location);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_3",
                            id + "_bombArea",
                            E_DURATION,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "bubblegum_bomb_trap",
                            this.id + "_bomb",
                            E_DURATION,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            false,
                            this.team,
                            0f);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "e", true, E_SECOND_USE_DELAY, 0);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            E_SECOND_USE_DELAY);
                } else if (this.eUses == 2) {
                    useBomb(getReducedCooldown(cooldown), gCooldown);
                }
                break;
        }
    }

    protected void useBomb(int cooldown, int gCooldown) {
        try {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            JsonNode spellData = parentExt.getAttackData("peebles", "spell3");
            List<Actor> actors = Champion.getEnemyActorsInRadius(handler, team, bombLocation, 3);
            if (this.location.distance(bombLocation) <= 3) actors.add(this);

            for (Actor a : actors) {
                if (a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) {
                    if (a.equals(this)) {
                        ExtensionCommands.actorAnimate(parentExt, room, id, "spell3b", 325, false);
                        Runnable animDelay =
                                () ->
                                        ExtensionCommands.actorAnimate(
                                                parentExt, room, id, "spell3c", 350, false);
                        scheduleTask(animDelay, 325);
                    } else {
                        a.addToDamageQueue(this, getSpellDamage(spellData, true), spellData, false);
                    }
                    a.knockback(this.bombLocation, 3.5f);
                }
            }
            String useBombVo = SkinData.getBubbleGumEGruntVO(avatar);
            ExtensionCommands.playSound(parentExt, room, this.id, useBombVo, this.location);
            ExtensionCommands.removeFx(parentExt, room, id + "_bomb");
            ExtensionCommands.removeFx(parentExt, room, id + "_bombArea");
            ExtensionCommands.playSound(parentExt, room, "", "sfx_bubblegum_bomb", bombLocation);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "bubblegum_bomb_explosion",
                    id + "_bombExplosion",
                    1500,
                    (float) bombLocation.getX(),
                    (float) bombLocation.getY(),
                    false,
                    team,
                    0f);
        } catch (Exception exception) {
            exception.printStackTrace();
            logExceptionMessage(avatar, 3);
        }
        ExtensionCommands.actorAbilityResponse(parentExt, player, "e", true, cooldown, gCooldown);
        Runnable handleECooldown =
                () -> {
                    this.eUses = 0;
                    this.canCast[2] = true;
                };
        scheduleTask(handleECooldown, cooldown);
        this.bombLocation = null;
    }

    private void spawnTurret(Point2D dest) {
        Turret t = null;
        if (this.turrets != null && this.turrets.size() == 2) {
            this.turrets.get(0).die(this);
            t = new Turret(dest, this.turretNum);
        } else if (this.turrets != null) {
            t = new Turret(dest, this.turretNum);
        }
        if (this.turrets != null) {
            this.turretNum++;
            this.turrets.add(t);
            this.parentExt.getRoomHandler(this.room.getName()).addCompanion(t);
        }
    }

    public void handleTurretDeath(Turret t) {
        this.turrets.remove(t);
    }

    private void handlePassiveStatusIcons(int passiveStacks) {
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "Sticky Sweet");
        int duration = passiveStacks < 3 ? PASSIVE_RECHARGE_TIME : 0;
        ExtensionCommands.addStatusIcon(
                this.parentExt,
                this.player,
                "Sticky Sweet",
                "princess_bubblegum_spell_4_short_description",
                PASSIVE_NAMES[passiveAmmunition],
                duration);
    }

    private PBAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new PBAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class PBAbilityRunnable extends AbilityRunnable {

        public PBAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - Q_CAST_DELAY;
            scheduleTask(enableQCasting, delay);
            if (getHealth() > 0) {
                potionActivated = true;
                potionLocation = dest;
                potionSpawn = System.currentTimeMillis();
                ExtensionCommands.playSound(parentExt, room, "", "sfx_bubblegum_potion_aoe", dest);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "bubblegum_ground_aoe",
                        id + "_potionAoe",
                        Q_DURATION,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
            } else {
                ExtensionCommands.removeFx(parentExt, room, id + "_potionArea");
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            if (eUses == 1) canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }

    private class PassiveAttack implements Runnable { // TODO: Maybe extend DelayedAttack?

        Actor attacker;
        Actor target;
        boolean crit;

        PassiveAttack(Actor a, Actor t, boolean crit) {
            this.attacker = a;
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = this.attacker.getPlayerStat("attackDamage");
            if (crit) {
                damage *= 2;
                damage = handleGrassSwordProc(damage);
            }
            if (BubbleGum.this.passiveAmmunition > 0
                    && this.target.getActorType()
                            == ActorType.PLAYER) { // TODO: Applying the passive here feels wrong
                BubbleGum.this.passiveAmmunition--;
                BubbleGum.this.passiveTimeStamp = System.currentTimeMillis();
                double delta = this.target.getStat("attackSpeed") * PASSIVE_ATTACKSPEED_VALUE;
                if (passiveVictims.containsKey(this.target.getId())) {
                    passiveVictims.put(
                            this.target.getId(), passiveVictims.get(this.target.getId()) + 1);
                    if (passiveVictims.get(this.target.getId()) == 3) {
                        this.target.addState(ActorState.SLOWED, 0.25, PASSIVE_EFFECT_DURATION / 2);
                        passiveVictims = new HashMap<>();
                    }
                } else passiveVictims.put(this.target.getId(), 1);
                this.target.addEffect("attackSpeed", delta, PASSIVE_EFFECT_DURATION);
                handlePassiveStatusIcons(passiveAmmunition);
                if (BubbleGum.this.passiveAmmunition == 0) passiveVictims = new HashMap<>();
            }
            new Champion.DelayedAttack(
                            parentExt, this.attacker, this.target, (int) damage, "basicAttack")
                    .run();
        }
    }

    private class Turret extends Actor {
        private long timeOfBirth;
        private Actor target;
        private boolean dead = false;
        private String iconName;
        private final int TURRET_LIFETIME = 60000;

        Turret(Point2D location, int turretNum) {
            this.room = BubbleGum.this.room;
            this.parentExt = BubbleGum.this.parentExt;
            this.currentHealth =
                    100
                            + (BubbleGum.this.getPlayerStat("healthPerLevel")
                                    * (BubbleGum.this.level - 1));
            this.maxHealth = this.currentHealth;
            this.location = location;
            this.avatar = "princessbubblegum_turret";
            this.id = "turret" + turretNum + "_" + BubbleGum.this.id;
            this.team = BubbleGum.this.team;
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            this.setStat("attackDamage", 10 + BubbleGum.this.getPlayerStat("spellDamage") * 0.4);
            this.iconName = "Turret #" + turretNum;
            ExtensionCommands.addStatusIcon(
                    parentExt, player, iconName, "Turret placed!", "icon_pb_s2", TURRET_LIFETIME);
            ExtensionCommands.createActor(
                    parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
            Runnable creationDelay =
                    () -> {
                        ExtensionCommands.playSound(
                                parentExt,
                                room,
                                this.id,
                                "sfx_bubblegum_turret_spawn",
                                this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fx_target_ring_3",
                                TURRET_LIFETIME,
                                this.id + "_ring",
                                true,
                                "",
                                false,
                                true,
                                this.team);
                    };
            int delay = 150;
            scheduleTask(creationDelay, delay);
            this.addState(ActorState.IMMUNITY, 0d, 1000 * 60 * 15);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {
            this.target = null;
            this.findTarget();
        }

        @Override
        public void attack(Actor a) {
            float time = (float) (a.getLocation().distance(this.location) / 10f);
            ExtensionCommands.playSound(
                    parentExt, room, this.id, "sfx_bubblegum_turret_shoot", this.location);
            ExtensionCommands.createProjectileFX(
                    parentExt,
                    room,
                    "bubblegum_turret_projectile",
                    this.id,
                    a.getId(),
                    "Bip01",
                    "targetNode",
                    time);
            int damage = 10 + (int) this.getPlayerStat("attackDamage");
            int delay = (int) time * 1000;
            String attack = "turretAttack";

            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, damage, attack);
            scheduleTask(delayedAttack, delay);
            BubbleGum.this.handleLifeSteal();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
        }

        @Override
        public void addState(ActorState state, double delta, int duration) {
            if (state == ActorState.CHARMED || state == ActorState.FEARED) return;
            super.addState(state, delta, duration);
        }

        @Override
        public void addState(
                ActorState state, double delta, int duration, String fxId, String emit) {
            if (state == ActorState.CHARMED || state == ActorState.FEARED) return;
            super.addState(state, delta, duration, fxId, emit);
        }

        @Override
        public void die(Actor a) {
            this.dead = true;
            this.currentHealth = 0;
            ExtensionCommands.removeStatusIcon(parentExt, player, iconName);
            ExtensionCommands.removeFx(parentExt, room, this.id + "_ring");
            ExtensionCommands.destroyActor(parentExt, room, this.id);
            this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this);
            BubbleGum.this.handleTurretDeath(this);
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            this.handleActiveEffects();
            if (this.dead) return;
            if (System.currentTimeMillis() - this.timeOfBirth >= TURRET_LIFETIME) {
                this.die(this);
                return;
            }
            if (this.attackCooldown > 0) this.reduceAttackCooldown();
            if (this.target != null && this.target.getHealth() > 0) {
                if (this.withinRange(this.target)
                        && this.canAttack()
                        && isNonStructure(this.target)) this.attack(this.target);
                else if (!this.withinRange(this.target)) {
                    this.target = null;
                    this.findTarget();
                }
            } else {
                if (this.target != null && this.target.getHealth() <= 0) this.target = null;
                this.findTarget();
            }
        }

        private boolean turretInBrush() {
            for (Path2D brush :
                    this.parentExt.getBrushPaths(
                            this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap())) {
                if (brush.contains(this.location)) {
                    return true;
                }
            }
            return false;
        }

        public void findTarget() {
            float range = (float) this.getPlayerStat("attackRange");
            RoomHandler handler = this.parentExt.getRoomHandler(room.getName());
            List<Actor> actorsInRange =
                    Champion.getEnemyActorsInRadius(handler, this.team, this.location, range);
            List<UserActor> playersInRange =
                    Champion.getUserActorsInRadius(handler, this.location, range);

            for (UserActor ua : playersInRange) {
                if (ua.getTeam() != this.team && ua.getHealth() > 0) {
                    if (ua.getState(ActorState.BRUSH) && turretInBrush()) this.target = ua;
                    if (!ua.getState(ActorState.BRUSH)) this.target = ua;
                    break;
                }
            }
            if (this.target == null) {
                for (Actor a : actorsInRange) {
                    if (a.getHealth() > 0) {
                        if (a.getState(ActorState.BRUSH) && turretInBrush()) this.target = a;
                        if (!a.getState(ActorState.BRUSH)) this.target = a;
                        break;
                    }
                }
            }
        }

        @Override
        public void setTarget(Actor a) {
            this.target = a;
        }
    }
}

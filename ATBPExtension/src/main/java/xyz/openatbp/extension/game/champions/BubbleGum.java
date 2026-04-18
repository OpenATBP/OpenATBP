package xyz.openatbp.extension.game.champions;

import static xyz.openatbp.extension.game.effects.EffectManager.DEFAULT_KNOCKBACK_SPEED;

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
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class BubbleGum extends UserActor {
    private static final String[] PASSIVE_NAMES = {
        "icon_pb_p0", "icon_pb_p1", "icon_pb_p2", "icon_pb_p3"
    };
    private static final float PASSIVE_AS_STACK_PERCENT = 0.2f;
    public static final double PASSIVE_SLOW_PERCENT = 0.25;
    private static final int PASSIVE_RECHARGE_TIME = 10000;
    private static final int PASSIVE_EFFECT_DURATION = 5000;
    private static final int Q_CAST_DELAY = 750;
    private static final int Q_DURATION = 3000;
    private static final int Q_SLOW_DURATION = 2000;
    private static final float Q_SLOW_VALUE_PERCENT = 0.3f;
    private static final int Q_SPEED_DURATION = 2000;
    private static final float Q_SPEED_VALUE_PERCENT = 0.4f;
    private static final int E_DURATION = 4000;
    private static final int E_SECOND_USE_DELAY = 750;
    public static final float E_KNOCKBACK_DIST = 3.5f;

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
    private Map<Actor, Integer> passiveStacks = new HashMap<>();
    private Map<Actor, Long> playersWithPassiveSlow = new HashMap<>();

    private long lastQSpeed = 0L;

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
                    if (isNeitherTowerNorAlly(a)) {
                        JsonNode spellData =
                                this.parentExt.getAttackData("princessbubblegum", "spell1");
                        double damage = this.getSpellDamage(spellData, false) / 10f;
                        a.addToDamageQueue(this, damage, spellData, true);
                        if (isNeitherStructureNorAlly(a)) {
                            a.getEffectManager()
                                    .addState(
                                            ActorState.SLOWED,
                                            id + "_pb_q_slow",
                                            Q_SLOW_VALUE_PERCENT,
                                            Q_SLOW_DURATION);
                        }

                    } else if (a.getId().equalsIgnoreCase(this.id)) {
                        if (System.currentTimeMillis() - lastQSpeed > Q_SPEED_DURATION) {
                            effectManager.addEffect(
                                    this.id + "_pb_q_speed",
                                    "speed",
                                    Q_SPEED_VALUE_PERCENT,
                                    ModifierType.MULTIPLICATIVE,
                                    ModifierIntent.BUFF,
                                    Q_SPEED_DURATION);

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

    private void handlePassive(Actor victim) {
        if (passiveAmmunition > 0 && victim instanceof UserActor) {
            int stacks = passiveStacks.getOrDefault(victim, 0);
            passiveAmmunition--;

            if (stacks < 3) {
                passiveStacks.put(victim, stacks + 1);
                victim.getEffectManager()
                        .addEffect(
                                victim.getId() + "_pb_as_passive_debuff",
                                "attackSpeed",
                                PASSIVE_AS_STACK_PERCENT,
                                ModifierType.MULTIPLICATIVE,
                                ModifierIntent.DEBUFF,
                                PASSIVE_EFFECT_DURATION);
            }

            if (stacks == 3) {
                long lastProc = playersWithPassiveSlow.getOrDefault(victim, -1L);

                if (lastProc == -1
                        || System.currentTimeMillis() - lastProc >= PASSIVE_EFFECT_DURATION) {
                    victim.getEffectManager()
                            .addState(
                                    ActorState.SLOWED,
                                    id + "_pb_passive_slow",
                                    PASSIVE_SLOW_PERCENT,
                                    PASSIVE_EFFECT_DURATION);
                    playersWithPassiveSlow.put(victim, System.currentTimeMillis());
                }
            }
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);

        if (a instanceof UserActor) {
            playersWithPassiveSlow.remove(a);
            passiveStacks.remove(a);
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
                    if (getHealth() > 0) {
                        this.stopMoving();
                        String turretVo = SkinData.getBubbleGumWVO(avatar);
                        ExtensionCommands.playSound(parentExt, room, id, turretVo, this.location);
                        this.spawnTurret(dest);
                    }
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
                } else if (this.eUses == 2 && getHealth() > 0) {
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
                double spellDamage = getSpellDamage(spellData, true);
                if (a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER) {

                    if (!a.equals(this)) {
                        a.handleKnockback(bombLocation, E_KNOCKBACK_DIST, DEFAULT_KNOCKBACK_SPEED);
                    }

                    if (effectManager.hasState(ActorState.SLOWED) && !a.equals(this)) {
                        spellDamage *= 1.25d;
                    }

                    Runnable animDelay =
                            () ->
                                    ExtensionCommands.actorAnimate(
                                            parentExt, room, id, "spell3c", 500, false);

                    if (a.equals(this)) {

                        float distToBomb = (float) location.distance(bombLocation);
                        float range = E_KNOCKBACK_DIST + distToBomb;

                        Point2D initLeap =
                                Champion.createLineTowards(bombLocation, location, range).getP2();
                        RoomHandler rh = parentExt.getRoomHandler(room.getName());
                        PathFinder pf = rh.getPathFinder();

                        Point2D leapLoc = pf.getNonObstaclePointOrIntersection(location, initLeap);

                        DashContext ctx =
                                new DashContext.Builder(location, leapLoc, DEFAULT_KNOCKBACK_SPEED)
                                        .isLeap(true)
                                        .canBeRedirected(false)
                                        .onEnd(animDelay)
                                        .build();
                        startDash(ctx);

                        ExtensionCommands.actorAnimate(parentExt, room, id, "spell3b", 325, false);
                    }
                }

                if (isNeitherTowerNorAlly(a)) {
                    a.addToDamageQueue(this, spellDamage, spellData, false);
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
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(
                            parentExt, this.attacker, this.target, (int) damage, "basicAttack")
                    .run();
        }
    }

    public class Turret extends Actor {
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
            this.currentHealth +=
                    (BubbleGum.this.maxHealth
                                    - this.parentExt
                                            .getActorStats("princessbubblegum")
                                            .get("health")
                                            .asInt())
                            * 0.2d;
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
            effectManager.addState(ActorState.IMMUNITY, id + "_immunity", 0d, 1000 * 60 * 15);
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
            if (this.dead) return;
            if (System.currentTimeMillis() - this.timeOfBirth >= TURRET_LIFETIME) {
                this.die(this);
                return;
            }
            if (this.attackCooldown > 0) this.reduceAttackCooldown();
            if (this.target != null && this.target.getHealth() > 0) {
                if (this.withinRange(this.target)
                        && this.canAttack()
                        && isNeitherStructureNorAlly(this.target)) this.attack(this.target);
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
            RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getName());
            GameMap gameMap = GameManager.getMap(roomGroup);
            List<Path2D> brushList = parentExt.getBrushPaths(gameMap);

            for (Path2D brush : brushList) {
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
                    if (ua.isInsideBrush() && turretInBrush()) this.target = ua;
                    if (!ua.isInsideBrush()) this.target = ua;
                    break;
                }
            }
            if (this.target == null) {
                for (Actor a : actorsInRange) {
                    if (a.getHealth() > 0) {
                        if (a.isInsideBrush() && turretInBrush()) this.target = a;
                        if (!a.isInsideBrush()) this.target = a;
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

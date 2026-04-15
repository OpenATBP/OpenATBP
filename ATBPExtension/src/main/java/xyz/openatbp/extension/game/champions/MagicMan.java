package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;

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

public class MagicMan extends UserActor {

    private static final int PASSIVE_DURATION = 3000;
    private static final int PASSIVE_SPEED_DURATION = 3000;
    private static final double PASSIVE_SPEED_PERCENT = 0.2d;
    private static final int Q_CAST_DELAY = 500;
    private static final int Q_SILENCE_DURATION = 2000;
    private static final int Q_ANGLE = 20;
    private static final int W_STEALTH_DURATION = 3000;

    private static final double E_DASH_SPEED = 10d;
    private static final double E_ARMOR_PERCENT = 0.2d;
    private static final double E_SHIELDS_PERCENT = 0.2;
    private static final double E_SLOW_PERCENT = 0.2d;
    private static final int E_SLOW_DURATION = 2500;
    private static final int E_DEBUFF_DURATION = 3000;
    public static final float E_RADIUS = 4f;

    private long passiveIconStarted = 0;
    private boolean passiveActivated = false;
    private Point2D wLocation = null;
    private Point2D wDest = null;
    private boolean ultStarted;
    private int eDashTime;
    private int wUses = 0;
    private MagicManClone magicManClone;
    private HashMap<Actor, Integer> snakedActors = new HashMap<>();

    public MagicMan(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (System.currentTimeMillis() - passiveIconStarted >= PASSIVE_DURATION
                && passiveActivated) {
            this.passiveActivated = false;
            ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "passive");
        }
        if (this.magicManClone != null) this.magicManClone.update(msRan);
        if (!effectManager.hasState(ActorState.INVISIBLE) && wLocation != null) {
            if (this.magicManClone != null) this.magicManClone.die(this);
            this.wLocation = null;
            this.wDest = null;
            this.canCast[1] = true;

            int wCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "w", true, getReducedCooldown(wCooldown), 250);
            Runnable resetWUses = () -> this.wUses = 0;
            scheduleTask(resetWUses, getReducedCooldown(wCooldown));
        }
    }

    @Override
    public void preventStealth() {
        if (this.magicManClone != null) return;
        super.preventStealth();
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            unveil();
            String projectile = "magicman_projectile";
            MagicManPassive passiveAttack = new MagicManPassive(a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectile);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.magicManClone != null) this.magicManClone.die(this);
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
                this.canCast[0] = false;
                try {
                    snakedActors.clear();
                    unveil();
                    stopMoving(castDelay);
                    basicAttackReset();

                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_magicman_snakes",
                            this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_magicman_snakes",
                            this.location);

                    Point2D endPoint = Champion.getAbilityLine(location, dest, 9.5f).getP2();
                    Line2D pathSP = new Line2D.Float(location, endPoint);

                    Line2D pathSP2 = Champion.getRotatedLine(location, endPoint, Q_ANGLE);
                    Line2D pathSP3 = Champion.getRotatedLine(location, endPoint, -Q_ANGLE);

                    SnakeProjectile sp =
                            new SnakeProjectile(
                                    parentExt,
                                    this,
                                    pathSP,
                                    7f,
                                    0.25f,
                                    "projectile_magicman_snake");

                    SnakeProjectile sp2 =
                            new SnakeProjectile(
                                    parentExt,
                                    this,
                                    pathSP2,
                                    7f,
                                    0.25f,
                                    "projectile_magicman_snake");

                    SnakeProjectile sp3 =
                            new SnakeProjectile(
                                    parentExt,
                                    this,
                                    pathSP3,
                                    7f,
                                    0.25f,
                                    "projectile_magicman_snake");

                    fireMMProjectile(sp, pathSP.getP1(), pathSP.getP2(), 9.5f);
                    fireMMProjectile(sp2, pathSP2.getP1(), pathSP2.getP2(), 9.5f);
                    fireMMProjectile(sp3, pathSP3.getP1(), pathSP3.getP2(), 9.5f);

                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "q", true, 0, gCooldown);
                int globalCD = gCooldown + 1000;
                scheduleTask(abilityRunnable(ability, spellData, 0, globalCD, dest), gCooldown);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    this.wUses++;
                    unveil();
                    if (this.wUses == 1) {

                        RoomHandler handler = parentExt.getRoomHandler(room.getName());
                        PathFinder pf = handler.getPathFinder();

                        effectManager.addState(
                                ActorState.STEALTH, id + "_mm_w_stealth", 0, W_STEALTH_DURATION);
                        effectManager.addState(
                                ActorState.INVISIBLE, id + "_mm_w_invis", 0d, W_STEALTH_DURATION);

                        wLocation = new Point2D.Double(this.location.getX(), this.location.getY());
                        Point2D endLocation =
                                Champion.getAbilityLine(this.wLocation, dest, 100f).getP2();

                        this.wDest = pf.getIntersectionPoint(location, endLocation);

                        magicManClone = new MagicManClone(wLocation);
                        handler.addCompanion(this.magicManClone);

                        teleport(pf.getNonObstaclePointOrIntersection(location, dest));

                        ExtensionCommands.actorAbilityResponse(
                                this.parentExt, this.player, "w", true, 1000, 0);
                    } else {
                        effectManager.setState(ActorState.INVISIBLE, false);
                        ExtensionCommands.actorAbilityResponse(
                                this.parentExt,
                                this.player,
                                "w",
                                true,
                                getReducedCooldown(cooldown),
                                gCooldown);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                int abilityDelay = this.wUses == 1 ? 1000 : getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                        abilityDelay);
                break;
            case 3:
                canCast[2] = false;
                unveil();
                stopMoving();

                String sound = "sfx_magicman_explode_roll";
                ExtensionCommands.playSound(parentExt, room, id, sound, location);

                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                PathFinder pf = rh.getPathFinder();

                Point2D initialEndPoint = pf.getIntersectionPoint(location, dest);

                eDashTime = (int) ((location.distance(initialEndPoint) / E_DASH_SPEED) * 1000);

                ExtensionCommands.actorAnimate(parentExt, room, id, "spell3", eDashTime, true);

                Runnable onInterrupt =
                        () -> {
                            handleECD();
                            playInterruptSoundAndIdle();
                        };

                DashContext ctx =
                        new DashContext.Builder(location, dest, (float) E_DASH_SPEED)
                                .canBeRedirected(true)
                                .triggerEndEffectOnRoot(true)
                                .onEnd(this::handleEEnd)
                                .onInterrupt(onInterrupt)
                                .build();

                startDash(ctx);

                int cd = getReducedCooldown(cooldown);
                ExtensionCommands.actorAbilityResponse(parentExt, player, "e", true, cd, gCooldown);
                break;
        }
    }

    private void handleECD() {
        int cooldown = ChampionData.getBaseAbilityCooldown(this, 3);
        Runnable enableECasting = () -> canCast[2] = true;
        int delay = getReducedCooldown(cooldown) - eDashTime;
        scheduleTask(enableECasting, delay);
    }

    private void unveil() {
        if (effectManager.hasState(ActorState.INVISIBLE)) {
            effectManager.setState(ActorState.INVISIBLE, false);
        }
    }

    private void handleCloneDeath() {
        this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this.magicManClone);
        this.magicManClone = null;
    }

    public void handleEEnd() {
        handleCloneDeath();
        handleECD();

        if (getHealth() > 0) {
            JsonNode spellData = parentExt.getAttackData("magicman", "spell3");
            ExtensionCommands.actorAnimate(parentExt, room, id, "spell3b", 500, false);
            ExtensionCommands.playSound(parentExt, room, id, "sfx_magicman_explode", location);

            String vo = "vo/vo_magicman_explosion";

            ExtensionCommands.playSound(parentExt, room, id, vo, location);

            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "magicman_explosion",
                    id + "_ultExplosion",
                    1500,
                    (float) location.getX(),
                    (float) location.getY(),
                    false,
                    team,
                    0f);

            RoomHandler handler = parentExt.getRoomHandler(room.getName());

            for (Actor a : Champion.getActorsInRadius(handler, location, E_RADIUS)) {
                if (isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                    String stat1 = "armor";
                    String stat2 = "spellResist";

                    a.getEffectManager()
                            .addEffect(
                                    a.getId() + "_magicman_ult_armor",
                                    stat1,
                                    E_ARMOR_PERCENT,
                                    ModifierType.MULTIPLICATIVE,
                                    ModifierIntent.DEBUFF,
                                    E_DEBUFF_DURATION);
                    a.getEffectManager()
                            .addEffect(
                                    a.getId() + "_magicman_ult_spellResist",
                                    stat2,
                                    E_SHIELDS_PERCENT,
                                    ModifierType.MULTIPLICATIVE,
                                    ModifierIntent.DEBUFF,
                                    E_DEBUFF_DURATION);

                    a.getEffectManager()
                            .addState(
                                    ActorState.SLOWED,
                                    id + "_mm_e_slow",
                                    E_SLOW_PERCENT,
                                    E_SLOW_DURATION);
                }

                if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                    double damage = getSpellDamage(spellData, false);
                    a.addToDamageQueue(MagicMan.this, damage, spellData, false);
                }
            }
        }
    }

    private MagicManAbilityRunnable abilityRunnable(
            int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
        return new MagicManAbilityRunnable(ability, spellData, cooldown, gCooldown, dest);
    }

    private class MagicManAbilityRunnable extends AbilityRunnable {

        public MagicManAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - Q_CAST_DELAY;
            scheduleTask(enableQCasting, delay);
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            if (wUses == 2) wUses = 0;
        }

        @Override
        protected void spellE() {}

        @Override
        protected void spellPassive() {}
    }

    private class SnakeProjectile extends Projectile {

        public SnakeProjectile(
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
            if (isNeitherStructureNorAlly(victim)) {
                victim.getEffectManager()
                        .addState(
                                ActorState.SILENCED, id + "_mm_q_silence", 0d, Q_SILENCE_DURATION);
            }

            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.owner.getRoom(),
                    this.id,
                    "magicman_snake_explosion",
                    this.id + "_explosion",
                    750,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    owner.getTeam(),
                    0f);

            int snakes = snakedActors.getOrDefault(victim, 0) + 1;
            snakedActors.put(victim, snakes);

            double damageModifier = 1;

            switch (snakes) {
                case 2:
                    damageModifier = 0.25;
                    break;
                case 3:
                    damageModifier = 0.0625;
                    break;
            }

            JsonNode spellData = parentExt.getAttackData(MagicMan.this.avatar, "spell1");
            double damage = getSpellDamage(spellData, true) * damageModifier;

            victim.addToDamageQueue(MagicMan.this, damage, spellData, false);
            destroy();
        }

        @Override
        public void destroy() {
            super.destroy();
        }
    }

    private class MagicManClone extends Actor {

        MagicManClone(Point2D location) {
            this.room = MagicMan.this.room;
            this.parentExt = MagicMan.this.parentExt;
            this.currentHealth = 99999;
            this.maxHealth = 99999;
            this.location = location;
            this.avatar = MagicMan.this.avatar + "_decoy";
            this.id = MagicMan.this + "_decoy";
            this.team = MagicMan.this.team;
            this.actorType = ActorType.COMPANION;

            this.stats = initializeStats();
            ExtensionCommands.createActor(parentExt, room, id, avatar, location, 0f, team);

            startMoveTo(MagicMan.this.wDest);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {}

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return false;
        }

        @Override
        public void attack(Actor a) {}

        @Override
        public void die(Actor a) {
            this.stopMoving();
            ExtensionCommands.destroyActor(parentExt, room, id);
            this.dead = true;
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "magicman_eat_it",
                    this.id + "_eatIt",
                    2000,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    this.team,
                    0f);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, "", "sfx_magicman_decoy", this.location);
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    MagicMan.this.id,
                    "vo/vo_magicman_decoy2",
                    MagicMan.this.location);

            String AVATAR = "magicman";

            JsonNode spellData = parentExt.getAttackData(AVATAR, "spell2");
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor actor : Champion.getActorsInRadius(handler, location, 2.5f)) {
                if (isNeitherTowerNorAlly(actor) && a.isNotLeaping()) {
                    double dmg = getSpellDamage(spellData, false);
                    actor.addToDamageQueue(MagicMan.this, dmg, spellData, false);
                }
            }
            ExtensionCommands.destroyActor(this.parentExt, this.room, this.id);
            MagicMan.this.handleCloneDeath();
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            if (this.dead) return;
            handleMovementUpdate();
        }

        @Override
        public void setTarget(Actor a) {}
    }

    private class MagicManPassive implements Runnable {

        Actor target;
        boolean crit;

        MagicManPassive(Actor target, boolean crit) {
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (crit) {
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(
                            parentExt, MagicMan.this, target, (int) damage, "basicAttack")
                    .run();
            if (this.target.getActorType() == ActorType.PLAYER) {
                effectManager.addEffect(
                        id + "_magic_man_passive_speed",
                        "speed",
                        PASSIVE_SPEED_PERCENT,
                        ModifierType.MULTIPLICATIVE,
                        ModifierIntent.BUFF,
                        PASSIVE_SPEED_DURATION);
                if (!passiveActivated) {
                    ExtensionCommands.addStatusIcon(
                            parentExt,
                            player,
                            "passive",
                            "magicman_spell_4_short_description",
                            "icon_magicman_passive",
                            0f);
                }
                passiveActivated = true;
                passiveIconStarted = System.currentTimeMillis();
            }
        }
    }
}

package xyz.openatbp.extension.game.champions;

import static xyz.openatbp.extension.game.effects.EffectManager.DEFAULT_KNOCKBACK_SPEED;

import java.awt.geom.Point2D;
import java.util.List;

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

public class Billy extends UserActor {
    private static final int Q_SELF_CRIPPLE_DURATION = 500;
    private static final float Q_OFFSET_DISTANCE = 1.5f;
    private static final float Q_SPELL_RANGE = 4.5f;
    public static final int Q_STUN_DURATION = 1500;
    public static final float Q_KNOCKBACK_DIST = 3.5f;

    private static final int W_ATTACKSPEED_DURATION = 5000;
    private static final float W_ATTACK_SPEED_PERCENT = 0.7f;
    private static final int W_SPEED_DURATION = 5000;
    private static final float W_SPEED_PERCENT = 0.5f;
    private static final int W_CRATER_OFFSET = 1;
    public static final float W_LEAP_SPEED = 14f;

    private static final int E_CAST_DELAY = 750;
    private static final int E_EMP_DURATION = 4500;
    private static final float E_MIN_DAMAGE_PERCENTAGE = 0.4f;
    private static final int E_TICK = 200;
    private static final float E_CENTER_RING_DMG = 1.1f;
    private static final float E_RADIUS = 2.25f;

    private int passiveUses = 0;
    private boolean jumpActive = false;
    private int wLeapDuration = 0;
    private long eStartTime = 0;
    private Point2D eLocation = null;

    public Billy(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);

        if (eLocation != null) {
            if (msRan % E_TICK == 0) {
                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                List<Actor> enemies =
                        Champion.getEnemyActorsInRadius(rh, team, eLocation, E_RADIUS);
                JsonNode spellData = this.parentExt.getAttackData(avatar, "spell3");
                float damage = getSpellDamage(spellData, false) / 5f;

                for (Actor a : enemies) {
                    if (a.getActorType() != ActorType.TOWER) {
                        float calculatedReduction = (float) (1 - (0.15 * (enemies.size() - 1)));
                        float dmgReduction = Math.max(E_MIN_DAMAGE_PERCENTAGE, calculatedReduction);
                        damage *= dmgReduction;

                        a.addToDamageQueue(this, damage, spellData, true);
                    }
                }
                ExtensionCommands.playSound(
                        parentExt, room, "", "sfx_billy_nothung_pulse", eLocation);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "billy_nothung_pulse",
                        id + "_ultPulse_" + Math.random(),
                        1000,
                        (float) eLocation.getX(),
                        (float) eLocation.getY(),
                        false,
                        team,
                        0f);
            }

            if (System.currentTimeMillis() - eStartTime >= E_EMP_DURATION) {
                eLocation = null;
            }
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (jumpActive) return false;
        return super.canUseAbility(ability);
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        if (this.passiveUses < 3) {
            if (this.passiveUses != 0)
                ExtensionCommands.removeStatusIcon(
                        this.parentExt, this.player, "p" + this.passiveUses);
            this.passiveUses++;
            if (this.passiveUses != 3) {
                ExtensionCommands.addStatusIcon(
                        this.parentExt,
                        this.player,
                        "p" + this.passiveUses,
                        "billy_spell_4_short_description",
                        "icon_billy_p" + this.passiveUses,
                        0f);
                if (this.passiveUses == 2)
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "billy_passive",
                            1000 * 60 * 15,
                            this.id + "_passive",
                            true,
                            "Bip001",
                            true,
                            false,
                            this.team);
            }
        }
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                try {
                    if (getHealth() > 0) {
                        this.stopMoving(Q_SELF_CRIPPLE_DURATION);
                        AbilityShape rect =
                                AbilityShape.createRectangle(
                                        location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);

                        RoomHandler rh = this.parentExt.getRoomHandler(this.room.getName());
                        List<Actor> nearbyEnemyActors =
                                Champion.getEnemyActorsInRadius(rh, team, location, Q_SPELL_RANGE);
                        if (!nearbyEnemyActors.isEmpty()) {
                            for (Actor a : nearbyEnemyActors) {
                                if (rect.contains(a.getLocation(), a.getCollisionRadius())) {
                                    if (isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                                        if (passiveUses == 3) {
                                            a.getEffectManager()
                                                    .addState(
                                                            ActorState.STUNNED,
                                                            id + "_billy_q_stun",
                                                            0d,
                                                            Q_STUN_DURATION);
                                        }
                                        a.handleKnockback(
                                                location,
                                                Q_KNOCKBACK_DIST,
                                                DEFAULT_KNOCKBACK_SPEED);
                                    }
                                    if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                                        double damage = getSpellDamage(spellData, true);
                                        a.addToDamageQueue(this, damage, spellData, false);
                                    }
                                }
                            }
                        }
                        if (this.passiveUses == 3) this.usePassiveAbility();

                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "vo/vo_billy_knock_back",
                                this.location);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 2:
                this.canCast[1] = false;
                this.jumpActive = true;

                RoomHandler rh = this.parentExt.getRoomHandler(this.room.getName());
                PathFinder pf = rh.getPathFinder();

                Point2D leapDest = pf.getNonObstaclePointOrIntersection(location, dest);

                wLeapDuration = (int) ((location.distance(leapDest) / W_LEAP_SPEED) * 1000);

                DashContext ctx =
                        new DashContext.Builder(location, leapDest, W_LEAP_SPEED)
                                .canBeRedirected(false)
                                .onEnd(wLeapEnd)
                                .isLeap(true)
                                .build();
                startDash(ctx);

                playSoundWithChance("vo/vo_billy_w", 50);

                ExtensionCommands.playSound(parentExt, room, id, "sfx_billy_jump", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "billy_dash_trail",
                        wLeapDuration,
                        id + "_dash",
                        true,
                        "Bip001",
                        true,
                        false,
                        team);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", wLeapDuration, false);
                if (passiveUses == 3) {
                    effectManager.addEffect(
                            this.id + "_billy_w_attack_speed",
                            "attackSpeed",
                            W_ATTACK_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            W_ATTACKSPEED_DURATION);
                    effectManager.addEffect(
                            this.id + "_billy_w_speed",
                            "speed",
                            W_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            W_SPEED_DURATION);

                    usePassiveAbility();
                    basicAttackReset();
                    ExtensionCommands.addStatusIcon(
                            parentExt,
                            player,
                            "finalpassive",
                            "billy_spell_4_short_description",
                            "icon_billy_passive",
                            W_ATTACKSPEED_DURATION);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "billy_crit_hands",
                            W_ATTACKSPEED_DURATION,
                            this.id + "_critHandsR",
                            true,
                            "Bip001 R Hand",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "billy_crit_hands",
                            W_ATTACKSPEED_DURATION,
                            this.id + "_critHandsL",
                            true,
                            "Bip001 L Hand",
                            true,
                            false,
                            this.team);
                }

                int globalCooldown = (int) (wLeapDuration * 1.5);

                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        globalCooldown);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.stopMoving(castDelay);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "vo/vo_billy_nothung", location);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "lemongrab_ground_aoe_target",
                            this.id + "_billyUltTarget",
                            1750,
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
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
        }
    }

    Runnable wLeapEnd =
            () -> {
                int cooldown = ChampionData.getBaseAbilityCooldown(this, 2);
                jumpActive = false;
                Runnable enableWCasting = () -> canCast[1] = true;
                int cd = getReducedCooldown(cooldown) - wLeapDuration;
                scheduleTask(enableWCasting, cd);

                if (getHealth() > 0) {
                    JsonNode spellData = parentExt.getAttackData(avatar, "spell2");
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_billy_ground_pound_temp", location);
                    ExtensionCommands.actorAnimate(parentExt, room, id, "spell2a", 500, false);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "billy_ground_pound",
                            id + "_qLand",
                            1500,
                            (float) location.getX(),
                            (float) location.getY() - W_CRATER_OFFSET,
                            false,
                            team,
                            0f);
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    for (Actor a : Champion.getActorsInRadius(handler, location, 2f)) {
                        if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                            a.addToDamageQueue(
                                    Billy.this, getSpellDamage(spellData, true), spellData, false);
                        }
                    }
                }
            };

    private void usePassiveAbility() {
        this.passiveUses = 0;
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_passive");
    }

    private BillyAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new BillyAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class BillyAbilityRunnable extends AbilityRunnable {

        public BillyAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {}

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);

            if (getHealth() > 0) {
                ExtensionCommands.playSound(parentExt, room, "", "sfx_billy_nothung_skyfall", dest);
                int duration = 1000;
                if (passiveUses == 3) {
                    eStartTime = System.currentTimeMillis();
                    eLocation = dest;
                    duration = E_EMP_DURATION;
                    usePassiveAbility();
                }
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "billy_nothung_skyfall",
                        id + "_ult",
                        duration,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "billy_nothung_pulse",
                        id + "_ultPulseNotEmpowered",
                        1000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);

                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, dest, E_RADIUS);

                for (Actor a : enemies) {
                    if (a.getActorType() != ActorType.TOWER) {
                        double distance = a.getLocation().distance(dest);
                        double damage = getSpellDamage(spellData, true);

                        if (distance <= 1) damage *= E_CENTER_RING_DMG;
                        a.addToDamageQueue(Billy.this, damage, spellData, false);
                    }
                }
            }
        }

        @Override
        protected void spellPassive() {}
    }
}

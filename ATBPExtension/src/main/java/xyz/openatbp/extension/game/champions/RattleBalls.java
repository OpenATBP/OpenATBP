package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class RattleBalls extends UserActor {
    private static final double PASSIVE_LIFE_STEAL_VALUE = 0.65d;
    private static final int PASSIVE_STACK_DURATION = 5000;
    private static final int Q_PARRY_DURATION = 1500;
    private static final int W_CAST_DELAY = 500;
    private static final int E_DURATION = 3500;
    private static final double E_SPEED_VALUE = 0.14d;
    private boolean passiveActive = false;
    private int passiveHits = 0;
    private long startPassiveStack = 0;
    private int qUse = 0;
    private boolean parryActive = false;
    private long parryCooldown = 0;
    private boolean ultActive = false;
    private long lastSoundTime = 0;
    private int qTime = 0;
    private int eCounter = 0;
    private long eStartTime;
    private boolean playFailSound = false;
    private static final float Q_SPELL_RANGE = 6f;
    private static final float Q_OFFSET_DISTANCE = 0.85f;
    private Path2D qThrustRectangle = null;

    public RattleBalls(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.playFailSound) {
            this.playFailSound = false;
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_rattleballs_counter_fail",
                    this.location);
        }
        if (this.passiveActive
                && System.currentTimeMillis() - this.startPassiveStack >= PASSIVE_STACK_DURATION) {
            this.endPassive();
        }
        if (this.parryActive
                && System.currentTimeMillis() - this.parryCooldown >= Q_PARRY_DURATION
                && !this.dead) {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(12000);
            scheduleTask(enableQCasting, delay);
            this.canCast[1] = true;
            this.canCast[2] = true;
            this.parryActive = false;
            this.qUse = 0;
            this.abilityEnded();
            String spinSfx =
                    (this.avatar.contains("spidotron"))
                            ? "sfx_rattleballs_luchador_counter_attack"
                            : "sfx_rattleballs_spin";
            String swordSpinFX =
                    (this.avatar.contains("spidotron"))
                            ? "rattleballs_luchador_sword_spin"
                            : "rattleballs_sword_spin";
            ExtensionCommands.playSound(this.parentExt, this.room, this.id, spinSfx, this.location);
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_rattleballs_rattle_balls_2",
                    this.location);
            ExtensionCommands.actorAnimate(
                    this.parentExt, this.room, this.id, "spell3a", 250, true);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    swordSpinFX,
                    250,
                    this.id + "_parrySpin",
                    true,
                    "Bip001 Footsteps",
                    false,
                    false,
                    this.team);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "rattleballs_counter_trail",
                    250,
                    this.id + "_trail",
                    true,
                    "Bip001 Prop1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt,
                    this.player,
                    "q",
                    true,
                    getReducedCooldown(getBaseQCooldown()),
                    250);
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> affectedActors = Champion.getActorsInRadius(handler, this.location, 2f);
            affectedActors =
                    affectedActors.stream()
                            .filter(this::isNonStructure)
                            .collect(Collectors.toList());
            for (Actor a : affectedActors) {
                if (this.isNonStructure(a)) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell1");
                    a.addToDamageQueue(this, getSpellDamage(spellData) + 25, spellData, false);
                }
            }
        }
        if (this.ultActive) {
            int soundCooldown = 450;
            if (System.currentTimeMillis() - lastSoundTime >= soundCooldown && !this.dead) {
                lastSoundTime = System.currentTimeMillis();
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_rattleballs_counter_stance",
                        this.location);
            }
            if (this.ultActive && this.dead
                    || this.ultActive && System.currentTimeMillis() - this.eStartTime >= E_DURATION
                    || this.hasInterrupingCC()) {
                this.endUlt();
                if (this.hasInterrupingCC()) {
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_skill_interrupted",
                            this.location);
                }
            }
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.location, 2f)) {
                if (this.isNonStructure(a)) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
                    a.addToDamageQueue(
                            this, (double) getSpellDamage(spellData) / 10d, spellData, true);
                }
            }
        }
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (this.parryActive && this.getAttackType(attackData) == AttackType.SPELL) {
            this.endCounterStance();
            this.playFailSound = true;
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt,
                    this.player,
                    "q",
                    true,
                    getReducedCooldown(getBaseQCooldown()),
                    0);
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, false);
        }
        if (this.parryActive && this.getAttackType(attackData) == AttackType.PHYSICAL) {
            this.handleLifeSteal();
            this.endCounterStance();
            JsonNode counterAttackData = counterAttackData();
            a.addToDamageQueue(
                    this, this.getPlayerStat("attackDamage") * 2, counterAttackData, false);
            String counterSFx =
                    (this.avatar.contains("spidotron"))
                            ? "sfx_rattleballs_luchador_counter_attack_crit"
                            : "sfx_rattleballs_counter_stance";
            String counterCritFx =
                    (this.avatar.contains("spidotron"))
                            ? "rattleballs_luchador_dash_hit"
                            : "rattleballs_dash_hit";
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, counterSFx, this.location);
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_rattleballs_rattle_balls_2",
                    this.location);
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "crit", 1000, true);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    counterCritFx,
                    1500,
                    this.id,
                    true,
                    "",
                    true,
                    false,
                    this.team);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt,
                    this.player,
                    "q",
                    true,
                    getReducedCooldown(getBaseQCooldown()),
                    0);
            return false;
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public boolean canAttack() {
        if (this.ultActive || this.parryActive) return false;
        return super.canAttack();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        this.playFailSound = false;
    }

    @Override
    public void handleLifeSteal() {
        if (this.passiveActive && passiveHits > 0) {
            double damage = this.getPlayerStat("attackDamage");
            double lifesteal = (this.getPlayerStat("lifeSteal") / 100) + PASSIVE_LIFE_STEAL_VALUE;
            if (lifesteal > 100) lifesteal = 100;
            this.changeHealth((int) (damage * lifesteal));
            ExtensionCommands.removeStatusIcon(
                    this.parentExt, this.player, "passive" + this.passiveHits);
            this.passiveHits--;
            if (this.passiveHits == 1) {
                ExtensionCommands.addStatusIcon(
                        this.parentExt,
                        this.player,
                        "passive1",
                        "rattleballs_spell_4_short_description",
                        "icon_rattleballs_p1",
                        PASSIVE_STACK_DURATION);
            }
            this.startPassiveStack = System.currentTimeMillis();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_passiveRegen");
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "rattleballs_regen",
                    2000,
                    this.id + "_passiveRegen",
                    true,
                    "Bip001 Spine1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "sfx_rattleballs_regen", this.location);
            if (this.passiveHits <= 0) {
                this.endPassive();
            }
        } else super.handleLifeSteal();
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
                this.canCast[1] = false;
                this.canCast[2] = false;
                if (this.qUse > 0) {
                    this.qThrustRectangle =
                            Champion.createRectangle(
                                    location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);
                }
                Point2D ogLocation = this.location;
                Point2D finalDashPoint = this.dash(dest, false, DASH_SPEED);
                double time = ogLocation.distance(finalDashPoint) / DASH_SPEED;
                this.qTime = (int) (time * 1000);
                int qTimeEffects = qTime * 5;
                if (this.qUse == 0) {
                    String counterPrefix =
                            (this.avatar.contains("spidotron"))
                                    ? "rattleballs_luchador_"
                                    : "rattleballs_";
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_rattleballs_counter_stance",
                            this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_rattleballs_dash",
                            this.location);
                    ExtensionCommands.actorAnimate(
                            parentExt, room, id, "spell1a", qTimeEffects, true);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            counterPrefix + "dash_trail",
                            qTimeEffects,
                            this.id + "_q1Trail",
                            true,
                            "Bip001",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            counterPrefix + "dash_dust",
                            qTimeEffects,
                            this.id + "_q1Dust",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    this.qUse++;
                } else {
                    this.qUse = 0;
                    this.parryActive = false;
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> enemiesInPolygon =
                            handler.getEnemiesInPolygon(this.team, qThrustRectangle);
                    if (!enemiesInPolygon.isEmpty()) {
                        for (Actor a : enemiesInPolygon) {
                            if (isNonStructure(a)) {
                                a.addToDamageQueue(
                                        this, getSpellDamage(spellData), spellData, false);
                            }
                        }
                    }
                    String dashPrefix =
                            (this.avatar.contains("spidotron"))
                                    ? "rattleballs_luchador_"
                                    : "rattleballs_";
                    if (this.avatar.contains("spidotron"))
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "sfx_rattleballs_luchador_counter_stance",
                                this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_rattleballs_dash",
                            this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_rattleballs_rattle_balls_2",
                            this.location);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell1c", qTimeEffects, false);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            dashPrefix + "dash_trail",
                            qTimeEffects,
                            this.id + "_q2Trail",
                            true,
                            "Bip001",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            dashPrefix + "dash_dust",
                            qTimeEffects,
                            this.id + "_q2Dust",
                            true,
                            "Bip001 Footsteps",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt,
                            this.player,
                            "q",
                            true,
                            getReducedCooldown(cooldown),
                            this.qTime + 250);
                }
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDashPoint),
                        qTime);
                break;
            case 2:
                this.canCast[1] = false;
                String pullSfx =
                        (this.avatar.contains("spidotron"))
                                ? "sfx_rattleballs_luchador_pull"
                                : "sfx_rattleballs_pull";
                ExtensionCommands.playSound(parentExt, room, id, pullSfx, location);
                this.stopMoving(castDelay);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_5",
                        this.id + "_wCircle",
                        castDelay,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        true,
                        this.team,
                        0f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 3:
                this.canCast[2] = false;
                this.eCounter++;
                this.eStartTime = System.currentTimeMillis();
                if (!this.ultActive) {
                    this.canCast[0] = false;
                    this.canCast[1] = false;
                    this.ultActive = true;
                    String ultPrefix =
                            (this.avatar.contains("spidotron"))
                                    ? "rattleballs_luchador_"
                                    : "rattleballs_";
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_" + ultPrefix + "spin_cycle",
                            this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_rattleballs_eggcelent_1",
                            this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_2",
                            E_DURATION,
                            this.id + "_ultRing",
                            true,
                            "",
                            false,
                            true,
                            this.team);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell3a", E_DURATION, true);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            ultPrefix + "sword_spin",
                            E_DURATION,
                            this.id + "_ultSpin",
                            true,
                            "",
                            false,
                            false,
                            this.team);
                    this.addEffect("speed", this.getStat("speed") * E_SPEED_VALUE, E_DURATION);
                } else {
                    this.endUlt();
                }
                int eDelay = this.eCounter == 1 ? 500 : getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), eDelay);
                break;
        }
    }

    private void endPassive() {
        this.passiveActive = false;
        ExtensionCommands.removeStatusIcon(
                this.parentExt, this.player, "passive" + this.passiveHits);
        this.passiveHits = 0;
    }

    private void abilityEnded() {
        if (this.passiveHits == 0) {
            this.startPassiveStack = System.currentTimeMillis();
            this.passiveActive = true;
            this.passiveHits = 2;
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "passive2",
                    "rattleballs_spell_4_short_description",
                    "icon_rattleballs_p2",
                    5000);
        }
    }

    private void endCounterStance() {
        this.parryActive = false;
        this.qUse = 0;
        if (!this.hasInterrupingCC()) this.canMove = true;
        this.canCast[1] = true;
        this.canCast[2] = true;
        this.abilityEnded();
    }

    private void endUlt() {
        this.ultActive = false;
        this.eCounter = 0;
        this.canCast[0] = true;
        this.canCast[1] = true;
        if (!this.dead) this.abilityEnded();
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSpin");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSparkles");
        int baseUltCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
        ExtensionCommands.actorAbilityResponse(
                this.parentExt, this.player, "e", true, getReducedCooldown(baseUltCooldown), 250);
        if (isStopped()) {
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, false);
        } else {
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 100, false);
        }
    }

    private JsonNode counterAttackData() {
        JsonNode attackData = this.parentExt.getAttackData(this.avatar, "spell1");
        ObjectNode counterAttackData = attackData.deepCopy();
        counterAttackData.remove("spellType");
        counterAttackData.put("attackType", "physical");
        counterAttackData.put("counterAttack", true);
        return counterAttackData;
    }

    private int getBaseQCooldown() {
        return ChampionData.getBaseAbilityCooldown(this, 1);
    }

    private RattleAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new RattleAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class RattleAbilityRunnable extends AbilityRunnable {

        public RattleAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            if (qUse == 1) {
                Runnable flipDelay =
                        () -> {
                            canCast[0] = true;
                            parryActive = true;
                            parryCooldown = System.currentTimeMillis();
                            stopMoving(Q_PARRY_DURATION);
                            ExtensionCommands.playSound(
                                    parentExt,
                                    room,
                                    id,
                                    "sfx_rattleballs_counter_stance",
                                    location);
                            ExtensionCommands.actorAnimate(
                                    parentExt, room, id, "spell1b", Q_PARRY_DURATION, true);
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    id,
                                    "rattleballs_counter_stance",
                                    Q_PARRY_DURATION,
                                    id + "_stance",
                                    true,
                                    "Bip001",
                                    true,
                                    false,
                                    team);
                        };
                scheduleTask(flipDelay, qTime);
            } else {
                abilityEnded();
                Runnable enableQCasting = () -> canCast[0] = true;
                int delay = getReducedCooldown(cooldown) - qTime;
                scheduleTask(enableQCasting, delay);
                canCast[1] = true;
                canCast[2] = true;
                qThrustRectangle = null;
            }
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            int delay = getReducedCooldown(cooldown) - W_CAST_DELAY;
            scheduleTask(enableWCasting, delay);
            abilityEnded();
            if (getHealth() > 0) {
                String pullFx =
                        (avatar.contains("spidotron"))
                                ? "rattleballs_luchador_pull"
                                : "rattleballs_pull";
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        pullFx,
                        id + "_pull",
                        1500,
                        (float) location.getX(),
                        (float) location.getY(),
                        false,
                        team,
                        0f);

                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, location, 5f)) {
                    if (isNonStructure(a)) {
                        a.handlePull(location, 1.2);
                        a.addToDamageQueue(
                                RattleBalls.this, getSpellDamage(spellData), spellData, false);
                    }
                }
            }
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }
}

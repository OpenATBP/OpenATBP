package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class RattleBalls extends UserActor {
    private boolean passiveActive = false;
    private int passiveHits = 0;
    private long passiveStart = 0;
    private long passiveCooldown = 0;
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
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        System.out.println("Ability used: " + ability);
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
                    for (Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()) {
                        if (a.getTeam() != this.team
                                && isNonStructure(a)
                                && this.qThrustRectangle.contains(a.getLocation())) {
                            a.addToDamageQueue(this, getSpellDamage(spellData), spellData);
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
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new RattleAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, finalDashPoint),
                                qTime,
                                TimeUnit.MILLISECONDS);
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
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new RattleAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.eCounter++;
                this.eStartTime = System.currentTimeMillis();
                if (!this.ultActive) {
                    this.canCast[0] = false;
                    this.canCast[1] = false;
                    this.canCast[2] = false;
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
                            3500,
                            this.id + "_ultRing",
                            true,
                            "",
                            false,
                            true,
                            this.team);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell3a", 3500, true);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            ultPrefix + "sword_spin",
                            3500,
                            this.id + "_ultSpin",
                            true,
                            "",
                            false,
                            false,
                            this.team);
                    this.addEffect("speed", this.getStat("speed") * 0.14d, 3500, null, "", true);
                } else {
                    this.canCast[0] = true;
                    this.canCast[1] = true;
                    this.canCast[2] = false;
                    this.ultActive = false;
                    ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
                    ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSpin");
                    ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSparkles");
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "idle", 1, false);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt,
                            this.player,
                            "e",
                            true,
                            getReducedCooldown(cooldown),
                            gCooldown);
                }
                int eDelay = this.eCounter < 2 ? 500 : gCooldown;
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new RattleAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                eDelay,
                                TimeUnit.MILLISECONDS);
                break;
        }
    }

    private void endPassive() {
        this.passiveActive = false;
        this.passiveCooldown = System.currentTimeMillis();
        if (this.passiveHits == 0) this.passiveHits++;
        ExtensionCommands.actorAbilityResponse(
                this.parentExt, this.player, "passive", true, 5000, 0);
        ExtensionCommands.removeStatusIcon(
                this.parentExt, this.player, "passive" + this.passiveHits);
    }

    private void abilityEnded() {
        if (!this.passiveActive && System.currentTimeMillis() - this.passiveCooldown >= 5000) {
            this.passiveStart = System.currentTimeMillis();
            this.passiveActive = true;
            this.passiveHits = 2;
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "passive2",
                    "rattleballs_spell_4_short_description",
                    "icon_rattleballs_p2",
                    3000);
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
        if (this.passiveActive && System.currentTimeMillis() - this.passiveStart >= 3000) {
            System.out.println("Passive ending update");
            this.endPassive();
        }
        if (this.parryActive
                && System.currentTimeMillis() - this.parryCooldown >= 1500
                && !this.dead) {
            this.canCast[0] = true;
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
                    this.parentExt, this.player, "q", true, getReducedCooldown(12000), 250);
            List<Actor> affectedActors =
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getId()), this.location, 2f);
            affectedActors =
                    affectedActors.stream()
                            .filter(this::isNonStructure)
                            .collect(Collectors.toList());
            for (Actor a : affectedActors) {
                if (this.isNonStructure(a)) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell1");
                    a.addToDamageQueue(this, getSpellDamage(spellData) + 25, spellData);
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
                    || this.ultActive && System.currentTimeMillis() - this.eStartTime >= 3500
                    || this.hasInterrupingCC()) {
                this.ultActive = false;
                this.eCounter = 0;
                this.canCast[0] = true;
                this.canCast[1] = true;
                if (!this.dead) this.abilityEnded();
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSpin");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSparkles");
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "e", true, getReducedCooldown(60000), 250);
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "idle", 100, false);
                if (this.hasInterrupingCC()) {
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_skill_interrupted",
                            this.location);
                }
            }
            for (Actor a :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getId()), this.location, 2f)) {
                if (this.isNonStructure(a)) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
                    a.addToDamageQueue(this, (double) getSpellDamage(spellData) / 10d, spellData);
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
                    this.parentExt, this.player, "q", true, getReducedCooldown(12000), 0);
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, false);
        }
        if (this.parryActive && this.getAttackType(attackData) == AttackType.PHYSICAL) {
            this.handleLifeSteal();
            this.endCounterStance();
            JsonNode counterAttackData = counterAttackData();
            a.addToDamageQueue(this, this.getPlayerStat("attackDamage") * 2, counterAttackData);
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
                    1000,
                    this.id,
                    true,
                    "",
                    true,
                    false,
                    this.team);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "q", true, getReducedCooldown(12000), 0);
            return false;
        }
        return super.damaged(a, damage, attackData);
    }

    private JsonNode counterAttackData() {
        JsonNode attackData = this.parentExt.getAttackData(this.avatar, "spell1");
        ObjectNode counterAttackData = attackData.deepCopy();
        counterAttackData.remove("spellType");
        counterAttackData.put("attackType", "physical");
        counterAttackData.put("counterAttack", true);
        return counterAttackData;
    }

    @Override
    public void handleLifeSteal() {
        if (this.passiveActive && passiveHits > 0) {
            this.changeHealth((int) (this.getPlayerStat("attackDamage") * 0.65d));
            this.passiveHits--;
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
                System.out.println("Passive ending here");
                this.endPassive();
            } else {
                ExtensionCommands.removeStatusIcon(
                        this.parentExt, this.player, "passive" + (this.passiveHits + 1));
                ExtensionCommands.addStatusIcon(
                        this.parentExt,
                        this.player,
                        "passive" + this.passiveHits,
                        "rattleballs_spell_4_short_description",
                        "icon_rattleballs_p" + this.passiveHits,
                        3000f - (System.currentTimeMillis() - this.passiveStart));
            }
        } else super.handleLifeSteal();
    }

    private class RattleAbilityHandler extends AbilityRunnable {

        public RattleAbilityHandler(
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
                            stopMoving(1500);
                            ExtensionCommands.playSound(
                                    parentExt,
                                    room,
                                    id,
                                    "sfx_rattleballs_counter_stance",
                                    location);
                            ExtensionCommands.actorAnimate(
                                    parentExt, room, id, "spell1b", 1500, true);
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    id,
                                    "rattleballs_counter_stance",
                                    1500,
                                    id + "_stance",
                                    true,
                                    "Bip001",
                                    true,
                                    false,
                                    team);
                        };
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(flipDelay, qTime, TimeUnit.MILLISECONDS);
            } else {
                abilityEnded();
                canCast[0] = true;
                canCast[1] = true;
                canCast[2] = true;
                qThrustRectangle = null;
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
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
            abilityEnded();
            for (Actor a :
                    Champion.getActorsInRadius(
                            parentExt.getRoomHandler(room.getId()), location, 5f)) {
                if (isNonStructure(a)) {
                    a.handlePull(location, 1.2);
                    a.addToDamageQueue(RattleBalls.this, getSpellDamage(spellData), spellData);
                }
            }
        }

        @Override
        protected void spellE() {
            if (eCounter == 1) {
                canCast[2] = true;
            } else {
                canCast[0] = true;
                canCast[1] = true;
                canCast[2] = true;
                ultActive = false;
                eCounter = 0;
                abilityEnded();
            }
        }

        @Override
        protected void spellPassive() {}
    }
}

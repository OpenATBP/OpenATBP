package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Marceline extends UserActor {
    private static final double PASSIVE_HP_REG_VALUE = 1.5d;
    private static final int PASSIVE_HP_REG_SOUND_DELAY = 3000;
    private static final int Q_ROOT_DURATION = 2000;
    private static final int Q_SLOW_DURATION = 1500;
    private static final double Q_SLOW_VALUE = 0.15d;
    private static final int W_DURATION = 3000;
    private static final double W_BEAST_SPEED_VALUE = 0.4d;
    private static final double W_VAMPIRE_SPEED_VALUE = 0.15d;
    private static final int E_CAST_DELAY = 750;
    private static final int E_ATTACKSPEED_DURATION = 3000;
    private static final int E_IMMUNITY_DURATION = 2000;
    private static final int E_CHARM_DURATION = 2000;
    private static final int E_FEAR_DURATION = 2000;
    private int passiveHits = 0;
    private boolean hpRegenActive = false;
    private Actor qVictim;
    private long qHit = -1;
    private long regenSound = 0;
    private boolean canDoUltAttack = true;
    private boolean vampireWActive = false;
    private boolean beastWActive = false;
    private long vampireWStartTime = 0;
    private long bestWStartTime = 0;
    private boolean eUsed = false;
    private boolean hadCCDuringECast = false;

    private enum Form {
        BEAST,
        VAMPIRE
    }

    private Form form = Form.VAMPIRE;

    public Marceline(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.vampireWActive
                && System.currentTimeMillis() - this.vampireWStartTime >= W_DURATION) {
            this.vampireWActive = false;
        }
        if (this.beastWActive && System.currentTimeMillis() - this.bestWStartTime >= W_DURATION) {
            this.beastWActive = false;
        }
        if (this.currentHealth < this.maxHealth
                && !this.hpRegenActive
                && this.form == Form.BEAST
                && !this.dead) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "marceline_vampiric_healing",
                    15 * 1000 * 60,
                    this.id + "_batRegen",
                    true,
                    "",
                    true,
                    false,
                    this.team);
            this.hpRegenActive = true;
        } else if (hpRegenActive && this.currentHealth == this.maxHealth
                || hpRegenActive && this.form == Form.BEAST && this.dead) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_batRegen");
            this.hpRegenActive = false;
        }

        if (this.vampireWActive) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.location, 2f)) {
                if (a.getTeam() != this.team
                        && a.getActorType() != ActorType.TOWER
                        && a.getActorType() != ActorType.BASE) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");
                    double damage = getSpellDamage(spellData, false) / 10d;
                    a.addToDamageQueue(this, damage, spellData, true);
                }
            }
        }

        if (this.qVictim != null && System.currentTimeMillis() - this.qHit >= 450) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell1");
            double damage = this.getSpellDamage(spellData, false) / 3d;
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.qVictim.getId(),
                    "sfx_marceline_blood_hit",
                    this.qVictim.getLocation());
            if (this.qVictim != null)
                this.qVictim.addToDamageQueue(
                        this, damage, spellData,
                        true); // TODO This is redundant for some reason qVictim is having a null
            // exception here
            this.qHit = System.currentTimeMillis();
        }
        if (this.form == Form.BEAST
                && this.currentHealth < this.maxHealth
                && System.currentTimeMillis() - regenSound >= PASSIVE_HP_REG_SOUND_DELAY) {
            regenSound = System.currentTimeMillis();
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "marceline_regen_loop", this.location);
        }
        checkForDisablingUltAttack();
    }

    @Override
    public void handleSwapFromPoly() {
        String bundle = this.form == Form.BEAST ? "marceline_bat" : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("healthRegen") && this.form == Form.BEAST) {
            return super.getPlayerStat(stat) * PASSIVE_HP_REG_VALUE;
        } else if (stat.equalsIgnoreCase("speed")) {
            if (this.beastWActive) return super.getPlayerStat(stat) * (1 + W_BEAST_SPEED_VALUE);
            else if (this.vampireWActive)
                return super.getPlayerStat(stat) * (1 + W_VAMPIRE_SPEED_VALUE);
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            MarcelineAttack marcelineAttack = new MarcelineAttack(a, handleAttack(a));
            scheduleTask(marcelineAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void handleLifeSteal() {
        double damage = this.getPlayerStat("attackDamage");
        double lifesteal = this.getPlayerStat("lifeSteal") / 100;
        if (this.passiveHits >= 3) {
            this.passiveHits = 0;
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "sfx_marceline_crit_fangs", this.location);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "marceline_crit_fangs",
                    1000,
                    this.id + "_passiveHit",
                    true,
                    "Bip001 Head",
                    false,
                    false,
                    this.team);
            lifesteal = 1d;
        }
        this.changeHealth((int) Math.round(damage * lifesteal));
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        this.vampireWActive = false;
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
                    this.stopMoving(castDelay);
                    Line2D abilityLine = Champion.getAbilityLine(this.location, dest, 7f);
                    String qVO = SkinData.getMarcelineQVO(avatar, form.toString());
                    String proj = "projectile_marceline_dot";
                    String proj2 = "projectile_marceline_root";
                    String projectile = form == Form.VAMPIRE ? proj : proj2;
                    ExtensionCommands.playSound(parentExt, room, this.id, qVO, this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            "",
                            "marceline_throw_projectile",
                            this.location);
                    this.fireProjectile(
                            new MarcelineProjectile(
                                    this.parentExt,
                                    this,
                                    abilityLine,
                                    8f,
                                    0.5f,
                                    projectile,
                                    this.form == Form.BEAST),
                            this.location,
                            dest,
                            7f);
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
                try {
                    String wVO = SkinData.getMarcelineWVO(avatar);
                    if (this.form == Form.BEAST) {
                        this.beastWActive = true;
                        this.bestWStartTime = System.currentTimeMillis();
                        attackCooldown = 0;
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "sfx_marceline_beast_crit_activate",
                                this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "marceline_beast_crit_hand",
                                W_DURATION,
                                this.id + "_beastHands",
                                true,
                                "Bip001 R Hand",
                                true,
                                false,
                                this.team);
                        // this.addEffect("speed", this.getStat("speed") * W_BEAST_SPEED_VALUE,
                        // W_DURATION);
                    } else {
                        this.vampireWActive = true;
                        this.vampireWStartTime = System.currentTimeMillis();
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, this.id, wVO, this.location);
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "sfx_marceline_blood_mist",
                                this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "marceline_vamp_mark",
                                W_DURATION,
                                this.id + "_wBats",
                                true,
                                "",
                                true,
                                false,
                                this.team);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "marceline_blood_mist",
                                W_DURATION,
                                this.id + "_mist",
                                true,
                                "",
                                true,
                                false,
                                this.team);
                        // this.addEffect("speed", this.getStat("speed") * W_VAMPIRE_SPEED_VALUE,
                        // W_DURATION);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
            case 3:
                this.canCast[2] = false;
                this.eUsed = true;
                this.stopMoving(castDelay);
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "e", true, getReducedCooldown(cooldown), gCooldown);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_marceline_spell_casting",
                        this.location);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "marceline_spell_casting",
                        castDelay,
                        this.id + "_transformCast",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_3",
                        castDelay,
                        this.id + "_transformRing",
                        true,
                        "",
                        false,
                        true,
                        this.team);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 4:
                break;
        }
    }

    private void checkForDisablingUltAttack() {
        if (eUsed && !hadCCDuringECast && hasInterrupingCC()) {
            canDoUltAttack = false;
            hadCCDuringECast = true;
            // disable ult attack if player was cc'd even for a brief moment during cast
        }
    }

    private class MarcelineAttack implements Runnable {

        Actor target;
        boolean crit;

        MarcelineAttack(Actor t, boolean crit) {
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (form == Form.VAMPIRE && crit) damage *= 1.25;
            if (crit
                    && grassSwordCooldown
                            >= ChampionData.getCustomJunkStat(Marceline.this, "junk_1_grass_sword"))
                damage *= 1.25d;
            if (beastWActive && form == Form.BEAST) {
                if (crit) damage *= 2.5;
                else damage *= 1.25;
                double lifesteal = 1d;
                if (this.target != null
                        && isNonStructure(this.target)
                        && !getState(ActorState.BLINDED))
                    changeHealth((int) Math.round(damage * lifesteal));
            }
            if (form == Form.VAMPIRE
                    && isNonStructure(this.target)
                    && !getState(ActorState.BLINDED)) {
                passiveHits++;
            }
            if (beastWActive && form == Form.BEAST) {
                beastWActive = false;
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_marceline_beast_crit_hit", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "marceline_beast_crit_hit",
                        1000,
                        target.getId() + "_marcelineHit",
                        false,
                        "targetNode",
                        true,
                        false,
                        target.getTeam());
                ExtensionCommands.removeFx(parentExt, room, id + "_beastHands");
            }
            new Champion.DelayedAttack(
                            parentExt, Marceline.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }

    private MarcelineAbilityRunnable abilityRunnable(
            int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
        return new MarcelineAbilityRunnable(ability, spellData, cooldown, gCooldown, dest);
    }

    private class MarcelineAbilityRunnable extends AbilityRunnable {
        public MarcelineAbilityRunnable(
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
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);
            if (beastWActive) {
                beastWActive = false;
                ExtensionCommands.removeFx(parentExt, room, id + "_beastHands");
            }

            if (getHealth() > 0) {
                boolean canSwapAsset = !getState(ActorState.POLYMORPH);

                if (form == Form.BEAST) {
                    form = Form.VAMPIRE;
                    attackCooldown = 0d;
                    if (canSwapAsset) {
                        ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
                    }
                    if (hpRegenActive) {
                        ExtensionCommands.removeFx(parentExt, room, id + "_batRegen");
                        hpRegenActive = false;
                    }
                    double delta = (getPlayerStat("attackSpeed") / 2) * -1;
                    if (getPlayerStat("attackSpeed") - delta > 500) {
                        Marceline.this.addEffect("attackSpeed", delta, E_ATTACKSPEED_DURATION);
                    } else {
                        Marceline.this.addEffect(
                                "attackSpeed",
                                500 - getPlayerStat("attackSpeed"),
                                E_ATTACKSPEED_DURATION);
                    }
                } else {
                    form = Form.BEAST;
                    passiveHits = 0;
                    if (canSwapAsset) {
                        ExtensionCommands.swapActorAsset(parentExt, room, id, "marceline_bat");
                    }
                }
                updateStatMenu("healthRegen");

                if (canDoUltAttack && !dead) {
                    Marceline.this.addState(ActorState.IMMUNITY, 0d, E_IMMUNITY_DURATION);
                    setState(ActorState.CLEANSED, true);
                    Marceline.this.cleanseEffects();
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "statusEffect_immunity",
                            E_IMMUNITY_DURATION,
                            id + "_ultImmunity",
                            true,
                            "displayBar",
                            false,
                            false,
                            team);
                    if (form == Form.BEAST) { // she gets immunity only if ult is not interrupted
                        String eToBeastVO = SkinData.getMarcelineEBeastVO(avatar);
                        ExtensionCommands.playSound(parentExt, room, id, eToBeastVO, location);

                    } else {
                        String eToVampireVO = SkinData.getMarcelineEVampireVO(avatar);
                        ExtensionCommands.playSound(parentExt, room, id, eToVampireVO, location);
                    }

                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    for (Actor a :
                            Champion.getActorsInRadius(handler, Marceline.this.location, 3)) {
                        if (isNonStructure(a)) {
                            double damage = getSpellDamage(spellData, true);
                            a.addToDamageQueue(Marceline.this, damage, spellData, false);
                            if (form == Form.VAMPIRE) {
                                a.handleCharm(Marceline.this, E_CHARM_DURATION);
                            } else {
                                a.handleFear(Marceline.this.location, E_FEAR_DURATION);
                            }
                        }
                    }
                } else {
                    canMove = true;
                    hadCCDuringECast = false;
                    canDoUltAttack = true;
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_skill_interrupted", location);
                }
            }
            eUsed = false;
        }

        @Override
        protected void spellPassive() {}
    }

    private class MarcelineProjectile extends Projectile {

        boolean transformed;

        public MarcelineProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id,
                boolean transformed) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.transformed = transformed;
        }

        @Override
        protected void hit(Actor victim) {
            if (transformed) {
                victim.addState(ActorState.ROOTED, 0d, Q_ROOT_DURATION);
            } else {
                qVictim = victim;
                qHit = System.currentTimeMillis();
                Runnable endVictim =
                        () -> {
                            qVictim = null;
                            qHit = -1;
                        };
                scheduleTask(endVictim, 1500);
                victim.addState(ActorState.SLOWED, Q_SLOW_VALUE, Q_SLOW_DURATION);
            }
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            victim.addToDamageQueue(this.owner, getSpellDamage(spellData, true), spellData, false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_marceline_blood_hit", this.location);
            this.destroy();
        }
    }
}

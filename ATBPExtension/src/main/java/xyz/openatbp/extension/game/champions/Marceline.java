package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public class Marceline extends UserActor {
    private static final double PASSIVE_HP_REG_VALUE = 1.5d;
    private static final int PASSIVE_HP_REG_SOUND_DELAY = 3000;
    private static final int Q_ROOT_DURATION = 2000;
    private static final int Q_SLOW_DURATION = 1500;
    private static final double Q_SLOW_PERCENT = 0.15d;
    private static final int W_DURATION = 3000;
    private static final double W_BEAST_SPEED_PERCENT = 0.4d;
    private static final double W_VAMPIRE_SPEED_PERCENT = 0.15d;
    private static final String W_BEAST_ICON = "beast_w";
    private static final String W_VAMP_ICON = "vamp_w";
    private static final int E_CAST_DELAY = 750;
    private static final int E_ATTACK_SPEED_DURATION = 3000;
    private static final int E_IMMUNITY_DURATION = 2000;
    private static final int E_CHARM_DURATION = 2000;
    private static final int E_FEAR_DURATION = 2000;
    public static final int E_AS_PERCENT = 1;
    public static final int ULT_RADIUS = 3;

    private final String BEAST_REGEN_BUFF_ID;
    private final String W_BEAST_SPEED_BUFF_ID;
    private final String BEAST_REGEN_FX_ID;

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
        this.hasCustomSwapFromPoly = true;
        BEAST_REGEN_BUFF_ID = id + "_marcy_beast_regen";
        W_BEAST_SPEED_BUFF_ID = id + "_marcy_beast_w_speed";
        BEAST_REGEN_FX_ID = id + "_bat_regen";
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.vampireWActive && System.currentTimeMillis() - vampireWStartTime >= W_DURATION) {
            this.vampireWActive = false;
            ExtensionCommands.removeStatusIcon(parentExt, player, W_VAMP_ICON);
        }
        if (beastWActive && System.currentTimeMillis() - bestWStartTime >= W_DURATION) {
            beastWActive = false;
            ExtensionCommands.removeStatusIcon(parentExt, player, W_BEAST_ICON);
        }

        if (vampireWActive) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, location, 2f)) {
                if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                    JsonNode spellData = parentExt.getAttackData(avatar, "spell2");
                    double damage = getSpellDamage(spellData, false) / 10d;
                    a.addToDamageQueue(this, damage, spellData, true);
                }
            }
        }
        if (form == Form.BEAST
                && currentHealth < maxHealth
                && System.currentTimeMillis() - regenSound >= PASSIVE_HP_REG_SOUND_DELAY) {
            regenSound = System.currentTimeMillis();
            ExtensionCommands.playSound(parentExt, player, id, "marceline_regen_loop", location);
        }
        checkForDisablingUltAttack();

        if (qVictim != null && System.currentTimeMillis() - qHit >= 450) {
            Actor currentVictim = qVictim;
            if (currentVictim == null) return;

            if (currentVictim.getHealth() > 0) {
                JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
                double damage = getSpellDamage(spellData, false) / 3d;
                currentVictim.addToDamageQueue(this, damage, spellData, true);

                ExtensionCommands.playSound(
                        parentExt,
                        room,
                        currentVictim.getId(),
                        "sfx_marceline_blood_hit",
                        currentVictim.getLocation());

                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        currentVictim.getId(),
                        "fx_hit_blksmoke",
                        1000,
                        String.valueOf(Math.random()),
                        false,
                        "",
                        true,
                        false,
                        team);
            }
            qHit = System.currentTimeMillis();
        }
    }

    @Override
    public void customSwapFromPoly() {
        String bundle = this.form == Form.BEAST ? "marceline_bat" : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
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
    protected boolean handleAttack(Actor a) {
        if (this.attackCooldown == 0) {

            boolean critAnimation;
            if (form == Form.BEAST && beastWActive) {
                critAnimation = true;
            } else {
                double critChance = this.getPlayerStat("criticalChance") / 100d;
                double random = Math.random();
                critAnimation = random < critChance;
            }

            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    critAnimation,
                    true);

            this.attackCooldown = this.getPlayerStat("attackSpeed");
            this.preventStealth();
            this.setLastAuto();
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            return critAnimation;
        }
        return false;
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
        if (vampireWActive) {
            vampireWActive = false;
            ExtensionCommands.removeStatusIcon(parentExt, player, W_VAMP_ICON);
        }

        if (beastWActive) {
            beastWActive = false;
            ExtensionCommands.removeStatusIcon(parentExt, player, W_BEAST_ICON);
        }

        if (form == Form.BEAST) {
            removeBeastPassiveFX();
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
                this.canCast[0] = false;
                try {
                    this.stopMoving(castDelay);
                    Line2D abilityLine = Champion.createLineTowards(this.location, dest, 7f);
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
                    addWStatusIcon(form);

                    if (this.form == Form.BEAST) {
                        this.beastWActive = true;
                        this.bestWStartTime = System.currentTimeMillis();
                        basicAttackReset();

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
                                this.id + "_beastRHand",
                                true,
                                "Bip001 R Hand",
                                true,
                                false,
                                this.team);

                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "marceline_beast_crit_hand",
                                W_DURATION,
                                this.id + "_beastLHand",
                                true,
                                "Bip001 L Hand",
                                true,
                                false,
                                this.team);

                        effectManager.addEffect(
                                W_BEAST_SPEED_BUFF_ID,
                                "speed",
                                W_BEAST_SPEED_PERCENT,
                                ModifierType.MULTIPLICATIVE,
                                ModifierIntent.BUFF,
                                W_DURATION);

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

                        effectManager.addEffect(
                                id + "_marcy_human_w_speed",
                                "speed",
                                W_VAMPIRE_SPEED_PERCENT,
                                ModifierType.MULTIPLICATIVE,
                                ModifierIntent.BUFF,
                                W_DURATION);
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

    private void removeBeastPassiveFX() {
        ExtensionCommands.removeFx(parentExt, room, BEAST_REGEN_FX_ID);
    }

    private void addWStatusIcon(Form currentForm) {
        String iconDesc = "marceline_spell_2_description";
        String monoName = "icon_marceline_s2";

        String iconName = currentForm == Form.BEAST ? W_BEAST_ICON : W_VAMP_ICON;

        ExtensionCommands.addStatusIcon(
                parentExt, player, iconName, iconDesc, monoName, W_DURATION);
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

            if (enhanceCrit()) {
                damage *= 1.25d;
            }

            if (beastWActive && form == Form.BEAST) {
                damage *= 2;
                double lifesteal = 1d;

                if (target != null
                        && isNeitherStructureNorAlly(target)
                        && !effectManager.hasState(ActorState.BLINDED)) {
                    changeHealth((int) Math.round(damage * lifesteal));
                }

                ExtensionCommands.removeStatusIcon(parentExt, player, W_BEAST_ICON);

            } else if (form == Form.BEAST && crit) {
                damage *= 1.25d;
            }

            if (form == Form.VAMPIRE
                    && this.target != null
                    && isNeitherStructureNorAlly(this.target)
                    && !effectManager.hasState(ActorState.BLINDED)) {
                passiveHits++;
            }
            if (beastWActive && form == Form.BEAST) {
                effectManager.removeAllEffectsById(W_BEAST_SPEED_BUFF_ID);
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
                removeHandsFX();
            }
            new Champion.DelayedAttack(
                            parentExt, Marceline.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }

    private void removeHandsFX() {
        ExtensionCommands.removeFx(parentExt, room, id + "_beastRHand");
        ExtensionCommands.removeFx(parentExt, room, id + "_beastLHand");
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
                removeHandsFX();
            }

            if (getHealth() > 0) {
                boolean canSwapAsset = !effectManager.hasState(ActorState.POLYMORPH);

                if (form == Form.BEAST) {
                    form = Form.VAMPIRE;
                    removeBeastPassiveFX();
                    if (canSwapAsset) {
                        ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
                    }

                    effectManager.removeAllEffectsById(BEAST_REGEN_BUFF_ID);
                    effectManager.addEffect(
                            Marceline.this.id + "_marcy_e_as_buff",
                            "attackSpeed",
                            E_AS_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            E_ATTACK_SPEED_DURATION);

                } else {
                    form = Form.BEAST;
                    passiveHits = 0;
                    if (canSwapAsset) {
                        ExtensionCommands.swapActorAsset(parentExt, room, id, "marceline_bat");
                    }

                    effectManager.addEffect(
                            BEAST_REGEN_BUFF_ID,
                            "healthRegen",
                            PASSIVE_HP_REG_VALUE,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            1000 * 60 * 15,
                            "marceline_vampiric_healing",
                            BEAST_REGEN_FX_ID,
                            "");
                }

                if (canDoUltAttack && !dead) {
                    Marceline.this.effectManager.addState(
                            ActorState.IMMUNITY, id + "_marcy_e_immunity", 0d, E_IMMUNITY_DURATION);
                    effectManager.setState(ActorState.CLEANSED, true);
                    // setState(ActorState.CLEANSED, true);
                    Marceline.this.effectManager.cleanseDebuffs();
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
                    Point2D loc = Marceline.this.location;
                    for (Actor a : Champion.getActorsInRadius(handler, loc, ULT_RADIUS)) {
                        if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {

                            double damage = getSpellDamage(spellData, true);
                            a.addToDamageQueue(Marceline.this, damage, spellData, false);

                            if (a.getActorType() != ActorType.BASE) {
                                if (form == Form.VAMPIRE) {
                                    a.setCharmer(Marceline.this);
                                    a.getEffectManager()
                                            .addState(
                                                    ActorState.CHARMED,
                                                    id + "_marcy_e_charm",
                                                    0d,
                                                    E_CHARM_DURATION);
                                } else {
                                    a.setFearer(Marceline.this);
                                    a.getEffectManager()
                                            .addState(
                                                    ActorState.FEARED,
                                                    id + "_marcy_e_fear",
                                                    0d,
                                                    E_FEAR_DURATION);
                                }
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
                float offsetDistance,
                String id,
                boolean transformed) {
            super(parentExt, owner, path, speed, offsetDistance, offsetDistance, id);
            this.transformed = transformed;
        }

        @Override
        protected void hit(Actor victim) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    victim.getId(),
                    "fx_hit_blksmoke",
                    1000,
                    this.id + "initialQHit",
                    false,
                    "",
                    true,
                    false,
                    team);
            if (transformed) {
                if (isNeitherStructureNorAlly(victim)) {
                    victim.getEffectManager()
                            .addState(ActorState.ROOTED, id + "_marcy_q_root", 0d, Q_ROOT_DURATION);
                }
            } else {
                qVictim = victim;
                qHit = System.currentTimeMillis();
                Runnable endVictim =
                        () -> {
                            qVictim = null;
                            qHit = -1;
                        };
                scheduleTask(endVictim, 1500);

                if (isNeitherStructureNorAlly(victim)) {
                    victim.getEffectManager()
                            .addState(
                                    ActorState.SLOWED,
                                    id + "_marcy_q_slow",
                                    Q_SLOW_PERCENT,
                                    Q_SLOW_DURATION);
                }
            }
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            victim.addToDamageQueue(this.owner, getSpellDamage(spellData, true), spellData, false);

            String sound = "sfx_marceline_blood_hit";

            ExtensionCommands.playSound(parentExt, room, "", sound, this.location);
            destroy();
        }
    }
}

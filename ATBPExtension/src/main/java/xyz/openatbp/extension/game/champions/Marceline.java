package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Marceline extends UserActor {

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

    private enum Form {
        BEAST,
        VAMPIRE
    }

    private Form form = Form.VAMPIRE;

    public Marceline(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void handleSwapFromPoly() {
        String bundle = this.form == Form.BEAST ? "marceline_bat" : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("healthRegen") && this.form == Form.BEAST) {
            return super.getPlayerStat(stat) * 1.5d;
        }
        return super.getPlayerStat(stat);
    }

    public void update(int msRan) {
        super.update(msRan);
        if (this.vampireWActive && System.currentTimeMillis() - this.vampireWStartTime >= 4500) {
            this.vampireWActive = false;
        }
        if (this.beastWActive && System.currentTimeMillis() - this.bestWStartTime >= 4500) {
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
            for (Actor a :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.location,
                            2f)) {
                if (a.getTeam() != this.team
                        && a.getActorType() != ActorType.TOWER
                        && a.getActorType() != ActorType.BASE) {
                    JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");
                    double damage = getSpellDamage(spellData) / 10d;
                    a.addToDamageQueue(this, damage, spellData, true);
                }
            }
        }

        if (this.qVictim != null && System.currentTimeMillis() - this.qHit >= 450) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell1");
            double damage = this.getSpellDamage(spellData) / 3d;
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.qVictim.getId(),
                    "sfx_marceline_blood_hit",
                    this.qVictim.getLocation());
            this.qVictim.addToDamageQueue(this, damage, spellData, true);
            this.qHit = System.currentTimeMillis();
        }
        if (this.form == Form.BEAST
                && this.currentHealth < this.maxHealth
                && System.currentTimeMillis() - regenSound >= 3000) {
            regenSound = System.currentTimeMillis();
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "marceline_regen_loop", this.location);
        }
        if (this.eUsed) this.canDoUltAttack = !this.hasInterrupingCC() && this.getHealth() > 0;
    }

    @Override
    public void attack(Actor a) {
        this.applyStopMovingDuringAttack();
        if (this.attackCooldown == 0) {
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            new MarcelineAttack(a, this.handleAttack(a)),
                            500,
                            TimeUnit.MILLISECONDS);
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
            case 1: // Q
                this.stopMoving(castDelay);
                this.canCast[0] = false;
                Line2D abilityLine = Champion.getAbilityLine(this.location, dest, 7f);
                String projectileId = "projectile_marceline_dot";
                String humanPrefix =
                        (this.avatar.contains("marshall"))
                                ? "marshall_lee_"
                                : (this.avatar.contains("young"))
                                        ? "marceline_young_"
                                        : "marceline_";
                String beastPrefix =
                        (this.avatar.contains("marshall")) ? "marshall_lee_" : "marceline_";
                if (this.form == Form.BEAST) {
                    projectileId = "projectile_marceline_root";
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            this.id,
                            "vo/vo_" + beastPrefix + "projectile_beast",
                            this.location);
                } else {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            this.id,
                            "vo/vo_" + humanPrefix + "projectile_human",
                            this.location);
                }
                ExtensionCommands.playSound(
                        this.parentExt, this.room, "", "marceline_throw_projectile", this.location);
                this.fireProjectile(
                        new MarcelineProjectile(
                                this.parentExt,
                                this,
                                abilityLine,
                                8f,
                                0.5f,
                                projectileId,
                                this.form == Form.BEAST),
                        this.location,
                        dest,
                        7f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new MarcelineAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 2: // W
                this.canCast[1] = false;
                String bloodMistVo =
                        (this.avatar.contains("marshall"))
                                ? "vo/vo_marshall_lee_blood_mist"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_marceline_young_blood_mist"
                                        : "vo/vo_marceline_blood_mist";
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
                            4500,
                            this.id + "_beastHands",
                            true,
                            "Bip001 R Hand",
                            true,
                            false,
                            this.team);
                    this.addEffect("speed", this.getStat("speed") * 0.4d, 4500);
                } else {
                    this.vampireWActive = true;
                    this.vampireWStartTime = System.currentTimeMillis();
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, bloodMistVo, this.location);
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
                            4500,
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
                            4500,
                            this.id + "_mist",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    this.addEffect("speed", this.getStat("speed") * 0.3d, 4500);
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new MarcelineAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 3: // E
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
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new MarcelineAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
            case 4: // Passive
                break;
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
            if (form == Form.VAMPIRE && crit) damage *= 2;
            if (beastWActive && form == Form.BEAST) {
                if (crit) damage *= 4;
                else damage *= 2;
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

    private class MarcelineAbilityHandler extends AbilityRunnable {
        public MarcelineAbilityHandler(
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
            int E_CAST_DELAY = 750;
            Runnable enableECasting = () -> canCast[2] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableECasting,
                            getReducedCooldown(cooldown) - E_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
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
                        Marceline.this.addEffect("attackSpeed", delta, 3000);
                    } else {
                        Marceline.this.addEffect(
                                "attackSpeed", 500 - getPlayerStat("attackSpeed"), 3000);
                    }
                } else {
                    form = Form.BEAST;
                    passiveHits = 0;
                    if (canSwapAsset) {
                        ExtensionCommands.swapActorAsset(parentExt, room, id, "marceline_bat");
                    }
                }
                updateStatMenu("healthRegen");

                if (canDoUltAttack) {
                    if (form == Form.BEAST) { // she gets immunity only if ult is not interrupted
                        Marceline.this.addState(ActorState.IMMUNITY, 0d, 2000);
                        setState(ActorState.CLEANSED, true);
                        Marceline.this.cleanseEffects();
                        String morphBeastVo =
                                (avatar.contains("marshall"))
                                        ? "vo/marshall_lee_morph_to_beast"
                                        : "marceline_morph_to_beast";
                        ExtensionCommands.playSound(parentExt, room, id, morphBeastVo, location);
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                id,
                                "statusEffect_immunity",
                                2000,
                                id + "_ultImmunity",
                                true,
                                "displayBar",
                                false,
                                false,
                                team);
                    } else {
                        String morphHumanVo =
                                (avatar.contains("marshall"))
                                        ? "vo/marshall_lee_morph_to_human"
                                        : "marceline_morph_to_human";
                        ExtensionCommands.playSound(parentExt, room, id, morphHumanVo, location);
                    }

                    for (Actor a :
                            Champion.getActorsInRadius(
                                    parentExt.getRoomHandler(room.getName()), dest, 3)) {
                        if (a.getTeam() != team && isNonStructure(a)) {
                            double damage = getSpellDamage(spellData);
                            a.addToDamageQueue(Marceline.this, damage, spellData, false);
                            if (!a.getId().contains("turret") || !a.getId().contains("decoy")) {
                                if (form == Form.VAMPIRE) {
                                    a.handleCharm(Marceline.this, 2000);
                                } else {
                                    a.handleFear(Marceline.this.location, 2000);
                                }
                            }
                        }
                    }
                } else {
                    canMove = true;
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
                victim.addState(ActorState.ROOTED, 0d, 3000);
            } else {
                qVictim = victim;
                qHit = System.currentTimeMillis();
                Runnable endVictim =
                        () -> {
                            qVictim = null;
                            qHit = -1;
                        };
                parentExt.getTaskScheduler().schedule(endVictim, 1500, TimeUnit.MILLISECONDS);
                victim.addState(ActorState.SLOWED, 0.15d, 1500);
            }
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            victim.addToDamageQueue(this.owner, getSpellDamage(spellData), spellData, false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_marceline_blood_hit", this.location);
            this.destroy();
        }
    }
}

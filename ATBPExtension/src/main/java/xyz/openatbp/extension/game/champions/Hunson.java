package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Hunson extends UserActor {

    private Map<Actor, Integer> qVictims;
    private boolean qActivated = false;
    private int qUses = 0;
    private boolean ultActivated = false;
    private boolean passiveActivated = false;
    private long qStartTime = 0;
    private long ultStart = 0;

    public Hunson(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultActivated) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            for (Actor a :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getId()), this.location, 4f)) {
                if (this.isNonStructure(a)) {
                    a.addToDamageQueue(this, this.getSpellDamage(spellData) / 10d, spellData);
                }
            }
            if (System.currentTimeMillis() - this.ultStart >= 3500) {
                this.ultActivated = false;
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "idle", 500, false);
            }
            if (this.ultActivated && this.hasInterrupingCC()) {
                this.interruptE();
                this.ultActivated = false;
            }
            if (this.currentHealth <= 0) {
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultHead");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSuck");
                this.ultActivated = false;
            }
        }
        if (this.qActivated
                && System.currentTimeMillis() - this.qStartTime >= 6000
                && this.qUses > 0) {
            this.qUses = 0;
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "q", true, getReducedCooldown(12000), 250);
            this.qActivated = false;
        }
    }

    @Override
    public boolean canAttack() {
        if (this.ultActivated) return false;
        return super.canAttack();
    }

    @Override
    public boolean canMove() {
        if (this.ultActivated) return false;
        return super.canMove();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultActivated) this.endUlt();
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if (this.hasStatusEffect(a)
                && a.getHealth() - this.getPlayerStat("attackDamage") > -5
                && !this.passiveActivated) {
            this.passiveActivated = true;
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "hunson_hands_passive",
                    5000,
                    this.id + "_passiveR",
                    true,
                    "Bip01 R Hand",
                    true,
                    false,
                    this.team);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "hunson_hands_passive",
                    5000,
                    this.id + "_passiveL",
                    true,
                    "Bip01 L Hand",
                    true,
                    false,
                    this.team);
            Champion.handleStatusIcon(
                    this.parentExt,
                    this,
                    "icon_hunson_passive",
                    "hunson_spell_4_short_description",
                    5000);
            this.addEffect(
                    "attackSpeed", this.getStat("attackSpeed") * -0.4d, 5000, null, "", false);
            this.addEffect("speed", 0.8d, 5000, null, "", false);
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            new HunsonAbilityHandler(4, null, 0, 0, null),
                            5000,
                            TimeUnit.MILLISECONDS);
        }
    }

    private boolean hasStatusEffect(Actor a) {
        ActorState[] states = ActorState.values();
        for (ActorState s : states) {
            if (a.getState(s)
                    && s != ActorState.BRUSH
                    && s != ActorState.TRANSFORMED
                    && s != ActorState.REVEALED) return true;
        }
        return false;
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
                this.qStartTime = System.currentTimeMillis();
                this.canCast[0] = false;
                this.stopMoving();
                if (!this.qActivated) {
                    this.qActivated = true;
                    this.qVictims = new HashMap<>(3);
                    this.qUses = 3;
                }
                this.qUses--;
                int abilityCooldown = this.qUses > 0 ? 850 : getReducedCooldown(cooldown);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "q", true, abilityCooldown, gCooldown);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_hunson_scream2", this.location);
                Line2D spellLine = Champion.getAbilityLine(this.location, dest, 8f);
                this.fireProjectile(
                        new HudsonProjectile(
                                this.parentExt,
                                this,
                                spellLine,
                                8f,
                                0.5f,
                                id + "projectile_hunson_pull"),
                        "projectile_hunson_pull",
                        this.location,
                        dest,
                        8f);
                if (this.qUses == 0) {
                    this.qActivated = false;
                }

                break;
            case 2:
                this.canCast[1] = false;
                this.stopMoving(castDelay);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "hunson_power2a", this.location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "hunson_fear",
                        1500,
                        id + "_fear",
                        false,
                        "",
                        false,
                        false,
                        team);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "fx_target_ring_2.5",
                        1500,
                        id + "_fearRing",
                        false,
                        "",
                        false,
                        true,
                        team);
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
                                new HunsonAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                this.resetTarget();
                this.stopMoving(3500 + castDelay);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_hunson_scream1", this.location);
                Runnable soundDelay =
                        () -> {
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "hunson_power3a",
                                    this.location);
                        };
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(soundDelay, 625, TimeUnit.MILLISECONDS);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_hunson_head1",
                        3500 + castDelay,
                        this.id + "_ultHead",
                        true,
                        "headNode",
                        true,
                        false,
                        this.team);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_4",
                        this.id + "_ultRing",
                        3500 + castDelay,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        true,
                        this.team,
                        0f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        3500 + castDelay);
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new HunsonAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
        }
    }

    private void endUlt() {
        this.ultActivated = false;
        this.canMove = true;
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultHead");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSuck");
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 500, false);
    }

    private void interruptE() {
        this.canMove = true;
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultHead");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSuck");
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 500, false);
    }

    private class HunsonAbilityHandler extends AbilityRunnable {

        public HunsonAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            int W_CAST_DELAY = 400;
            Runnable enableWCasting = () -> canCast[1] = true;
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            enableWCasting,
                            getReducedCooldown(cooldown) - W_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            for (Actor a :
                    Champion.getActorsInRadius(
                            parentExt.getRoomHandler(room.getId()), dest, 2.5f)) {
                if (isNonStructure(a)) {
                    a.addToDamageQueue(Hunson.this, getSpellDamage(spellData), spellData);
                    if (!a.getId().contains("turret") || !a.getId().contains("decoy"))
                        a.handleFear(Hunson.this.location, 1500);
                }
            }
        }

        @Override
        protected void spellE() {
            int E_CAST_DELAY = 750;
            Runnable enableECasting = () -> canCast[2] = true;
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            enableECasting,
                            getReducedCooldown(cooldown) - E_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "hunson_soul_suck",
                    3500,
                    id + "_ultSuck",
                    true,
                    "",
                    true,
                    false,
                    team);
            addEffect("armor", getStat("armor") * 0.5d, 3500, null, "", false);
            addEffect("spellVamp", 45, 3500, null, "", false);
            ultActivated = true;
            ultStart = System.currentTimeMillis();
        }

        @Override
        protected void spellPassive() {
            passiveActivated = false;
        }
    }

    private class HudsonProjectile extends Projectile {

        public HudsonProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            double damage = getSpellDamage(spellData);
            if (qVictims.containsKey(victim)) {
                int timesHit = qVictims.get(victim);
                if (timesHit == 1) damage *= 0.3d;
                else if (timesHit == 2) damage *= 0.1d;
                qVictims.put(victim, timesHit + 1);
            } else {
                qVictims.put(victim, 1);
            }
            victim.handlePull(Hunson.this.location, 1.2);
            victim.addToDamageQueue(Hunson.this, damage, spellData);
            victim.addState(ActorState.SLOWED, 0.1d, 2000, null, false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
            this.destroy();
        }

        @Override
        public void destroy() {
            super.destroy();
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    room,
                    id,
                    "hunson_projectile_explode",
                    id + "_destroyed",
                    1000,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    team,
                    0f);
            canCast[0] = true;
        }
    }
}

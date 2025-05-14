package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Hunson extends UserActor {
    private static final int PASSIVE_DURATION = 3500;
    private static final int PASSIVE_ATTACKSPEED_DURATION = 3500;
    private static final int PASSIVE_SPEED_DURATION = 3500;
    private static final double PASSIVE_SPEED_VALUE = 1d;
    private static final double PASSIVE_ATTACKSPEED_VALUE = 0.5d;
    private static final double Q_SLOW_VALUE = 0.1d;
    private static final double Q_PULL_DISTANCE = 1.2;
    private static final int Q_SLOW_DURATION = 2000;
    private static final double Q_THIRD_HIT_DMG_MULTIPLIER = 1.25d;
    private static final int W_SOUND_DELAY = 625;
    private static final int W_CAST_DELAY = 400;
    private static final int W_FEAR_DURATION = 1500;
    private static final int W_DAMAGE_DURATION = 1500;
    private static final int E_DURATION = 3500;
    private static final int E_CAST_DELAY = 750;
    private static final double E_ARMOR_VALUE = 0.5d;
    private static final int E_SPELLVAMP_VALUE = 45;

    private Map<Actor, Integer> qVictims;
    private boolean qActivated = false;
    private int qUses = 0;
    private boolean ultActivated = false;
    private boolean passiveActivated = false;
    private long qStartTime = 0;
    private long ultStart = 0;
    private long wStartTime = 0;
    private Point2D wLocation;
    private boolean canUsePassive = true;
    private HashMap<Actor, Long> dotActors = new HashMap<>();

    public Hunson(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (wLocation != null && System.currentTimeMillis() - wStartTime >= 1100) {
            wLocation = null;
        }

        if (wLocation != null) {
            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            for (Actor a : rh.getActorsInRadius(wLocation, 2.5f)) {
                if (isNeitherStructureNorAlly(a) && !dotActors.containsKey(a)) {
                    a.handleFear(wLocation, W_FEAR_DURATION);
                }

                if (!dotActors.containsKey(a) && isNeitherTowerNorAlly(a)) {
                    dotActors.put(a, System.currentTimeMillis());
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            a.getId(),
                            "neptr_dot_poison",
                            W_DAMAGE_DURATION,
                            id + "hunsonW" + Math.random(),
                            true,
                            "",
                            false,
                            false,
                            a.getTeam());
                }
            }
        }

        Iterator<Map.Entry<Actor, Long>> iterator = dotActors.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Actor, Long> entry = iterator.next();
            if (System.currentTimeMillis() - entry.getValue() >= 1500) {
                iterator.remove();
            }
        }

        if (!dotActors.isEmpty()) {
            JsonNode spellData = parentExt.getAttackData(this.getAvatar(), "spell2");
            double damage = getSpellDamage(spellData, false) / 10d;
            for (Actor a : dotActors.keySet()) {
                a.addToDamageQueue(Hunson.this, damage, spellData, true);
            }
        }

        if (this.ultActivated) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.location, 4f)) {
                if (isNeitherTowerNorAlly(a)) {
                    double dmg = getSpellDamage(spellData, false) / 10d;
                    a.addToDamageQueue(this, dmg, spellData, true);
                }
            }
        }
        if (this.ultActivated && System.currentTimeMillis() - this.ultStart >= E_DURATION) {
            this.ultActivated = false;
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 500, false);
            if (!this.isStopped()) this.move(this.movementLine.getP2());
        }
        if (this.ultActivated && (this.dead || this.hasInterrupingCC())) {
            if (hasInterrupingCC()) {
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
            }
            this.endUlt();
        }
        if (this.qActivated
                && System.currentTimeMillis() - this.qStartTime >= 4500
                && this.qUses > 0) {
            this.qUses = 0;
            int baseQCooldown = ChampionData.getBaseAbilityCooldown(this, 1);
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "q", true, getReducedCooldown(baseQCooldown), 250);
            this.qActivated = false;
        }
    }

    @Override
    public boolean canAttack() {
        if (this.ultActivated) return false;
        return super.canAttack();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultActivated) this.endUlt();
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("speed") && this.ultActivated)
            return super.getPlayerStat(stat) * 0.40d;
        return super.getPlayerStat(stat);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        Console.debugLog("Passive: " + this.passiveActivated + " | CanUse: " + this.canUsePassive);
        if (this.hasStatusEffect(a) && !this.passiveActivated && this.canUsePassive) {
            this.passiveActivated = true;
            this.canUsePassive = false;
            basicAttackReset();
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "vo/vo_hunson_offer_your_soul_short",
                    this.location);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "hunson_hands_passive",
                    PASSIVE_DURATION,
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
                    PASSIVE_DURATION,
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
                    PASSIVE_DURATION);
            double delta = this.getStat("attackSpeed") * -PASSIVE_ATTACKSPEED_VALUE;
            this.addEffect("attackSpeed", delta, PASSIVE_ATTACKSPEED_DURATION);
            this.addEffect("speed", PASSIVE_SPEED_VALUE, PASSIVE_SPEED_DURATION);
            int cooldown = ChampionData.getBaseAbilityCooldown(this, 4);
            scheduleTask(abilityRunnable(4, null, cooldown, 0, null), PASSIVE_DURATION);
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
                int abilityCooldown = 0;
                try {
                    this.qStartTime = System.currentTimeMillis();
                    this.stopMoving();
                    if (!this.qActivated) {
                        this.qActivated = true;
                        this.qVictims = new HashMap<>(3);
                        this.qUses = 3;
                    }
                    this.qUses--;
                    abilityCooldown = this.qUses > 0 ? 850 : getReducedCooldown(cooldown);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_hunson_scream2",
                            this.location);
                    Line2D spellLine = Champion.getAbilityLine(this.location, dest, 8f);
                    this.fireProjectile(
                            new HudsonProjectile(
                                    this.parentExt,
                                    this,
                                    spellLine,
                                    8f,
                                    0.5f,
                                    "projectile_hunson_pull"),
                            this.location,
                            dest,
                            8f);
                    if (this.qUses == 0) {
                        this.qActivated = false;
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "q", true, abilityCooldown, gCooldown);
                break;
            case 2:
                this.canCast[1] = false;
                try {
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
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, this.location),
                        W_CAST_DELAY);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.resetTarget();
                    this.stopMoving(castDelay);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_hunson_scream1",
                            this.location);
                    Runnable soundDelay =
                            () ->
                                    ExtensionCommands.playSound(
                                            this.parentExt,
                                            this.room,
                                            this.id,
                                            "hunson_power3a",
                                            this.location);
                    scheduleTask(soundDelay, W_SOUND_DELAY);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_hunson_head1",
                            E_DURATION + castDelay,
                            this.id + "_ultHead",
                            true,
                            "headNode",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_4",
                            E_DURATION + castDelay,
                            this.id + "_ultRing",
                            true,
                            "",
                            true,
                            true,
                            this.team);
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
                        E_DURATION + castDelay);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
        }
    }

    private boolean hasStatusEffect(Actor a) {
        ActorState[] states = ActorState.values();
        for (ActorState s : states) {
            Console.debugLog("STATE: " + s.name());
            if (a.getState(s)
                    && s != ActorState.BRUSH
                    && s != ActorState.TRANSFORMED
                    && s != ActorState.REVEALED) return true;
        }
        return false;
    }

    private void endUlt() {
        this.ultActivated = false;
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultHead");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultRing");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_ultSuck");
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 500, false);
        if (!this.isStopped()) this.move(this.movementLine.getP2());
    }

    private HunsonAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new HunsonAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class HunsonAbilityRunnable extends AbilityRunnable {

        public HunsonAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            if (getHealth() > 0) {
                wLocation = location;
                wStartTime = System.currentTimeMillis();

            } else {
                ExtensionCommands.removeFx(parentExt, room, id + "_fear");
            }

            Runnable enableWCasting = () -> canCast[1] = true;
            int delay = getReducedCooldown(cooldown) - W_CAST_DELAY;
            scheduleTask(enableWCasting, delay);
        }

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "hunson_soul_suck",
                    E_DURATION,
                    id + "_ultSuck",
                    true,
                    "",
                    true,
                    false,
                    team);
            addEffect("armor", getStat("armor") * E_ARMOR_VALUE, E_DURATION);
            addEffect("spellVamp", E_SPELLVAMP_VALUE, E_DURATION);
            ultActivated = true;
            ultStart = System.currentTimeMillis();
        }

        @Override
        protected void spellPassive() {
            passiveActivated = false;
            ExtensionCommands.actorAbilityResponse(parentExt, player, "passive", true, cooldown, 0);
            Runnable allowPassive = () -> canUsePassive = true;
            Console.debugLog("Cooldown: " + cooldown);
            scheduleTask(allowPassive, cooldown);
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
            double damage = getSpellDamage(spellData, true);

            if (qVictims.containsKey(victim)) {
                int timesHit = qVictims.get(victim);
                if (timesHit == 2) damage *= Q_THIRD_HIT_DMG_MULTIPLIER;
                qVictims.put(victim, timesHit + 1);
            } else {
                qVictims.put(victim, 1);
            }

            if (isNeitherStructureNorAlly(victim)) {
                victim.handlePull(Hunson.this.location, Q_PULL_DISTANCE);
                victim.addState(ActorState.SLOWED, Q_SLOW_VALUE, Q_SLOW_DURATION);
            }

            victim.addToDamageQueue(Hunson.this, damage, spellData, false);
            String sound = "akubat_projectileHit1";
            ExtensionCommands.playSound(parentExt, room, "", sound, victim.getLocation());
            destroy();
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

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Billy extends UserActor {
    private static final int FINAL_PASSIVE_DURATION = 6000;
    private static final int W_ATTACKSPEED_DURATION = 4000;
    private static final float W_ATTACKSPEED_VALUE = 0.7f;
    private static final int W_SPEED_DURATION = 6000;
    private static final float W_SPEED_VALUE = 0.8f;
    private static final float Q_OFFSET_DISTANCE = 1.5f;
    private static final float Q_SPELL_RANGE = 4.5f;
    private static final int W_CRATER_OFFSET = 1;
    private static final int E_CAST_DELAY = 750;
    private static final int E_EMP_DURATION = 4500;
    private int passiveUses = 0;
    private Point2D ultLocation = null;
    private long ultStartTime = 0;
    private long lastUltTick = 0;
    private long finalPassiveStart = 0;
    private long lastSoundPlayed = 0;
    private Point2D ultLoc = null;
    private long lastPulseTime = 0;
    private int pulseCounter = 0;

    public Billy(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultLocation != null
                && System.currentTimeMillis() - this.ultStartTime < E_EMP_DURATION
                && System.currentTimeMillis() - this.lastUltTick >= 200) {
            this.lastUltTick = System.currentTimeMillis();
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
            List<Actor> impactedActors =
                    Champion.getEnemyActorsInRadius(handler, this.team, this.ultLocation, 2.25f);
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            for (Actor a : impactedActors) {
                double damageReduction = 1 - (0.15 * impactedActors.size());
                if (damageReduction >= 0.7d) damageReduction = 0.7d;
                a.addToDamageQueue(
                        this,
                        (this.getSpellDamage(spellData) / 5d) * damageReduction,
                        spellData,
                        true);
            }
        } else if (this.ultLocation != null
                && System.currentTimeMillis() - this.ultStartTime >= E_EMP_DURATION) {
            this.ultLocation = null;
        }
        if (System.currentTimeMillis() - finalPassiveStart >= FINAL_PASSIVE_DURATION) {
            ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "finalpassive");
        }
        if (this.ultLocation != null
                && System.currentTimeMillis() - this.ultStartTime < E_EMP_DURATION
                && System.currentTimeMillis() - lastSoundPlayed >= 300) {
            ExtensionCommands.playSound(
                    this.parentExt, this.room, "", "sfx_billy_nothung_pulse", this.ultLocation);
            lastSoundPlayed = System.currentTimeMillis();
        }
        if (ultLocation != null
                && System.currentTimeMillis() - ultStartTime < E_EMP_DURATION
                && System.currentTimeMillis() - lastPulseTime >= 175) {
            lastPulseTime = System.currentTimeMillis();
            pulseCounter++;
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "billy_nothung_pulse",
                    this.id + "_ultPulse" + pulseCounter,
                    1000,
                    (float) this.ultLocation.getX(),
                    (float) this.ultLocation.getY(),
                    false,
                    this.team,
                    0f);
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
                    this.stopMoving();
                    Path2D quadrangle =
                            Champion.createRectangle(
                                    location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> actorsInPolygon =
                            handler.getEnemiesInPolygon(this.team, quadrangle);
                    if (!actorsInPolygon.isEmpty()) {
                        for (Actor a : actorsInPolygon) {
                            if (isNonStructure(a)) {
                                a.knockback(this.location, 5f);
                                if (this.passiveUses == 3) a.addState(ActorState.STUNNED, 0d, 2000);
                            }
                            a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                        }
                    }
                    if (this.passiveUses == 3) this.usePassiveAbility();
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_billy_knock_back",
                            this.location);

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
                Point2D finalDashPoint = this.location;
                double time = 0;
                try {
                    Point2D ogLocation = this.location;
                    finalDashPoint = this.dash(dest, true, 14d);
                    time = ogLocation.distance(finalDashPoint) / 14d;
                    int wTime = (int) (time * 1000);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_billy_jump", this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "billy_dash_trail",
                            (int) (time * 1000),
                            this.id + "_dash",
                            true,
                            "Bip001",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell2", wTime, false);
                    if (this.passiveUses == 3) {
                        double delta = this.getStat("attackSpeed") * -W_ATTACKSPEED_VALUE;
                        this.addEffect("attackSpeed", delta, W_ATTACKSPEED_DURATION);
                        this.addEffect("speed", W_SPEED_VALUE, W_SPEED_DURATION);
                        this.usePassiveAbility();
                        ExtensionCommands.addStatusIcon(
                                this.parentExt,
                                this.player,
                                "finalpassive",
                                "billy_spell_4_short_description",
                                "icon_billy_passive",
                                6000);
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
                        finalPassiveStart = System.currentTimeMillis();
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
                int delay1 = (int) (time * 1000d);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDashPoint),
                        delay1);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.stopMoving(castDelay);
                    this.ultLoc = Champion.getAbilityLine(this.location, dest, 5.5f).getP2();
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_billy_nothung",
                            this.location);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "lemongrab_ground_aoe_target",
                            this.id + "_billyUltTarget",
                            1750,
                            (float) ultLoc.getX(),
                            (float) ultLoc.getY(),
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
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            int cd = getReducedCooldown(cooldown);
            scheduleTask(enableWCasting, cd);
            if (getHealth() > 0) {
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_billy_ground_pound_temp", dest);
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
                for (Actor a : Champion.getActorsInRadius(handler, dest, 2f)) {
                    if (isNonStructure(a)) {
                        a.addToDamageQueue(Billy.this, getSpellDamage(spellData), spellData, false);
                    }
                }
            }
        }

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);

            if (getHealth() > 0) {
                ExtensionCommands.playSound(
                        parentExt, room, "", "sfx_billy_nothung_skyfall", ultLoc);
                int duration = 1000;
                if (passiveUses == 3) {
                    ultLocation = ultLoc;
                    ultStartTime = System.currentTimeMillis();
                    lastUltTick = System.currentTimeMillis();
                    lastSoundPlayed = System.currentTimeMillis();
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
                        (float) ultLoc.getX(),
                        (float) ultLoc.getY(),
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
                        (float) ultLoc.getX(),
                        (float) ultLoc.getY(),
                        false,
                        team,
                        0f);
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, ultLoc, 2.25f)) {
                    if (isNonStructure(a)) {
                        a.addToDamageQueue(Billy.this, getSpellDamage(spellData), spellData, false);
                    }
                }
            }
        }

        @Override
        protected void spellPassive() {}
    }
}

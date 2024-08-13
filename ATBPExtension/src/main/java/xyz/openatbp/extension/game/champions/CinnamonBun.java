package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class CinnamonBun extends UserActor {
    private static final float PASSIVE_HEAL = 0.05f;
    private static final float Q_OFFSET_DISTANCE = 1f;
    private static final float Q_SPELL_RANGE = 3f;
    private static final float W_OFFSET_DISTANCE = 0.75f;
    private static final float W_SPELL_RANGE = 7f;
    private static final int W_DURATION = 5000;
    private static final int E_DURATION = 4500;
    private static final int ULT_TICK_DELAY = 500;
    private static final int E_ATTACKSPEED_DURATION = 4500;
    private static final int E_ATTACKDAMAGE_DURATION = 4500;
    private static final float E_ATTACKSPEED_VALUE = 0.2f;
    private static final float E_ATTACKDAMAGE_VALUE = 0.2f;
    private Point2D ultPoint = null;
    private Point2D ultPoint2 = null;
    private int ultUses = 0;
    private long ultStart = 0;
    private long lastUltEffect = 0;
    private boolean canApplyUltEffects = false;
    private boolean ultEffectsApplied = false;
    private Path2D wPolygon = null;
    private long wStartTime = 0;
    private long lastUltTick = 0;
    private boolean testing = false;
    private long testingTime = 0;

    public CinnamonBun(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.wPolygon != null && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            this.wPolygon = null;
        }
        if (this.wPolygon != null) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");
            double percentage = 0.2d + ((double) (this.level) * 0.01d);
            int duration = 2000 + (this.level * 100);
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
            List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(this.team, this.wPolygon);
            if (!actorsInPolygon.isEmpty()) {
                for (Actor a : actorsInPolygon) {
                    a.addToDamageQueue(this, getSpellDamage(spellData) / 10d, spellData, true);
                    if (isNonStructure(a)) a.addState(ActorState.SLOWED, percentage, duration);
                }
            }
        }

        if (this.ultPoint != null && System.currentTimeMillis() - this.ultStart < E_DURATION) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            double tickDamage = getSpellDamage(spellData);
            int radius = 2;
            if (this.ultUses > 1 && this.ultPoint2 == null) {
                radius = 4;
                tickDamage *= 1.5;
            }
            if (System.currentTimeMillis() - this.lastUltTick >= ULT_TICK_DELAY) {
                this.lastUltTick = System.currentTimeMillis();
                for (Actor a : this.getEnemiesInRadius(this.ultPoint, radius)) {
                    a.addToDamageQueue(this, tickDamage / 2, spellData, true);
                }
                if (ultPoint2 != null && ultUses > 1) {
                    for (Actor a : this.getEnemiesInRadius(ultPoint2, radius)) {
                        a.addToDamageQueue(this, tickDamage / 2, spellData, true);
                    }
                }
            }
            if (this.getLocation().distance(ultPoint) <= radius
                    || ultPoint2 != null && this.getLocation().distance(ultPoint2) <= radius) {
                handleUltBuff();
            }
        } else if (this.ultPoint != null
                && System.currentTimeMillis() - this.ultStart >= E_DURATION) {
            int baseCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, "", "sfx_cb_power3_end", this.ultPoint);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "e", true, getReducedCooldown(baseCooldown), 500);
            float radius = 2f;
            if (this.ultUses > 1 && this.ultPoint2 == null) {
                radius = 4f;
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "cb_ring_explode_big",
                        this.id + "_bigExplosion",
                        2000,
                        (float) this.ultPoint.getX(),
                        (float) this.ultPoint.getY(),
                        false,
                        this.team,
                        0f);
            } else {
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "cb_ring_sm_explode",
                        this.id + "_smallExplosion",
                        2000,
                        (float) this.ultPoint.getX(),
                        (float) this.ultPoint.getY(),
                        false,
                        this.team,
                        0f);
            }

            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());

            for (Actor a : Champion.getActorsInRadius(handler, this.ultPoint, radius)) {
                if (a.getTeam() != this.team) {
                    a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                }
            }
            if (this.ultPoint2 != null) {
                for (Actor a : Champion.getActorsInRadius(handler, this.ultPoint2, radius)) {
                    if (a.getTeam() != this.team) {
                        a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                    }
                }
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "cb_ring_sm_explode",
                        this.id + "_smallExplosion2",
                        2000,
                        (float) this.ultPoint2.getX(),
                        (float) this.ultPoint2.getY(),
                        false,
                        this.team,
                        0f);
            }
            this.ultPoint = null;
            this.ultPoint2 = null;
            this.ultUses = 0;
            this.ultStart = 0;
        }
        if (this.ultEffectsApplied) {
            if (System.currentTimeMillis() - lastUltEffect >= E_DURATION) {
                ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "ultEffect");
                this.ultEffectsApplied = false;
            }
        }
        if (testing
                && System.currentTimeMillis() - testingTime
                        >= E_DURATION) { // TODO: REMOVE AFTER FIXING ABILITIES
            testing = false;
            int baseCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
            Runnable resetCanCast = () -> this.canCast[2] = true;
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "e", true, getReducedCooldown(baseCooldown), 500);
            scheduleTask(resetCanCast, getReducedCooldown(baseCooldown));
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
                    this.stopMoving();
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_cb_power1", this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "cb_lance_jab_v2",
                            500,
                            this.id + "_jab",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    handlePassive();
                    Path2D qRect =
                            Champion.createRectangle(
                                    location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);

                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(this.team, qRect);
                    if (!actorsInPolygon.isEmpty()) {
                        for (Actor a : actorsInPolygon) {
                            a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                        }
                    }
                    this.attackCooldown = 0;
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
                Point2D finalDashPoint = null;
                try {
                    this.wStartTime = System.currentTimeMillis();
                    handlePassive();
                    Point2D origLocation = this.location;
                    Line2D wLine = Champion.getAbilityLine(origLocation, dest, 6.5f);
                    double slideX = Champion.getAbilityLine(origLocation, dest, 1.5f).getX2();
                    double slideY = Champion.getAbilityLine(origLocation, dest, 1.5f).getY2();
                    float rotation = getRotation(dest);
                    finalDashPoint = this.dash(wLine.getP2(), false, 15d);
                    double time = origLocation.distance(finalDashPoint) / 15d;
                    int wTime = (int) (time * 1000);
                    Line2D wPolyStartLine = Champion.getAbilityLine(origLocation, dest, 0.5f);
                    Line2D wPolyLengthLine = Champion.getAbilityLine(origLocation, dest, 7f);
                    Point2D wPolyStartPoint =
                            new Point2D.Float(
                                    (float) wPolyStartLine.getX2(), (float) wPolyStartLine.getY2());
                    Point2D wPolyEndPoint =
                            new Point2D.Float(
                                    (float) wPolyLengthLine.getX2(),
                                    (float) wPolyLengthLine.getY2());
                    this.wPolygon =
                            Champion.createRectangle(
                                    wPolyStartPoint,
                                    wPolyEndPoint,
                                    W_SPELL_RANGE,
                                    W_OFFSET_DISTANCE);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_rect_7",
                            W_DURATION,
                            this.id + "w",
                            false,
                            "",
                            true,
                            true,
                            this.team);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "cb_frosting_slide",
                            this.id + "_slide",
                            W_DURATION,
                            (float) slideX,
                            (float) slideY,
                            false,
                            this.team,
                            rotation);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_cb_power2", this.location);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell2b", wTime, false);
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
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDashPoint),
                        delay1);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.stopMoving();
                    if (this.ultUses == 0) {
                        this.canApplyUltEffects = true;
                        handlePassive();
                        this.ultPoint = dest;
                        this.ultStart = System.currentTimeMillis();
                        this.lastUltTick = System.currentTimeMillis();
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, "", "sfx_cb_power3a", dest);
                        ExtensionCommands.createWorldFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "cb_frosting_ring_sm",
                                this.id + "_ultSmall",
                                E_DURATION,
                                (float) dest.getX(),
                                (float) dest.getY(),
                                false,
                                this.team,
                                0f);
                        ExtensionCommands.createWorldFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fx_target_ring_2",
                                this.id + "_smUltRing",
                                E_DURATION,
                                (float) dest.getX(),
                                (float) dest.getY(),
                                true,
                                this.team,
                                0f);
                    } else if (this.ultUses == 1) {
                        if (this.ultPoint.distance(dest) <= 2) {
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_ultSmall");
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_smUltRing");
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "cb_frosting_ring_big",
                                    this.id + "_ultBig",
                                    E_DURATION - (int) (System.currentTimeMillis() - this.ultStart),
                                    (float) dest.getX(),
                                    (float) dest.getY(),
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "fx_target_ring_4",
                                    this.id + "_bigUltRing",
                                    E_DURATION - (int) (System.currentTimeMillis() - this.ultStart),
                                    (float) dest.getX(),
                                    (float) dest.getY(),
                                    true,
                                    this.team,
                                    0f);
                            ExtensionCommands.playSound(
                                    this.parentExt, this.room, "", "sfx_cb_power3c", dest);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "vo/vo_cb_wanna_pet",
                                    this.location);
                        } else {
                            this.ultPoint2 = dest;
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "cb_frosting_ring_sm",
                                    this.id + "_ultSmall2",
                                    E_DURATION - (int) (System.currentTimeMillis() - this.ultStart),
                                    (float) dest.getX(),
                                    (float) dest.getY(),
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "fx_target_ring_2",
                                    this.id + "_smUltRing2",
                                    E_DURATION - (int) (System.currentTimeMillis() - this.ultStart),
                                    (float) dest.getX(),
                                    (float) dest.getY(),
                                    true,
                                    this.team,
                                    0f);
                            ExtensionCommands.playSound(
                                    this.parentExt, this.room, "", "sfx_cb_power3b", dest);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "vo/vo_cb_wanna_pet",
                                    this.location);
                        }
                    } else {
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, "", "sfx_cb_power3_end", dest);
                        float radius = 2f;
                        if (this.ultPoint2 == null) {
                            radius = 4f;
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_ultBig");
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_bigUltRing");
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "cb_ring_explode_big",
                                    this.id + "_bigExplosion",
                                    2000,
                                    (float) this.ultPoint.getX(),
                                    (float) this.ultPoint.getY(),
                                    false,
                                    this.team,
                                    0f);
                        } else {
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_ultSmall");
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_smUltRing");
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "cb_ring_sm_explode",
                                    this.id + "_smallExplosion",
                                    2000,
                                    (float) this.ultPoint.getX(),
                                    (float) this.ultPoint.getY(),
                                    false,
                                    this.team,
                                    0f);
                        }
                        RoomHandler handler1 = parentExt.getRoomHandler(room.getName());
                        for (Actor a :
                                Champion.getActorsInRadius(handler1, this.ultPoint, radius)) {
                            if (a.getTeam() != this.team) {
                                a.addToDamageQueue(
                                        this, getSpellDamage(spellData), spellData, false);
                            }
                        }
                        if (this.ultPoint2 != null) {
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "cb_ring_sm_explode",
                                    this.id + "_smallExplosion2",
                                    2000,
                                    (float) this.ultPoint2.getX(),
                                    (float) this.ultPoint2.getY(),
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_ultSmall2");
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_smUltRing2");
                            RoomHandler handler2 = parentExt.getRoomHandler(room.getName());
                            for (Actor a :
                                    Champion.getActorsInRadius(handler2, this.ultPoint2, radius)) {
                                if (a.getTeam() != this.team) {
                                    a.addToDamageQueue(
                                            this, getSpellDamage(spellData), spellData, false);
                                }
                            }
                        }
                        this.ultPoint = null;
                        this.ultPoint2 = null;
                        this.ultStart = 0;
                    }
                    if (this.ultUses < 3) {
                        this.ultUses++;
                    }
                    int eUseDelay = ultUses < 2 ? 0 : gCooldown;
                    if (this.ultUses == 2) {
                        ExtensionCommands.actorAbilityResponse(
                                this.parentExt, this.player, "e", true, eUseDelay, 0);
                    }
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            eUseDelay);
                    if (this.ultUses == 3) {
                        ExtensionCommands.actorAbilityResponse(
                                this.parentExt,
                                this.player,
                                "e",
                                true,
                                getReducedCooldown(cooldown),
                                gCooldown);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                    this.testing = true;
                    this.testingTime = System.currentTimeMillis();
                }
                break;
        }
    }

    private void handleUltBuff() {
        if (this.canApplyUltEffects) {
            lastUltEffect = System.currentTimeMillis();
            this.canApplyUltEffects = false;
            this.ultEffectsApplied = true;
            this.addEffect(
                    "attackSpeed",
                    this.getStat("attackSpeed") * -E_ATTACKSPEED_VALUE,
                    E_ATTACKSPEED_DURATION);
            this.addEffect(
                    "attackDamage",
                    this.getStat("attackDamage") * E_ATTACKDAMAGE_VALUE,
                    E_ATTACKDAMAGE_DURATION);
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "ultEffect",
                    "cinnamonbun_spell_3_short_description",
                    "icon_cinnamonbun_s3",
                    E_DURATION);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "billy_crit_hands",
                    E_DURATION,
                    this.id + "_cbCritHandsR",
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
                    E_DURATION,
                    this.id + "_cbCritHandsL",
                    true,
                    "Bip001 L Hand",
                    true,
                    false,
                    this.team);
        }
    }

    private List<Actor> getEnemiesInRadius(Point2D center, float radius) {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        List<Actor> returnVal = Champion.getActorsInRadius(handler, center, radius);
        returnVal.removeIf(a -> a.getTeam() == this.team);
        return returnVal;
    }

    private void handlePassive() {
        this.changeHealth((int) ((double) (this.getMaxHealth()) * PASSIVE_HEAL));
    }

    private CinnamonAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new CinnamonAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class CinnamonAbilityRunnable extends AbilityRunnable {

        public CinnamonAbilityRunnable(
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
            canCast[2] = true;
            if (ultUses == 3) {
                int E_GLBAL_COOLDOWN = 500;
                Runnable enableQCasting = () -> canCast[2] = true;
                int delay = getReducedCooldown(cooldown) - E_GLBAL_COOLDOWN;
                scheduleTask(enableQCasting, delay);
                ultUses = 0;
            }
        }

        @Override
        protected void spellPassive() {}
    }
}

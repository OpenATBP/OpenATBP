package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class CinnamonBun extends UserActor {
    private static final float PASSIVE_HEAL_PERCENT = 0.05f;
    private static final float Q_OFFSET_DISTANCE = 1f;
    private static final float Q_SPELL_RANGE = 3f;
    private static final float W_OFFSET_DISTANCE = 0.75f;
    private static final float W_SPELL_RANGE = 7f;
    private static final int W_DURATION = 5000;
    private static final float W_SLOW_PERCENT = 0.2f;
    private static final int W_SLOW_DURATION = 1000;
    private static final int E_DURATION = 4500;
    private static final int E_TICK_DELAY = 500;
    private static final float E_SPEED_BUFF_PERCENT = 0.1f;
    public static final float W_DASH_SPEED = 15f;

    private Point2D ultPoint = null;
    private Point2D ultPoint2 = null;
    private int ultUses = 0;
    private long ultStart = 0;
    private AbilityShape wPolygon = null;
    private long wStartTime = 0;
    private long lastUltTick = 0;

    private Map<Actor, Long> actorsWithWSlow = new HashMap<>();

    public CinnamonBun(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (ultUses == 1
                && ultPoint != null
                && location.distance(ultPoint) <= 2
                && !effectManager.hasEffect(id + "_cb_e_speed")) {
            addESpeed();
        }

        if (ultUses == 1
                && ultPoint != null
                && location.distance(ultPoint) > 2
                && effectManager.hasEffect(id + "_cb_e_speed")) {
            removeESpeed();
        }

        if (ultUses > 1
                && ultPoint2 == null
                && ultPoint != null
                && location.distance(ultPoint) <= 4
                && !effectManager.hasEffect(id + "_cb_e_speed")) {
            addESpeed();
        } else if (effectManager.hasEffect(id + "_cb_e_speed")
                && ultPoint != null
                && location.distance(ultPoint) > 4) {
            effectManager.removeAllEffectsById(id + "_cb_e_speed");
        }

        if (ultUses > 1 && ultPoint2 != null && ultPoint != null) {
            boolean insideUltRing = false;
            for (Point2D p : new Point2D[] {ultPoint, ultPoint2}) {
                if (location.distance(p) <= 2) {
                    insideUltRing = true;
                    break;
                }
            }

            if (insideUltRing && !effectManager.hasEffect(id + "_cb_e_speed")) {
                addESpeed();
            } else if (!insideUltRing && effectManager.hasEffect(id + "_cb_e_speed")) {
                effectManager.removeAllEffectsById(id + "_cb_e_speed ult uses 2");
            }
        }

        if (this.wPolygon != null && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            this.wPolygon = null;
        }
        if (this.wPolygon != null) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");

            RoomHandler rh = this.parentExt.getRoomHandler(this.room.getName());

            List<Actor> nearbyEnemies =
                    Champion.getEnemyActorsInRadius(rh, team, location, W_SPELL_RANGE);
            if (!nearbyEnemies.isEmpty()) {
                for (Actor a : nearbyEnemies) {
                    if (isNeitherTowerNorAlly(a)
                            && wPolygon.contains(a.getLocation(), a.getCollisionRadius())
                            && a.isNotLeaping()) {
                        int damage = (int) (getSpellDamage(spellData, false) / 10d);
                        a.addToDamageQueue(this, damage, spellData, true);
                    }

                    if (isNeitherStructureNorAlly(a)
                            && wPolygon.contains(a.getLocation(), a.getCollisionRadius())
                            && a.isNotLeaping()) {
                        long lastProc = actorsWithWSlow.getOrDefault(a, -1L);

                        if (lastProc == -1
                                || System.currentTimeMillis() - lastProc > W_SLOW_DURATION) {
                            actorsWithWSlow.put(a, System.currentTimeMillis());
                            a.getEffectManager()
                                    .addState(
                                            ActorState.SLOWED,
                                            id + "_cb_w_slow",
                                            W_SLOW_PERCENT,
                                            W_SLOW_DURATION);
                        }
                    }
                }
            }
        }

        if (this.ultPoint != null && System.currentTimeMillis() - this.ultStart < E_DURATION) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
            double tickDamage = getSpellDamage(spellData, false);
            int radius = 2;

            if (this.ultUses > 1 && this.ultPoint2 == null) {
                radius = 4;
            }

            if (ultUses > 1 && ultPoint2 != null) {
                tickDamage *= 1.5;
            }

            if (System.currentTimeMillis() - this.lastUltTick >= E_TICK_DELAY) {
                this.lastUltTick = System.currentTimeMillis();
                for (Actor a : this.getEnemiesInRadius(this.ultPoint, radius)) {
                    if (isNeitherTowerNorAlly(a)) {
                        a.addToDamageQueue(this, tickDamage / 2, spellData, true);
                    }
                }
                if (ultPoint2 != null && ultUses > 1) {
                    for (Actor a : this.getEnemiesInRadius(ultPoint2, radius)) {
                        if (isNeitherTowerNorAlly(a)) {
                            a.addToDamageQueue(this, tickDamage / 2, spellData, true);
                        }
                    }
                }
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
                if (isNeitherTowerNorAlly(a)) {
                    a.addToDamageQueue(this, getSpellDamage(spellData, false), spellData, false);
                }
            }
            if (this.ultPoint2 != null) {
                for (Actor a : Champion.getActorsInRadius(handler, this.ultPoint2, radius)) {
                    if (isNeitherTowerNorAlly(a)) {
                        double damage = getSpellDamage(spellData, false);
                        a.addToDamageQueue(this, damage, spellData, false);
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
    }

    private void removeESpeed() {
        effectManager.removeAllEffectsById(id + "_cb_e_speed");
    }

    private void addESpeed() {
        effectManager.addEffect(
                id + "_cb_e_speed",
                "speed",
                E_SPEED_BUFF_PERCENT,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                E_DURATION);
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

                    AbilityShape qRect =
                            AbilityShape.createRectangle(
                                    location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);

                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> nearbyEnemies =
                            Champion.getActorsInRadius(handler, location, Q_SPELL_RANGE);
                    if (!nearbyEnemies.isEmpty()) {
                        for (Actor a : nearbyEnemies) {
                            if (isNeitherTowerNorAlly(a)
                                    && qRect.contains(a.getLocation(), a.getCollisionRadius())
                                    && a.isNotLeaping()) {
                                double damage = getSpellDamage(spellData, true);
                                a.addToDamageQueue(this, damage, spellData, false);
                            }
                        }
                    }
                    basicAttackReset();
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

                wStartTime = System.currentTimeMillis();
                handlePassive();
                double slideX = Champion.createLineTowards(location, dest, 1.5f).getX2();
                double slideY = Champion.createLineTowards(location, dest, 1.5f).getY2();
                float rotation = getRotation(dest);

                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                PathFinder pf = rh.getPathFinder();

                Point2D leapDest =
                        Champion.createLineTowards(location, dest, W_SPELL_RANGE).getP2();

                Point2D leapPoint = pf.getNonObstaclePointOrIntersection(location, leapDest);
                double time = location.distance(leapPoint) / W_DASH_SPEED;
                int wTime = (int) (time * 1000);

                DashContext ctx =
                        new DashContext.Builder(location, leapPoint, W_DASH_SPEED)
                                .isLeap(true)
                                .canBeRedirected(false)
                                .build();
                startDash(ctx);

                Point2D rectStart = Champion.createLineTowards(location, dest, 0.5f).getP2();
                Point2D rectEnd = Champion.createLineTowards(location, dest, 7f).getP2();

                wPolygon =
                        AbilityShape.createRectangle(
                                rectStart, rectEnd, W_SPELL_RANGE, W_OFFSET_DISTANCE);

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

                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, leapPoint),
                        delay1);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.stopMoving();
                    if (this.ultUses == 0) {
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
                        for (Actor a : Champion.getActorsInRadius(handler1, ultPoint, radius)) {
                            if (isNeitherTowerNorAlly(a)) {
                                double damage = getSpellDamage(spellData, false);
                                a.addToDamageQueue(this, damage, spellData, false);
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
                                            this,
                                            getSpellDamage(spellData, false),
                                            spellData,
                                            false);
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
                }
                break;
        }
    }

    private void handleUltBuff() {}

    private List<Actor> getEnemiesInRadius(Point2D center, float radius) {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        List<Actor> returnVal = Champion.getActorsInRadius(handler, center, radius);
        returnVal.removeIf(a -> a.getTeam() == this.team);
        return returnVal;
    }

    private void handlePassive() {
        this.changeHealth((int) ((double) (this.getMaxHealth()) * PASSIVE_HEAL_PERCENT));
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

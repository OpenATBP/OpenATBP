package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.SkinData;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class RattleBalls extends UserActor {
    private static final double PASSIVE_LIFE_STEAL_VALUE = 0.65d;
    private static final int PASSIVE_STACK_DURATION = 5000;
    private static final int Q_PARRY_DURATION = 1500;
    private static final int W_CAST_DELAY = 500;
    private static final int E_DURATION = 3500;
    private static final double E_SPEED_VALUE = 0.14d;
    public static final int E_SECOND_USE_DELAY = 800;
    private boolean passiveActive = false;
    private int passiveHits = 0;
    private long startPassiveStack = 0;
    private int qUses = 0;
    private boolean qJumpActive = false;
    private boolean parryActive = false;
    private long parryCooldown = 0;
    private boolean ultActive = false;
    private long lastSoundTime = 0;
    private int qTime = 0;
    private int eCounter = 0;
    private long eStartTime;
    private static final float Q_SPELL_RANGE = 6f;
    private static final float Q_OFFSET_DISTANCE = 0.85f;
    private Path2D qThrustRectangle = null;

    public RattleBalls(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (passiveActive
                && System.currentTimeMillis() - startPassiveStack >= PASSIVE_STACK_DURATION) {
            endPassive();
        }
        if (parryActive
                && System.currentTimeMillis() - parryCooldown >= Q_PARRY_DURATION
                && !dead) { // Q SPIN ATTACK
            performQSpinAttack();
        }
        if (ultActive) {
            int soundCooldown = 450;
            if (System.currentTimeMillis() - lastSoundTime >= soundCooldown && !dead) {
                lastSoundTime = System.currentTimeMillis();
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_rattleballs_counter_stance", location);
            }
            if (ultActive && dead
                    || ultActive && System.currentTimeMillis() - eStartTime >= E_DURATION
                    || hasInterrupingCC()) {
                endUlt();
                if (hasInterrupingCC()) {
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_skill_interrupted", location);
                }
            }
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, location, 2f)) {
                if (isNonStructure(a)) {
                    JsonNode spellData = parentExt.getAttackData(avatar, "spell3");
                    a.addToDamageQueue(
                            this, (double) getSpellDamage(spellData) / 10d, spellData, true);
                }
            }
        }
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (parryActive && !hasMovementCC()) canMove = true;
        if (parryActive && getAttackType(attackData) == AttackType.SPELL) { // Q INTERRUPTION
            finishQAbility(true);
            String sound = "sfx_rattleballs_counter_fail";
            ExtensionCommands.playSound(parentExt, room, id, sound, location);
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
        }
        if (parryActive && getAttackType(attackData) == AttackType.PHYSICAL) { // Q COUNTER-ATTACK
            performQCounterAttack(a);
            return false;
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public boolean canAttack() {
        if (ultActive || parryActive) return false;
        return super.canAttack();
    }

    @Override
    public boolean canUseAbility(int ability) {
        // q jump active - cant use anything
        // parry active - can only use Q
        // ultActive - can only use E
        if (qJumpActive) return false;
        if (parryActive) return ability == 1;
        if (ultActive) return ability == 3;
        return super.canUseAbility(ability);
    }

    @Override
    public void handleLifeSteal() {
        if (passiveActive && passiveHits > 0) {
            double damage = getPlayerStat("attackDamage");
            double lifesteal = (getPlayerStat("lifeSteal") / 100) + PASSIVE_LIFE_STEAL_VALUE;
            if (lifesteal > 100) lifesteal = 100;
            changeHealth((int) (damage * lifesteal));
            ExtensionCommands.removeStatusIcon(parentExt, player, "passive" + passiveHits);
            passiveHits--;
            if (passiveHits == 1) {
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "passive1",
                        "rattleballs_spell_4_short_description",
                        "icon_rattleballs_p1",
                        PASSIVE_STACK_DURATION);
            }
            startPassiveStack = System.currentTimeMillis();
            ExtensionCommands.removeFx(parentExt, room, id + "_passiveRegen");
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "rattleballs_regen",
                    2000,
                    id + "_passiveRegen",
                    true,
                    "Bip001 Spine1",
                    true,
                    false,
                    team);
            ExtensionCommands.playSound(parentExt, room, id, "sfx_rattleballs_regen", location);
            if (passiveHits <= 0) {
                endPassive();
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
                if (qUses > 0) {
                    qThrustRectangle =
                            Champion.createRectangle(
                                    location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);
                }
                Point2D ogLocation = location;
                Point2D finalDashPoint = dash(dest, false, DASH_SPEED);
                double time = ogLocation.distance(finalDashPoint) / DASH_SPEED;
                qTime = (int) (time * 1000);
                int qTimeEffects = qTime * 5;
                String qTrailFX = SkinData.getRattleBallsQTrailFX(avatar);
                String qDashDustFX = SkinData.getRattleBallsQDustFX(avatar);
                if (qUses == 0) {
                    qJumpActive = true;
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_rattleballs_counter_stance", location);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_rattleballs_dash", location);
                    ExtensionCommands.actorAnimate(
                            parentExt, room, id, "spell1a", qTimeEffects, true);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            qTrailFX,
                            qTimeEffects,
                            id + "_q1Trail",
                            true,
                            "Bip001",
                            true,
                            false,
                            team);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            qDashDustFX,
                            qTimeEffects,
                            id + "_q1Dust",
                            true,
                            "",
                            true,
                            false,
                            team);
                    qUses++;
                } else { // Q THRUST ATTACK
                    finishQAbility(false);
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    List<Actor> enemiesInPolygon =
                            handler.getEnemiesInPolygon(team, qThrustRectangle);
                    if (!enemiesInPolygon.isEmpty()) {
                        for (Actor a : enemiesInPolygon) {
                            if (isNonStructure(a)) {
                                a.addToDamageQueue(
                                        this, getSpellDamage(spellData), spellData, false);
                            }
                        }
                    }
                    if (avatar.contains("spidotron")) {
                        ExtensionCommands.playSound(
                                parentExt,
                                room,
                                id,
                                "sfx_rattleballs_luchador_counter_stance",
                                location);
                    }
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_rattleballs_dash", location);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_rattleballs_rattle_balls_2", location);
                    ExtensionCommands.actorAnimate(
                            parentExt, room, id, "spell1c", qTimeEffects, false);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            qTrailFX,
                            qTimeEffects,
                            id + "_q2Trail",
                            true,
                            "Bip001",
                            true,
                            false,
                            team);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            qDashDustFX,
                            qTimeEffects,
                            id + "_q2Dust",
                            true,
                            "Bip001 Footsteps",
                            true,
                            false,
                            team);
                }
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDashPoint),
                        qTime);
                break;
            case 2:
                canCast[1] = false;
                try {
                    String wSFX = SkinData.getRattleBallsWSFX(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, wSFX, location);
                    stopMoving(castDelay);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_5",
                            id + "_wCircle",
                            castDelay,
                            (float) location.getX(),
                            (float) location.getY(),
                            true,
                            team,
                            0f);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 3:
                canCast[2] = false;
                eCounter++;
                eStartTime = System.currentTimeMillis();
                if (!ultActive) {
                    ultActive = true;
                    String spinCycleSFX = SkinData.getRattleBallsESpinCycleSFX(avatar);
                    String swordSpinFX = SkinData.getRattleBallsESwordSpinFX(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, spinCycleSFX, location);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "vo/vo_rattleballs_eggcelent_1", location);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_2",
                            E_DURATION,
                            id + "_ultRing",
                            true,
                            "",
                            false,
                            true,
                            team);
                    ExtensionCommands.actorAnimate(
                            parentExt, room, id, "spell3a", E_DURATION, true);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            swordSpinFX,
                            E_DURATION,
                            id + "_ultSpin",
                            true,
                            "",
                            false,
                            false,
                            team);
                    ExtensionCommands.actorAbilityResponse(
                            parentExt, player, "e", true, E_SECOND_USE_DELAY, 0);
                    addEffect("speed", getStat("speed") * E_SPEED_VALUE, E_DURATION);
                } else {
                    endUlt();
                }
                int eDelay = eCounter == 1 ? E_SECOND_USE_DELAY : getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), eDelay);
                break;
        }
    }

    private void endPassive() {
        passiveActive = false;
        ExtensionCommands.removeStatusIcon(parentExt, player, "passive" + passiveHits);
        passiveHits = 0;
    }

    private void activatePassive() {
        if (passiveHits == 0) {
            startPassiveStack = System.currentTimeMillis();
            passiveActive = true;
            passiveHits = 2;
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    "passive2",
                    "rattleballs_spell_4_short_description",
                    "icon_rattleballs_p2",
                    5000);
        }
    }

    private void performQSpinAttack() {
        finishQAbility(true);
        String qEndSFX = SkinData.getRattleBallsQEndSFX(avatar);
        String qSwordSpin = SkinData.getRattleBallsESwordSpinFX(avatar);
        ExtensionCommands.playSound(parentExt, room, id, qEndSFX, location);
        ExtensionCommands.playSound(
                parentExt, room, id, "sfx_rattleballs_rattle_balls_2", location);
        ExtensionCommands.actorAnimate(parentExt, room, id, "spell1b", 400, false);

        Runnable resetAnim =
                () -> ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 10, false);
        scheduleTask(resetAnim, 400);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                qSwordSpin,
                500,
                id + "_parrySpin",
                true,
                "",
                false,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "rattleballs_counter_trail",
                400,
                id + "_trail",
                true,
                "Bip001 Prop1",
                true,
                false,
                team);
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        List<Actor> affectedActors = Champion.getActorsInRadius(handler, location, 2f);
        for (Actor a : affectedActors) {
            if (isNonStructure(a)) {
                JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
                a.addToDamageQueue(this, getSpellDamage(spellData) + 25, spellData, false);
            }
        }
    }

    private void performQCounterAttack(Actor a) {
        finishQAbility(true);
        JsonNode counterAttackData = counterAttackData();
        a.addToDamageQueue(this, getPlayerStat("attackDamage") * 2, counterAttackData, false);
        String qCounterFX = SkinData.getRattleBallsQCounterFX(avatar);
        String qCounterSFX = SkinData.getRattleBallsQCounterSFX(avatar);
        ExtensionCommands.playSound(parentExt, room, id, qCounterSFX, location);
        ExtensionCommands.playSound(
                parentExt, room, id, "sfx_rattleballs_rattle_balls_2", location);
        ExtensionCommands.actorAnimate(parentExt, room, id, "crit", 1000, true);
        ExtensionCommands.createActorFX(
                parentExt, room, id, qCounterFX, 1500, id, true, "", true, false, team);
    }

    private void finishQAbility(boolean triggerPassive) {
        parryActive = false;
        qUses = 0;
        if (triggerPassive) activatePassive();
        int baseQCooldown = ChampionData.getBaseAbilityCooldown(this, 1);
        int cd = getReducedCooldown(baseQCooldown);
        ExtensionCommands.actorAbilityResponse(parentExt, player, "q", true, cd, 0);
        Runnable enableQCasting = () -> canCast[0] = true;
        scheduleTask(enableQCasting, cd);
    }

    private void endUlt() {
        ultActive = false;
        eCounter = 0;
        if (!dead) activatePassive();
        ExtensionCommands.removeFx(parentExt, room, id + "_ultRing");
        ExtensionCommands.removeFx(parentExt, room, id + "_ultSpin");
        ExtensionCommands.removeFx(parentExt, room, id + "_ultSparkles");
        int baseUltCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
        ExtensionCommands.actorAbilityResponse(
                parentExt, player, "e", true, getReducedCooldown(baseUltCooldown), 250);
        if (isStopped()) {
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
        } else {
            ExtensionCommands.actorAnimate(parentExt, room, id, "run", 100, false);
        }
    }

    private JsonNode counterAttackData() {
        JsonNode attackData = parentExt.getAttackData(avatar, "spell1");
        ObjectNode counterAttackData = attackData.deepCopy();
        counterAttackData.remove("spellType");
        counterAttackData.put("attackType", "physical");
        counterAttackData.put("counterAttack", true);
        return counterAttackData;
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
            if (qUses == 1) {
                Runnable flipDelay =
                        () -> {
                            canCast[0] = true;
                            qJumpActive = false;
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
                activatePassive();
                qThrustRectangle = null;
            }
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            int delay = getReducedCooldown(cooldown) - W_CAST_DELAY;
            scheduleTask(enableWCasting, delay);
            activatePassive();
            if (getHealth() > 0) {
                String wFX = SkinData.getRattleBallsWFX(avatar);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        wFX,
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

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class ChooseGoose extends UserActor {
    private boolean qActive = false;
    private final HashMap<Actor, Long> bleedingActors = new HashMap<>();
    private boolean jumpActive = false;
    private Point2D chestLocation = null;
    private Long lastQTick = System.currentTimeMillis();

    public ChooseGoose(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        // setStat("criticalChance", 100);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);

        if (chestLocation != null) {
            for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
                if (chestLocation.distance(ua.getLocation()) <= 1) {
                    ExtensionCommands.removeFx(parentExt, room, id + "_tChest");

                    int duration = 800;
                    Runnable removeFX =
                            () -> {
                                ExtensionCommands.removeFx(parentExt, room, id + "_tChestRing");
                                ExtensionCommands.removeFx(parentExt, room, id + "_tChestFX");
                            };
                    scheduleTask(removeFX, duration);

                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "treasure_chest_open",
                            id + "_chest_open",
                            duration,
                            (float) chestLocation.getX(),
                            (float) chestLocation.getY(),
                            false,
                            team,
                            0f);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "sfx_choosegoose_chest_open", chestLocation);

                    chestLocation = null;
                    break;
                }
            }
        }

        if (!bleedingActors.isEmpty()) {
            JsonNode spellData = parentExt.getAttackData(avatar, "spell1");
            double damage = getSpellDamage(spellData, false);

            Iterator<Map.Entry<Actor, Long>> iterator = bleedingActors.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Actor, Long> entry = iterator.next();
                Actor a = entry.getKey();
                Long bleedingStartTime = entry.getValue();

                if (System.currentTimeMillis() - lastQTick >= 500) {
                    lastQTick = System.currentTimeMillis();
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            a.getId(),
                            "neptr_passive",
                            1000,
                            a.getId() + Math.random(),
                            false,
                            "",
                            true,
                            false,
                            a.getTeam());

                    a.addToDamageQueue(this, damage, spellData, false);
                }

                if (System.currentTimeMillis() - bleedingStartTime >= 3000 || a.getHealth() <= 0) {
                    iterator.remove();
                }
            }
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
                canCast[0] = false;
                qActive = true;
                stopMoving();

                double asDelta = this.getStat("attackSpeed") * -0.2;
                this.addEffect("attackSpeed", asDelta, 5000);

                String q_sfx = "sfx_choosegoose_q_activation";
                ExtensionCommands.playSound(parentExt, room, id, q_sfx, location);

                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "iceKing_spellCasting_hand",
                        5000,
                        id + "_rHand",
                        true,
                        "mixamorig:RightHand",
                        true,
                        false,
                        team);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "iceKing_spellCasting_hand",
                        5000,
                        id + "_lHand",
                        true,
                        "mixamorig:LeftHand",
                        true,
                        false,
                        team);
                Champion.handleStatusIcon(parentExt, this, "icon_choosegoose_s1", "", 5000);

                int dur = parentExt.getAttackData(avatar, "spell1").get("spellDuration").asInt();
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), dur);
                break;
            case 2:
                canCast[1] = false;
                jumpActive = true;
                Point2D ogLocation = location;
                Point2D dPoint = dash(dest, true, 14d);
                double time = ogLocation.distance(dPoint) / 14d;
                int wTime = (int) (time * 1000);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell2a", wTime, false);
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_choosegoose_w_jump", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "neptr_pie_trail",
                        wTime,
                        id + "_wTrail",
                        true,
                        "mixamorig:Spine",
                        false,
                        false,
                        team);

                int cd = getReducedCooldown(cooldown);
                ExtensionCommands.actorAbilityResponse(parentExt, player, "w", true, cd, gCooldown);

                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dPoint), wTime);
                break;
            case 3:
                // canCast[2] = false;
                chestLocation = dest;
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "treasure_chest",
                        id + "_tChest",
                        10000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "billy_passive",
                        id + "_tChestFX",
                        10000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "fx_aggrorange_2",
                        id + "_tChestRing",
                        10000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        team,
                        0f);
                ExtensionCommands.playSound(parentExt, room, id, "vo/vo_choosegoose_e", dest);
                break;
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (jumpActive) return false;
        else return super.canUseAbility(ability);
    }

    @Override
    public void attack(Actor a) {
        if (attackCooldown == 0) {
            applyStopMovingDuringAttack();
            BasicAttack basicAttack = new BasicAttack(a, handleAttack(a));
            scheduleTask(basicAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (attackData.has("spellName")) {
            String sn = attackData.get("spellName").asText();
            if (sn.contains("spell_2")) {
                canCast[1] = true;
                ExtensionCommands.actorAbilityResponse(parentExt, player, "w", true, 0, 0);
            }
        }
    }

    private ChooseGooseAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new ChooseGooseAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class ChooseGooseAbilityRunnable extends AbilityRunnable {

        public ChooseGooseAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int cd = getReducedCooldown(cooldown);
            Runnable enableQCasting = () -> canCast[0] = true;
            scheduleTask(enableQCasting, cd);

            qActive = false;
            bleedingActors.clear();
            ExtensionCommands.actorAbilityResponse(parentExt, player, "q", true, cd, gCooldown);
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            scheduleTask(enableWCasting, getReducedCooldown(cooldown));
            jumpActive = false;

            ExtensionCommands.actorAnimate(parentExt, room, id, "spell2b", 500, false);
            ExtensionCommands.playSound(parentExt, room, id, "sfx_choosegoose_w_impact", location);

            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "finn_dash_whirlwind_fx",
                    id + "_dashImpactFX",
                    2000,
                    (float) location.getX(),
                    (float) location.getY(),
                    false,
                    team,
                    0f);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "fx_target_ring_2",
                    id + "_dashImpactRing",
                    500,
                    (float) location.getX(),
                    (float) location.getY(),
                    true,
                    team,
                    0f);

            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actors4 = Champion.getActorsInRadius(handler, location, 4f);

            double damage4 = getSpellDamage(spellData, true);

            for (Actor a : actors4) {
                if (isNonStructure(a)) {
                    a.addToDamageQueue(ChooseGoose.this, damage4, spellData, false);
                }
            }
        }

        @Override
        protected void spellE() {}

        @Override
        protected void spellPassive() {}
    }

    private class BasicAttack implements Runnable {

        Actor target;
        boolean crit;

        BasicAttack(Actor t, boolean crit) {
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (this.crit) {
                damage *= 2;
                damage = handleGrassSwordProc(damage);
            }
            if (qActive) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_choosegoose_q_hit", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "magicman_snake_explosion",
                        1000,
                        target.getId() + Math.random(),
                        false,
                        "",
                        true,
                        false,
                        target.getTeam());
                if (!bleedingActors.containsKey(target) && isNonStructure(target)) {
                    bleedingActors.put(target, System.currentTimeMillis());
                }
            }
            new Champion.DelayedAttack(
                            parentExt, ChooseGoose.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

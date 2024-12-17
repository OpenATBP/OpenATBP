package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class IceKing extends UserActor {
    public static final int PASSIVE_TIME = 10000;
    public static final int PASSIVE_SLOW_DURAITON = 2000;
    public static final double PASSIVE_SLOW_VALUE = 0.25d;
    public static final int PASSIVE_AS_DEBUFF_TIME = 2000;
    public static final double PASSIVE_AS_DEBUFF_VALUE = 0.33d;
    public static final int PASSIVE_COOLDOWN = 5000;
    public static final int Q_CAST_DELAY = 250;
    public static final int Q_ROOT_DURATION = 1750;
    public static final int W_WHIRLWIND_CD = 2000;
    public static final int W_DURATION = 3000;
    public static final int E_DURATION = 6000;
    private boolean iceShield = false;
    private long lastAbilityUsed;
    private Actor qVictim = null;
    private long qHitTime = -1;
    private Map<String, Long> lastWHit;
    private Point2D wLocation = null;
    private boolean ultActive = false;
    private Point2D ultLocation = null;
    private long wStartTime = 0;
    private long ultStartTime = 0;
    private long lasWhirlwindTime = 0;

    private enum AssetBundle {
        NORMAL,
        FLIGHT
    }

    private AssetBundle bundle = AssetBundle.NORMAL;

    public IceKing(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.lastAbilityUsed = System.currentTimeMillis();
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultActive && this.ultLocation != null) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actorsInUlt = Champion.getActorsInRadius(handler, this.ultLocation, 5.5f);
            boolean containsIceKing = actorsInUlt.contains(this);
            if (containsIceKing && this.bundle == AssetBundle.NORMAL) {
                this.bundle = AssetBundle.FLIGHT;
                if (!this.getState(ActorState.POLYMORPH)) {
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, getFlightAssetbundle());
                }
                if (System.currentTimeMillis() - this.lasWhirlwindTime >= W_WHIRLWIND_CD) {
                    this.lasWhirlwindTime = System.currentTimeMillis();
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "ice_king_whirlwind",
                            2500,
                            this.id + "_whirlWind" + Math.random(),
                            true,
                            "",
                            true,
                            false,
                            this.team);
                }
            } else if (!containsIceKing && this.bundle == AssetBundle.FLIGHT) {
                this.bundle = AssetBundle.NORMAL;
                if (!this.getState(ActorState.POLYMORPH)) {
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, getSkinAssetBundle());
                }
            }

            if (!actorsInUlt.isEmpty()) {
                for (Actor a : actorsInUlt) {
                    if (a.equals(this)) {
                        this.addEffect("speed", this.getStat("speed") * 0.9, 150);
                        this.updateStatMenu("speed");
                    } else if (a.getTeam() != this.team && a.getActorType() != ActorType.BASE) {
                        JsonNode spellData = this.parentExt.getAttackData("iceking", "spell3");
                        a.addToDamageQueue(
                                this, getSpellDamage(spellData, false) / 10d, spellData, true);
                    }
                }
            }
        }

        if (this.wLocation != null && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            this.lastWHit = null;
            this.wLocation = null;
        }
        if (this.ultActive && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            this.ultLocation = null;
            this.ultActive = false;
            this.bundle = AssetBundle.NORMAL;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            this.updateStatMenu("speed");
        }

        if (System.currentTimeMillis() - lastAbilityUsed > PASSIVE_TIME) {
            if (!this.iceShield) {
                this.iceShield = true;
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "ice_king_frost_shield",
                        1000 * 60 * 15,
                        this.id + "_iceShield",
                        true,
                        "Bip001 Pelvis",
                        true,
                        false,
                        this.team);
            }
        }

        if (this.qVictim != null) {
            if (System.currentTimeMillis() < qHitTime) {
                JsonNode spellData = parentExt.getAttackData("iceking", "spell1");
                this.qVictim.addToDamageQueue(
                        this, getSpellDamage(spellData, false) / 10d, spellData, true);
            } else {
                this.qVictim = null;
                this.qHitTime = -1;
            }
        }

        if (this.wLocation != null) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.wLocation, 3f)) {
                if (this.isNonStructure(a)) {
                    if (this.lastWHit != null && this.lastWHit.containsKey(a.getId())) {
                        if (System.currentTimeMillis() >= this.lastWHit.get(a.getId()) + 500) {
                            JsonNode spellData = this.parentExt.getAttackData("iceking", "spell2");
                            a.addToDamageQueue(
                                    this, getSpellDamage(spellData, false) / 2f, spellData, true);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    a.getId(),
                                    "fx_ice_explosion1",
                                    1000,
                                    a.getId() + "_" + Math.random(),
                                    true,
                                    "",
                                    true,
                                    false,
                                    this.team);
                            this.lastWHit.put(a.getId(), System.currentTimeMillis());
                        }
                    } else if (this.lastWHit != null && !this.lastWHit.containsKey(a.getId())) {
                        JsonNode spellData = this.parentExt.getAttackData("iceking", "spell2");
                        a.addToDamageQueue(
                                this, getSpellDamage(spellData, false) / 2f, spellData, true);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                a.getId(),
                                "fx_ice_explosion1",
                                1000,
                                a.getId() + "_" + Math.random(),
                                true,
                                "",
                                true,
                                false,
                                this.team);
                        this.lastWHit.put(a.getId(), System.currentTimeMillis());
                    }
                }
            }
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            applyStopMovingDuringAttack();
            String emit = "Bip01";
            String projectile = "iceKing_projectile";
            if (this.bundle == AssetBundle.FLIGHT) emit = "Bip001 Neck";
            BasicAttack basicAttack = new BasicAttack(this, a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, basicAttack, projectile, emit);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void handleSwapFromPoly() {
        String bundle =
                this.bundle == AssetBundle.FLIGHT && this.ultActive
                        ? getFlightAssetbundle()
                        : getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (attackData.has("attackName")
                && attackData.get("attackName").asText().contains("basic_attack")
                && this.iceShield) {
            damage /= 2;
            a.addState(ActorState.SLOWED, PASSIVE_SLOW_VALUE, PASSIVE_SLOW_DURAITON);
            a.addEffect(
                    "attackSpeed",
                    a.getStat("attackSpeed") * PASSIVE_AS_DEBUFF_VALUE,
                    PASSIVE_AS_DEBUFF_TIME);
            this.iceShield = false;
            this.lastAbilityUsed = System.currentTimeMillis() + 5000;
            Runnable handlePassiveCooldown =
                    () -> this.lastAbilityUsed = System.currentTimeMillis();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_iceShield");
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "passive", true, PASSIVE_COOLDOWN, 0);
            scheduleTask(handlePassiveCooldown, PASSIVE_COOLDOWN);
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        if (System.currentTimeMillis() > this.lastAbilityUsed)
            this.lastAbilityUsed = System.currentTimeMillis();
        switch (ability) {
            case 1:
                stopMoving();
                this.canCast[0] = false;
                String freezeVO = SkinData.getIceKingQVO(avatar);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, freezeVO, this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    stopMoving();
                    this.wStartTime = System.currentTimeMillis();
                    this.wLocation = dest;
                    this.lastWHit = new HashMap<>();
                    String hailStormVO = SkinData.getIceKingWVO(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, "", "sfx_ice_king_hailstorm", dest);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, hailStormVO, this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "ice_king_spell_casting_hand",
                            1000,
                            this.id + "_lHand",
                            true,
                            "Bip001 L Hand",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "ice_king_spell_casting_hand",
                            1000,
                            this.id + "_rHand",
                            true,
                            "Bip001 R Hand",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "AoE_iceking_snowballs",
                            this.id + "_snowBalls",
                            W_DURATION,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            false,
                            this.team,
                            0f);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_3",
                            this.id + "_wRing",
                            W_DURATION,
                            (float) dest.getX(),
                            (float) dest.getY(),
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
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    stopMoving(castDelay);
                    this.ultActive = true;
                    this.ultLocation = this.location;
                    this.ultStartTime = System.currentTimeMillis();
                    String ultimateVO = SkinData.getIceKingEVO(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_ice_king_ultimate",
                            this.location);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, ultimateVO, this.location);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "idle", 100, true);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_5.5",
                            this.id + "eRing",
                            E_DURATION,
                            (float) this.location.getX(),
                            (float) this.location.getY(),
                            true,
                            this.team,
                            0f);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "iceKing_freezeGround",
                            this.id + "_ultFreeze",
                            E_DURATION,
                            (float) this.location.getX(),
                            (float) this.location.getY(),
                            false,
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
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
        }
    }

    private String getFlightAssetbundle() {
        return this.avatar.contains("queen")
                ? "iceking2_icequeen2"
                : this.avatar.contains("young") ? "iceking2_young2" : "iceking2";
    }

    private class IceKingProjectile extends Projectile {

        public IceKingProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            List<Actor> eligibleActors =
                    roomHandler.getEligibleActors(team, true, true, false, true);
            for (Actor a : eligibleActors) {
                double collisionRadius =
                        parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                if (a.getLocation().distance(location) <= hitbox + collisionRadius
                        && !a.getAvatar().equalsIgnoreCase("neptr_mine")) {
                    return a;
                }
            }
            return null;
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData("iceking", "spell1");
            qVictim = victim;
            qHitTime = System.currentTimeMillis() + 1750;
            victim.addToDamageQueue(
                    IceKing.this, getSpellDamage(spellData, true), spellData, false);
            victim.addState(ActorState.ROOTED, 0d, Q_ROOT_DURATION, "iceKing_snare", "");
            ExtensionCommands.playSound(
                    this.parentExt,
                    victim.getRoom(),
                    victim.getId(),
                    "sfx_ice_king_freeze",
                    victim.getLocation());
            this.destroy();
        }
    }

    private IceKingAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new IceKingAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class IceKingAbilityRunnable extends AbilityRunnable {

        public IceKingAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - Q_CAST_DELAY;
            scheduleTask(enableQCasting, delay);
            if (getHealth() > 0) {
                Line2D abilityLine = Champion.getAbilityLine(location, dest, 7.5f);
                fireProjectile(
                        new IceKingProjectile(
                                parentExt,
                                IceKing.this,
                                abilityLine,
                                9f,
                                0.5f,
                                "projectile_iceking_deepfreeze"),
                        location,
                        dest,
                        7.5f);
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }

    private class BasicAttack implements Runnable {
        Actor attacker;
        Actor target;
        boolean crit;

        BasicAttack(Actor a, Actor t, boolean crit) {
            this.attacker = a;
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = this.attacker.getPlayerStat("attackDamage");
            if (crit) {
                damage *= 2;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(parentExt, attacker, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

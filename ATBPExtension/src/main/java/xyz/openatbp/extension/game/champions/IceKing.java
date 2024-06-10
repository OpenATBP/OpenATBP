package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class IceKing extends UserActor {
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
            List<Actor> actorsInUlt =
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.ultLocation,
                            5.5f);
            boolean containsIceKing = actorsInUlt.contains(this);
            if (containsIceKing && this.bundle == AssetBundle.NORMAL) {
                this.bundle = AssetBundle.FLIGHT;
                if (!this.getState(ActorState.POLYMORPH)) {
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, getFlightAssetbundle());
                }
                if (System.currentTimeMillis() - this.lasWhirlwindTime >= 2000) {
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
                        this.addEffect("speed", this.getStat("speed"), 150);
                        this.updateStatMenu("speed");
                    } else if (a.getTeam() != this.team && a.getActorType() != ActorType.BASE) {
                        JsonNode spellData = this.parentExt.getAttackData("iceking", "spell3");
                        a.addToDamageQueue(this, getSpellDamage(spellData) / 10d, spellData, true);
                    }
                }
            }
        }

        if (this.wLocation != null && System.currentTimeMillis() - this.wStartTime >= 3000) {
            this.lastWHit = null;
            this.wLocation = null;
        }
        if (this.ultActive && System.currentTimeMillis() - this.ultStartTime >= 6000) {
            this.ultLocation = null;
            this.ultActive = false;
            this.bundle = AssetBundle.NORMAL;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            this.updateStatMenu("speed");
        }

        if (System.currentTimeMillis() - lastAbilityUsed > 10000) {
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
                        this, getSpellDamage(spellData) / 10d, spellData, true);
            } else {
                this.qVictim = null;
                this.qHitTime = -1;
            }
        }

        if (this.wLocation != null) {
            for (Actor a :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.wLocation,
                            3f)) {
                if (this.isNonStructure(a)) {
                    if (this.lastWHit != null && this.lastWHit.containsKey(a.getId())) {
                        if (System.currentTimeMillis() >= this.lastWHit.get(a.getId()) + 500) {
                            JsonNode spellData = this.parentExt.getAttackData("iceking", "spell2");
                            a.addToDamageQueue(
                                    this, getSpellDamage(spellData) / 2f, spellData, true);
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
                        a.addToDamageQueue(this, getSpellDamage(spellData) / 2f, spellData, true);
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
            a.addState(ActorState.SLOWED, 0.25d, 2000);
            a.addEffect("attackSpeed", a.getStat("attackSpeed") * 0.33d, 2000);
            this.iceShield = false;
            this.lastAbilityUsed = System.currentTimeMillis() + 5000;
            Runnable handlePassiveCooldown =
                    () -> this.lastAbilityUsed = System.currentTimeMillis();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_iceShield");
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "passive", true, 5000, 0);
            parentExt
                    .getTaskScheduler()
                    .schedule(handlePassiveCooldown, 5000, TimeUnit.MILLISECONDS);
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
        super.useAbility(ability, spellData, cooldown, gCooldown, castDelay, dest);
        if (System.currentTimeMillis() > this.lastAbilityUsed)
            this.lastAbilityUsed = System.currentTimeMillis();
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                String freezeSfx =
                        (this.avatar.contains("queen"))
                                ? "vo/vo_ice_queen_freeze"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_ice_king_young_freeze"
                                        : "vo/vo_ice_king_freeze";
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, freezeSfx, this.location);
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
                                new IceKingAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.wStartTime = System.currentTimeMillis();
                this.wLocation = dest;
                this.lastWHit = new HashMap<>();
                String hailStormSfx =
                        (this.avatar.contains("queen"))
                                ? "vo/vo_ice_queen_hailstorm"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_ice_king_young_hailstorm"
                                        : "vo/vo_ice_king_hailstorm";
                ExtensionCommands.playSound(
                        this.parentExt, this.room, "", "sfx_ice_king_hailstorm", dest);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, hailStormSfx, this.location);
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
                        3000,
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
                        3000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
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
                                new IceKingAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                this.ultActive = true;
                this.ultLocation = this.location;
                this.ultStartTime = System.currentTimeMillis();
                String ultimateSfx =
                        (this.avatar.contains("queen"))
                                ? "vo/vo_ice_queen_ultimate"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_ice_king_young_ultimate"
                                        : "vo/vo_ice_king_ultimate";
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_ice_king_ultimate", this.location);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, ultimateSfx, this.location);
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "idle", 100, true);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_5.5",
                        this.id + "eRing",
                        6000,
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
                        6000,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        false,
                        this.team,
                        0f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new IceKingAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
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
            List<Actor> teammates = this.getTeammates(roomHandler);
            for (Actor a : roomHandler.getActors()) {
                if (a.getActorType() != ActorType.TOWER
                        && !teammates.contains(a)
                        && a.getTeam() != IceKing.this.team) {
                    double collisionRadius =
                            parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                    if (a.getLocation().distance(location) <= hitbox + collisionRadius
                            && !a.getAvatar().equalsIgnoreCase("neptr_mine")) {
                        return a;
                    }
                }
            }
            return null;
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData("iceking", "spell1");
            qVictim = victim;
            qHitTime = System.currentTimeMillis() + 1750;
            victim.addToDamageQueue(IceKing.this, getSpellDamage(spellData), spellData, false);
            victim.addState(ActorState.ROOTED, 0d, 1750, "iceKing_snare", "");
            ExtensionCommands.playSound(
                    this.parentExt,
                    victim.getRoom(),
                    victim.getId(),
                    "sfx_ice_king_freeze",
                    victim.getLocation());
            this.destroy();
        }
    }

    private class IceKingAbilityHandler extends AbilityRunnable {

        public IceKingAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int Q_CAST_DELAY = 250;
            Runnable enableQCasting = () -> canCast[0] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableQCasting,
                            getReducedCooldown(cooldown) - Q_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
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
}

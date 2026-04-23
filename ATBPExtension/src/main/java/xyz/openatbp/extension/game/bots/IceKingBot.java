package xyz.openatbp.extension.game.bots;

import static xyz.openatbp.extension.game.champions.IceKing.*;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.actors.Tower;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public class IceKingBot extends Bot {
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

    private static final double LOW_HP_ACTION_PHEALTH = 0.3;
    private static final int SOLO_JUNGLE_LV = 5;
    private static final double SOLO_JUNGLE_PHEALTH = 0.85;
    private static final double DUO_JUNGLE_PHEALTH = 0.45;
    private static final double TRIO_JUNGLE_PHEALTH = 0.35;
    private static final float FLEE_MINIONS_ATTACKED_PHP_PER_LV = 0.04f;
    private static final float DEF_ALTAR_CAPTURE_ACTION_DIST = 3f;
    private static final double PLAYER_ATTACKED_LV_DIF = -1;
    private static final double JUNGLING_ALLIES_RADIUS = 7;
    private static final float AGGRO_RANGE = 5f;
    private static final float FLEE_FROM_FIGHT_PHEALTH = 0.6f;
    private static final float FLEE_FARM_MINIONS_PHEALTH = 0.4f;

    private enum AssetBundle {
        NORMAL,
        FLIGHT
    }

    private AssetBundle bundle = AssetBundle.NORMAL;

    // TODO: CHECK THIS VALUE
    private int MAX_W_CAST_DISTANCE = 5;

    public IceKingBot(
            ATBPExtension parentExt,
            Room room,
            int botId,
            String avatar,
            String displayName,
            int team,
            BotMapConfig mapConfig) {
        super(parentExt, room, botId, avatar, displayName, team, mapConfig);

        this.qCooldownMs = 10000;
        this.wCooldownMs = 12000;
        this.eCooldownMs = 70000;

        this.qGCooldownMs = 250;
        this.wGCooldownMs = 250;
        this.eGCooldownMs = 250;

        this.qCastDelayMS = 250;
        this.wCastDelayMS = 250;
        this.eCastDelayMS = 250;

        this.hasCustomSwapFromPoly = true;

        this.botRole = BotRole.LANE_PUSHER;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        handleESpeedRemoval();
        handleUlt();
        handleUpdatePassive();
        handleWUpdateEnd();
        handleUpdateEEnd();
        handleUpdateQ();
        handleUpdateW();
    }

    private void handleESpeedRemoval() {
        if ((ultLocation == null || ultLocation.distance(location) > E_RADIUS)
                && effectManager.hasEffect(id + "_iceking_e_speed")) {
            effectManager.removeAllEffectsById(id + "_iceking_e_speed");
        }
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        this.target = a;
        if (this.attackCooldown == 0) {
            applyStopMovingDuringAttack();
            String emit = "Bip01";
            String projectile = "iceKing_projectile";
            if (this.bundle == AssetBundle.FLIGHT) emit = "Bip001 Neck";
            BasicAttack basicAttack = new BasicAttack(this, a, handleAttack(a));
            UserActor.RangedAttack rangedAttack =
                    new UserActor.RangedAttack(a, basicAttack, projectile, emit);
            parentExt
                    .getTaskScheduler()
                    .schedule(rangedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void customSwapFromPoly() {
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
            a.getEffectManager()
                    .addState(
                            ActorState.SLOWED,
                            id + "_ice_king_passive_slow",
                            PASSIVE_SLOW_PERCENT,
                            PASSIVE_SLOW_DURAITON);

            a.getEffectManager()
                    .addEffect(
                            a.getId() + "_iceking_passive_debuff",
                            "attackSpeed",
                            PASSIVE_AS_DEBUFF_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.DEBUFF,
                            PASSIVE_AS_DEBUFF_TIME);

            this.iceShield = false;
            this.lastAbilityUsed = System.currentTimeMillis() + 5000;
            Runnable handlePassiveCooldown =
                    () -> this.lastAbilityUsed = System.currentTimeMillis();
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_iceShield");
            parentExt
                    .getTaskScheduler()
                    .schedule(handlePassiveCooldown, PASSIVE_COOLDOWN, TimeUnit.MILLISECONDS);
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void handleFightingAbilities() {

        if (target != null && canAttack()) {

            if (target.getActorType() == ActorType.TOWER) return;

            if (canUseQ() && target.getLocation().distance(location) <= Q_RANGE) {
                faceTarget(target);
                useQ(target.getLocation());
            }

            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            List<Actor> enemies =
                    Champion.getEnemyActorsInRadius(rh, team, target.getLocation(), W_RADIUS);

            if (canUseW()
                    && (target instanceof UserActor || enemies.size() > 1)
                    && target.getLocation().distance(location) <= MAX_W_CAST_DISTANCE) {
                faceTarget(target);
                useW(target.getLocation());
            }

            if (canUseE()) useE(target.getLocation());
        }
    }

    @Override
    public void handleRetreatAbilities() {
        if (canUseE() && canAttack()) useE(location);
    }

    private void handleLastAbilityVar() {
        if (System.currentTimeMillis() > this.lastAbilityUsed) {
            this.lastAbilityUsed = System.currentTimeMillis();
        }
    }

    @Override
    public boolean canUseQ() {
        if (!defaultAbilityCheck(1) || target == null) return false;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, Q_RANGE);
        enemies.removeIf(a -> a instanceof Tower);

        AbilityShape qShape =
                AbilityShape.createRectangle(location, target.getLocation(), Q_RANGE, 0.5f);

        boolean isTargetOnlyActorInQ = true;
        for (Actor a : enemies) {
            JsonNode actorData = parentExt.getActorData(a.getAvatar());
            if (actorData != null && actorData.has("collisionRadius")) {
                double radius = actorData.get("collisionRadius").asDouble();
                if (qShape.contains(a.getLocation(), radius) && a != target)
                    isTargetOnlyActorInQ = false;
            }
        }
        return isTargetOnlyActorInQ;
    }

    @Override
    public boolean canUseW() {
        return defaultAbilityCheck(2);
    }

    @Override
    public boolean canUseE() {
        if (!defaultAbilityCheck(3)) return false;

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, E_RADIUS);
        enemies.removeIf(a -> a instanceof Tower);

        if (getPHealth() <= 0.15) {
            return true;
        }
        for (Actor a : enemies) {
            if (a instanceof UserActor && a.getHealth() > 0) {
                return a.hasMovementCC();
            }
        }

        return false;
    }

    @Override
    public void useQ(Point2D destination) {
        lastQUse = System.currentTimeMillis();
        globalCooldown = qCastDelayMS;

        stopMoving();
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "spell1", 100, true);
        handleLastAbilityVar();
        String freezeVO = SkinData.getIceKingQVO(avatar);
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, freezeVO, this.location);

        JsonNode spellData = parentExt.getAttackData("iceking", "spell1");

        IKBotAbilityRunnable r =
                new IKBotAbilityRunnable(
                        this, 1, spellData, qCooldownMs, qGCooldownMs, destination);
        scheduleTask(r, qCastDelayMS);
    }

    @Override
    public void useW(Point2D destination) {
        handleLastAbilityVar();
        lastWUse = System.currentTimeMillis();
        globalCooldown = wCastDelayMS;

        stopMoving();
        this.wStartTime = System.currentTimeMillis();
        this.wLocation = destination;
        this.lastWHit = new HashMap<>();
        String hailStormVO = SkinData.getIceKingWVO("iceking");
        ExtensionCommands.playSound(
                this.parentExt, this.room, "", "sfx_ice_king_hailstorm", destination);
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, hailStormVO, this.location);
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
                (float) destination.getX(),
                (float) destination.getY(),
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
                (float) destination.getX(),
                (float) destination.getY(),
                true,
                this.team,
                0f);

        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "spell2", 100, true);

        JsonNode spellData = parentExt.getAttackData("iceking", "spell2");
        IKBotAbilityRunnable r =
                new IKBotAbilityRunnable(
                        this, 2, spellData, wCooldownMs, wGCooldownMs, destination);
        scheduleTask(r, wCooldownMs);
    }

    @Override
    public void useE(Point2D destination) {
        handleLastAbilityVar();
        lastEUse = System.currentTimeMillis();
        globalCooldown = eCastDelayMS;

        stopMoving(qCastDelayMS);
        this.ultActive = true;
        this.ultLocation = this.location;
        this.ultStartTime = System.currentTimeMillis();
        String ultimateVO = SkinData.getIceKingEVO("iceking");
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_ice_king_ultimate", this.location);
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, ultimateVO, this.location);
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, true);
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

        JsonNode spellData = parentExt.getAttackData("iceking", "spell3");
        IKBotAbilityRunnable r =
                new IKBotAbilityRunnable(
                        this, 3, spellData, eCooldownMs, eGCooldownMs, destination);
        scheduleTask(r, eCooldownMs);
    }

    private String getFlightAssetbundle() {
        return this.avatar.contains("queen")
                ? "iceking2_icequeen2"
                : this.avatar.contains("young") ? "iceking2_young2" : "iceking2";
    }

    public class IceKingBotProjectile extends Projectile {
        Actor caster;

        public IceKingBotProjectile(
                ATBPExtension parentExt,
                Actor caster,
                Line2D path,
                float speed,
                float offsetDistance,
                String id) {
            super(parentExt, caster, path, speed, offsetDistance, offsetDistance, id);
            this.caster = caster;
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData("iceking", "spell1");
            qVictim = victim;
            qHitTime = System.currentTimeMillis() + 1750;
            victim.addToDamageQueue(caster, getSpellDamage(spellData), spellData, false);

            String id = victim.getId() + "_iceKing_freeze";
            victim.getEffectManager()
                    .addState(
                            ActorState.ROOTED,
                            id + "_ice_king_q_root",
                            0,
                            "iceKing_snare",
                            Q_ROOT_DURATION,
                            id,
                            "");
            ExtensionCommands.playSound(
                    this.parentExt,
                    victim.getRoom(),
                    victim.getId(),
                    "sfx_ice_king_freeze",
                    victim.getLocation());
            this.destroy();
        }
    }

    private class IKBotAbilityRunnable extends AbilityRunnable {
        Actor caster;

        public IKBotAbilityRunnable(
                Actor caster,
                int ability,
                JsonNode spellData,
                int cooldown,
                int gCooldown,
                Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
            this.caster = caster;
        }

        @Override
        protected void spellQ() {
            if (getHealth() > 0) {
                Line2D abilityLine = Champion.createLineTowards(location, dest, 7.5f);
                IceKingBotProjectile p =
                        new IceKingBotProjectile(
                                parentExt,
                                caster,
                                abilityLine,
                                9f,
                                0.5f,
                                "projectile_iceking_deepfreeze");
                fireProjectile(p, location, dest, 7.5f);
            }
        }

        @Override
        protected void spellW() {}

        @Override
        protected void spellE() {}

        @Override
        protected void spellPassive() {}
    }

    @Override
    public void levelUpStats() {
        switch (level) {
            case 1:
                setStat("attackDamage", 42);
                setStat("armor", 11);
                setStat("attackSpeed", 1235);
                setStat("spellDamage", 63);
                setStat("spellResist", 16);
                setStat("healthRegen", 3);
                setHealth(getHealth(), 375);
                break;
            case 2:
                setStat("attackDamage", 44);
                setStat("armor", 12);
                setStat("attackSpeed", 1220);
                setStat("spellDamage", 66);
                setStat("spellResist", 17);
                setHealth(getHealth(), 400);
                setStat("healthRegen", 4);
                break;
            case 3:
                setStat("attackDamage", 46);
                setStat("armor", 13);
                setStat("attackSpeed", 1205);
                setStat("spellDamage", 69);
                setStat("spellResist", 18);
                setHealth(getHealth(), 425);
                setStat("healthRegen", 5);
                break;
            case 4:
                setStat("attackDamage", 48);
                setStat("armor", 14);
                setStat("attackSpeed", 1190);
                setStat("spellDamage", 72);
                setStat("spellResist", 19);
                setHealth(getHealth(), 450);
                setStat("healthRegen", 6);
                break;
            case 5:
                setStat("attackDamage", 50);
                setStat("armor", 15);
                setStat("attackSpeed", 1175);
                setStat("spellDamage", 75);
                setStat("spellResist", 20);
                setHealth(getHealth(), 475);
                setStat("healthRegen", 7);
                break;
            case 6:
                setStat("attackDamage", 52);
                setStat("armor", 16);
                setStat("attackSpeed", 1160);
                setStat("spellDamage", 78);
                setStat("spellResist", 21);
                setHealth(getHealth(), 500);
                setStat("healthRegen", 8);
                break;
            case 7:
                setStat("attackDamage", 54);
                setStat("armor", 17);
                setStat("attackSpeed", 1145);
                setStat("spellDamage", 81);
                setStat("spellResist", 22);
                setHealth(getHealth(), 525);
                setStat("healthRegen", 9);
                break;
            case 8:
                setStat("attackDamage", 56);
                setStat("armor", 18);
                setStat("attackSpeed", 1130);
                setStat("spellDamage", 84);
                setStat("spellResist", 23);
                setHealth(getHealth(), 550);
                setStat("healthRegen", 10);
                break;
            case 9:
                setStat("attackDamage", 58);
                setStat("armor", 19);
                setStat("attackSpeed", 1115);
                setStat("spellDamage", 87);
                setStat("spellResist", 24);
                setHealth(getHealth(), 575);
                setStat("healthRegen", 11);
                break;
            case 10:
                setStat("attackDamage", 60);
                setStat("armor", 20);
                setStat("attackSpeed", 1100);
                setStat("spellDamage", 90);
                setStat("spellResist", 25);
                setHealth(getHealth(), 600);
                setStat("healthRegen", 12);
                break;
        }
    }

    public void handleUlt() {
        if (this.ultActive && this.ultLocation != null) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actorsInUlt = Champion.getActorsInRadius(handler, this.ultLocation, 5.5f);
            boolean containsIceKing = actorsInUlt.contains(this);
            if (containsIceKing && this.bundle == AssetBundle.NORMAL) {
                this.bundle = AssetBundle.FLIGHT;

                if (!effectManager.hasState(ActorState.POLYMORPH)) {
                    ExtensionCommands.swapActorAsset(parentExt, room, id, getFlightAssetbundle());
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
                if (!effectManager.hasState(ActorState.POLYMORPH)) {
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, getSkinAssetBundle());
                }
            }

            if (!actorsInUlt.isEmpty()) {
                for (Actor a : actorsInUlt) {
                    if (a.equals(this) && !effectManager.hasEffect(id + "_iceking_e_speed")) {
                        effectManager.addEffect(
                                id + "_iceking_e_speed",
                                "speed",
                                E_SPEED_PERCENT,
                                ModifierType.MULTIPLICATIVE,
                                ModifierIntent.BUFF,
                                E_DURATION);

                    } else if (isNonStructureEnemy(a) && a.isNotLeaping()) {
                        JsonNode spellData = this.parentExt.getAttackData("iceking", "spell3");
                        double dmg = getSpellDamage(spellData) / 10d;
                        a.addToDamageQueue(this, dmg, spellData, true);
                    }
                }
            }
        }
    }

    void handleWUpdateEnd() {
        if (this.wLocation != null && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            this.lastWHit = null;
            this.wLocation = null;
        }
    }

    void handleUpdateEEnd() {
        if (this.ultActive && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            this.ultLocation = null;
            this.ultActive = false;
            this.bundle = AssetBundle.NORMAL;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }
    }

    void handleUpdatePassive() {
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
    }

    void handleUpdateQ() {
        if (this.qVictim != null && qVictim.isNotLeaping()) {
            if (System.currentTimeMillis() < qHitTime) {
                JsonNode spellData = parentExt.getAttackData("iceking", "spell1");
                double dmg = getSpellDamage(spellData) / 10d;
                this.qVictim.addToDamageQueue(this, dmg, spellData, true);
            } else {
                this.qVictim = null;
                this.qHitTime = -1;
            }
        }
    }

    void handleUpdateW() {
        if (this.wLocation != null) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.wLocation, W_RADIUS)) {
                if (isNonStructureEnemy(a) && a.isNotLeaping()) {
                    if (this.lastWHit != null && this.lastWHit.containsKey(a.getId())) {
                        if (System.currentTimeMillis() >= this.lastWHit.get(a.getId()) + 500) {
                            JsonNode spellData = this.parentExt.getAttackData("iceking", "spell2");
                            double dmg = getSpellDamage(spellData) / 2f;
                            a.addToDamageQueue(this, dmg, spellData, true);
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
                    } else if (this.lastWHit != null
                            && !this.lastWHit.containsKey(a.getId())
                            && a.isNotLeaping()) {
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
                damage *= 1.25;
            }
            new Champion.DelayedAttack(parentExt, attacker, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
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

public class BMO extends UserActor {
    private static final int PASSIVE_SLOW_DURATION = 2500;
    private static final double PASSIVE_SLOW_PERCENT = 0.5f;
    private static final int Q_BLIND_DURATION = 2500;
    private static final float Q_OFFSET_DISTANCE_BOTTOM = 1.5f;
    private static final float Q_OFFSET_DISTANCE_TOP = 4f;
    private static final float Q_SPELL_RANGE = 6f;
    private static final int W_DURATION = 3000;
    private static final int W_RECAST_DELAY = 500;
    private static final float W_ARMOR_PERCENT = 0.2f;
    private static final float W_SHIELDS_PERCENT = 0.5f;
    public static final int W_STUN_DURATION = 1000;
    private static final int E_CAST_DELAY = 250;
    private static final int E_RANGE = 16;

    private final String W_ARMOR_BUFF_ID;
    private final String W_SHIELDS_BUFF_ID;

    private int passiveStacks = 0;
    private boolean wActive = false;
    private long wStartTime = 0;
    private long lastWSound = 0;
    private boolean ultSlowActive = false;
    private boolean passiveFxRemoved = false;

    private Map<Actor, Long> actorsWithPassiveSlow = new HashMap<>();

    public BMO(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        W_ARMOR_BUFF_ID = id + "_bmo_w_armor";
        W_SHIELDS_BUFF_ID = id + "_bmo_w_shields";
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (wActive && this.currentHealth <= 0) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
            if (passiveStacks < 3) addPasiveStacks();
            int baseWCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "w", true, getReducedCooldown(baseWCooldown), 250);
            this.canCast[0] = true;
            this.canCast[2] = true;
            this.wActive = false;
        }
        if (this.wActive && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            int baseWCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            this.wEnd(getReducedCooldown(baseWCooldown), 250);
            this.canCast[0] = true;
            this.canCast[2] = true;
            this.wActive = false;
        }
        if (wActive) {
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
            List<Actor> targets = Champion.getEnemyActorsInRadius(handler, team, location, 4f);
            JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
            double damage = (double) getSpellDamage(spellData, false) / 10d;
            for (Actor a : targets) {
                if (isNeitherStructureNorAlly(a) && passiveStacks == 3) {
                    applySlow(a);
                }

                if (isNeitherTowerNorAlly(a)) {
                    a.addToDamageQueue(this, damage, spellData, true);
                }
            }
            if (System.currentTimeMillis() - lastWSound >= 500) {
                lastWSound = System.currentTimeMillis();
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_bmo_pixels_shoot1", this.location);
            }
        }
        if (wActive && this.hasInterrupingCC()) {
            interrputW();
            this.wActive = false;
        }
    }

    @Override
    public void respawn() {
        super.respawn();
        if (passiveFxRemoved && passiveStacks == 3) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    SkinData.getBMOPassiveFX(avatar),
                    1000 * 60 * 15,
                    id + "_bmo_p_fx",
                    true,
                    "",
                    false,
                    false,
                    team);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (passiveStacks == 3) {
            ExtensionCommands.removeFx(parentExt, room, id + "_bmo_p_fx");
            passiveFxRemoved = true;
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            String projectileFx = SkinData.getBMOBasicAttackProjectile(avatar);
            BMOPassive passiveAttack = new BMOPassive(a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectileFx);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    private void removeWBuffs() {
        effectManager.removeAllEffectsById(W_ARMOR_BUFF_ID);
        effectManager.removeAllEffectsById(W_SHIELDS_BUFF_ID);
    }

    @Override
    public boolean canAttack() {
        return !wActive && super.canAttack();
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
                    AbilityShape qTrapezoid =
                            AbilityShape.createTrapezoid(
                                    location,
                                    dest,
                                    Q_SPELL_RANGE,
                                    Q_OFFSET_DISTANCE_BOTTOM,
                                    Q_OFFSET_DISTANCE_TOP);

                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());

                    List<Actor> nearbyEnemies =
                            Champion.getEnemyActorsInRadius(handler, team, location, 9f);
                    if (!nearbyEnemies.isEmpty() && getHealth() > 0) {
                        for (Actor a : nearbyEnemies) {
                            if (isNeitherStructureNorAlly(a)
                                    && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                                    && a.isNotLeaping()) {
                                a.getEffectManager()
                                        .addState(
                                                ActorState.BLINDED,
                                                id + "_bmo_q_blind",
                                                0d,
                                                Q_BLIND_DURATION);
                                if (this.passiveStacks == 3) applySlow(a);
                            }

                            if (isNeitherTowerNorAlly(a)
                                    && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                                    && a.isNotLeaping()) {
                                double damage = getSpellDamage(spellData, false);
                                a.addToDamageQueue(this, damage, spellData, false);
                            }
                        }
                    }
                    if (this.passiveStacks == 3) usePassiveStacks();
                    else addPasiveStacks();
                    String cameraFx = SkinData.getBMOQCameraFX(avatar);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            cameraFx,
                            1000,
                            this.id + "_camera",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_bmo_camera", this.location);
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
                if (!this.wActive) {
                    this.setCanMove(false);
                    this.stopMoving();
                    this.canCast[0] = false;
                    this.canCast[2] = false;

                    effectManager.addEffect(
                            W_ARMOR_BUFF_ID,
                            "armor",
                            W_ARMOR_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            W_DURATION);
                    effectManager.addEffect(
                            W_SHIELDS_BUFF_ID,
                            "spellResist",
                            W_SHIELDS_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            W_DURATION);

                    this.wActive = true;
                    wStartTime = System.currentTimeMillis();
                    String pixelsAoeFx = SkinData.getBMOWPixelsFX(avatar);
                    String remoteSpinFx = SkinData.getBMOWRemoteFX(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_bmo_pixels_start",
                            this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            remoteSpinFx,
                            3000,
                            this.id + "_bmo_remote",
                            true,
                            "fxNode",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            pixelsAoeFx,
                            3000,
                            this.id + "_pixels_aoe",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_4",
                            3000,
                            this.id + "_target_ring_4.5",
                            true,
                            "",
                            true,
                            true,
                            this.team);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell2", 3000, true);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "w", true, W_RECAST_DELAY, 0);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            W_RECAST_DELAY);
                } else {
                    this.canCast[0] = true;
                    this.canCast[2] = true;
                    this.wActive = false;
                    this.wEnd(cooldown, gCooldown);
                    int delay1 = getReducedCooldown(cooldown);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                }
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                try {
                    if (passiveStacks == 3) {
                        ultSlowActive = true;
                        passiveStacks = 0;
                    } else addPasiveStacks();
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell3", 250, false);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_bmo_ultimate", this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_bmo_ultimate",
                            this.location);
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

    private void applySlow(Actor a) {
        long lastProc = actorsWithPassiveSlow.getOrDefault(a, -1L);

        if (lastProc == -1 || System.currentTimeMillis() - lastProc > PASSIVE_SLOW_DURATION) {
            actorsWithPassiveSlow.put(a, System.currentTimeMillis());
            a.getEffectManager()
                    .addState(
                            ActorState.SLOWED,
                            id + "_bmo_passive_slow",
                            PASSIVE_SLOW_PERCENT,
                            PASSIVE_SLOW_DURATION);
        }
    }

    private void addPasiveStacks() {
        if (passiveStacks < 3) {
            if (passiveStacks != 0)
                ExtensionCommands.removeStatusIcon(
                        this.parentExt, this.player, "bmoPassive" + passiveStacks);
            this.passiveStacks++;
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "bmoPassive" + passiveStacks,
                    "bmo_spell_4_short_description",
                    "icon_bmo_p" + passiveStacks,
                    0);
        }
        if (passiveStacks == 3) {
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "vo/vo_bmo_passive_on", this.location);
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "sfx_bmo_passive", this.location);
            String passiveFx = SkinData.getBMOPassiveFX(avatar);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    passiveFx,
                    1000 * 60 * 15,
                    this.id + "_bmo_p_fx",
                    true,
                    "",
                    false,
                    false,
                    this.team);
        }
    }

    private void usePassiveStacks() {
        ExtensionCommands.removeStatusIcon(
                this.parentExt, this.player, "bmoPassive" + passiveStacks);
        ExtensionCommands.removeFx(this.parentExt, this.player, this.id + "_bmo_p_fx");
        this.passiveStacks = 0;
    }

    private void interrputW() {
        if (passiveStacks < 3) addPasiveStacks();
        removeWBuffs();
        canMove = true;
        this.canCast[0] = true;
        this.canCast[2] = true;
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 500, false);
        int baseCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
        ExtensionCommands.actorAbilityResponse(
                parentExt, player, "w", true, getReducedCooldown(baseCooldown), 0);
    }

    private void wEnd(int cooldown, int gCooldown) {
        canMove = true;
        removeWBuffs();
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        for (Actor a : Champion.getActorsInRadius(handler, this.location, 4f)) {
            if (isNeitherStructureNorAlly(a)) {
                a.getEffectManager()
                        .addState(ActorState.STUNNED, id + "_bmo_w_stun", 0d, W_STUN_DURATION);
            }

            if (isNeitherTowerNorAlly(a)) {
                JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                long wDuration = System.currentTimeMillis() - wStartTime;
                double damageMultiplier = 1 + (wDuration / 10000d);
                double damage = getSpellDamage(spellData, false) * damageMultiplier;

                a.addToDamageQueue(this, damage, spellData, false);
            }
        }
        if (this.passiveStacks == 3) usePassiveStacks();
        else addPasiveStacks();
        String aoeExplodeFX = SkinData.getBMOWExplodeFX(avatar);
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "vo/vo_bmo_yay", this.location);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_bmo_pixels_explode", this.location);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                aoeExplodeFX,
                1000,
                this.id + "_pixels_aoe_explode",
                true,
                "",
                false,
                false,
                this.team);
        ExtensionCommands.actorAbilityResponse(
                this.parentExt, this.player, "w", true, getReducedCooldown(cooldown), gCooldown);
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, false);
    }

    private BMOAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new BMOAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class BMOAbilityRunnable extends AbilityRunnable {

        public BMOAbilityRunnable(
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
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);
            if (getHealth() > 0) {
                Line2D abilityLine = Champion.createLineTowards(location, dest, E_RANGE);
                String ultProjectile = SkinData.getBMOEProjectileFX(avatar);
                fireProjectile(
                        new BMOUltProjectile(
                                parentExt, BMO.this, abilityLine, 5f, 1.5f, ultProjectile),
                        location,
                        dest,
                        E_RANGE);
            }
        }

        @Override
        protected void spellPassive() {}
    }

    public class BMOUltProjectile extends Projectile {
        private final List<Actor> eVictims;
        private double damageReduction = 0d;

        public BMOUltProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float offsetDistance,
                String id) {
            super(parentExt, owner, path, speed, offsetDistance, offsetDistance, id);
            this.eVictims = new ArrayList<>();
        }

        @Override
        protected void hit(Actor victim) {
            this.eVictims.add(victim);
            JsonNode spellData = this.parentExt.getAttackData(BMO.this.avatar, "spell3");
            double damage = getSpellDamage(spellData, true) * (1 - damageReduction);

            victim.addToDamageQueue(BMO.this, damage, spellData, false);

            String soundName = "akubat_projectileHit1";
            ExtensionCommands.playSound(parentExt, room, "", soundName, victim.getLocation());

            if (ultSlowActive) applySlow(victim);
            this.damageReduction += 0.15d;
            if (this.damageReduction > 0.7d) this.damageReduction = 0.7d;
        }

        @Override
        public boolean isTargetable(Actor a) {
            return super.isTargetable(a) && !eVictims.contains(a);
        }

        public void destroy() {
            super.destroy();
            if (ultSlowActive) {
                ExtensionCommands.removeStatusIcon(
                        BMO.this.parentExt, BMO.this.player, "bmoPassive3");
                ultSlowActive = false;
            }
        }
    }

    private class BMOPassive implements Runnable {
        Actor target;
        boolean crit;

        BMOPassive(Actor a, boolean crit) {
            this.target = a;
            this.crit = crit;
        }

        public void run() {
            double damage = BMO.this.getPlayerStat("attackDamage");
            if (crit) {
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(
                            parentExt, BMO.this, this.target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

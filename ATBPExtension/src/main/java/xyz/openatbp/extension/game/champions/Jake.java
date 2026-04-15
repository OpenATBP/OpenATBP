package xyz.openatbp.extension.game.champions;

import static xyz.openatbp.extension.game.effects.EffectManager.DEFAULT_KNOCKBACK_SPEED;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class Jake extends UserActor {
    public static final int PASSIVE_PER_TARGET_CD = 8000;
    public static final double PASSIVE_ATTACKDAMAGE_VALUE = 0.4d;
    public static final int PASSIVE_ROOT_DURATION = 1000;

    public static final int Q_RESTRAINT_TIME = 1600;
    public static final int Q_STUN_DURATION = 2000;
    public static final int Q_UNLOCK_SKILLS_DELAY = 500;
    public static final int Q_PROJECTILE_DELAY = 500;
    public static final float Q_MAX_RANGE = 7f;
    public static final int Q_VICTIM_SEPARATION = 1;
    public static final float Q_PROJECTILE_SPEED = 9f;
    public static final float Q_PULL_SPEED = 10f;

    public static final float W_KNOCKBACK_DIST = 3.5f;
    public static final float W_RADIUS = 3f;

    public static final double E_SPEED_PERCENT = 0.2;
    public static final int E_DURATION = 5000;
    public static final int E_STOMP_CD = 500;

    private boolean blockSkillsAndWalking = false;
    private boolean ultActivated = false;
    private long lastStomped = 0;
    private boolean stompSoundChange = false;
    private Map<String, Long> lastPassiveTime = new HashMap<>();
    private long ultStartTime = 0;
    private long lastSpell1cAnimTime = 0L;
    private boolean qAnimResetNeeded = false;

    private JakeQProjectile qProjectile;

    public Jake(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);

        if (qAnimResetNeeded && isMoving && canMove()) {
            // Console.debugLog("changing to run anim!");
            qAnimResetNeeded = false;
            ExtensionCommands.actorAnimate(parentExt, room, id, "run", 100, false);
        }

        if (qAnimResetNeeded && System.currentTimeMillis() - lastSpell1cAnimTime >= 1000) {
            // Console.debugLog("QAnim reset no longer needed!");
            qAnimResetNeeded = false;
        }

        if (blockSkillsAndWalking && qProjectile != null && hasInterrupingCC()) {
            qProjectile.destroy();
            playInterruptSoundAndIdle();
        }

        if (this.ultActivated && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.removeFx(parentExt, room, id + "_stomp");
            this.ultActivated = false;
        }
        if (this.ultActivated && !this.isStopped()) {
            if (System.currentTimeMillis() - this.lastStomped >= E_STOMP_CD) {
                this.lastStomped = System.currentTimeMillis();
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, this.location, 2f)) {
                    if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                        JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
                        double damage = (double) (getSpellDamage(spellData, false)) / 2d;
                        a.addToDamageQueue(this, (int) damage, spellData, true);
                    }
                }

                String stompFX = SkinData.getJakeEStompFX(avatar);
                String stompSFX = SkinData.getJakeEStompSFX(avatar);
                String stompSFX1 = SkinData.getJakeEStomp1SFX(avatar);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        stompFX,
                        325,
                        this.id + "_stomp",
                        true,
                        "",
                        false,
                        false,
                        this.team);
                this.stompSoundChange = !this.stompSoundChange;
                if (this.stompSoundChange) {
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, stompSFX1, this.location);
                } else {
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, stompSFX, this.location);
                }
            }
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (blockSkillsAndWalking) return false;
        return super.canUseAbility(ability);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (this.ultActivated)
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "sfx_jake_grow_fart", this.location);
    }

    @Override
    public boolean canAttack() {
        if (this.ultActivated) return false;
        return super.canAttack();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultActivated) {
            this.ultActivated = false;
            ExtensionCommands.swapActorAsset(
                    this.parentExt, this.room, this.id, this.getSkinAssetBundle());
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_jake_ring_2");
        }
        if (blockSkillsAndWalking) blockSkillsAndWalking = false;
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if (isNeitherStructureNorAlly(a)) {
            if (!lastPassiveTime.isEmpty()) {
                if (lastPassiveTime.containsKey(a.getId())) {
                    if (System.currentTimeMillis() - lastPassiveTime.get(a.getId())
                            >= PASSIVE_PER_TARGET_CD) {
                        doPassive(a);
                    }
                } else {
                    doPassive(a);
                }
            } else {
                doPassive(a);
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
                stopMoving();
                blockSkillsAndWalking = true;
                Runnable activateHitbox =
                        () -> {
                            Line2D qLine = Champion.getAbilityLine(location, dest, Q_MAX_RANGE);
                            qProjectile =
                                    new JakeQProjectile(
                                            parentExt, this, qLine, Q_PROJECTILE_SPEED, 1f, 1f, "");
                            fireProjectile(qProjectile, location, qLine.getP2(), Q_MAX_RANGE);
                        };
                scheduleTask(activateHitbox, Q_PROJECTILE_DELAY);
                String stretchSFX = SkinData.getJakeQSFX(avatar);
                String stretchVO = SkinData.getJakeQVO(avatar);
                ExtensionCommands.playSound(parentExt, room, id, stretchSFX, location);
                ExtensionCommands.playSound(parentExt, room, id, stretchVO, location);
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "q", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(
                        abilityHandler(ability, spellData, cooldown, gCooldown, dest),
                        getReducedCooldown(cooldown));
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    this.stopMoving(gCooldown);
                    if (getHealth() > 0) {
                        String ballFX = SkinData.getJakeWFX(avatar);
                        String dustUpFX = SkinData.getJakeWDustUpFX(avatar);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                ballFX,
                                2000,
                                this.id + "_ball",
                                true,
                                "targetNode",
                                true,
                                false,
                                this.team);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                dustUpFX,
                                1500,
                                this.id + "_dust",
                                false,
                                "Bip001 Footsteps",
                                false,
                                false,
                                this.team);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fx_target_ring_3",
                                850,
                                this.id + "_jake_ring_3",
                                true,
                                "",
                                true,
                                true,
                                this.team);
                        RoomHandler handler = parentExt.getRoomHandler(room.getName());
                        for (Actor a : Champion.getActorsInRadius(handler, location, W_RADIUS)) {
                            if (isNeitherStructureNorAlly(a)) {
                                a.handleKnockback(
                                        location, W_KNOCKBACK_DIST, DEFAULT_KNOCKBACK_SPEED);
                            }

                            if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                                double dmg = getSpellDamage(spellData, false);
                                a.addToDamageQueue(this, dmg, spellData, false);
                            }
                        }

                        String ballVO = SkinData.getJakeWVO(avatar);
                        String ballSFX = SkinData.getJakeWSFX(avatar);
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, this.id, ballVO, this.location);
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, this.id, ballSFX, this.location);
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
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityHandler(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.stopMoving();
                    String bigFX = SkinData.getJakeEFX(avatar);
                    ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bigFX);
                    this.ultActivated = true;
                    this.ultStartTime = System.currentTimeMillis();

                    effectManager.cleanseDebuffs();
                    effectManager.addEffect(
                            id + "_jake_e_speed",
                            "speed",
                            E_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.DEBUFF,
                            E_DURATION);
                    effectManager.addState(
                            ActorState.IMMUNITY, id + "_jake_e_immunity", 0d, E_DURATION);

                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "statusEffect_immunity",
                            E_DURATION,
                            id + "_ultImmunity",
                            true,
                            "displayBar",
                            false,
                            false,
                            team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_2",
                            E_DURATION,
                            this.id + "_jake_ring_2",
                            true,
                            "",
                            true,
                            true,
                            this.team);

                    String growVO = SkinData.getJakeEVO(avatar);
                    String growSFX = SkinData.getJakeESFX(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, growSFX, location);
                    ExtensionCommands.playSound(parentExt, room, id, growVO, location);
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
                scheduleTask(abilityHandler(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
        }
    }

    private class JakeQProjectile extends Projectile {
        public JakeQProjectile(
                ATBPExtension parentExt,
                Actor owner,
                Line2D path,
                float speed,
                float offsetDistance,
                float rectangleHeight,
                String projectileAsset) {
            super(parentExt, owner, path, speed, offsetDistance, rectangleHeight, projectileAsset);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getChampionName(avatar), "spell1");

            Point2D jakeLocation = Jake.this.location;
            float distance = (float) jakeLocation.distance(victim.getLocation());
            JsonNode qData = parentExt.getAttackData(avatar, "spell1");
            float dmgPerUnitOfDistance = (float) qData.get("damageRatio").asDouble();
            int baseDmg = qData.get("damage").asInt();

            float modifier = dmgPerUnitOfDistance * distance;
            double damage = baseDmg + (modifier * getPlayerStat("spellDamage"));

            victim.addToDamageQueue(Jake.this, damage, spellData, false);

            if (distance > 5) {
                ExtensionCommands.playSound(
                        parentExt, room, victim.getId(), "sfx_oildrum_dead", victim.getLocation());

                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        victim.getId(),
                        "jake_stomp_fx",
                        500,
                        this.id + "_jake_q_fx",
                        false,
                        "",
                        false,
                        false,
                        team);
            }

            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            PathFinder pf = rh.getPathFinder();

            Point2D initialPullPoint = pf.getIntersectionPoint(victim.getLocation(), jakeLocation);
            Point2D finalPullPoint =
                    pf.getStoppingPoint(
                            victim.getLocation(), initialPullPoint, Q_VICTIM_SEPARATION);

            Console.debugLog("Pull distance: " + victim.getLocation().distance(finalPullPoint));

            float finalDistance = (float) victim.getLocation().distance(finalPullPoint);

            victim.handlePull(Jake.this.location, finalDistance, Q_PULL_SPEED);

            if (isNeitherStructureNorAlly(victim)) {
                victim.getEffectManager()
                        .addState(
                                ActorState.STUNNED,
                                Jake.this.id + "_jake_q_stun",
                                0d,
                                Q_STUN_DURATION);
            }

            int victimTravelTimeMs = (int) ((finalDistance / Q_PULL_SPEED) * 1000.0);

            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    victim.getId(),
                    "jake_trail",
                    victimTravelTimeMs,
                    victim.getId() + "qTrail",
                    true,
                    "",
                    false,
                    false,
                    team);

            destroy();
        }

        @Override
        public void destroy() {
            super.destroy();
            unlockSkillsAndWalking();
            qAnimResetNeeded = true;
            lastSpell1cAnimTime = System.currentTimeMillis();
            ExtensionCommands.actorAnimate(parentExt, room, Jake.this.id, "spell1c", -1, false);
        }
    }

    @Override
    public boolean canMove() {
        if (blockSkillsAndWalking) return false;
        return super.canMove();
    }

    private void unlockSkillsAndWalking() {
        Runnable unlock = () -> blockSkillsAndWalking = false;
        scheduleTask(unlock, Q_UNLOCK_SKILLS_DELAY);
    }

    public void doPassive(Actor target) {
        Runnable passive =
                () -> {
                    lastPassiveTime.put(target.getId(), System.currentTimeMillis());
                    JsonNode attackData = this.parentExt.getAttackData("jake", "spell4");
                    target.addToDamageQueue(
                            this,
                            getPlayerStat("attackDamage") * PASSIVE_ATTACKDAMAGE_VALUE,
                            attackData,
                            false);

                    target.getEffectManager()
                            .addState(
                                    ActorState.ROOTED,
                                    id + "_jake_passive_root",
                                    0d,
                                    PASSIVE_ROOT_DURATION);

                    if (!this.avatar.contains("cake"))
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "vo/vo_jake_passive_1",
                                this.location);
                };
        SmartFoxServer.getInstance()
                .getTaskScheduler()
                .schedule(passive, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
    }

    private JakeAbilityHandler abilityHandler(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new JakeAbilityHandler(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class JakeAbilityHandler extends AbilityRunnable {

        public JakeAbilityHandler(
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
        }

        @Override
        protected void spellPassive() {}
    }
}

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Jake extends UserActor {
    private static final int PASSIVE_PER_TARGET_CD = 8000;
    private static final double PASSIVE_ATTACKDAMAGE_VALUE = 0.4d;
    private static final double PASSIVE_SLOW_VALUE = 0.5d;
    private static final int PASSIVE_SLOW_DURATION = 1500;
    private static final int PASSIVE_ROOT_DURATION = 1000;
    private static final int Q_RESTRAINT_TIME = 1600;
    private static final int Q_STUN_DURATION = 2000;
    private static final int Q_UNLOCK_SKILLS_DELAY = 500;
    private static final double E_SPEED_VALUE = 0.8;
    private static final int E_DURATION = 5000;
    private static final int E_STOMP_CD = 500;
    private boolean grabActive = false;
    private Point2D grabPoint;
    private float grabStatus = 0;
    private Point2D qDestination;
    private boolean blockAbilities = false;
    private float offsetDistance = 1;
    private boolean ultActivated = false;
    private long lastStomped = 0;
    private boolean stompSoundChange = false;
    private Map<String, Long> lastPassiveTime = new HashMap<>();
    private long ultStartTime = 0;

    public Jake(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (grabActive && (hasInterrupingCC() || this.dead)) {
            blockAbilities = false;
            resetGrab();
            if (!hasMovementCC()) canMove = true;
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 10, false);
        }
        if (grabActive) {
            if (grabStatus == 5) offsetDistance = 1.75f;
            Path2D rectangle = Champion.createRectangle(grabPoint, qDestination, 1, offsetDistance);
            List<Actor> victims =
                    parentExt.getRoomHandler(room.getName()).getEnemiesInPolygon(team, rectangle);

            if (!victims.isEmpty()) {
                resetGrab();
                Actor victim = victims.get(0);
                JsonNode spellData = parentExt.getAttackData(getChampionName(avatar), "spell1");

                Point2D origLocation = this.location;

                float distance = (float) origLocation.distance(victim.getLocation());
                float modifier = ((130 / 9f) * distance) / 100;
                double damage =
                        35
                                + Math.round(
                                        modifier
                                                * getPlayerStat(
                                                        "spellDamage")); // TODO tie to xml file

                victim.addToDamageQueue(this, damage, spellData, false);
                if (isNonStructure(victim))
                    victim.addState(ActorState.STUNNED, 0d, Q_STUN_DURATION);
                if (distance > 5) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            victim.getId(),
                            "sfx_oildrum_dead",
                            victim.getLocation());

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
                Point2D initialDashLocation =
                        Champion.getAbilityLine(origLocation, victim.getLocation(), distance - 1)
                                .getP2();

                Point2D dashPoint = this.dash(initialDashLocation, true, 13);
                double time = origLocation.distance(dashPoint) / 13;
                int dashTimeMs = (int) (time * 1000);

                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "jake_trail",
                        dashTimeMs,
                        id + "qTrail",
                        true,
                        "",
                        false,
                        false,
                        team);

                ExtensionCommands.actorAnimate(parentExt, room, id, "spell1b", dashTimeMs, true);

                Runnable nextAnimation =
                        () -> {
                            unlockAbilitiesAfterDelay();
                            ExtensionCommands.actorAnimate(
                                    parentExt, room, id, "spell1c", 500, false);
                        };
                scheduleTask(nextAnimation, dashTimeMs);
            } else if (grabStatus < 8) {
                grabPoint = Champion.getAbilityLine(grabPoint, qDestination, 1).getP2();
                grabStatus++;
            } else {
                resetGrab();
                unlockAbilitiesAfterDelay();
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell1c", 500, false);
            }
        }

        if (this.ultActivated && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.removeFx(parentExt, room, id + "_stomp");
            this.ultActivated = false;
            updateStatMenu("speed");
        }
        if (this.ultActivated && !this.isStopped()) {
            if (System.currentTimeMillis() - this.lastStomped >= E_STOMP_CD) {
                this.lastStomped = System.currentTimeMillis();
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, this.location, 2f)) {
                    if (this.isNonStructure(a)) {
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
    public Point2D dash(Point2D dest, boolean noClip, double dashSpeed) {
        this.isDashing = true;
        if (movementDebug)
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "gnome_a",
                    this.id + "_test" + Math.random(),
                    5000,
                    (float) dest.getX(),
                    (float) dest.getY(),
                    false,
                    0,
                    0f);
        Point2D dashPoint = dest;
        /*
        if (MovementManager.insideAnyObstacle(
                this.parentExt,
                this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap(),
                dest)) {
            Line2D extendedLine =
                    MovementManager.extendLine(new Line2D.Double(this.location, dest), 5f);
            Point2D[] points = MovementManager.findAllPoints(extendedLine);
            boolean atDashPoint = false;
            for (Point2D p : points) {
                if (p.distance(dest) <= 0.01 && !atDashPoint) atDashPoint = true;
                if (atDashPoint) {
                    if (!MovementManager.insideAnyObstacle(
                            this.parentExt,
                            this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap(),
                            p)) {
                        dashPoint = p;
                        break;
                    }
                }
            }
            if (dashPoint == null) dashPoint = dest;
        } else dashPoint = dest;

         */
        double time = dashPoint.distance(this.location) / dashSpeed;
        int timeMs = (int) (time * 1000d);
        this.stopMoving(timeMs);
        Runnable setIsDashing = () -> this.isDashing = false;
        parentExt.getTaskScheduler().schedule(setIsDashing, timeMs, TimeUnit.MILLISECONDS);
        ExtensionCommands.moveActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                dashPoint,
                (float) dashSpeed,
                true);
        this.setLocation(dashPoint);
        this.target = null;
        return dashPoint;
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (blockAbilities) return false;
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
            updateStatMenu("speed");
        }
        if (grabActive) {
            resetGrab();
            blockAbilities = false;
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("speed") && ultActivated) {
            return super.getPlayerStat("speed") * E_SPEED_VALUE;
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if (isNonStructure(a)) {
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
                stopMoving(Q_RESTRAINT_TIME);
                blockAbilities = true;
                Runnable activateHitbox =
                        () -> {
                            grabPoint = this.location;
                            qDestination = Champion.getAbilityLine(location, dest, 9).getP2();
                            grabActive = true;
                        };
                scheduleTask(activateHitbox, 500);
                String stretchSFX = SkinData.getJakeQSFX(avatar);
                String stretchVO = SkinData.getJakeQVO(avatar);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, stretchSFX, this.location);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, stretchVO, this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
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
                        for (Actor a : Champion.getActorsInRadius(handler, this.location, 3f)) {
                            if (this.isNonStructure(a)) {
                                a.knockback(this.location, 3.5f);
                                a.addToDamageQueue(
                                        this, getSpellDamage(spellData, true), spellData, false);
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
                    this.cleanseEffects();
                    this.ultActivated = true;
                    this.ultStartTime = System.currentTimeMillis();
                    updateStatMenu("speed");
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
                    this.addState(ActorState.CLEANSED, 0d, E_DURATION);
                    this.addState(ActorState.IMMUNITY, 0d, E_DURATION);

                    String growVO = SkinData.getJakeEVO(avatar);
                    String growSFX = SkinData.getJakeESFX(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, growSFX, this.location);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, growVO, this.location);
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

    private void unlockAbilitiesAfterDelay() {
        Runnable unlock = () -> blockAbilities = false;
        scheduleTask(unlock, Q_UNLOCK_SKILLS_DELAY);
    }

    private void resetGrab() {
        grabActive = false;
        grabStatus = 0;
        offsetDistance = 1;
    }

    private void doPassive(Actor target) {
        Runnable passive =
                () -> {
                    lastPassiveTime.put(target.getId(), System.currentTimeMillis());
                    JsonNode attackData = this.parentExt.getAttackData("jake", "spell4");
                    target.addToDamageQueue(
                            this,
                            getPlayerStat("attackDamage") * PASSIVE_ATTACKDAMAGE_VALUE,
                            attackData,
                            false);
                    target.addState(ActorState.ROOTED, 0, PASSIVE_ROOT_DURATION);
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

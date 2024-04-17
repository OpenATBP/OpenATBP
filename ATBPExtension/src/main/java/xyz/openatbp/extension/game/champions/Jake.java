package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
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
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class Jake extends UserActor {
    private boolean ultActivated = false;
    private long lastStomped = 0;
    private boolean stompSoundChange = false;
    private boolean dashActive = false;
    private Map<String, Long> lastPassiveTime = new HashMap<>();
    private long qStartTime = 0;
    private boolean qUsed = false;
    private int qAnimationTime;
    private Actor qVictim;
    private Path2D qPolygon;
    private boolean doGrab = false;
    private long ultStartTime = 0;
    private boolean doQEndAnimation = false;
    private static final float Q_FIRST_HALF_OFFSET = 0.75f;
    private static final float Q_SECOND_HALF_OFFSET = 1.75f;
    private static final float Q_SPELL_RANGE = 9;

    public Jake(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if (isNonStructure(target)) {
            if (!lastPassiveTime.isEmpty()) {
                if (lastPassiveTime.containsKey(target.getId())) {
                    if (System.currentTimeMillis() - lastPassiveTime.get(target.getId()) >= 8000) {
                        doPassive();
                    }
                } else {
                    doPassive();
                }
            } else {
                doPassive();
            }
        }
    }

    private void doPassive() {
        lastPassiveTime.put(target.getId(), System.currentTimeMillis());
        JsonNode attackData = this.parentExt.getAttackData("jake", "spell4");
        target.addToDamageQueue(this, getPlayerStat("attackDamage") * 0.4d, attackData);
        target.addState(ActorState.SLOWED, 0.5d, 1500, null, false);
        if (!this.avatar.contains("cake"))
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "vo/vo_jake_passive_1", this.location);
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
        if (this.ultActivated || this.qUsed) return false;
        return super.canAttack();
    }

    public boolean canMove() {
        if (dashActive) return false;
        else return super.canMove();
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
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.doQEndAnimation) {
            ExtensionCommands.actorAnimate(parentExt, room, id, "spell1c", 500, false);
            this.doQEndAnimation = false;
        }
        if (this.ultActivated && System.currentTimeMillis() - this.ultStartTime >= 5000) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.removeFx(parentExt, room, id + "_stomp");
            this.ultActivated = false;
        }
        if (this.ultActivated && !this.isStopped()) {
            if (System.currentTimeMillis() - this.lastStomped >= 500) {
                this.lastStomped = System.currentTimeMillis();
                for (Actor a :
                        Champion.getActorsInRadius(
                                this.parentExt.getRoomHandler(this.room.getId()),
                                this.location,
                                2f)) {
                    if (this.isNonStructure(a)) {
                        JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell3");
                        double damage = (double) (getSpellDamage(spellData)) / 2d;
                        a.addToDamageQueue(this, (int) damage, spellData);
                    }
                }
                String stompSfxPrefix =
                        (this.avatar.contains("guardian")) ? "jake_guardian_" : "jake_";
                String stompFxPrefix =
                        (this.avatar.contains("guardian"))
                                ? "jake_guardian_"
                                : (this.avatar.contains("cake")) ? "cake_" : "jake_";
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        stompFxPrefix + "stomp_fx",
                        325,
                        this.id + "_stomp",
                        true,
                        "",
                        false,
                        false,
                        this.team);
                this.stompSoundChange = !this.stompSoundChange;
                if (this.stompSoundChange)
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_" + stompSfxPrefix + "grow_stomp1",
                            this.location);
                else
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_" + stompSfxPrefix + "grow_stomp",
                            this.location);
            }
        }
        if (this.qUsed
                && System.currentTimeMillis() - this.qStartTime <= 300
                && this.hasInterrupingCC()) {
            this.qAnimationTime = 0;
            this.dashActive = false;
            this.qPolygon = null;
            this.qUsed = false;
            ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 500, false);
        }
        if (this.doGrab
                && this.qVictim != null
                && this.qPolygon != null
                && this.qPolygon.contains(this.qVictim.getLocation())) {
            JsonNode spellData = this.parentExt.getAttackData("jake", "spell1");
            this.dashActive = true;
            this.resetTarget();
            double distance = this.location.distance(this.qVictim.getLocation());
            double newTime = (distance / 15d) * 1000d;
            Runnable animationDelay =
                    () ->
                            ExtensionCommands.actorAnimate(
                                    this.parentExt, this.room, this.id, "spell1c", 250, false);
            Runnable canCast =
                    () -> {
                        this.dashActive = false;
                        this.qUsed = false;
                        this.canCast[1] = true;
                        this.canCast[2] = true;
                    };
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(animationDelay, (int) newTime, TimeUnit.MILLISECONDS);
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(canCast, (int) newTime + 500, TimeUnit.MILLISECONDS);
            if (this.getHealth() > 0) {
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "spell1b", (int) newTime, true);
                Point2D dashPoint =
                        MovementManager.getDashPoint(
                                this, new Line2D.Float(this.location, this.qVictim.getLocation()));
                this.dash(dashPoint, true, 15);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "jake_trail",
                        (int) newTime,
                        this.id,
                        true,
                        "",
                        true,
                        false,
                        this.team);
                double percentage = distance / 7d;
                if (percentage < 0.5d) percentage = 0.5d;
                System.out.println("percentage " + percentage);
                double spellModifer = this.getPlayerStat("spellDamage") * percentage;
                if (isNonStructure(this.qVictim))
                    this.qVictim.addState(ActorState.STUNNED, 0d, 2000, null, false);
                this.qVictim.addToDamageQueue(this, 35 + spellModifer, spellData);
                if (distance >= 5.5d) {
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_oildrum_dead",
                            this.qVictim.getLocation());
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.qVictim.getId(),
                            "jake_stomp_fx",
                            500,
                            this.id + "_jake_q_fx",
                            false,
                            "",
                            false,
                            false,
                            this.team);
                }
            }
            this.doGrab = false;
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
                this.stopMoving();
                this.canCast[0] = false;
                this.canCast[1] = false;
                this.canCast[2] = false;
                canMove = false;
                this.qUsed = true;
                this.qStartTime = System.currentTimeMillis();
                this.qAnimationTime = 1300;
                Line2D qLine = Champion.getAbilityLine(this.location, dest, Q_SPELL_RANGE);
                this.qPolygon = createOctagon(this.location, dest);
                Runnable projectileDelay =
                        () -> {
                            if (this.getHealth() > 0) {
                                fireQProjectile(
                                        new JakeQProjectile(
                                                this.parentExt,
                                                this,
                                                qLine,
                                                8.5f,
                                                1f,
                                                this.id + "stretchy_grab"),
                                        "stretchy_grab",
                                        this.location,
                                        dest,
                                        Q_SPELL_RANGE);
                            }
                        };
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(projectileDelay, 300, TimeUnit.MILLISECONDS);
                String stretchFxPrefix =
                        (this.avatar.contains("cake"))
                                ? "cake_"
                                : (this.avatar.contains("randy")) ? "jake_butternubs_" : "jake_";
                String stretchSfxPrefix =
                        (this.avatar.contains("guardian")) ? "jake_guardian_" : "jake_";
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_" + stretchSfxPrefix + "stretch",
                        this.location);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_" + stretchFxPrefix + "stretch",
                        this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new JakeAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                qAnimationTime,
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.stopMoving(gCooldown);
                String ballFx =
                        (this.avatar.contains("cake"))
                                ? "fx_cake_ball"
                                : (this.avatar.contains("guardian"))
                                        ? "fx_jake_guardian_ball"
                                        : (this.avatar.contains("randy"))
                                                ? "fx_jake_butternubs_ball"
                                                : (this.avatar.contains("wizard"))
                                                        ? "fx_jake_wizard_ball"
                                                        : "fx_jake_ball";
                String dustFx =
                        (this.avatar.contains("cake"))
                                ? "cake_dust_up"
                                : (this.avatar.contains("guardian"))
                                        ? "jake_guardian_dust_up"
                                        : "jake_dust_up";
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        ballFx,
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
                        dustFx,
                        1000,
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
                for (Actor a :
                        Champion.getActorsInRadius(
                                this.parentExt.getRoomHandler(this.room.getId()),
                                this.location,
                                3f)) {
                    if (this.isNonStructure(a)) {
                        a.knockback(this.location);
                        a.addToDamageQueue(this, getSpellDamage(spellData), spellData);
                    }
                }
                String ballVoPrefix =
                        (this.avatar.contains("cake"))
                                ? "cake_"
                                : (this.avatar.contains("randy")) ? "jake_butternubs_" : "jake_";
                String ballSfxPrefix =
                        (this.avatar.contains("guardian")) ? "jake_guardian_" : "jake_";
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_" + ballSfxPrefix + "ball",
                        this.location);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_" + ballVoPrefix + "ball",
                        this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new JakeAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                String bigFx =
                        (this.avatar.contains("cake"))
                                ? "jake_cake_big"
                                : (this.avatar.contains("guardian"))
                                        ? "jake_guardian_big"
                                        : (this.avatar.contains("randy"))
                                                ? "jake_butternubs_big"
                                                : (this.avatar.contains("wizard"))
                                                        ? "jake_wizard_big"
                                                        : (this.avatar.contains("zombie"))
                                                                ? "jake_zombie_big"
                                                                : "jake_big";
                ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bigFx);
                this.cleanseEffects();
                this.ultActivated = true;
                this.ultStartTime = System.currentTimeMillis();
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "statusEffect_immunity",
                        5000,
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
                        5000,
                        this.id + "_jake_ring_2",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                this.addState(ActorState.CLEANSED, 0d, 5000, null, false);
                this.addState(ActorState.IMMUNITY, 0d, 5000, null, false);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                String growVoPrefix =
                        (this.avatar.contains("cake"))
                                ? "cake_"
                                : (this.avatar.contains("randy")) ? "jake_butternubs_" : "jake_";
                String growSfxPrefix =
                        (this.avatar.contains("guardian")) ? "jake_guardian_" : "jake_";
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_" + growSfxPrefix + "grow",
                        this.location);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_" + growVoPrefix + "grow",
                        this.location);
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(
                                new JakeAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
        }
    }

    private Path2D createOctagon(Point2D location, Point2D destination) {
        Line2D abilityLine = Champion.getAbilityLine(location, destination, Q_SPELL_RANGE);
        Line2D firstHalf = Champion.getAbilityLine(location, destination, 5f);
        double angle =
                Math.atan2(
                        abilityLine.getY2() - abilityLine.getY1(),
                        abilityLine.getX2() - abilityLine.getX1());
        int PERPENDICULAR_ANGLE = 90;
        Point2D bottomFirstHalf1 =
                Champion.calculatePolygonPoint(
                        abilityLine.getP1(),
                        Q_FIRST_HALF_OFFSET,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D bottomFirstHalf2 =
                Champion.calculatePolygonPoint(
                        abilityLine.getP1(),
                        Q_FIRST_HALF_OFFSET,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D topFirstHalf1 =
                Champion.calculatePolygonPoint(
                        firstHalf.getP2(),
                        Q_FIRST_HALF_OFFSET,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D topFirstHalf2 =
                Champion.calculatePolygonPoint(
                        firstHalf.getP2(),
                        Q_FIRST_HALF_OFFSET,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D bottomSecondHalf1 =
                Champion.calculatePolygonPoint(
                        firstHalf.getP2(),
                        Q_SECOND_HALF_OFFSET,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D bottomSecondHalf2 =
                Champion.calculatePolygonPoint(
                        firstHalf.getP2(),
                        Q_SECOND_HALF_OFFSET,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D topSecondHalf1 =
                Champion.calculatePolygonPoint(
                        abilityLine.getP2(),
                        Q_SECOND_HALF_OFFSET,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D topSecondHalf2 =
                Champion.calculatePolygonPoint(
                        abilityLine.getP2(),
                        Q_SECOND_HALF_OFFSET,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));

        Path2D octagon = new Path2D.Float();
        octagon.moveTo(bottomFirstHalf1.getX(), bottomFirstHalf1.getY());
        octagon.lineTo(topFirstHalf1.getX(), topFirstHalf1.getY());
        octagon.lineTo(bottomSecondHalf1.getX(), bottomSecondHalf1.getY());
        octagon.lineTo(topSecondHalf1.getX(), topSecondHalf1.getY());
        octagon.lineTo(topSecondHalf2.getX(), topSecondHalf2.getY());
        octagon.lineTo(bottomSecondHalf2.getX(), bottomSecondHalf2.getY());
        octagon.lineTo(topFirstHalf2.getX(), topFirstHalf2.getY());
        octagon.lineTo(bottomFirstHalf2.getX(), bottomFirstHalf2.getY());
        return octagon;
    }

    private void fireQProjectile(
            Projectile projectile, String id, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        double speed = 8.5;
        ExtensionCommands.createProjectile(
                parentExt, this.room, this, id, location, lineEndPoint, (float) speed);
        this.parentExt.getRoomHandler(this.room.getId()).addProjectile(projectile);
    }

    private class JakeAbilityHandler extends AbilityRunnable {

        public JakeAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int Q_CAST_DELAY = 1300;
            Runnable enableQCasting = () -> canCast[0] = true;
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(
                            enableQCasting,
                            getReducedCooldown(cooldown) - Q_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            canMove = true;
            Runnable enableCastingAbilities =
                    () -> {
                        if (!dashActive) {
                            canCast[1] = true;
                            canCast[2] = true;
                        }
                    };
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(enableCastingAbilities, 500, TimeUnit.MILLISECONDS);
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

    private class JakeQProjectile extends Projectile {
        private Actor victim = null;

        public JakeQProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            List<Actor> teammates = this.getTeammates(roomHandler);
            for (Actor a : roomHandler.getActors()) {
                if (!teammates.contains(a) && a.getTeam() != owner.getTeam()) {
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
        public void destroy() {
            super.destroy();
            if (Jake.this.doGrab) return;
            if (this.victim == null) Jake.this.doQEndAnimation = true;
            Jake.this.qAnimationTime = 0;
            Jake.this.dashActive = false;
            Jake.this.qPolygon = null;
            Jake.this.qUsed = false;
        }

        @Override
        public void update(RoomHandler roomHandler) {
            if (destroyed) return;
            if (!Jake.this.doGrab
                    && (this.destination.distance(this.getLocation()) <= 0.01
                            || System.currentTimeMillis() - this.startTime
                                    > this.estimatedDuration)) {
                this.destroy();
            }
            this.updateTimeTraveled();
            Actor hitActor = this.checkPlayerCollision(roomHandler);
            if (hitActor != null) {
                this.hit(hitActor);
                return;
            }
            if (this.destination.distance(this.getLocation()) <= 0.01
                    || System.currentTimeMillis() - this.startTime > this.estimatedDuration) {
                this.destroy();
            }
        }

        @Override
        protected void hit(Actor victim) {
            if (Jake.this.qPolygon != null && Jake.this.qPolygon.contains(victim.getLocation())) {
                this.victim = victim;
                Jake.this.qVictim = victim;
                Jake.this.doGrab = true;
                this.destroy();
            }
        }
    }
}

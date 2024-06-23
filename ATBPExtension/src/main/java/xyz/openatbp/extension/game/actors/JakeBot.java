package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.UserActor.BASIC_ATTACK_DELAY;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class JakeBot extends Actor {
    private boolean isAutoAttacking = false;
    private long lastBallon = 0;
    private Point2D spawnPoint;
    private Actor charmer;

    public JakeBot(ATBPExtension parentExt, Room room, Point2D spawnPoint, int num) {
        this.parentExt = parentExt;
        this.room = room;
        this.id = "jake_bot" + team + num;
        this.dead = false;
        this.location = spawnPoint;
        this.avatar = "jake";
        this.currentHealth = 750;
        this.maxHealth = 750;
        this.team = 1;
        this.actorType = ActorType.COMPANION;
        this.spawnPoint = spawnPoint;
        this.stats = this.initializeStats();
        ExtensionCommands.createActor(
                parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (room.getGroupId().equals("Practice")) {
            return false;
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    false,
                    true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            double damage = this.getPlayerStat("attackDamage");
            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, (int) damage, "basicAttack");
            try {
                parentExt
                        .getTaskScheduler()
                        .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
            } catch (NullPointerException e) {
                // e.printStackTrace();
                parentExt
                        .getTaskScheduler()
                        .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
            }
        }
        this.attackCooldown = getPlayerStat("attackSpeed");
    }

    private void applyStopMovingDuringAttack() {
        this.stopMoving();
        this.isAutoAttacking = true;
        Runnable resetIsAttacking = () -> this.isAutoAttacking = false;
        parentExt
                .getTaskScheduler()
                .schedule(resetIsAttacking, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
    }

    private void ballon() {
        stopMoving();
        canMove = false;
        Runnable resetMoving = () -> canMove = true;
        parentExt.getTaskScheduler().schedule(resetMoving, 1000, TimeUnit.MILLISECONDS);
        String ballFX = "fx_jake_ball";
        String dustUpFX = "jake_dust_up";

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", 1000, false);
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
        JsonNode spellData = parentExt.getAttackData("jake", "spell2");
        for (Actor a : Champion.getActorsInRadius(handler, this.location, 3f)) {
            if (a.getActorType() != ActorType.BASE
                    && a.getActorType() != ActorType.TOWER
                    && a.getTeam() != team) {
                a.knockback(this.location);
                a.addToDamageQueue(this, getBallonDamage(), spellData, false);
            }
        }
        String ballVO = "vo/vo_jake_ball";
        String ballSFX = "sfx_jake_ball";
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, ballVO, this.location);
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, ballSFX, this.location);
    }

    private double getBallonDamage() {
        return Math.round(65 + getStat("spellDamage") * 0.6);
    }

    @Override
    public void die(Actor a) {
        if (this.dead) return;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) a;
            ExtensionCommands.playSound(
                    parentExt, killer.getUser(), "global", "announcer/you_defeated_enemy");
        }

        this.dead = true;
        this.currentHealth = 0;
        this.setHealth(0, (int) this.maxHealth);
        this.target = null;
        this.canMove = false;
        ExtensionCommands.knockOutActor(parentExt, room, id, a.getId(), 30);
        ExtensionCommands.destroyActor(parentExt, room, this.id);
    }

    @Override
    public void update(int msRan) {
        handleDamageQueue();
        handleActiveEffects();
        if (dead) return;
        if (!this.isStopped() && this.canMove()) this.timeTraveled += 0.1f;
        this.location =
                MovementManager.getRelativePoint(
                        this.movementLine, this.getPlayerStat("speed"), this.timeTraveled);

        if (room.getGroupId().equals("Tutorial")) {
            if (this.attackCooldown > 0) this.attackCooldown -= 100;

            if (target == null) {
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> potentialTargets =
                        handler.getEligibleActors(team, true, true, true, true);
                float closestDistance = 1000;
                for (Actor a : potentialTargets) {
                    float distance = (float) a.getLocation().distance(this.getLocation());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        this.target = a;
                    }
                }
            }

            if (target != null) {
                if (!withinRange(target) && canMove()) {
                    moveWithCollision(target.getLocation());
                } else if (withinRange(target)) {
                    if (!isStopped()) stopMoving();
                    if (canAttack()) attack(target);
                }
            }

            if (!isAutoAttacking && System.currentTimeMillis() - lastBallon >= 10000) {
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> actorsInRadius =
                        Champion.getEnemyActorsInRadius(handler, team, location, 2);
                if (!actorsInRadius.isEmpty()) {
                    ballon();
                    lastBallon = System.currentTimeMillis();
                }
            }
        } else {
            if (this.location.distance(spawnPoint) > 0.1
                    && canMove
                    && !getState(ActorState.CHARMED)) {
                moveWithCollision(spawnPoint);
            }

            if (this.getState(ActorState.CHARMED) && this.charmer != null) {
                int minVictimDist = 1;
                float dist = (float) this.location.distance(charmer.getLocation());
                if (canMove() && charmer.getHealth() > 0 && dist > minVictimDist) {
                    Line2D movementLine =
                            Champion.getAbilityLine(
                                    this.location, charmer.getLocation(), dist - minVictimDist);
                    this.moveWithCollision(movementLine.getP2());
                }
            }
        }
    }

    @Override
    public boolean canMove() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.AIRBORNE) {
                if (this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    @Override
    public void handleCharm(UserActor charmer, int duration) {
        this.addState(ActorState.CHARMED, 0d, duration);
        this.charmer = charmer;
    }

    @Override
    public void setTarget(Actor a) {}
}

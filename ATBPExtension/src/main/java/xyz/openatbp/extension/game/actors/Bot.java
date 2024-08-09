package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class Bot extends Actor {
    private Point2D spawnPoint;
    private UserActor charmer;
    private static boolean movementDebug = false;

    public Bot(
            ATBPExtension parentExt,
            Room room,
            String avatar,
            int team,
            Point2D spawnPoint,
            int num) {
        this.parentExt = parentExt;
        this.room = room;
        this.team = team;
        this.id = avatar + team + num;
        this.location = spawnPoint;
        this.avatar = avatar;
        this.currentHealth = 750;
        this.maxHealth = 750;
        this.actorType = ActorType.COMPANION;
        this.spawnPoint = spawnPoint;
        this.stats = this.initializeStats();
        ExtensionCommands.createActor(
                parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
        if (movementDebug) {
            ExtensionCommands.createActor(parentExt, room, "test", "creep1", location, 0f, 1);
        }
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
    public void attack(Actor a) {}

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
        if (movementDebug) {
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    "test",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    true);
        }

        moveTowardsSpawnPoint();
        moveTowardsCharmer();
    }

    protected void moveTowardsSpawnPoint() {
        if (this.location.distance(spawnPoint) > 0.1 && canMove && !getState(ActorState.CHARMED)) {
            moveWithCollision(spawnPoint);
        }
    }

    protected void moveTowardsCharmer() {
        if (this.getState(ActorState.CHARMED) && charmer != null) {
            moveTowardsCharmer(charmer);
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

package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class Bot extends Actor {
    protected Point2D spawnPoint;
    protected UserActor charmer;
    protected static boolean movementDebug = false;
    protected static final int deathTimeSeconds = 3;

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
        this.currentHealth = 500;
        this.maxHealth = 500;
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
    public void attack(Actor a) {}

    @Override
    public void die(Actor a) {
        if (this.dead) return;
        this.dead = true;
        this.currentHealth = 0;
        this.setHealth(0, (int) this.maxHealth);
        this.target = null;
        this.canMove = false;
        ExtensionCommands.knockOutActor(parentExt, room, id, a.getId(), deathTimeSeconds);
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) a;
            killer.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(killer, killer.getTeam(), 25);
            killer.addXP(150);
        }
        if (!room.getGroupId().equals("Tutorial")) {
            Runnable respawn = this::respawn;
            parentExt.getTaskScheduler().schedule(respawn, deathTimeSeconds, TimeUnit.SECONDS);
        }
    }

    protected void respawn() {
        this.canMove = true;
        this.setHealth((int) maxHealth, (int) maxHealth);
        Point2D respawnPoint = spawnPoint;
        this.location = respawnPoint;
        this.movementLine = new Line2D.Float(respawnPoint, respawnPoint);
        this.timeTraveled = 0f;
        this.dead = false;
        this.removeEffects();
        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);
        ExtensionCommands.respawnActor(parentExt, room, id);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "champion_respawn_effect",
                1000,
                id + "_respawn",
                true,
                "Bip001",
                false,
                false,
                team);
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

        for (UserActor ua : this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
            if (!ua.getId().equalsIgnoreCase(this.id)) {
                if (ua.getLocation().distance(this.location) <= 5f)
                    ua.setGlassesBuff(
                            ChampionData.getCustomJunkStat(ua, "junk_2_simon_petrikovs_glasses"),
                            ua);
                else ua.setGlassesBuff(-1d, ua);
            }
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

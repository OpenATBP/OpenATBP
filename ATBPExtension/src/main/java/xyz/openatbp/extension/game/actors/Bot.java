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
    private static final boolean movementDebug = true;
    private static final int RESPAWN_TIME_MS = 3000;
    private final Point2D spawnPoint;

    public Bot(ATBPExtension parentExt, Room room, String avatar, int team, Point2D spawnPoint) {
        this.room = room;
        this.parentExt = parentExt;
        this.currentHealth = 500;
        this.maxHealth = 500;
        this.location = spawnPoint;
        this.avatar = avatar;
        this.id = avatar + "_" + team;
        this.team = team;
        this.actorType = ActorType.COMPANION;
        this.stats = initializeStats();
        this.spawnPoint = spawnPoint;

        ExtensionCommands.createActor(parentExt, room, id, avatar, spawnPoint, 0f, team);

        if (movementDebug) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "moveDebug", "creep1", location, 0f, 1);
        }
    }

    @Override
    public void die(Actor a) {
        dead = true;
        currentHealth = 0;
        if (!this.getState(ActorState.AIRBORNE)) this.stopMoving();
        ExtensionCommands.knockOutActor(parentExt, room, id, a.getId(), RESPAWN_TIME_MS);

        Runnable respawn = this::respawn;
        parentExt.getTaskScheduler().schedule(respawn, RESPAWN_TIME_MS, TimeUnit.MILLISECONDS);

        if (a.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) a;
            killer.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(killer, killer.getTeam(), 25);
            killer.addXP(getXPValue(killer));
        }
    }

    @Override
    public void update(int msRan) {
        if (dead) return;
        handleDamageQueue();
        handleActiveEffects();

        if (movementLine != null) {
            location =
                    MovementManager.getRelativePoint(
                            movementLine, getPlayerStat("speed"), timeTraveled);
        } else {
            movementLine = new Line2D.Float(this.location, this.location);
        }

        if (movementLine.getP1().distance(movementLine.getP2()) > 0.01d) {
            timeTraveled += 0.1f;
        }

        if (movementDebug) {
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id + "moveDebug",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    true);
        }

        for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
            if (!ua.getId().equalsIgnoreCase(id)) {
                if (ua.getLocation().distance(location) <= 5f)
                    ua.setGlassesBuff(
                            ChampionData.getCustomJunkStat(ua, "junk_2_simon_petrikovs_glasses"),
                            ua);
                else ua.setGlassesBuff(-1d, ua);
            }
        }
    }

    private int getXPValue(UserActor killer) {
        int killerLV = killer.getLevel();
        switch (killerLV) {
            case 1:
                return 100;
            case 2:
                return 110;
            case 3:
                return 140;
            case 4:
                return 170;
            case 5:
                return 200;
            case 6:
                return 230;
            case 7:
                return 260;
            case 8:
                return 290;
            case 9:
                return 320;
            default:
                return 150;
        }
    }

    public void respawn() {
        dead = false;
        setHealth((int) maxHealth, (int) maxHealth);
        setLocation(spawnPoint);
        removeEffects();
        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);
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

        ExtensionCommands.respawnActor(parentExt, room, id);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public void attack(Actor a) {}

    @Override
    public void setTarget(Actor a) {}
}

package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;

public class Base extends Actor {
    private boolean unlocked = false;
    private long lastHit = -1;
    private Long lastAction = 0L;

    public Base(ATBPExtension parentExt, Room room, int team) {
        this.currentHealth = 3500;
        this.maxHealth = 3500;
        this.team = team;
        this.location = GameModeSpawns.getBaseLocationForMode(room, team);
        if (team == 0) this.id = "base_purple";
        else this.id = "base_blue";
        this.parentExt = parentExt;
        this.avatar = id;
        this.actorType = ActorType.BASE;
        this.room = room;
        this.stats = this.initializeStats();
        ExtensionCommands.updateActorState(parentExt, room, this.id, ActorState.INVINCIBLE, true);
        ExtensionCommands.updateActorState(parentExt, room, this.id, ActorState.IMMUNITY, true);

        if (room.getGroupId().equals("Tutorial")) {
            this.currentHealth = 450;
            this.maxHealth = 450;
        }
    }

    public int getTeam() {
        return this.team;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (!this.unlocked) return false;
        lastAction = System.currentTimeMillis();
        this.currentHealth -= getMitigatedDamage(damage, AttackType.PHYSICAL, a);
        if (System.currentTimeMillis() - this.lastHit >= 15000) {
            this.lastHit = System.currentTimeMillis();
            RoomHandler handler = parentExt.getRoomHandler(room.getName());

            for (UserActor ua : handler.getPlayers()) {
                if (ua.getTeam() == this.team)
                    ExtensionCommands.playSound(
                            parentExt,
                            ua.getUser(),
                            "global",
                            "announcer/base_under_attack",
                            new Point2D.Float(0, 0));
            }
        }
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) currentHealth);
        updateData.putDouble("pHealth", getPHealth());
        updateData.putInt("maxHealth", (int) maxHealth);
        ExtensionCommands.updateActorData(parentExt, this.room, updateData);
        return !(currentHealth > 0);
    }

    @Override
    public void attack(Actor a) {
        Console.logWarning("Bases can't attack silly!");
    }

    @Override
    public void die(Actor a) {
        if (this.currentHealth <= 0) {
            try {
                int oppositeTeam = 0;
                if (this.team == 0) oppositeTeam = 1;
                parentExt.getRoomHandler(this.room.getName()).gameOver(oppositeTeam);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        handleStructureRegen(lastAction, BaseTower.TIME_REQUIRED_TO_REGEN, BaseTower.REGEN_VALUE);
    }

    @Override
    public void setTarget(Actor a) {}

    public void unlock() {
        unlocked = true;
        ExtensionCommands.updateActorState(parentExt, room, this.id, ActorState.INVINCIBLE, false);
        ExtensionCommands.updateActorState(parentExt, room, this.id, ActorState.IMMUNITY, false);
    }

    public boolean isUnlocked() {
        return this.unlocked;
    }
}

package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;

public class BaseTower extends Tower {
    private boolean isUnlocked = false;
    private Long lastAction = 0L;
    protected static final int TIME_REQUIRED_TO_REGEN = 8000;
    protected static final float REGEN_VALUE = 0.002f;

    public BaseTower(ATBPExtension parentExt, Room room, String id, int team) {
        super(parentExt, room, id, team);
        this.location = GameModeSpawns.getBaseTowerLocationForMode(room, team);
        ExtensionCommands.updateActorState(parentExt, room, id, ActorState.INVINCIBLE, true);
        ExtensionCommands.updateActorState(parentExt, room, id, ActorState.IMMUNITY, true);
        ExtensionCommands.createWorldFX(
                parentExt,
                room,
                this.id,
                "fx_target_ring_6",
                this.id + "_ring",
                15 * 60 * 1000,
                (float) this.location.getX(),
                (float) this.location.getY(),
                true,
                this.team,
                0f);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        lastAction = System.currentTimeMillis();
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void attack(Actor a) {
        lastAction = System.currentTimeMillis();
        super.attack(a);
    }

    @Override
    public void update(int msRan) {
        handleStructureRegen(lastAction, TIME_REQUIRED_TO_REGEN, REGEN_VALUE);
        super.update(msRan);
    }

    @Override
    protected String getTowerDownSound(UserActor ua) {
        return ua.getTeam() == this.team ? "base_tower_down" : "you_destroyed_tower";
    }

    @Override
    public int getTowerNum() { // Gets tower number for the client to process correctly
        if (this.team == 0) return 0;
        else return 3;
    }

    public void unlockBaseTower() {
        this.isUnlocked = true;
        ExtensionCommands.updateActorState(parentExt, room, id, ActorState.INVINCIBLE, false);
        ExtensionCommands.updateActorState(parentExt, room, this.id, ActorState.IMMUNITY, false);
    }

    public boolean isUnlocked() {
        return this.isUnlocked;
    }
}

package xyz.openatbp.extension.game.actors;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;

public class BaseTower extends Tower {
    private boolean isUnlocked = false;

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

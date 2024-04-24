package xyz.openatbp.extension.game;

import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.Actor;

public class FxHandler {
    private Actor parent;
    private String fxAsset;
    private String fxId;
    private String emit;
    private long currentDuration;
    private long nextDuration;

    public FxHandler(Actor parent, String fxAsset, int duration) {
        this.parent = parent;
        this.fxAsset = fxAsset;
        this.fxId = parent.getId() + "_" + fxAsset;
        this.emit = "";
        this.currentDuration = System.currentTimeMillis() + duration;
        this.nextDuration = -1;
        this.displayFx();
    }

    public FxHandler(Actor parent, String fxAsset, String emit, int duration) {
        this.parent = parent;
        this.fxAsset = fxAsset;
        this.fxId = parent.getId() + "_" + fxAsset;
        this.emit = emit;
        this.currentDuration = System.currentTimeMillis() + duration;
        this.nextDuration = -1;
        this.displayFx();
    }

    public boolean update() {
        if (System.currentTimeMillis() > this.currentDuration && this.nextDuration != -1) {
            this.currentDuration = this.nextDuration;
            this.nextDuration = -1;
            this.displayFx();
            return false;
        } else return System.currentTimeMillis() > this.currentDuration;
    }

    public void addFx(int duration) {
        long endTime = System.currentTimeMillis() + duration;
        if (endTime > this.nextDuration) this.nextDuration = endTime;
    }

    public void displayFx() {
        ExtensionCommands.createActorFX(
                this.parent.getParentExt(),
                this.parent.getRoom(),
                this.parent.getId(),
                this.fxAsset,
                (int) (this.currentDuration -= System.currentTimeMillis()),
                this.fxId,
                true,
                this.emit,
                true,
                false,
                this.parent.getTeam());
    }

    public void forceStopFx() {
        ExtensionCommands.removeFx(
                this.parent.getParentExt(), this.parent.getRoom(), this.parent.getId());
        this.currentDuration = -1;
        this.nextDuration = -1;
    }
}

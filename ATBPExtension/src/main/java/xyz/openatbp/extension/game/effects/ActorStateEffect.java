package xyz.openatbp.extension.game.effects;

public class ActorStateEffect {
    private ActorState state;
    private String stateId;
    private int durationMs;
    private long startTime;
    private double modifier;
    private String fxId = null;

    public ActorStateEffect(ActorState state, String stateId, double modifier, int durationMs) {
        this.state = state;
        this.stateId = stateId;
        this.durationMs = durationMs;
        this.modifier = modifier;
        this.startTime = System.currentTimeMillis();
    }

    public ActorState getState() {
        return this.state;
    }

    public String getStateId() {
        return this.stateId;
    }

    public double getModifier() {
        return this.modifier;
    }

    public ModifierIntent getIntent() {
        switch (this.state) {
            case INVINCIBLE:
            case INVISIBLE:
            case IMMUNITY:
            case CLEANSED:
                return ModifierIntent.BUFF;
            default:
                return ModifierIntent.DEBUFF;
        }
    }

    public int getDurationMs() {
        return this.durationMs;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.startTime >= this.durationMs;
    }

    public void setFxId(String fxId) {
        this.fxId = fxId;
    }
}

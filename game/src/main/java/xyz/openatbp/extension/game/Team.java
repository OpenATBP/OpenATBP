package xyz.openatbp.extension.game;

public enum Team {
    BLUE(1),
    PURPLE(0);

    private final int value;

    Team(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

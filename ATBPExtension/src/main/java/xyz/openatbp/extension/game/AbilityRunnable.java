package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class AbilityRunnable implements Runnable {
    protected int abilityNum;
    protected JsonNode spellData;
    protected int cooldown;
    protected int gCooldown;
    protected Point2D dest;

    public AbilityRunnable(
            int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
        this.abilityNum = ability;
        this.spellData = spellData;
        this.cooldown = cooldown;
        this.gCooldown = gCooldown;
        this.dest = dest;
    }

    public void run() {
        switch (this.abilityNum) {
            case 1:
                this.spellQ();
                break;
            case 2:
                this.spellW();
                break;
            case 3:
                this.spellE();
                break;
            case 4:
                this.spellPassive();
                break;
        }
    }

    protected abstract void spellQ();

    protected abstract void spellW();

    protected abstract void spellE();

    protected abstract void spellPassive();
}

package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;

public class TutorialSuperMinion extends Actor {
    private int num;

    public TutorialSuperMinion(ATBPExtension parentExt, Room room, Point2D spawnPoint, int num) {
        this.parentExt = parentExt;
        this.room = room;
        this.dead = false;
        this.location = spawnPoint;
        this.avatar = "creep1_super";
        this.currentHealth = 10;
        this.maxHealth = 10;
        this.team = 1;
        this.id = "creep1_super" + team + num;
        this.actorType = ActorType.MINION;
        this.stats = this.initializeStats();
        this.num = num;
        this.xpWorth = num == 2 ? ChampionData.getXPLevels(0) : 0;
        ExtensionCommands.createActor(
                parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
    }

    public int getNum() {
        return this.num;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {}

    @Override
    public void attack(Actor a) {}

    @Override
    public void die(Actor a) {
        this.dead = true;
        this.currentHealth = 0;
        ExtensionCommands.destroyActor(parentExt, room, this.id);
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
    }

    @Override
    public void setTarget(Actor a) {}
}

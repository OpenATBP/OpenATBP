package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.game.Champion;

public class GumballGuardian extends Tower {

    public GumballGuardian(
            ATBPExtension parentExt, Room room, String id, int team, Point2D location) {
        super(parentExt, room, id, team, location);
        System.out.println("Invalid constructor used!");
        this.avatar = "gumball_guardian";
        this.displayName = "Gumball Guardian";
        this.team = team;
        this.parentExt = parentExt;
        this.room = room;
        this.id = "gumball_guardian" + team;
        this.currentHealth = 99999;
        this.maxHealth = 99999;
    }

    public GumballGuardian(ATBPExtension parentExt, Room room, int team) {
        super(parentExt, room, team);
        this.avatar = "gumball_guardian";
        this.displayName = "Gumball Guardian";
        this.id = "gumball" + team;
        this.currentHealth = 99999;
        this.maxHealth = 99999;
        this.location = MapData.getGuardianLocationData(team, room.getGroupId());
        this.stats = this.initializeStats();
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
    public void attack(Actor a) {
        this.attackCooldown = 800;
        String projectileName = "gumballGuardian_projectile";
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(
                this.parentExt,
                this.room,
                projectileName,
                this.id,
                a.getId(),
                "emitNode",
                "Bip01",
                time);
        SmartFoxServer.getInstance()
                .getTaskScheduler()
                .schedule(
                        new Champion.DelayedAttack(this.parentExt, this, a, 1000, "basicAttack"),
                        (int) (time * 1000),
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) {
        System.out.println(a.getDisplayName() + " somehow killed a guardian!");
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        return false;
    }

    @Override
    public void targetPlayer(UserActor user) {
        ExtensionCommands.setTarget(this.parentExt, user.getUser(), this.id, user.getId());
        ExtensionCommands.createWorldFX(
                this.parentExt,
                user.getUser(),
                user.getId(),
                "tower_danger_alert",
                this.id + "_aggro",
                1000 * 60 * 15,
                (float) this.location.getX(),
                (float) this.location.getY(),
                false,
                this.team,
                0f);
        ExtensionCommands.playSound(
                this.parentExt,
                user.getUser(),
                user.getId(),
                "sfx_turret_has_you_targeted",
                this.location);
    }
}

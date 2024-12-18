package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.actors.UserActor;

public class GooMonster extends Monster {

    private int abilityCooldown;
    private boolean usingAbility;
    private Point2D puddleLocation;
    private boolean puddleActivated;
    private long puddleStarted;
    private static final int GOO_BUFF_DURATION = 60000;

    public GooMonster(
            ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName) {
        super(parentExt, room, startingLocation, monsterName);
        this.abilityCooldown = 5000;
        this.usingAbility = false;
        this.puddleActivated = false;
        this.puddleStarted = -1;
        this.puddleLocation = null;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (!this.usingAbility && this.abilityCooldown > 0) this.abilityCooldown -= 100;
        if (this.puddleActivated) {
            if (System.currentTimeMillis() - puddleStarted >= 3000) {
                puddleActivated = false;
                puddleStarted = -1;
                puddleLocation = null;
            } else {
                try {
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    List<Actor> damagedActors =
                            Champion.getActorsInRadius(handler, puddleLocation, 2f);
                    JsonNode attackData = parentExt.getAttackData(this.avatar, "basicAttack");
                    ObjectMapper mapper = new ObjectMapper();
                    ISFSObject data = new SFSObject();
                    data.putUtfString("attackName", attackData.get("specialAttackName").asText());
                    data.putUtfString(
                            "attackDescription",
                            attackData.get("specialAttackDescription").asText());
                    data.putUtfString(
                            "attackIconImage", attackData.get("specialAttackIconImage").asText());
                    data.putUtfString("attackType", "spell");
                    JsonNode newAttackData = mapper.readTree(data.toJson());
                    for (Actor a : damagedActors) {
                        if (!a.getId().equalsIgnoreCase(this.id)) {
                            a.addState(ActorState.SLOWED, 0.25d, 1000);
                            a.addToDamageQueue(this, 4, newAttackData, true);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void die(Actor a) {
        if (isProperKiller(a)) {
            List<UserActor> players = parentExt.getRoomHandler(room.getName()).getPlayers();
            int killerTeam = a.getTeam();

            for (UserActor ua : players) {
                String sound = ua.getTeam() == killerTeam ? "you_goomonster" : "enemy_goomonster";
                String finalSound = "announcer/" + sound;
                ExtensionCommands.playSound(parentExt, ua.getUser(), "global", finalSound);

                if (ua.getTeam() == killerTeam && ua.getHealth() > 0 && !ua.isDead()) {
                    double delta = ua.getPlayerStat("speed") * 0.1d;
                    if (ChampionData.getCustomJunkStat(ua, "junk_1_demon_blood_sword") > 0)
                        delta += 0.15;
                    ua.setHasGooBuff(true);
                    ua.setGooBuffStartTime(System.currentTimeMillis());
                    ua.addEffect("speed", delta, GOO_BUFF_DURATION, "jungle_buff_goo", "");
                    ExtensionCommands.addStatusIcon(
                            this.parentExt,
                            ua.getUser(),
                            "goomonster_buff",
                            "goomonster_buff_desc",
                            "icon_buff_goomonster",
                            GOO_BUFF_DURATION);
                }
            }
        }
        super.die(a);
    }

    private boolean isProperKiller(Actor a) {
        ActorType[] types = {ActorType.PLAYER, ActorType.COMPANION};
        for (ActorType type : types) {
            if (a.getActorType() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void attack(Actor a) {
        this.stopMoving();
        this.canMove = false;
        if (!this.usingAbility && this.abilityCooldown <= 0) {
            this.usingAbility = true;
            ExtensionCommands.playSound(
                    parentExt, room, id, "sfx/sfx_goo_monster_growl", this.location);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "goo_monster_spell_glob",
                    1250,
                    id + "_glob",
                    true,
                    "fxNode",
                    true,
                    false,
                    team);
            ExtensionCommands.actorAnimate(parentExt, room, id, "spell", 1250, false);
            Runnable oozeAttack =
                    () -> {
                        puddleActivated = true;
                        puddleLocation = a.getLocation();
                        abilityCooldown = 8000;
                        usingAbility = false;
                        canMove = true;
                        puddleStarted = System.currentTimeMillis();
                        ExtensionCommands.playSound(
                                parentExt, room, "", "sfx/sfx_goo_monster_puddle", puddleLocation);
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "goo_monster_puddle",
                                id + "_puddle",
                                3000,
                                (float) puddleLocation.getX(),
                                (float) puddleLocation.getY(),
                                false,
                                team,
                                0f);
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "fx_target_ring_2",
                                id + "_puddle_circle",
                                3000,
                                (float) puddleLocation.getX(),
                                (float) puddleLocation.getY(),
                                true,
                                2,
                                0f);
                    };
            parentExt.getTaskScheduler().schedule(oozeAttack, 1250, TimeUnit.MILLISECONDS);
        } else if (!this.usingAbility) {
            this.attackCooldown = 1200;
            int attackDamage = (int) this.getPlayerStat("attackDamage");
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    false,
                    true);
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            new Champion.DelayedRangedAttack(this, a), 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void rangedAttack(Actor a) {
        this.attackCooldown = this.getPlayerStat("attackSpeed");
        int attackDamage = (int) this.getPlayerStat("attackDamage");
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(
                parentExt,
                room,
                "goo_projectile",
                this.id,
                a.getId(),
                "Bip01 R Hand",
                "Bip01",
                time);
        parentExt
                .getTaskScheduler()
                .schedule(
                        new Champion.DelayedAttack(parentExt, this, a, attackDamage, "basicAttack"),
                        (int) (time * 1000),
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean canAttack() {
        if (this.usingAbility) return false;
        return super.canAttack();
    }

    @Override
    public String getPortrait() {
        return "goomonster";
    }
}

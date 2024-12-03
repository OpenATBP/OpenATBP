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
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.actors.UserActor;

public class Keeoth extends Monster {

    private int abilityCooldown;
    private boolean usingAbility;
    private static final int KEEOTH_BUFF_DURATION = 90000;

    public Keeoth(
            ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName) {
        super(parentExt, room, startingLocation, monsterName);
        this.abilityCooldown = 3000;
        this.usingAbility = false;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (!this.usingAbility && this.abilityCooldown > 0) this.abilityCooldown -= 100;
    }

    @Override
    public void die(Actor a) {
        if (isProperKiller(a)) {
            List<UserActor> players = parentExt.getRoomHandler(room.getName()).getPlayers();
            int killerTeam = a.getTeam();

            for (UserActor ua : players) {
                String sound = ua.getTeam() == killerTeam ? "you_keeoth" : "enemy_keeoth";
                String finalSound = "announcer/" + sound;
                ExtensionCommands.playSound(parentExt, ua.getUser(), "global", finalSound);

                if (ua.getTeam() == killerTeam && ua.getHealth() > 0 && !ua.isDead()) {
                    ua.setHasKeeothBuff(true);
                    ua.setKeeothBuffStartTime(System.currentTimeMillis());
                    ua.addEffect("lifeSteal", 35d, KEEOTH_BUFF_DURATION, "jungle_buff_keeoth", "");
                    ua.addEffect("spellVamp", 40d, KEEOTH_BUFF_DURATION);
                    double critChange = 35d;
                    if(ChampionData.getCustomJunkStat(ua,"junk_1_demon_blood_sword") > 0) critChange+=5d;
                    ua.addEffect("criticalChance", critChange, KEEOTH_BUFF_DURATION);
                    double healthChange = (double) ua.getHealth() * 0.3d;
                    ua.heal((int) healthChange); //TODO: Maybe change?
                    ExtensionCommands.addStatusIcon(
                            this.parentExt,
                            ua.getUser(),
                            "keeoth_buff",
                            "keeoth_buff_desc",
                            "icon_buff_keeoth",
                            KEEOTH_BUFF_DURATION);
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

        if (!this.usingAbility && this.abilityCooldown <= 0) {
            this.usingAbility = true;
            this.stopMoving();
            this.canMove = false;
            Point2D playerLoc = a.getLocation();
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "lemongrab_ground_aoe_target",
                    id + "specialCurcle",
                    2500,
                    (float) playerLoc.getX(),
                    (float) playerLoc.getY(),
                    true,
                    2,
                    0f);
            ExtensionCommands.actorAnimate(parentExt, room, id, "spell", 1250, false);
            Runnable keeothSpecial =
                    () -> {
                        ExtensionCommands.playSound(
                                parentExt, room, "", "sfx_keeoth_explosion", playerLoc);
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "keeoth_explosion",
                                id + "_explosion",
                                2000,
                                (float) playerLoc.getX(),
                                (float) playerLoc.getY(),
                                true,
                                team,
                                0f);
                        usingAbility = false;
                        canMove = true;
                        abilityCooldown = 1100;
                        Runnable specialDamage =
                                () -> {
                                    try {
                                        abilityCooldown = 3000;
                                        JsonNode attackData =
                                                parentExt.getAttackData(this.avatar, "basicAttack");
                                        ObjectMapper mapper = new ObjectMapper();
                                        ISFSObject data = new SFSObject();
                                        data.putUtfString(
                                                "attackName",
                                                attackData.get("specialAttackName").asText());
                                        data.putUtfString(
                                                "attackDescription",
                                                attackData
                                                        .get("specialAttackDescription")
                                                        .asText());
                                        data.putUtfString(
                                                "attackIconImage",
                                                attackData.get("specialAttackIconImage").asText());
                                        data.putUtfString("attackType", "spell");
                                        JsonNode newAttackData = mapper.readTree(data.toJson());

                                        RoomHandler handler =
                                                parentExt.getRoomHandler(room.getName());
                                        for (Actor actor :
                                                Champion.getActorsInRadius(
                                                        handler, playerLoc, 2.5f)) {
                                            if (actor.getActorType() == ActorType.PLAYER
                                                    || actor.getActorType()
                                                            == ActorType.COMPANION) {
                                                double dist =
                                                        actor.getLocation().distance(playerLoc);
                                                if (dist > 1)
                                                    actor.addToDamageQueue(
                                                            Keeoth.this, 150, newAttackData, false);
                                                else
                                                    actor.addToDamageQueue(
                                                            Keeoth.this, 450, newAttackData, false);
                                            }
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                };
                        parentExt
                                .getTaskScheduler()
                                .schedule(specialDamage, 1000, TimeUnit.MILLISECONDS);
                    };
            parentExt.getTaskScheduler().schedule(keeothSpecial, 1250, TimeUnit.MILLISECONDS);
        } else if (!this.usingAbility) {
            super.attack(a);
        }
    }

    @Override
    public boolean canAttack() {
        if (this.usingAbility) return false;
        return super.canAttack();
    }

    @Override
    public boolean withinRange(Actor a) {
        if (this.abilityCooldown == 0) return a.getLocation().distance(this.location) <= 5;
        return super.withinRange(a);
    }
}

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Lemongrab extends UserActor {

    private int unacceptableLevels = 0;
    private long lastHit = -1;
    private String lastIcon = "lemon0";
    private boolean isCastingUlt = false;
    private boolean juice = false;
    private static final float Q_OFFSET_DISTANCE_BOTTOM = 1.5f;
    private static final float Q_OFFSET_DISTANCE_TOP = 4f;
    private static final float Q_SPELL_RANGE = 6f;

    public Lemongrab(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "lemon0", "UNACCEPTABLE!!!!!", "icon_lemongrab_passive", 0f);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (!this.dead
                && this.unacceptableLevels < 3
                && System.currentTimeMillis() - lastHit >= 2000
                && this.getAttackType(attackData) == AttackType.SPELL) {
            this.unacceptableLevels++;
            String iconName = "lemon" + this.unacceptableLevels;
            ExtensionCommands.removeStatusIcon(parentExt, player, lastIcon);
            this.lastIcon = iconName;
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    iconName,
                    "UNACCEPTABLE!!!!!",
                    "icon_lemongrab_p" + this.unacceptableLevels,
                    2000f);
            String voiceLinePassive = "";
            switch (this.unacceptableLevels) {
                case 1:
                    voiceLinePassive = "vo/lemongrab_ooo";
                    break;
                case 2:
                    voiceLinePassive = "vo/lemongrab_haha";
                    break;
                case 3:
                    voiceLinePassive = "vo/lemongrab_unacceptable";
                    break;
            }
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, voiceLinePassive, this.location);
            this.lastHit = System.currentTimeMillis();
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.unacceptableLevels > 0 && System.currentTimeMillis() - lastHit >= 6000) {
            this.unacceptableLevels--;
            String iconName = "lemon" + this.unacceptableLevels;
            ExtensionCommands.removeStatusIcon(parentExt, player, lastIcon);
            this.lastIcon = iconName;
            if (this.unacceptableLevels != 0)
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        iconName,
                        "UNACCEPTABLE!!!!!",
                        "icon_lemongrab_p" + this.unacceptableLevels,
                        0f);
            else
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        iconName,
                        "UNACCEPTABLE!!!!!",
                        "icon_lemongrab_passive",
                        0f);
            this.lastHit = System.currentTimeMillis();
        }
    }

    @Override
    public void die(Actor a) {
        this.unacceptableLevels = 0;
        ExtensionCommands.removeStatusIcon(parentExt, player, lastIcon);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "lemon0", "UNACCEPTABLE!!!!!", "icon_lemongrab_passive", 0f);
        lastIcon = "lemon" + unacceptableLevels;
        super.die(a);
    }

    public boolean canMove() {
        if (this.isCastingUlt) return false;
        return super.canMove();
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        super.useAbility(ability, spellData, cooldown, gCooldown, castDelay, dest);
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                Path2D trapezoid =
                        Champion.createTrapezoid(
                                location,
                                dest,
                                Q_SPELL_RANGE,
                                Q_OFFSET_DISTANCE_BOTTOM,
                                Q_OFFSET_DISTANCE_TOP);
                for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getActors()) {
                    if (a.getTeam() != this.team && trapezoid.contains(a.getLocation())) {
                        if (isNonStructure(a)) a.addState(ActorState.SLOWED, 0.4d, 2500);
                        a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                    }
                }
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_lemongrab_sound_sword",
                        this.location);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_lemongrab_sound_sword",
                        this.location);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "lemongrab_sonic_sword_effect",
                        750,
                        this.id + "_sonicSword",
                        true,
                        "Sword",
                        true,
                        false,
                        this.team);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new LemonAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "lemongrab_ground_aoe_target",
                        this.id + "wTarget",
                        castDelay,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
                ExtensionCommands.playSound(parentExt, room, "", "sfx_lemongrab_my_juice", dest);
                Runnable delayedJuice =
                        () -> {
                            if (getHealth() > 0) {
                                juice = true;
                                ExtensionCommands.createWorldFX(
                                        parentExt,
                                        room,
                                        id,
                                        "lemongrab_ground_juice_aoe",
                                        id + "_wJuice",
                                        2000,
                                        (float) dest.getX(),
                                        (float) dest.getY(),
                                        false,
                                        team,
                                        0f);
                            }
                        };
                parentExt.getTaskScheduler().schedule(delayedJuice, 500, TimeUnit.MILLISECONDS);
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "vo/vo_lemongrab_my_juice",
                        this.location);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new LemonAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                1000,
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                this.isCastingUlt = true;
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "e", true, getReducedCooldown(cooldown), 1000);
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_2.5",
                        this.id + "_jailRing",
                        castDelay,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new LemonAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                1000,
                                TimeUnit.MILLISECONDS);
                String voiceLine = "lemongrab_dungeon_3hours";
                switch (this.unacceptableLevels) {
                    case 1:
                        voiceLine = "lemongrab_dungeon_30days";
                        break;
                    case 2:
                        voiceLine = "lemongrab_dungeon_12years";
                        break;
                    case 3:
                        voiceLine = "lemongrab_dungeon_1myears";
                        break;
                }
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, voiceLine, this.location);
                break;
            case 4:
                break;
        }
    }

    private class LemonAbilityHandler extends AbilityRunnable {

        public LemonAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            int W_CAST_DELAY = 1000;
            Runnable enableWCasting = () -> canCast[1] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableWCasting,
                            getReducedCooldown(cooldown) - W_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            if (juice) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "lemongrab_head_splash",
                        id + "_wHead",
                        500,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                List<Actor> affectedActors = new ArrayList<>();
                for (Actor a :
                        Champion.getActorsInRadius(
                                parentExt.getRoomHandler(room.getName()), dest, 1f)) {
                    if (isNonStructure(a)) {
                        affectedActors.add(a);
                        a.addToDamageQueue(
                                Lemongrab.this, getSpellDamage(spellData), spellData, false);
                        a.addState(ActorState.BLINDED, 0d, 4000);
                        a.addState(ActorState.SILENCED, 0d, 2000);
                    }
                }
                for (Actor a :
                        Champion.getActorsInRadius(
                                parentExt.getRoomHandler(room.getName()), dest, 2f)) {
                    if (isNonStructure(a) && !affectedActors.contains(a)) {
                        double damage = 60d + (getPlayerStat("spellDamage") * 0.4d);
                        a.addState(ActorState.BLINDED, 0d, 4000);
                        a.addToDamageQueue(Lemongrab.this, damage, spellData, false);
                    }
                }
                juice = false;
            }
        }

        @Override
        protected void spellE() {
            int E_CAST_DELAY = 1000;
            Runnable enableECasting = () -> canCast[2] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableECasting,
                            getReducedCooldown(cooldown) - E_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            isCastingUlt = false;
            if (getHealth() > 0) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "lemongrab_dungeon_hit",
                        id + "_jailHit",
                        1000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                double damage = getSpellDamage(spellData);
                double duration = 2000d;
                damage *= (1d + (0.1d * unacceptableLevels));
                duration *= (1d + (0.1d * unacceptableLevels));
                for (Actor a :
                        Champion.getActorsInRadius(
                                parentExt.getRoomHandler(room.getName()), dest, 2.5f)) {
                    if (isNonStructure(a)) {
                        a.addToDamageQueue(Lemongrab.this, damage, spellData, false);
                        if (a.getActorType() == ActorType.PLAYER) {
                            a.addState(ActorState.STUNNED, 0d, (int) duration);
                        }
                        if (a.getActorType() == ActorType.PLAYER
                                && !a.getState(ActorState.IMMUNITY))
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    a.getId(),
                                    "lemongrab_lemon_jail",
                                    (int) duration,
                                    a.getId() + "_jailed",
                                    true,
                                    "",
                                    true,
                                    false,
                                    team);
                    }
                }
                unacceptableLevels = 0;
                ExtensionCommands.removeStatusIcon(parentExt, player, lastIcon);
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "lemon0",
                        "UNACCEPTABLE!!!",
                        "icon_lemongrab_passive",
                        0f);
                lastIcon = "lemon0";
            }
        }

        @Override
        protected void spellPassive() {}
    }
}

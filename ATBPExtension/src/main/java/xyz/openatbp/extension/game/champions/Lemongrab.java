package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;

public class Lemongrab extends UserActor {
    public static final int PASSIVE_COOLDOWN = 2000;
    public static final int PASSIVE_STACK_DURATION = 6000;
    public static final int Q_SLOW_DURATION = 2500;
    public static final double Q_SLOW_PERCENT = 0.4d;
    public static final float Q_OFFSET_DISTANCE_BOTTOM = 1.5f;
    public static final float Q_OFFSET_DISTANCE_TOP = 4f;
    public static final float Q_SPELL_RANGE = 6f;

    public static final int W_CAST_DELAY = 1000;
    public static final int W_BLIND_DURATION = 4000;
    public static final int W_SILENCE_DURATION = 2000;
    public static final int W_FX_DELAY = 500;
    public static final int W_DELAY = 1000;

    public static final double W_CENTER_DMG_MULTIPLIER = 1.25;
    public static final float W_RADIUS = 2.25f;
    public static final float E_RADIUS = 2.5f;

    private int unacceptableLevels = 0;
    private long lastHit = -1;
    private String lastIcon = "lemon0";
    private boolean isCastingUlt = false;
    private boolean juice = false;
    private int ultDelay;

    public Lemongrab(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "lemon0", "UNACCEPTABLE!!!!!", "icon_lemongrab_passive", 0f);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.unacceptableLevels > 0
                && System.currentTimeMillis() - lastHit >= PASSIVE_STACK_DURATION) {
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

    @Override
    public boolean canMove() {
        if (this.isCastingUlt) return false;
        return super.canMove();
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (!this.dead
                && this.unacceptableLevels < 3
                && System.currentTimeMillis() - lastHit >= PASSIVE_COOLDOWN
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
                    PASSIVE_COOLDOWN);
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
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                try {
                    stopMoving(castDelay);

                    AbilityShape qTrapezoid =
                            AbilityShape.createTrapezoid(
                                    location,
                                    dest,
                                    Q_SPELL_RANGE,
                                    Q_OFFSET_DISTANCE_BOTTOM,
                                    Q_OFFSET_DISTANCE_TOP);

                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> nearbyEnemies =
                            Champion.getActorsInRadius(handler, location, Q_SPELL_RANGE + 2);

                    if (!nearbyEnemies.isEmpty()) {
                        for (Actor a : nearbyEnemies) {
                            if (isNeitherStructureNorAlly(a)
                                    && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                                    && a.isNotLeaping()) {
                                a.getEffectManager()
                                        .addState(
                                                ActorState.SLOWED,
                                                id + "_lemon_q_slow",
                                                Q_SLOW_PERCENT,
                                                Q_SLOW_DURATION);
                            }

                            if (isNeitherTowerNorAlly(a)
                                    && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                                    && a.isNotLeaping()) {
                                double dmg = getSpellDamage(spellData, true);
                                a.addToDamageQueue(this, dmg, spellData, false);
                            }
                        }
                    }
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_lemongrab_sound_sword",
                            this.location);

                    playSoundWithChance("vo/vo_lemongrab_sound_sword", 50);

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
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "q",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    stopMoving(castDelay);
                    playSoundWithChance("vo/vo_lemongrab_my_juice", 50);

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
                    ExtensionCommands.playSound(
                            parentExt, room, "", "sfx_lemongrab_my_juice", dest);
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
                    scheduleTask(delayedJuice, W_FX_DELAY);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), W_DELAY);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    stopMoving();
                    this.isCastingUlt = true;
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
                    String voiceLine = "";
                    switch (this.unacceptableLevels) {
                        case 0:
                            voiceLine = "lemongrab_dungeon_3hours";
                            ultDelay = 1250;
                            break;
                        case 1:
                            voiceLine = "lemongrab_dungeon_30days";
                            ultDelay = 1000;
                            break;
                        case 2:
                            voiceLine = "lemongrab_dungeon_12years";
                            ultDelay = 750;
                            break;
                        case 3:
                            voiceLine = "lemongrab_dungeon_1myears";
                            ultDelay = 500;
                            break;
                    }
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, voiceLine, this.location);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, this.player, "e", true, getReducedCooldown(cooldown), 1000);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), ultDelay);
                break;
        }
    }

    private LemonAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new LemonAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class LemonAbilityRunnable extends AbilityRunnable {

        public LemonAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            scheduleTask(enableWCasting, W_CAST_DELAY + 1000);
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

                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                boolean hitPlayer = false;
                boolean hitAnything = false;

                for (Actor a : Champion.getActorsInRadius(handler, dest, W_RADIUS)) {

                    double distance = a.getLocation().distance(dest);
                    double damage = getSpellDamage(spellData, false);

                    if (distance <= 1 && isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.SILENCED,
                                        id + "_lemon_w_silence",
                                        0d,
                                        W_SILENCE_DURATION);
                    }

                    if (isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.BLINDED,
                                        id + "_lemon_w_blind",
                                        0d,
                                        W_BLIND_DURATION);
                    }

                    if (distance <= 1 && isNeitherTowerNorAlly(a)) {
                        damage *= W_CENTER_DMG_MULTIPLIER;
                    }

                    if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                        hitAnything = true;
                        if (a.getActorType() == ActorType.PLAYER) hitPlayer = true;
                        a.addToDamageQueue(Lemongrab.this, damage, spellData, false);
                    }
                }
                juice = false;

                if (hitAnything && !hitPlayer) {
                    ExtensionCommands.actorAbilityResponse(
                            parentExt,
                            player,
                            "w",
                            true,
                            (int) (Math.floor(getReducedCooldown(cooldown) * 0.7) - W_DELAY),
                            0);
                }
            }
        }

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - ultDelay;
            scheduleTask(enableECasting, delay);
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
                double damage = getSpellDamage(spellData, true);
                double duration = 2000d;
                damage *= (1d + (0.1d * unacceptableLevels));
                duration *= (1d + (0.1d * unacceptableLevels));
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, dest, E_RADIUS)) {

                    if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                        a.addToDamageQueue(Lemongrab.this, damage, spellData, false);
                    }

                    if ((a instanceof UserActor || a instanceof Bot)
                            && a.getTeam() != team
                            && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.STUNNED,
                                        id + "_lemon_e_stun",
                                        0d,
                                        (int) duration);

                        if (!a.getEffectManager().hasState(ActorState.IMMUNITY)) {
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

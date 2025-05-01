package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Gunter extends UserActor {
    private static final float E_OFFSET_DISTANCE_BOTTOM = 0.5f;
    private static final float E_OFFSET_DISTANCE_TOP = 5f;
    private static final float E_SPELL_RANGE = 8f;
    private static final int E_DURATION = 2500;
    private static final float PASSIVE_RADIUS = 2f;
    private boolean ultActivated = false;
    private long ultStartTime = 0;
    private int qTime;

    Path2D eTrapezoid = null;

    public Gunter(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultActivated && this.eTrapezoid != null) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(), "spell3");
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
            List<Actor> actorsInTrapezoid = handler.getEnemiesInPolygon(this.team, this.eTrapezoid);
            if (!actorsInTrapezoid.isEmpty()) {
                for (Actor a : actorsInTrapezoid) {
                    double damage = getSpellDamage(spellData, false) / 10d;
                    a.addToDamageQueue(this, damage, spellData, true);
                }
            }
        }
        if (this.ultActivated && this.hasInterrupingCC()) {
            this.interruptE();
            this.eTrapezoid = null;
            this.ultActivated = false;
        }
        if (this.ultActivated
                && System.currentTimeMillis() - ultStartTime >= 2000
                && !hasInterrupingCC()) {
            ExtensionCommands.actorAnimate(parentExt, room, id, "spell3c", 100, false);
        }
        if (this.ultActivated && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            ultActivated = false;
            eTrapezoid = null;
        }
    }

    @Override
    public boolean canUseAbility(int ability) {
        if (ultActivated && ability != 1) return false;
        else return super.canUseAbility(ability);
    }

    @Override
    public boolean canAttack() {
        if (this.ultActivated) return false;
        else return super.canAttack();
    }

    @Override
    public boolean canMove() {
        if (this.ultActivated) return false;
        else return super.canMove();
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultActivated) {
            this.ultActivated = false;
            this.eTrapezoid = null;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_gunterPower");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "gunterUlt");
            ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 500, false);
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (attackData.has("spellName") && attackData.get("spellName").asText().contains("spell_2"))
            this.shatter(a);
        else if (attackData.has("attackName")
                && attackData.get("attackName").asText().contains("Basic")) this.shatter(a);
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
                if (this.ultActivated) {
                    this.interruptE();
                    this.eTrapezoid = null;
                    this.ultActivated = false;
                }
                this.canCast[0] = false;
                Point2D finalDastPoint = this.location;
                try {
                    Point2D ogLocation = this.location;
                    finalDastPoint = this.dash(dest, true, DASH_SPEED);
                    double time = ogLocation.distance(finalDastPoint) / DASH_SPEED;
                    this.qTime = (int) (time * 1000);
                    ExtensionCommands.playSound(
                            parentExt, this.room, this.id, "sfx_gunter_slide", this.location);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            this.id,
                            "gunter_slide_trail",
                            qTime,
                            this.id + "_gunterTrail",
                            true,
                            "Bip01",
                            true,
                            false,
                            team);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            this.id,
                            "gunter_slide_snow",
                            qTime,
                            this.id + "_gunterTrail",
                            true,
                            "Bip01",
                            true,
                            false,
                            team);
                    ExtensionCommands.actorAnimate(parentExt, room, id, "spell1b", qTime, false);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, player, "q", true, getReducedCooldown(cooldown), gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDastPoint),
                        qTime);
                break;
            case 2:
                this.canCast[1] = false;
                try {
                    stopMoving();
                    if (getHealth() > 0) {
                        Line2D abilityLine = Champion.getAbilityLine(this.location, dest, 8f);
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, "", "sfx_gunter_wing_it", this.location);
                        this.fireProjectile(
                                new BottleProjectile(
                                        this.parentExt,
                                        this,
                                        abilityLine,
                                        11f,
                                        0.5f,
                                        "projectile_gunter_bottle"),
                                this.location,
                                dest,
                                8f);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    stopMoving();
                    this.ultStartTime = System.currentTimeMillis();
                    this.ultActivated = true;
                    this.eTrapezoid =
                            Champion.createTrapezoid(
                                    location,
                                    dest,
                                    E_SPELL_RANGE,
                                    E_OFFSET_DISTANCE_BOTTOM,
                                    E_OFFSET_DISTANCE_TOP);
                    ExtensionCommands.playSound(
                            parentExt, room, this.id, "sfx_gunter_bottles_ultimate", this.location);
                    ExtensionCommands.actorAnimate(parentExt, room, this.id, "spell3b", 2500, true);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            this.id,
                            "gunter_powered_up",
                            E_DURATION,
                            this.id + "_gunterPower",
                            true,
                            "Bip01",
                            true,
                            false,
                            team);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            this.id,
                            "gunter_bottle_cone",
                            E_DURATION,
                            this.id + "gunterUlt",
                            true,
                            "Bip01",
                            true,
                            false,
                            team);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt, player, "e", true, getReducedCooldown(cooldown), gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
        }
    }

    public void shatter(Actor a) {
        ExtensionCommands.playSound(
                parentExt, room, "", "sfx_gunter_slide_shatter", a.getLocation());
        ExtensionCommands.createWorldFX(
                parentExt,
                room,
                id,
                "gunter_belly_slide_bottles",
                a.getId() + "_shattered",
                1000,
                (float) a.getLocation().getX(),
                (float) a.getLocation().getY(),
                false,
                team,
                0f);
        RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
        List<Actor> enemyActorsInRadius =
                Champion.getEnemyActorsInRadius(
                        handler, this.team, a.getLocation(), PASSIVE_RADIUS);
        for (Actor actor : enemyActorsInRadius) {
            if (actor.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) {
                JsonNode spellData = this.parentExt.getAttackData(this.getAvatar(), "spell4");
                actor.addToDamageQueue(this, getSpellDamage(spellData, true), spellData, false);
            }
        }
    }

    private void interruptE() {
        ExtensionCommands.removeFx(parentExt, this.room, this.id + "_gunterPower");
        ExtensionCommands.removeFx(parentExt, this.room, this.id + "gunterUlt");
        ExtensionCommands.actorAnimate(parentExt, this.room, this.id, "run", 500, false);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
    }

    private GunterAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new GunterAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class GunterAbilityRunnable extends AbilityRunnable {

        public GunterAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - qTime;
            scheduleTask(enableQCasting, delay);
            if (getHealth() > 0 && !hasDashAttackInterruptCC()) {
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "gunter_belly_slide_bottles",
                        1500,
                        id + "_slide_bottles",
                        false,
                        "",
                        false,
                        false,
                        team);
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_gunter_slide_shatter", location);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell1c", 500, false);
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> affectedActors = Champion.getActorsInRadius(handler, location, 2f);
                for (Actor a : affectedActors) {
                    if (a.getTeam() != team && isNonStructure(a)) {
                        a.addToDamageQueue(
                                Gunter.this, getSpellDamage(spellData, true), spellData, false);
                    }
                }
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }

    private class BottleProjectile extends Projectile {

        public BottleProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(), "spell2");
            victim.addToDamageQueue(Gunter.this, getSpellDamage(spellData, true), spellData, false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_gunter_bottle_shatter", this.location);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    this.id,
                    "gunter_bottle_shatter",
                    this.id + "_bottleShatter",
                    1000,
                    (float) this.location.getX(),
                    (float) this.location.getY(),
                    false,
                    team,
                    0f);
            destroy();
        }
    }
}

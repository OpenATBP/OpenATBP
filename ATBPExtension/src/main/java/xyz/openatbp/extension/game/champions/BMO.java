package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class BMO extends UserActor {
    private int passiveStacks = 0;
    private boolean wActive = false;
    private long wStartTime = 0;
    private long lastWSound = 0;
    private boolean ultSlowActive = false;
    private static final float Q_OFFSET_DISTANCE_BOTTOM = 1.5f;
    private static final float Q_OFFSET_DISTANCE_TOP = 4f;
    private static final float Q_SPELL_RANGE = 6f;

    public BMO(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        this.applyStopMovingDuringAttack();
        String projectileFx =
                (this.avatar.contains("noir")) ? "bmo_projectile_noire" : "bmo_projectile";
        parentExt
                .getTaskScheduler()
                .schedule(
                        new RangedAttack(a, new BMOPassive(a, this.handleAttack(a)), projectileFx),
                        500,
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (wActive && this.currentHealth <= 0) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
            if (passiveStacks < 3) addPasiveStacks();
            int baseWCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt, this.player, "w", true, getReducedCooldown(baseWCooldown), 250);
            this.canCast[0] = true;
            this.canCast[2] = true;
            this.wActive = false;
        }
        if (this.wActive && System.currentTimeMillis() - this.wStartTime >= 3000) {
            int baseWCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            this.wEnd(getReducedCooldown(baseWCooldown), 250);
            this.canCast[0] = true;
            this.canCast[2] = true;
            this.wActive = false;
            String[] statsToUpdate = {"armor", "spellResist"};
            this.updateStatMenu(statsToUpdate);
        }
        if (wActive) {
            for (Actor a :
                    Champion.getActorsInRadius(
                            parentExt.getRoomHandler(this.room.getName()), this.location, 4f)) {
                if (a.getTeam() != this.team && isNonStructure(a)) {
                    JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                    a.addToDamageQueue(
                            this, (double) getSpellDamage(spellData) / 10d, spellData, true);
                    if (passiveStacks == 3) a.addState(ActorState.SLOWED, 0.5d, 2500);
                }
            }
            if (System.currentTimeMillis() - lastWSound >= 500) {
                lastWSound = System.currentTimeMillis();
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_bmo_pixels_shoot1", this.location);
            }
        }
        if (wActive && this.hasInterrupingCC()) {
            interrputW();
            this.wActive = false;
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        if (this.wActive) {
            if (stat.equalsIgnoreCase("armor")) return super.getPlayerStat(stat) * 1.2;
            else if (stat.equalsIgnoreCase("spellResist")) return super.getPlayerStat(stat) * 1.5;
        }
        return super.getPlayerStat(stat);
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
                this.stopMoving();
                Path2D trapezoid =
                        Champion.createTrapezoid(
                                location,
                                dest,
                                Q_SPELL_RANGE,
                                Q_OFFSET_DISTANCE_BOTTOM,
                                Q_OFFSET_DISTANCE_TOP);
                for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getActors()) {
                    if (a.getTeam() != this.team && trapezoid.contains(a.getLocation())) {
                        if (isNonStructure(a)) {
                            a.addState(ActorState.BLINDED, 0.5d, 2500);
                            if (passiveStacks == 3) a.addState(ActorState.SLOWED, 0.5d, 2500);
                        }
                        a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                    }
                }
                if (passiveStacks == 3) usePassiveStacks();
                else addPasiveStacks();
                String cameraFx =
                        (this.avatar.contains("noir")) ? "bmo_camera_noire" : "bmo_camera";
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        cameraFx,
                        1000,
                        this.id + "_camera",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_bmo_camera", this.location);
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
                                new BMOAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                if (!this.wActive) {
                    this.setCanMove(false);
                    this.stopMoving();
                    this.canCast[0] = false;
                    this.canCast[2] = false;
                    this.wActive = true;
                    wStartTime = System.currentTimeMillis();
                    String[] statsToUpdate = {"armor", "spellResist"};
                    this.updateStatMenu(statsToUpdate);
                    String pixelsAoeFx =
                            (this.avatar.contains("noir"))
                                    ? "bmo_pixels_aoe_noire"
                                    : "bmo_pixels_aoe";
                    String remoteSpinFx =
                            (this.avatar.contains("noir"))
                                    ? "bmo_remote_spin_noire"
                                    : "bmo_remote_spin";
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_bmo_pixels_start",
                            this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            remoteSpinFx,
                            3000,
                            this.id + "_bmo_remote",
                            true,
                            "fxNode",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            pixelsAoeFx,
                            3000,
                            this.id + "_pixels_aoe",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_4",
                            3000,
                            this.id + "_target_ring_4.5",
                            true,
                            "",
                            true,
                            true,
                            this.team);
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell2", 3000, true);
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "w", true, 500, 0);
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new BMOAbilityHandler(
                                            ability, spellData, cooldown, gCooldown, dest),
                                    500,
                                    TimeUnit.MILLISECONDS);
                } else {
                    this.canCast[0] = true;
                    this.canCast[2] = true;
                    this.wActive = false;
                    this.wEnd(cooldown, gCooldown);
                    String[] statsToUpdate = {"armor", "spellResist"};
                    this.updateStatMenu(statsToUpdate);
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new BMOAbilityHandler(
                                            ability, spellData, cooldown, gCooldown, dest),
                                    getReducedCooldown(cooldown),
                                    TimeUnit.MILLISECONDS);
                }
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                if (passiveStacks == 3) {
                    ultSlowActive = true;
                    passiveStacks = 0;
                } else addPasiveStacks();
                ExtensionCommands.actorAnimate(
                        this.parentExt, this.room, this.id, "spell3", 250, false);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "sfx_bmo_ultimate", this.location);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "vo/vo_bmo_ultimate", this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new BMOAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
        }
    }

    private void addPasiveStacks() {
        if (passiveStacks < 3) {
            if (passiveStacks != 0)
                ExtensionCommands.removeStatusIcon(
                        this.parentExt, this.player, "bmoPassive" + passiveStacks);
            this.passiveStacks++;
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "bmoPassive" + passiveStacks,
                    "bmo_spell_4_short_description",
                    "icon_bmo_p" + passiveStacks,
                    0);
        }
        if (passiveStacks == 3) {
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "vo/vo_bmo_passive_on", this.location);
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "sfx_bmo_passive", this.location);
            String passiveFx = (this.avatar.contains("noir")) ? "bmo_passive_noire" : "bmo_passive";
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    passiveFx,
                    1000 * 60 * 15,
                    this.id + "_bmo_p_fx",
                    true,
                    "",
                    false,
                    false,
                    this.team);
        }
    }

    private void usePassiveStacks() {
        ExtensionCommands.removeStatusIcon(
                this.parentExt, this.player, "bmoPassive" + passiveStacks);
        ExtensionCommands.removeFx(this.parentExt, this.player, this.id + "_bmo_p_fx");
        this.passiveStacks = 0;
    }

    private void interrputW() {
        if (passiveStacks < 3) addPasiveStacks();
        canMove = true;
        this.canCast[0] = true;
        this.canCast[2] = true;
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "run", 500, false);
    }

    public boolean canAttack() {
        if (wActive) return false;
        return super.canAttack();
    }

    private void wEnd(int cooldown, int gCooldown) {
        canMove = true;
        for (Actor a :
                Champion.getActorsInRadius(
                        parentExt.getRoomHandler(this.room.getName()), this.location, 4f)) {
            if (a.getTeam() != this.team && isNonStructure(a)) {
                JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                long wDuration = System.currentTimeMillis() - wStartTime;
                double damageMultiplier = 1 + (wDuration / 10000d);
                a.addToDamageQueue(
                        this,
                        (this.getSpellDamage(spellData)) * damageMultiplier,
                        spellData,
                        false);
                a.addState(ActorState.STUNNED, 0d, 1000);
            }
        }
        if (passiveStacks == 3) usePassiveStacks();
        else addPasiveStacks();
        String aoeExplodeFX =
                (this.avatar.contains("noir"))
                        ? "bmo_pixels_aoe_explode_noire"
                        : "bmo_pixels_aoe_explode";
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_pixels_aoe");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_bmo_remote");
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_target_ring_4.5");
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "vo/vo_bmo_yay", this.location);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_bmo_pixels_explode", this.location);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                aoeExplodeFX,
                1000,
                this.id + "_pixels_aoe_explode",
                true,
                "",
                false,
                false,
                this.team);
        ExtensionCommands.actorAbilityResponse(
                this.parentExt, this.player, "w", true, getReducedCooldown(cooldown), gCooldown);
        ExtensionCommands.actorAnimate(this.parentExt, this.room, this.id, "idle", 100, false);
    }

    private class BMOAbilityHandler extends AbilityRunnable {

        public BMOAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            int E_CAST_DELAY = 250;
            Runnable enableECasting = () -> canCast[2] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableECasting,
                            getReducedCooldown(cooldown) - E_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            if (getHealth() > 0) {
                Line2D abilityLine = Champion.getAbilityLine(location, dest, 16f);
                String ultProjectile =
                        (avatar.contains("noir"))
                                ? "projectile_bmo_bee_noire"
                                : "projectile_bmo_bee";
                fireProjectile(
                        new BMOUltProjectile(
                                parentExt, BMO.this, abilityLine, 5f, 1.5f, ultProjectile),
                        location,
                        dest,
                        16f);
            }
        }

        @Override
        protected void spellPassive() {}
    }

    private class BMOUltProjectile extends Projectile {
        private List<Actor> victims;
        private double damageReduction = 0d;

        public BMOUltProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.victims = new ArrayList<>();
        }

        @Override
        protected void hit(Actor victim) {
            this.victims.add(victim);
            JsonNode spellData = this.parentExt.getAttackData(BMO.this.avatar, "spell3");
            victim.addToDamageQueue(
                    BMO.this, getSpellDamage(spellData) * (1 - damageReduction), spellData, false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
            if (ultSlowActive) victim.addState(ActorState.SLOWED, 0.5d, 2500);
            this.damageReduction += 0.3d;
            if (this.damageReduction > 0.7d) this.damageReduction = 0.7d;
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            for (Actor a : roomHandler.getActors()) {
                if (!this.victims.contains(a)
                        && a.getTeam() != BMO.this.getTeam()
                        && a.getActorType() != ActorType.BASE
                        && a.getActorType() != ActorType.TOWER
                        && !a.getId().equalsIgnoreCase(BMO.this.id)) {
                    double collisionRadius =
                            parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                    if (a.getLocation().distance(location) <= hitbox + collisionRadius
                            && !a.getAvatar().equalsIgnoreCase("neptr_mine")) {
                        return a;
                    }
                }
            }
            return null;
        }

        public void destroy() {
            super.destroy();
            if (ultSlowActive) {
                ExtensionCommands.removeStatusIcon(
                        BMO.this.parentExt, BMO.this.player, "bmoPassive3");
                ultSlowActive = false;
            }
        }
    }

    private class BMOPassive implements Runnable {
        Actor target;
        boolean crit;

        BMOPassive(Actor a, boolean crit) {
            this.target = a;
            this.crit = crit;
        }

        public void run() {
            double damage = BMO.this.getPlayerStat("attackDamage");
            if (crit) damage *= 2;
            new Champion.DelayedAttack(
                            parentExt, BMO.this, this.target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

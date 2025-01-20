package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class BMO extends UserActor {
    private static final int PASSIVE_SLOW_DURATION = 2500;
    private static final double PASSIVE_SLOW_VALUE = 0.5f;
    private static final int Q_BLIND_DURATION = 2500;
    private static final float Q_OFFSET_DISTANCE_BOTTOM = 1.5f;
    private static final float Q_OFFSET_DISTANCE_TOP = 4f;
    private static final float Q_SPELL_RANGE = 6f;
    private static final int W_DURATION = 3000;
    private static final int W_RECAST_DELAY = 500;
    private static final float W_ARMOR_VALUE = 1.2f;
    private static final float W_SHIELDS_VALUE = 1.5f;
    private static final int E_CAST_DELAY = 250;
    private static final int E_RANGE = 16;
    private int passiveStacks = 0;
    private boolean wActive = false;
    private long wStartTime = 0;
    private long lastWSound = 0;
    private boolean ultSlowActive = false;
    private boolean passiveFxRemoved = false;

    public BMO(User u, ATBPExtension parentExt) {
        super(u, parentExt);
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
        if (this.wActive && System.currentTimeMillis() - this.wStartTime >= W_DURATION) {
            int baseWCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
            this.wEnd(getReducedCooldown(baseWCooldown), 250);
            this.canCast[0] = true;
            this.canCast[2] = true;
            this.wActive = false;
            String[] statsToUpdate = {"armor", "spellResist"};
            this.updateStatMenu(statsToUpdate);
        }
        if (wActive) {
            RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
            List<Actor> targets = Champion.getEnemyActorsInRadius(handler, team, location, 4f);
            JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
            double damage = (double) getSpellDamage(spellData, false) / 10d;
            for (Actor a : targets) {
                if (a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) {
                    a.addToDamageQueue(this, damage, spellData, true);
                    if (this.passiveStacks == 3) applySlow(a);
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
    public void respawn() {
        super.respawn();
        if (passiveFxRemoved && passiveStacks == 3) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    SkinData.getBMOPassiveFX(avatar),
                    1000 * 60 * 15,
                    id + "_bmo_p_fx",
                    true,
                    "",
                    false,
                    false,
                    team);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (passiveStacks == 3) {
            ExtensionCommands.removeFx(parentExt, room, id + "_bmo_p_fx");
            passiveFxRemoved = true;
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            String projectileFx = SkinData.getBMOBasicAttackProjectile(avatar);
            BMOPassive passiveAttack = new BMOPassive(a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectileFx);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        if (this.wActive) {
            if (stat.equalsIgnoreCase("armor")) return super.getPlayerStat(stat) * W_ARMOR_VALUE;
            else if (stat.equalsIgnoreCase("spellResist"))
                return super.getPlayerStat(stat) * W_SHIELDS_VALUE;
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public boolean canAttack() {
        if (wActive) return false;
        return super.canAttack();
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
                    this.stopMoving();
                    Path2D trapezoid =
                            Champion.createTrapezoid(
                                    location,
                                    dest,
                                    Q_SPELL_RANGE,
                                    Q_OFFSET_DISTANCE_BOTTOM,
                                    Q_OFFSET_DISTANCE_TOP);
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(this.team, trapezoid);
                    if (!actorsInPolygon.isEmpty()) {
                        for (Actor a : actorsInPolygon) {
                            if (isNonStructure(a)) {
                                a.addState(ActorState.BLINDED, 0d, Q_BLIND_DURATION);
                                if (this.passiveStacks == 3) applySlow(a);
                            }
                            a.addToDamageQueue(
                                    this, getSpellDamage(spellData, false), spellData, false);
                        }
                    }
                    if (this.passiveStacks == 3) usePassiveStacks();
                    else addPasiveStacks();
                    String cameraFx = SkinData.getBMOQCameraFX(avatar);
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
                if (!this.wActive) {
                    this.setCanMove(false);
                    this.stopMoving();
                    this.canCast[0] = false;
                    this.canCast[2] = false;
                    this.wActive = true;
                    wStartTime = System.currentTimeMillis();
                    String[] statsToUpdate = {"armor", "spellResist"};
                    this.updateStatMenu(statsToUpdate);
                    String pixelsAoeFx = SkinData.getBMOWPixelsFX(avatar);
                    String remoteSpinFx = SkinData.getBMOWRemoteFX(avatar);
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
                            this.parentExt, this.player, "w", true, W_RECAST_DELAY, 0);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest),
                            W_RECAST_DELAY);
                } else {
                    this.canCast[0] = true;
                    this.canCast[2] = true;
                    this.wActive = false;
                    this.wEnd(cooldown, gCooldown);
                    String[] statsToUpdate = {"armor", "spellResist"};
                    this.updateStatMenu(statsToUpdate);
                    int delay1 = getReducedCooldown(cooldown);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                }
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                try {
                    if (passiveStacks == 3) {
                        ultSlowActive = true;
                        passiveStacks = 0;
                    } else addPasiveStacks();
                    ExtensionCommands.actorAnimate(
                            this.parentExt, this.room, this.id, "spell3", 250, false);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_bmo_ultimate", this.location);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_bmo_ultimate",
                            this.location);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
        }
    }

    private void applySlow(Actor a) {
        a.addState(ActorState.SLOWED, PASSIVE_SLOW_VALUE, PASSIVE_SLOW_DURATION);
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
            String passiveFx = SkinData.getBMOPassiveFX(avatar);
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
        int baseCooldown = ChampionData.getBaseAbilityCooldown(this, 2);
        ExtensionCommands.actorAbilityResponse(
                this.parentExt, this.player, "w", true, getReducedCooldown(baseCooldown), 0);
    }

    private void wEnd(int cooldown, int gCooldown) {
        canMove = true;
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        for (Actor a : Champion.getActorsInRadius(handler, this.location, 4f)) {
            if (a.getTeam() != this.team && isNonStructure(a)) {
                JsonNode spellData = parentExt.getAttackData("bmo", "spell2");
                long wDuration = System.currentTimeMillis() - wStartTime;
                double damageMultiplier = 1 + (wDuration / 10000d);
                a.addToDamageQueue(
                        this,
                        (this.getSpellDamage(spellData, false)) * damageMultiplier,
                        spellData,
                        false);
                a.addState(ActorState.STUNNED, 0d, 1000);
            }
        }
        if (this.passiveStacks == 3) usePassiveStacks();
        else addPasiveStacks();
        String aoeExplodeFX = SkinData.getBMOWExplodeFX(avatar);
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

    private BMOAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new BMOAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class BMOAbilityRunnable extends AbilityRunnable {

        public BMOAbilityRunnable(
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
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);
            if (getHealth() > 0) {
                Line2D abilityLine = Champion.getAbilityLine(location, dest, E_RANGE);
                String ultProjectile = SkinData.getBMOEProjectileFX(avatar);
                fireProjectile(
                        new BMOUltProjectile(
                                parentExt, BMO.this, abilityLine, 5f, 1.5f, ultProjectile),
                        location,
                        dest,
                        E_RANGE);
            }
        }

        @Override
        protected void spellPassive() {}
    }

    public class BMOUltProjectile extends Projectile {
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
                    BMO.this,
                    getSpellDamage(spellData, true) * (1 - damageReduction),
                    spellData,
                    false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
            if (ultSlowActive) applySlow(victim);
            this.damageReduction += 0.15d;
            if (this.damageReduction > 0.7d) this.damageReduction = 0.7d;
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            List<Actor> nonStructureEnemies = roomHandler.getNonStructureEnemies(team);
            for (Actor a : nonStructureEnemies) {
                if (!this.victims.contains(a)) {
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
            if (crit) {
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            new Champion.DelayedAttack(
                            parentExt, BMO.this, this.target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

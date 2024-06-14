package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class LSP extends UserActor {
    public static final int W_DURATION = 3500;
    public static final int Q_CAST_DELAY = 750;
    public static final int Q_FEAR_DURATION = 2000;
    public static final int W_CAST_DELAY = 500;
    public static final int E_CAST_DELAY = 1250;
    private int lumps = 0;
    private long wTime = 0;
    private boolean isCastingult = false;
    private boolean interruptE = false;
    private boolean wActive = false;
    private static final float Q_OFFSET_DISTANCE = 0.75f;
    private static final float Q_SPELL_RANGE = 7.5f;

    public LSP(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "p0", "lsp_spell_4_short_description", "icon_lsp_passive", 0f);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.wActive && this.getHealth() <= 0) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_wRing");
            ExtensionCommands.removeFx(parentExt, room, id + "_w");
            this.wActive = false;
        }
        if (this.wActive && System.currentTimeMillis() - this.wTime >= W_DURATION) {
            this.wActive = false;
        }
        if (this.wActive) {
            JsonNode spellData = this.parentExt.getAttackData(this.avatar, "spell2");
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, this.location, 3f)) {
                if (this.isNonStructure(a)) {
                    a.addToDamageQueue(
                            this, (double) getSpellDamage(spellData) / 10d, spellData, true);
                }
            }
        }
        if (this.isCastingult && this.hasInterrupingCC()) {
            this.interruptE = true;
        }
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            String projectile = "lsp_projectile";
            String emit = "Bip001 R Hand";
            LSPPassive passiveAttack = new LSPPassive(a, handleAttack(a));
            RangedAttack rangedAttack = new RangedAttack(a, passiveAttack, projectile, emit);
            scheduleTask(rangedAttack, BASIC_ATTACK_DELAY);
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (isCastingult)
            ExtensionCommands.swapActorAsset(
                    this.parentExt, this.room, this.id, getSkinAssetBundle());
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
                    this.stopMoving(castDelay);
                    String qVO = SkinData.getLSPQVO(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, qVO, this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_rect_7",
                            1100,
                            this.id + "_qRect",
                            false,
                            "",
                            true,
                            true,
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
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 2:
                this.canCast[1] = false;
                this.wActive = true;
                this.wTime = System.currentTimeMillis();
                String wVO = SkinData.getLSPWVO(avatar);
                ExtensionCommands.playSound(this.parentExt, this.room, this.id, wVO, this.location);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "fx_target_ring_3",
                        W_DURATION,
                        this.id + "_wRing",
                        true,
                        "",
                        true,
                        true,
                        this.team);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), castDelay);
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                this.isCastingult = true;
                String eVO = SkinData.getLSPEVO(avatar);
                ExtensionCommands.playSound(this.parentExt, this.room, this.id, eVO, this.location);
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

    private LSPAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new LSPAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class LSPAbilityRunnable extends AbilityRunnable {

        public LSPAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            Runnable enableQCasting = () -> canCast[0] = true;
            int delay = getReducedCooldown(cooldown) - Q_CAST_DELAY;
            scheduleTask(enableQCasting, delay);
            if (getHealth() > 0) {
                double healthHealed = (double) getMaxHealth() * (0.03d * lumps);
                ExtensionCommands.playSound(parentExt, room, id, "sfx_lsp_drama_beam", location);
                ExtensionCommands.removeStatusIcon(parentExt, player, "p" + lumps);
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "p0",
                        "lsp_spell_4_short_description",
                        "icon_lsp_passive",
                        0f);
                lumps = 0;
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "lsp_drama_beam",
                        1100,
                        id + "q",
                        false,
                        "",
                        true,
                        false,
                        team);
                Path2D qRect =
                        Champion.createRectangle(location, dest, Q_SPELL_RANGE, Q_OFFSET_DISTANCE);

                List<Actor> affectedActors = new ArrayList<>();
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(team, qRect);
                if (!actorsInPolygon.isEmpty()) {
                    for (Actor a : actorsInPolygon) {
                        if (a.getActorType() != ActorType.TOWER
                                && a.getActorType() != ActorType.BASE) {
                            if (!a.getId().contains("turret"))
                                a.handleFear(LSP.this.location, Q_FEAR_DURATION);
                            a.addToDamageQueue(
                                    LSP.this, getSpellDamage(spellData), spellData, false);
                            affectedActors.add(a);
                        }
                    }
                }
                if (!affectedActors.isEmpty()) {
                    changeHealth((int) healthHealed);
                }
            }
        }

        @Override
        protected void spellW() {
            Runnable enableWCasting = () -> canCast[1] = true;
            int delay = getReducedCooldown(cooldown) - W_CAST_DELAY;
            scheduleTask(enableWCasting, delay);
            if (getHealth() > 0) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_lsp_lumps_aoe", location);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "lsp_the_lumps_aoe",
                        3000,
                        id + "_w",
                        true,
                        "",
                        true,
                        false,
                        team);
            }
        }

        @Override
        protected void spellE() {
            Runnable enableECasting = () -> canCast[2] = true;
            int delay = getReducedCooldown(cooldown) - E_CAST_DELAY;
            scheduleTask(enableECasting, delay);
            isCastingult = false;
            if (!interruptE && getHealth() > 0) {
                Line2D projectileLine = Champion.getAbilityLine(location, dest, 100f);
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell3b", 500, false);
                String eProjectile = SkinData.getLSPEProjectile(avatar);
                fireProjectile(
                        new LSPUltProjectile(
                                parentExt, LSP.this, projectileLine, 8f, 2f, eProjectile),
                        location,
                        dest,
                        100f);
                ExtensionCommands.playSound(
                        parentExt, room, "global", "sfx_lsp_cellphone_throw", location);
            } else if (interruptE) {
                ExtensionCommands.playSound(parentExt, room, id, "sfx_skill_interrupted", location);
                ExtensionCommands.actorAnimate(parentExt, room, id, "run", 200, false);
                ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            }
            interruptE = false;
        }

        @Override
        protected void spellPassive() {}
    }

    private class LSPUltProjectile extends Projectile {

        private List<Actor> victims;
        private double damageReduction = 0d;
        private double healReduction = 0d;

        public LSPUltProjectile(
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
            JsonNode spellData = this.parentExt.getAttackData(LSP.this.avatar, "spell3");
            if (victim.getTeam() == LSP.this.team) {
                victim.changeHealth((int) (getSpellDamage(spellData) * (1 - this.healReduction)));
                this.healReduction += 0.3d;
                if (this.healReduction > 0.7d) this.healReduction = 0.7d;
            } else {
                victim.addToDamageQueue(
                        LSP.this,
                        getSpellDamage(spellData) * (1 - this.damageReduction),
                        spellData,
                        false);
                this.damageReduction += 0.3d;
                if (this.damageReduction > 0.7d) this.damageReduction = 0.7d;
            }
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
    }

    private class LSPPassive implements Runnable {

        Actor target;
        boolean crit;

        LSPPassive(Actor a, boolean crit) {
            this.target = a;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = LSP.this.getPlayerStat("attackDamage");
            if (crit) damage *= 2;
            new Champion.DelayedAttack(
                            parentExt, LSP.this, this.target, (int) damage, "basicAttack")
                    .run();
            ExtensionCommands.removeStatusIcon(parentExt, player, "p" + lumps);
            if (LSP.this.lumps < 10) LSP.this.lumps++;
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    "p" + lumps,
                    "lsp_spell_4_short_description",
                    "icon_lsp_p" + lumps,
                    0f);
        }
    }
}

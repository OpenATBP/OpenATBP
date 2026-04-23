package xyz.openatbp.extension.game.champions;

import static xyz.openatbp.extension.game.effects.EffectManager.DEFAULT_KNOCKBACK_SPEED;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;

public class Neptr extends UserActor {
    private static final int PASSIVE_SPEED_DURATION = 3500;
    private static final double PASSIVE_SPEED_PERCENT = 0.35d;
    private static final int PASSIVE_ATTACK_SPEED_DURATION = 3500;
    private static final double PASSIVE_ATTACK_SPEED_PERCENT = 0.25d;
    private static final int PASSIVE_DURATION = 3500;
    private static final int W_SLOW_DURATION = 3000;
    private static final double W_SLOW_PERCENT = 0.4d;
    private static final int MINE_LIFE_SPAN = 30000;
    private static final int E_DAMAGE_DURATION = 3000;
    private static final int E_CAST_DELAY = 500;
    private static final int E_SILENCE_DURATION = 1000;
    public static final float E_KNOCKBACK_DIST = 3.5f;
    private boolean passiveActive = false;
    private long passiveStart = 0;
    private int mineNum = 0;
    private List<Mine> mines;
    private List<Actor> ultImpactedActors;
    private long ultDamageStartTime = 0;
    private boolean soundPlayed = false;
    private long lastMoveSoundPlayed = 0;
    private Point2D ultLocation;

    public Neptr(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.mines = new ArrayList<>(3);
        this.ultImpactedActors = new ArrayList<>();
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.passiveActive
                && System.currentTimeMillis() - this.passiveStart >= PASSIVE_DURATION) {
            this.passiveActive = false;
            ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "passive");
        }
        if (this.isStopped() && !this.soundPlayed) {
            String moveEndSFX = SkinData.getNeptrMoveEndSFX(avatar);
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, moveEndSFX, this.location);
            this.soundPlayed = true;
        } else if (!this.isStopped()
                && System.currentTimeMillis() - this.lastMoveSoundPlayed > 500) {
            String moveSFX = SkinData.getNeptrMoveSFX(avatar, passiveActive);
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, moveSFX, this.location);
            this.lastMoveSoundPlayed = System.currentTimeMillis();
        }
        List<Actor> impactedActors = new ArrayList<>(this.ultImpactedActors);
        if (!impactedActors.isEmpty()
                && System.currentTimeMillis() - this.ultDamageStartTime < E_DAMAGE_DURATION) {
            JsonNode attackData = this.parentExt.getAttackData(this.avatar, "spell3");
            for (Actor a : impactedActors) {
                double dmg = getSpellDamage(attackData, false) / 10d;
                a.addToDamageQueue(this, dmg, attackData, true);
            }
        }
    }

    @Override
    public void setInsideBrush(boolean isInsideBrush) {
        super.setInsideBrush(isInsideBrush);
        if (isInsideBrush) {
            this.passiveStart = System.currentTimeMillis();
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "neptr_passive",
                    750,
                    this.id + "_passive" + Math.random(),
                    true,
                    "targetNode",
                    true,
                    false,
                    this.team);

            String passiveSFX = SkinData.getNeptrPassiveSFX(avatar);
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, passiveSFX, this.location);
            if (!this.passiveActive) {
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "vo/vo_neptr_passive", this.location);
            }
            effectManager.addEffect(
                    this.id + "_neptr_passive_speed",
                    "speed",
                    PASSIVE_SPEED_PERCENT,
                    ModifierType.MULTIPLICATIVE,
                    ModifierIntent.BUFF,
                    PASSIVE_SPEED_DURATION);
            effectManager.addEffect(
                    this.id + "_neptr_passive_as",
                    "attackSpeed",
                    PASSIVE_ATTACK_SPEED_PERCENT,
                    ModifierType.MULTIPLICATIVE,
                    ModifierIntent.BUFF,
                    PASSIVE_ATTACK_SPEED_DURATION);

            if (this.passiveActive) {
                ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "passive");
            }
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "passive",
                    "neptr_spell_4_short_description",
                    "icon_neptr_passive",
                    PASSIVE_DURATION);
            this.passiveActive = true;
        }
    }

    @Override
    public void startMoveTo(Point2D endPoint, boolean forcedMovement) {
        super.startMoveTo(endPoint, forcedMovement);
        if (this.isStopped())
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "sfx_neptr_move_start", this.location);
        this.soundPlayed = false;
    }

    @Override
    public void fireProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        super.fireProjectile(projectile, location, dest, abilityRange);
        Runnable creationDelay =
                () ->
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                projectile.getId(),
                                "neptr_pie_trail",
                                10000,
                                projectile.getId() + "_fx",
                                true,
                                "Bip001",
                                true,
                                true,
                                this.team);
        int delay = 200;
        scheduleTask(creationDelay, delay);
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        for (Mine m : mines) {
            m.die(a);
        }
        this.mines = new ArrayList<>();
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        this.stopMoving();
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                try {
                    Line2D abilityLine = Champion.createLineTowards(this.location, dest, 8f);
                    this.fireProjectile(
                            new NeptrProjectile(
                                    this.parentExt,
                                    this,
                                    abilityLine,
                                    8f,
                                    0.5f,
                                    "projectile_neptr_boom_meringue"),
                            this.location,
                            dest,
                            8f);
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
                    this.spawnMine(dest);
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.ultLocation = dest;
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, "", "sfx_neptr_ultimate", dest);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_neptr_locked_on",
                            this.location);
                    float rotation = getRotation(dest);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "neptr_ultimate",
                            this.id + "_ult",
                            2000,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            false,
                            this.team,
                            rotation);
                    ExtensionCommands.createWorldFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "fx_target_ring_3",
                            this.id + "_ultRing",
                            500,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
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

    private void spawnMine(Point2D dest) {
        Mine m = null;

        if (this.mines != null && this.mines.size() == 3) {
            this.mines.get(0).die(this);
            this.mines.remove(0);
            m = new Mine(dest, this.mineNum);
        } else if (this.mines != null) {
            m = new Mine(dest, this.mineNum);
        }
        if (this.mines != null) {
            this.mineNum++;
            this.mines.add(m);
            this.parentExt.getRoomHandler(this.room.getName()).addCompanion(m);
        }
    }

    public void handleMineDeath(Mine m) {
        this.mines.remove(m);
    }

    private NeptrAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new NeptrAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class NeptrAbilityRunnable extends AbilityRunnable {

        public NeptrAbilityRunnable(
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
            ultDamageStartTime = System.currentTimeMillis();
            ultImpactedActors = new ArrayList<>();
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor a : Champion.getActorsInRadius(handler, ultLocation, 3f)) {
                if (a.getActorType() != ActorType.BASE && a.isNotLeaping()) {
                    if (isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                        a.handleKnockback(
                                Neptr.this.location, E_KNOCKBACK_DIST, DEFAULT_KNOCKBACK_SPEED);
                        a.getEffectManager()
                                .addState(
                                        ActorState.SILENCED,
                                        id + "_neptr_e_silence",
                                        0d,
                                        E_SILENCE_DURATION);
                    }
                    if (isNeitherTowerNorAlly(a)) {
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                a.getId(),
                                "neptr_dot_poison",
                                E_DAMAGE_DURATION,
                                a.getId() + "_neptrPoison",
                                true,
                                "",
                                false,
                                false,
                                team);
                        ultImpactedActors.add(a);
                    }
                }
            }
        }

        @Override
        protected void spellPassive() {}
    }

    private class NeptrProjectile extends Projectile {
        public static final int REVERSING_DELAY = 500;
        private boolean isReversed = false;
        private boolean doPieReversing = false;
        private float damageReduction = 0;
        private List<Actor> damagedActors;
        private boolean clear = false;
        private boolean projectileWasStopped = false;

        public NeptrProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float offsetDistance,
                String projectileAsset) {
            super(parentExt, owner, path, speed, offsetDistance, offsetDistance, projectileAsset);
            this.damagedActors = new ArrayList<>();
        }

        @Override
        public void update(RoomHandler roomHandler) {
            if (destroyed) return;
            this.updateLocation();
            Actor hitActor = this.checkPlayerCollision(roomHandler);
            if (hitActor != null) {
                this.hit(hitActor);
            }
            if (this.doPieReversing) {
                this.maxTravelTimeMs =
                        (this.startingLocation.distance(Neptr.this.getLocation()) / 8) * 1000;
                this.destination = Neptr.this.getLocation();
                ExtensionCommands.moveActor(
                        this.parentExt,
                        this.owner.getRoom(),
                        this.id,
                        this.getLocation(),
                        this.destination,
                        8,
                        true);
            }
            if (this.destination.distance(this.getLocation()) <= getDistance()
                    || System.currentTimeMillis() - this.startTime > this.maxTravelTimeMs) {
                if (this.isReversed) {
                    Console.debugLog("Projectile being destroyed in update!");
                    this.destroy();
                } else if (!this.projectileWasStopped) {
                    this.projectileWasStopped = true;
                    this.startingLocation = this.path.getP2();
                    Runnable enableReversing =
                            () -> {
                                this.startTime = System.currentTimeMillis();
                                this.travelTimeMs = 0;
                                this.doPieReversing = true;
                                this.isReversed = true;
                                this.damageReduction = 0;

                                if (!this.clear) {
                                    this.damagedActors.clear();
                                    this.clear = true;
                                }
                            };
                    scheduleTask(enableReversing, REVERSING_DELAY);
                }
            }
        }

        private float getDistance() {
            return this.isReversed ? 1 : 0.01f;
        }

        @Override
        public boolean isTargetable(Actor a) {
            return super.isTargetable(a) && !this.damagedActors.contains(a);
        }

        @Override
        protected void hit(Actor victim) {
            this.damagedActors.add(victim);
            JsonNode spellData = parentExt.getAttackData(Neptr.this.getAvatar(), "spell1");
            double dmg = getSpellDamage(spellData, true) * (1 - damageReduction);
            String sound = "akubat_projectileHit1";

            victim.addToDamageQueue(Neptr.this, dmg, spellData, false);

            ExtensionCommands.playSound(parentExt, room, "", sound, victim.getLocation());
            this.damageReduction += 0.15f;
            if (this.damageReduction > 0.75) this.damageReduction = 0.75f;
        }
    }

    private class Mine extends Actor {
        private boolean dead = false;
        private String iconName;
        private long timeOfBirth;
        private boolean mineActivated = false;

        Mine(Point2D location, int mineNum) {
            this.room = Neptr.this.room;
            this.parentExt = Neptr.this.parentExt;
            this.currentHealth = 999;
            this.maxHealth = 999;
            this.location = location;
            this.avatar = "neptr_mine";
            this.id = "mine" + mineNum + "_" + Neptr.this.id;
            this.team = Neptr.this.team;
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            this.iconName = "Mine #" + mineNum;
            ExtensionCommands.addStatusIcon(
                    parentExt, player, iconName, "Mine placed!", "icon_neptr_s2", MINE_LIFE_SPAN);
            ExtensionCommands.createActor(
                    parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
            Runnable creationDelay =
                    () -> {
                        ExtensionCommands.playSound(
                                parentExt,
                                this.room,
                                Neptr.this.id,
                                "vo/vo_neptr_mine",
                                Neptr.this.location);
                        ExtensionCommands.playSound(
                                parentExt, room, this.id, "sfx_neptr_mine_spawn", this.location);
                        ExtensionCommands.createWorldFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fx_target_ring_2",
                                this.id + "_mine",
                                30000,
                                (float) this.location.getX(),
                                (float) this.location.getY(),
                                true,
                                this.team,
                                0f);
                    };
            int delay = 150;
            scheduleTask(creationDelay, delay);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {}

        @Override
        public void attack(Actor a) {}

        @Override
        public void die(Actor a) {
            this.dead = true;
            this.currentHealth = 0;
            if (!mineActivated) {
                ExtensionCommands.removeFx(parentExt, room, this.id + "_mine");
                ExtensionCommands.removeStatusIcon(parentExt, player, this.iconName);
                ExtensionCommands.destroyActor(parentExt, room, this.id);
                this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this);
            }
        }

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            if (a.getActorType() == ActorType.TOWER) {
                this.die(this);
                Neptr.this.handleMineDeath(this);
                ExtensionCommands.playSound(
                        this.parentExt, room, "", "sfx_neptr_mine_activate", this.location);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        this.id,
                        "neptr_mine_explode",
                        this.id + "_explosion",
                        1000,
                        (float) this.location.getX(),
                        (float) this.location.getY(),
                        false,
                        this.team,
                        0f);
            }
            return false;
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            if (this.dead) return;
            if (System.currentTimeMillis() - this.timeOfBirth >= MINE_LIFE_SPAN) {
                this.die(this);
                Neptr.this.handleMineDeath(this);
            }
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            List<Actor> actors = Champion.getActorsInRadius(handler, this.location, 2f);
            if (!actors.isEmpty()) {
                for (Actor a : actors) {
                    if (isNeitherStructureNorAlly(a) && !this.mineActivated) {
                        this.mineActivated = true;
                        explodeMine();
                        this.die(this);
                        Neptr.this.handleMineDeath(this);
                        break;
                    }
                }
            }
        }

        private void explodeMine() {
            Runnable activate =
                    () ->
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    room,
                                    this.id,
                                    "sfx_neptr_mine_activate",
                                    this.location);
            int explodeDelay = 500;
            scheduleTask(activate, explodeDelay);
            Runnable mineExplosion =
                    () -> {
                        RoomHandler handler = parentExt.getRoomHandler(room.getName());
                        List<Actor> targets =
                                Champion.getActorsInRadius(handler, this.location, 2f);

                        if (!targets.isEmpty()) {
                            for (Actor t : targets) {
                                if (isNeitherStructureNorAlly(t) && t.isNotLeaping()) {
                                    t.getEffectManager()
                                            .addState(
                                                    ActorState.SLOWED,
                                                    id + "_neptr_w_slow",
                                                    W_SLOW_PERCENT,
                                                    W_SLOW_DURATION);
                                }

                                if (isNeitherTowerNorAlly(t) && t.isNotLeaping()) {
                                    String ava = Neptr.this.avatar;
                                    JsonNode spellData = parentExt.getAttackData(ava, "spell2");
                                    double dmg = getSpellDamage(spellData, true);

                                    t.addToDamageQueue(Neptr.this, dmg, spellData, false);
                                }
                            }
                        }
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                this.id,
                                "neptr_mine_explode",
                                this.id + "_explosion",
                                1000,
                                (float) this.location.getX(),
                                (float) this.location.getY(),
                                false,
                                this.team,
                                0f);
                        ExtensionCommands.playSound(
                                parentExt, room, "", "sfx_neptr_mine_explode", this.location);
                        ExtensionCommands.removeFx(parentExt, room, this.id + "_mine");
                        ExtensionCommands.removeStatusIcon(parentExt, player, this.iconName);
                        ExtensionCommands.destroyActor(parentExt, room, this.id);
                        this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this);
                    };
            int explosionDelay = 1200;
            scheduleTask(mineExplosion, explosionDelay);
        }

        @Override
        public void setTarget(Actor a) {}
    }
}

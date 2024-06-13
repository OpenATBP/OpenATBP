package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Neptr extends UserActor {
    private static final int PASSIVE_SPEED_DURATION = 3500;
    private static final double PASSIVE_SPEED_VALUE = 0.35d;
    private static final int PASSIVE_ATTACKSPEED_DURATION = 3500;
    private static final double PASSIVE_ATTACKSPEED_VALUE = 0.25d;
    private static final int PASSIVE_DURATION = 3500;
    private static final int W_SLOW_DURATION = 3000;
    private static final double W_SLOW_VALUE = 0.4d;
    private static final int MINE_LIFE_SPAN = 30000;
    private static final int E_DAMAGE_DURATION = 3000;
    private static final int E_CAST_DELAY = 500;
    private static final int E_SILENCE_DURATION = 1000;
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
            String moveEndSound =
                    this.avatar.contains("racing")
                            ? "neptr_racing_move_stop"
                            : "sfx_neptr_move_end";
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, moveEndSound, this.location);
            this.soundPlayed = true;
        } else if (!this.isStopped()
                && System.currentTimeMillis() - this.lastMoveSoundPlayed > 500) {
            String moveSound;
            if (passiveActive && this.avatar.contains("racing")) {
                moveSound = "neptr_racing_move_fast";
            } else {
                moveSound = this.avatar.contains("racing") ? "neptr_racing_move" : "sfx_neptr_move";
            }
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, moveSound, this.location);
            this.lastMoveSoundPlayed = System.currentTimeMillis();
        }
        List<Actor> impactedActors = new ArrayList<>(this.ultImpactedActors);
        if (!impactedActors.isEmpty()
                && System.currentTimeMillis() - this.ultDamageStartTime < E_DAMAGE_DURATION) {
            JsonNode attackData = this.parentExt.getAttackData(this.avatar, "spell3");
            for (Actor a : impactedActors) {
                a.addToDamageQueue(this, this.getSpellDamage(attackData) / 10d, attackData, true);
            }
        }

        ArrayList<Mine> mines = new ArrayList<>(this.mines); // To remove concurrent exceptions
        for (Mine m : mines) {
            m.update(msRan);
        }
    }

    @Override
    public void setState(ActorState state, boolean enabled) {
        super.setState(state, enabled);
        if (state == ActorState.BRUSH && enabled) {
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
            String passiveSound =
                    this.avatar.contains("racing") ? "neptr_racing_passive" : "sfx_neptr_passive";
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, passiveSound, this.location);
            if (!this.passiveActive) {
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "vo/vo_neptr_passive", this.location);
            }
            this.addEffect(
                    "speed", this.getStat("speed") * PASSIVE_SPEED_VALUE, PASSIVE_SPEED_DURATION);
            this.addEffect(
                    "attackSpeed",
                    this.getStat("attackSpeed") * -PASSIVE_ATTACKSPEED_VALUE,
                    PASSIVE_ATTACKSPEED_DURATION);
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
    public void move(ISFSObject params, Point2D destination) {
        if (this.isStopped())
            ExtensionCommands.playSound(
                    this.parentExt, this.player, this.id, "sfx_neptr_move_start", this.location);
        this.soundPlayed = false;
        super.move(params, destination);
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
                Line2D abilityLine = Champion.getAbilityLine(this.location, dest, 8f);
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
                this.spawnMine(dest);
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
                this.ultLocation = dest;
                ExtensionCommands.playSound(
                        this.parentExt, this.room, "", "sfx_neptr_ultimate", dest);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, "vo/vo_neptr_locked_on", this.location);
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
                if (a.getActorType() != ActorType.BASE) {
                    if (isNonStructure(a)) {
                        a.knockback(Neptr.this.location);
                        a.addState(ActorState.SILENCED, 0d, E_SILENCE_DURATION);
                    }
                    if (a.getActorType() != ActorType.BASE && a.getTeam() != getTeam()) {
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
                float hitboxRadius,
                String projectileAsset) {
            super(parentExt, owner, path, speed, hitboxRadius, projectileAsset);
            this.damagedActors = new ArrayList<>();
        }

        @Override
        public void update(RoomHandler roomHandler) {
            if (destroyed) return;
            this.updateTimeTraveled();
            Actor hitActor = this.checkPlayerCollision(roomHandler);
            if (hitActor != null) {
                this.hit(hitActor);
            }
            if (this.doPieReversing) {
                this.estimatedDuration =
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
                    || System.currentTimeMillis() - this.startTime > this.estimatedDuration) {
                if (this.isReversed) {
                    Console.debugLog("Projectile being destroyed in update!");
                    this.destroy();
                } else if (!this.projectileWasStopped) {
                    this.projectileWasStopped = true;
                    this.startingLocation = this.path.getP2();
                    Runnable enableReversing =
                            () -> {
                                this.startTime = System.currentTimeMillis();
                                this.timeTraveled = 0;
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
        public Actor checkPlayerCollision(RoomHandler roomHandler) {
            List<Actor> nonStructureEnemies = roomHandler.getNonStructureEnemies(team);
            for (Actor a : nonStructureEnemies) {
                if (!this.damagedActors.contains(a)) {
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

        @Override
        protected void hit(Actor victim) {
            this.damagedActors.add(victim);
            JsonNode spellData = parentExt.getAttackData(Neptr.this.getAvatar(), "spell1");
            victim.addToDamageQueue(
                    Neptr.this,
                    getSpellDamage(spellData) * (1 - damageReduction),
                    spellData,
                    false);
            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
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
                    if (isNonStructure(a) && !this.mineActivated) {
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
                            for (Actor target : targets) {
                                if (isNonStructure(target)) {
                                    JsonNode spellData =
                                            this.parentExt.getAttackData(
                                                    Neptr.this.avatar, "spell2");
                                    target.addToDamageQueue(
                                            Neptr.this,
                                            getSpellDamage(spellData),
                                            spellData,
                                            false);
                                    target.addState(
                                            ActorState.SLOWED, W_SLOW_VALUE, W_SLOW_DURATION);
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

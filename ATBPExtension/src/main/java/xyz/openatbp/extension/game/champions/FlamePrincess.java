package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class FlamePrincess extends UserActor {

    private boolean ultFinished = false;
    private boolean passiveEnabled = false;
    private long lastPassiveUsage = 0;
    private boolean ultStarted = false;
    private int ultUses = 3;
    private int dashTime = 0;
    private boolean wUsed = false;
    private boolean polymorphActive = false;
    private long ultStartTime = 0;
    private long lastPolymorphTime = 0;

    public FlamePrincess(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultStarted && System.currentTimeMillis() - this.ultStartTime >= 5000
                || this.ultFinished) {
            setState(ActorState.TRANSFORMED, false);
            ExtensionCommands.removeFx(parentExt, room, id + "flameE");
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.actorAbilityResponse(
                    parentExt, player, "e", true, getReducedCooldown(getBaseUltCooldown()), 0);
            ExtensionCommands.scaleActor(parentExt, room, id, 0.6667f);
            this.ultStarted = false;
            this.ultFinished = false;
            this.ultUses = 3;
        }
        if (this.ultStarted) {
            for (Actor a :
                    Champion.getActorsInRadius(
                            parentExt.getRoomHandler(this.room.getName()), this.location, 2)) {
                if (a.getTeam() != this.team) {
                    JsonNode attackData = this.parentExt.getAttackData(getAvatar(), "spell3");
                    double damage = (double) this.getSpellDamage(attackData) / 10;
                    if (a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE) {
                        a.addToDamageQueue(this, damage, attackData);
                    }
                }
            }
        }
        if (System.currentTimeMillis() - lastPolymorphTime <= 3000) {
            for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                this.polymorphActive = a.getState(ActorState.POLYMORPH);
                if (polymorphActive) {
                    List<Actor> actorsInRadius =
                            Champion.getActorsInRadius(
                                    this.parentExt.getRoomHandler(this.room.getName()),
                                    a.getLocation(),
                                    2f);
                    actorsInRadius.remove(a);

                    for (Actor affectedActor : actorsInRadius) {
                        if (!affectedActor.getId().equalsIgnoreCase(this.id)
                                && affectedActor.getTeam() != this.team
                                && isNonStructure(affectedActor)) {
                            JsonNode spellData = this.parentExt.getAttackData("flame", "spell2");
                            affectedActor.addToDamageQueue(
                                    this, getSpellDamage(spellData) / 10d, spellData);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.ultStarted && !this.ultFinished) {
            this.ultFinished = true;
            setState(ActorState.TRANSFORMED, false);
            ExtensionCommands.removeFx(parentExt, room, this.id + "flameE");
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.actorAbilityResponse(
                    parentExt,
                    player,
                    "e",
                    canUseAbility(2),
                    getReducedCooldown(getBaseUltCooldown()),
                    0);
            ExtensionCommands.scaleActor(parentExt, room, id, 0.6667f);
        }
        if (passiveEnabled) {
            passiveEnabled = false;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flame_passive");
            ExtensionCommands.actorAbilityResponse(parentExt, player, "passive", true, 10000, 0);
        }
    }

    private int getBaseUltCooldown() {
        return ChampionData.getBaseAbilityCooldown(this, 3);
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
        if (ultUses == 3
                && !passiveEnabled
                && System.currentTimeMillis() - lastPassiveUsage >= 10000) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "flame_princess_passive_flames",
                    1000 * 60 * 15,
                    this.id + "_flame_passive",
                    true,
                    "",
                    false,
                    false,
                    this.team);
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_flame_princess_passive_ignite",
                    this.location);
            passiveEnabled = true;
        }
        switch (ability) {
            case 1: // Q
                this.canCast[0] = false;
                Line2D abilityLine = Champion.getAbilityLine(location, dest, 8f);
                ExtensionCommands.playSound(
                        parentExt,
                        room,
                        this.id,
                        "sfx_flame_princess_cone_of_flame",
                        this.location);
                fireProjectile(
                        new FlameProjectile(
                                this.parentExt,
                                this,
                                abilityLine,
                                8f,
                                0.5f,
                                this.id + "projectile_flame_cone"),
                        "projectile_flame_cone",
                        location,
                        dest,
                        8f);
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
                                new FlameAbilityRunnable(
                                        ability, spellData, cooldown, gCooldown, dest),
                                gCooldown,
                                TimeUnit.MILLISECONDS);
                break;
            case 2: // W
                this.canCast[1] = false;
                this.wUsed = true;
                ExtensionCommands.createWorldFX(
                        this.parentExt,
                        this.player.getLastJoinedRoom(),
                        this.id,
                        "fx_target_ring_2",
                        "flame_w",
                        1500,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
                Runnable fxDelay =
                        () ->
                                ExtensionCommands.createWorldFX(
                                        this.parentExt,
                                        this.player.getLastJoinedRoom(),
                                        this.id,
                                        "flame_princess_polymorph_fireball",
                                        this.id + "_flame_w_polymorph",
                                        2000,
                                        (float) dest.getX(),
                                        (float) dest.getY(),
                                        false,
                                        this.team,
                                        0f);
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt.getTaskScheduler().schedule(fxDelay, 500, TimeUnit.MILLISECONDS);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new FlameAbilityRunnable(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                break;
            case 3: // E
                this.canCast[2] = false;
                if (!ultStarted && ultUses == 3) {
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new FlameAbilityRunnable(
                                            ability, spellData, cooldown, gCooldown, dest),
                                    200,
                                    TimeUnit.MILLISECONDS);
                    this.ultStartTime = System.currentTimeMillis();
                    this.ultStarted = true;
                    this.setState(ActorState.TRANSFORMED, true);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "vo/vo_flame_princess_flame_form",
                            this.getLocation());
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_flame_princess_flame_form",
                            this.getLocation());
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.id, "flame_ult");
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            "flame_princess_ultimate_aoe",
                            5000,
                            this.id + "flameE",
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    ExtensionCommands.scaleActor(this.parentExt, this.room, this.id, 1.5f);
                } else {
                    if (this.ultUses > 0 && canDash()) {
                        // TODO: Fix so FP can dash and still get health packs
                        Point2D ogLocation = this.location;
                        Point2D dashLocation = this.dash(dest, false, 15d);
                        double time = ogLocation.distance(dashLocation) / DASH_SPEED;
                        this.dashTime = (int) (time * 1000);
                        ExtensionCommands.actorAnimate(
                                this.parentExt, this.room, this.id, "run", this.dashTime, false);
                        this.ultUses--;
                    } else {
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.player,
                                this.id,
                                "not_allowed_error",
                                new Point2D.Float(0, 0));
                    }
                    if (this.ultUses > 0) {
                        parentExt
                                .getTaskScheduler()
                                .schedule(
                                        new FlameAbilityRunnable(
                                                ability, spellData, cooldown, gCooldown, dest),
                                        this.dashTime,
                                        TimeUnit.MILLISECONDS);
                    } else {
                        parentExt
                                .getTaskScheduler()
                                .schedule(
                                        new FlameAbilityRunnable(
                                                ability, spellData, cooldown, gCooldown, dest),
                                        this.dashTime + 100,
                                        TimeUnit.MILLISECONDS);
                    }
                }
                break;
            case 4: // Passive
                break;
        }
    }

    @Override
    public void attack(Actor a) {
        this.applyStopMovingDuringAttack();
        parentExt
                .getTaskScheduler()
                .schedule(
                        new RangedAttack(
                                a,
                                new PassiveAttack(a, this.handleAttack(a)),
                                "flame_princess_projectile"),
                        500,
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean canMove() {
        if (this.wUsed) return false;
        else return super.canMove();
    }

    private class FlameAbilityRunnable extends AbilityRunnable {

        public FlameAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            attackCooldown = 0;
            int Q_GLOBAL_COOLDOWN = 250;
            Runnable enableQCasting = () -> canCast[0] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableQCasting,
                            getReducedCooldown(cooldown) - Q_GLOBAL_COOLDOWN,
                            TimeUnit.MILLISECONDS);
        }

        @Override
        protected void spellW() {
            ExtensionCommands.playSound(
                    parentExt, room, "", "sfx_flame_princess_projectile_explode", this.dest);
            wUsed = false;
            RoomHandler roomHandler =
                    parentExt.getRoomHandler(player.getLastJoinedRoom().getName());
            List<Actor> affectedUsers =
                    Champion.getActorsInRadius(roomHandler, this.dest, 2).stream()
                            .filter(a -> a.getTeam() != FlamePrincess.this.team)
                            .collect(Collectors.toList());
            for (Actor a : affectedUsers) {
                if (a.getActorType() == ActorType.PLAYER) {
                    UserActor userActor = (UserActor) a;
                    userActor.addState(ActorState.POLYMORPH, 0d, 3000, null, false);
                    lastPolymorphTime = System.currentTimeMillis();
                }
                double newDamage = getSpellDamage(spellData);
                if (isNonStructure(a))
                    a.addToDamageQueue(
                            FlamePrincess.this,
                            newDamage,
                            parentExt.getAttackData(getAvatar(), "spell2"));
            }
            int W_CAST_DELAY = 1000;
            Runnable enableWCasting = () -> canCast[1] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableWCasting,
                            getReducedCooldown(cooldown) - W_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
        }

        @Override
        protected void spellE() {
            if (ultUses > 0) canCast[2] = true;
            if (ultUses == 0) {
                ultFinished = true;
                int E_DASH_TIME = dashTime + 100;
                Runnable enableECasting = () -> canCast[2] = true;
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                enableECasting,
                                getReducedCooldown(cooldown) - E_DASH_TIME,
                                TimeUnit.MILLISECONDS);
            }
        }

        @Override
        protected void spellPassive() {}
    }

    private class FlameProjectile extends Projectile {

        private boolean hitPlayer = false;

        public FlameProjectile(
                ATBPExtension parentExt,
                UserActor owner,
                Line2D path,
                float speed,
                float hitboxRadius,
                String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        public void hit(Actor victim) {
            if (this.hitPlayer) return;
            this.hitPlayer = true;
            JsonNode attackData = parentExt.getAttackData(getAvatar(), "spell1");
            victim.addToDamageQueue(FlamePrincess.this, getSpellDamage(attackData), attackData);
            ExtensionCommands.playSound(
                    parentExt, room, "", "akubat_projectileHit1", victim.getLocation());
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    this.id,
                    "flame_princess_projectile_large_explosion",
                    1000,
                    "flame_explosion",
                    false,
                    "",
                    false,
                    false,
                    team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    this.id,
                    "flame_princess_cone_of_flames",
                    1500,
                    "flame_cone",
                    false,
                    "",
                    true,
                    false,
                    team);
            for (Actor a :
                    Champion.getActorsAlongLine(
                            parentExt.getRoomHandler(room.getName()),
                            Champion.extendLine(path, 0.75f),
                            0.75f)) {
                if (!a.getId().equalsIgnoreCase(victim.getId()) && a.getTeam() != team) {
                    double newDamage = (double) getSpellDamage(attackData) * 1.2d;
                    a.addToDamageQueue(FlamePrincess.this, Math.round(newDamage), attackData);
                }
            }
            parentExt
                    .getTaskScheduler()
                    .schedule(new DelayedProjectile(), 300, TimeUnit.MILLISECONDS);
        }

        private class DelayedProjectile implements Runnable {

            @Override
            public void run() {
                destroy();
            }
        }
    }

    private class PassiveAttack implements Runnable {

        Actor target;
        boolean crit;

        PassiveAttack(Actor target, boolean crit) {
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (crit) damage *= 2;
            new Champion.DelayedAttack(
                            parentExt, FlamePrincess.this, target, (int) damage, "basicAttack")
                    .run();
            if (FlamePrincess.this.passiveEnabled
                    && (target.getActorType() != ActorType.TOWER
                            && target.getActorType() != ActorType.BASE)) {
                FlamePrincess.this.passiveEnabled = false;
                ExtensionCommands.removeFx(parentExt, room, id + "_flame_passive");
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "passive", true, 10000, 0);
                lastPassiveUsage = System.currentTimeMillis();
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        target.getId(),
                        "flame_princess_dot",
                        3000,
                        "flame_passive_burn",
                        true,
                        "",
                        false,
                        false,
                        team);
                for (int i = 0; i < 3; i++) {
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new Champion.DelayedAttack(
                                            parentExt, FlamePrincess.this, target, 20, "spell4"),
                                    i + 1,
                                    TimeUnit.SECONDS);
                }
            }
            if (FlamePrincess.this.passiveEnabled && target.getActorType() == ActorType.BASE
                    || target.getActorType() == ActorType.TOWER) {
                FlamePrincess.this.passiveEnabled = false;
                ExtensionCommands.removeFx(parentExt, room, id + "_flame_passive");
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "passive", true, 10000, 0);
                lastPassiveUsage = System.currentTimeMillis();
            }
        }
    }
}

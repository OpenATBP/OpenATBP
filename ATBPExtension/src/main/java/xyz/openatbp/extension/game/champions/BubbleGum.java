package xyz.openatbp.extension.game.champions;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class BubbleGum extends UserActor {

    private int gumStacks;
    private long lastGum;
    // icon_pb_p0 - icon_pb_p3
    private String currentIcon = "icon_pb_p0";
    private boolean potionActivated = false;
    private Point2D potionLocation;
    private long potionSpawn;
    private List<Turret> turrets;
    private int turretNum = 0;
    private boolean bombPlaced;
    private Point2D bombLocation;
    private long bombPlaceTime = 0;

    public BubbleGum(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.turrets = new ArrayList<>(2);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "Sticky Sweet", "Gum up your enemies", currentIcon, 0f);
    }

    @Override
    public void destroy() {
        super.destroy();
        int turretSize = this.turrets.size();
        for (int i = 0; i < turretSize; i++) {
            this.turrets.get(i).die(BubbleGum.this);
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
                                new PassiveAttack(this, a, this.handleAttack(a)),
                                "bubblegum_projectile",
                                "weapon_holder"),
                        500,
                        TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan) { // TODO: Working on PB Passive like the icons
        super.update(msRan);
        int gum = Character.getNumericValue(currentIcon.charAt(currentIcon.length() - 1));
        if (System.currentTimeMillis() - lastGum >= 3000) {
            if (gumStacks > 0) gumStacks--;
            this.lastGum = System.currentTimeMillis();
        }
        if (gumStacks != gum) {
            ExtensionCommands.removeStatusIcon(parentExt, player, "Sticky Sweet");
            currentIcon = "icon_pb_p" + gumStacks;
            float duration = 3000f;
            if (gumStacks == 0) duration = 0f;
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    "Sticky Sweet",
                    "Gum up your enemies",
                    currentIcon,
                    duration);
        }
        if (potionActivated) {
            if (System.currentTimeMillis() - potionSpawn >= 3000) {
                potionActivated = false;
                potionSpawn = -1;
                potionLocation = null;
            } else {
                List<Actor> affectedActors =
                        Champion.getActorsInRadius(
                                parentExt.getRoomHandler(room.getName()), potionLocation, 2f);
                for (Actor a : affectedActors) {
                    if (a.getTeam() != this.team) {
                        JsonNode spellData =
                                this.parentExt.getAttackData("princessbubblegum", "spell1");
                        double damage = this.getSpellDamage(spellData) / 10f;
                        a.addToDamageQueue(this, damage, spellData, true);
                        if (isNonStructure(a)) a.addState(ActorState.SLOWED, 0.3d, 2000);
                    } else if (a.getId().equalsIgnoreCase(this.id)) {
                        this.addEffect("speed", this.getStat("speed") * 0.4d, 2000);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "statusEffect_speed",
                                2000,
                                this.id + "_pbQSpeed",
                                true,
                                "Bip01 Footsteps",
                                true,
                                false,
                                this.team);
                    }
                }
            }
        }
        ArrayList<Turret> turrets =
                new ArrayList<>(this.turrets); // To remove concurrent exceptions
        for (Turret t : turrets) {
            t.update(msRan);
        }
        if (this.bombPlaced && System.currentTimeMillis() - this.bombPlaceTime >= 4000) {
            int baseUltCooldown = ChampionData.getBaseAbilityCooldown(this, 3);
            this.useBomb(getReducedCooldown(baseUltCooldown), 250);
        }
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
            case 1: // Q
                this.canCast[0] = false;
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new PBAbilityRunnable(
                                        ability, spellData, cooldown, gCooldown, dest),
                                castDelay,
                                TimeUnit.MILLISECONDS);
                this.stopMoving();
                String potionVo =
                        (this.avatar.equals("princessbubblegum_skin_prince"))
                                ? "vo/vo_gumball_potion"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_bubblegum_young_potion"
                                        : "vo/vo_bubblegum_potion";
                ExtensionCommands.playSound(parentExt, room, id, potionVo, this.location);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "fx_target_ring_2",
                        id + "_potionArea",
                        3000 + castDelay,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        true,
                        this.team,
                        0f);
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "q", true, getReducedCooldown(cooldown), gCooldown);
                break;
            case 2: // W
                this.canCast[1] = false;
                this.stopMoving();
                String turretVo =
                        (this.avatar.equals("princessbubblegum_skin_prince"))
                                ? "vo/vo_gumball_turret"
                                : (this.avatar.contains("young"))
                                        ? "vo/vo_bubblegum_young_turret"
                                        : "vo/vo_bubblegum_turret";
                ExtensionCommands.playSound(parentExt, room, id, turretVo, this.location);
                this.spawnTurret(dest);
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new PBAbilityRunnable(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 3: // E
                this.canCast[2] = false;
                if (!this.bombPlaced) {
                    this.bombPlaced = true;
                    this.stopMoving();
                    this.bombPlaceTime = System.currentTimeMillis();
                    String bombVo =
                            (this.avatar.equals("princessbubblegum_skin_prince"))
                                    ? "vo/vo_gumball_bomb_hup"
                                    : (this.avatar.contains("young"))
                                            ? "vo/vo_bubblegum_young_bomb_hup"
                                            : "vo/vo_bubblegum_bomb_hup";
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, bombVo, this.location);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_3",
                            id + "_bombArea",
                            4000,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            this.team,
                            0f);
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "bubblegum_bomb_trap",
                            this.id + "_bomb",
                            4000,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            false,
                            this.team,
                            0f);
                    this.bombLocation = dest;
                    ExtensionCommands.actorAbilityResponse(
                            this.parentExt, this.player, "e", true, 750, 0);
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new PBAbilityRunnable(
                                            ability, spellData, cooldown, gCooldown, dest),
                                    750,
                                    TimeUnit.MILLISECONDS);
                } else {
                    this.useBomb(getReducedCooldown(cooldown), gCooldown);
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new PBAbilityRunnable(
                                            ability, spellData, cooldown, gCooldown, dest),
                                    getReducedCooldown(cooldown),
                                    TimeUnit.MILLISECONDS);
                }
                break;
        }
    }

    protected void useBomb(int cooldown, int gCooldown) {
        for (Actor a :
                Champion.getActorsInRadius(
                        this.parentExt.getRoomHandler(this.room.getName()),
                        this.bombLocation,
                        3f)) {
            if ((a.getTeam() != this.team || a.getId().equalsIgnoreCase(this.id))
                    && a.getActorType() != ActorType.BASE
                    && a.getActorType() != ActorType.TOWER) {
                a.knockback(this.bombLocation);
                JsonNode spellData = parentExt.getAttackData("peebles", "spell3");
                if (a.getTeam() != this.team)
                    a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                else if (a.getId().equalsIgnoreCase(this.id)) {
                    ExtensionCommands.actorAnimate(parentExt, room, this.id, "spell3b", 325, false);
                    Runnable animationDelay =
                            () ->
                                    ExtensionCommands.actorAnimate(
                                            parentExt, room, id, "spell3c", 350, false);
                    parentExt
                            .getTaskScheduler()
                            .schedule(animationDelay, 325, TimeUnit.MILLISECONDS);
                }
            }
        }
        String useBombVo =
                (this.avatar.equals("princessbubblegum_skin_prince"))
                        ? "vo/vo_gumball_turret"
                        : (this.avatar.contains("young"))
                                ? "vo/vo_bubblegum_young_bomb_grunt"
                                : "vo/vo_bubblegum_bomb_grunt";
        ExtensionCommands.playSound(parentExt, room, this.id, useBombVo, this.location);
        ExtensionCommands.removeFx(parentExt, room, id + "_bomb");
        ExtensionCommands.removeFx(parentExt, room, id + "_bombArea");
        ExtensionCommands.playSound(parentExt, room, "", "sfx_bubblegum_bomb", bombLocation);
        ExtensionCommands.createWorldFX(
                parentExt,
                room,
                id,
                "bubblegum_bomb_explosion",
                id + "_bombExplosion",
                1500,
                (float) bombLocation.getX(),
                (float) bombLocation.getY(),
                false,
                team,
                0f);
        ExtensionCommands.actorAbilityResponse(parentExt, player, "e", true, cooldown, gCooldown);
        this.bombPlaced = false;
        this.bombLocation = null;
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        List<Turret> aliveTurrets = new ArrayList<>(this.turrets);
        for (Turret t : aliveTurrets) {
            t.die(a);
        }
        this.turrets = new ArrayList<>();
    }

    private void spawnTurret(Point2D dest) {
        Turret t = null;
        if (this.turrets != null && this.turrets.size() == 2) {
            this.turrets.get(0).die(this);
            t = new Turret(dest, this.turretNum);
        } else if (this.turrets != null) {
            t = new Turret(dest, this.turretNum);
        }
        if (this.turrets != null) {
            this.turretNum++;
            this.turrets.add(t);
            this.parentExt.getRoomHandler(this.room.getName()).addCompanion(t);
        }
    }

    public void handleTurretDeath(Turret t) {
        this.turrets.remove(t);
    }

    private class PBAbilityRunnable extends AbilityRunnable {

        public PBAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int Q_CAST_DELAY = 750;
            Runnable enableQCasting = () -> canCast[0] = true;
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            enableQCasting,
                            getReducedCooldown(cooldown) - Q_CAST_DELAY,
                            TimeUnit.MILLISECONDS);
            if (getHealth() > 0) {
                potionActivated = true;
                potionLocation = dest;
                potionSpawn = System.currentTimeMillis();
                ExtensionCommands.playSound(parentExt, room, "", "sfx_bubblegum_potion_aoe", dest);
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "bubblegum_ground_aoe",
                        id + "_potionAoe",
                        3000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
            } else {
                ExtensionCommands.removeFx(parentExt, room, id + "_potionArea");
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

    private class PassiveAttack implements Runnable { // TODO: Maybe extend DelayedAttack?

        Actor attacker;
        Actor target;
        boolean crit;

        PassiveAttack(Actor a, Actor t, boolean crit) {
            this.attacker = a;
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = this.attacker.getPlayerStat("attackDamage");
            if (crit) damage *= 2;
            new Champion.DelayedAttack(
                            parentExt, this.attacker, this.target, (int) damage, "basicAttack")
                    .run();
            if (this.target.getActorType() == ActorType.PLAYER) {
                gumStacks++;
                lastGum = System.currentTimeMillis();
                if (gumStacks > 3) {
                    gumStacks = 3;
                }
                double change = 0.25d / (4 - gumStacks);
                double targetAttackSpeed = this.target.getStat("attackSpeed");
                double getReducedSpeed = this.target.getTempStat("attackSpeed");
                double newDamage = (targetAttackSpeed * change);
                if (getReducedSpeed / targetAttackSpeed >= 0.25) newDamage = 0;
                this.target.addEffect("attackSpeed", newDamage, 3000);
            }
        }
    }

    private class Turret extends Actor {
        private long timeOfBirth;
        private Actor target;
        private boolean dead = false;
        private String iconName;

        Turret(Point2D location, int turretNum) {
            this.room = BubbleGum.this.room;
            this.parentExt = BubbleGum.this.parentExt;
            this.currentHealth = 100 + (BubbleGum.this.maxHealth * 0.3d);
            this.maxHealth = this.currentHealth;
            this.location = location;
            this.avatar = "princessbubblegum_turret";
            this.id = "turret" + turretNum + "_" + BubbleGum.this.id;
            this.team = BubbleGum.this.team;
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            this.setStat("attackDamage", BubbleGum.this.getStat("attackDamage"));
            this.iconName = "Turret #" + turretNum;
            ExtensionCommands.addStatusIcon(
                    parentExt, player, iconName, "Turret placed!", "icon_pb_s2", 60000f);
            ExtensionCommands.createActor(
                    parentExt, room, this.id, this.avatar, this.location, 0f, this.team);
            Runnable creationDelay =
                    () -> {
                        ExtensionCommands.playSound(
                                parentExt,
                                room,
                                this.id,
                                "sfx_bubblegum_turret_spawn",
                                this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fx_target_ring_3",
                                60000,
                                this.id + "_ring",
                                true,
                                "",
                                false,
                                true,
                                this.team);
                    };
            parentExt.getTaskScheduler().schedule(creationDelay, 150, TimeUnit.MILLISECONDS);
            this.addState(ActorState.IMMUNITY, 0d, 1000 * 60 * 15);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {
            this.target = null;
            this.findTarget();
        }

        @Override
        public void attack(Actor a) {
            float time = (float) (a.getLocation().distance(this.location) / 10f);
            ExtensionCommands.playSound(
                    parentExt, room, this.id, "sfx_bubblegum_turret_shoot", this.location);
            ExtensionCommands.createProjectileFX(
                    parentExt,
                    room,
                    "bubblegum_turret_projectile",
                    this.id,
                    a.getId(),
                    "Bip01",
                    "targetNode",
                    time);
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            new Champion.DelayedAttack(
                                    parentExt,
                                    this,
                                    a,
                                    10 + (int) this.getPlayerStat("attackDamage"),
                                    "turretAttack"),
                            (int) time * 1000,
                            TimeUnit.MILLISECONDS);
            BubbleGum.this.handleLifeSteal();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
        }

        @Override
        public void addState(ActorState state, double delta, int duration) {
            if (state == ActorState.CHARMED || state == ActorState.FEARED) return;
            super.addState(state, delta, duration);
        }

        @Override
        public void addState(
                ActorState state, double delta, int duration, String fxId, String emit) {
            if (state == ActorState.CHARMED || state == ActorState.FEARED) return;
            super.addState(state, delta, duration, fxId, emit);
        }

        @Override
        public void die(Actor a) {
            this.dead = true;
            this.currentHealth = 0;
            ExtensionCommands.removeStatusIcon(parentExt, player, iconName);
            ExtensionCommands.removeFx(parentExt, room, this.id + "_ring");
            ExtensionCommands.destroyActor(parentExt, room, this.id);
            this.parentExt.getRoomHandler(this.room.getName()).removeCompanion(this);
            BubbleGum.this.handleTurretDeath(this);
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            this.handleActiveEffects();
            if (this.dead) return;
            if (System.currentTimeMillis() - this.timeOfBirth >= 60000) {
                this.die(this);
                return;
            }
            if (this.attackCooldown > 0) this.reduceAttackCooldown();
            if (this.target != null && this.target.getHealth() > 0) {
                if (this.withinRange(this.target)
                        && this.canAttack()
                        && isNonStructure(this.target)) this.attack(this.target);
                else if (!this.withinRange(this.target)) {
                    this.target = null;
                    this.findTarget();
                }
            } else {
                if (this.target != null && this.target.getHealth() <= 0) this.target = null;
                this.findTarget();
            }
        }

        private boolean turretInBrush() {
            for (Path2D brush :
                    this.parentExt.getBrushPaths(
                            this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap())) {
                if (brush.contains(this.location)) {
                    return true;
                }
            }
            return false;
        }

        public void findTarget() {
            List<Actor> potentialTargets =
                    this.parentExt.getRoomHandler(this.room.getName()).getActors();
            List<Actor> users =
                    potentialTargets.stream()
                            .filter(a -> a.getActorType() == ActorType.PLAYER)
                            .collect(Collectors.toList());
            for (Actor ua : users) {
                if (ua.getTeam() != this.team && this.withinRange(ua) && ua.getHealth() > 0) {
                    if (ua.getState(ActorState.BRUSH) && turretInBrush()) this.target = ua;
                    if (!ua.getState(ActorState.BRUSH)) this.target = ua;
                    break;
                }
            }
            if (this.target == null) {
                for (Actor a : potentialTargets) {
                    if (a.getTeam() != this.team && this.withinRange(a) && a.getHealth() > 0) {
                        if (a.getState(ActorState.BRUSH) && turretInBrush()) this.target = a;
                        if (!a.getState(ActorState.BRUSH)) this.target = a;
                        break;
                    }
                }
            }
        }

        @Override
        public void setTarget(Actor a) {
            this.target = a;
        }
    }
}

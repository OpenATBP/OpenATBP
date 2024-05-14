package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Finn extends UserActor {
    private int furyStacks = 0;
    private Actor furyTarget = null;
    private boolean qActive = false;
    private boolean[] wallsActivated = {false, false, false, false}; // NORTH, EAST, SOUTH, WEST
    private Line2D[] wallLines;
    private boolean ultActivated = false;
    private long passiveStart = 0;
    private float ultX;
    private float ultY;
    private long qStartTime = 0;
    private long eStartTime = 0;
    private boolean isCastingUlt = false;
    private Path2D finnUltRing;
    private boolean ringBoostApplied = false;
    private static final float W_OFFSET_DISTANCE = 1.25f;

    public Finn(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        this.applyStopMovingDuringAttack();
        parentExt
                .getTaskScheduler()
                .schedule(new PassiveAttack(a, this.handleAttack(a)), 500, TimeUnit.MILLISECONDS);
        passiveStart = System.currentTimeMillis();
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (a.getActorType() == ActorType.PLAYER) {
            this.canCast[1] = true;
            ExtensionCommands.actorAbilityResponse(this.parentExt, this.player, "w", true, 0, 0);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "vo/vo_finn_assist_1", this.location);
        }
    }

    @Override
    public void increaseStat(String key, double num) {
        super.increaseStat(key, num);
        if (key.equalsIgnoreCase("assists")) {
            this.canCast[1] = true;
            ExtensionCommands.actorAbilityResponse(this.parentExt, this.player, "w", true, 0, 0);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "vo/vo_finn_assist_1", this.location);
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("attackSpeed")
                && finnUltRing != null
                && finnUltRing.contains(this.getLocation())) {
            double currentAttackSpeed = super.getPlayerStat("attackSpeed");
            double modifier = (this.getStat("attackSpeed") * 0.2d);
            return currentAttackSpeed - modifier < 500 ? 500 : currentAttackSpeed - modifier;
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.furyTarget != null)
            ExtensionCommands.removeFx(parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
        furyStacks = 0;
        if (qActive) {
            for (Actor actor :
                    Champion.getActorsInRadius(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.location,
                            2f)) {
                if (isNonStructure(actor)) {
                    JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                    actor.addToDamageQueue(Finn.this, getSpellDamage(spellData), spellData, false);
                }
            }
            qActive = false;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_shield");
            String shatterPrefix = (this.avatar.contains("guardian")) ? "finn_guardian_" : "finn_";
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.room,
                    this.id,
                    "sfx_" + shatterPrefix + "shield_shatter",
                    this.location);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    shatterPrefix + "shieldShatter",
                    1000,
                    this.id + "_qShatter",
                    true,
                    "",
                    true,
                    false,
                    this.team);
        }
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.isCastingUlt) this.canCast[1] = false;
        if (ultActivated) {
            if (finnUltRing != null
                    && finnUltRing.contains(this.getLocation())
                    && !ringBoostApplied) {
                ringBoostApplied = true;
                updateStatMenu("attackSpeed");
            } else if (finnUltRing != null
                    && !finnUltRing.contains(this.getLocation())
                    && ringBoostApplied) {
                ringBoostApplied = false;
                updateStatMenu("attackSpeed");
            }
        }
        if (this.ultActivated && System.currentTimeMillis() - this.eStartTime >= 5000) {
            this.wallLines = null;
            this.wallsActivated = new boolean[] {false, false, false, false};
            this.ultActivated = false;
            this.finnUltRing = null;
            this.updateStatMenu("attackSpeed");
        }
        if (this.qActive && this.qStartTime + 3000 <= System.currentTimeMillis()) {
            Console.debugLog("Disabling Finn Q!");
            this.qActive = false;
        }
        if (this.ultActivated) {
            for (int i = 0; i < this.wallLines.length; i++) {
                if (this.wallsActivated[i]) {
                    for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getActors()) {
                        if (this.isNonStructure(a)
                                && this.wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            this.wallsActivated[i] = false;
                            JsonNode spellData = this.parentExt.getAttackData("finn", "spell3");
                            a.addState(ActorState.ROOTED, 0d, 2000);
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                            String direction = "north";
                            if (i == 1) direction = "east";
                            else if (i == 2) direction = "south";
                            else if (i == 3) direction = "west";
                            String destroyFxPrefix =
                                    (this.avatar.contains("guardian")) ? "finn_guardian" : "finn";
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_" + direction + "Wall");
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    destroyFxPrefix + "_wall_" + direction + "_destroy",
                                    this.id + "_wallDestroy_" + direction,
                                    1000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    180f);
                            String wallDestroyedSfx =
                                    (this.avatar.contains("guardian"))
                                            ? "sfx_finn_guardian_wall_destroyed"
                                            : "finn_wall_destroyed";
                            ExtensionCommands.playSound(
                                    parentExt, room, id, wallDestroyedSfx, this.location);
                            break;
                        }
                    }
                }
            }
        }
        if (furyStacks > 0) {
            if (System.currentTimeMillis() - passiveStart >= 5000) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
            if (furyTarget.getHealth() <= 0) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
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
            case 1:
                this.canCast[0] = false;
                this.attackCooldown = 0;
                this.qStartTime = System.currentTimeMillis();
                this.qActive = true;
                this.updateStatMenu("speed");
                String shieldPrefix =
                        (this.avatar.contains("guardian")) ? "finn_guardian_" : "finn_";
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_" + shieldPrefix + "shield",
                        this.location);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        shieldPrefix + "shieldShimmer",
                        3000,
                        this.id + "_shield",
                        true,
                        "Bip001 Pelvis",
                        true,
                        false,
                        this.team);
                this.addEffect("speed", 0.5d, 3000);
                this.addEffect("armor", this.getStat("armor") * 0.25d, 3000);
                this.addEffect("attackSpeed", this.getStat("attackSpeed") * -0.20d, 3000);
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
                                new FinnAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                float W_SPELL_RANGE =
                        this.location.distance(dest) >= 5
                                ? 5
                                : (float) this.location.distance(dest);
                Path2D quadrangle =
                        Champion.createRectangle(location, dest, W_SPELL_RANGE, W_OFFSET_DISTANCE);
                Point2D ogLocation = this.location;
                Point2D finalDashPoint = this.dash(dest, false, DASH_SPEED);
                double time = ogLocation.distance(finalDashPoint) / DASH_SPEED;
                int wTime = (int) (time * 1000);
                for (Actor a : this.parentExt.getRoomHandler(this.room.getName()).getActors()) {
                    if (a.getTeam() != this.team && quadrangle.contains(a.getLocation())) {
                        if (!isNonStructure(a))
                            a.addToDamageQueue(this, getSpellDamage(spellData), spellData, false);
                        else {
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                        }
                    }
                }
                String sfxDash =
                        (this.avatar.contains("guardian"))
                                ? "sfx_finn_guardian_dash_attack"
                                : "sfx_finn_dash_attack";
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, sfxDash, this.location);
                Runnable changeAnimation =
                        () ->
                                ExtensionCommands.actorAnimate(
                                        this.parentExt, this.room, this.id, "run", 100, false);

                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "w",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                parentExt
                        .getTaskScheduler()
                        .schedule(changeAnimation, wTime, TimeUnit.MILLISECONDS);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new FinnAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, finalDashPoint),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.isCastingUlt = true;
                int immobilizationTime = 1200;
                this.stopMoving(immobilizationTime);
                Runnable enableDashCasting =
                        () -> {
                            this.isCastingUlt = false;
                            this.canCast[1] = true;
                        };
                parentExt
                        .getTaskScheduler()
                        .schedule(enableDashCasting, immobilizationTime, TimeUnit.MILLISECONDS);
                this.canCast[2] = false;
                Runnable cast =
                        () -> {
                            this.ultActivated = true;
                            this.eStartTime = System.currentTimeMillis();
                            double widthHalf = 3.675d;
                            Point2D p1 =
                                    new Point2D.Double(
                                            this.location.getX() - widthHalf,
                                            this.location.getY() + widthHalf); // BOT RIGHT
                            Point2D p2 =
                                    new Point2D.Double(
                                            this.location.getX() + widthHalf,
                                            this.location.getY() + widthHalf); // BOT LEFT
                            Point2D p3 =
                                    new Point2D.Double(
                                            this.location.getX() - widthHalf,
                                            this.location.getY() - widthHalf); // TOP RIGHT
                            Point2D p4 =
                                    new Point2D.Double(
                                            this.location.getX() + widthHalf,
                                            this.location.getY() - widthHalf); // TOP LEFT
                            this.ultX = (float) this.location.getX();
                            this.ultY = (float) this.location.getY();
                            finnUltRing = new Path2D.Float();
                            finnUltRing.moveTo(p2.getX(), p2.getY());
                            finnUltRing.lineTo(p4.getX(), p4.getY());
                            finnUltRing.lineTo(p3.getX(), p3.getY());
                            finnUltRing.lineTo(p1.getX(), p1.getY());
                            String wallPrefix =
                                    (this.avatar.contains("guardian")) ? "finn_guardian_" : "finn_";
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "sfx_" + wallPrefix + "walls_drop",
                                    this.location);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    "fx_target_square_4.5",
                                    5000,
                                    this.id + "_eSquare",
                                    false,
                                    "",
                                    false,
                                    true,
                                    this.team);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallPrefix + "wall_south",
                                    this.id + "_northWall",
                                    5000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallPrefix + "wall_north",
                                    this.id + "_southWall",
                                    5000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallPrefix + "wall_west",
                                    this.id + "_eastWall",
                                    5000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallPrefix + "wall_east",
                                    this.id + "_westWall",
                                    5000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    0f);
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallPrefix + "wall_corner_swords",
                                    this.id + "_p1Sword",
                                    5000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    0f);
                            Line2D northWall = new Line2D.Float(p4, p3);
                            Line2D eastWall = new Line2D.Float(p3, p1);
                            Line2D southWall = new Line2D.Float(p2, p1);
                            Line2D westWall = new Line2D.Float(p4, p2);
                            this.wallLines =
                                    new Line2D[] {northWall, eastWall, southWall, westWall};
                            this.wallsActivated = new boolean[] {true, true, true, true};
                            ExtensionCommands.actorAbilityResponse(
                                    this.parentExt,
                                    this.player,
                                    "e",
                                    true,
                                    getReducedCooldown(cooldown),
                                    gCooldown);
                            updateStatMenu("attackSpeed");
                        };
                parentExt.getTaskScheduler().schedule(cast, castDelay, TimeUnit.MILLISECONDS);
                parentExt
                        .getTaskScheduler()
                        .schedule(
                                new FinnAbilityHandler(
                                        ability, spellData, cooldown, gCooldown, dest),
                                getReducedCooldown(cooldown),
                                TimeUnit.MILLISECONDS);
                break;
        }
    }

    protected double handlePassive(Actor target, double damage) {
        if (furyTarget != null) {
            if (furyTarget.getId().equalsIgnoreCase(target.getId())) {
                damage *= (1 + (0.2 * furyStacks));
                if (furyStacks < 3) {
                    if (furyStacks > 0)
                        ExtensionCommands.removeFx(
                                parentExt, room, target.getId() + "_mark" + furyStacks);
                    furyStacks++;
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            target.getId(),
                            "fx_mark" + furyStacks,
                            1000 * 15 * 60,
                            target.getId() + "_mark" + furyStacks,
                            true,
                            "",
                            true,
                            false,
                            target.getTeam());
                } else {
                    furyStacks = 0;
                    ExtensionCommands.removeFx(parentExt, room, target.getId() + "_mark3");
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            target.getId(),
                            "fx_mark4",
                            500,
                            target.getId() + "_mark4",
                            true,
                            "",
                            true,
                            false,
                            target.getTeam());
                    if (this.qActive) {
                        Console.debugLog("Finn Q Popped!");
                        for (Actor actor :
                                Champion.getActorsInRadius(
                                        this.parentExt.getRoomHandler(this.room.getName()),
                                        this.location,
                                        2f)) {
                            if (isNonStructure(actor)) {
                                JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                                actor.addToDamageQueue(
                                        Finn.this, getSpellDamage(spellData), spellData, false);
                            }
                        }
                        this.qActive = false;
                        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_shield");
                        String shatterPrefix =
                                (this.avatar.contains("guardian")) ? "finn_guardian_" : "finn_";
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "sfx_" + shatterPrefix + "shield_shatter",
                                this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                shatterPrefix + "shieldShatter",
                                1000,
                                this.id + "_qShatter",
                                true,
                                "",
                                true,
                                false,
                                this.team);
                    }
                }
            } else {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        target.getId(),
                        "fx_mark1",
                        1000 * 15 * 60,
                        target.getId() + "_mark1",
                        true,
                        "",
                        true,
                        false,
                        target.getTeam());
                furyTarget = target;
                furyStacks = 1;
            }
        } else {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    target.getId(),
                    "fx_mark1",
                    1000 * 15 * 60,
                    target.getId() + "_mark1",
                    true,
                    "",
                    true,
                    false,
                    target.getTeam());
            furyTarget = target;
            furyStacks = 1;
        }
        return damage;
    }

    private class FinnAbilityHandler extends AbilityRunnable {

        Point2D originalLocation = null;

        public FinnAbilityHandler(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        public FinnAbilityHandler(
                int ability,
                JsonNode spellData,
                int cooldown,
                int gCooldown,
                Point2D dest,
                Point2D ogLocation) {
            super(ability, spellData, cooldown, gCooldown, dest);
            this.originalLocation = ogLocation;
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
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }

    private class PassiveAttack implements Runnable {

        Actor target;
        boolean crit;

        PassiveAttack(Actor t, boolean crit) {
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if (this.crit) damage *= 2;
            if (target.getActorType() != ActorType.TOWER && target.getActorType() != ActorType.BASE)
                damage = handlePassive(target, damage);
            new Champion.DelayedAttack(parentExt, Finn.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

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
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Finn extends UserActor {
    private static final int PASSIVE_DURATION = 5000;
    private static final float Q_ATTACKSPEED_VALUE = 0.2f;
    private static final float Q_ARMOR_VALUE = 0.15f;
    private static final float Q_SPEED_VALUE = 0.5f;
    private static final int Q_ATTACKSPEED_DURATION = 3000;
    private static final int Q_ARMOR_DURATION = 3000;
    private static final int Q_SPEED_DURATION = 3000;
    private static final float W_OFFSET_DISTANCE = 1.25f;
    private static final int E_DURATION = 5000;
    private static final int E_ROOT_DURATION = 2000;
    private static final int E_SELF_CRIPPLE_DURATION = 1200;
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

    public Finn(User u, ATBPExtension parentExt) {
        super(u, parentExt);
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
        if (this.ultActivated && System.currentTimeMillis() - this.eStartTime >= E_DURATION) {
            this.wallLines = null;
            this.wallsActivated = new boolean[] {false, false, false, false};
            this.ultActivated = false;
            this.finnUltRing = null;
            this.updateStatMenu("attackSpeed");
        }
        if (this.qActive && this.qStartTime + 3000 <= System.currentTimeMillis()) {
            this.qActive = false;
        }
        if (this.ultActivated) {
            for (int i = 0; i < this.wallLines.length; i++) {
                if (this.wallsActivated[i]) {
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> nonStructureEnemies = handler.getNonStructureEnemies(this.team);
                    for (Actor a : nonStructureEnemies) {
                        if (this.wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            this.wallsActivated[i] = false;
                            JsonNode spellData = this.parentExt.getAttackData("finn", "spell3");
                            a.addState(ActorState.ROOTED, 0d, E_ROOT_DURATION);
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData, true)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                            String direction = "north";
                            if (i == 1) direction = "east";
                            else if (i == 2) direction = "south";
                            else if (i == 3) direction = "west";

                            String wallDestroyedFX = SkinData.getFinnEDestroyFX(avatar, direction);
                            String wallDestroyedSFX = SkinData.getFinnEDestroySFX(avatar);
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, this.id + "_" + direction + "Wall");
                            ExtensionCommands.createWorldFX(
                                    this.parentExt,
                                    this.room,
                                    this.id,
                                    wallDestroyedFX,
                                    this.id + "_wallDestroy_" + direction,
                                    1000,
                                    ultX,
                                    ultY,
                                    false,
                                    this.team,
                                    180f);
                            ExtensionCommands.playSound(
                                    parentExt, room, id, wallDestroyedSFX, this.location);
                            break;
                        }
                    }
                }
            }
        }
        if (furyStacks > 0) {
            if (System.currentTimeMillis() - passiveStart >= PASSIVE_DURATION) {
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
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            PassiveAttack passiveAttack = new PassiveAttack(a, this.handleAttack(a));
            scheduleTask(passiveAttack, BASIC_ATTACK_DELAY);
            passiveStart = System.currentTimeMillis();
        }
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
        /* NERF 8/13/24
        if (stat.equalsIgnoreCase("attackSpeed")
                && finnUltRing != null
                && finnUltRing.contains(this.getLocation())) {
            double currentAttackSpeed = super.getPlayerStat("attackSpeed");
            double modifier = (this.getStat("attackSpeed") * Q_ATTACKSPEED_VALUE);
            return currentAttackSpeed - modifier < BASIC_ATTACK_DELAY
                    ? BASIC_ATTACK_DELAY
                    : currentAttackSpeed - modifier;
        }
         */
        return super.getPlayerStat(stat);
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        if (this.furyTarget != null)
            ExtensionCommands.removeFx(parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
        furyStacks = 0;
        if (qActive) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            for (Actor actor : Champion.getActorsInRadius(handler, this.location, 2f)) {
                if (isNonStructure(actor)) {
                    JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                    actor.addToDamageQueue(
                            Finn.this, getSpellDamage(spellData, true), spellData, false);
                }
            }
            qActive = false;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_shield");
            String shatterFX = SkinData.getFinnQShatterFX(avatar);
            String shatterSFX = SkinData.getFinnQShatterSFX(avatar);
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, shatterSFX, this.location);
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    shatterFX,
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
                    this.attackCooldown = 0;
                    this.qStartTime = System.currentTimeMillis();
                    this.qActive = true;
                    this.updateStatMenu("speed");
                    String shieldFX = SkinData.getFinnQFX(avatar);
                    String shieldSFX = SkinData.getFinnQSFX(avatar);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, shieldSFX, this.location);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            this.id,
                            shieldFX,
                            3000,
                            this.id + "_shield",
                            true,
                            "Bip001 Pelvis",
                            true,
                            false,
                            this.team);
                    double asDelta = this.getStat("attackSpeed") * -Q_ATTACKSPEED_VALUE;
                    this.addEffect("speed", Q_SPEED_VALUE, Q_SPEED_DURATION);
                    this.addEffect(
                            "armor", this.getStat("armor") * Q_ARMOR_VALUE, Q_ARMOR_DURATION);
                    this.addEffect("attackSpeed", asDelta, Q_ATTACKSPEED_DURATION);
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
                float W_SPELL_RANGE =
                        location.distance(dest) >= 5 ? 5 : (float) this.location.distance(dest);
                Path2D quadrangle =
                        Champion.createRectangle(location, dest, W_SPELL_RANGE, W_OFFSET_DISTANCE);
                Point2D ogLocation = this.location;
                Point2D finalDashPoint = this.dash(dest, false, DASH_SPEED);
                double time = ogLocation.distance(finalDashPoint) / DASH_SPEED;
                int wTime = (int) (time * 1000);
                Runnable endAnim =
                        () ->
                                ExtensionCommands.actorAnimate(
                                        parentExt, room, id, "idle", wTime, false);
                scheduleTask(endAnim, wTime);
                String dashFX = SkinData.getFinnWFX(avatar);
                String dashSFX = SkinData.getFinnWSFX(avatar);
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        dashFX,
                        wTime,
                        this.id + "finnWTrail",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                ExtensionCommands.playSound(
                        this.parentExt, this.room, this.id, dashSFX, this.location);

                RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                List<Actor> actorsInPolygon = handler.getEnemiesInPolygon(this.team, quadrangle);
                if (!actorsInPolygon.isEmpty()) {
                    for (Actor a : actorsInPolygon) {
                        if (a.getActorType() == ActorType.TOWER
                                || a.getActorType() == ActorType.BASE) {
                            a.addToDamageQueue(
                                    this, getSpellDamage(spellData, true), spellData, false);
                        } else {
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData, true)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                        }
                    }
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
                        abilityRunnable(ability, spellData, cooldown, gCooldown, finalDashPoint),
                        delay1);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    this.isCastingUlt = true;
                    this.stopMoving(E_SELF_CRIPPLE_DURATION);
                    Runnable enableDashCasting =
                            () -> {
                                this.isCastingUlt = false;
                                this.canCast[1] = true;
                            };
                    scheduleTask(enableDashCasting, E_SELF_CRIPPLE_DURATION);

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

                                String[] directions = {"north", "east", "south", "west"};
                                String wallDropSFX = SkinData.getFinnEWallDropSFX(avatar);
                                String cornerSwordsFX = SkinData.getFinnECornerSwordsFX(avatar);

                                for (String direction : directions) {
                                    String bundle = SkinData.getFinnEWallFX(avatar, direction);
                                    ExtensionCommands.createWorldFX(
                                            this.parentExt,
                                            this.room,
                                            this.id,
                                            bundle,
                                            this.id + "_" + direction + "Wall",
                                            E_DURATION,
                                            ultX,
                                            ultY,
                                            false,
                                            this.team,
                                            180f);
                                }
                                ExtensionCommands.createActorFX(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        "fx_target_square_4.5",
                                        E_DURATION,
                                        this.id + "_eSquare",
                                        false,
                                        "",
                                        false,
                                        true,
                                        this.team);
                                ExtensionCommands.playSound(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        wallDropSFX,
                                        this.location);
                                ExtensionCommands.createWorldFX(
                                        this.parentExt,
                                        this.room,
                                        this.id,
                                        cornerSwordsFX,
                                        this.id + "_p1Sword",
                                        E_DURATION,
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

                                updateStatMenu("attackSpeed");
                            };

                    scheduleTask(cast, castDelay);
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
                int delay2 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay2);
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
                        RoomHandler handler = parentExt.getRoomHandler(room.getName());
                        for (Actor actor : Champion.getActorsInRadius(handler, this.location, 2f)) {
                            if (isNonStructure(actor)) {
                                JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                                actor.addToDamageQueue(
                                        Finn.this,
                                        getSpellDamage(spellData, true),
                                        spellData,
                                        false);
                            }
                        }
                        this.qActive = false;
                        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_shield");
                        String shatterFX = SkinData.getFinnQShatterFX(avatar);
                        String shatterSFX = SkinData.getFinnQShatterSFX(avatar);
                        ExtensionCommands.playSound(
                                this.parentExt, this.room, this.id, shatterSFX, this.location);
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                shatterFX,
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

    private FinnAbilityRunnable abilityRunnable(
            int ability, JsonNode spelldata, int cooldown, int gCooldown, Point2D dest) {
        return new FinnAbilityRunnable(ability, spelldata, cooldown, gCooldown, dest);
    }

    private class FinnAbilityRunnable extends AbilityRunnable {

        public FinnAbilityRunnable(
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

package xyz.openatbp.extension.game.champions;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class Finn extends UserActor {
    public static final int PASSIVE_DURATION = 5000;
    public static final float Q_ATTACK_SPEED_PERCENT = 0.2f;
    public static final float Q_ARMOR_PERCENT = 0.15f;
    public static final float Q_SPEED_PERCENT = 0.1f;
    public static final int Q_ATTACKSPEED_DURATION = 3000;
    public static final int Q_ARMOR_DURATION = 3000;
    public static final int Q_SPEED_DURATION = 3000;
    public static final float W_OFFSET_DISTANCE = 1.25f;
    public static final int E_DURATION = 5000;
    public static final int E_ROOT_DURATION = 2000;
    public static final int E_SELF_CRIPPLE_DURATION = 1200;

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
    private int wDuration = 0;

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
            } else if (finnUltRing != null
                    && !finnUltRing.contains(this.getLocation())
                    && ringBoostApplied) {
                ringBoostApplied = false;
            }
        }

        if (this.qActive && this.qStartTime + 3000 <= System.currentTimeMillis()) {
            this.qActive = false;
        }

        if (this.ultActivated && wallLines != null) {
            for (int i = 0; i < this.wallLines.length; i++) {
                if (this.wallsActivated[i]) {
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> aInRadius = handler.getActorsInRadius(wallLines[0].getP1(), 10f);
                    for (Actor a : aInRadius) {
                        if (this.wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            if (isNeitherStructureNorAlly(a) && a.isNotLeaping()) {
                                a.getEffectManager()
                                        .addState(
                                                ActorState.ROOTED,
                                                id + "_finn_e_root",
                                                0d,
                                                E_ROOT_DURATION);
                            }

                            if (isNeitherTowerNorAlly(a) && a.isNotLeaping()) {
                                this.wallsActivated[i] = false;
                                JsonNode spellData = this.parentExt.getAttackData("finn", "spell3");
                                double damage = handlePassive(a, getSpellDamage(spellData, true));

                                a.addToDamageQueue(this, damage, spellData, false);

                                passiveStart = System.currentTimeMillis();
                                String direction = "north";
                                if (i == 1) direction = "east";
                                else if (i == 2) direction = "south";
                                else if (i == 3) direction = "west";

                                String wallDestroyedFX =
                                        SkinData.getFinnEDestroyFX(avatar, direction);
                                String wallDestroyedSFX = SkinData.getFinnEDestroySFX(avatar);
                                ExtensionCommands.removeFx(
                                        this.parentExt,
                                        this.room,
                                        this.id + "_" + direction + "Wall");
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
        }

        if (this.ultActivated && System.currentTimeMillis() - this.eStartTime >= E_DURATION) {
            this.wallLines = null;
            this.wallsActivated = new boolean[] {false, false, false, false};
            this.ultActivated = false;
            this.finnUltRing = null;
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
        if (a.isChampion()) {
            this.canCast[1] = true;
            ExtensionCommands.actorAbilityResponse(this.parentExt, this.player, "w", true, 0, 0);
            if (getHealth() > 0) {
                ExtensionCommands.playSound(parentExt, room, id, "vo/vo_finn_assist_1", location);
            }
        }
    }

    @Override
    public void increaseStat(String key, double num) {
        super.increaseStat(key, num);
        if (key.equalsIgnoreCase("assists")) {
            this.canCast[1] = true;
            ExtensionCommands.actorAbilityResponse(this.parentExt, this.player, "w", true, 0, 0);
            if (getHealth() > 0) {
                ExtensionCommands.playSound(parentExt, room, id, "vo/vo_finn_assist_1", location);
            }
        }
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
                if (isNeitherTowerNorAlly(actor) && actor.isNotLeaping()) {
                    JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                    double damage = getSpellDamage(spellData, true);
                    actor.addToDamageQueue(Finn.this, damage, spellData, false);
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
                    basicAttackReset();
                    this.qStartTime = System.currentTimeMillis();
                    this.qActive = true;
                    String shieldFX = SkinData.getFinnQFX(avatar);
                    String shieldSFX = SkinData.getFinnQSFX(avatar);
                    ExtensionCommands.playSound(parentExt, room, id, shieldSFX, location);
                    playSoundWithChance("vo/vo_finn_q", 50);

                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            shieldFX,
                            3000,
                            id + "_shield",
                            true,
                            "Bip001 Pelvis",
                            true,
                            false,
                            team);

                    effectManager.addEffect(
                            id + "_finn_q_speed",
                            "speed",
                            Q_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            Q_SPEED_DURATION);
                    effectManager.addEffect(
                            id + "_finn_q_armor",
                            "armor",
                            Q_ARMOR_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            Q_ARMOR_DURATION);
                    effectManager.addEffect(
                            id + "_finn_q_attack_speed",
                            "attackSpeed",
                            Q_ATTACK_SPEED_PERCENT,
                            ModifierType.MULTIPLICATIVE,
                            ModifierIntent.BUFF,
                            Q_ATTACKSPEED_DURATION);

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
                RoomHandler rh = this.parentExt.getRoomHandler(this.room.getName());
                PathFinder pf = rh.getPathFinder();

                Point2D dashEndPoint = pf.getIntersectionPoint(location, dest);

                float distance = (float) location.distance(dashEndPoint);
                wDuration = (int) ((distance / DEFAULT_DASH_SPEED) * 1000);

                AbilityShape wRect =
                        AbilityShape.createRectangle(
                                location, dashEndPoint, distance, W_OFFSET_DISTANCE);

                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, location, distance)) {
                    if (isNeitherTowerNorAlly(a)
                            && wRect.contains(a.getLocation(), a.getCollisionRadius())
                            && a.isNotLeaping()) {
                        double damage = handlePassive(a, getSpellDamage(spellData, true));
                        a.addToDamageQueue(Finn.this, damage, spellData, false);
                        passiveStart = System.currentTimeMillis();
                    }
                }

                Runnable onEnd =
                        () -> {
                            doWEndAnim();
                            handleWCD();
                        };

                Runnable onInterrupt =
                        () -> {
                            doWEndAnim();
                            handleWCD();
                            playInterruptSoundAndIdle();
                        };

                DashContext ctx =
                        new DashContext.Builder(
                                        new Point2D.Double(location.getX(), location.getY()),
                                        dashEndPoint,
                                        (float) DEFAULT_DASH_SPEED)
                                .canBeRedirected(true)
                                .triggerEndEffectOnRoot(false)
                                .onEnd(onEnd)
                                .onInterrupt(onInterrupt)
                                .build();

                startDash(ctx);

                String dashFX = SkinData.getFinnWFX(avatar);
                String dashSFX = SkinData.getFinnWSFX(avatar);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        dashFX,
                        wDuration,
                        id + "finnWTrail",
                        true,
                        "",
                        true,
                        false,
                        team);
                playSoundWithChance("vo/vo_finn_w", 50);

                ExtensionCommands.playSound(parentExt, room, id, dashSFX, location);

                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dashEndPoint),
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

                    ExtensionCommands.playSound(parentExt, room, id, "vo/vo_finn_e", location);

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

    private void doWEndAnim() {
        ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
    }

    private void handleWCD() {
        int cd = ChampionData.getBaseAbilityCooldown(this, 1);
        int finalCD = getReducedCooldown(cd) - wDuration;
        Runnable allowUse = () -> canCast[1] = true;
        scheduleTask(allowUse, finalCD);
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
                            if (isNeitherStructureNorAlly(actor) && actor.isNotLeaping()) {
                                JsonNode spellData = parentExt.getAttackData("finn", "spell1");

                                double dmg = getSpellDamage(spellData, true);
                                actor.addToDamageQueue(Finn.this, dmg, spellData, false);
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
        protected void spellW() {}

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
            if (this.crit) {
                damage *= 1.25;
                damage = handleGrassSwordProc(damage);
            }
            if (target.getActorType() != ActorType.TOWER && target.getActorType() != ActorType.BASE)
                damage = handlePassive(target, damage);
            new Champion.DelayedAttack(parentExt, Finn.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

package xyz.openatbp.extension.game.bots;

import static xyz.openatbp.extension.game.champions.Finn.*;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.actors.Tower;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class FinnBot extends Bot {
    public static final double ATTACK_RANGE = 1.5;
    public static final double E_AREA = 3.5;
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

    public FinnBot(
            ATBPExtension parentExt,
            Room room,
            int botId,
            String avatar,
            String displayName,
            int team,
            BotMapConfig mapConfig) {
        super(parentExt, room, botId, avatar, displayName, team, mapConfig);

        this.qCooldownMs = 10000;
        this.wCooldownMs = 12000;
        this.eCooldownMs = 40000;

        this.qGCooldownMs = 0;
        this.wGCooldownMs = 500;
        this.eGCooldownMs = 1000;

        this.qCastDelayMS = 0;
        this.wCastDelayMS = 0;
        this.eCastDelayMS = 500;

        this.lowHpActionPHealth = 0.25;

        this.canWinUnderTowerLvDif = -2;
        this.canWinEReadyLvDif = 0;
        this.canWinQWReadyLvDif = -1;

        this.soloJungleLv = 3;
        this.soloJunglePHealth = 0.7;
        this.duoJungleLv = 2;
        this.duoJunglePHealth = 0.5;
        this.trioJunglePHeath = 0.35;
        this.closestPlayerLvDif = -2;

        this.fleeMinionsAttackedPHpPerLv = 0.05f;
        this.defAltarCaptureActionDist = 3f;
        this.playerAttackedLvDif = -2;

        this.botRole = BotRole.FIGHTER;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        handleUlt();
        handleUpdateEEnd();
        handleUpdateQEnd();
        handleUpdateFuryStacks();
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            if (canUseQ()) useQ(null);
            this.applyStopMovingDuringAttack();
            PassiveAttack passiveAttack = new PassiveAttack(a, this.handleAttack(a));
            scheduleTask(passiveAttack, BASIC_ATTACK_DELAY);
            passiveStart = System.currentTimeMillis();
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        if (a instanceof UserActor) {
            lastQUse = 0L;
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
                if (isNonStructureEnemy(actor) && actor.isNotLeaping()) {
                    JsonNode spellData = parentExt.getAttackData("finn", "spell1");
                    double damage = getSpellDamage(spellData);
                    actor.addToDamageQueue(this, damage, spellData, false);
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
    public void handleFightingAbilities() {
        if (target != null && canAttack()) {

            if (target.getActorType() == ActorType.TOWER) return;

            double distance = target.getLocation().distance(location);

            boolean targetLowHP = target.getPHealth() <= 0.05;

            if (canUseW() && distance <= 5f && isEnemyProtectedByTower(target) && targetLowHP) {
                useW(target.getLocation());
            }

            if (canUseW() && distance <= 5f && !isEnemyProtectedByTower(target)) {
                useW(target.getLocation());
            }

            if (canUseE()) useE(target.getLocation());
        }
    }

    @Override
    public void handleRetreatAbilities() {
        if (canAttack()) {
            Point2D fleePoint = getNextFleeWaypoint();
            Point2D dashPoint = Champion.createLineTowards(location, fleePoint, 5f).getP2();

            if (canUseW()) useW(dashPoint);

            if (canUseE()) useE(location);
        }
    }

    @Override
    public void useQ(Point2D destination) {
        lastQUse = System.currentTimeMillis();

        basicAttackReset();
        this.qStartTime = System.currentTimeMillis();
        this.qActive = true;
        String shieldFX = SkinData.getFinnQFX(avatar);
        String shieldSFX = SkinData.getFinnQSFX(avatar);
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, shieldSFX, this.location);
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
        effectManager.addEffect(
                this.id + "_finn_q_speed",
                "speed",
                Q_SPEED_PERCENT,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                Q_SPEED_DURATION);
        effectManager.addEffect(
                this.id + "_finn_q_armor",
                "armor",
                Q_ARMOR_PERCENT,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                Q_ARMOR_DURATION);
        effectManager.addEffect(
                this.id + "_finn_q_attack_speed",
                "attackSpeed",
                Q_ATTACK_SPEED_PERCENT,
                ModifierType.MULTIPLICATIVE,
                ModifierIntent.BUFF,
                Q_ATTACKSPEED_DURATION);
    }

    @Override
    public void useW(Point2D destination) {
        globalCooldown += wGCooldownMs;

        lastWUse = System.currentTimeMillis();

        RoomHandler rh = this.parentExt.getRoomHandler(this.room.getName());
        PathFinder pf = rh.getPathFinder();

        Point2D dashEndPoint = pf.getIntersectionPoint(location, destination);

        float distance = (float) location.distance(dashEndPoint);
        int wDuration = (int) ((distance / DEFAULT_DASH_SPEED) * 1000);

        DashContext ctx =
                new DashContext.Builder(
                                new Point2D.Double(location.getX(), location.getY()),
                                dashEndPoint,
                                (float) DEFAULT_DASH_SPEED)
                        .canBeRedirected(false)
                        .triggerEndEffectOnRoot(false)
                        .onEnd(this::doWEndAnim)
                        .onInterrupt(this::doWEndAnim)
                        .build();

        startDash(ctx);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", wDuration, true);

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
        ExtensionCommands.playSound(parentExt, room, id, dashSFX, location);

        JsonNode spellData = parentExt.getAttackData(avatar, "spell2");

        AbilityShape wRect =
                AbilityShape.createRectangle(location, dashEndPoint, distance, W_OFFSET_DISTANCE);

        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        for (Actor a : Champion.getActorsInRadius(handler, location, distance)) {
            if (a.getTeam() != this.team
                    && a.getActorType() != ActorType.TOWER
                    && wRect.contains(a.getLocation(), a.getCollisionRadius())
                    && a.isNotLeaping()) {
                double damage = handlePassive(a, getSpellDamage(spellData));
                a.addToDamageQueue(this, damage, spellData, false);
                passiveStart = System.currentTimeMillis();
            }
        }
    }

    private void doWEndAnim() {
        ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 100, false);
    }

    @Override
    public void useE(Point2D destination) {
        globalCooldown += eGCooldownMs;
        lastEUse = System.currentTimeMillis();

        this.isCastingUlt = true;
        this.stopMoving(E_SELF_CRIPPLE_DURATION);
        Runnable enableDashCasting = () -> this.isCastingUlt = false;
        scheduleTask(enableDashCasting, E_SELF_CRIPPLE_DURATION);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell3", eCastDelayMS, true);

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
                            this.parentExt, this.room, this.id, wallDropSFX, this.location);
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
                    this.wallLines = new Line2D[] {northWall, eastWall, southWall, westWall};
                    this.wallsActivated = new boolean[] {true, true, true, true};
                };
        scheduleTask(cast, eCastDelayMS);
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
                            if (isNonStructureEnemy(actor) && actor.isNotLeaping()) {
                                JsonNode spellData = parentExt.getAttackData("finn", "spell1");

                                double dmg = getSpellDamage(spellData);
                                actor.addToDamageQueue(this, dmg, spellData, false);
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
                if (furyTarget != null) {
                    ExtensionCommands.removeFx(
                            parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                }
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

    @Override
    public boolean canUseQ() {
        return defaultAbilityCheck(1) && !isCastingUlt;
    }

    @Override
    public boolean canUseW() {
        if (target != null
                && target.getLocation().distance(location) <= 5
                && !(target instanceof Tower)) {
            return defaultAbilityCheck(2) && !isCastingUlt;
        }
        return false;
    }

    @Override
    public boolean canUseE() {
        if (defaultAbilityCheck(3) && target != null && !(target instanceof Tower)) {

            // DEFENSIVE ULT
            if (lastPlayerAttacker != null) {
                double distance = lastPlayerAttacker.getLocation().distance(location);
                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                List<Actor> enemiesInArea = Champion.getEnemyActorsInRadius(rh, team, location, 3);
                if (getPHealth() <= 0.2
                        && distance >= E_AREA
                        && distance < 6
                        && enemiesInArea.isEmpty()) return true;
            }

            // OFFENSIVE ULT
            double distance = location.distance(target.getLocation());
            return distance <= E_AREA && distance > ATTACK_RANGE && target.getPHealth() <= 0.4;
        }
        return false;
    }

    @Override
    public void levelUpStats() {
        switch (level) {
            case 1:
                setStat("attackDamage", 55);
                setStat("armor", 16);
                setStat("attackSpeed", 1450);
                setStat("spellDamage", 17);
                setStat("spellResist", 16);
                setStat("healthRegen", 3);
                setHealth(getHealth(), 460);
                break;
            case 2:
                setStat("attackDamage", 60);
                setStat("armor", 17);
                setStat("attackSpeed", 1400);
                setStat("spellDamage", 19);
                setStat("spellResist", 17);
                setHealth(getHealth(), 495);
                setStat("healthRegen", 4);
                break;
            case 3:
                setStat("attackDamage", 65);
                setStat("armor", 18);
                setStat("attackSpeed", 1350);
                setStat("spellDamage", 21);
                setStat("spellResist", 18);
                setHealth(getHealth(), 530);
                setStat("healthRegen", 5);
                break;
            case 4:
                setStat("attackDamage", 70);
                setStat("armor", 19);
                setStat("attackSpeed", 1300);
                setStat("spellDamage", 23);
                setStat("spellResist", 19);
                setHealth(getHealth(), 565);
                setStat("healthRegen", 6);
                break;
            case 5:
                setStat("attackDamage", 75);
                setStat("armor", 20);
                setStat("attackSpeed", 1250);
                setStat("spellDamage", 25);
                setStat("spellResist", 20);
                setHealth(getHealth(), 600);
                setStat("healthRegen", 7);
                break;
            case 6:
                setStat("attackDamage", 80);
                setStat("armor", 21);
                setStat("attackSpeed", 1200);
                setStat("spellDamage", 27);
                setStat("spellResist", 21);
                setHealth(getHealth(), 635);
                setStat("healthRegen", 8);
                break;
            case 7:
                setStat("attackDamage", 85);
                setStat("armor", 22);
                setStat("attackSpeed", 1150);
                setStat("spellDamage", 29);
                setStat("spellResist", 22);
                setHealth(getHealth(), 670);
                setStat("healthRegen", 9);
                break;
            case 8:
                setStat("attackDamage", 90);
                setStat("armor", 23);
                setStat("attackSpeed", 1100);
                setStat("spellDamage", 31);
                setStat("spellResist", 23);
                setHealth(getHealth(), 705);
                setStat("healthRegen", 10);
                break;
            case 9:
                setStat("attackDamage", 95);
                setStat("armor", 24);
                setStat("attackSpeed", 1050);
                setStat("spellDamage", 33);
                setStat("spellResist", 24);
                setHealth(getHealth(), 740);
                setStat("healthRegen", 11);
                break;
            case 10:
                setStat("attackDamage", 100);
                setStat("armor", 25);
                setStat("attackSpeed", 1000);
                setStat("spellDamage", 35);
                setStat("spellResist", 25);
                setHealth(getHealth(), 775);
                setStat("healthRegen", 12);
                break;
        }
    }

    private void handleUpdateEEnd() {
        if (this.ultActivated && System.currentTimeMillis() - this.eStartTime >= E_DURATION) {
            this.wallLines = null;
            this.wallsActivated = new boolean[] {false, false, false, false};
            this.ultActivated = false;
            this.finnUltRing = null;
        }
    }

    private void handleUpdateQEnd() {
        if (this.qActive && this.qStartTime + 3000 <= System.currentTimeMillis()) {
            this.qActive = false;
        }
    }

    private void handleUlt() {
        if (this.ultActivated && wallLines != null) {
            for (int i = 0; i < this.wallLines.length; i++) {
                if (this.wallsActivated[i]) {
                    RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
                    List<Actor> aInRadius = handler.getActorsInRadius(wallLines[0].getP1(), 10f);
                    for (Actor a : aInRadius) {
                        if (this.wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            if (isNonStructureEnemy(a) && a.isNotLeaping()) {
                                a.getEffectManager()
                                        .addState(
                                                ActorState.ROOTED,
                                                id + "_finn_e_root",
                                                0d,
                                                E_ROOT_DURATION);
                            }

                            if (isNonStructureEnemy(a) && a.isNotLeaping()) {
                                this.wallsActivated[i] = false;
                                JsonNode spellData = this.parentExt.getAttackData("finn", "spell3");
                                double damage = handlePassive(a, getSpellDamage(spellData));

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
    }

    private void handleUpdateFuryStacks() {
        if (furyStacks > 0) {
            if (System.currentTimeMillis() - passiveStart >= PASSIVE_DURATION) {
                if (furyTarget != null) {
                    ExtensionCommands.removeFx(
                            parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                }
                furyStacks = 0;
            }
            if (furyTarget != null && furyTarget.getHealth() <= 0) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
        }
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
            }
            if (target.getActorType() != ActorType.TOWER && target.getActorType() != ActorType.BASE)
                damage = handlePassive(target, damage);
            new Champion.DelayedAttack(parentExt, FinnBot.this, target, (int) damage, "basicAttack")
                    .run();
        }
    }
}

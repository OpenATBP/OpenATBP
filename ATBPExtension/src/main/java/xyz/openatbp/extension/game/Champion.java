package xyz.openatbp.extension.game;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.champions.*;

public class Champion {
    public static void updateServerHealth(ATBPExtension parentExt, Actor a) {
        ISFSObject data = new SFSObject();
        data.putUtfString("id", a.getId());
        data.putInt("maxHealth", a.getMaxHealth());
        data.putInt("currentHealth", a.getHealth());
        data.putDouble("pHealth", a.getPHealth());
        ExtensionCommands.updateActorData(parentExt, a.getRoom(), data);
    }

    public static UserActor getCharacterClass(User u, ATBPExtension parentExt) {
        String avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        String character = avatar.split("_")[0];
        switch (character) {
            case "flame":
                return new FlamePrincess(u, parentExt);
            case "lich":
                return new Lich(u, parentExt);
            case "gunter":
                return new Gunter(u, parentExt);
            case "princessbubblegum":
                return new BubbleGum(u, parentExt);
            case "fionna":
                return new Fionna(u, parentExt);
            case "marceline":
                return new Marceline(u, parentExt);
            case "lemongrab":
                return new Lemongrab(u, parentExt);
            case "peppermintbutler":
                return new PeppermintButler(u, parentExt);
            case "iceking":
                return new IceKing(u, parentExt);
            case "finn":
                return new Finn(u, parentExt);
            case "jake":
                return new Jake(u, parentExt);
            case "rattleballs":
                return new RattleBalls(u, parentExt);
            case "neptr":
                return new Neptr(u, parentExt);
            case "lsp":
                return new LSP(u, parentExt);
            case "billy":
                return new Billy(u, parentExt);
            case "hunson":
                return new Hunson(u, parentExt);
            case "magicman":
                return new MagicMan(u, parentExt);
            case "bmo":
                return new BMO(u, parentExt);
            case "cinnamonbun":
                return new CinnamonBun(u, parentExt);
        }
        return new UserActor(u, parentExt);
    }

    public static JsonNode getSpellData(ATBPExtension parentExt, String avatar, int spell) {
        JsonNode actorDef = parentExt.getDefinition(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell" + spell);
    }

    @Deprecated
    public static Point2D getIntersectionPoint(
            Line2D line, Line2D line2) { // Finds the intersection of two lines
        float slope1 =
                (float)
                        ((line.getP2().getY() - line.getP1().getY())
                                / (line.getP2().getX() - line.getP1().getX()));
        float slope2 =
                (float)
                        ((line2.getP2().getY() - line2.getP1().getY())
                                / (line2.getP2().getX() - line2.getP1().getX()));
        float intercept1 = (float) (line.getP2().getY() - (slope1 * line.getP2().getX()));
        float intercept2 = (float) (line2.getP2().getY() - (slope2 * line2.getP2().getX()));
        float x = (intercept2 - intercept1) / (slope1 - slope2);
        float y = slope1 * ((intercept2 - intercept1) / (slope1 - slope2)) + intercept1;
        if (Float.isNaN(x) || Float.isNaN(y)) return line.getP1();
        return new Point2D.Float(x, y);
    }

    public static Point2D calculatePolygonPoint(
            Point2D originalPoint, float distance, double angle) {
        float x = (float) (originalPoint.getX() + distance * Math.cos(angle));
        float y = (float) (originalPoint.getY() + distance * Math.sin(angle));
        return new Point2D.Float(x, y);
    }

    public static Path2D createTrapezoid(
            Point2D location,
            Point2D destination,
            float spellRange,
            float offsetDistanceBottom,
            float offsetDistanceTop) {
        Line2D abilityLine = Champion.getAbilityLine(location, destination, spellRange);
        double angle =
                Math.atan2(
                        abilityLine.getY2() - abilityLine.getY1(),
                        abilityLine.getX2() - abilityLine.getX1());
        int PERPENDICULAR_ANGLE = 90;
        Point2D startPoint1 =
                calculatePolygonPoint(
                        abilityLine.getP1(),
                        offsetDistanceBottom,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D startPoint2 =
                calculatePolygonPoint(
                        abilityLine.getP1(),
                        offsetDistanceBottom,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D endPoint1 =
                calculatePolygonPoint(
                        abilityLine.getP2(),
                        offsetDistanceTop,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D endPoint2 =
                calculatePolygonPoint(
                        abilityLine.getP2(),
                        offsetDistanceTop,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));

        Path2D.Float trapezoid = new Path2D.Float();
        trapezoid.moveTo(startPoint1.getX(), startPoint1.getY());
        trapezoid.lineTo(endPoint1.getX(), endPoint1.getY());
        trapezoid.lineTo(endPoint2.getX(), endPoint2.getY());
        trapezoid.lineTo(startPoint2.getX(), startPoint2.getY());
        return trapezoid;
    }

    public static Path2D createRectangle(
            Point2D location, Point2D destination, float spellRange, float offsetDistance) {
        Line2D abilityLine = getAbilityLine(location, destination, spellRange);
        double angle =
                Math.atan2(
                        abilityLine.getY2() - abilityLine.getY1(),
                        abilityLine.getX2() - abilityLine.getX1());
        int PERPENDICULAR_ANGLE = 90;
        Point2D startPoint1 =
                calculatePolygonPoint(
                        abilityLine.getP1(),
                        offsetDistance,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D startPoint2 =
                calculatePolygonPoint(
                        abilityLine.getP1(),
                        offsetDistance,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D endPoint1 =
                calculatePolygonPoint(
                        abilityLine.getP2(),
                        offsetDistance,
                        angle + Math.toRadians(PERPENDICULAR_ANGLE));
        Point2D endPoint2 =
                calculatePolygonPoint(
                        abilityLine.getP2(),
                        offsetDistance,
                        angle - Math.toRadians(PERPENDICULAR_ANGLE));

        Path2D.Float rectangle = new Path2D.Float();
        rectangle.moveTo(startPoint1.getX(), startPoint1.getY());
        rectangle.lineTo(endPoint1.getX(), endPoint1.getY());
        rectangle.lineTo(endPoint2.getX(), endPoint2.getY());
        rectangle.lineTo(startPoint2.getX(), startPoint2.getY());
        return rectangle;
    }

    @Deprecated
    public static Point2D[] findAllPoints(Line2D line) { // Finds all points within a line
        int arrayLength =
                (int) (line.getP1().distance(line.getP2()))
                        * 30; // Longer movement have more precision when checking collisions
        if (arrayLength < 8) arrayLength = 8;
        Point2D[] points = new Point2D[arrayLength];
        float slope =
                (float)
                        ((line.getP2().getY() - line.getP1().getY())
                                / (line.getP2().getX() - line.getP1().getX()));
        float intercept = (float) (line.getP2().getY() - (slope * line.getP2().getX()));
        float distance = (float) (line.getX2() - line.getX1());
        int pValue = 0;
        for (int i = 0; i < points.length; i++) { // Finds the points on the line based on distance
            float x = (float) line.getP1().getX() + ((distance / points.length) * i);
            float y = slope * x + intercept;
            Point2D point = new Point2D.Float(x, y);
            points[pValue] = point;
            pValue++;
        }
        return points;
    }

    public static List<UserActor> getUserActorsInRadius(
            RoomHandler room, Point2D center, float radius) {
        List<UserActor> players = room.getPlayers();
        List<UserActor> playersInRadius = new ArrayList<>();
        for (UserActor ua : players) {
            Point2D location = ua.getLocation();
            if (location.distance(center) <= radius) playersInRadius.add(ua);
        }
        return playersInRadius;
    }

    public static List<Actor> getActorsInRadius(RoomHandler room, Point2D center, float radius) {
        List<Actor> actors = room.getActors();
        List<Actor> affectedActors = new ArrayList<>(actors.size());
        for (Actor a : actors) {
            Point2D location = a.getLocation();
            if (location.distance(center) <= radius) affectedActors.add(a);
        }
        return affectedActors;
    }

    public static List<Actor> getEnemyActorsInRadius(
            RoomHandler room, int team, Point2D center, float radius) {
        List<Actor> actors = room.getActors();
        List<Actor> affectedActors = new ArrayList<>(actors.size());
        Ellipse2D circle =
                new Ellipse2D.Double(
                        center.getX() - radius, center.getY() - radius, radius * 2, radius * 2);
        for (Actor a : actors) {
            if (a.getTeam() != team && a.getHealth() > 0) {
                Point2D location = a.getLocation();
                if (circle.contains(location)) affectedActors.add(a);
            }
        }
        return affectedActors;
    }

    public static List<Actor> getActorsAlongLine(RoomHandler room, Line2D line, double range) {
        Point2D[] allPoints = findAllPoints(line);
        List<Actor> affectedActors = new ArrayList<>();
        for (Actor a : room.getActors()) {
            for (Point2D p : allPoints) {
                if (a.getLocation().distance(p) <= range
                        && (facingEntity(line, a.getLocation()) || line.getX1() == line.getX2())) {
                    affectedActors.add(a);
                    break;
                }
            }
        }
        return affectedActors;
    }

    private static boolean facingEntity(
            Line2D movementLine,
            Point2D testPoint) { // Returns true if the point is in the same direction
        double deltaX = movementLine.getX2() - movementLine.getX1();
        double pointDelta = testPoint.getX() - movementLine.getX1();
        // Negative = left Positive = right
        if (Double.isNaN(deltaX)) return false;
        return (deltaX > 0 && pointDelta > 0) || (deltaX < 0 && pointDelta < 0);
    }

    public static Line2D getMaxRangeLine(Line2D projectileLine, float spellRange) {
        float remainingRange =
                (float) (spellRange - projectileLine.getP1().distance(projectileLine.getP2()));
        if (projectileLine.getP1().distance(projectileLine.getP2()) >= spellRange - 0.01)
            return projectileLine;
        float slope =
                (float)
                        ((projectileLine.getP2().getY() - projectileLine.getP1().getY())
                                / (projectileLine.getP2().getX() - projectileLine.getP1().getX()));
        float intercept =
                (float) (projectileLine.getP2().getY() - (slope * projectileLine.getP2().getX()));
        float deltaX = (float) (projectileLine.getX2() - projectileLine.getX1());
        float x = (float) projectileLine.getP2().getX() + (remainingRange);
        if (deltaX < 0) x = (float) projectileLine.getX2() - remainingRange;
        float y = slope * x + intercept;
        Point2D newPoint = new Point2D.Float(x, y);
        return new Line2D.Float(projectileLine.getP1(), newPoint);
    }

    public static Line2D extendLine(Line2D projectileLine, float distance) {
        double angle =
                Math.atan2(
                        projectileLine.getY2() - projectileLine.getY1(),
                        projectileLine.getX2() - projectileLine.getX1());
        double extendedX = projectileLine.getX2() + distance * Math.cos(angle);
        double extendedY = projectileLine.getY2() + distance * Math.sin(angle);
        return new Line2D.Double(projectileLine.getP1(), new Point2D.Double(extendedX, extendedY));
    }

    public static Line2D getDistanceLine(Line2D movementLine, float distance) {
        float slope =
                (float)
                        ((movementLine.getP2().getY() - movementLine.getP1().getY())
                                / (movementLine.getP2().getX() - movementLine.getP1().getX()));
        float intercept =
                (float) (movementLine.getP2().getY() - (slope * movementLine.getP2().getX()));
        float deltaX = (float) (movementLine.getX2() - movementLine.getX1());
        float x = -1;
        if (distance > 0) {
            x = (float) movementLine.getP1().getX() + (distance);
            if (deltaX < 0) x = (float) movementLine.getX1() - distance;
        } else if (distance < 0) {
            x = (float) movementLine.getX2() + distance;
            if (deltaX < 0) x = (float) movementLine.getX2() - distance;
        }
        float y = slope * x + intercept;
        Point2D newPoint = new Point2D.Float(x, y);
        return new Line2D.Float(movementLine.getP1(), newPoint);
    }

    public static Line2D getAbilityLine(Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        return new Line2D.Double(location, lineEndPoint);
    }

    public static HashMap<ActorState, Boolean> getBlankStates() {
        HashMap<ActorState, Boolean> states = new HashMap<>(ActorState.values().length);
        for (ActorState s : ActorState.values()) {
            states.put(s, false);
        }
        return states;
    }

    public static void handleStatusIcon(
            ATBPExtension parentExt,
            UserActor player,
            String icon,
            String iconDesc,
            float duration) {
        String iconName = icon + player.getId() + Math.random();
        Runnable endIcon =
                () -> {
                    ExtensionCommands.removeStatusIcon(parentExt, player.getUser(), iconName);
                    player.removeIconHandler(iconName);
                };
        ExtensionCommands.addStatusIcon(
                parentExt, player.getUser(), iconName, iconDesc, icon, duration);
        player.addIconHandler(
                iconName,
                parentExt
                        .getTaskScheduler()
                        .schedule(endIcon, (int) duration, TimeUnit.MILLISECONDS));
    }

    public static class DelayedAttack implements Runnable {

        Actor attacker;
        Actor target;
        int damage;
        ATBPExtension parentExt;
        String attack;
        boolean crit;

        public DelayedAttack(
                ATBPExtension parentExt, Actor attacker, Actor target, int damage, String attack) {
            this.attacker = attacker;
            this.target = target;
            this.damage = damage;
            this.parentExt = parentExt;
            this.attack = attack;
        }

        @Override
        public void run() {
            if (this.target.isDead()) {
                if (this.attacker.getActorType() == ActorType.MINION) attacker.setCanMove(true);
                return;
            }
            if (this.attacker.getState(ActorState.BLINDED) || this.attacker.isDead()) {
                if (this.attacker.getActorType() == ActorType.PLAYER)
                    ExtensionCommands.playSound(
                            parentExt,
                            attacker.getRoom(),
                            attacker.getId(),
                            "sfx/sfx_attack_miss",
                            attacker.getLocation());
                if (this.attacker.getActorType() == ActorType.MONSTER
                        && !this.attacker.getId().contains("gnome")) {
                    attacker.setCanMove(true);
                }
                return;
            }
            ExtensionCommands.playSound(
                    this.parentExt,
                    this.attacker.getRoom(),
                    this.target.getId(),
                    "sfx_generic_hit",
                    this.target.getLocation());
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.attacker.getRoom(),
                    this.target.getId(),
                    "_playerGotHitSparks",
                    500,
                    this.target.getId() + "_hit" + Math.random(),
                    true,
                    "",
                    true,
                    false,
                    target.getTeam());
            JsonNode attackData;
            if (this.attacker.getActorType() == ActorType.MINION) {
                attackData =
                        this.parentExt.getAttackData(
                                this.attacker.getAvatar().replace("0", ""), this.attack);
                this.attacker.setCanMove(true);
            } else {
                switch (this.attack) { // to make Rattleballs counter-attack possible
                    case "turretAttack":
                        attackData = this.parentExt.getAttackData("princessbubblegum", "spell2");
                        ((ObjectNode) attackData).remove("spellType");
                        ((ObjectNode) attackData).put("attackType", "physical");
                        break;
                    case "skullyAttack":
                        attackData = this.parentExt.getAttackData("lich", "spell4");
                        ((ObjectNode) attackData).remove("spellType");
                        ((ObjectNode) attackData).put("attackType", "physical");
                        break;
                    default:
                        attackData =
                                this.parentExt.getAttackData(
                                        this.attacker.getAvatar(), this.attack);
                }
            }
            if (this.attacker.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) this.attacker;
                if (ua.hasBackpackItem("junk_1_numb_chucks") && ua.getStat("sp_category1") > 0) {
                    if (!this.target.hasTempStat(
                            "attackSpeed")) // TODO: This will make this not apply when people are
                        // impacted by other attackSpeed debuffs/buffs
                        this.target.addEffect(
                                "attackSpeed",
                                this.target.getPlayerStat("attackSpeed") * -0.25,
                                3000);
                } else if (ua.hasBackpackItem("junk_4_grob_gob_glob_grod")
                        && ua.getStat("sp_category4") > 0) {
                    if (!this.target.hasTempStat("spellDamage"))
                        this.target.addEffect(
                                "spellDamage",
                                this.target.getPlayerStat("spellDamage") * -0.1,
                                3000);
                }
                if (this.attack.contains("basic")
                        && this.target != null
                        && this.target.getActorType() != ActorType.TOWER
                        && this.target.getActorType() != ActorType.BASE) ua.handleLifeSteal();
            }
            /*
            if(this.target.getActorType() == ActorType.PLAYER){
                UserActor user = (UserActor) this.target;
                if(user.damaged(attacker,damage,attackData) && this.attacker.getActorType() == ActorType.TOWER){
                    Tower t = (Tower) attacker;
                    t.resetTarget(target);
                }
            }
            else if(target.damaged(attacker,damage,attackData) && this.attacker.getActorType() == ActorType.TOWER){
                Tower t = (Tower) attacker;
                t.resetTarget(target);
            }

             */
            if (attacker.getActorType() == ActorType.MONSTER && !attacker.getId().contains("gnome"))
                attacker.setCanMove(true);
            this.target.addToDamageQueue(this.attacker, this.damage, attackData, false);
        }
    }

    public static class DelayedRangedAttack implements Runnable {
        Actor attacker;
        Actor target;

        public DelayedRangedAttack(Actor a, Actor t) {
            this.attacker = a;
            this.target = t;
        }

        @Override
        public void run() {
            attacker.rangedAttack(target);
            attacker.setCanMove(true);
        }
    }

    public static class RespawnCharacter implements Runnable {

        UserActor deadActor;

        public RespawnCharacter(UserActor a) {
            this.deadActor = a;
        }

        @Override
        public void run() {
            deadActor.respawn();
        }
    }
}

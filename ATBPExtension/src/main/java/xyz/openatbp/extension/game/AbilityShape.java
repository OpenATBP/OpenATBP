package xyz.openatbp.extension.game;

import static xyz.openatbp.extension.game.Champion.getAbilityLine;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

public class AbilityShape {
    public final Path2D path;
    public final Point2D[] vertices;
    private static final int PERPENDICULAR_ANGLE = 90;
    private static final int TEST_DISPLAY_DURATION = 3000;

    public AbilityShape(Path2D path, Point2D[] vertices) {
        this.path = path;
        this.vertices = vertices;
    }

    public boolean contains(Point2D point, double collisionRadius) {
        double px = point.getX();
        double py = point.getY();

        // Center is inside the shape?
        if (path.contains(px, py)) {
            return true;
        }

        // Circle overlaps any edge?
        double radiusSq = collisionRadius * collisionRadius;
        for (int i = 0; i < vertices.length; i++) {
            int j = (i + 1) % vertices.length;
            double distSq =
                    pointToSegmentDistanceSq(
                            px,
                            py,
                            vertices[i].getX(),
                            vertices[i].getY(),
                            vertices[j].getX(),
                            vertices[j].getY());
            if (distSq <= radiusSq) return true;
        }
        return false;
    }

    private static double pointToSegmentDistanceSq(
            double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;

        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSq));

        double closestX = ax + t * dx;
        double closestY = ay + t * dy;

        double ex = px - closestX;
        double ey = py - closestY;
        return ex * ex + ey * ey;
    }

    public static AbilityShape createRectangle(
            Point2D start, Point2D end, float spellRange, float offsetDistance) {
        Line2D abilityLine = getAbilityLine(start, end, spellRange);
        double angle =
                Math.atan2(
                        abilityLine.getY2() - abilityLine.getY1(),
                        abilityLine.getX2() - abilityLine.getX1());
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

        Point2D[] vertices = {startPoint1, endPoint1, endPoint2, startPoint2};
        return new AbilityShape(rectangle, vertices);
    }

    public static AbilityShape createTrapezoid(
            Point2D start,
            Point2D end,
            float spellRange,
            float offsetDistanceBottom,
            float offsetDistanceTop) {
        Line2D abilityLine = Champion.getAbilityLine(start, end, spellRange);
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

        Point2D[] vertices = {startPoint1, endPoint1, endPoint2, startPoint2};
        return new AbilityShape(trapezoid, vertices);
    }

    public static Point2D calculatePolygonPoint(
            Point2D originalPoint, float distance, double angle) {
        float x = (float) (originalPoint.getX() + distance * Math.cos(angle));
        float y = (float) (originalPoint.getY() + distance * Math.sin(angle));
        return new Point2D.Float(x, y);
    }

    public void displayVertices(ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D vertex : this.vertices) {
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "skully",
                    id + "testing" + Math.random(),
                    TEST_DISPLAY_DURATION,
                    (float) vertex.getX(),
                    (float) vertex.getY(),
                    false,
                    team,
                    0f);
        }
    }
}

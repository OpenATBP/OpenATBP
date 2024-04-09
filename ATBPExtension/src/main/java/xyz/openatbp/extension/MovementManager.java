package xyz.openatbp.extension;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import com.dongbat.walkable.FloatArray;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Obstacle;
import xyz.openatbp.extension.game.actors.Actor;

public class MovementManager {

    public static boolean playerIntersectsWithCollider(Point2D player, Path2D collider) {
        Rectangle2D playerBoundingBox =
                new Rectangle2D.Double(player.getX(), player.getY(), 0.5d, 0.5d);
        return collider.intersects(playerBoundingBox);
    }

    public static Point2D getStoppingPoint(
            Point2D location, Point2D destination, double stoppingDistance) {
        Line2D movementLine = new Line2D.Double(location, destination);
        Point2D[] allPoints = findAllPoints(movementLine);
        for (Point2D p : allPoints) {
            if (p.distance(destination) <= stoppingDistance) return p;
        }
        return null;
    }

    public static boolean playerIntersectsWithCollider(
            Point2D player, List<Vector<Float>> collider) {
        Rectangle2D playerBoundingBox =
                new Rectangle2D.Double(player.getX(), player.getY(), 0.5d, 0.5d);
        Path2D path = new Path2D.Float();
        for (int i = 0; i < collider.size(); i++) {
            Vector<Float> c = collider.get(i);
            if (i == 0) path.moveTo(c.get(0), c.get(1));
            else path.lineTo(c.get(0), c.get(1));
        }
        path.closePath();
        return path.intersects(playerBoundingBox) || path.contains(player);
    }

    public static List<Vector<Float>> getCollidingVectors(
            Line2D movementLine, ATBPExtension parentExt, Room room) {
        ArrayList<Vector<Float>>[] colliders =
                parentExt.getColliders(room.getGroupId()); // Gets all collision object vertices
        ArrayList<Path2D> mapPaths =
                parentExt.getMapPaths(
                        room.getGroupId()); // Gets all created paths for the collision objects
        for (int i = 0; i < mapPaths.size(); i++) { // Search through all colliders
            if (mapPaths.get(i)
                    .intersects(
                            movementLine.getBounds())) { // If the player's movement intersects a
                // collider
                return colliders[i];
            }
        }
        return null;
    }

    public static List<Line2D> getColliderVectorLines(List<Vector<Float>> collider) {
        List<Line2D> colliderLines = new ArrayList<>();
        if (collider == null) return colliderLines;
        for (int g = 0; g < collider.size(); g++) { // Check all vertices in the collider
            Vector<Float> v = collider.get(g);
            Vector<Float> v2;
            if (g + 1 == collider.size()) { // If it's the final vertex, loop to the beginning
                v2 = collider.get(0);
            } else {
                v2 = collider.get(g + 1);
            }
            Line2D colliderLine =
                    new Line2D.Float(
                            v.get(0), v.get(1), v2.get(0),
                            v2.get(1)); // Draws a line segment for the sides of the collider
            colliderLines.add(colliderLine);
        }
        return colliderLines;
    }

    public static Point2D getDashPoint(Actor player, Line2D movementLine) {
        if (!player.getParentExt().getRoomHandler(player.getRoom().getId()).isPracticeMap()) {
            if (Math.abs(movementLine.getP2().getX()) >= 51 || Math.abs(movementLine.getY2()) >= 31)
                return movementLine.getP1();
        } else {
            if (Math.abs(movementLine.getP2().getX()) >= 61 || Math.abs(movementLine.getY2()) >= 31)
                return movementLine.getP1();
        }
        List<Vector<Float>> collider =
                getCollidingVectors(movementLine, player.getParentExt(), player.getRoom());
        if (collider != null) {
            List<Line2D> vectorLines = getColliderVectorLines(collider);
            Line2D closestLine =
                    findClosestLine(
                            new Line2D.Float(movementLine.getP2(), movementLine.getP1()),
                            vectorLines);
            if (closestLine == null) return movementLine.getP2();
            if (closestLine.getP1().distance(player.getLocation()) > 5
                    && (player.getAvatar().contains("finn")
                            || player.getAvatar().contains("flame")))
                closestLine = findClosestLine(movementLine, vectorLines);
            if (collider.size() < 25
                    && !playerIntersectsWithCollider(movementLine.getP2(), collider)) {
                return movementLine.getP2();
            }
            Line2D checkLine = extendLine(movementLine, 10f);
            Point2D intPoint = getIntersectionPoint(checkLine, closestLine);
            if (intPoint != null) {
                if (collider.size() > 25) {
                    Point2D wallPoint = getPathIntersectionPoint(movementLine, collider);
                    if (wallPoint != null) return wallPoint;
                    else return movementLine.getP2();
                }
                Point2D[] movementPoints = findAllPoints(checkLine);
                Point2D[] colliderPoints = findAllPoints(closestLine);
                if (colliderPoints.length > 600) {
                    return movementLine.getP1();
                }
                for (int i = 0; i < movementPoints.length; i++) {
                    // ExtensionCommands.createWorldFX(player.getParentExt(),
                    // player.getUser(),"t","gnome_c","gnome2"+Math.random(),4000,(float)movementPoints[i].getX(),(float)movementPoints[i].getY(),false,0,0f);
                    for (Point2D p : colliderPoints) {
                        Point2D currentPoint = movementPoints[i];
                        if (currentPoint.distance(p) <= 0.6d) {
                            if (i == 0) return currentPoint;
                            else if (!playerIntersectsWithCollider(
                                    movementPoints[i - 1], collider)) {
                                return movementPoints[i - 1];
                            }
                        }
                    }
                }
                return getPathIntersectionPoint(movementLine, collider);
            }
        }
        return movementLine.getP2();
    }

    public static Point2D getRelativePoint(
            Line2D movementLine,
            double speed,
            double timeTraveled) { // Gets player's current location based on time
        double currentTime = timeTraveled;
        Point2D rPoint = new Point2D.Float();
        float x2 = (float) movementLine.getX2();
        float y2 = (float) movementLine.getY2();
        float x1 = (float) movementLine.getX1();
        float y1 = (float) movementLine.getY1();
        double dist = movementLine.getP1().distance(movementLine.getP2());
        if (dist == 0) return movementLine.getP1();
        double time = dist / speed;
        if (currentTime > time) currentTime = time;
        double currentDist = speed * currentTime;
        float x = (float) (x1 + (currentDist / dist) * (x2 - x1));
        float y = (float) (y1 + (currentDist / dist) * (y2 - y1));
        rPoint.setLocation(x, y);
        return rPoint;
    }

    public static Point2D lerp(Line2D movementLine, double dist) {
        float slope =
                (float)
                        ((movementLine.getP2().getY() - movementLine.getP1().getY())
                                / (movementLine.getP2().getX() - movementLine.getP1().getX()));
        float intercept =
                (float) (movementLine.getP2().getY() - (slope * movementLine.getP2().getX()));
        float deltaX = (float) (movementLine.getX2() - movementLine.getX1());
        float distance = (float) (movementLine.getP1().distance(movementLine.getP2()) * dist);
        float x = -1;
        if (distance > 0) {
            x = (float) movementLine.getP1().getX() + (distance);
            if (deltaX < 0) x = (float) movementLine.getX1() - distance;
        } else if (distance < 0) {
            x = (float) movementLine.getX2() + distance;
            if (deltaX < 0) x = (float) movementLine.getX2() - distance;
        }
        float y = slope * x + intercept;
        return new Point2D.Float(x, y);
    }

    public static Point2D findPullPoint(Line2D movementLine, float dist) {
        float distance = (float) (movementLine.getP1().distance(movementLine.getP2()));
        if (dist >= distance) return movementLine.getP2();
        else return Champion.getDistanceLine(movementLine, dist).getP2();
    }

    public static Line2D getColliderLine(ATBPExtension parentExt, Room room, Line2D movementLine) {
        Point2D dest =
                getPathIntersectionPoint(
                        movementLine, getCollidingVectors(movementLine, parentExt, room));
        if (dest != null) return new Line2D.Float(movementLine.getP1(), dest);
        else return movementLine;
    }

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

    public static Line2D extendLine(Line2D projectileLine, float distance) {
        double angle =
                Math.atan2(
                        projectileLine.getY2() - projectileLine.getY1(),
                        projectileLine.getX2() - projectileLine.getX1());
        double extendedX = projectileLine.getX2() + distance * Math.cos(angle);
        double extendedY = projectileLine.getY2() + distance * Math.sin(angle);
        return new Line2D.Double(projectileLine.getP1(), new Point2D.Double(extendedX, extendedY));
    }

    public static Line2D findClosestLine(Line2D movementLine, List<Line2D> lines) {
        Line2D closestLine = null;
        double closestLineDist = 1000;
        for (Line2D line : lines) {
            if (line.getP1().distance(movementLine.getP1()) < closestLineDist
                    && (movementLine.intersectsLine(line)
                            || extendLine(
                                            new Line2D.Float(
                                                    movementLine.getP2(), movementLine.getP1()),
                                            10f)
                                    .intersectsLine(line))) {
                closestLineDist = line.getP1().distance(movementLine.getP1());
                closestLine = line;
            }
        }
        return closestLine;
    }

    public static boolean insideAnyObstacle(
            ATBPExtension parentExt, boolean practice, Point2D point) {
        List<Obstacle> obstacles;
        if (practice) obstacles = parentExt.getPracticeMapObstacles();
        else obstacles = parentExt.getMainMapObstacles();
        for (Obstacle o : obstacles) {
            if (o.contains(point)) return true;
        }
        return false;
    }

    public static Point2D getPathIntersectionPoint(
            Line2D movementLine, List<Vector<Float>> collider) {
        Point2D[] allPoints = findAllPoints(movementLine);
        List<Line2D> colliderLines =
                getColliderVectorLines(collider).stream()
                        .filter(l -> l.intersectsLine(movementLine))
                        .collect(Collectors.toList());
        Line2D closestLine = findClosestLine(movementLine, colliderLines);
        if (closestLine != null) {
            Point2D[] allLinePoints = findAllPoints(closestLine);
            for (int i = 0; i < allPoints.length; i++) {
                for (Point2D p : allLinePoints) {
                    if (p.distance(allPoints[i]) <= 0.5f) {
                        if (i != 0) return allPoints[i - 1];
                        else {
                            Console.logWarning("Inside collider!");
                            return movementLine.getP1();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Point2D getPathIntersectionPoint(
            ATBPExtension parentExt, boolean practice, Line2D movementLine) {
        List<Obstacle> obstacles;
        if (practice) obstacles = parentExt.getPracticeMapObstacles();
        else obstacles = parentExt.getMainMapObstacles();
        for (Obstacle o : obstacles) {
            if (o.intersects(movementLine)) {
                return o.intersectPoint(movementLine);
            }
        }
        return null;
    }

    public static List<Point2D> getPath(RoomHandler roomHandler, Point2D location, Point2D dest) {
        try {
            FloatArray path = new FloatArray();
            roomHandler
                    .getPathHelper()
                    .findPath(
                            (float) location.getX() + 50,
                            (float) location.getY() + 30,
                            (float) dest.getX() + 50,
                            (float) dest.getY() + 30,
                            0.6f,
                            path);
            List<Point2D> pathList = new ArrayList<>();
            float px = 0;
            float py = 0;
            for (int i = 0; i < path.size; i++) {
                if (i % 2 == 0) px = path.get(i);
                else py = path.get(i);
                if (px != 0 && py != 0) {
                    pathList.add(new Point2D.Float(px - 50, py - 30));
                    px = 0f;
                    py = 0f;
                }
            }
            return pathList;
        } catch (Exception e) {
            ArrayList<Point2D> newPath = new ArrayList<>(2);
            newPath.add(location);
            newPath.add(dest);
            return newPath;
        }
    }

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
}

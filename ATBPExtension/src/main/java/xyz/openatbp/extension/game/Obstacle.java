package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import xyz.openatbp.extension.MovementManager;

public class Obstacle {

    private Path2D path;
    private List<Point2D> points;

    public Obstacle(Path2D path, List<Vector<Float>> vectors) {
        this.path = path;
        this.points = new ArrayList<>(vectors.size());
        for (Vector<Float> v : vectors) {
            points.add(new Point2D.Float(v.get(0), v.get(1)));
        }
    }

    public boolean contains(Point2D point) {
        double x = point.getX();
        double y = point.getY();
        boolean inside = false;

        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Point2D p = points.get(i);
            Point2D p2 = points.get(j);
            double xi = p.getX();
            double yi = p.getY();
            double xj = p2.getX();
            double yj = p2.getY();

            boolean intersect =
                    ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    public boolean intersects(Line2D line) {
        Point2D[] allPoints = MovementManager.findAllPoints(line);
        for (Point2D p : allPoints) {
            if (this.contains(p)) return true;
        }
        return false;
    }

    public Point2D intersectPoint(Line2D line) {
        Point2D[] allPoints = MovementManager.findAllPoints(line);
        for (int i = 0; i < allPoints.length; i++) {
            if (this.contains(allPoints[i])) {
                if (i <= 3) line.getP1();
                else return allPoints[i - 4];
            }
        }
        return null;
    }

    public Point2D intersectPoint(Point2D point, float radius) {
        return null;
    }
}

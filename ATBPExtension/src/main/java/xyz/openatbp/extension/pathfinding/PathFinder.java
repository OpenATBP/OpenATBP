package xyz.openatbp.extension.pathfinding;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.Champion;

public class PathFinder {
    public static final double SMOOTH_ANGLE_DEG = 3.0;
    private Point2D[] mapBoundary;
    private List<Point2D[]> obstacles;

    public static float INTERSECTION_STOP_DISTANCE = 0.5f;
    private List<Obstacle> obstacleList;
    private final Path2D mapArea;
    private final List<Line2D> mapEdges;

    private static final int GRID_SIZE = 128;
    private static final int SPLINE_SUBDIVISIONS = 8;

    private final boolean[][] walkable;
    private final double minX, minY, maxX, maxY;
    private final double cellW, cellH;

    public PathFinder(Point2D[] mapBoundary, List<Point2D[]> obstacles) {
        this.mapBoundary = mapBoundary;
        this.obstacles = obstacles;

        Path2D area = new Path2D.Float();
        area.moveTo(mapBoundary[0].getX(), mapBoundary[0].getY());

        for (int i = 1; i < mapBoundary.length; i++) {
            area.lineTo(mapBoundary[i].getX(), mapBoundary[i].getY());
        }

        area.closePath();
        this.mapArea = area;
        this.mapEdges = getEdges(List.of(mapBoundary));

        List<Obstacle> obsList = new ArrayList<>();

        for (Point2D[] obstacle : obstacles) {
            Obstacle obs = getObs(obstacle);
            obsList.add(obs);
        }

        this.obstacleList = obsList;

        minX = Arrays.stream(mapBoundary).mapToDouble(Point2D::getX).min().orElse(0);
        minY = Arrays.stream(mapBoundary).mapToDouble(Point2D::getY).min().orElse(0);
        maxX = Arrays.stream(mapBoundary).mapToDouble(Point2D::getX).max().orElse(1);
        maxY = Arrays.stream(mapBoundary).mapToDouble(Point2D::getY).max().orElse(1);
        cellW = (maxX - minX) / GRID_SIZE;
        cellH = (maxY - minY) / GRID_SIZE;
        walkable = buildGrid(mapBoundary, obstacles);
    }

    public static Obstacle getObs(Point2D[] obstacle) {
        Path2D obstacleShape = new Path2D.Float();
        obstacleShape.moveTo(obstacle[0].getX(), obstacle[0].getY());

        for (int i = 1; i < obstacle.length; i++) {
            obstacleShape.lineTo(obstacle[i].getX(), obstacle[i].getY());
        }
        obstacleShape.closePath();

        List<Line2D> edges = getEdges(List.of(obstacle));
        return new Obstacle(obstacleShape, edges);
    }

    public static List<Line2D> getEdges(List<Point2D> verticesList) {
        List<Line2D> edges = new ArrayList<>();

        for (int i = 0; i < verticesList.size(); i++) {
            Point2D p1 = verticesList.get(i);
            Point2D p2 = verticesList.get((i + 1) % verticesList.size());
            Line2D line = new Line2D.Float(p1, p2);
            edges.add(line);
        }
        return edges;
    }

    public Path2D getMapArea() {
        return this.mapArea;
    }

    public Point2D getNonObstaclePointOrIntersection(Point2D startPoint, Point2D dest) {

        if (!isPointInsideMap(dest)) {
            return getMapEdgeIntersectionPoint(startPoint, dest);
        }

        if (isPointInsideObstacle(dest)) {
            return getIntersectionPoint(startPoint, dest);
        } else {
            return dest;
        }
    }

    public boolean isPointInsideMap(Point2D point) {
        return mapArea.contains(point);
    }

    public boolean lineIntersectsObstacle(Point2D start, Point2D dest) {
        Line2D line = new Line2D.Float(start, dest);
        for (Obstacle obs : obstacleList) {
            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPointInsideObstacle(Point2D point) {
        for (Obstacle obs : obstacleList) {
            if (obs.getPath().contains(point)) {
                return true;
            }
        }
        return false;
    }

    public Point2D getIntersectionPoint(Point2D start, Point2D end) {
        Line2D line = new Line2D.Float(start, end);
        for (Obstacle obs : obstacleList) {
            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(line)) {
                    Point2D intersectionPoint = getIntersectionPoint(line, edge);
                    return getValidMovePointToIntersection(start, intersectionPoint);
                }
            }
        }
        return new Point2D.Double(end.getX(), end.getY());
    }

    public Point2D getMapEdgeIntersectionPoint(Point2D start, Point2D end) {
        Point2D mapIntersection = null;
        Line2D movementLine = new Line2D.Float(start, end);

        for (Line2D edge : mapEdges) {
            if (edge.intersectsLine(movementLine)) {
                mapIntersection = getIntersectionPoint(movementLine, edge);
                break;
            }
        }

        if (mapIntersection != null) {
            return getValidMovePointToIntersection(start, mapIntersection);
        }
        return start;
    }

    public static Point2D[] getRandomPointsFromList(
            Point2D[] points, double minDifference, double maxDifference) {

        Random random = new Random();
        Point2D[] result = new Point2D[points.length];

        for (int i = 0; i < points.length; i++) {
            double range = maxDifference - minDifference;

            double xVariation = minDifference + (random.nextDouble() * range);
            double yVariation = minDifference + (random.nextDouble() * range);

            if (random.nextBoolean()) xVariation = -xVariation;
            if (random.nextBoolean()) yVariation = -yVariation;

            result[i] =
                    new Point2D.Double(
                            points[i].getX() + xVariation, points[i].getY() + yVariation);
        }

        return result;
    }

    public List<Point2D> getMovePointsToDest(Point2D start, Point2D end) {
        // end point outside an obstacle but line intersects an obstacle = pathfinding needed
        // end point inside an obstacle = move to obstacle edge
        // no intersection and end point outside obstacles = simple movement

        Line2D movementLine = new Line2D.Float(start, end);

        boolean obstacleIntersection = false;
        Point2D intersectionPoint = null;
        boolean insideObstacle = false;

        for (Obstacle obs : obstacleList) {
            if (obs.getPath().contains(end)) {
                insideObstacle = true;
            }

            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(movementLine)) {
                    obstacleIntersection = true;
                    intersectionPoint = getIntersectionPoint(movementLine, edge);
                    break;
                }
            }
            if (obstacleIntersection) break;
        }

        List<Point2D> movePointsToDest = new ArrayList<>();
        if (!obstacleIntersection && !insideObstacle && mapArea.contains(end)) {
            // simple movement
            movePointsToDest.add(end);
        }

        if (insideObstacle && intersectionPoint != null && mapArea.contains(intersectionPoint)) {
            // click inside an obstacle, move to the edge of the obstacle
            Point2D validPoint = getValidMovePointToIntersection(start, intersectionPoint);
            movePointsToDest.add(validPoint);
        }

        if (!isPointInsideMap(end)) {
            // move point outside the map, move to the edge
            movePointsToDest.add(getMapEdgeIntersectionPoint(start, end));
        }

        if (!insideObstacle
                && obstacleIntersection
                && intersectionPoint != null
                && mapArea.contains(intersectionPoint)) {
            // pathfinding needed

            List<Point2D> movePoints = findPath(start, end);
            if (!movePoints.isEmpty()) {
                movePoints.remove(0);
                movePointsToDest.addAll(movePoints);
            }
        }
        return movePointsToDest;
    }

    public static Point2D getValidMovePointToIntersection(
            Point2D startPoint, Point2D exactIntersection) {
        float distance = (float) startPoint.distance(exactIntersection);
        float newDistance = distance - INTERSECTION_STOP_DISTANCE;

        Line2D line = Champion.getAbilityLine(startPoint, exactIntersection, newDistance);
        return line.getP2();
    }

    private static double calculateDet(Point2D p1, Point2D p2) {
        return (p1.getX() * p2.getY()) - (p2.getX() * p1.getY());
    }

    public static Point2D getIntersectionPoint(Line2D line1, Line2D line2) {
        // first point L1
        double L1_x1 = line1.getX1();
        double L1_y1 = line1.getY1();
        Point2D p1 = new Point2D.Double(L1_x1, L1_y1);

        // second point L1
        double L1_x2 = line1.getX2();
        double L1_y2 = line1.getY2();
        Point2D p2 = new Point2D.Double(L1_x2, L1_y2);

        // first point L2
        double L2_x1 = line2.getX1();
        double L2_y1 = line2.getY1();

        // second point L2
        double L2_x2 = line2.getX2();
        double L2_y2 = line2.getY2();

        Point2D x_Diff = new Point2D.Double(L1_x1 - L1_x2, L2_x1 - L2_x2);
        Point2D y_Diff = new Point2D.Double(L1_y1 - L1_y2, L2_y1 - L2_y2);

        double divisorDet = calculateDet(x_Diff, y_Diff);
        if (divisorDet == 0) return null;

        double L1_d = calculateDet(line1.getP1(), line1.getP2());
        double L2_d = calculateDet(line2.getP1(), line2.getP2());

        Point2D d = new Point2D.Double(L1_d, L2_d);

        double x = calculateDet(d, x_Diff) / divisorDet;
        double y = calculateDet(d, y_Diff) / divisorDet;
        return new Point2D.Double(x, y);
    }

    public void displayMapBoundaries(ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D p : mapBoundary) {
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "skully",
                    id + Math.random(),
                    1000 * 60 * 15,
                    (float) p.getX(),
                    (float) p.getY(),
                    false,
                    team,
                    0f);
        }
    }

    public void displayMapObstacles(ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D[] obstacle : obstacles) {
            for (Point2D p : obstacle) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "skully",
                        id + Math.random(),
                        1000 * 60 * 15,
                        (float) p.getX(),
                        (float) p.getY(),
                        false,
                        team,
                        0f);
            }
        }
    }

    public Point2D getStoppingPoint(
            Point2D start, Point2D initialDestination, double stoppingDistance) {
        double lineLength = start.distance(initialDestination) - stoppingDistance;
        return Champion.getAbilityLine(start, initialDestination, (float) lineLength).getP2();
    }

    private boolean[][] buildGrid(Point2D[] mapShape, List<Point2D[]> obstacles) {
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gy = 0; gy < GRID_SIZE; gy++) {
                Point2D center = gridToWorld(gx, gy);
                boolean inMap = pointInPolygon(center, mapShape);
                boolean inObstacle =
                        obstacles.stream().anyMatch(obs -> pointInPolygon(center, obs));
                grid[gx][gy] = inMap && !inObstacle;
            }
        }
        return grid;
    }

    // ------------------------------------------------------------------ pathfinding (Theta*)
    public List<Point2D> findPath(Point2D start, Point2D end) {
        int[] gs = worldToGrid(start);
        int[] ge = worldToGrid(end);

        if (!inBounds(gs[0], gs[1]) || !inBounds(ge[0], ge[1])) return Collections.emptyList();
        if (!walkable[gs[0]][gs[1]] || !walkable[ge[0]][ge[1]]) return Collections.emptyList();

        // fast path – direct line of sight
        if (hasLOS(gs[0], gs[1], ge[0], ge[1])) {
            return subdivideLine(start, end, SPLINE_SUBDIVISIONS);
        }

        int[][] gParent = new int[GRID_SIZE][GRID_SIZE];
        double[][] gScore = new double[GRID_SIZE][GRID_SIZE];
        for (double[] row : gScore) Arrays.fill(row, Double.MAX_VALUE);
        for (int[] row : gParent) Arrays.fill(row, -1);

        gScore[gs[0]][gs[1]] = 0;
        gParent[gs[0]][gs[1]] = gs[0] * GRID_SIZE + gs[1];

        PriorityQueue<int[]> open =
                new PriorityQueue<>(
                        Comparator.comparingDouble(
                                n -> gScore[n[0]][n[1]] + heuristic(n[0], n[1], ge[0], ge[1])));
        open.add(gs);
        Set<Long> closed = new HashSet<>();

        int[][] dirs = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

        while (!open.isEmpty()) {
            int[] cur = open.poll();
            long key = (long) cur[0] * GRID_SIZE + cur[1];
            if (!closed.add(key)) continue;

            if (cur[0] == ge[0] && cur[1] == ge[1]) {
                List<Point2D> raw = reconstructGrid(gParent, gs, ge);
                raw.set(0, start); // exact world start
                raw.set(raw.size() - 1, end); // exact world end
                return smoothPath(raw);
            }

            int[] par = parentOf(gParent, cur);

            for (int[] d : dirs) {
                int nx = cur[0] + d[0], ny = cur[1] + d[1];
                if (!inBounds(nx, ny) || !walkable[nx][ny]) continue;
                if (closed.contains((long) nx * GRID_SIZE + ny)) continue;

                // prevent diagonal corner-cutting
                if (d[0] != 0 && d[1] != 0) {
                    if (!walkable[cur[0] + d[0]][cur[1]] || !walkable[cur[0]][cur[1] + d[1]])
                        continue;
                }

                int[] candidate;
                double candidateG;
                if (hasLOS(par[0], par[1], nx, ny)) {
                    candidateG = gScore[par[0]][par[1]] + gridDist(par[0], par[1], nx, ny);
                    candidate = par;
                } else {
                    candidateG = gScore[cur[0]][cur[1]] + gridDist(cur[0], cur[1], nx, ny);
                    candidate = cur;
                }

                if (candidateG < gScore[nx][ny]) {
                    gScore[nx][ny] = candidateG;
                    gParent[nx][ny] = candidate[0] * GRID_SIZE + candidate[1];
                    open.add(new int[] {nx, ny});
                }
            }
        }
        return Collections.emptyList();
    }

    // ------------------------------------------------ line-of-sight (Bresenham + diagonal safety)
    private boolean hasLOS(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0, cy = y0;
        while (true) {
            if (!inBounds(cx, cy) || !walkable[cx][cy]) return false;
            if (cx == x1 && cy == y1) return true;
            int e2 = 2 * err;
            boolean stepX = e2 > -dy;
            boolean stepY = e2 < dx;
            if (stepX && stepY) { // diagonal step
                if (!inBounds(cx + sx, cy) || !walkable[cx + sx][cy]) return false;
                if (!inBounds(cx, cy + sy) || !walkable[cx][cy + sy]) return false;
            }
            if (stepX) {
                err -= dy;
                cx += sx;
            }
            if (stepY) {
                err += dx;
                cy += sy;
            }
        }
    }

    // ------------------------------------------------ path reconstruction
    private List<Point2D> reconstructGrid(int[][] parent, int[] start, int[] end) {
        LinkedList<Point2D> path = new LinkedList<>();
        int cx = end[0], cy = end[1];
        int safety = GRID_SIZE * GRID_SIZE;
        while ((cx != start[0] || cy != start[1]) && --safety > 0) {
            path.addFirst(gridToWorld(cx, cy));
            int p = parent[cx][cy];
            cx = p / GRID_SIZE;
            cy = p % GRID_SIZE;
        }
        path.addFirst(gridToWorld(start[0], start[1]));
        return new ArrayList<>(path);
    }

    // ------------------------------------------------ smoothing
    private List<Point2D> smoothPath(List<Point2D> path) {
        if (path.size() <= 1) return path;
        if (path.size() == 2) return subdivideLine(path.get(0), path.get(1), SPLINE_SUBDIVISIONS);
        List<Point2D> smooth = catmullRomSmooth(path, SPLINE_SUBDIVISIONS);
        return reduceCollinear(smooth, SMOOTH_ANGLE_DEG);
    }

    private List<Point2D> reduceCollinear(List<Point2D> path, double angleDeg) {
        if (path.size() <= 2) return path;

        double threshold = Math.toRadians(angleDeg);
        List<Point2D> result = new ArrayList<>();
        result.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            Point2D prev = result.get(result.size() - 1);
            Point2D cur = path.get(i);
            Point2D next = path.get(i + 1);

            double a1 = Math.atan2(cur.getY() - prev.getY(), cur.getX() - prev.getX());
            double a2 = Math.atan2(next.getY() - cur.getY(), next.getX() - cur.getX());

            double diff = Math.abs(a2 - a1);
            if (diff > Math.PI) diff = 2.0 * Math.PI - diff;

            if (diff > threshold) {
                result.add(cur);
            }
        }

        result.add(path.get(path.size() - 1));
        return result;
    }

    /** Simple linear subdivision – used for straight-line segments and as fallback. */
    private List<Point2D> subdivideLine(Point2D a, Point2D b, int segments) {
        List<Point2D> r = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            r.add(
                    new Point2D.Double(
                            a.getX() + t * (b.getX() - a.getX()),
                            a.getY() + t * (b.getY() - a.getY())));
        }
        return r;
    }

    /**
     * Replaces every straight segment with a Catmull-Rom spline arc, producing new points per
     * original segment. If any interpolated point lands in an obstacle the segment falls back to
     * simple linear subdivision so the path stays valid.
     */
    private List<Point2D> catmullRomSmooth(List<Point2D> pts, int subdivisions) {
        List<Point2D> result = new ArrayList<>();
        result.add(pts.get(0));

        for (int i = 0; i < pts.size() - 1; i++) {
            Point2D p0 = pts.get(Math.max(0, i - 1));
            Point2D p1 = pts.get(i);
            Point2D p2 = pts.get(i + 1);
            Point2D p3 = pts.get(Math.min(pts.size() - 1, i + 2));

            List<Point2D> seg = new ArrayList<>();
            boolean valid = true;

            for (int j = 1; j <= subdivisions; j++) {
                double t = j / (double) subdivisions;
                Point2D pt = catmullRom(p0, p1, p2, p3, t);
                int[] g = worldToGrid(pt);
                if (inBounds(g[0], g[1]) && walkable[g[0]][g[1]]) {
                    seg.add(pt);
                } else {
                    valid = false;
                    break;
                }
            }

            if (valid) {
                result.addAll(seg);
            } else {
                // linear fallback keeps the path inside walkable space
                for (int j = 1; j <= subdivisions; j++) {
                    double t = j / (double) subdivisions;
                    result.add(
                            new Point2D.Double(
                                    p1.getX() + t * (p2.getX() - p1.getX()),
                                    p1.getY() + t * (p2.getY() - p1.getY())));
                }
            }
        }
        return result;
    }

    /** Standard uniform Catmull-Rom interpolation. */
    private Point2D catmullRom(Point2D p0, Point2D p1, Point2D p2, Point2D p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x =
                0.5
                        * ((2 * p1.getX())
                                + (-p0.getX() + p2.getX()) * t
                                + (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t2
                                + (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t3);
        double y =
                0.5
                        * ((2 * p1.getY())
                                + (-p0.getY() + p2.getY()) * t
                                + (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * t2
                                + (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * t3);
        return new Point2D.Double(x, y);
    }

    // ------------------------------------------------ geometry
    private boolean pointInPolygon(Point2D p, Point2D[] poly) {
        boolean inside = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            Point2D a = poly[i], b = poly[j];
            if ((a.getY() > p.getY()) != (b.getY() > p.getY())
                    && p.getX()
                            < (b.getX() - a.getX()) * (p.getY() - a.getY()) / (b.getY() - a.getY())
                                    + a.getX()) inside = !inside;
        }
        return inside;
    }

    // ------------------------------------------------ grid helpers
    private int[] worldToGrid(Point2D p) {
        int gx = (int) Math.min(GRID_SIZE - 1, Math.max(0, (p.getX() - minX) / cellW));
        int gy = (int) Math.min(GRID_SIZE - 1, Math.max(0, (p.getY() - minY) / cellH));
        return new int[] {gx, gy};
    }

    private Point2D gridToWorld(int gx, int gy) {
        return new Point2D.Double(minX + (gx + 0.5) * cellW, minY + (gy + 0.5) * cellH);
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    private double gridDist(int x0, int y0, int x1, int y1) {
        double dx = x0 - x1, dy = y0 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double heuristic(int x, int y, int ex, int ey) {
        return gridDist(x, y, ex, ey);
    }

    private int[] parentOf(int[][] parent, int[] node) {
        int p = parent[node[0]][node[1]];
        return new int[] {p / GRID_SIZE, p % GRID_SIZE};
    }
}

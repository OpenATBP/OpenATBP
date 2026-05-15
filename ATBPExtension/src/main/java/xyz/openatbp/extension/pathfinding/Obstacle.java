package xyz.openatbp.extension.pathfinding;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

public class Obstacle {
    private final Path2D path;
    private final List<Line2D> edges;

    public Obstacle(Path2D path, List<Line2D> edges) {
        this.path = path;
        this.edges = edges;
    }

    public Path2D getPath() {
        return this.path;
    }

    public List<Line2D> getEdges() {
        return this.edges;
    }
}

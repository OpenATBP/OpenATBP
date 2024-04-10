package xyz.openatbp.extension.pathfinding;

import java.awt.geom.Point2D;
import java.util.*;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.Actor;

public class Node {
    public static final int SIZE = 1;
    private int col;
    private int row;
    private boolean solid;
    private int x;
    private int y;

    public Node(int col, int row, boolean practice) {
        this.col = col;
        this.row = row;
        this.x = (col * SIZE * -1) + (ATBPExtension.MAX_COL / 2);
        this.y =
                (row * SIZE)
                        - (practice
                                ? (ATBPExtension.MAX_PRAC_ROW / 2)
                                : (ATBPExtension.MAX_MAIN_ROW / 2));
    }

    public void run() {}

    public void display(ATBPExtension parentExt, Room room) {
        ExtensionCommands.createActor(
                parentExt,
                room,
                "node" + "col-" + this.col + "row-" + this.row,
                "gnome_a",
                new Point2D.Float(this.x, this.y),
                0f,
                2);
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public static Node getCurrentNode(ATBPExtension parentExt, Actor actor) {
        Node[][] nodes = parentExt.getMainMapNodes();
        int maxRow = ATBPExtension.MAX_MAIN_ROW;
        if (parentExt.getRoomHandler(actor.getRoom().getId()).isPracticeMap()) {
            nodes = parentExt.getPracticeMapNodes();
            maxRow = ATBPExtension.MAX_PRAC_ROW;
        }
        int likelyCol =
                (int)
                        Math.round(
                                Math.abs(
                                        actor.getLocation().getX()
                                                - ((double) ATBPExtension.MAX_COL / 2d)));
        int likelyRow = (int) Math.round(actor.getLocation().getY() + ((double) maxRow / 2d));
        if (likelyCol < 0) likelyCol = 0;
        if (likelyRow < 0) likelyRow = 0;
        // Console.debugLog("Col: " + likelyCol + " Row: " + likelyRow);
        try {
            return nodes[likelyCol][likelyRow];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Node getNodeAtLocation(ATBPExtension parentExt, Point2D dest, boolean practice) {
        Node[][] nodes = parentExt.getMainMapNodes();
        int maxRow = ATBPExtension.MAX_MAIN_ROW;
        if (practice) {
            nodes = parentExt.getPracticeMapNodes();
            maxRow = ATBPExtension.MAX_PRAC_ROW;
        }
        int likelyCol =
                (int) Math.round(Math.abs(dest.getX() - ((double) ATBPExtension.MAX_COL / 2d)));
        int likelyRow = (int) Math.round(dest.getY() + ((double) maxRow / 2d));
        if (likelyCol < 0) likelyCol = 0;
        if (likelyRow < 0) likelyRow = 0;
        // Console.debugLog("Col: " + likelyCol + " Row: " + likelyRow);
        try {
            return nodes[likelyCol][likelyRow];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getCol() {
        return this.col;
    }

    public int getRow() {
        return this.row;
    }

    public int getGCost(Node startNode) {
        int xDist = Math.abs(this.col - startNode.getCol());
        int yDist = Math.abs(this.row - startNode.getRow());
        return xDist + yDist;
    }

    public int getHCost(Node goalNode) {
        int xDist = Math.abs(this.col - goalNode.getCol());
        int yDist = Math.abs(this.row - goalNode.getRow());
        return xDist + yDist;
    }

    public void printCosts(Node startNode, Node goalNode) {
        Console.debugLog("Node: x=" + this.x + " y=" + this.y);
        Console.debugLog("GCOST: " + this.getGCost(startNode));
        Console.debugLog("HCOST: " + this.getHCost(goalNode));
        Console.debugLog("FCOST: " + this.getFCost(startNode, goalNode));
    }

    public int getFCost(Node startNode, Node goalNode) {
        return this.getGCost(startNode) + this.getHCost(goalNode);
    }

    public static List<Point2D> getPath(
            ATBPExtension parentExt,
            Node startNode,
            Node currentNode,
            Node goalNode,
            boolean practice) {
        // Console.debugLog("Practice: " + practice);
        Node[][] mapNodes = parentExt.getMainMapNodes();
        int maxRow = ATBPExtension.MAX_MAIN_ROW;
        if (practice) {
            mapNodes = parentExt.getPracticeMapNodes();
            maxRow = ATBPExtension.MAX_PRAC_ROW;
        }
        List<Node> checkedNodes = new ArrayList<>();
        List<Node> openNodes = new ArrayList<>();
        List<Point2D> path = new ArrayList<>();
        Map<Node, Node> trackedNodes = new HashMap<>();
        int step = 0;
        while (step < 300) { // TODO: May lag the server if done unnecessarily. Seems to be an issue
            // when players die sometimes
            if (currentNode == goalNode) {
                Node current = goalNode;
                int test = 0;
                while (current != startNode && test < 200) {
                    test++;
                    path.add(current.getLocation());
                    current = trackedNodes.get(current);
                    // current.display(parentExt, a.getRoom());
                }
                break;
            } else {
                checkedNodes.add(currentNode);
                openNodes.remove(currentNode);
            }
            // Console.debugLog("StartNode: x=" + startNode.getX() + " y=" + startNode.getY());
            // Console.debugLog("CurrentNode: x=" + currentNode.getX() + " y=" +
            // currentNode.getY());
            // Console.debugLog("Solid: " + currentNode.isSolid());
            // Console.debugLog("GoalNode: x=" + goalNode.getX() + " y=" + goalNode.getY());
            // Console.debugLog("===========================");
            if (currentNode.getRow() - 1 >= 0) {
                Node upNode = mapNodes[currentNode.getCol()][currentNode.getRow() - 1];
                if (upNode.canBeOpened(checkedNodes, openNodes)) {
                    openNodes.add(upNode);
                    trackedNodes.put(upNode, currentNode);
                }
            }
            if (currentNode.getCol() - 1 >= 0) {
                Node leftNode = mapNodes[currentNode.getCol() - 1][currentNode.getRow()];
                if (leftNode.canBeOpened(checkedNodes, openNodes)) {
                    openNodes.add(leftNode);
                    trackedNodes.put(leftNode, currentNode);
                }
            }
            if (currentNode.getRow() + 1 < maxRow) {
                Node downNode = mapNodes[currentNode.getCol()][currentNode.getRow() + 1];
                if (downNode.canBeOpened(checkedNodes, openNodes)) {
                    openNodes.add(downNode);
                    trackedNodes.put(downNode, currentNode);
                }
            }
            if (currentNode.getCol() + 1 < ATBPExtension.MAX_COL) {
                Node rightNode = mapNodes[currentNode.getCol() + 1][currentNode.getRow()];
                if (rightNode.canBeOpened(checkedNodes, openNodes)) {
                    openNodes.add(rightNode);
                    trackedNodes.put(rightNode, currentNode);
                }
            }

            int bestNodeIndex = 0;
            int bestNodefCost = 999;

            for (int i = 0; i < openNodes.size(); i++) {
                // Console.debugLog("Best F Cost:" + bestNodefCost);
                // openNodes.get(i).printCosts(startNode, goalNode);
                if (openNodes.get(i).getFCost(startNode, goalNode) < bestNodefCost) {
                    bestNodefCost = openNodes.get(i).getFCost(startNode, goalNode);
                    bestNodeIndex = i;
                } else if (openNodes.get(i).getFCost(startNode, goalNode) == bestNodefCost) {
                    if (openNodes.get(i).getGCost(startNode)
                            < openNodes.get(bestNodeIndex).getGCost(startNode)) {
                        bestNodeIndex = i;
                    }
                }
            }
            currentNode = openNodes.get(bestNodeIndex);
            // Console.debugLog("NewNode: x=" + currentNode.getX() + " y=" + currentNode.getY());
            step++;
        }
        Collections.reverse(path);
        return path;
    }

    @Deprecated
    public static List<Point2D> smoothPath(List<Point2D> points) {
        Console.debugLog("Running!");
        if (points.size() == 2) return points;
        List<Point2D> funnel = new ArrayList<>();
        funnel.add(points.get(0));
        Console.debugLog("Here!");
        Point2D apex = points.get(0);
        Point2D left = points.get(0);

        for (int i = 1; i < points.size(); i++) {
            Point2D current = points.get(i);
            double ccwVal = ccw(apex, left, current);
            if (ccwVal > 0) {
                left = current;
            } else if (ccwVal < 0) {
                while (funnel.size() > 1
                        && ccw(
                                        funnel.get(funnel.size() - 2),
                                        funnel.get(funnel.size() - 1),
                                        current)
                                <= 0) {
                    funnel.remove(funnel.size() - 1);
                }
                funnel.add(current);
                apex = left;
            } else {
                funnel.add(current);
                left = current;
            }
        }
        funnel.add(points.get(points.size() - 1));
        for (Point2D p : funnel) {
            Console.debugLog("Path: x=" + p.getX() + " y=" + p.getY());
        }
        return funnel;
    }

    private static double ccw(Point2D a, Point2D b, Point2D c) {
        Console.debugLog(
                (b.getX() - a.getX()) * (c.getY() - a.getY())
                        - (b.getY() - a.getY()) * (c.getX() - a.getX()));
        return (b.getX() - a.getX()) * (c.getY() - a.getY())
                - (b.getY() - a.getY()) * (c.getX() - a.getX());
    }

    public Point2D getLocation() {
        return new Point2D.Float(this.x, this.y);
    }

    public boolean canBeOpened(List<Node> checkedNodes, List<Node> openNodes) {
        return !openNodes.contains(this) && !checkedNodes.contains(this) && !this.solid;
    }

    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    public boolean isSolid() {
        return this.solid;
    }
}

package xyz.openatbp.extension.pathfinding;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.Actor;

public class Node {
    public static final int SIZE = 1;
    Node parent;
    int col;
    int row;
    int gCost;
    int hCost;
    int fCost;
    boolean start;
    boolean goal;
    boolean solid;
    boolean open;
    boolean checked;
    private int x;
    private int y;

    public Node(int col, int row) {
        this.col = col;
        this.row = row;
        this.x = (col * SIZE * -1) + 60;
        this.y = (row * SIZE) - 30;
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

    public void setAsStart() {
        this.start = true;
    }

    public void setAsGoal() {
        this.goal = true;
    }

    public static Node getCurrentNode(ATBPExtension parentExt, Actor actor) {
        Node[][] nodes = parentExt.getMainMapNodes();
        int likelyCol = (int) Math.round(Math.abs(actor.getLocation().getX() - 60));
        int likelyRow = (int) Math.round(actor.getLocation().getY() + 30);
        if (likelyCol < 0) likelyCol = 0;
        if (likelyRow < 0) likelyRow = 0;
        Console.debugLog("Col: " + likelyCol + " Row: " + likelyRow);
        try {
            return nodes[likelyCol][likelyRow];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Node getNodeAtLocation(ATBPExtension parentExt, Point2D dest) {
        Node[][] nodes = parentExt.getMainMapNodes();
        int likelyCol = (int) Math.round(Math.abs(dest.getX() - 60));
        int likelyRow = (int) Math.round(dest.getY() + 30);
        if (likelyCol < 0) likelyCol = 0;
        if (likelyRow < 0) likelyRow = 0;
        Console.debugLog("Col: " + likelyCol + " Row: " + likelyRow);
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

    public int getFCost(Actor actor) {
        return this.getGCost(actor.getStartNode()) + this.getHCost(actor.getGoalNode());
    }

    public int getFCost(Node startNode, Node goalNode) {
        return this.getGCost(startNode) + this.getHCost(goalNode);
    }

    public static void getPath(Actor a, Node startNode, Node currentNode, Node goalNode) {
        ATBPExtension parentExt = a.getParentExt();
        List<Node> checkedNodes = new ArrayList<>();
        List<Node> openNodes = new ArrayList<>();
        int step = 0;
        boolean goalReached = false;
        while (!goalReached && step < 300) {
            Console.debugLog("CurrentNode: x=" + currentNode.getX() + " y=" + currentNode.getY());
            if (currentNode.getRow() - 1 >= 0) {
                Node upNode =
                        parentExt.getMainMapNodes()[currentNode.getCol()][currentNode.getRow() - 1];
                if (upNode.canBeOpened(checkedNodes, openNodes)) openNodes.add(upNode);
            }
            if (currentNode.getCol() - 1 >= 0) {
                Node leftNode =
                        parentExt.getMainMapNodes()[currentNode.getCol() - 1][currentNode.getRow()];
                if (leftNode.canBeOpened(checkedNodes, openNodes)) openNodes.add(leftNode);
            }
            if (currentNode.getRow() + 1 < 60) {
                Node downNode =
                        parentExt.getMainMapNodes()[currentNode.getCol()][currentNode.getRow() + 1];
                if (downNode.canBeOpened(checkedNodes, openNodes)) openNodes.add(downNode);
            }
            if (currentNode.getCol() + 1 < 120) {
                Node rightNode =
                        parentExt.getMainMapNodes()[currentNode.getCol() + 1][currentNode.getRow()];
                if (rightNode.canBeOpened(checkedNodes, openNodes)) openNodes.add(rightNode);
            }

            int bestNodeIndex = 0;
            int bestNodefCost = 999;

            for (int i = 0; i < openNodes.size(); i++) {
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
            Node bestNode = openNodes.get(bestNodeIndex);
            currentNode = bestNode;
            Console.debugLog("NewNode: x=" + currentNode.getX() + " y=" + currentNode.getY());
            if (currentNode == goalNode) goalReached = true;
            ExtensionCommands.createActor(
                    parentExt,
                    a.getRoom(),
                    "pathTest" + Math.random() * 1000,
                    "gnome_b",
                    bestNode.getLocation(),
                    0f,
                    2);
            step++;
        }
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

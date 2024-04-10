package xyz.openatbp.extension.reqhandlers;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.dongbat.walkable.PathfinderException;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.pathfinding.MovementManager;
import xyz.openatbp.extension.pathfinding.Node;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Called when player clicks on the map to move

        // Console.debugLog(params.getDump());
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        RoomHandler roomHandler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId());
        if (roomHandler == null && (int) sender.getLastJoinedRoom().getProperty("state") != 2)
            ExtensionCommands.abortGame(parentExt, sender.getLastJoinedRoom());
        if (roomHandler == null) {
            Console.logWarning(
                    sender.getId()
                            + " tried to move in room "
                            + sender.getLastJoinedRoom().getId()
                            + " but failed!");
            return;
        }
        UserActor user = roomHandler.getPlayer(String.valueOf(sender.getId()));
        if (user != null && user.canMove() && !user.getIsDashing()) {
            user.resetTarget();
            user.resetIdleTime();
            user.clearPath();
            Room room = sender.getLastJoinedRoom();
            long timeSinceBasicAttack =
                    sender.getVariable("stats").getSFSObjectValue().getLong("timeSinceBasicAttack");
            if ((System.currentTimeMillis() - timeSinceBasicAttack) < 500)
                return; // hard coded, this seems to be when the projectile should leaving during
            // the
            // animation

            float px = params.getFloat("orig_x");
            float pz = params.getFloat("orig_z");
            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");
            // Console.debugLog("dx: " + dx + " dz: " + dz);

            try {
                List<Point2D> path = new ArrayList<>(0);
                Line2D movementLine = new Line2D.Float(px, pz, dx, dz);
                if (MovementManager.getPathIntersectionPoint(
                                        parentExt,
                                        parentExt.getRoomHandler(room.getId()).isPracticeMap(),
                                        movementLine)
                                != null
                        && !MovementManager.insideAnyObstacle(
                                parentExt,
                                parentExt.getRoomHandler(room.getId()).isPracticeMap(),
                                new Point2D.Float(dx, dz))) {
                    path =
                            Node.getPath(
                                    parentExt,
                                    Node.getCurrentNode(parentExt, user),
                                    Node.getCurrentNode(parentExt, user),
                                    Node.getNodeAtLocation(parentExt, new Point2D.Float(dx, dz)));
                }

                if (path.size() <= 2
                        || MovementManager.insideAnyObstacle(
                                parentExt,
                                parentExt.getRoomHandler(room.getId()).isPracticeMap(),
                                new Point2D.Float(dx, dz))) {
                    // Creates the path of the player
                    // ExtensionCommands.createWorldFX(parentExt, user.getRoom(),
                    // "test","gnome_c","testBox"+Math.random(),5000,(float)playerBoundingBox.getCenterX(),(float)playerBoundingBox.getCenterY(),false,0,0f);
                    Point2D intersectionPoint =
                            MovementManager.getPathIntersectionPoint(
                                    parentExt,
                                    parentExt.getRoomHandler(room.getId()).isPracticeMap(),
                                    movementLine);

                    float destx = (float) movementLine.getX2();
                    float destz = (float) movementLine.getY2();
                    if (intersectionPoint
                            != null) { // If the player hits an object, find where they should end
                        // up
                        // ExtensionCommands.createWorldFX(parentExt,user.getRoom(),"nothing","gnome_a","testPoint"+Math.random(),10000,(float)intersectionPoint.getX(),(float)intersectionPoint.getY(),false,0,0f);
                        destx = (float) intersectionPoint.getX();
                        destz = (float) intersectionPoint.getY();
                    }
                    Point2D dest = new Point2D.Float(destx, destz);
                    if (movementLine.getP1().distance(dest) >= 0.1f) user.move(params, dest);
                    else user.stopMoving();
                } else {
                    user.setPath(path);
                }
            } catch (PathfinderException pe) {
                Line2D movementLine =
                        new Line2D.Float(px, pz, dx, dz); // Creates the path of the player
                // ExtensionCommands.createWorldFX(parentExt, user.getRoom(),
                // "test","gnome_c","testBox"+Math.random(),5000,(float)playerBoundingBox.getCenterX(),(float)playerBoundingBox.getCenterY(),false,0,0f);
                Point2D intersectionPoint =
                        MovementManager.getPathIntersectionPoint(
                                parentExt,
                                parentExt.getRoomHandler(room.getId()).isPracticeMap(),
                                movementLine);

                float destx = (float) movementLine.getX2();
                float destz = (float) movementLine.getY2();
                if (intersectionPoint
                        != null) { // If the player hits an object, find where they should end up
                    // ExtensionCommands.createWorldFX(parentExt,user.getRoom(),"nothing","gnome_a","testPoint"+Math.random(),10000,(float)intersectionPoint.getX(),(float)intersectionPoint.getY(),false,0,0f);
                    destx = (float) intersectionPoint.getX();
                    destz = (float) intersectionPoint.getY();
                }
                Point2D dest = new Point2D.Float(destx, destz);
                if (movementLine.getP1().distance(dest) >= 0.1f) user.move(params, dest);
                else user.stopMoving();
            }
        }
    }
}

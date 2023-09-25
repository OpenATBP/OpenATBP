package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Obstacle;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) { //Called when player clicks on the map to move

        //trace(params.getDump());
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        RoomHandler roomHandler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId());
        if(roomHandler == null && (int)sender.getLastJoinedRoom().getProperty("state") != 2) ExtensionCommands.abortGame(parentExt,sender.getLastJoinedRoom());
        if(roomHandler == null){
            System.out.println(sender.getId() + " tried to move in room " + sender.getLastJoinedRoom().getId() + " but failed!");
            return;
        }
        UserActor user = roomHandler.getPlayer(String.valueOf(sender.getId()));
        if(user != null && user.canMove()){
            user.resetTarget();
            user.resetIdleTime();
            String room = sender.getLastJoinedRoom().getGroupId();
            long timeSinceBasicAttack = sender.getVariable("stats").getSFSObjectValue().getLong("timeSinceBasicAttack");
            if ((System.currentTimeMillis() - timeSinceBasicAttack) < 500) return; //hard coded, this seems to be when the projectile should leaving during the animation

            float px = params.getFloat("orig_x");
            float pz = params.getFloat("orig_z");
            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");
            Line2D movementLine = new Line2D.Float(px,pz,dx,dz); //Creates the path of the player
            //ExtensionCommands.createWorldFX(parentExt, user.getRoom(), "test","gnome_c","testBox"+Math.random(),5000,(float)playerBoundingBox.getCenterX(),(float)playerBoundingBox.getCenterY(),false,0,0f);
            ArrayList<Vector<Float>>[] colliders = ((ATBPExtension) getParentExtension()).getColliders(room); //Gets all collision object vertices
            ArrayList<Path2D> mapPaths = ((ATBPExtension) getParentExtension()).getMapPaths(room); //Gets all created paths for the collision objects
            Point2D intersectionPoint = null;
            for(Obstacle o : parentExt.getMainMapObstacles()){
                if(o.intersects(movementLine)){
                    intersectionPoint = o.intersectPoint(movementLine);
                    break;
                }
            }
            /*
            for(int i = 0; i < mapPaths.size(); i++){ //Search through all colliders
                Point2D playerLoc = MovementManager.getPathIntersectionPoint(movementLine,colliders[i]);
                if(playerLoc != null){
                    intersectionPoint = playerLoc;
                    break;
                }
            }

             */
            float destx = (float)movementLine.getX2();
            float destz = (float)movementLine.getY2();
            if(intersectionPoint != null){ //If the player hits an object, find where they should end up
                //ExtensionCommands.createWorldFX(parentExt,user.getRoom(),"nothing","gnome_a","testPoint"+Math.random(),10000,(float)intersectionPoint.getX(),(float)intersectionPoint.getY(),false,0,0f);
                destx = (float)intersectionPoint.getX();
                destz = (float)intersectionPoint.getY();
            }
            Point2D dest = new Point2D.Float(destx,destz);
            if(movementLine.getP1().distance(dest) >= 0.1f) user.move(params,dest);
            else user.stopMoving();
        }else System.out.println("Can't move!");

    }

}

package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.Champion;
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
            for(int i = 0; i < mapPaths.size(); i++){ //Search through all colliders
                if(mapPaths.get(i).intersects(movementLine.getBounds())){ //If the player's movement intersects a collider
                    intersectionPoint = MovementManager.getPathIntersectionPoint(movementLine,colliders[i]);
                }
            }
            float destx = (float)movementLine.getX2();
            float destz = (float)movementLine.getY2();
            if(intersectionPoint != null){ //If the player hits an object, find where they should end up
                //ExtensionCommands.createWorldFX(parentExt,user.getRoom(),"nothing","gnome_a","testPoint"+Math.random(),10000,(float)intersectionPoint.getX(),(float)intersectionPoint.getY(),false,0,0f);
                destx = (float)intersectionPoint.getX();
                destz = (float)intersectionPoint.getY();
                /*
                if(insideCollider(finalPoint,mapPaths) && !insideCollider(movementLine.getP2(), mapPaths)){
                    System.out.println("Test case 1");
                    destx = (float)movementLine.getX2();
                    destz = (float)movementLine.getY2();
                }

                 */
            }else{
                System.out.println("Does not intersect!");
            }
            /*Point2D testPoint = new Point2D.Float(destx,destz);
            if(insideCollider(movementLine.getP1(), mapPaths)){
                System.out.println("Test case 2");
                Point2D newPoint = this.getOutsidePoint(new Line2D.Float(movementLine.getP1(),testPoint),mapPaths);
                destx = (float) newPoint.getX();
                destz = (float) newPoint.getY();
            }

             */
            Point2D dest = new Point2D.Float(destx,destz);
            if(movementLine.getP1().distance(dest) >= 0.1f)user.move(params,dest);
            else user.stopMoving();
        }else System.out.println("Can't move!");

    }

    private Point2D[] findAllPoints(Line2D line){ //Finds all points within a line
        int arrayLength = (int)(line.getP1().distance(line.getP2()))*30; //Longer movement have more precision when checking collisions
        if(arrayLength < 8) arrayLength = 8;
        Point2D[] points = new Point2D[arrayLength];
        float slope = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        float intercept = (float)(line.getP2().getY()-(slope*line.getP2().getX()));
        float distance = (float)(line.getX2()-line.getX1());
        int pValue = 0;
        for(int i = 0; i < points.length; i++){ //Finds the points on the line based on distance
            float x = (float)line.getP1().getX()+((distance/points.length)*i);
            float y = slope*x + intercept;
            Point2D point = new Point2D.Float(x,y);
            points[pValue] = point;
            pValue++;
        }
        return points;
    }

    private Point2D getIntersectionPoint(Line2D line, Line2D line2){ //Finds the intersection of two lines
        float slope1 = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        float slope2 = (float)((line2.getP2().getY() - line2.getP1().getY())/(line2.getP2().getX()-line2.getP1().getX()));
        float intercept1 = (float)(line.getP2().getY()-(slope1*line.getP2().getX()));
        float intercept2 = (float)(line2.getP2().getY()-(slope2*line2.getP2().getX()));
        float x = (intercept2-intercept1)/(slope1-slope2);
        float y = slope1 * ((intercept2-intercept1)/(slope1-slope2)) + intercept1;
        return new Point2D.Float(x,y);
    }

    //Returns a point where the player is no longer colliding with an object so that they can move freely and don't clip inside an object
    @Deprecated
    private Point2D collidePlayer(Line2D movementLine, Path2D collider){
        if(collider.contains(movementLine.getP1())) return movementLine.getP1();
        Point2D[] points = findAllPoints(movementLine);
        Point2D p = movementLine.getP1();
        for(int i = points.length-2; i>0; i--){ //Searchs all points in the movement line to see how close it can move without crashing into the collider
            Point2D p2 = new Point2D.Double(points[i].getX(),points[i].getY());
            Line2D line = new Line2D.Double(movementLine.getP1(),p2);
            if(collider.intersects(line.getBounds())){
                p = p2;
                break;
            }
        }
        return p;
    }

    private boolean insideCollider(Point2D point, List<Path2D> colliders){
        for(Path2D collider : colliders){
            if(collider.contains(point)) return true;
        }
        return false;
    }

    private Point2D getOutsidePoint(Line2D line, ArrayList<Path2D> colliders){
        Point2D[] allPoints = findAllPoints(new Line2D.Float(line.getP2(),line.getP1()));
        for(Path2D collider: colliders){
            for(int i = allPoints.length-1; i >= 0; i--){
                if(!collider.contains(allPoints[i])) return allPoints[i];
            }
        }
        return line.getP2();
    }

}

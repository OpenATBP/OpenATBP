package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Vector;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) { //Called when player clicks on the map to move
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        String room = sender.getLastJoinedRoom().getGroupId();
        float px = params.getFloat("orig_x");
        float pz = params.getFloat("orig_z");
        float dx = params.getFloat("dest_x");
        float dz = params.getFloat("dest_z");
        Line2D movementLine = new Line2D.Float(px,pz,dx,dz); //Creates the path of the player
        boolean intersects = false;
        int mapPathIndex = -1;
        float closestDistance = 100000;
        ArrayList<Vector<Float>>[] colliders = ((ATBPExtension) getParentExtension()).getColliders(room); //Gets all collision object vertices
        ArrayList<Path2D> mapPaths = ((ATBPExtension) getParentExtension()).getMapPaths(room); //Gets all created paths for the collision objects
        Point2D intersectionPoint = new Point2D.Float(-1,-1);
        for(int i = 0; i < mapPaths.size(); i++){ //Search through all colliders
            if(mapPaths.get(i).intersects(movementLine.getBounds())){ //If the player's movement intersects a collider
                ArrayList<Vector<Float>> collider = colliders[i];
                for(int g = 0; g < collider.size(); g++){ //Check all vertices in the collider

                    Vector<Float> v = collider.get(g);
                    Vector<Float> v2;
                    if(g+1 == collider.size()){ //If it's the final vertex, loop to the beginning
                        v2 = collider.get(0);
                    }else{
                        v2 = collider.get(g+1);
                    }


                    Line2D colliderLine = new Line2D.Float(v.get(0),v.get(1),v2.get(0),v2.get(1)); //Draws a line segment for the sides of the collider
                    if(movementLine.intersectsLine(colliderLine)){ //If the player movement intersects a side
                        intersects = true;
                        Point2D intPoint = getIntersectionPoint(movementLine,colliderLine);
                        float dist = (float)movementLine.getP1().distance(intPoint);
                        if(dist<closestDistance){ //If the player intersects two objects, this chooses the closest one.
                            mapPathIndex = i;
                            closestDistance = dist;
                            intersectionPoint = intPoint;
                        }

                    }
                }
            }
        }
        float destx = (float)movementLine.getX2();
        float destz = (float)movementLine.getY2();
        if(intersects){ //If the player hits an object, find where they should end up
            Point2D finalPoint = collidePlayer(new Line2D.Double(movementLine.getX1(),movementLine.getY1(),intersectionPoint.getX(),intersectionPoint.getY()),mapPaths.get(mapPathIndex));
            destx = (float)finalPoint.getX();
            destz = (float)finalPoint.getY();
        }
        trace("X: " + movementLine.getX2());
        trace("Y:" + movementLine.getY2());
        //Updates the player's location variable for the server's internal use
        ISFSObject userLocation = sender.getVariable("location").getSFSObjectValue();
        userLocation.putFloat("x",destx);
        userLocation.putFloat("z",destz);
        ISFSObject currentLoc = new SFSObject();
        currentLoc.putFloat("x",(float)movementLine.getX1());
        currentLoc.putFloat("z",(float)movementLine.getY1());
        userLocation.putSFSObject("p1",currentLoc);
        userLocation.putFloat("time",0);
        userLocation.putFloat("speed",params.getFloat("speed"));
        ISFSObject data = new SFSObject();
        data.putUtfString("i", String.valueOf(sender.getId()));
        data.putFloat("px", params.getFloat("orig_x"));
        data.putFloat("pz", params.getFloat("orig_z"));
        data.putFloat("dx", destx);
        data.putFloat("dz", destz);
        data.putBool("o", params.getBool("orient"));
        data.putFloat("s", params.getFloat("speed"));
        //Send all users the movement data
        GameManager.sendAllUsers(parentExt,data,"cmd_move_actor",sender.getLastJoinedRoom());
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
    private Point2D collidePlayer(Line2D movementLine, Path2D collider){
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

}

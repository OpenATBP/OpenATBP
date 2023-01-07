package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        String room = sender.getLastJoinedRoom().getGroupId();
        float px = params.getFloat("orig_x");
        float pz = params.getFloat("orig_z");
        float dx = params.getFloat("dest_x");
        float dz = params.getFloat("dest_z");
        Line2D movementLine = new Line2D.Float(px,pz,dx,dz);
        Point2D[] movementLinePoints = findAllPoints(movementLine);
        boolean intersects = false;
        Point2D closestVector = new Point2D.Float(-100,-100);
        int mapPathIndex = -1;
        float closestDistance = 100000;
        ArrayList<Vector<Float>>[] colliders = ((ATBPExtension) getParentExtension()).getColliders(room);
        ArrayList<Vector<Float>> closestCollider = new ArrayList<Vector<Float>>(2);
        ArrayList<Path2D> mapPaths = ((ATBPExtension) getParentExtension()).getMapPaths(room);
        Point2D intersectionPoint = new Point2D.Float(-1,-1);
        float[] testCoords = new float[2];
        for(int i = 0; i < mapPaths.size(); i++){
            if(mapPaths.get(i).intersects(movementLine.getBounds())){
                //trace("Intersects!");
                ArrayList<Vector<Float>> collider = colliders[i];

                for(int g = 0; g < collider.size(); g++){

                    Vector<Float> v = collider.get(g);
                    Vector<Float> v2;
                    if(g+1 == collider.size()){
                        v2 = collider.get(0);
                    }else{
                        v2 = collider.get(g+1);
                    }


                    Line2D colliderLine = new Line2D.Float(v.get(0),v.get(1),v2.get(0),v2.get(1));
                    if(movementLine.intersectsLine(colliderLine)){
                        //trace("Intersects!");
                        intersects = true;
                        Point2D intPoint = getIntersectionPoint(movementLine,colliderLine);
                        float dist = (float)movementLine.getP1().distance(intPoint);
                        if(dist<closestDistance){
                            mapPathIndex = i;
                            closestDistance = dist;
                            intersectionPoint = intPoint;
                        }

                    }else{
                        //trace("Does not intersect!");
                    }
                }
            }
        }
        /*
        for(int i = 1; i < movementLinePoints.length; i++){ //Search all points in the line
            Point2D movementPoint = movementLinePoints[i];

            for(int g = 0; g < colliders.length; g++){ //Get all colliders
                Path2D colliderPath = mapPaths.get(g);
                Point2D currentPoint = new Point2D.Float(colliders[g].get(0).get(0),colliders[g].get(0).get(1));
                //trace(movementPoint.toString());
                //trace(currentPoint.toString());
                //trace(closestVector.toString());
                if(movementPoint.distance(currentPoint) < movementPoint.distance(closestVector) && !intersects){
                    closestVector = currentPoint;
                    closestCollider = colliders[g];
                    mapPathIndex = g;
                }

            }
            for(int g = 0; g < closestCollider.size(); g++){

                Vector<Float> v = closestCollider.get(g);
                Vector<Float> v2;
                if(g+1 == closestCollider.size()){
                    v2 = closestCollider.get(0);
                }else{
                    v2 = closestCollider.get(g+1);
                }


                Line2D colliderLine = new Line2D.Float(v.get(0),v.get(1),v2.get(0),v2.get(1));
                if(movementLine.intersectsLine(colliderLine)){
                    intersects = true;
                    //trace("Intersects!");
                    Point2D intPoint = getIntersectionPoint(movementLine,colliderLine);
                    float dist = (float)movementLine.getP1().distance(intPoint);
                    if(dist<closestDistance){
                        closestDistance = dist;
                        intersectionPoint = intPoint;
                    }

                }else{
                    //trace("Does not intersect!");
                }
            }
            if(mapPaths.get(mapPathIndex).intersects(movementLine.getBounds())){
                trace("Intersects!");
            }
            if(intersects) break;
        }
        */
        int testPoly = 11;
        testCoords[0] = (float)closestVector.getX();
        testCoords[1] = (float)closestVector.getY();
        float destx = (float)movementLine.getX2();
        float destz = (float)movementLine.getY2();
        if(intersects){
            Point2D finalPoint = collidePlayer(new Line2D.Double(movementLine.getX1(),movementLine.getY1(),intersectionPoint.getX(),intersectionPoint.getY()),mapPaths.get(mapPathIndex));
            destx = (float)finalPoint.getX();
            destz = (float)finalPoint.getY();
        }
        trace("X: " + movementLine.getX2());
        trace("Y:" + movementLine.getY2());
        ISFSObject userLocation = sender.getVariable("location").getSFSObjectValue();
        userLocation.putFloat("x",destx);
        userLocation.putFloat("z",destz);
        ISFSObject data = new SFSObject();
        data.putUtfString("i", String.valueOf(sender.getId()));
        data.putFloat("px", params.getFloat("orig_x"));
        data.putFloat("pz", params.getFloat("orig_z"));
        data.putFloat("dx", destx);
        data.putFloat("dz", destz);
        data.putBool("o", params.getBool("orient"));
        data.putFloat("s", params.getFloat("speed"));
        GameManager.sendAllUsers(parentExt,data,"cmd_move_actor",sender.getLastJoinedRoom());
    }

    private Point2D[] findAllPoints(Line2D line){
        int arrayLength = (int)(line.getP1().distance(line.getP2()))*30;
        if(arrayLength < 8) arrayLength = 8;
        Point2D[] points = new Point2D[arrayLength];
        float slope = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        //trace("Slope: " + slope);
        float intercept = (float)(line.getP2().getY()-(slope*line.getP2().getX()));
        //trace("Intercept: " + intercept);
        float distance = (float)(line.getX2()-line.getX1());
        int pValue = 0;
        for(int i = 0; i < points.length; i++){
            float x = (float)line.getP1().getX()+((distance/points.length)*i);
            float y = slope*x + intercept;
            Point2D point = new Point2D.Float(x,y);
            points[pValue] = point;
            //("i: " + i);
            //trace(point.toString());
            pValue++;
        }
        return points;
    }

    private Point2D getIntersectionPoint(Line2D line, Line2D line2){
        float slope1 = (float)((line.getP2().getY() - line.getP1().getY())/(line.getP2().getX()-line.getP1().getX()));
        float slope2 = (float)((line2.getP2().getY() - line2.getP1().getY())/(line2.getP2().getX()-line2.getP1().getX()));
        float intercept1 = (float)(line.getP2().getY()-(slope1*line.getP2().getX()));
        float intercept2 = (float)(line2.getP2().getY()-(slope2*line2.getP2().getX()));
        float x = (intercept2-intercept1)/(slope1-slope2);
        float y = slope1 * ((intercept2-intercept1)/(slope1-slope2)) + intercept1;
        return new Point2D.Float(x,y);
    }

    private Point2D collidePlayer(Line2D movementLine, Path2D collider){
        Point2D[] points = findAllPoints(movementLine);
        Point2D p = movementLine.getP1();
        for(int i = points.length-2; i>0; i--){
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

package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.ActorState;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

public class UserActor extends Actor {

    protected User player;
    protected boolean canMove = true;
    private Point2D destination;
    private Point2D originalLocation;
    private float timeTraveled = 0;
    protected Map<ActorState, Boolean> states;

    public UserActor(User u, ATBPExtension parentExt){
        this.parentExt = parentExt;
        this.id = String.valueOf(u.getId());
        this.team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
        player = u;
        this.avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x,z);
        this.speed = getStat("speed");
        states = new HashMap<>(ActorState.values().length);
        for(ActorState s : ActorState.values()){
            states.put(s, false);
        }
    }

    public Point2D getRelativePoint(){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        if(this.destination == null) this.destination = this.location;
        float x2 = (float) this.destination.getX();
        float y2 = (float) this.destination.getY();
        if(this.originalLocation == null) this.originalLocation = this.location;
        float x1 = (float) this.originalLocation.getX();
        float y1 = (float) this.originalLocation.getY();
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        if(dist == 0) return this.originalLocation;
        double time = dist/speed;
        double currentTime = this.timeTraveled + 0.1;
        if(currentTime>time) currentTime=time;
        double currentDist = speed*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        this.location = rPoint;
        return rPoint;
    }

    @Override
    public Room getRoom() {
        return player.getLastJoinedRoom();
    }

    public ISFSObject getStats(){
        return player.getVariable("stats").getSFSObjectValue();
    }

    public double getStat(String stat){
        return player.getVariable("stats").getSFSObjectValue().getDouble(stat);
    }

    public ISFSObject getSFSLocation(){
        return player.getVariable("location").getSFSObjectValue();
    }

    public void setPath(Point2D start, Point2D end){
        this.originalLocation = start;
        this.destination = end;
        this.timeTraveled = 0f;
    }

    public void updateMovementTime(){
        this.timeTraveled+=0.1f;
    }

    public User getUser(){
        return this.player;
    }
    public Point2D getOriginalLocation(){
        return this.originalLocation;
    }

    @Override
    public Point2D getLocation(){
        return this.getRelativePoint();
    }

    @Override
    public void setLocation(Point2D location){
        this.location = location;
        this.originalLocation = location;
        this.destination = location;
        this.timeTraveled = 0f;
    }

    public void useAbility(int ability, ISFSObject abilityData){System.out.println("No character selected!");}
    public boolean canMove(){
        return canMove;
    }
    public void setCanMove(boolean canMove){
        this.canMove = canMove;
    }

    public void setState(ActorState state, boolean stateBool){
        states.put(state,stateBool);
        ExtensionCommands.updateActorState(parentExt,player,id,state,stateBool);
    }

    public void setState(ActorState[] states, boolean stateBool){
        for(ActorState s : states){
            this.states.put(s,stateBool);
            ExtensionCommands.updateActorState(parentExt,player,id,s,stateBool);
        }
    }

    public boolean isState(ActorState state){
        return this.states.get(state);
    }

    public boolean canUseAbility(){
        ActorState[] hinderingStates = {ActorState.POLYMORPH, ActorState.AIRBORNE, ActorState.CHARMED, ActorState.FEARED, ActorState.SILENCED, ActorState.STUNNED};
        for(ActorState s : hinderingStates){
            if(this.states.get(s)) return false;
        }
        return true;
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        MovementStopper(boolean move){
            this.move = move;
        }
        @Override
        public void run() {
            canMove = this.move;
        }
    }
}

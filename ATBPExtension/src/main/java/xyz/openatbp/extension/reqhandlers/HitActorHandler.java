package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.MovementManager;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Base;
import xyz.openatbp.extension.game.actors.BaseTower;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

@MultiHandler
public class HitActorHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {

        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        RoomHandler handler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId());
        UserActor actor = handler.getPlayer(String.valueOf(sender.getId()));
        String roomGroup = sender.getLastJoinedRoom().getGroupId();

        if(actor != null){
            String targetId = params.getUtfString("target_id");
            Actor target = handler.getActor(targetId);
            if(target == null){
                Console.logWarning(targetId + " is not a valid target");
                return;
            }
            if(target.getActorType() == ActorType.BASE){
                Base b = (Base) target;
                if(!b.isUnlocked()) return;
            }
            if(roomGroup.equalsIgnoreCase("practice")){
                if(target.getActorType() == ActorType.TOWER && target.getId().equalsIgnoreCase("purple_tower0") || target.getId().equalsIgnoreCase("blue_tower3")){
                    BaseTower bt = (BaseTower) target;
                    if(!bt.isUnlocked()) return;
                }
            } else {
                if(target.getActorType() == ActorType.TOWER && target.getId().equalsIgnoreCase("purple_tower3") || target.getId().equalsIgnoreCase("blue_tower3")){
                    BaseTower bt = (BaseTower) target;
                    if(!bt.isUnlocked()) return;
                }
            }
            if(target.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) target;
                if(ua.getState(ActorState.INVISIBLE) && !ua.getState(ActorState.REVEALED)) return;
            }
            Point2D location = new Point2D.Float(params.getFloat("x"),params.getFloat("z"));
            actor.resetIdleTime();
            actor.setTarget(target);
            if(actor.withinRange(target) && actor.canAttack()){
                actor.stopMoving();
                actor.attack(target);
            }else if(!actor.withinRange(target) && actor.canMove() && actor.canAttack()){ //Move actor
                int attackRange = parentExt.getActorStats(actor.getAvatar()).get("attackRange").asInt();
                Line2D movementLine = new Line2D.Float(location,target.getLocation());
                float targetDistance = (float)target.getLocation().distance(location)-attackRange;
                Line2D newPath = Champion.getDistanceLine(movementLine,targetDistance);
                actor.setPath(MovementManager.getPath(parentExt,parentExt.getRoomHandler(actor.getRoom().getId()).isPracticeMap(),location,target.getLocation()));
            }else if(actor.withinRange(target)){
                actor.stopMoving();
            }
        }else{
            trace("No player found!");
        }
    }

    public static class MovementStopper implements Runnable {

        UserActor actor;

        public MovementStopper(UserActor actor){
            this.actor = actor;
        }

        @Override
        public void run() {
            actor.setCanMove(true);
        }
    }
}

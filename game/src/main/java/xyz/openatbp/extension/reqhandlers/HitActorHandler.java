package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import com.smartfoxserver.v2.util.TaskScheduler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

@MultiHandler
public class HitActorHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {

        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        RoomHandler handler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId());
        UserActor actor = handler.getPlayer(String.valueOf(sender.getId()));
        if(actor != null){
            String targetId = params.getUtfString("target_id");
            Actor target = handler.getActor(targetId);
            Point2D location = new Point2D.Float(params.getFloat("x"),params.getFloat("z"));
            if(target != null){
                actor.setTarget(target);
                if(actor.withinRange(target) && actor.canAttack()){

                    ExtensionCommands.moveActor(parentExt,sender,actor.getId(),location,location,(float) actor.getSpeed(),false);
                    actor.setLocation(location);
                    actor.setCanMove(false);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(actor),250,TimeUnit.MILLISECONDS);
                    actor.attack(target);
                }else if(!actor.withinRange(target)){ //Move actor
                    int attackRange = parentExt.getActorStats(actor.getAvatar()).get("attackRange").asInt();
                    Line2D movementLine = new Line2D.Float(location,target.getLocation());
                    float targetDistance = (float)target.getLocation().distance(location)-attackRange;
                    Line2D newPath = Champion.getDistanceLine(movementLine,targetDistance);
                    actor.setPath(newPath);
                    ExtensionCommands.moveActor(parentExt,sender, actor.getId(),location, newPath.getP2(), (float) actor.getSpeed(),true);
                }else if(actor.withinRange(target)){
                    trace("Failed to attack!");
                    trace("Attack cooldown: " + actor.getAttackCooldown());
                    ExtensionCommands.moveActor(parentExt,sender,actor.getId(),location,location, (float) actor.getSpeed(),false);
                    actor.setLocation(location);
                }
            }else{
                trace("No target found!");
            }
        }else{
            trace("No player found!");
        }
        trace(params.getDump());
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

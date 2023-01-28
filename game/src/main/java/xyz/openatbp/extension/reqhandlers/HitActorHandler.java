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
            Actor target = null;
            if(targetId.contains("creep")) target = handler.getMinion(targetId);
            else if(targetId.contains("tower")) target = handler.getTower(targetId);
            else if(targetId.contains("base")) target = handler.getOpposingTeamBase(actor.getTeam());
            else handler.getPlayer(targetId);
            if(target != null){
                if(actor.withinRange(target) && actor.canAttack()){
                    Point2D location = new Point2D.Float(params.getFloat("x"),params.getFloat("z"));
                    ExtensionCommands.moveActor(parentExt,sender,actor.getId(),location,location,3.75f,false);
                    actor.setLocation(location);
                    actor.setCanMove(false);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(actor),250,TimeUnit.MILLISECONDS);
                    actor.attack(target);
                }else if(!actor.withinRange(target)){ //Move actor

                }
            }
        }
        trace(params.getDump());
    }

    private class MovementStopper implements Runnable {

        UserActor actor;

        MovementStopper(UserActor actor){
            this.actor = actor;
        }

        @Override
        public void run() {
            actor.setCanMove(true);
        }
    }
}

package xyz.openatbp.extension.reqhandlers;

import java.awt.geom.Point2D;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.actors.UserActor;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Called when player clicks on the map to move

        // Console.debugLog(params.getDump());
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        RoomHandler roomHandler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getName());
        if (roomHandler == null && (int) sender.getLastJoinedRoom().getProperty("state") != 3)
            ExtensionCommands.abortGame(parentExt, sender.getLastJoinedRoom());
        if (roomHandler == null) {
            Console.logWarning(
                    sender.getId()
                            + " tried to move in room "
                            + sender.getLastJoinedRoom().getName()
                            + " but failed!");
            return;
        }
        UserActor user = roomHandler.getPlayer(String.valueOf(sender.getId()));
        if (user != null) user.resetTarget();
        if (user != null
                && user.canMove()
                && !user.getIsDashing()
                && !user.getIsAutoAttacking()
                && !user.getState(ActorState.CHARMED)) {
            user.resetIdleTime();
            user.clearPath();
            long timeSinceBasicAttack =
                    sender.getVariable("stats").getSFSObjectValue().getLong("timeSinceBasicAttack");
            if ((System.currentTimeMillis() - timeSinceBasicAttack) < 500)
                return; // hard coded, this seems to be when the projectile should leave during
            // the
            // animation
            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");
            user.moveWithCollision(new Point2D.Float(dx, dz));
            // Console.debugLog("dx: " + dx + " dz: " + dz);
        } else if (user != null && user.getIsAutoAttacking()) {
            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");
            user.queueMovement(new Point2D.Float(dx, dz));
        } else Console.logWarning(sender.getId() + " has no user object!");
    }
}

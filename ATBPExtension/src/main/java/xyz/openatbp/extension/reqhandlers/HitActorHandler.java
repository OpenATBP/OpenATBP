package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.MovementState;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Base;
import xyz.openatbp.extension.game.actors.BaseTower;
import xyz.openatbp.extension.game.actors.UserActor;

@MultiHandler
public class HitActorHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {

        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        RoomHandler handler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getName());
        UserActor actor = handler.getPlayer(String.valueOf(sender.getId()));
        String roomGroup = sender.getLastJoinedRoom().getGroupId();

        if (actor != null && actor.getMovementState() == MovementState.IDLE) {
            String targetId = params.getUtfString("target_id");
            Actor target = handler.getActor(targetId);
            if (target == null) {

                return;
            }
            if (target.getId().equals("creep1_super12")) {
                return;
            }
            if (target.getActorType() == ActorType.BASE) {
                Base b = (Base) target;
                if (!b.isUnlocked()) return;
            }

            GameMap gameMap = GameManager.getMap(GameManager.getRoomGroupEnum(roomGroup));

            if (gameMap == GameMap.CANDY_STREETS) {
                if (target.getActorType() == ActorType.TOWER
                                && target.getId().equalsIgnoreCase("purple_tower0")
                        || target.getId().equalsIgnoreCase("blue_tower3")) {
                    BaseTower bt = (BaseTower) target;
                    if (!bt.isUnlocked()) return;
                }
            } else {
                if (target.getActorType() == ActorType.TOWER
                                && target.getId().equalsIgnoreCase("purple_tower3")
                        || target.getId().equalsIgnoreCase("blue_tower3")) {
                    BaseTower bt = (BaseTower) target;
                    if (!bt.isUnlocked()) return;
                }
            }

            actor.resetIdleTime();
            actor.setTarget(target);
            if (actor.withinRange(target) && actor.canAttack()) {
                actor.stopMoving();
                actor.attack(target);
            } else if (!actor.withinRange(target) && actor.canMove() && actor.canAttack()) {
                // Move actor
                actor.startMoveTo(target.getLocation(), false);
            } else if (actor.withinRange(target)) {
                actor.stopMoving();
            }
        }
    }
}

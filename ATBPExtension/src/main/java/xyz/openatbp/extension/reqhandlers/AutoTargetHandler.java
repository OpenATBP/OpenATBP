package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.game.actors.UserActor;

public class AutoTargetHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        UserActor player =
                parentExt
                        .getRoomHandler(sender.getLastJoinedRoom().getId())
                        .getPlayer(String.valueOf(sender.getId()));
        if (player != null) {
            player.setAutoAttackEnabled(params.getBool("enabled"));
        }
    }
}

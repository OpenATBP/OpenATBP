package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.actors.UserActor;

public class PingHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        RoomHandler roomHandler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getName());
        UserActor user = roomHandler.getPlayer(String.valueOf(sender.getId()));
        for (UserActor ua : roomHandler.getPlayers()) {
            if (ua.getTeam() == user.getTeam()) {
                ISFSObject data = new SFSObject();
                data.putUtfString("id", String.valueOf(sender.getId()));
                data.putInt("msg_type", params.getInt("msg_type"));
                data.putFloat("x", params.getFloat("x"));
                data.putFloat("y", params.getFloat("y"));
                parentExt.send("cmd_mini_map_message", data, ua.getUser());
            }
        }
    }
}

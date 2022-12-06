package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        trace(params.getDump());

        ISFSObject data = new SFSObject();
        data.putUtfString("i", String.valueOf(sender.getId()));
        data.putFloat("px", params.getFloat("orig_x"));
        data.putFloat("pz", params.getFloat("orig_z"));
        data.putFloat("dx", params.getFloat("dest_x"));
        data.putFloat("dz", params.getFloat("dest_z"));
        data.putBool("o", params.getBool("orient"));
        data.putFloat("s", params.getFloat("speed"));
        GameManager.sendAllUsers(parentExt,data,"cmd_move_actor",sender.getLastJoinedRoom());
    }
}

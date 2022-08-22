package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import xyz.openatbp.extension.ATBPExtension;

public class MoveActor extends BaseClientRequestHandler {
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

        parentExt.send("cmd_move_actor", data, sender);
    }
}

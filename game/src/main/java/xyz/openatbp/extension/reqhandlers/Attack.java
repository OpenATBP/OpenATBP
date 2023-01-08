package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@MultiHandler
public class Attack extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        trace(params.getDump());
        trace("multi " + params.getUtfString(SFSExtension.MULTIHANDLER_REQUEST_ID));

        String userId = String.valueOf(sender.getId());
        String attackString = params.getUtfString("attackString");
        String targetId = params.getUtfString("target_id");
        float x = params.getFloat("x");
        float y = params.getFloat("y");
        float z = params.getFloat("z");

        //does animation
        ISFSObject attackActorData = new SFSObject();
        attackActorData.putUtfString("id", userId);
        attackActorData.putUtfString("target_id", targetId);
        attackActorData.putFloat("dest_x", x);
        attackActorData.putFloat("dest_y", y);
        attackActorData.putFloat("dest_z", z);
        attackActorData.putUtfString("attack_type", "BASIC"); //unused???
        attackActorData.putBool("crit", false);
        attackActorData.putBool("orient", true);

        GameManager.sendAllUsers(parentExt, attackActorData,"cmd_attack_actor", sender.getLastJoinedRoom());

        //creates projectile
        ISFSObject CreateProjectileFxData = new SFSObject();
        CreateProjectileFxData.putUtfString("name", "gunter_bottle_projectile"); //fx name
        CreateProjectileFxData.putUtfString("attacker", userId); //attacker id
        CreateProjectileFxData.putUtfString("target", targetId); //target id
        CreateProjectileFxData.putUtfString("emit", "Gunter_Mesh"); //where the projectile comes from
        CreateProjectileFxData.putUtfString("hit", ""); //where the projectile stops (?)
        CreateProjectileFxData.putFloat("time", 1);

        GameManager.sendAllUsers(parentExt, CreateProjectileFxData,"cmd_create_projectile_fx", sender.getLastJoinedRoom());


        ISFSObject data = new SFSObject();
        data.putUtfString("i", String.valueOf(sender.getId()));
        data.putFloat("px", x);
        data.putFloat("pz", z);
        data.putFloat("dx", x);
        data.putFloat("dz", z);
        data.putBool("o", false);
        data.putFloat("s", 2.85f);
        GameManager.sendAllUsers(parentExt,data,"cmd_move_actor",sender.getLastJoinedRoom());
    }
}

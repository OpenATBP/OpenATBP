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
public class DoActorAbilityHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        trace(params.getDump());

        String userId = String.valueOf(sender.getId());
        String ability = params.getUtfString("id");
        float x = params.getFloat("x");
        float y = params.getFloat("y");
        float z = params.getFloat("z");
        float fx = params.getFloat("fx");
        float fy = params.getFloat("fy");
        float fz = params.getFloat("fz");

        ISFSObject specialAttackData = new SFSObject();
        List<Float> location = new ArrayList<>(Arrays.asList(x, y, z));
        specialAttackData.putUtfString("id", userId);
        specialAttackData.putFloatArray("location", location);
        specialAttackData.putUtfString("ability", ability);

        GameManager.sendAllUsers(parentExt, specialAttackData,"cmd_special_attack", sender.getLastJoinedRoom());

        ISFSObject actorAbilityResponseData = new SFSObject();
        actorAbilityResponseData.putUtfString("id", ability);
        actorAbilityResponseData.putBool("canCast", true);
        actorAbilityResponseData.putInt("cooldown", 5000); //load from xml files
        actorAbilityResponseData.putInt("gCooldown", 1000);



    }
}

package xyz.openatbp.extension.reqhandlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@MultiHandler
public class DoActorAbilityHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        UserActor player = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId()).getPlayer(String.valueOf(sender.getId()));
        int spellNum = getAbilityNum(params.getUtfString("id"));
        trace(params.getDump());
        if(player.canUseAbility(spellNum)){
            player.cancelAuto(false);
            String userId = String.valueOf(sender.getId());
            String ability = params.getUtfString("id");
            float x = params.getFloat("x");
            float y = 0f;
            float z = params.getFloat("z");

            ISFSObject specialAttackData = new SFSObject();
            List<Float> location = new ArrayList<>(Arrays.asList(x, y, z));
            specialAttackData.putUtfString("id", userId);
            specialAttackData.putFloatArray("location", location);
            specialAttackData.putUtfString("ability", ability);
            GameManager.sendAllUsers(parentExt, specialAttackData,"cmd_special_attack", sender.getLastJoinedRoom());
            Point2D newLocation = new Point2D.Float(x,z);
            String playerActor = player.getAvatar();
            JsonNode spellData = getSpellData(playerActor,spellNum);
            int cooldown = spellData.get("spellCoolDown").asInt();
            int gCooldown = spellData.get("spellGlobalCoolDown").asInt();
            int castDelay = spellData.get("castDelay").asInt();
            player.useAbility(spellNum,spellData,cooldown,gCooldown,castDelay,newLocation);
        }

    }

    private JsonNode getSpellData(String avatar, int spell){
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        JsonNode actorDef = parentExt.getDefinition(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell"+spell);
    }

    private int getAbilityNum(String ability){
        switch(ability){
            case "q":
                return 1;
            case "w":
                return 2;
            case "e":
                return 3;
            default:
                return 4;
        }
    }
}

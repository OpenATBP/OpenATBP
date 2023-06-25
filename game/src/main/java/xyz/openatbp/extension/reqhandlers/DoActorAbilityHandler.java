package xyz.openatbp.extension.reqhandlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@MultiHandler
public class DoActorAbilityHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        UserActor player = parentExt.getRoomHandler(sender.getLastJoinedRoom().getId()).getPlayer(String.valueOf(sender.getId()));
        trace(params.getDump());
        if(player.canUseAbility()){
            String userId = String.valueOf(sender.getId());
            String ability = params.getUtfString("id");
            float x = params.getFloat("x");
            float y = 0f;
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

            String playerActor = sender.getVariable("player").getSFSObjectValue().getUtfString("avatar");
            int spellNum = getAbilityNum(params.getUtfString("id"));
            JsonNode spellData = getSpellData(playerActor,spellNum);
            int cooldown = spellData.get("spellCoolDown").asInt();
            int gCooldown = spellData.get("spellGlobalCoolDown").asInt();
            int castDelay = spellData.get("castDelay").asInt();
            Point2D loc = player.getLocation();
            ExtensionCommands.moveActor(parentExt,sender, String.valueOf(sender.getId()),loc,loc,1f,false);
            player.setLocation(loc); //TODO: Make ability based by putting in useAbility method
            player.setCanMove(false);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new DelayedAbility(player,spellNum),castDelay,TimeUnit.MILLISECONDS);
            player.useAbility(spellNum,params);
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

    private class DelayedAbility implements Runnable{

        UserActor player;
        int spellNum;

        DelayedAbility(UserActor player, int spellNum){
            this.player = player;
            this.spellNum = spellNum;
        }
        @Override
        public void run() {
            player.setCanMove(true);
        }
    }
}

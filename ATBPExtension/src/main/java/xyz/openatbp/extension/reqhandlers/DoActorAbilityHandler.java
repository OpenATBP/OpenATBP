package xyz.openatbp.extension.reqhandlers;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.actors.UserActor;

@MultiHandler
public class DoActorAbilityHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        // Console.debugLog(params.getDump());
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        UserActor player =
                parentExt
                        .getRoomHandler(sender.getLastJoinedRoom().getName())
                        .getPlayer(String.valueOf(sender.getId()));
        int spellNum = getAbilityNum(params.getUtfString("id"));
        boolean playSound = false;
        if (player.canUseAbility(spellNum)) {
            if (player.isCastingDashAbility(player.getAvatar(), spellNum)) {
                if (player.canDash()) {
                    doAbility(parentExt, player, sender, params, spellNum);
                } else {
                    playSound = true;
                }
            } else {
                doAbility(parentExt, player, sender, params, spellNum);
            }
        }
        if (player.hasInterrupingCC() || playSound) {
            ExtensionCommands.playSound(
                    parentExt, sender, String.valueOf(sender.getId()), "not_allowed_error");
        }
    }

    private void doAbility(
            ATBPExtension parentExt,
            UserActor player,
            User sender,
            ISFSObject params,
            int spellNum) {
        // player.resetIdleTime();
        String userId = String.valueOf(sender.getId());
        String ability = params.getUtfString("id");
        float x = params.getFloat("x");
        float y = 0f;
        float z = params.getFloat("z");
        // Console.debugLog(params.getDump());
        String playerActor = player.getAvatar();
        JsonNode spellData = getSpellData(playerActor, spellNum);
        if (spellData.has("castType")
                && spellData.get("castType").asText().equalsIgnoreCase("AIMED")) {
            Point2D serverLocation =
                    new Point2D.Float(params.getFloat("fx"), params.getFloat("fz"));
            player.setLocation(serverLocation);
        }
        Point2D oldLocation = new Point2D.Float(x, z);
        ISFSObject specialAttackData = new SFSObject();
        List<Float> locationArray = new ArrayList<>(Arrays.asList(x, y, z));
        specialAttackData.putUtfString("id", userId);
        specialAttackData.putFloatArray("location", locationArray);
        specialAttackData.putUtfString("ability", ability);
        GameManager.sendAllUsers(
                parentExt, specialAttackData, "cmd_special_attack", player.getRoom());
        // Console.debugLog("Px: " + player.getLocation().getX() + " py: " +
        // player.getLocation().getY());
        // Console.debugLog("Cx: " + x + " cy: " + y);
        // Console.debugLog("Px: " + player.getLocation().getX() + " py: " +
        // player.getLocation().getY());
        int gCooldown = spellData.get("spellGlobalCoolDown").asInt();
        int castDelay = spellData.get("castDelay").asInt();
        int baseCooldown = ChampionData.getBaseAbilityCooldown(player, spellNum);
        player.useAbility(spellNum, spellData, baseCooldown, gCooldown, castDelay, oldLocation);
        player.preventStealth();
    }

    private JsonNode getSpellData(String avatar, int spell) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        JsonNode actorDef = parentExt.getDefinition(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell" + spell);
    }

    private int getAbilityNum(String ability) {
        switch (ability) {
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

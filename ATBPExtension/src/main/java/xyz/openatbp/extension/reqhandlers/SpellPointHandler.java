package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;

public class SpellPointHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        String category = params.getUtfString("category");

        if (category
                != null) { // If there is a category that means the player is trying to use a skill
            // point
            ISFSObject data =
                    ChampionData.useSpellPoint(sender, params.getUtfString("category"), parentExt);
            if (data != null) {
                ExtensionCommands.updateActorData(parentExt, sender.getLastJoinedRoom(), data);
            }
        } else
            parentExt.send(
                    "cmd_update_actor_data",
                    ChampionData.resetSpellPoints(sender, parentExt),
                    sender);
    }
}

package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;

public class ClientReadyHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        trace("Client is ready");
        ISFSObject data = new SFSObject();
        data.putUtfString("id", String.valueOf(sender.getId()));
        data.putInt("progress", 100);
        data.putBool("isReady", true);
        parentExt.send("cmd_client_ready", data, sender);

        ISFSObject actorData = new SFSObject();
        actorData.putUtfString("id", String.valueOf(sender.getId()));
        actorData.putUtfString("actor", sender.getVariable("avatar").getStringValue());
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) -1.5);
        spawnPoint.putFloat("y", (float) 0);
        spawnPoint.putFloat("z", (float) -11.7);
        spawnPoint.putFloat("rotation", 0);
        actorData.putSFSObject("spawn_point", spawnPoint);
        parentExt.send("cmd_create_actor", actorData, sender);

        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", String.valueOf(sender.getId()));
        int champMaxHealth = ChampionData.getMaxHealth(sender.getVariable("avatar").getStringValue());
        updateData.putInt("currentHealth", champMaxHealth);
        updateData.putInt("maxHealth", champMaxHealth);
        updateData.putDouble("pHealth", 1);
        updateData.putInt("xp", 0);
        updateData.putDouble("pLevel", 0);
        updateData.putInt("level", 1);
        updateData.putInt("availableSpellPoints", 1);
        //SP_CATEGORY 1-5 TBD
        updateData.putInt("sp_category1", 0);
        updateData.putInt("sp_category2", 0);
        updateData.putInt("sp_category3" ,0);
        updateData.putInt("sp_category4", 0);
        updateData.putInt("sp_category5", 0);
        updateData.putInt("kills", 0);
        updateData.putInt("deaths", 0);
        updateData.putInt("assists", 0);
        parentExt.send("cmd_update_actor_data", updateData, sender);

        parentExt.send("cmd_match_starting", data, sender);

    }
}

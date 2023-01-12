package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

public class Champion {

    public static void attackChampion(ATBPExtension parentExt, User player, int damage){
        ExtensionCommands.damageActor(parentExt,player, String.valueOf(player.getId()),damage);
        ISFSObject stats = player.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(player.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,player,updateData);
        }else{
            handleDeath(parentExt,player);
        }

    }

    private static void handleDeath(ATBPExtension parentExt, User player){

    }
}

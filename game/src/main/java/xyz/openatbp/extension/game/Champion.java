package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.util.concurrent.TimeUnit;

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

    public static void attackChampion(ATBPExtension parentExt, User u, User target, int damage){
        ExtensionCommands.damageActor(parentExt,u, String.valueOf(target.getId()),damage);
        ISFSObject stats = target.getVariable("stats").getSFSObjectValue();
        float currentHealth = stats.getInt("currentHealth")-damage;
        if(currentHealth>0){
            float maxHealth = stats.getInt("maxHealth");
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(target.getId()));
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            stats.putInt("currentHealth", (int) currentHealth);
            stats.putDouble("pHealth", pHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }else{
            handleDeath(parentExt,u);
        }
    }


    private static void handleDeath(ATBPExtension parentExt, User player){

    }

    public static void rangedAttackChampion(ATBPExtension parentExt, Room room, String attacker, String target, int damage, int time){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,target),500, TimeUnit.MILLISECONDS);
        for(User u : room.getUserList()){
            String fxId;
            if(attacker.contains("creep")){
                fxId = "minion_projectile_";
                int team = Integer.parseInt(String.valueOf(attacker.charAt(0)));
                if(team == 1) fxId+="blue";
                else fxId+="purple";
            }else{
                User p = room.getUserById(Integer.parseInt(attacker));
                String avatar = p.getVariable("player").getSFSObjectValue().getUtfString("avatar");
                if(avatar.contains("skin")){
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,target,"Bip001","Bip001",(float)0.5);
        }
    }
}

class RangedAttack implements Runnable{
    ATBPExtension parentExt;
    Room room;
    int damage;
    String target;

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String target){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = target;
    }
    @Override
    public void run() {
        User p = room.getUserById(Integer.parseInt(target));
        for(User u : room.getUserList()){
            Champion.attackChampion(parentExt,u,p,damage);
        }
    }
}

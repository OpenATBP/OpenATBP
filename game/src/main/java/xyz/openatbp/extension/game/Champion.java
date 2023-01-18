package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;

import java.util.concurrent.TimeUnit;

public class Champion {

    @Deprecated
    public static void attackChampion(ATBPExtension parentExt, User player, int damage){ //Used for melee attacks TODO: Move over to one function as this likely does not work with multiplayer
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
            handleDeath(parentExt,player); //TODO: Implement player death
        }

    }

    public static void attackChampion(ATBPExtension parentExt, User u, User target, int damage){ //Used for ranged attacks
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

    public static void attackMinion(ATBPExtension parentExt, String attacker, Minion m, int damage){
        System.out.println(attacker + " attacking " + m.getId() + "!");
        float currentHealth = m.getHealth()-damage;
        if(m.damage(parentExt,attacker,damage)){ //Minion dies
            System.out.println("Minion dead!");
        }else{
            float maxHealth = m.getMaxHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id",m.getId());
            updateData.putInt("currentHealth",(int) currentHealth);
            updateData.putDouble("pHealth",pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            for(User u : m.getRoomUsers()){
                ExtensionCommands.updateActorData(parentExt,u,updateData);
            }
        }
    }


    //TODO: Implement player death
    private static void handleDeath(ATBPExtension parentExt, User player){

    }

    public static void rangedAttackChampion(ATBPExtension parentExt, Room room, String attacker, String target, int damage){
        //Handles the damage after the projectile animation is finished
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,target),500, TimeUnit.MILLISECONDS);
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
                if(avatar.contains("skin")){ //Skins don't have their own projectile effect so we have to pull from the base
                    avatar = avatar.split("_")[0];
                }
                fxId = avatar+"_projectile";
            }
            //TODO: Make more accurate emit & hit locations
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,target,"Bip001","Bip001",(float)0.5);
        }
    }

    public static void attackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles attacking the tower
        boolean towerDown = tower.damage(damage); // Returns true if tower is destroyed
        boolean notify = System.currentTimeMillis()-tower.getLastHit() >= 1000*5; //Returns true if we should notify players of a tower being hit
        for(User u : room.getUserList()){
            if(notify) ExtensionCommands.towerAttacked(parentExt,u, tower.getTowerNum());
            if(towerDown){ // Tower is dead
                handleTowerDeath(parentExt,u,tower,attacker);
            }
            float maxHealth = tower.getMaxHealth();
            float currentHealth = tower.getHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", tower.getId());
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        if(notify) tower.triggerNotification(); //Resets tower notification time
    }

    public static void attackBase(ATBPExtension parentExt, Room room, String attacker, Base base, int damage){
        boolean gameEnded = base.damage(damage);
        for(User u : room.getUserList()){
            if(gameEnded){ //Handle end of game
                double oppositeTeam = 0;
                if(base.getTeam() == 0) oppositeTeam = 1;
                System.out.println("Game ended!");
                try{
                    ExtensionCommands.gameOver(parentExt,u,oppositeTeam);
                }catch(Exception e){
                    e.printStackTrace();
                    System.out.println(e);
                }
            }
            float maxHealth = Base.MAX_HEALTH;
            float currentHealth = base.getHealth();
            double pHealth = currentHealth/maxHealth;
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", base.getId());
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", pHealth);
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
    }

    public static void handleBaseDeath(ATBPExtension parentExt, Room room, Base base){

    }

    private static void handleTowerDeath(ATBPExtension parentExt, User u, Tower tower, String attacker){ //Handles tower death
        ExtensionCommands.towerDown(parentExt,u, tower.getTowerNum());
        ExtensionCommands.knockOutActor(parentExt,u,tower.getId(),attacker,100);
        if(!tower.isDestroyed()){
            tower.destroy();
            ExtensionCommands.destroyActor(parentExt,u,tower.getId());
        }
        String actorId = "tower2a";
        if(tower.getTowerNum() == 0 || tower.getTowerNum() == 3 ){
            actorId = "tower1a";
        }
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),actorId,tower.getId()+"_destroyed",1000*60*15,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),"tower_destroyed_explosion",tower.getId()+"_destroyed_explosion",1000,(float)tower.getLocation().getX(),(float)tower.getLocation().getY(),false,tower.getTeam(),0f);
        Room room = u.getLastJoinedRoom();
        ISFSObject scoreObj = room.getVariable("score").getSFSObjectValue();
        int teamA = scoreObj.getInt("purple");
        int teamB = scoreObj.getInt("blue");
        if(tower.getTeam() == 0) teamB+=50;
        else teamA+=50;
        scoreObj.putInt("purple",teamA);
        scoreObj.putInt("blue",teamB);
        ExtensionCommands.updateScores(parentExt,u,teamA,teamB);
    }

    public static void rangedAttackTower(ATBPExtension parentExt, Room room, String attacker, Tower tower, int damage){ //Handles ranged attacks against tower
        //Schedules damage after projectile hits target
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,tower),500, TimeUnit.MILLISECONDS);
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
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,tower.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void rangedAttackMinion(ATBPExtension parentExt, Room room, String attacker, Minion m, int damage){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,m), 500, TimeUnit.MILLISECONDS);
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
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,m.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void rangedAttackBase(ATBPExtension parentExt, Room room, String attacker, Base base, int damage){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(parentExt,room,damage,attacker,base), 500, TimeUnit.MILLISECONDS);
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
            ExtensionCommands.createProjectileFX(parentExt,u,fxId,attacker,base.getId(),"Bip001","Bip001",(float)0.5);
        }
    }

    public static void handleMinionDeath(ATBPExtension parentExt, User u, String attacker, Minion m){
        System.out.println("Dying!");
        ExtensionCommands.knockOutActor(parentExt,u,m.getId(),attacker,0);
        ExtensionCommands.destroyActor(parentExt,u,m.getId());
        if(!attacker.contains("creep") & !attacker.contains("tower")){ // Attacker is a player

        }
    }

    public static void giveBuff(ATBPExtension parentExt,User u, Buff buff){
        String stat = null;
        float duration = 0;
        double value = 0;
        boolean icon = false;
        String effect = null;
        switch(buff){
            case HEALTH_PACK:
                stat = "healthRegen";
                value = 20;
                duration = 5f;
                effect = "fx_health_regen";
                break;
            case ATTACK_ALTAR:
                break;
        }
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        if(stat != null && stats.getDouble(stat) != null){
            double newStat = stats.getDouble(stat) + value;
            stats.putDouble(stat,newStat);
            int interval = (int) Math.floor(duration*1000);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new BuffHandler(u,stat,value,icon),interval,TimeUnit.MILLISECONDS);
            if(effect != null && effect.length() > 0){
                int team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
                ExtensionCommands.createActorFX(parentExt,u.getLastJoinedRoom(),String.valueOf(u.getId()),effect,interval,effect+u.getId(),true,"",false,false,team);
            }
        }
    }

    public static void updateHealth(ATBPExtension parentExt, User u, int health){
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        double currentHealth = stats.getInt("currentHealth");
        double maxHealth = stats.getInt("maxHealth");
        currentHealth+=health;
        if(currentHealth>maxHealth) currentHealth = maxHealth;
        double pHealth = currentHealth/maxHealth;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", String.valueOf(u.getId()));
        updateData.putInt("maxHealth",(int)maxHealth);
        updateData.putInt("currentHealth",(int)currentHealth);
        updateData.putDouble("pHealth",pHealth);
        ExtensionCommands.updateActorData(parentExt,u,updateData);
        stats.putInt("currentHealth",(int)currentHealth);
        stats.putDouble("pHealth",pHealth);
    }
}

class RangedAttack implements Runnable{ //Handles damage from ranged attacks
    ATBPExtension parentExt;
    Room room;
    int damage;
    String target;
    Tower tower;
    String attacker;
    Minion minion;
    Base base;

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, String target){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = target;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Tower tower){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = tower.getId();
        this.tower = tower;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Minion m){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = m.getId();
        this.minion = m;
        this.attacker = attacker;
    }

    RangedAttack(ATBPExtension parentExt, Room room, int damage, String attacker, Base b){
        this.parentExt = parentExt;
        this.room = room;
        this.damage = damage;
        this.target = b.getId();
        this.base = b;
        this.attacker = attacker;
    }
    @Override
    public void run() {
        if(target.contains("tower") && tower != null){
            Champion.attackTower(parentExt,room,attacker,tower,damage);
        }else if(target.contains("creep") && minion != null){
            Champion.attackMinion(parentExt,attacker,minion,damage);
        }else if(target.contains("base") && base != null){
            Champion.attackBase(parentExt,room,attacker,base,damage);
        }else{
            User p = room.getUserById(Integer.parseInt(target));
            for(User u : room.getUserList()){
                Champion.attackChampion(parentExt,u,p,damage);
            }
        }
    }
}

class BuffHandler implements Runnable {

    User u;
    String buff;
    double value;
    boolean icon;

    BuffHandler(User u, String buff, double value, boolean icon){
        this.u = u;
        this.buff = buff;
        this.value = value;
        this.icon = icon;
    }

    @Override
    public void run() {
        double currentStat = u.getVariable("stats").getSFSObjectValue().getDouble(buff);
        u.getVariable("stats").getSFSObjectValue().putDouble(buff,currentStat-value);
        if(icon){

        }
    }
}

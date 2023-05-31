package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Point2D;

//TODO: Add tower fighting back
public class Tower extends Actor{
    private final int[] PURPLE_TOWER_NUM = {2,1,0};
    private final int[] BLUE_TOWER_NUM = {5,4,3};
    private long lastHit;
    private boolean destroyed = false;

    public Tower(ATBPExtension parentExt, Room room, String id, int team, Point2D location){
        this.currentHealth = 800;
        this.maxHealth = 800;
        this.location = location;
        this.id = id;
        this.room = room;
        this.team = team;
        this.parentExt = parentExt;
        this.lastHit = 0;
    }

    @Override
    public boolean damaged(Actor a, int damage) {
        this.currentHealth-=damage;
        boolean notify = System.currentTimeMillis()-this.lastHit >= 1000*5;
        for(User u : room.getUserList()){
            if(notify) ExtensionCommands.towerAttacked(parentExt,u,this.getTowerNum());
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", this.id);
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", this.getPHealth());
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        if(this.currentHealth <= 0) this.die(a);
        if(notify) this.triggerNotification();
        return false;
    }

    @Override
    public void attack(Actor a) {

    }

    @Override
    public void die(Actor a) {
        for(User u : room.getUserList()){
            ExtensionCommands.towerDown(parentExt,u, this.getTowerNum());
            ExtensionCommands.knockOutActor(parentExt,u,this.id,a.getId(),100);
            if(!this.isDestroyed()){
                this.destroy();
                ExtensionCommands.destroyActor(parentExt,u,this.id);
            }
            String actorId = "tower2a";
            if(this.getTowerNum() == 0 || this.getTowerNum() == 3 ){
                actorId = "tower1a";
            }
            ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),actorId,this.id+"_destroyed",1000*60*15,(float)this.location.getX(),(float)this.location.getY(),false,this.team,0f);
            ExtensionCommands.createWorldFX(parentExt,u,String.valueOf(u.getId()),"tower_destroyed_explosion",this.id+"_destroyed_explosion",1000,(float)this.location.getX(),(float)this.location.getY(),false,this.team,0f);
            this.parentExt.getRoomHandler(this.room.getId()).addScore(a.getTeam(),50);
        }
        if(this.getTowerNum() == 0 || this.getTowerNum() == 3) parentExt.getRoomHandler(room.getId()).getOpposingTeamBase(this.team).unlock();
    }

    @Override
    public void update(int msRan) {

    }

    public String getId(){
        return this.id;
    }
    public int getTowerNum(){ //Gets tower number for the client to process correctly
        /*
        0 - Purple Base Tower
        1 - Purple Bot Tower
        2 - Purple Top Tower
        3 - Blue Base Tower
        4 - Blue Bot Tower
        5 - Blue Top Tower
         */
        String[] towerIdComponents = this.id.split("_");
        if(towerIdComponents[0].equalsIgnoreCase("blue")){
            return BLUE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
        }else{
            return PURPLE_TOWER_NUM[Integer.parseInt(towerIdComponents[1].replace("tower",""))-1];
        }
    }

    public void triggerNotification(){ //Resets the hit timer so players aren't spammed by the tower being attacked
        this.lastHit = System.currentTimeMillis();
    }

    public long getLastHit(){
        return this.lastHit;
    }

    public void destroy(){
        this.destroyed = true;
    }

    public boolean isDestroyed(){
        return this.destroyed;
    }
}

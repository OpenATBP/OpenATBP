package xyz.openatbp.extension.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;

import java.awt.geom.Point2D;

public class Base extends Actor{
    private boolean unlocked = false;

    public Base(ATBPExtension parentExt, Room room, int team){
        this.currentHealth = 3500;
        this.maxHealth = 3500;
        this.team = team;
        if(team == 0){
            id = "base_purple";
            location = new Point2D.Float(MapData.L2_BASE1_X*-1,0f);
        }
        else{
            id = "base_blue";
            location = new Point2D.Float(MapData.L2_BASE1_X,0f);
        }
        this.parentExt = parentExt;
        this.room = room;
    }

    public int getTeam(){
        return this.team;
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        this.currentHealth-=damage;
        for(User u : room.getUserList()){
            if(this.currentHealth <= 0){
                double oppositeTeam = 0;
                if(this.team == 0) oppositeTeam = 1;
                try{
                    ExtensionCommands.gameOver(parentExt,u,oppositeTeam);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", this.id);
            updateData.putInt("currentHealth", (int) currentHealth);
            updateData.putDouble("pHealth", getPHealth());
            updateData.putInt("maxHealth", (int) maxHealth);
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        if(currentHealth > 0) return false;
        else return true;
    }

    @Override
    public void attack(Actor a) {
        System.out.println("Bases can't attack silly!");
    }

    @Override
    public void die(Actor a) {

    }

    @Override
    public void update(int msRan) {

    }

    public void unlock(){
        unlocked = true;
    }

    public boolean isUnlocked(){
        return this.unlocked;
    }
}

package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;

import java.awt.geom.Point2D;

public class Base extends Actor {
    private boolean unlocked = false;
    private long lastHit = -1;

    public Base(ATBPExtension parentExt, Room room, int team){
        this.currentHealth = 3500;
        this.maxHealth = 3500;
        this.team = team;
        if(room.getGroupId().equalsIgnoreCase("practice")){
            if(team == 0) this.location = new Point2D.Float(MapData.L1_PURPLE_BASE[0], MapData.L1_PURPLE_BASE[1]);
            else this.location = new Point2D.Float(MapData.L1_BLUE_BASE[0], MapData.L1_BLUE_BASE[1]);
        } else {
            if(team == 0) this.location = new Point2D.Float(MapData.L2_PURPLE_BASE[0], MapData.L2_PURPLE_BASE[1]);
            else this.location = new Point2D.Float(MapData.L2_BLUE_BASE[0], MapData.L2_BLUE_BASE[1]);
        }
        if(team == 0) this.id = "base_purple";
        else this.id = "base_blue";
        this.parentExt = parentExt;
        this.avatar = id;
        this.actorType = ActorType.BASE;
        this.room = room;
        this.stats = this.initializeStats();
        ExtensionCommands.updateActorState(parentExt,room,this.id, ActorState.INVINCIBLE,true);
        ExtensionCommands.updateActorState(parentExt,room,this.id, ActorState.IMMUNITY,true);
    }

    public int getTeam(){
        return this.team;
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {

    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(!this.unlocked) return false;
        this.currentHealth-=getMitigatedDamage(damage,AttackType.PHYSICAL,a);
        if(System.currentTimeMillis() - this.lastHit >= 15000){
            this.lastHit = System.currentTimeMillis();
            for(UserActor ua : parentExt.getRoomHandler(room.getId()).getPlayers()){ //TODO: Playing for enemy team?
                if(ua.getTeam() == this.team) ExtensionCommands.playSound(parentExt, ua.getUser(), "global", "announcer/base_under_attack",new Point2D.Float(0,0));
            }
        }
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) currentHealth);
        updateData.putDouble("pHealth", getPHealth());
        updateData.putInt("maxHealth", (int) maxHealth);
        ExtensionCommands.updateActorData(parentExt,this.room,updateData);
        return !(currentHealth > 0);
    }

    @Override
    public void attack(Actor a) {
        System.out.println("Bases can't attack silly!");
    }

    @Override
    public void die(Actor a) {
        if(this.currentHealth <= 0){
            try{
                int oppositeTeam = 0;
                if(this.team == 0) oppositeTeam = 1;
                parentExt.getRoomHandler(this.room.getId()).gameOver(oppositeTeam);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
    }

    @Override
    public void setTarget(Actor a) {

    }

    public void unlock(){
        unlocked = true;
        ExtensionCommands.updateActorState(parentExt,room,this.id, ActorState.INVINCIBLE,false);
        ExtensionCommands.updateActorState(parentExt,room,this.id, ActorState.IMMUNITY,false);
    }

    public boolean isUnlocked(){
        return this.unlocked;
    }
}

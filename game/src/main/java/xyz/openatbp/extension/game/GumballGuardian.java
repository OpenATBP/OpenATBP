package xyz.openatbp.extension.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class GumballGuardian extends Tower{ //TODO: Last left off - test the guardians

    public GumballGuardian(ATBPExtension parentExt, Room room, String id, int team, Point2D location){
        super(parentExt,room,id,team,location);
        System.out.println("Invalid constructor used!");
        this.avatar = "gumball_guardian";
        this.displayName = "Gumball Guardian";
        this.team = team;
        this.parentExt = parentExt;
        this.room = room;
        this.id = "gumball_guardian"+team;
        this.currentHealth = 99999;
        this.maxHealth = 99999;
    }

    public GumballGuardian(ATBPExtension parentExt, Room room, int team){
        super(parentExt,room,team);
        this.avatar = "gumball_guardian";
        this.displayName = "Gumball Guardian";
        this.id = "gumball_guardian"+team;
        this.currentHealth = 99999;
        this.maxHealth = 99999;
        if(team == 1) this.location = new Point2D.Float(MapData.L2_GUARDIAN1_X,MapData.L2_GUARDIAN1_Z);
        else this.location = new Point2D.Float(MapData.L2_GUARDIAN1_X*-1,MapData.L2_GUARDIAN1_Z);
        this.stats = this.initializeStats();
    }

    @Override
    public void attack(Actor a) {
        this.attackCooldown = 800;
        for(User u : this.room.getUserList()){
            String projectileName = "gumballGuardian_projectile";
            ExtensionCommands.createProjectileFX(this.parentExt,u,projectileName,this.id,a.getId(),"emitNode","Bip01",0.6f);
        }
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(this.parentExt,this,a,1000,"basicAttack"),600, TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a) {
        System.out.println(a.getDisplayName() + " somehow killed a guardian!");
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData){
        return false;
    }

}

package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.Actor;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Lich extends UserActor{

    private Skully skully;
    private long lastSkullySpawn;
    private boolean qActivated = false;
    private List<Point2D> slimePath = null;

    public Lich(User u, ATBPExtension parentExt){
        super(u,parentExt);
        lastSkullySpawn = 0;
    }

    @Override
    public void useAbility(int ability, ISFSObject abilityData){
        if(skully == null && System.currentTimeMillis()-lastSkullySpawn > 40000){
            this.spawnSkully();
        }
        switch(ability){
            case 1: //Q
                double statIncrease = this.speed * 0.25;
                this.giveStatBuff("speed",statIncrease,6000);
                qActivated = true;
                slimePath = new ArrayList<>();
                ExtensionCommands.createActorFX(parentExt,room,id,"lichking_deathmist",6000,"lich_trail",true, "",true,false,team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new TrailHandler(),6000,TimeUnit.MILLISECONDS);
                break;
            case 2: //W
                break;
            case 3: //E
                break;
            case 4: //Passive
                break;
        }
    }

    @Override
    public void attack(Actor a){
        super.attack(a);
        currentAutoAttack = SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a,new Champion.DelayedAttack(this,a,50),"lich_projectile"),500, TimeUnit.MILLISECONDS);
    }

    private void spawnSkully(){
        skully = new Skully();
        lastSkullySpawn = System.currentTimeMillis();
    }

    private class TrailHandler implements Runnable {
        @Override
        public void run() {
            qActivated = false;
            slimePath = null;
        }
    }

    private class Skully extends Actor {

        Skully(){
            this.room = Lich.this.room;
            this.parentExt = Lich.this.parentExt;
            this.currentHealth = 500;
            this.maxHealth = 500;
            this.location = Lich.this.location;
            this.avatar = "skully";
            this.id = "skully_"+Lich.this.id;
            this.team = Lich.this.team;
            ExtensionCommands.createActor(parentExt,player,this.id,this.avatar,this.location,0f,this.team);
        }

        @Override
        public boolean damaged(Actor a, int damage) {
            return false;
        }

        @Override
        public void attack(Actor a) {

        }

        @Override
        public void die(Actor a) {

        }
    }
}

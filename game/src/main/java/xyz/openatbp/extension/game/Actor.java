package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Point2D;

public abstract class Actor {
    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected String id;
    protected Room room;
    protected int team;
    protected double speed;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected double attackSpeed;
    protected double attackCooldown;
    protected ActorType actorType;
    protected double attackRange;

    public double getPHealth(){
        return currentHealth/maxHealth;
    }

    public int getHealth(){
        return (int) currentHealth;
    }

    public int getMaxHealth(){
        return (int) maxHealth;
    }

    public Point2D getLocation(){
        return this.location;
    }

    public String getId(){return this.id;}
    public int getTeam(){return this.team;}

    public void setLocation(Point2D location){
        this.location = location;
    }
    public String getAvatar(){return this.avatar;}
    public ActorType getActorType(){return this.actorType;}
    public void reduceAttackCooldown(){
        this.attackCooldown-=100;
    }

    public double getAttackCooldown(){
        return this.attackCooldown;
    }

    public boolean withinRange(Actor a){
        return a.getLocation().distance(this.location) <= this.attackRange;
    }

    public void stopMoving(){
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,this.location, (float) this.speed, false);
        }
    }

    public abstract boolean damaged(Actor a, int damage);
    public abstract void attack(Actor a);
    public abstract void die(Actor a);

    public Room getRoom(){
        return this.room;
    }
}

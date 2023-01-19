package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;

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

    public abstract Room getRoom();
}

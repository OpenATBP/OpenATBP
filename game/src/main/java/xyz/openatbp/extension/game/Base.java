package xyz.openatbp.extension.game;

import xyz.openatbp.extension.MapData;

import java.awt.geom.Point2D;

public class Base {

    private int team;
    private String id;
    private Point2D location;
    private int health = 2500;
    public static final int MAX_HEALTH = 2500;
    private boolean unlocked = false;

    public Base(int team){
        this.team = team;
        if(team == 0){
            id = "base_purple";
            location = new Point2D.Float(MapData.L2_BASE1_X*-1,0f);
        }
        else{
            id = "base_blue";
            location = new Point2D.Float(MapData.L2_BASE1_X,0f);
        }
    }

    public int getTeam(){
        return this.team;
    }

    public int getHealth(){
        return this.health;
    }

    public Point2D getLocation(){
        return this.location;
    }

    public String getId(){
        return this.id;
    }

    public void unlock(){
        unlocked = true;
    }

    public boolean isUnlocked(){
        return this.unlocked;
    }

    public boolean damage(int damage){
        this.health-=damage;
        return this.health<=0;
    }
}

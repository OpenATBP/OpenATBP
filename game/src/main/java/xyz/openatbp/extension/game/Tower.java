package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;

public class Tower {
    private int health = 800;
    private Point2D location;
    private int team;
    private String id;

    public Tower(String id, int team, Point2D location){
        this.id = id;
        this.team = team;
        this.location = location;
    }

    public int getHealth(){
        return this.health;
    }

    public Point2D getLocation(){
        return this.location;
    }
    public int getTeam(){
        return this.team;
    }

    public String getId(){
        return this.id;
    }
}

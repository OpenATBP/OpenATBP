package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;

//TODO: Add tower fighting back
public class Tower {
    private int health = 800;
    private int maxHealth = 800;
    private Point2D location;
    private int team;
    private String id;
    private final int[] PURPLE_TOWER_NUM = {2,1,0};
    private final int[] BLUE_TOWER_NUM = {5,4,3};
    private long lastHit;

    public Tower(String id, int team, Point2D location){
        this.id = id;
        this.team = team;
        this.location = location;
        this.lastHit = 0;
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
    public boolean damage(int damage){ //Reduces tower health and returns true if it is destroyed
        this.health-=damage;
        return this.health<=0;
    }

    public void triggerNotification(){ //Resets the hit timer so players aren't spammed by the tower being attacked
        this.lastHit = System.currentTimeMillis();
    }

    public long getLastHit(){
        return this.lastHit;
    }

    public int getMaxHealth(){
        return this.maxHealth;
    }
}

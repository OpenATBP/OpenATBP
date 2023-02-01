package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Point2D;

public abstract class NPC {

    Team team;
    String id;
    Point2D location;
    int health;
    public int MAX_HEALTH;


    public static void attackNPC(ATBPExtension parentExt, NPC target, int damage) { //Used for ranged attacks

    }

    public abstract boolean takeDamage(ATBPExtension parentExt, String attacker, int damage);
    public abstract void handleDeath();

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public Point2D getLocation() {
        return location;
    }

    public void setLocation(Point2D location) {
        this.location = location;
    }

    public int getMaxHealth(){
        return MAX_HEALTH;

    }
}

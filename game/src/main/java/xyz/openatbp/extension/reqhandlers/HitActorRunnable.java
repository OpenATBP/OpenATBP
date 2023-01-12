package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

public class HitActorRunnable implements Runnable {

    User user;
    ATBPExtension parentExt;
    String userId;
    String targetId;
    String fxName;
    String emitLocation;
    String hitLocation;
    float projectileSpeed;

    public HitActorRunnable(User user, ATBPExtension parentExt, String userId, String targetId, String fxName, String emitLocation, String hitLocation, float projectileSpeed) {
        this.user = user;
        this.parentExt = parentExt;
        this.userId = userId;
        this.targetId = targetId;
        this.fxName = fxName;
        this.emitLocation = emitLocation;
        this.hitLocation = hitLocation;
        this.projectileSpeed = projectileSpeed;
    }

    @Override
    public void run() {
        ISFSObject CreateProjectileFxData = new SFSObject();
        CreateProjectileFxData.putUtfString("name", this.fxName); //fx name
        CreateProjectileFxData.putUtfString("attacker", this.userId); //attacker id
        CreateProjectileFxData.putUtfString("target", this.targetId); //target id
        CreateProjectileFxData.putUtfString("emit", this.emitLocation); //where the projectile comes from
        CreateProjectileFxData.putUtfString("hit",  this.hitLocation); //where the projectile stops (?)
        CreateProjectileFxData.putFloat("time", this.projectileSpeed);

        GameManager.sendAllUsers(parentExt, CreateProjectileFxData,"cmd_create_projectile_fx", this.user.getLastJoinedRoom());
    }
}

package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.annotations.MultiHandler;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import com.smartfoxserver.v2.extensions.SFSExtension;
import com.smartfoxserver.v2.util.TaskScheduler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.GameManager;

import java.util.concurrent.TimeUnit;

@MultiHandler
public class HitActorHandler extends BaseClientRequestHandler {
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        trace(params.getDump());
        double attackRange = sender.getVariable("stats").getSFSObjectValue().getDouble("attackRange");
        float userX = params.getFloat("x");
        float userY = params.getFloat("y");
        float userZ = params.getFloat("z");
        float targetX = -1.5f; //TODO: get position from target
        float targetZ = -11f;

        if (!inAttackRange(userX, userZ, targetX, targetZ, attackRange)) return;

        long timeSinceBasicAttack = sender.getVariable("stats").getSFSObjectValue().getLong("timeSinceBasicAttack");
        double attackSpeed = sender.getVariable("stats").getSFSObjectValue().getDouble("attackSpeed"); //cast to float?

        //check if attackSpeed timer has passed
        if ((System.currentTimeMillis() - timeSinceBasicAttack) < attackSpeed) return;

        float projectileSpeed = 1f;
        int projectileAppearTimeMs = 500; //when projectile should appear during the auto attack animation;
        String userAvatar = sender.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        //TODO: make definitions easier to get (helper class)
        boolean isRanged = parentExt.getDefintion(userAvatar).get("MonoBehaviours").get("ActorData").get("attackType").textValue().equals("RANGED");
        String userId = String.valueOf(sender.getId());
        String attackString = params.getUtfString("attackString"); //seems hardcoded to "attack1,0" atleast for autos
        String targetId = params.getUtfString("target_id");


        //update auto attack timer
        ISFSObject updateData = sender.getVariable("stats").getSFSObjectValue(); //TODO: take this out of stats variable
        updateData.putLong("timeSinceBasicAttack", System.currentTimeMillis());
        parentExt.send("cmd_update_actor_data", updateData, sender);

        //stops actor when starting to auto attack
        ISFSObject data = new SFSObject();
        data.putUtfString("i", String.valueOf(sender.getId()));
        data.putFloat("px", userX);
        data.putFloat("pz", userZ);
        data.putFloat("dx", userX);
        data.putFloat("dz", userZ);
        data.putBool("o", false);
        data.putFloat("s", 2.85f);
        GameManager.sendAllUsers(parentExt, data, "cmd_move_actor", sender.getLastJoinedRoom());


        //does animation
        ISFSObject attackActorData = new SFSObject();
        attackActorData.putUtfString("id", userId);
        attackActorData.putUtfString("target_id", targetId);
        attackActorData.putFloat("dest_x", userX); //seems unused
        attackActorData.putFloat("dest_y", userY);//seems unused
        attackActorData.putFloat("dest_z", userZ);//seems unused
        attackActorData.putUtfString("attack_type", attackString); //unused and hard coded
        attackActorData.putBool("crit", false);
        attackActorData.putBool("orient", true);
        GameManager.sendAllUsers(parentExt, attackActorData, "cmd_attack_actor", sender.getLastJoinedRoom());


        if (isRanged) {
            //TODO: make definitions easier to get (helper class)
            String projectileFx = parentExt.getDefintion(userAvatar).get("MonoBehaviours").get("ActorData").get("scriptData").get("projectileAsset").textValue();
            TaskScheduler scheduler = SmartFoxServer.getInstance().getTaskScheduler();
            scheduler.schedule(
                    new HitActorRunnable(sender, parentExt, userId, targetId, projectileFx, "Bip001", "", projectileSpeed),
                    projectileAppearTimeMs,
                    TimeUnit.MILLISECONDS);
        }else{
            trace("Don't work");
        }
    }

    private boolean inAttackRange(float userX, float userZ, float targetX, float targetZ, double attackRange) {
        return distanceFromTarget(userX, userZ, targetX, targetZ) <= attackRange;
    }

    private float distanceFromTarget(float userX, float userZ, float targetX, float targetZ) {
        return (float) Math.sqrt(Math.pow(targetZ - userZ, 2) + Math.pow(targetX - userX, 2));
    }
}

class HitActorRunnable implements Runnable {

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
        System.out.println("Running!");
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

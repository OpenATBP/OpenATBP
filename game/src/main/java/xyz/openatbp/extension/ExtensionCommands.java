package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import java.awt.geom.Point2D;

public class ExtensionCommands {
    /**
        @param id - ID of the actor
        @param bundle - Name of the asset bundle containing the effect
        @param duration - Duration of the effect in milliseconds
        @param fxId - ID of the specific effect (similar to a player id). This does not need to be pre-exisiting.
        @param parent - Is the effect attached to the actor?
        @param emit - ID of location effect is coming from (actorID would spawn where actor is).
        @param orient - Do you want the effect to be in the same direction as the actor?
        @param highlight - Should the effect be highlighted based on the team of the actor?
        @param team - Int value of the team such as blue(1) or purple(0)
     */
    public static void createActorFX(ATBPExtension parentExt, User u, String id, String bundle, int duration, String fxId, boolean parent, String emit, boolean orient, boolean highlight, int team){
        System.out.println("Creating actor FX!");
        ISFSObject data2 = new SFSObject();
        data2.putUtfString("id",id);
        data2.putUtfString("bundle",bundle);
        data2.putInt("duration",duration);
        data2.putUtfString("fx_id",fxId);
        data2.putBool("parent",parent);
        data2.putUtfString("emit",emit);
        data2.putBool("orient",orient);
        data2.putBool("highlight",highlight);
        data2.putInt("team",team);
        parentExt.send("cmd_create_actor_fx",data2,u);
    }
    public static void updateActorData(ATBPExtension parentExt, User u, ISFSObject data){
        System.out.println(data.getDump());
        parentExt.send("cmd_update_actor_data",data,u);
    }

    /**
     *
     * @param id - ID of who is moving
     * @param p - Point2D object of where the actor is
     * @param d - Point2D object of where the actor is going
     * @param speed - Movement speed of actor
     * @param orient - Should the actor face in the direction of their path?
     */
    public static void moveActor(ATBPExtension parentExt, User u, String id, Point2D p, Point2D d, float speed, boolean orient){
        System.out.println("Moving from " + p.getX() + "," + p.getY() + " to " + d.getX() + "," + d.getY());
        ISFSObject data = new SFSObject();
        data.putUtfString("i",id);
        data.putFloat("px",(float)p.getX());
        data.putFloat("pz",(float)p.getY());
        data.putFloat("dx",(float) d.getX());
        data.putFloat("dz",(float) d.getY());
        data.putFloat("s",speed);
        data.putBool("o",orient);
        parentExt.send("cmd_move_actor",data,u);
    }

    /**
     *
     * @param id - String ID of actor being spawned
     * @param actor - Name of actor/avatar asset for the new actor
     * @param spawn - Point2D object of spawn point
     * @param rotation - Rotation float for how it should be facing
     * @param team - Team int value
     */
    public static void createActor(ATBPExtension parentExt, User u, String id, String actor, Point2D spawn, float rotation, int team){
        ISFSObject data = new SFSObject();
        data.putUtfString("id",id);
        data.putUtfString("actor",actor);
        ISFSObject spawnPoint = new SFSObject();
        spawnPoint.putFloat("x", (float) spawn.getX());
        spawnPoint.putFloat("y",0f);
        spawnPoint.putFloat("z", (float) spawn.getY());
        data.putSFSObject("spawn_point",spawnPoint);
        data.putFloat("rotation",rotation);
        data.putInt("team",team);
        parentExt.send("cmd_create_actor",data,u);
    }

    public static void createActor(ATBPExtension parentExt, User u, ISFSObject data){
        parentExt.send("cmd_create_actor",data,u);
    }
}

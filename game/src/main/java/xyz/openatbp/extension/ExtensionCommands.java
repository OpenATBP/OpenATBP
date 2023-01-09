package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

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
}

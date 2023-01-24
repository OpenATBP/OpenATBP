package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Buff;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FlamePrincess extends UserActor{

    private boolean ultFinished = false;
    private boolean ultStarted = false;
    private int ultUses = 3;

    public FlamePrincess(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void useAbility(int ability, ISFSObject abilityData){
        switch(ability){
            case 1: //Q
                break;
            case 2: //W
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"fx_target_ring_2","flame_w",1000, abilityData.getFloat("x"), abilityData.getFloat("z"),true,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"flame_princess_polymorph_fireball","flame_w_polymorph",1000, abilityData.getFloat("x"),abilityData.getFloat("z"),false,this.team,0f);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new AbilityRunnable(ability,abilityData), 500, TimeUnit.MILLISECONDS);
                break;
            case 3: //E
                if(!ultStarted && ultUses == 3){
                    int duration = Champion.getSpellData(parentExt,this.avatar,ability).get("spellDuration").asInt();
                    ultStarted = true;
                    ultFinished = false;
                    this.setState(ActorState.TRANSFORMED, true);
                    ExtensionCommands.playSound(this.parentExt,this.player,"vo/vo_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.playSound(this.parentExt,this.player,"sfx_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.swapActorAsset(this.parentExt,this.player,this.id,"flame_ult");
                    ExtensionCommands.createActorFX(this.parentExt,this.player.getLastJoinedRoom(),this.id,"flame_princess_ultimate_aoe",5000,"flame_e",true,"",true,false,this.team);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new AbilityRunnable(ability),duration, TimeUnit.MILLISECONDS);
                }else{
                    if(ultUses>0){
                        ultUses--;
                        Point2D dest = new Point2D.Float(abilityData.getFloat("x"),abilityData.getFloat("z"));
                        ExtensionCommands.moveActor(parentExt,player,id,getLocation(),Champion.getDashPoint(parentExt,this,dest),20f,true);
                        double time = dest.distance(getLocation())/20f;
                        this.canMove = false;
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        if(ultUses == 0){
                            System.out.println("Time: " + time);
                            SmartFoxServer.getInstance().getTaskScheduler().schedule(new AbilityRunnable(ability),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        }
                        setLocation(dest);
                    }
                }
                break;
            case 4: //Passive
                break;
        }
    }

    private String getAbilityString(int ability){
        switch(ability){
            case 1:
                return "q";
            case 2:
                return "w";
            case 3:
                return "e";
            default:
                return "passive";
        }
    }

    @Override
    public void setCanMove(boolean canMove){
        if(ultStarted && !canMove) this.canMove = true;
        else this.canMove = canMove;
    }

    private class AbilityRunnable implements Runnable {

        int ability;
        ISFSObject abilityData;

        AbilityRunnable(int ability){
            this.ability = ability;
        }
        AbilityRunnable(int ability, ISFSObject abilityData){
            this.ability = ability;
            this.abilityData = abilityData;
        }

        @Override
        public void run() {
            switch(ability){
                case 1:
                    break;
                case 2:
                    RoomHandler roomHandler = parentExt.getRoomHandler(player.getLastJoinedRoom().getId());
                    Point2D center = new Point2D.Float(this.abilityData.getFloat("x"),this.abilityData.getFloat("z"));
                    List<UserActor> affectedUsers = Champion.getUsersInRadius(roomHandler,center,2);
                    for(UserActor u : affectedUsers){
                        System.out.println("Hit: " + u.getAvatar());
                        u.setState(ActorState.POLYMORPH, true);
                        Champion.attackChampion(parentExt, player, u.getUser(), id, 50);
                        Champion.giveBuff(parentExt,u.getUser(), Buff.POLYMORPH);
                    }
                    System.out.println("Ability done!");
                    break;
                case 3:
                    if(!ultFinished && ultStarted){
                        System.out.println("Ending ability!");
                        JsonNode spellData = Champion.getSpellData(parentExt,avatar,ability);
                        int cooldown = spellData.get("spellCoolDown").asInt();
                        int gCooldown = spellData.get("spellGlobalCoolDown").asInt();
                        setState(ActorState.TRANSFORMED, false);
                        ExtensionCommands.removeFx(parentExt,player,"flame_e");
                        ExtensionCommands.swapActorAsset(parentExt,player,id,avatar);
                        ExtensionCommands.actorAbilityResponse(parentExt,player,getAbilityString(ability),true,cooldown,gCooldown);
                        ultStarted = false;
                        ultFinished = true;
                        ultUses = 3;
                    }else if(ultFinished){
                        System.out.println("Ability already ended!");
                        ultStarted = false;
                        ultFinished = false;
                        ultUses = 3;
                    }
                    break;
                case 4:
                    break;
            }
        }
    }
}

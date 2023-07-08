package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Keeoth extends Monster {

    private int abilityCooldown;
    private boolean usingAbility;

    public Keeoth(ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName) {
        super(parentExt, room, startingLocation, monsterName);
        this.abilityCooldown = 3000;
        this.usingAbility = false;
    }
    @Override
    public void update(int msRan){
        super.update(msRan);
        if(!this.usingAbility && this.abilityCooldown > 0) this.abilityCooldown-=100;
    }

    @Override
    public void die(Actor a){
        if(!this.dead && a.getActorType() == ActorType.PLAYER){
            UserActor ua = (UserActor) a;
            for(UserActor u : parentExt.getRoomHandler(this.room.getId()).getPlayers()){
                if(u.getTeam() == ua.getTeam()){
                    u.handleEffect("lifeSteal",35,60000,"jungle_buff_keeoth");
                    u.handleEffect("spellVamp",40,60000,"jungle_buff_keeoth");
                    u.handleEffect("criticalChance",35,60000,"jungle_buff_keeoth");
                    double healthChange = (double)u.getHealth() * 0.3d;
                    u.changeHealth((int)healthChange);
                }
            }
        }
        super.die(a);
    }

    @Override
    public void attack(Actor a){

        if(!this.usingAbility && this.abilityCooldown <= 0){
            this.usingAbility = true;
            this.stopMoving();
            this.canMove = false;
            Point2D playerLoc = a.getLocation();
            ExtensionCommands.createWorldFX(parentExt,room,id,"fx_target_ring_2",id+"_ring",1250,(float)playerLoc.getX(),(float)playerLoc.getY(),true,team,0f);
            ExtensionCommands.createWorldFX(parentExt,room,id+"2","fx_target_ring_1",id+"_ring2",1250,(float)playerLoc.getX(),(float)playerLoc.getY(),true,team,0f);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell",1250,false);
            Runnable keeothSpecial = () -> {
                ExtensionCommands.createWorldFX(parentExt,room,id,"keeoth_explosion",id+"_explosion",1000,(float) playerLoc.getX(), (float)playerLoc.getY(),true,team,0f);
                usingAbility = false;
                canMove = true;
                abilityCooldown = 1100;
                Runnable specialDamage = () -> {
                    try{
                        abilityCooldown = 3000;
                        JsonNode attackData = parentExt.getAttackData(this.avatar,"basicAttack");
                        ObjectMapper mapper = new ObjectMapper();
                        ISFSObject data = new SFSObject();
                        data.putUtfString("attackName",attackData.get("specialAttackName").asText());
                        data.putUtfString("attackDescription",attackData.get("specialAttackDescription").asText());
                        data.putUtfString("attackIconImage",attackData.get("specialAttackIconImage").asText());
                        data.putUtfString("attackType","spell");
                        JsonNode newAttackData = mapper.readTree(data.toJson());

                        for(Actor actor : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),playerLoc,2.5f)){
                            if(actor.getActorType() == ActorType.PLAYER){
                                double dist = actor.getLocation().distance(playerLoc);
                                if(dist > 1) actor.damaged(Keeoth.this,150,newAttackData);
                                else actor.damaged(Keeoth.this,450,newAttackData);
                            }
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                };
                SmartFoxServer.getInstance().getTaskScheduler().schedule(specialDamage,1000,TimeUnit.MILLISECONDS);
            };
            SmartFoxServer.getInstance().getTaskScheduler().schedule(keeothSpecial,1250, TimeUnit.MILLISECONDS);
        }
        else if(!this.usingAbility){
            super.attack(a);
        }
    }

    @Override
    public boolean canAttack(){
        if(this.usingAbility) return false;
        return super.canAttack();
    }

    @Override
    public boolean withinRange(Actor a){
        if(this.abilityCooldown == 0) return a.getLocation().distance(this.location) <= 5;
        return super.withinRange(a);
    }
}

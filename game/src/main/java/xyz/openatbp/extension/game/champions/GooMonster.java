package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GooMonster extends Monster {

    private int abilityCooldown;
    private boolean usingAbility;
    private Point2D puddleLocation;
    private boolean puddleActivated;
    private long puddleStarted;

    public GooMonster(ATBPExtension parentExt, Room room, float[] startingLocation, String monsterName) {
        super(parentExt, room, startingLocation, monsterName);
        this.abilityCooldown = 5000;
        this.usingAbility = false;
        this.puddleActivated = false;
        this.puddleStarted = -1;
        this.puddleLocation = null;
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(!this.usingAbility && this.abilityCooldown > 0) this.abilityCooldown-=100;
        if(this.puddleActivated){
            if(System.currentTimeMillis() - puddleStarted >= 3000){
                puddleActivated = false;
                puddleStarted = -1;
                puddleLocation = null;
            }else{
                try{
                    List<Actor> damagedActors = Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),puddleLocation,2f);
                    JsonNode attackData = parentExt.getAttackData(this.avatar,"basicAttack");
                    ObjectMapper mapper = new ObjectMapper();
                    ISFSObject data = new SFSObject();
                    data.putUtfString("attackName",attackData.get("specialAttackName").asText());
                    data.putUtfString("attackDescription",attackData.get("specialAttackDescription").asText());
                    data.putUtfString("attackIconImage",attackData.get("specialAttackIconImage").asText());
                    data.putUtfString("attackType","spell");
                    JsonNode newAttackData = mapper.readTree(data.toJson());
                    for(Actor a : damagedActors){
                        if(!a.getId().equalsIgnoreCase(this.id)){
                            a.addState(ActorState.SLOWED, 0.25d, 1000, null, false);
                            a.addToDamageQueue(this,4,newAttackData);
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void die(Actor a){
        if(!this.dead && (a.getActorType() == ActorType.PLAYER || a.getActorType() == ActorType.COMPANION)){
            for(UserActor u : parentExt.getRoomHandler(this.room.getId()).getPlayers()){
                if(u.getTeam() == a.getTeam()){
                    u.addEffect("speed",u.getPlayerStat("speed")*0.1d,60000,"jungle_buff_goo",false);
                    Champion.handleStatusIcon(this.parentExt,u,"icon_buff_goomonster","goomonster_buff_desc",60000f);
                    ExtensionCommands.playSound(parentExt,u.getUser(),"global","announcer/you_goomonster");
                }else{
                    ExtensionCommands.playSound(parentExt,u.getUser(),"global","announcer/enemy_goomonster");
                }
            }
        }
        super.die(a);
    }

    @Override
    public void attack(Actor a){
        this.stopMoving();
        this.canMove = false;
        if(!this.usingAbility && this.abilityCooldown <= 0){
            this.usingAbility = true;
            ExtensionCommands.playSound(parentExt,room,id,"sfx/sfx_goo_monster_growl",this.location);
            ExtensionCommands.createActorFX(parentExt,room,id,"goo_monster_spell_glob",1250,id+"_glob",true,"fxNode",true,false,team);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell",1250,false);
            Runnable oozeAttack = () -> {
                puddleActivated = true;
                puddleLocation = a.getLocation();
                abilityCooldown = 8000;
                usingAbility = false;
                canMove = true;
                puddleStarted = System.currentTimeMillis();
                ExtensionCommands.playSound(parentExt,room,"","sfx/sfx_goo_monster_puddle",puddleLocation);
                ExtensionCommands.createWorldFX(parentExt,room,id,"goo_monster_puddle",id+"_puddle",3000,(float)puddleLocation.getX(),(float)puddleLocation.getY(),false,team,0f);
            };
            SmartFoxServer.getInstance().getTaskScheduler().schedule(oozeAttack,1250, TimeUnit.MILLISECONDS);
        }else if(!this.usingAbility){
            this.attackCooldown = 1200;
            int attackDamage = (int) this.getPlayerStat("attackDamage");
            ExtensionCommands.attackActor(parentExt,room,id,a.getId(),(float)a.getLocation().getX(),(float)a.getLocation().getY(),false,true);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedRangedAttack(this,a),1000,TimeUnit.MILLISECONDS);
        }
    }
    @Override
    public void rangedAttack(Actor a){
        this.attackCooldown = this.getPlayerStat("attackSpeed");
        int attackDamage = (int) this.getPlayerStat("attackDamage");
        float time = (float) (a.getLocation().distance(this.location) / 10f);
        ExtensionCommands.createProjectileFX(parentExt,room,"goo_projectile",this.id,a.getId(),"Bip01 R Hand", "Bip01",time);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(parentExt,this,a,attackDamage,"basicAttack"),(int)(time*1000),TimeUnit.MILLISECONDS);
    }
    @Override
    public boolean canAttack(){
        if(this.usingAbility) return false;
        return super.canAttack();
    }

    @Override
    public String getPortrait(){
        return "goomonster";
    }
}

package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class CinnamonBun extends UserActor {

    private Line2D wLine = null;
    private Point2D ultPoint = null;
    private Point2D ultPoint2 = null;
    private int ultUses = 0;
    private long ultStart = 0;

    public CinnamonBun(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(this.wLine != null){
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell2");
            double percentage = 0.2d + ((double)(this.level) * 0.01d);
            int duration = 2000 + (this.level*100);
            for(Actor a : Champion.getActorsAlongLine(this.parentExt.getRoomHandler(this.room.getId()),this.wLine,1.5d)){
                if(this.isNonStructure(a)){
                    a.addToDamageQueue(this,getSpellDamage(spellData)/10d,spellData);
                    a.addState(ActorState.SLOWED,percentage,duration,null,false);
                }
            }
        }

        if(this.ultPoint != null && System.currentTimeMillis() - this.ultStart < 4500){
            float radius = 2f;
            if(this.ultUses > 1 && this.ultPoint2 == null) radius = 4f;
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell3");
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultPoint,radius)){
                if(a.getTeam() != this.team){
                    a.addToDamageQueue(this,getSpellDamage(spellData)/10d,spellData);
                }else if(a.getId().equalsIgnoreCase(this.id)){
                    a.addEffect("attackSpeed",this.getStat("attackSpeed")*-0.2d,200,null,false);
                    a.addEffect("attackDamage",this.getStat("attackDamage")*0.2d,200,null,false);
                }
            }
        }else if(this.ultPoint != null && System.currentTimeMillis() - this.ultStart >= 4500){
            ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_cb_power3_end",this.ultPoint);
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(55000),500);
            float radius = 2f;
            if(this.ultUses > 1 && this.ultPoint2 == null){
                radius = 4f;
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_explode_big",this.id+"_bigExplosion",500,(float)this.ultPoint.getX(),(float)this.ultPoint.getY(),false,this.team,0f);
            }else{
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_sm_explode",this.id+"_smallExplosion",500,(float)this.ultPoint.getX(),(float)this.ultPoint.getY(),false,this.team,0f);
            }
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell3");
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultPoint,radius)){
                if(a.getTeam() != this.team){
                    a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                }
            }
            if(this.ultPoint2 != null){
                for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultPoint2,radius)){
                    if(a.getTeam() != this.team){
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                    }
                }
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_sm_explode",this.id+"_smallExplosion2",500,(float)this.ultPoint2.getX(),(float)this.ultPoint2.getY(),false,this.team,0f);
            }
            this.ultPoint = null;
            this.ultPoint2 = null;
            this.ultUses = 0;
            this.ultStart = 0;
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch (ability){
            case 1:
                this.canCast[0] = false;
                this.stopMoving();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_cb_power1",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"cb_lance_jab_v2",500,this.id+"_jab",true,"",true,false,this.team);
                this.changeHealth((int) ((double)(this.getMaxHealth())*0.05d));
                for(Actor a : Champion.getActorsAlongLine(this.parentExt.getRoomHandler(this.room.getId()), new Line2D.Float(this.location,dest),2f)){
                    if(this.isNonStructure(a)){
                        a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                    }
                }
                this.attackCooldown = 0;
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new CinnamonAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown, TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.canMove = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_cb_power2",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"cb_frosting_slide",5000,this.id+"_slide",false,"",true,false,this.team);
                this.changeHealth((int) ((double)(this.getMaxHealth())*0.05d));
                Point2D firstLocation = new Point2D.Double(this.location.getX(),this.location.getY());
                Point2D dashPoint = dest;
                if(this.location.distance(dashPoint) < 6.5f) dashPoint = Champion.getMaxRangeLine(new Line2D.Float(this.location,dashPoint),6.5f).getP2();
                Point2D dashEndPoint = this.dash(dashPoint,false);
                double dashTime = firstLocation.distance(dashEndPoint)/DASH_SPEED;
                int msRan = (int)(dashTime*1000d);
                this.wLine = new Line2D.Double(firstLocation,dashEndPoint);
                Runnable dashEnd = () -> this.canMove = true;
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(dashEnd,msRan,TimeUnit.MILLISECONDS);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new CinnamonAbilityHandler(ability,spellData,cooldown,gCooldown,dashEndPoint),5000,TimeUnit.MILLISECONDS);
                break;
            case 3:
                System.out.println("Ult uses: " + this.ultUses);
                System.out.println("Point2 Exists: " + (this.ultPoint2 == null));
                this.canCast[2] = false;
                this.stopMoving();
                if(this.ultUses == 0){
                    this.changeHealth((int) ((double)(this.getMaxHealth())*0.05d));
                    this.ultPoint = dest;
                    this.ultStart = System.currentTimeMillis();
                    ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_cb_power3a",dest);
                    ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_frosting_ring_sm",this.id+"_ultSmall",4500,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                }else if(this.ultUses == 1){
                    ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_cb_power3b",dest);
                    if(this.ultPoint.distance(dest) <= 2){
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSmall");
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_frosting_ring_big",this.id+"_ultBig",4500-(int)(System.currentTimeMillis()-this.ultStart),(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                    }else{
                        this.ultPoint2 = dest;
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_frosting_ring_sm",this.id+"_ultSmall2",4500-(int)(System.currentTimeMillis()-this.ultStart),(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                    }
                }else{
                    ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_cb_power3c",dest);
                    float radius = 2f;
                    if(this.ultPoint2 == null){
                        radius = 4f;
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultBig");
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_explode_big",this.id+"_bigExplosion",500,(float)this.ultPoint.getX(),(float)this.ultPoint.getY(),false,this.team,0f);
                    }else{
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSmall");
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_sm_explode",this.id+"_smallExplosion",500,(float)this.ultPoint.getX(),(float)this.ultPoint.getY(),false,this.team,0f);

                    }
                    for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultPoint,radius)){
                        if(a.getTeam() != this.team){
                            a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                        }
                    }
                    if(this.ultPoint2 != null){
                        System.out.println("Here!");
                        ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"cb_ring_sm_explode",this.id+"_smallExplosion2",500,(float)this.ultPoint2.getX(),(float)this.ultPoint2.getY(),false,this.team,0f);
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_ultSmall2");
                        for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultPoint2,radius)){
                            if(a.getTeam() != this.team){
                                a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                            }
                        }
                    }
                    this.ultPoint = null;
                    this.ultPoint2 = null;
                    this.ultStart = 0;
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                }
                if(this.ultUses < 2){
                    this.ultUses++;
                    ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,gCooldown,gCooldown);
                }
                else this.ultUses = 0;
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new CinnamonAbilityHandler(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class CinnamonAbilityHandler extends AbilityRunnable {

        public CinnamonAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            wLine = null;
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {

        }
    }
}

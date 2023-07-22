package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Base;
import xyz.openatbp.extension.game.actors.Tower;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FlamePrincess extends UserActor {

    private boolean ultFinished = false;
    private boolean passiveEnabled = false;
    private long lastPassiveUsage = 0;
    private long lastUltUsage = -1;
    private boolean ultStarted = false;
    private int ultUses = 3;

    public FlamePrincess(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.ultStarted && lastUltUsage == -1) lastUltUsage = msRan;
        if(this.ultStarted && !this.ultFinished){
            lastUltUsage = msRan;
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,2)){
                if(a.getTeam() != this.team){
                    JsonNode attackData = this.parentExt.getAttackData(getAvatar(),"spell3");
                    double damage = (double) this.getSpellDamage(attackData) / 10;
                    if(a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                        handleSpellVamp(damage);
                        a.addToDamageQueue(this,damage,attackData);
                    }
                }
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        super.useAbility(ability,spellData,cooldown,gCooldown,castDelay,dest);
        if(ultUses == 3 && !passiveEnabled && System.currentTimeMillis()-lastPassiveUsage >= 10000){
            ExtensionCommands.createActorFX(parentExt,room,id,"flame_princess_passive_flames",1000*60,"flame_passive",true,"",false,false,team);
            passiveEnabled = true;
        }
        switch(ability){
            case 1: //Q
                Line2D skillShotLine = new Line2D.Float(this.location,dest);
                Line2D maxRangeLine = Champion.getMaxRangeLine(skillShotLine,8f);
                ExtensionCommands.playSound(parentExt,room,this.id,"sfx_flame_princess_projectile_throw",this.location);
                this.fireProjectile(new FlameProjectile(this.parentExt,this,maxRangeLine,8f,0.5f,this.id+"projectile_flame_cone"),"projectile_flame_cone",dest,8f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.getUser(),"q",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                this.canCast[0] = false;
                break;
            case 2: //W
                ExtensionCommands.playSound(parentExt,room,this.id,"sfx_flame_princess_projectile_throw",this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"fx_target_ring_2","flame_w",castDelay, (float) dest.getX(),(float) dest.getY(),true,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"flame_princess_polymorph_fireball","flame_w_polymorph",castDelay, (float) dest.getX(),(float) dest.getY(),false,this.team,0f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.getUser(),"w",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                this.canCast[1] = false;
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest), castDelay, TimeUnit.MILLISECONDS);
                break;
            case 3: //E TODO: FP does not return to form when skin is used also she needs to be scaled up
                if(!ultStarted && ultUses == 3){
                    int duration = Champion.getSpellData(parentExt,getAvatar(),ability).get("spellDuration").asInt();
                    ultStarted = true;
                    ultFinished = false;
                    this.setState(ActorState.TRANSFORMED, true);
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,"flame_ult");
                    ExtensionCommands.createActorFX(this.parentExt,this.player.getLastJoinedRoom(),this.id,"flame_princess_ultimate_aoe",5000,"flame_e",true,"",true,false,this.team);
                    ExtensionCommands.scaleActor(parentExt,room,id,1.5f);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),duration, TimeUnit.MILLISECONDS);
                }else{
                    if(ultUses>0){
                        //TODO: Fix so FP can dash and still get health packs
                        ultUses--;
                        Point2D dashPoint = Champion.getDashPoint(parentExt,this,dest);
                        if(this.location.distance(dashPoint) < 5) dashPoint = Champion.getDashPoint(parentExt,this,Champion.getMaxRangeLine(new Line2D.Float(this.location,dashPoint),5f).getP2());
                        ExtensionCommands.moveActor(parentExt,room,id,getLocation(),dashPoint,20f,true);
                        double time = dest.distance(getLocation())/20f;
                        this.canMove = false;
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        if(ultUses == 0){
                            SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dashPoint),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        }
                        setLocation(dashPoint);
                    }
                }
                break;
            case 4: //Passive
                break;
        }
    }

    @Override
    public void attack(Actor a){
        new RangedAttack(a,new PassiveAttack(a,this.handleAttack(a)),"flame_princess_projectile").run();
    }

    @Override
    public void setCanMove(boolean canMove){
        if(ultStarted && !canMove) this.canMove = true;
        else super.setCanMove(canMove);
        System.out.println("FP Move set to " + this.canMove);
    }

    private class FlameAbilityRunnable extends AbilityRunnable {

        public FlameAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            ExtensionCommands.playSound(parentExt,room,"","sfx_flame_princess_projectile_explode",this.dest);
            RoomHandler roomHandler = parentExt.getRoomHandler(player.getLastJoinedRoom().getId());
            List<Actor> affectedUsers = Champion.getActorsInRadius(roomHandler,this.dest,2).stream().filter(a -> a.getTeam() != FlamePrincess.this.team).collect(Collectors.toList());
            for(Actor a : affectedUsers){
                if(a.getActorType() == ActorType.PLAYER){
                    UserActor userActor = (UserActor) a;
                    userActor.addState(ActorState.POLYMORPH,0d,3000,null,false);
                }
                double newDamage = getSpellDamage(spellData);
                handleSpellVamp(newDamage);
                a.addToDamageQueue(FlamePrincess.this,newDamage,parentExt.getAttackData(getAvatar(),"spell2"));
            }
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            if(!ultFinished && ultStarted){
                setState(ActorState.TRANSFORMED, false);
                ExtensionCommands.removeFx(parentExt,room,"flame_e");
                ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",canUseAbility(2), getReducedCooldown(cooldown), gCooldown);
                ExtensionCommands.scaleActor(parentExt,room,id,0.6667f);
                ultStarted = false;
                ultFinished = true;
                ultUses = 3;
                lastUltUsage = -1;
            }else if(ultFinished){
                ultStarted = false;
                ultFinished = false;
                ultUses = 3;
                lastUltUsage = -1;
            }
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class FlameProjectile extends Projectile {

        private boolean hitPlayer = false;

        public FlameProjectile(ATBPExtension parentExt,UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt,owner, path, speed, hitboxRadius, id);
        }

        @Override
        public void hit(Actor victim){
            if(this.hitPlayer) return;
            this.hitPlayer = true;
            JsonNode attackData = parentExt.getAttackData(getAvatar(),"spell1");
            handleSpellVamp(getSpellDamage(attackData));
            victim.addToDamageQueue(FlamePrincess.this,getSpellDamage(attackData),attackData);
            //ExtensionCommands.moveActor(parentExt,player,this.id,this.location,this.location,this.speed,false);
            ExtensionCommands.playSound(parentExt,room,"","sfx_flame_princess_cone_of_flame",victim.getLocation());
            ExtensionCommands.createActorFX(parentExt,room,this.id,"flame_princess_projectile_large_explosion",200,"flame_explosion",false,"",false,false,team);
            ExtensionCommands.createActorFX(parentExt,room,this.id,"flame_princess_cone_of_flames",300,"flame_cone",false,"",true,false,team);
            for(Actor a : Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),Champion.extendLine(path,7f),4f)){
                if(!a.getId().equalsIgnoreCase(victim.getId()) && a.getTeam() != team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                    double newDamage = (double)getSpellDamage(attackData)*1.2d;
                    handleSpellVamp(newDamage);
                    a.addToDamageQueue(FlamePrincess.this,Math.round(newDamage),attackData);
                }
            }
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new DelayedProjectile(), 300, TimeUnit.MILLISECONDS);
        }

        private class DelayedProjectile implements Runnable {

            @Override
            public void run() {
                destroy();
            }
        }
    }

    private class PassiveAttack implements Runnable {

        Actor target;
        boolean crit;

        PassiveAttack(Actor target, boolean crit){
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if(crit) damage*=2;
            FlamePrincess.this.handleLifeSteal();
            ExtensionCommands.playSound(parentExt,room,target.getId(),"sfx_flame_princess_passive_ignite",target.getLocation());
            target.addToDamageQueue(FlamePrincess.this,damage, parentExt.getAttackData(getAvatar(),"basicAttack"));
            if(FlamePrincess.this.passiveEnabled && (target.getActorType() != ActorType.TOWER && target.getActorType() != ActorType.BASE)){
                FlamePrincess.this.passiveEnabled = false;
                ExtensionCommands.removeFx(parentExt,room,"flame_passive");
                ExtensionCommands.actorAbilityResponse(parentExt,player,"passive",true,10000,0);
                lastPassiveUsage = System.currentTimeMillis();
                ExtensionCommands.createActorFX(parentExt,room,target.getId(),"flame_princess_dot",3000,"flame_passive_burn",true,"",false,false,team);
                for(int i = 0; i < 3; i++){
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(parentExt,FlamePrincess.this,target,20,"spell4"),i+1,TimeUnit.SECONDS);
                }
            }
        }
    }
}

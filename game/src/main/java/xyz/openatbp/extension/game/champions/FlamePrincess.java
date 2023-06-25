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
        if(this.ultStarted && !this.ultFinished && msRan-lastUltUsage == 1000){
            lastUltUsage = msRan;
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),this.location,2)){
                if(a.getTeam() != this.team){ //TODO: Set so FP doesn't target her team or self
                    JsonNode attackData = this.parentExt.getAttackData(this.avatar,"spell3");
                    if(a.getActorType() != ActorType.PLAYER) a.damaged(this,75,attackData);
                    else{
                        UserActor userActor = (UserActor) a;
                        userActor.damaged(this,75,attackData);
                    }
                }else{
                    System.out.println(a.getId() + " is on your team!");
                }
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        super.useAbility(ability,spellData,cooldown,gCooldown,castDelay,dest);
        if(ultUses == 3 && !passiveEnabled && System.currentTimeMillis()-lastPassiveUsage >= 10000){
            ExtensionCommands.createActorFX(parentExt,player,id,"flame_princess_passive_flames",1000*60,"flame_passive",true,"",false,false,team);
            passiveEnabled = true;
        }
        switch(ability){
            case 1: //Q
                double x = dest.getX();
                double z = dest.getY();
                Point2D endLocation = new Point2D.Double(x,z);
                Line2D skillShotLine = new Line2D.Float(this.location,endLocation);
                Line2D maxRangeLine = Champion.getMaxRangeLine(skillShotLine,8f);
                ExtensionCommands.createProjectile(parentExt,this.room,this,"projectile_flame_cone", maxRangeLine.getP1(), maxRangeLine.getP2(), 8f);
                this.parentExt.getRoomHandler(this.room.getId()).addProjectile(new FlameProjectile(this.parentExt,this,maxRangeLine,8f,0.5f,this.id+"projectile_flame_cone"));
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.getUser(),"q",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 2: //W
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"fx_target_ring_2","flame_w",1000, (float) dest.getX(),(float) dest.getY(),true,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.player.getLastJoinedRoom(), this.id,"flame_princess_polymorph_fireball","flame_w_polymorph",1000, (float) dest.getX(),(float) dest.getY(),false,this.team,0f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.getUser(),"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest), 500, TimeUnit.MILLISECONDS);
                break;
            case 3: //E TODO: FP does not return to form when skin is used also she needs to be scaled up
                if(!ultStarted && ultUses == 3){
                    int duration = Champion.getSpellData(parentExt,this.avatar,ability).get("spellDuration").asInt();
                    ultStarted = true;
                    ultFinished = false;
                    this.setState(ActorState.TRANSFORMED, true);
                    ExtensionCommands.playSound(this.parentExt,this.player,"vo/vo_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.playSound(this.parentExt,this.player,"sfx_flame_princess_flame_form",this.getLocation());
                    ExtensionCommands.swapActorAsset(this.parentExt,this.player,this.id,"flame_ult");
                    ExtensionCommands.createActorFX(this.parentExt,this.player.getLastJoinedRoom(),this.id,"flame_princess_ultimate_aoe",5000,"flame_e",true,"",true,false,this.team);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),duration, TimeUnit.MILLISECONDS);
                }else{
                    if(ultUses>0){
                        //TODO: Fix so FP can dash and still get health packs
                        ultUses--;
                        ExtensionCommands.moveActor(parentExt,player,id,getLocation(),Champion.getDashPoint(parentExt,this,dest),20f,true);
                        double time = dest.distance(getLocation())/20f;
                        this.canMove = false;
                        SmartFoxServer.getInstance().getTaskScheduler().schedule(new MovementStopper(true),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        if(ultUses == 0){
                            System.out.println("Time: " + time);
                            SmartFoxServer.getInstance().getTaskScheduler().schedule(new FlameAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),(int)Math.floor(time*1000),TimeUnit.MILLISECONDS);
                        }
                        setLocation(dest);
                    }
                }
                break;
            case 4: //Passive
                break;
        }
    }

    @Override
    public void attack(Actor a){
        super.attack(a);
        boolean crit = Math.random() < this.getPlayerStat("criticalChance");
        for(User u : room.getUserList()){
            ExtensionCommands.attackActor(parentExt,u,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),crit,true);
        }
        currentAutoAttack = SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a,new PassiveAttack(a),"flame_princess_projectile"),500,TimeUnit.MILLISECONDS);
    }

    @Override
    public void setCanMove(boolean canMove){
        if(ultStarted && !canMove) this.canMove = true;
        else this.canMove = canMove;
    }

    private class FlameAbilityRunnable extends AbilityRunnable {

        public FlameAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {

        }

        @Override
        protected void spellW() {
            RoomHandler roomHandler = parentExt.getRoomHandler(player.getLastJoinedRoom().getId());
            List<Actor> affectedUsers = Champion.getActorsInRadius(roomHandler,this.dest,2).stream().filter(a -> a.getClass() == UserActor.class).collect(Collectors.toList());
            for(Actor u : affectedUsers){
                UserActor userActor = (UserActor) u;
                System.out.println("Hit: " + u.getAvatar());
                userActor.setState(ActorState.POLYMORPH, true);
                double newDamage = u.getMitigatedDamage(50d,AttackType.SPELL,FlamePrincess.this);
                handleSpellVamp(newDamage);
                userActor.damaged(FlamePrincess.this,50,parentExt.getAttackData(avatar,"spell2"));
                userActor.handleEffect(ActorState.POLYMORPH,-1d,3000);
            }
            System.out.println("Ability done!");
        }

        @Override
        protected void spellE() {
            if(!ultFinished && ultStarted){
                setState(ActorState.TRANSFORMED, false);
                ExtensionCommands.removeFx(parentExt,player,"flame_e");
                ExtensionCommands.swapActorAsset(parentExt,player,id,avatar);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true, getReducedCooldown(cooldown), (int) gCooldown);
                ultStarted = false;
                ultFinished = true;
                ultUses = 3;
                lastUltUsage = -1;
            }else if(ultFinished){
                System.out.println("Ability already ended!");
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

        public FlameProjectile(ATBPExtension parentExt,UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt,owner, path, speed, hitboxRadius, id);
        }

        @Override
        public void hit(Actor victim){
            //ExtensionCommands.moveActor(parentExt,player,this.id,this.location,this.location,this.speed,false);
            ExtensionCommands.createActorFX(parentExt,room,this.id,"flame_princess_projectile_large_explosion",200,"flame_explosion",false,"",false,false,team);
            ExtensionCommands.createActorFX(parentExt,room,this.id,"flame_princess_cone_of_flames",300,"flame_cone",false,"",true,false,team);
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

        PassiveAttack(Actor target){
            this.target = target;
        }

        @Override
        public void run() {
            target.damaged(FlamePrincess.this,(int)getPlayerStat("attackDamage"), parentExt.getAttackData(avatar,"basicAttack"));
            if(FlamePrincess.this.passiveEnabled && (target.getClass() != Tower.class && target.getClass() != Base.class)){
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

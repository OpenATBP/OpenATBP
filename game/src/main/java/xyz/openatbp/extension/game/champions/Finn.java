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

public class Finn extends UserActor {
    private int furyStacks = 0;
    private Actor furyTarget = null;
    private boolean qActive = false;
    private boolean[] wallsActivated = {false,false,false,false}; //NORTH, EAST, SOUTH, WEST
    private Line2D[] wallLines;
    private boolean ultActivated = false;

    public Finn(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        new PassiveAttack(a,this.handleAttack(a)).run();
    }

    @Override
    public double getPlayerStat(String stat) {
        if(stat.equalsIgnoreCase("speed") && qActive) return super.getPlayerStat(stat) * 1.5d;
        return super.getPlayerStat(stat);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        super.handleKill(a, attackData);
        this.canCast[1] = true;
        ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,0,0);
    }

    @Override
    public void increaseStat(String key, double num) {
        super.increaseStat(key, num);
        if(key.equalsIgnoreCase("assists")){
            this.canCast[1] = true;
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,0,0);
        }
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(this.ultActivated){
            for(int i = 0; i < this.wallLines.length; i++){
                if(this.wallsActivated[i]){
                    for(Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()){
                        if(this.isNonStructure(a) && this.wallLines[i].ptSegDist(a.getLocation()) <= 0.5f){
                            this.wallsActivated[i] = false;
                            JsonNode spellData = this.parentExt.getAttackData("finn","spell3");
                            a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                            a.addState(ActorState.ROOTED,0d,2000,null,false);
                            String direction = "north";
                            if(i == 1) direction = "east";
                            else if(i == 2) direction = "south";
                            else if(i == 3) direction = "west";
                            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_"+direction+"Wall");
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch (ability){
            case 1:
                this.canCast[0] = false;
                this.attackCooldown = 0;
                this.qActive = true;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_finn_shield",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"finn_shieldShimmer",3000,this.id+"_shield",true,"Bip001 Pelvis",true,false,this.team);
                this.addEffect("armor",this.getStat("armor")*0.25d,3000,null,false);
                this.addEffect("attackSpeed",this.getStat("attackSpeed")*-0.20d,3000,null,false);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinnAbilityHandler(ability,spellData,cooldown,gCooldown,dest),3000,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.stopMoving();
                this.canCast[1] = false;
                Point2D dashPoint = Champion.getDashPoint(this.parentExt,this,dest);
                double time = dashPoint.distance(this.location)/15d;
                ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,dashPoint, 15f,true);
                this.canMove = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_finn_dash_attack",this.location);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinnAbilityHandler(ability,spellData,cooldown,gCooldown,dashPoint,this.location),(int)(time*1000),TimeUnit.MILLISECONDS);
                this.setLocation(dashPoint);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                this.ultActivated = true;
                double widthHalf = 3.675d;
                Point2D p1 = new Point2D.Double(this.location.getX()-widthHalf,this.location.getY()+widthHalf); //BOT RIGHT
                Point2D p2 = new Point2D.Double(this.location.getX()+widthHalf,this.location.getY()+widthHalf); //BOT LEFT
                Point2D p3 = new Point2D.Double(this.location.getX()-widthHalf,this.location.getY()-widthHalf); //TOP RIGHT
                Point2D p4 = new Point2D.Double(this.location.getX()+widthHalf,this.location.getY()-widthHalf); // TOP LEFT
                float x = (float) this.location.getX();
                float y = (float) this.location.getY();
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_finn_walls_drop",this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"finn_wall_south",this.id+"_northWall",5000,x,y,false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"finn_wall_north",this.id+"_southWall",5000,x,y,false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"finn_wall_west",this.id+"_eastWall",5000,x,y,false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"finn_wall_east",this.id+"_westWall",5000,x,y,false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"finn_wall_corner_swords",this.id+"_p1Sword",5000,x,y,false,this.team,0f);
                /*
                ExtensionCommands.createActor(this.parentExt,this.room,"testp1","ironowl_a",p1,0f,2);
                ExtensionCommands.createActor(this.parentExt,this.room,"testp2","gnome_a",p2,0f,2);
                ExtensionCommands.createActor(this.parentExt,this.room,"testp3","creep",p3,0f,2);
                ExtensionCommands.createActor(this.parentExt,this.room,"testp4","bmo",p4,0f,2);

                 */
                Line2D northWall = new Line2D.Float(p4,p3);
                Line2D eastWall = new Line2D.Float(p3,p1);
                Line2D southWall = new Line2D.Float(p2,p1);
                Line2D westWall = new Line2D.Float(p4,p2);
                this.wallLines = new Line2D[]{northWall, eastWall, southWall, westWall};
                this.wallsActivated = new boolean[]{true,true,true,true};
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FinnAbilityHandler(ability,spellData,cooldown,gCooldown,dest),5000,TimeUnit.MILLISECONDS);
                break;
        }
    }

    protected double handlePassive(Actor target, double damage){
        if(furyTarget != null){
            if(furyTarget.getId().equalsIgnoreCase(target.getId())){
                damage*=(1+(0.2*furyStacks));
                if(furyStacks < 3){
                    if(furyStacks > 0) ExtensionCommands.removeFx(parentExt,room,target.getId()+"_mark"+furyStacks);
                    furyStacks++;
                    ExtensionCommands.createActorFX(parentExt,room,target.getId(),"fx_mark"+furyStacks,1000*15*60,target.getId()+"_mark"+furyStacks,true,"",true,false,target.getTeam());
                }
                else{
                    furyStacks = 0;
                    ExtensionCommands.removeFx(parentExt,room,target.getId()+"_mark3");
                    ExtensionCommands.createActorFX(parentExt,room,target.getId(),"fx_mark4",500,target.getId()+"_mark4",true,"",true,false,target.getTeam());
                    if(qActive){
                        JsonNode spellData = parentExt.getAttackData("finn","spell1");
                        target.addToDamageQueue(Finn.this,getSpellDamage(spellData),spellData);
                        ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_shield");
                        ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_finn_shield_shatter",this.location);
                        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"finn_shieldShatter",500,this.id+"_qShatter",true,"",true,false,this.team);
                        qActive = false;
                    }
                }
            }else{
                ExtensionCommands.removeFx(parentExt,room,target.getId()+"_mark"+furyStacks);
                furyTarget = target;
                furyStacks = 1;
            }
        }else{
            ExtensionCommands.createActorFX(this.parentExt,this.room,target.getId(),"fx_mark1",1000*15*60,target.getId()+"_mark1",true,"",true,false,this.team);
            furyTarget = target;
            furyStacks = 1;
        }
        return damage;
    }

    private class FinnAbilityHandler extends AbilityRunnable {

        Point2D originalLocation = null;

        public FinnAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        public FinnAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest, Point2D ogLocation){
            super(ability,spellData,cooldown,gCooldown,dest);
            this.originalLocation = ogLocation;
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            qActive = false;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            canMove = true;
            ExtensionCommands.actorAnimate(parentExt,room,id,"run",100,false);
            if(this.originalLocation != null){
                for(Actor a : Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),new Line2D.Float(this.originalLocation,dest),2f)){
                    if(isNonStructure(a)){
                        a.addToDamageQueue(Finn.this,handlePassive(a,getSpellDamage(spellData)),spellData);
                        ExtensionCommands.playSound(parentExt,room,id,"sfx_finn_dash_impact",location);
                    }
                }
            }
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            wallLines = null;
            wallsActivated = new boolean[]{false,false,false,false};
            ultActivated = false;
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class PassiveAttack implements Runnable{

        Actor target;
        boolean crit;

        PassiveAttack(Actor t, boolean crit){
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if(this.crit) damage*=2;
            damage = handlePassive(target,damage);
            new Champion.DelayedAttack(parentExt,Finn.this,target,(int)damage,"basicAttack").run();
        }
    }
}

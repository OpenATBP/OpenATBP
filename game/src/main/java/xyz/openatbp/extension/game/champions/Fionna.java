package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class Fionna extends UserActor {
    private enum SwordType{FIERCE, FEARLESS};
    private int dashesRemaining = 0;
    private boolean hitWithDash = false;
    private long dashTime = -1;
    private SwordType swordType = SwordType.FEARLESS;
    private boolean ultActivated = false;
    private double previousAttackDamage;
    private double previousSpellDamage;

    public Fionna(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.createActorFX(parentExt,room,id,"fionna_sword_blue",1000*60*15,this.id+"_fearless",true,"Bip001 Prop1",true,false,this.team);
        ExtensionCommands.createActorFX(parentExt,room,id,"fionna_health_regen",1000*60*15,this.id+"_blueRegen",true,"Bip001 Prop1",true,false,this.team);
        ExtensionCommands.addStatusIcon(parentExt,player,"fionna_fearless","FEARLESS","icon_fionna_s2b",0f);
        String[] statsToUpdate = {"healthRegen","armor","spellResist","attackSpeed","speed"};
        this.updateStatMenu(statsToUpdate);
        this.previousAttackDamage = this.getPlayerStat("attackDamage");
        this.previousSpellDamage = this.getPlayerStat("spellDamage");
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(System.currentTimeMillis() - dashTime >= 3000 && this.dashesRemaining > 0){
            ExtensionCommands.removeStatusIcon(parentExt,player,this.id+"_dash"+this.dashesRemaining);
            this.dashTime = -1;
            this.dashesRemaining = 0;
            double cooldown = this.getReducedCooldown(14000d);
            if(!this.hitWithDash) cooldown*=0.7d;
            this.hitWithDash = false;
            ExtensionCommands.actorAbilityResponse(parentExt,player,"q",true,(int)cooldown,0);
        }
        if(this.getPlayerStat("attackDamage") != this.previousAttackDamage){
            this.updateStatMenu("attackDamage");
            this.previousAttackDamage = this.getPlayerStat("attackDamage");
        }
        if(this.getPlayerStat("spellDamage") != this.previousSpellDamage){
            this.updateStatMenu("spellDamage");
            this.previousSpellDamage = this.getPlayerStat("spellDamage");
        }
    }

    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        switch(ability){
            case 1:
                if(this.dashTime == -1){
                    this.dashTime = System.currentTimeMillis();
                    this.dashesRemaining = 3;
                }
                if(dashesRemaining > 0){
                    this.dashTime = System.currentTimeMillis();
                    Point2D origLocation = this.location;
                    Point2D dashPoint = this.dash(dest,false,15d);
                    double time = origLocation.distance(dashPoint)/15d;
                    int qTime = (int)(time*1000);
                    this.dashesRemaining--;
                    if(this.dashesRemaining == 0){
                        this.dashTime = -1;
                        this.canCast[0] = false;
                        double newCooldown = getReducedCooldown(cooldown);
                        if(!this.hitWithDash) newCooldown*=0.7d;
                        ExtensionCommands.actorAbilityResponse(parentExt,player,"q",true,(int)newCooldown,gCooldown);
                        ExtensionCommands.actorAnimate(parentExt,room,id,"spell1c",qTime,false);
                    }else if(this.dashesRemaining == 1){
                        ExtensionCommands.actorAnimate(parentExt,room,id,"spell1b",qTime,false);
                    }
                    if(this.dashesRemaining != 0){
                        ExtensionCommands.playSound(parentExt,room,this.id,"sfx_fionna_dash_small",this.location);
                        ExtensionCommands.addStatusIcon(parentExt,player,this.id+"_dash"+this.dashesRemaining,this.dashesRemaining + " dashes remaining!","icon_fionna_s1",3000f);
                    }else{
                        ExtensionCommands.playSound(parentExt,room,this.id,"sfx_fionna_dash_large",this.location);
                    }
                    if(this.dashesRemaining != 2) ExtensionCommands.removeStatusIcon(parentExt,player,this.id+"_dash"+(this.dashesRemaining+1));
                    ExtensionCommands.playSound(parentExt,room,this.id,"sfx_fionna_dash_wind",this.location);
                    ExtensionCommands.createActorFX(parentExt,room,this.id,"fionna_trail",qTime,this.id+"_dashTrail"+dashesRemaining,true,"",true,false,this.team);
                    int gruntNum = 3-this.dashesRemaining;
                    ExtensionCommands.playSound(parentExt,room,this.id,"fionna_grunt"+gruntNum,this.location);

                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FionnaAbilityRunnable(ability,spellData,cooldown,gCooldown,dashPoint),qTime, TimeUnit.MILLISECONDS);
                }
                break;
            case 2:
                if(this.swordType == SwordType.FEARLESS) this.swordType = SwordType.FIERCE;
                else this.swordType = SwordType.FEARLESS;
                this.handleSwordAnimation();
                ExtensionCommands.actorAbilityResponse(parentExt,player,"w",true,getReducedCooldown(cooldown),gCooldown);
                this.canCast[1] = false;
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FionnaAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),250,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.stopMoving(500);
                this.canCast[2] = false;
                this.ultActivated = true;
                ExtensionCommands.addStatusIcon(this.parentExt,this.player,"fionna_ult","fionna_spell_3_description","icon_fionna_s3",6000f);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_fionna_invuln",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"fionna_ult",this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fionna_invuln_fx",6000,this.id+"_ult",true,"",true,false,this.team);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new FionnaAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),6000,TimeUnit.MILLISECONDS);
                break;
            case 4:
                break;
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        switch(stat){
            case "healthRegen":
                if(this.swordType == SwordType.FIERCE) return super.getPlayerStat(stat) - 2 - (maxHealth*0.02d);
                else return super.getPlayerStat(stat) + (maxHealth*0.01d);
            case "speed":
                if(this.swordType == SwordType.FIERCE) return super.getPlayerStat(stat) + (this.getStat("speed")*0.2d);
                break;
            case "attackSpeed":
                if(this.swordType == SwordType.FIERCE) return super.getPlayerStat(stat) - (this.getStat("attackSpeed")*0.2d);
                break;
            case "armor":
            case "spellResist":
                if(this.swordType == SwordType.FEARLESS) return super.getPlayerStat(stat) + (this.getStat(stat)*0.3d);
                break;
            case "attackDamage":
            case "spellDamage":
                return super.getPlayerStat(stat) + this.getPassiveAttackDamage(stat);
        }
        return super.getPlayerStat(stat);
    }

    private double getPassiveAttackDamage(String stat){
        double missingPHealth = 1d-this.getPHealth();
        double modifier = (0.8d*missingPHealth);
        return this.getStat(stat)*modifier;
    }

    private void handleSwordAnimation(){
        if(this.swordType == SwordType.FEARLESS){
            ExtensionCommands.createActorFX(parentExt,room,id,"fionna_sword_blue",1000*60*15,this.id+"_fearless",true,"Bip001 Prop1",true,false,this.team);
            ExtensionCommands.createActorFX(parentExt,room,id,"fionna_health_regen",1000*60*15,this.id+"_blueRegen",true,"Bip001 Prop1",true,false,this.team);
            ExtensionCommands.removeFx(parentExt,room,this.id+"_fierce");
            ExtensionCommands.removeFx(parentExt,room,this.id+"_attackUp");
            ExtensionCommands.addStatusIcon(parentExt,player,"fionna_fearless","FEARLESS","icon_fionna_s2b",0f);
            ExtensionCommands.removeStatusIcon(parentExt,player,"fionna_fierce");
            ExtensionCommands.playSound(parentExt,player,this.id,"sfx_fionna_health_regen",this.location);

        }else{
            ExtensionCommands.createActorFX(parentExt,room,id,"fionna_sword_pink",1000*60*15,this.id+"_fierce",true,"Bip001 Prop1",true,false,this.team);
            ExtensionCommands.createActorFX(parentExt,room,id,"fionna_attack_up",1000*60*15,this.id+"_attackUp",true,"Bip001 Prop1",true,false,this.team);
            ExtensionCommands.removeFx(parentExt,room,this.id+"_fearless");
            ExtensionCommands.removeFx(parentExt,room,this.id+"_blueRegen");
            ExtensionCommands.addStatusIcon(parentExt,player,"fionna_fierce","FIERCE","icon_fionna_s2a",0f);
            ExtensionCommands.removeStatusIcon(parentExt,player,"fionna_fearless");
            ExtensionCommands.playSound(parentExt,player,this.id,"sfx_fionna_attack_up",this.location);
        }
        String[] statsToUpdate = {"healthRegen","armor","spellResist","attackSpeed","speed"};
        this.updateStatMenu(statsToUpdate);
        this.move(this.movementLine.getP2());
    }

    public boolean ultActivated(){
        return this.ultActivated;
    }

    private class FionnaAbilityRunnable extends AbilityRunnable{

        public FionnaAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            int dashInt = dashesRemaining+1;
            float range = 1f;
            String explosionFx = "fionna_dash_explode_small";
            if(dashInt == 1){
                range = 2f;
                explosionFx = "fionna_dash_explode";
                canCast[0] = true;
            }
            ExtensionCommands.createWorldFX(parentExt,room,id,explosionFx,id+"_explosion"+dashInt,1500,(float)dest.getX(),(float)dest.getY(),false,team,0f);
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,range)){
                if(a.getTeam() != team && a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER){
                    if(!hitWithDash) hitWithDash = true;
                    double damage = getSpellDamage(spellData);
                    a.addToDamageQueue(Fionna.this,damage,spellData);
                    if(dashInt == 1) a.addState(ActorState.SLOWED,0.5d,1000,null,false);
                }
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            ultActivated = false;
            canCast[2] = true;
            ExtensionCommands.removeStatusIcon(parentExt,player,"fionna_ult");
        }

        @Override
        protected void spellPassive() {

        }
    }
}

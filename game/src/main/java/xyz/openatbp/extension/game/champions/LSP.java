package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LSP extends UserActor {
    private int lumps = 0;
    private long wTime = 0;

    public LSP(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.addStatusIcon(parentExt,player,"p0","lsp_spell_4_short_description","icon_lsp_passive",0f);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(System.currentTimeMillis() - this.wTime < 3000){
            JsonNode spellData = this.parentExt.getAttackData(this.avatar,"spell2");
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.location,3f)){
                if(this.isNonStructure(a)){
                    a.addToDamageQueue(this,(double)getSpellDamage(spellData)/10d,spellData);
                }
            }
        }
    }

    @Override
    public void attack(Actor a) {
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new LSPPassive(a,this.handleAttack(a)),"lsp_projectile","Bip001 R Hand"),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch(ability){
            case 1:
                this.stopMoving(castDelay);
                this.canCast[0] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lsp_drama_beam",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_rect_7",1100,this.id+"_qRect",false,"",true,true,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LSPAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lsp_lumps_aoe",this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_3",3500,this.id+"_wRing",true,"",true,true,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LSPAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.stopMoving(castDelay);
                this.canCast[2] = false;
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lsp_cellphone_throw",this.location);
                ExtensionCommands.playSound(parentExt,room,"global","sfx_lsp_cellphone_throw",location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LSPAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
        }
    }

    private class LSPAbilityHandler extends AbilityRunnable {

        public LSPAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            double healthHealed = (double)getMaxHealth() * (0.03d*lumps);
            ExtensionCommands.playSound(parentExt,room,id,"sfx_lsp_drama_beam",location);
            ExtensionCommands.removeStatusIcon(parentExt,player,"p"+lumps);
            ExtensionCommands.addStatusIcon(parentExt,player,"p0","lsp_spell_4_short_description","icon_lsp_passive",0f);
            lumps = 0;
            changeHealth((int)healthHealed);
            ExtensionCommands.createActorFX(parentExt,room,id,"lsp_drama_beam",1100,id+"q",false,"",true,false,team);
            Line2D maxLine = Champion.getMaxRangeLine(new Line2D.Float(LSP.this.location,dest),7f);
            for(Actor a : Champion.getActorsAlongLine(parentExt.getRoomHandler(room.getId()),maxLine,1.5d)){
                if(isNonStructure(a)){
                    a.handleFear(LSP.this,3000);
                    a.addToDamageQueue(LSP.this,getSpellDamage(spellData),spellData);
                }
            }

        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            ExtensionCommands.playSound(parentExt,room,id,"sfx_lsp_lumps_aoe",location);
            ExtensionCommands.createActorFX(parentExt,room,id,"lsp_the_lumps_aoe",3000,id+"_w",true,"",true,false,team);
            wTime = System.currentTimeMillis();
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            Line2D projectileLine = Champion.getMaxRangeLine(new Line2D.Float(location,dest),100f);
            ExtensionCommands.actorAnimate(parentExt,room,id,"spell3b",500,false);
            fireProjectile(new LSPUltProjectile(parentExt,LSP.this,projectileLine,8f,2f,id+"projectile_lsp_ult"),"projectile_lsp_ult", projectileLine.getP2(), 100f);
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class LSPUltProjectile extends Projectile {

        private List<Actor> victims;
        private double damageReduction = 0d;

        public LSPUltProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
            this.victims = new ArrayList<>();
        }

        @Override
        protected void hit(Actor victim) {
            this.victims.add(victim);
            JsonNode spellData = this.parentExt.getAttackData(LSP.this.avatar,"spell3");
            if(victim.getTeam() == LSP.this.team){
                victim.changeHealth(getSpellDamage(spellData));
            }else{
                victim.addToDamageQueue(LSP.this,getSpellDamage(spellData)*(1-this.damageReduction),spellData);
                this.damageReduction+=0.3d;
                if(this.damageReduction > 0.9d) this.damageReduction = 0.9d;
            }
        }

        @Override
        public Actor checkPlayerCollision(RoomHandler roomHandler){
            for(Actor a : roomHandler.getActors()){
                if(!this.victims.contains(a) && a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER && !a.getId().equalsIgnoreCase(LSP.this.id)){
                    double collisionRadius = parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
                    if(a.getLocation().distance(location) <= hitbox + collisionRadius){
                        return a;
                    }
                }
            }
            return null;
        }
    }

    private class LSPPassive implements Runnable {

        Actor target;
        boolean crit;

        LSPPassive(Actor a, boolean crit){
            this.target = a;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = LSP.this.getPlayerStat("attackDamage");
            if(crit) damage*=2;
            new Champion.DelayedAttack(parentExt,LSP.this,this.target,(int)damage,"basicAttack").run();
            ExtensionCommands.removeStatusIcon(parentExt,player,"p"+lumps);
            if(LSP.this.lumps < 10) LSP.this.lumps++;
            ExtensionCommands.addStatusIcon(parentExt,player,"p"+lumps,"lsp_spell_4_short_description","icon_lsp_p"+lumps,0f);
        }
    }
}
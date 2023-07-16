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

public class Marceline extends UserActor {

    private int passiveHits = 0;
    public Marceline(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public double getPlayerStat(String stat){
        if(stat.equalsIgnoreCase("healthRegen")){
            if(this.getState(ActorState.TRANSFORMED)) return super.getPlayerStat(stat) * 1.2d;
            else return super.getPlayerStat(stat);
        }else return super.getPlayerStat(stat);
    }

    @Override
    public void attack(Actor a){
        if(this.attackCooldown == 0){
            this.handleAttack(a);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(parentExt,this,a,(int)this.getPlayerStat("attackDamage"),"basicAttack"),250,TimeUnit.MILLISECONDS);
            Runnable passiveHandler = () -> {
                if(!getState(ActorState.TRANSFORMED)) passiveHits++;
                handleLifeSteal();
            };
            SmartFoxServer.getInstance().getTaskScheduler().schedule(passiveHandler,250,TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void handleLifeSteal(){
        double damage = this.getPlayerStat("attackDamage");
        double lifesteal = this.getPlayerStat("lifeSteal")/100;
        if(this.passiveHits == 3){
            this.passiveHits = 0;
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_crit_fangs",1000,this.id+"_passiveHit",true,"Bip001 Head",false,false,this.team);
            lifesteal = 1d;
        }
        this.changeHealth((int)Math.round(damage*lifesteal));
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        switch(ability){
            case 1: //Q
                break;
            case 2: //W
                break;
            case 3: //E
                this.canCast[2] = false;
                this.stopMoving(castDelay);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,getReducedCooldown(cooldown),gCooldown);
                if(getState(ActorState.TRANSFORMED)){
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"marceline_morph_to_human",this.location);
                }else{
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,"marceline_morph_to_beast",this.location);
                }
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"marceline_spell_casting",castDelay,this.id+"_transformCast",true,"",true,false,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_3",castDelay,this.id+"_transformRing",false,"",false,true,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new MarcelineAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay, TimeUnit.MILLISECONDS);
                break;
            case 4: //Passive
                break;
        }
    }

    private class MarcelineAbilityHandler extends AbilityRunnable {
        public MarcelineAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {

        }

        @Override
        protected void spellW() {

        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            if(getState(ActorState.TRANSFORMED)){
                ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
                setState(ActorState.TRANSFORMED, false);
                Marceline.this.handleEffect(ActorState.IMMUNITY,0d,2000,"statusEffect_immunity");
                setState(ActorState.CLEANSED, true);
                Marceline.this.cleanseEffects();
            }else{
                ExtensionCommands.swapActorAsset(parentExt,room,id,"marceline_bat");
                passiveHits = 0;
                setState(ActorState.TRANSFORMED, true);
                double currentAttackSpeed = getPlayerStat("attackSpeed");
                attackCooldown = 0d;
                Marceline.this.handleEffect("attackSpeed",500-currentAttackSpeed,3000,null);
            }
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),dest,3)){
                if(a.getTeam() != team && a.getActorType() != ActorType.TOWER && a.getActorType() != ActorType.BASE){
                    double damage = getSpellDamage(spellData);
                    a.addToDamageQueue(Marceline.this,damage,spellData);
                    if(!getState(ActorState.TRANSFORMED)){
                        a.handleCharm(Marceline.this,2000);
                    }else{
                        a.handleFear(Marceline.this,2000);
                    }
                }
            }
        }

        @Override
        protected void spellPassive() {

        }
    }
}

package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IceKing extends UserActor {
    private boolean iceShield = false;
    private long lastAbilityUsed;
    private Actor qVictim = null;
    private long qHitTime = -1;
    private Map<String, Long> lastWHit;
    private Point2D wLocation = null;
    private boolean ultActive = false;
    private Point2D ultLocation = null;
    private boolean assetSwapped = false;
    private boolean hasDefaultAsset = true;
    private long lastWhirlwindTime = 0;

    public IceKing(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.lastAbilityUsed = System.currentTimeMillis();
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(System.currentTimeMillis() - lastAbilityUsed > 10000){
            if(!this.iceShield){
                this.iceShield = true;
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"ice_king_frost_shield",1000*60*15,this.id+"_iceShield",true,"Bip001 Pelvis",true,false,this.team);
            }
        }

        if(this.qVictim != null){
            if(System.currentTimeMillis() < qHitTime){
                JsonNode spellData = parentExt.getAttackData("iceking","spell1");
                this.qVictim.addToDamageQueue(this,getSpellDamage(spellData)/10d,spellData);
            }else{
                this.qVictim = null;
                this.qHitTime = -1;
            }
        }

        if(this.wLocation != null){
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.wLocation,3f)){
                if(this.isNonStructure(a)){
                    if(this.lastWHit != null && this.lastWHit.containsKey(a.getId())){
                        if(System.currentTimeMillis() >= this.lastWHit.get(a.getId()) + 500){
                            JsonNode spellData = this.parentExt.getAttackData("iceking","spell2");
                            a.addToDamageQueue(this,getSpellDamage(spellData)/2f,spellData);
                            ExtensionCommands.createActorFX(this.parentExt,this.room,a.getId(),"fx_ice_explosion1",500,a.getId()+"_"+Math.random(),true,"",true,false,this.team);
                            this.lastWHit.put(a.getId(),System.currentTimeMillis());
                        }
                    }else if(this.lastWHit != null && !this.lastWHit.containsKey(a.getId())){
                        System.out.println("First time attacking " + a.getId());
                        JsonNode spellData = this.parentExt.getAttackData("iceking","spell2");
                        a.addToDamageQueue(this,getSpellDamage(spellData)/2f,spellData);
                        ExtensionCommands.createActorFX(this.parentExt,this.room,a.getId(),"fx_ice_explosion1",500,a.getId()+"_"+Math.random(),true,"",true,false,this.team);
                        this.lastWHit.put(a.getId(),System.currentTimeMillis());
                    }
                }
            }
        }

        if(this.ultLocation != null){
            for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.ultLocation,5.5f)){
                if(a.getTeam() != this.team || a.getId().equalsIgnoreCase(this.id)){
                    if(a.getId().equalsIgnoreCase(this.id)){
                        this.addEffect("speed",this.getStat("speed"),500,null,false);
                    }else{
                        JsonNode spellData = this.parentExt.getAttackData("iceking","spell3");
                        a.addToDamageQueue(this,getSpellDamage(spellData)/10d,spellData);
                    }
                }
            }
            String flyFx = this.avatar.contains("queen") ? "iceking2_icequeen2" : this.avatar.contains("young") ? "iceking2_young2" : "iceking2";
            List<Actor> affectedActors = Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),ultLocation,5.5f);
            if(affectedActors.contains(this)){
                if(!this.assetSwapped){
                    ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,flyFx);
                    if(System.currentTimeMillis() - lastWhirlwindTime > 2500) ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"ice_king_whirlwind",2500,this.id+"_whirlWind"+Math.random(),true,"",true,false,this.team);
                    this.assetSwapped = true;
                    this.hasDefaultAsset = false;
                    this.lastWhirlwindTime = System.currentTimeMillis();
                    this.setState(ActorState.TRANSFORMED,true);
                }
            }else if(!hasDefaultAsset){
                ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,getSkinAssetBundle());
                this.hasDefaultAsset = true;
                this.assetSwapped = false;
                this.setState(ActorState.TRANSFORMED,false);
            }
        }
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(attackData.has("attackName") && attackData.get("attackName").asText().contains("basic_attack") && this.iceShield){
            a.addState(ActorState.SLOWED,0.25d,2000,null,false);
            a.addEffect("attackSpeed",a.getStat("attackSpeed")*0.33d,2000,null,true);
            this.iceShield = false;
            this.lastAbilityUsed = System.currentTimeMillis()+5000;
            Runnable handlePassiveCooldown = () -> {this.lastAbilityUsed = System.currentTimeMillis();};
            ExtensionCommands.removeFx(this.parentExt,this.room,this.id+"_iceShield");
            ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"passive",true,5000,0);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(handlePassiveCooldown,5000, TimeUnit.MILLISECONDS);
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        super.useAbility(ability, spellData, cooldown, gCooldown, castDelay, dest);
        if(System.currentTimeMillis() > this.lastAbilityUsed) this.lastAbilityUsed = System.currentTimeMillis();
        switch (ability){
            case 1:
                this.canCast[0] = false;
                String freezeSfx = (this.avatar.contains("queen")) ? "vo/vo_ice_queen_freeze" : (this.avatar.contains("young")) ? "vo/vo_ice_king_young_freeze" : "vo/vo_ice_king_freeze";
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,freezeSfx,this.location);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"q",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new IceKingAbilityHandler(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 2:
                this.canCast[1] = false;
                this.wLocation = dest;
                this.lastWHit = new HashMap<>();
                String hailStormSfx = (this.avatar.contains("queen")) ? "vo/vo_ice_queen_hailstorm" : (this.avatar.contains("young")) ? "vo/vo_ice_king_young_hailstorm" : "vo/vo_ice_king_hailstorm";
                ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_ice_king_hailstorm",dest);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,hailStormSfx,this.location);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"ice_king_spell_casting_hand",500,this.id+"_lHand",true,"Bip001 L Hand",true,false,this.team);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"ice_king_spell_casting_hand",500,this.id+"_rHand",true,"Bip001 R Hand",true,false,this.team);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"AoE_iceking_snowballs",this.id+"_snowBalls",3000,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_3",this.id+"_wRing",3000,(float)dest.getX(),(float)dest.getY(),true,this.team,0f);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new IceKingAbilityHandler(ability,spellData,cooldown,gCooldown,dest),3000,TimeUnit.MILLISECONDS);
                break;
            case 3:
                this.canCast[2] = false;
                this.ultActive = true;
                this.ultLocation = this.location;
                String ultimateSfx = (this.avatar.contains("queen")) ? "vo/vo_ice_queen_ultimate" : (this.avatar.contains("young")) ? "vo/vo_ice_king_young_ultimate" : "vo/vo_ice_king_ultimate";
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"sfx_ice_king_ultimate",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,ultimateSfx,this.location);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"fx_target_ring_5.5",this.id+"eRing",6000,(float)this.location.getX(),(float)this.location.getY(),true,this.team,0f);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"ice_king_whirlwind",2500,this.id+"_whirlWind"+Math.random(),true,"",true,false,this.team);
                ExtensionCommands.createWorldFX(this.parentExt,this.room,this.id,"iceKing_freezeGround",this.id+"_ultFreeze",6000,(float)this.location.getX(),(float)this.location.getY(),false,this.team,0f);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new IceKingAbilityHandler(ability,spellData,cooldown,gCooldown,dest),6000,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(this.parentExt,this.player,"e",true,getReducedCooldown(cooldown),gCooldown);
                break;
        }
    }

    private class IceKingProjectile extends Projectile {

        public IceKingProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData("iceking","spell1");
            qVictim = victim;
            qHitTime = System.currentTimeMillis() + 1750;
            victim.addToDamageQueue(IceKing.this,getSpellDamage(spellData),spellData);
            victim.addState(ActorState.ROOTED,0d,1750,"iceKing_snare",true);
            ExtensionCommands.playSound(this.parentExt,victim.getRoom(),victim.getId(),"sfx_ice_king_freeze",victim.getLocation());
            this.destroy();
        }
    }

    private class IceKingAbilityHandler extends AbilityRunnable {

        public IceKingAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            Line2D fireLine = new Line2D.Float(location,dest);
            Line2D newLine = Champion.getMaxRangeLine(fireLine,7.5f);
            fireProjectile(new IceKingProjectile(parentExt,IceKing.this,newLine,9f,0.5f,id+"projectile_iceking_deepfreeze"),"projectile_iceking_deepfreeze", newLine.getP2(), 7.5f);
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
            wLocation = null;
            lastWHit = null;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            if(ultActive){
                ultActive = false;
                assetSwapped = false;
                hasDefaultAsset = true;
                ExtensionCommands.swapActorAsset(parentExt,room,id,getSkinAssetBundle());
                setState(ActorState.TRANSFORMED,false);
            }
            ultLocation = null;
        }

        @Override
        protected void spellPassive() {

        }
    }
}

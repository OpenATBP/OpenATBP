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
import java.util.concurrent.TimeUnit;

public class MagicMan extends UserActor {

    private boolean qHit = false;
    private long passiveIconStarted = 0;
    private boolean passiveActivated = false;

    public MagicMan(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }

    @Override
    public void attack(Actor a) {
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a,new MagicManPassive(a,this.handleAttack(a)),"magicman_projectile"),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if(System.currentTimeMillis() - passiveIconStarted >= 3000 && passiveActivated){
            ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"passive");
            this.passiveActivated = false;
        }
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        switch(ability){
            case 1:
                this.canCast[0] = false;

                break;
            case 2:
                break;
            case 3:
                break;
        }
    }

    private class MagicManAbilityHandler extends AbilityRunnable {

        public MagicManAbilityHandler(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
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

        }

        @Override
        protected void spellPassive() {

        }
    }

    private class SnakeProjectile extends Projectile {

        public SnakeProjectile(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            victim.addState(ActorState.SILENCED,0d,1000,null,false);
            if(!qHit){
                JsonNode spellData = parentExt.getAttackData(MagicMan.this.avatar,"spell1");
                victim.addToDamageQueue(MagicMan.this,getSpellDamage(spellData),spellData);
                qHit = true;
            }
            this.destroy();
        }
    }

    private class MagicManPassive implements Runnable {

        Actor target;
        boolean crit;

        MagicManPassive(Actor target, boolean crit){
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = getPlayerStat("attackDamage");
            if(crit) damage*=2;
            new Champion.DelayedAttack(parentExt,MagicMan.this,target,(int)damage,"basicAttack").run();
            addEffect("speed",getStat("speed")*0.2d,3000,null,false);
            passiveActivated = true;
            if(System.currentTimeMillis() - passiveIconStarted < 3000){
                ExtensionCommands.removeStatusIcon(parentExt,player,"passive");
                passiveIconStarted = System.currentTimeMillis();
            }
            ExtensionCommands.addStatusIcon(parentExt,player,"passive","magicman_spell_4_short_description","icon_magicman_passive",3000);
        }
    }
}

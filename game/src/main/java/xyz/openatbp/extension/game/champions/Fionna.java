package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;

public class Fionna extends UserActor {

    private int dashesRemaining = 0;
    private boolean hitWithDash = false;
    private long dashTime = -1;

    public Fionna(User u, ATBPExtension parentExt) {
        super(u, parentExt);
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
                    Point2D dashPoint = Champion.getDashPoint(this.parentExt,this,dest);
                    ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.getLocation(),dashPoint,20f,true);
                    this.canMove = false;
                    double time = dest.distance(getLocation())/20f;
                    this.dashesRemaining--;
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"sfx_fionna_dash_small",(int)(time*1000),this.id+"_dash"+dashesRemaining,true,"",true,false,this.team);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new FionnaAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),(int)(time*1000), TimeUnit.MILLISECONDS);
                }
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
        }
    }

    private class FionnaAbilityRunnable extends AbilityRunnable{

        public FionnaAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canMove = true;
            int dashInt = dashesRemaining+1;
            double range = 1d;
            String explosionFx = "fionna_dash_explode_small";
            if(dashInt == 1){
                range = 2d;
                explosionFx = "fionna_dash_explode";
            }
            //ExtensionCommands.createWorldFX(parentExt,room,id,explosionFx,explosionFx+"_",);
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
}

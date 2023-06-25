package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class Actor {
    public enum AttackType{ PHYSICAL, SPELL};
    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected String id;
    protected Room room;
    protected int team;
    protected double speed;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected int level = 1;
    protected boolean canMove = true;
    protected double attackSpeed;
    protected double attackCooldown;
    protected ActorType actorType;
    protected double attackRange;
    protected Map<ActorState, Boolean> states = Champion.getBlankStates();
    protected String displayName = "FuzyBDragon";
    protected Map<String, Double> stats; //TODO: Maybe change to ISFSObject
    protected Map<String, Double> tempStats = new HashMap<>();
    protected Map<String, Champion.FinalBuffHandler> buffHandlers = new HashMap<>();


    public double getPHealth(){
        return currentHealth/maxHealth;
    }

    public int getHealth(){
        return (int) currentHealth;
    }

    public int getMaxHealth(){
        return (int) maxHealth;
    }

    public Point2D getLocation(){
        return this.location;
    }

    public String getId(){return this.id;}
    public int getTeam(){return this.team;}

    public void setLocation(Point2D location){
        this.location = location;
    }
    public String getAvatar(){return this.avatar;}
    public ActorType getActorType(){return this.actorType;}
    public void reduceAttackCooldown(){
        this.attackCooldown-=100;
    }

    public boolean withinRange(Actor a){
        return a.getLocation().distance(this.location) <= this.attackRange;
    }

    public void stopMoving(){
        for(User u : room.getUserList()){
            ExtensionCommands.moveActor(parentExt,u,this.id,this.location,this.location, (float) this.speed, false);
        }
    }

    public void setSpeed(float speed){
        this.speed = speed;
    }

    public void setCanMove(boolean move){
        this.canMove = move;
    }

    public double getSpeed(){return this.speed;}


    public void setState(ActorState state, boolean enabled){
        System.out.println(state.toString() + " set to: " + enabled);
        this.states.put(state,enabled);
        ExtensionCommands.updateActorState(this.parentExt,this.room,this.id,state,enabled);
    }

    @Deprecated
    public double getStat(String stat){
        return this.parentExt.getActorStats(this.avatar).get(stat).asDouble();
    }

    public double getNewStat(String stat){
        if(this.stats != null) return this.stats.get(stat);
        else return -1d;
    }

    public double getTempStat(String stat){
        if(!this.hasTempStat(stat)) return 0d;
        return this.tempStats.get(stat);
    }

    public boolean setTempStat(String stat, double delta){ //TODO: Should maybe make this private/protected
        try{
            System.out.println("Subtracting " + stat + " by " + delta);
            if(this.tempStats.containsKey(stat)){
                double tempStat = this.tempStats.get(stat);
                double newStat = tempStat + delta;
                if(newStat == 0){
                    this.tempStats.remove(stat);
                    return true;
                }else{
                    this.tempStats.put(stat,newStat);
                    return false;
                }
            }else{
                this.tempStats.put(stat,delta);
                return false;
            }
        }catch(Exception e){
            e.printStackTrace();
            return true;
        }
    }

    public void updateTempStat(String stat, double delta){
        this.tempStats.put(stat,delta);
    }

    public void handleEffect(String stat, double delta, int duration, String fxId){
        if(!this.buffHandlers.containsKey(stat)){
            System.out.println("Effect increased!");
            this.setTempStat(stat,delta);
        }
        else{
            double currentTempStat = this.getTempStat(stat);
            if(delta > currentTempStat){
                if(fxId.contains("altar"))this.setTempStat(stat,delta);
                else this.setTempStat(stat,delta-currentTempStat);
            }
            if(delta != currentTempStat) this.buffHandlers.get(stat).setDelta(this.getTempStat(stat));
            if(fxId.equalsIgnoreCase("fountainSpeed")) this.buffHandlers.get(stat).setDuration(duration);
            else this.buffHandlers.get(stat).extendBuff(duration);
            if(stat.equalsIgnoreCase("armor") && fxId.contains("altar")){ //Only armor to prevent multiple icons from appearing
                UserActor ua = (UserActor) this;
                ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_defense","altar1_description","icon_altar_armor",1000*60);
            }
            return;
        }
        switch(stat){
            case "speed":
                if(delta > 0){
                    System.out.println("Starting buff handler!");
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,stat,delta,"statusEffect_speed"),duration,TimeUnit.MILLISECONDS);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"statusEffect_speed",duration,this.id+"_"+fxId,true,"Bip01",true,false,this.team);
                }
                break;
            case "healthRegen":
                ExtensionCommands.createActorFX(parentExt,this.room,this.id,"fx_health_regen",duration,this.id+"_"+fxId,true,"Bip01",false,false,this.team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,stat,delta,"fx_health_regen"),duration,TimeUnit.MILLISECONDS);
                break;
            case "attackDamage":
                if(fxId.contains("altar")){ //Keeping the altar buff handling in the RoomHandler to declutter the Actor class for now
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_offense","altar2_description","icon_altar_attack",1000*60);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"altar_buff_offense",duration,this.id+"_"+fxId,true,"Bip01",true,true, ua.getTeam());
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,stat,delta,"altar_buff_offense"),duration,TimeUnit.MILLISECONDS);
                }
                break;
            case "armor":
                if(fxId.contains("altar")){
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_defense","altar1_description","icon_altar_armor",1000*60);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"altar_buff_defense",duration,this.id+"_"+fxId,true,"Bip01",true,true, ua.getTeam());
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,stat,delta,"altar_buff_defense"),duration,TimeUnit.MILLISECONDS);
                }
                break;
            default:
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,stat,delta),duration,TimeUnit.MILLISECONDS);
                break;
        }
    }

    public void handleEffect(ActorState state, double delta, int duration){
        switch(state){
            case POLYMORPH:
                UserActor ua = (UserActor) this;
                ExtensionCommands.swapActorAsset(parentExt,ua.getUser(), this.id,"flambit");
                double speedChange = this.getPlayerStat("speed")*-0.3;
                ua.setTempStat("speed",speedChange);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.FinalBuffHandler(this,ActorState.POLYMORPH,speedChange),duration,TimeUnit.MILLISECONDS);
                break;
        }
    }

    public boolean hasTempStat(String stat){
        return this.tempStats.containsKey(stat);
    }

    public double getPlayerStat(String stat){
        double currentStat = this.stats.get(stat);
        if(this.tempStats.containsKey(stat)){
            return currentStat+this.tempStats.get(stat);
        }
        else return currentStat;
    }
    public String getDisplayName(){ return this.displayName;}
    @Deprecated
    public boolean damaged(Actor a, int damage, JsonNode attackData){
        this.currentHealth-=damage;
        if(this.currentHealth <= 0) this.currentHealth = 0;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id",this.id);
        updateData.putInt("currentHealth", (int) this.currentHealth);
        updateData.putDouble("pHealth", this.getPHealth());
        updateData.putInt("maxHealth", (int) this.maxHealth);
        for(User u : this.room.getUserList()){
            ExtensionCommands.updateActorData(parentExt,u,updateData);
        }
        return this.currentHealth<=0;
    }
    public abstract void attack(Actor a);
    public abstract void die(Actor a);

    public abstract void update(int msRan);
    public void rangedAttack(Actor a){
        System.out.println(this.id + " is using an undefined method.");
    }

    public Room getRoom(){
        return this.room;
    }
    public void changeHealth(int delta){
        ISFSObject data = new SFSObject();
        this.currentHealth+=delta;
        if(this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if(this.currentHealth < 0) this.currentHealth = 0;
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth",(int) this.maxHealth);
        data.putDouble("pHealth",this.getPHealth());
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,data);
    }
    public void setHealth(int currentHealth, int maxHealth){
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        if(this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if(this.currentHealth < 0) this.currentHealth = 0;
        ISFSObject data = new SFSObject();
        data.putInt("currentHealth",(int)this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth",this.getPHealth());
        ExtensionCommands.updateActorData(this.parentExt,this.room,this.id,data);
    }

    public int getMitigatedDamage(double rawDamage, AttackType attackType, Actor attacker){
        try{
            double armor = this.getPlayerStat("armor")*(1-(attacker.getPlayerStat("armorPenetration")/100));
            double spellResist = this.getPlayerStat("spellResist")*(1-(attacker.getPlayerStat("spellPenetration")/100));
            if(armor < 0) armor = 0;
            if(spellResist < 0) spellResist = 0;
            double modifier;
            if(attackType == AttackType.PHYSICAL){
                modifier = 100/(100+armor);
            }else modifier = 100/(100+spellResist);
            System.out.println(this.id + " damaged for " + rawDamage + " raw damage but took: " + Math.round(rawDamage*modifier) + " damage!");
            return (int) Math.round(rawDamage*modifier);
        }catch(Exception e){
            e.printStackTrace();
            return 0;
        }

    }

    public void setStat(String key, double value){
        this.stats.put(key,value);
    }

    protected HashMap<String, Double> initializeStats(){
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar);
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k,actorStats.get(k).asDouble());
        }
        return stats;
    }

    protected AttackType getAttackType(JsonNode attackData){
        if(attackData.has("spellType")) return AttackType.SPELL;
        String type = attackData.get("attackType").asText();
        if(type.equalsIgnoreCase("physical")) return AttackType.PHYSICAL;
        else return AttackType.SPELL;
    }

    public void setBuffHandler(String buff, Champion.FinalBuffHandler handler){
        this.buffHandlers.put(buff,handler);
    }

    public void removeBuffHandler(String buff){
        this.buffHandlers.remove(buff);
    }

    public ATBPExtension getParentExt(){
        return this.parentExt;
    }
}

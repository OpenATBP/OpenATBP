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
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class Actor {
    public enum AttackType{ PHYSICAL, SPELL}
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
    protected Map<String, Double> stats;
    protected Map<String, Double> tempStats = new HashMap<>();
    protected Map<String, Champion.FinalBuffHandler> buffHandlers = new HashMap<>();
    protected List<ISFSObject> damageQueue = new ArrayList<>();


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
        ExtensionCommands.moveActor(parentExt,this.room,this.id,this.location,this.location, (float) this.speed, false);
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

    public double getStat(String stat){
        return this.stats.get(stat);
    }

    public double getTempStat(String stat){
        if(!this.hasTempStat(stat)) return 0d;
        return this.tempStats.get(stat);
    }

    public boolean setTempStat(String stat, double delta){
        try{
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

    public void handleEffect(String stat, double delta, int duration, String fxId){
        if(!this.buffHandlers.containsKey(stat)){
            System.out.println(stat + " increased! for " + this.id + " by " + delta);
            this.setTempStat(stat,delta);
        }
        else{
            double currentTempStat = this.getTempStat(stat);
            if(delta > currentTempStat){
                System.out.println("Delta: " + delta + " vs " + currentTempStat);
                if(fxId.contains("altar"))this.setTempStat(stat,delta);
                else this.setTempStat(stat,delta-currentTempStat);
            }
            if(delta != currentTempStat) this.buffHandlers.get(stat).setDelta(this.getTempStat(stat));
            this.buffHandlers.get(stat).setDuration(duration);
            if(stat.equalsIgnoreCase("armor") && fxId.contains("altar")){ //Only armor to prevent multiple icons from appearing
                UserActor ua = (UserActor) this;
                ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_defense","altar1_description","icon_altar_armor",1000*60);
            }
            return;
        }
        Champion.FinalBuffHandler buffHandler = new Champion.FinalBuffHandler(this,stat,delta);
        switch(stat){
            case "speed":
                String fxName = fxId;
                if(delta > 0 && !fxId.contains("goo") && !fxId.contains("lich")) fxName = "statusEffect_speed";
                if(fxId.contains("goo") && this.getActorType() == ActorType.PLAYER){
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(parentExt,ua.getUser(),"Ooze Buff","Oozed","icon_buff_goomonster",60000);
                }
                buffHandler = new Champion.FinalBuffHandler(this,stat,delta,fxName);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,fxName,duration,this.id+"_"+fxId,true,"Bip01",true,false,this.team);
                break;
            case "healthRegen":
                ExtensionCommands.createActorFX(parentExt,this.room,this.id,"fx_health_regen",duration,this.id+"_"+fxId,true,"Bip01",false,false,this.team);
                buffHandler = new Champion.FinalBuffHandler(this,stat,delta,"fx_health_regen");
                break;
            case "attackDamage":
                if(fxId.contains("altar")){ //Keeping the altar buff handling in the RoomHandler to declutter the Actor class for now
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_offense","altar2_description","icon_altar_attack",1000*60);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"altar_buff_offense",duration,this.id+"_"+fxId,true,"Bip01",true,true, ua.getTeam());
                    buffHandler = new Champion.FinalBuffHandler(this,stat,delta,fxId);
                }
                break;
            case "armor":
                if(fxId.contains("altar")){
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(this.parentExt,ua.getUser(),"altar_buff_defense","altar1_description","icon_altar_armor",1000*60);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"altar_buff_defense",duration,this.id+"_"+fxId,true,"Bip01",true,true, ua.getTeam());
                    buffHandler = new Champion.FinalBuffHandler(this,stat,delta,fxId);
                }
                break;
            case "lifeSteal":
                if(fxId.contains("keeoth")){
                    UserActor ua = (UserActor) this;
                    ExtensionCommands.addStatusIcon(parentExt,ua.getUser(),"Keeoth Buff", "Keeoth Buff", "icon_buff_keeoth",60000);
                }
                buffHandler = new Champion.FinalBuffHandler(this,stat,delta,fxId);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,fxId,duration,this.id+"_"+fxId,true,"Bip01",true,false,this.team);
                break;
            default:
                buffHandler = new Champion.FinalBuffHandler(this,stat,delta);
                this.setBuffHandler(stat,buffHandler);

                break;
        }
        this.setBuffHandler(stat,buffHandler);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(buffHandler,duration,TimeUnit.MILLISECONDS);
    }

    public void handleEffect(ActorState state, double delta, int duration, String fxName){
        Champion.FinalBuffHandler buffHandler = null;
        switch(state){
            case POLYMORPH:
                UserActor ua = (UserActor) this;
                ExtensionCommands.swapActorAsset(parentExt,this.room, this.id,"flambit");
                double speedChange = this.getPlayerStat("speed")*-0.3;
                ua.setTempStat("speed",speedChange);
                buffHandler = new Champion.FinalBuffHandler(this,ActorState.POLYMORPH,speedChange);
                break;
            case SLOWED:
                if(this.buffHandlers.containsKey("slowed")){
                    double currentStat = this.getTempStat("speed");
                    if(delta < currentStat){
                        double diff = currentStat-delta;
                        this.setTempStat("speed",diff);
                    }
                    if(delta != currentStat)this.buffHandlers.get("slowed").setDelta(this.getTempStat("speed"));
                    this.buffHandlers.get("slowed").setDuration(duration);
                    return;
                }else{
                    this.setTempStat("speed",delta);
                    buffHandler = new Champion.FinalBuffHandler(this,ActorState.SLOWED,delta);
                }
                break;
            default:
                buffHandler = new Champion.FinalBuffHandler(this,state,delta);
                break;
        }
        this.setState(state,true);
        SmartFoxServer.getInstance().getTaskScheduler().schedule(buffHandler,duration,TimeUnit.MILLISECONDS);
        this.setBuffHandler(state.name().toLowerCase(),buffHandler);
    }

    public void handleCharm(UserActor charmer, int duration){
        if(!this.states.get(ActorState.CHARMED)){
            Champion.FinalBuffHandler buffHandler = new Champion.FinalBuffHandler(this,ActorState.CHARMED,0f);
            this.setState(ActorState.CHARMED,true);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(buffHandler,duration,TimeUnit.MILLISECONDS);
            this.setBuffHandler("charmed",buffHandler);
            this.setTarget(charmer);
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

    public abstract void handleKill(Actor a, JsonNode attackData);

    public boolean damaged(Actor a, int damage, JsonNode attackData){
        this.currentHealth-=damage;
        if(this.currentHealth <= 0) this.currentHealth = 0;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id",this.id);
        updateData.putInt("currentHealth", (int) this.currentHealth);
        updateData.putDouble("pHealth", this.getPHealth());
        updateData.putInt("maxHealth", (int) this.maxHealth);
        ExtensionCommands.updateActorData(parentExt,this.room,updateData);
        return this.currentHealth<=0;
    }

    public void addToDamageQueue(Actor attacker, double damage, JsonNode attackData){
        if(this.currentHealth <= 0) return;
        ISFSObject data = new SFSObject();
        data.putClass("attacker",attacker);
        data.putDouble("damage",damage);
        data.putClass("attackData",attackData);
        this.damageQueue.add(data);
    }

    public void handleDamageQueue(){
        List<ISFSObject> queue = new ArrayList<>(this.damageQueue);
        this.damageQueue = new ArrayList<>();
        if(this.currentHealth <= 0){
            return;
        }
        for(ISFSObject data : queue){
            Actor damager = (Actor) data.getClass("attacker");
            double damage = data.getDouble("damage");
            JsonNode attackData = (JsonNode) data.getClass("attackData");
            if(this.damaged(damager,(int)damage,attackData)){
                damager.handleKill(this,attackData);
                this.die(damager);
                return;
            }
        }
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

    public void addDamageGameStat(UserActor ua, double value, AttackType type){
        ua.addGameStat("damageDealtTotal",value);
        if(type == AttackType.PHYSICAL) ua.addGameStat("damageDealtPhysical",value);
        else ua.addGameStat("damageDealtSpell",value);
    }

    public boolean canMove(){
        for(ActorState s : this.states.keySet()){
            if(s == ActorState.ROOTED || s == ActorState.STUNNED || s == ActorState.FEARED || s == ActorState.CHARMED || s == ActorState.AIRBORNE){
                if(this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    public boolean canAttack(){
        for(ActorState s : this.states.keySet()){
            if(s == ActorState.STUNNED || s == ActorState.FEARED || s == ActorState.CHARMED || s == ActorState.AIRBORNE || s == ActorState.POLYMORPH){
                if(this.states.get(s)) return false;
            }
        }
        if(this.attackCooldown < 0) this.attackCooldown = 0;
        return this.attackCooldown == 0;
    }

    public String getPortrait(){
        return this.getAvatar();
    }

    public abstract void setTarget(Actor a);
}

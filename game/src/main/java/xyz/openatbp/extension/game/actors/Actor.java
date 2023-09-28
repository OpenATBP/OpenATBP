package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MovementManager;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class Actor {
    public enum AttackType{ PHYSICAL, SPELL}
    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected Line2D movementLine;
    protected float timeTraveled;
    protected String id;
    protected Room room;
    protected int team;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected int level = 1;
    protected boolean canMove = true;
    protected double attackCooldown;
    protected ActorType actorType;
    protected Map<ActorState, Boolean> states = Champion.getBlankStates();
    protected String displayName = "FuzyBDragon";
    protected Map<String, Double> stats;
    protected Map<String, Double> tempStats = new HashMap<>();
    protected Map<String, ISFSObject> activeBuffs = new HashMap<>();
    protected List<ISFSObject> damageQueue = new ArrayList<>();
    protected Actor target;
    protected List<Point2D> path;
    protected int pathIndex = 1;


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
    public int getOppositeTeam(){
        if(this.getTeam() == 1) return 0;
        else return 1;
    }

    public void setLocation(Point2D location){
        this.location = location;
        this.movementLine = new Line2D.Float(location,location);
        this.timeTraveled = 0f;
    }
    public String getAvatar(){return this.avatar;}
    public ActorType getActorType(){return this.actorType;}
    public void reduceAttackCooldown(){
        this.attackCooldown-=100;
    }

    public boolean withinRange(Actor a){
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public void stopMoving(){
        this.movementLine = new Line2D.Float(this.location,this.location);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(parentExt,this.room,this.id,this.location,this.location, 5f, false);
    }

    protected boolean isStopped(){
        return this.location.distance(this.movementLine.getP2()) < 0.01d;
    }

    public void setCanMove(boolean move){
        this.canMove = move;
    }

    public double getSpeed(){return this.getPlayerStat("speed");}


    public void setState(ActorState state, boolean enabled){
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

    public void move(Point2D destination){
        this.movementLine = new Line2D.Float(this.location,destination);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,destination, (float) this.getPlayerStat("speed"),true);
    }

    public void moveWithCollision(Point2D dest){
        Line2D testLine = new Line2D.Float(this.location,dest);
        Point2D newPoint = MovementManager.getPathIntersectionPoint(this.parentExt,testLine);
        if(newPoint != null){
            this.move(newPoint);
        }else this.move(dest);
    }

    public void setPath(List<Point2D> path){

        Line2D pathLine = new Line2D.Float(this.location,path.get(1));
        Point2D dest = MovementManager.getPathIntersectionPoint(parentExt,pathLine);
        if(dest == null) dest = path.get(1);
        this.path = path;
        this.pathIndex = 1;
        this.move(dest);
    }

    public void clearPath(){
        this.path = null;
        this.pathIndex = 1;
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

    protected void handleActiveEffects(){
        Set<String> keys = new HashSet<>(this.activeBuffs.keySet());
        for(String k : keys){
            ISFSObject data = this.activeBuffs.get(k);
            if(System.currentTimeMillis() >= data.getLong("endTime")){ //Checks to see if the effect has ended
                System.out.println("Effect: " + k + " is ending!");
                if(data.containsKey("newEndTime")){ //If the effect has been modified, run handler for restarting the effect
                    if(data.containsKey("state")) this.handleStateEnd(data);
                    else this.handleEffectEnd(k,data);
                }else{ //Runs if there is no modification and actor should go back to normal
                    if(data.containsKey("state")){ //Runs if the effect is a state
                        ActorState state = (ActorState) data.getClass("state");
                        this.setState(state,false);
                        if(data.containsKey("delta")){ //If the state had a stat effect, reset it
                            this.setTempStat(data.getUtfString("stat"),data.getDouble("delta")*-1);
                        }
                        if(state == ActorState.POLYMORPH){
                            UserActor ua = (UserActor) this;
                            ExtensionCommands.swapActorAsset(this.parentExt,this.room,this.id,ua.getSkinAssetBundle());
                            if(!this.activeBuffs.containsKey(ActorState.SLOWED.toString())) this.setState(ActorState.SLOWED, false);
                        }
                    }else{ //Resets stat back to normal by removing what it was modified by
                        this.setTempStat(k,data.getDouble("delta")*-1);
                    }
                    this.activeBuffs.remove(k);
                }
            }else{
                if(data.containsKey("fxId") && System.currentTimeMillis() >= data.getLong("fxEndTime")){
                    int fxDuration = (int)(data.getLong("endTime")-System.currentTimeMillis());
                    System.out.println("New effect playing for " + fxDuration);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,data.getUtfString("fxId"),fxDuration,this.id+"_"+data.getUtfString("fxId"),true,"",true,false,this.team);
                    this.activeBuffs.get(k).putLong("fxEndTime",data.getLong("endTime"));
                }
                if(data.containsKey("state")){
                    ActorState state = (ActorState) data.getClass("state");
                    if(state == ActorState.CHARMED && this.target != null){
                        if(this.location.distance(this.movementLine.getP2()) < 0.01d){
                            this.movementLine = MovementManager.getColliderLine(this.parentExt,this.room,new Line2D.Float(this.location, this.target.getLocation()));
                            this.timeTraveled = 0f;
                            ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,this.movementLine.getP2(), (float) this.getPlayerStat("speed"),true);
                        }
                    }
                }
            }
        }
    }

    protected void handleEffectEnd(String stat, ISFSObject data){
        double newDelta = data.getDouble("newDelta");
        double currentDelta = data.getDouble("delta");
        double diff = newDelta-currentDelta;
        this.setTempStat(stat,diff);
        ISFSObject newData = new SFSObject();
        newData.putDouble("delta",this.getTempStat(stat)); //Sets the change of the end of the effect to the total of the temp stat
        //TODO: Potential issue - could be messy if the same stat is impacted twice (i.e. speed and slow/polymorph)
        newData.putLong("endTime",data.getLong("newEndTime"));
        newData.putBool("stacks",data.getBool("stacks"));
        if(data.containsKey("newFxId")){ //Checks to see if an effect should be run at the expiration of the effect
            String fxId = data.getUtfString("newFxId");
            newData.putUtfString("fxId",fxId);
            newData.putLong("fxEndTime",data.getLong("newEndTime"));
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,fxId,(int)(data.getLong("newEndTime")-System.currentTimeMillis()),this.id+"_"+fxId,true,"",true,false,this.team);
        }
        this.activeBuffs.put(stat,newData);
    }

    protected void handleStateEnd(ISFSObject data){
        ActorState state = (ActorState) data.getClass("state");
        ISFSObject newData = new SFSObject();
        newData.putLong("endTime",data.getLong("newEndTime"));
        newData.putClass("state",state);
        if(state == ActorState.SLOWED){ //If the state is a slow, removes the difference between the modified stat and the original stat change
            double newDelta = data.getDouble("newDelta");
            double currentDelta = data.getDouble("delta");
            double diff = newDelta-currentDelta;
            this.setTempStat("speed",diff);
            newData.putUtfString("stat","speed");
            //TODO: Potential issue that "delta" is not set again in newData but it seems to run fine right now? for some reason...
            newData.putDouble("delta",diff);
        }
        if(data.containsKey("newFxId")){ //Checks to see if an effect should be run at the expiration of the effect
            String fxId = data.getUtfString("newFxId");
            newData.putUtfString("fxId",fxId);
            newData.putLong("fxEndTime",data.getLong("newEndTime"));
            ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,fxId,(int)(data.getLong("newEndTime")-System.currentTimeMillis()),this.id+"_"+fxId,true,"",true,false,this.team);
        }
        this.activeBuffs.put(state.toString(),newData);
    }

    public void addEffect(String stat, double delta, int duration, String fxId, boolean stacks) {
        ISFSObject data = new SFSObject();
        if (!this.activeBuffs.containsKey(stat)) { //Runs if there is no existing stat effect
            long endTime = System.currentTimeMillis() + duration;
            data.putDouble("delta", delta);
            data.putLong("endTime", endTime);
            if (fxId != null) {
                data.putUtfString("fxId", fxId);
                data.putLong("fxEndTime", endTime);
                data.putInt("fxDuration", duration);
                ExtensionCommands.createActorFX(this.parentExt, this.room, this.id, fxId, duration, this.id + "_" + fxId, true, "", true, false, this.team);
            }
            data.putBool("stacks",stacks);
            this.setTempStat(stat, delta);
            this.activeBuffs.put(stat, data);
        } else { //Runs if existing effect exists
            ISFSObject currentData = this.activeBuffs.get(stat);
            double currentDelta = currentData.getDouble("delta");
            if (currentDelta == delta) { //Runs if proposed stat change is the same as the current change
                if(!currentData.getBool("stacks")){ //If there is not a stacking effect, just extend the current effect's time
                    currentData.putLong("endTime", System.currentTimeMillis() + duration);
                }else{ //If the effect should stack, increase the current stack and set "newDelta" for future handling
                    this.setTempStat(stat, delta);
                    currentData.putLong("newEndTime",System.currentTimeMillis() + duration);
                    currentData.putDouble("newDelta",delta);
                }
            } else { //Runs if proposed stat change is different from current change
                if(currentData.containsKey("newDelta")){ //Checks to see if the effect has already been modified
                    double currentNewDelta = currentData.getDouble("newDelta");
                    if(currentNewDelta == delta){ //Checks to see if the modified stat change is the same as the proposed change
                        if(stacks){ //If the effect should stack, increase the actor's stat and then change the effect's modified "delta"
                            this.setTempStat(stat,delta);
                            currentData.putDouble("newDelta",currentNewDelta+delta);
                        }
                        currentData.putLong("newEndTime",System.currentTimeMillis() + duration);
                    }
                }else{ //Runs if the effect has not been modified | Increases the actor's stat and sets "newDelta" to be used later
                    this.setTempStat(stat, delta);
                    double currentNewDelta = 0d;
                    currentData.putDouble("newDelta", currentNewDelta+delta);
                    currentData.putLong("newEndTime", System.currentTimeMillis() + duration);
                }
            }
            this.activeBuffs.put(stat, currentData);
        }
    }

    public void addState(ActorState state, double delta, int duration, String fxId, boolean stacks){
        if(this.getState(ActorState.IMMUNITY) && this.isCC(state)) return;
        if(!this.activeBuffs.containsKey(state.toString())){ //Runs if there is no existing state/effect
            ISFSObject data = new SFSObject();
            long endTime = System.currentTimeMillis()+duration;
            data.putClass("state",state);
            data.putLong("endTime",endTime);
            if(fxId != null){
                data.putUtfString("fxId",fxId);
                data.putLong("fxEndTime",endTime);
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,fxId,duration,this.id+"_"+fxId,true,"",true,false,this.team);
            }
            switch(state){
                case SLOWED:
                    data.putUtfString("stat","speed");
                    double speedRemoval = this.getStat("speed")*delta;
                    this.setTempStat("speed",speedRemoval*-1);
                    data.putDouble("delta",speedRemoval*-1);
                    break;
                case POLYMORPH:
                    ExtensionCommands.swapActorAsset(parentExt,this.room, this.id,"flambit");
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"flambit_aoe",3000,this.id+"_flambit_aoe",true,"",true,false,this.team);
                    ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"fx_target_ring_2",3000,this.id+"_flambit_ring_"+Math.random(),true,"",true,true,getOppositeTeam());
                    data.putUtfString("stat","speed");
                    double speedChange = this.getStat("speed")*-0.3;
                    this.setTempStat("speed",speedChange);
                    data.putDouble("delta",speedChange);
                    this.setState(ActorState.SLOWED, true);
                    break;
                case ROOTED:
                    this.stopMoving();
                    break;
            }
            this.setState(state,true);
            this.activeBuffs.put(state.toString(),data);
        }else{ //Runs if there is an existing effect
            if(state == ActorState.SLOWED){
                ISFSObject currentData = this.activeBuffs.get(state.toString());
                double currentDelta = currentData.getDouble("delta");
                double speedRemoval = this.getStat("speed")*delta*-1;
                if (currentDelta == speedRemoval) { //If the current effect has the same stat change, just extend the length of the state
                    currentData.putLong("endTime", System.currentTimeMillis() + duration);
                } else { //If the change is different, adjust the current temp stat and put in the new speed diff for future handling
                    this.setTempStat("speed", speedRemoval);
                    currentData.putDouble("newDelta", speedRemoval);
                    currentData.putLong("newEndTime", System.currentTimeMillis() + duration);
                }
            }else if(stacks){ //If the state stacks, extend the state duration
                this.activeBuffs.get(state.toString()).putLong("endTime",System.currentTimeMillis()+duration);
            }
        }
    }

    public void handleCharm(UserActor charmer, int duration){
        if(!this.states.get(ActorState.CHARMED)){
            this.setState(ActorState.CHARMED,true);
            this.setTarget(charmer);
            this.movementLine = new Line2D.Float(this.location,charmer.getLocation());
            this.movementLine = MovementManager.getColliderLine(this.parentExt,this.room,this.movementLine);
            this.timeTraveled = 0f;
            this.addState(ActorState.CHARMED,0d,duration,null,false);
            if(this.canMove) ExtensionCommands.moveActor(this.parentExt,this.room,this.id,this.location,this.movementLine.getP2(), (float) this.getSpeed(),true);
        }
    }

    public void handleFear(UserActor feared, int duration){
        if(!this.states.get(ActorState.FEARED)){
            this.setState(ActorState.FEARED,true);
            Line2D oppositeLine = Champion.getMaxRangeLine(new Line2D.Float(feared.getLocation(),this.location),10f);
            oppositeLine = MovementManager.getColliderLine(this.parentExt,this.room,oppositeLine);
            this.movementLine = new Line2D.Float(this.location,oppositeLine.getP2());
            this.timeTraveled = 0f;
            this.addState(ActorState.FEARED,0d,duration,null,false);
            ExtensionCommands.moveActor(parentExt,room,id,this.location, oppositeLine.getP2(), (float) this.getPlayerStat("speed"),true);
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
        if(attacker.getActorType() == ActorType.PLAYER && this.getAttackType(attackData) == AttackType.SPELL){
            UserActor ua = (UserActor) attacker;
            ua.handleSpellVamp(damage);
        }
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
        data.putInt("health",(int)this.maxHealth);
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

    public void removeEffects(){
        for(ActorState state : ActorState.values()){
            if(state != ActorState.TRANSFORMED) this.setState(state,false);
        }
        Set<String> effectSet = this.activeBuffs.keySet();
        for(String s : effectSet){
            ISFSObject data = this.activeBuffs.get(s);
            if(data.containsKey("fxId")) ExtensionCommands.removeFx(this.parentExt,this.room,data.getUtfString("fxId"));
            if(data.containsKey("state")){
                if(data.containsKey("stat")){
                    this.setTempStat(data.getUtfString("stat"),this.getTempStat(data.getUtfString("stat"))*-1);
                }
            }else{
                this.setTempStat(s,this.getTempStat(s)*-1);
            }
        }
        this.activeBuffs = new HashMap<>();
        this.setCanMove(true);
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

    public boolean getState(ActorState state){
        return this.states.get(state);
    }

    private boolean isCC(ActorState state){
        ActorState[] cc = {ActorState.SLOWED, ActorState.AIRBORNE, ActorState.STUNNED, ActorState.ROOTED, ActorState.BLINDED, ActorState.SILENCED, ActorState.FEARED, ActorState.CHARMED, ActorState.POLYMORPH};
        for(ActorState c : cc){
            if(state == c) return true;
        }
        return false;
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

    public void knockback(Point2D source){
        this.stopMoving();
        Line2D originalLine = new Line2D.Double(source,this.location);
        Line2D knockBackLine = Champion.extendLine(originalLine,6f);
        Line2D finalLine = new Line2D.Double(this.location,MovementManager.getDashPoint(this,knockBackLine));
        this.addState(ActorState.AIRBORNE,0d,250,null,false);
        double speed = this.location.distance(finalLine.getP2()) / 0.25f;
        ExtensionCommands.knockBackActor(this.parentExt,this.room,this.id,this.location, finalLine.getP2(), (float)speed, false);
        this.setLocation(finalLine.getP2());
    }

    public void handlePathing(){
        if(this.path != null && this.location.distance(this.movementLine.getP2()) <= 0.9d){
            if(this.pathIndex+1 != this.path.size()){
                this.pathIndex++;
                if(MovementManager.insideAnyObstacle(this.parentExt,this.path.get(this.pathIndex))) this.moveWithCollision(this.path.get(this.pathIndex));
                else this.move(this.path.get(this.pathIndex));
            }else{
                this.path = null;
            }
        }
    }

    public void pulled(Point2D source){
        this.stopMoving();
        Line2D pullLine = new Line2D.Float(this.location,source);
        Line2D newLine = new Line2D.Double(this.location,MovementManager.findPullPoint(pullLine,1.2f));
        Line2D finalLine = new Line2D.Double(this.location,MovementManager.getDashPoint(this,newLine));
        this.addState(ActorState.AIRBORNE,0d,250,null,false);
        double speed = this.location.distance(finalLine.getP2()) / 0.25f;
        ExtensionCommands.knockBackActor(this.parentExt,this.room,this.id,this.location, finalLine.getP2(), (float)speed, false);
        this.setLocation(finalLine.getP2());
    }

    public String getPortrait(){
        return this.getAvatar();
    }

    public String getSkinAssetBundle(){
        return this.parentExt.getActorData(this.avatar).get("assetBundle").asText();
    }

    public abstract void setTarget(Actor a);
}

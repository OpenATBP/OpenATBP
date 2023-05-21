package xyz.openatbp.extension.game;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class Actor {
    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected String id;
    protected Room room;
    protected int team;
    protected double speed;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected boolean canMove = true;
    protected double attackSpeed;
    protected double attackCooldown;
    protected ActorType actorType;
    protected double attackRange;
    protected Map<ActorState, Boolean> states = Champion.getBlankStates();
    protected Map<ActorState, ScheduledFuture<?>> stateCommands = new HashMap<>(ActorState.values().length);

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

    public double getAttackCooldown(){
        return this.attackCooldown;
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
        if(!enabled) this.stateCommands.remove(state);
        ExtensionCommands.updateActorState(this.parentExt,this.room,this.id,state,enabled);
    }

    public void getEffect(ActorState state, int duration, double value){
        //TODO: Extend durations rather than create new task schedulers
        Champion.EffectHandler effectHandler = null;
        boolean alreadyHasEffect = this.stateCommands.containsKey(state) && this.stateCommands.get(state) != null;
        if(!alreadyHasEffect) this.setState(state,true);
        try{
            switch(state){
                case SLOWED:
                    if(!alreadyHasEffect){
                        System.out.println("Slowing actor!");
                        effectHandler = new Champion.EffectHandler(this,state,this.speed);
                        this.speed*=(1-value);
                    }
                    else effectHandler = new Champion.EffectHandler(this,state, this.speed/(1-value));
                    break;
            }
            if(alreadyHasEffect && effectHandler != null){
                System.out.println("Cancelling set back!");
                this.stateCommands.get(state).cancel(true);
            }
            this.stateCommands.put(state,SmartFoxServer.getInstance().getTaskScheduler().schedule(effectHandler,duration, TimeUnit.MILLISECONDS));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public double getStat(String stat){
        return this.parentExt.getActorStats(this.avatar).get(stat).asDouble();
    }

    public boolean damaged(Actor a, int damage){
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
}

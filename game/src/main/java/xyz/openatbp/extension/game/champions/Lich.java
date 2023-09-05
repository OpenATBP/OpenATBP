package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Lich extends UserActor{

    private Skully skully;
    private long lastSkullySpawn;
    private boolean qActivated = false;
    private List<Point2D> slimePath = null;
    private HashMap<String, Long> slimedEnemies = null;
    private boolean ultStarted = false;
    private long ultTime = -1;
    private int ultTeleportsRemaining = 0;
    private Point2D ultLocation;

    public Lich(User u, ATBPExtension parentExt){
        super(u,parentExt);
        lastSkullySpawn = 0;
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        if (skully == null && System.currentTimeMillis() - lastSkullySpawn > getReducedCooldown(40000)) {
            this.spawnSkully();
        }
        switch (ability) {
            case 1: //Q
                double statIncrease = this.getStat("speed") * 0.25d;
                this.addEffect("speed",statIncrease,6000,null,false);
                qActivated = true;
                slimePath = new ArrayList<>();
                slimedEnemies = new HashMap<>();
                ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"lichking_deathmist",6000,this.id+"_lichTrail",true,"",true,false,this.team);
                ExtensionCommands.playSound(parentExt,room,id,"sfx_lich_trail",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lich_trail",this.location);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new TrailHandler(), 6000, TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"q",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LichAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);
                break;
            case 2: //W
                this.stopMoving();
                ExtensionCommands.playSound(parentExt,room,this.id,"sfx_lich_charm_shot",this.location);
                ExtensionCommands.playSound(this.parentExt,this.room,this.id,"vo/vo_lich_charm_shot",this.location);
                Line2D fireLine = new Line2D.Float(this.getRelativePoint(false),dest);
                Line2D newLine = Champion.getMaxRangeLine(fireLine,8f);
                this.fireProjectile(new LichCharm(parentExt,this,newLine,9f,0.5f,this.id+"projectile_lich_charm"),"projectile_lich_charm",dest,8f);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"w",this.canUseAbility(ability),getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LichAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),gCooldown,TimeUnit.MILLISECONDS);

                break;
            case 3: //E
                if(!this.ultStarted){
                    this.canMove = false;
                    this.stopMoving();
                    ExtensionCommands.playSound(parentExt,room,this.id,"sfx_lich_death_pool",this.location);
                    ExtensionCommands.playSound(parentExt,room,"","sfx_lich_well",dest);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new LichAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),1000,TimeUnit.MILLISECONDS);
                }
                else{
                    if(this.ultTeleportsRemaining > 0){
                        Point2D testLocation = Champion.getTeleportPoint(parentExt,player,this.location,ultLocation);
                        ExtensionCommands.snapActor(parentExt,room,this.id,testLocation,testLocation,false);
                        this.setLocation(testLocation);
                        if(this.skully != null){
                            this.skully.setLocation(testLocation);
                            ExtensionCommands.snapActor(parentExt,room,this.skully.getId(),testLocation,testLocation,false);
                        }
                        ExtensionCommands.createActorFX(parentExt,room,this.id,"lich_teleport",500,this.id+"_lichTeleport",true,"Bip01",true,false,team);
                        this.ultTeleportsRemaining--;
                    }
                }
                break;
            case 4: //Passive
                break;
        }
        this.canCast[ability-1] = false;
    }

    @Override
    public void attack(Actor a){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new PassiveAttack(this,a,this.handleAttack(a)),"lich_projectile"),500,TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a){
        super.die(a);
        //if(this.skully != null) this.setSkullyTarget(a);
        this.skully.die(this.skully);

    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData){
        boolean returnVal = super.damaged(a,damage,attackData);
        if(!returnVal && this.skully != null && this.skully.getTarget() == null) this.setSkullyTarget(a);
        return returnVal;
    }

    @Override
    public void update(int msRan){
        super.update(msRan);
        if(this.skully != null) skully.update(msRan);
        if(this.qActivated){
            this.slimePath.add(this.location);
            for(Point2D slime : this.slimePath){
                for(Actor a : this.parentExt.getRoomHandler(this.room.getId()).getActors()){
                    if(a.getTeam() != this.team && a.getLocation().distance(slime) < 0.5){
                        JsonNode attackData = this.parentExt.getAttackData(getAvatar(),"spell1");
                        if(slimedEnemies.containsKey(a.getId())){
                            if(System.currentTimeMillis() - slimedEnemies.get(a.getId()) >= 1000){
                                handleSpellVamp(getSpellDamage(attackData));
                                a.addToDamageQueue(this,getSpellDamage(attackData),attackData);
                                a.addState(ActorState.SLOWED,0.3d,1500,null,false);
                                slimedEnemies.put(a.getId(),System.currentTimeMillis());
                                break;
                            }
                        }else{
                            handleSpellVamp(getSpellDamage(attackData));
                            a.addToDamageQueue(this,getSpellDamage(attackData),attackData);
                            a.addState(ActorState.SLOWED,0.3d,1500,null,false);
                            slimedEnemies.put(a.getId(),System.currentTimeMillis());
                            break;
                        }
                    }
                }
            }
            if(this.slimePath.size() > 150) this.slimePath.remove(this.slimePath.size()-1);
        }

        if(this.ultStarted && System.currentTimeMillis() - this.ultTime >= 500){
            this.ultTime = System.currentTimeMillis();
            boolean damageDealt = false;
            JsonNode spellData = this.parentExt.getAttackData(this.getAvatar(),"spell3");
            for(Actor a : Champion.getActorsInRadius(parentExt.getRoomHandler(this.room.getId()),ultLocation,3f)){
                if(a.getTeam() != this.team && a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER){
                    if(!damageDealt) damageDealt = true;
                    double damage = getSpellDamage(spellData)/10d;
                    handleSpellVamp(damage);
                    a.addToDamageQueue(this,Math.round(damage),spellData);
                }
            }
            if(damageDealt){
                ExtensionCommands.playSound(this.parentExt,this.room,"","sfx_lich_charm_shot_hit",this.ultLocation);
            }
        }
    }

    private void spawnSkully(){
        this.skully = new Skully();
        this.parentExt.getRoomHandler(this.room.getId()).addCompanion(this.skully);
        this.lastSkullySpawn = System.currentTimeMillis();
        ExtensionCommands.addStatusIcon(this.parentExt,this.player,"icon_lich_passive","lich_spell_4_short_description","icon_lich_passive",20000);
        ExtensionCommands.actorAbilityResponse(parentExt,player,"passive",true,getReducedCooldown(40000),2);
        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"lich_skeleton_poof",500,this.id+"_skeleton_poof",false,"",false,false,this.team);
    }

    public void setSkullyTarget(Actor a){
        if(this.skully != null) this.skully.setTarget(a);
    }

    private void handleSkullyDeath(){
        this.parentExt.getRoomHandler(this.room.getId()).removeCompanion(this.skully);
        this.skully = null;
        ExtensionCommands.removeStatusIcon(this.parentExt,this.player,"icon_lich_passive");
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData){
        if(this.skully != null) this.skully.resetTarget();
        if(attackData.has("spellType") && (attackData.get("spellType").asText().equalsIgnoreCase("spell1") || attackData.get("spellType").asText().equalsIgnoreCase("passive"))) this.increaseStat("spellDamage",1);
    }

    private class TrailHandler implements Runnable {
        @Override
        public void run() {
            qActivated = false;
            slimePath = null;
            slimedEnemies = null;
        }
    }

    private class Skully extends Actor {

        private Line2D movementLine;
        private float timeTraveled = 0f;
        private Actor target;
        private Point2D lastLichLocation;
        private Point2D lastTargetLocation;
        private long timeOfBirth;

        Skully(){
            this.room = Lich.this.room;
            this.parentExt = Lich.this.parentExt;
            this.currentHealth = 500;
            this.maxHealth = 500;
            this.location = Lich.this.location;
            this.avatar = "skully";
            this.id = "skully_"+Lich.this.id;
            this.team = Lich.this.team;
            movementLine = new Line2D.Float(this.location,this.location);
            this.lastLichLocation = Lich.this.getRelativePoint(false);
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            ExtensionCommands.createActor(parentExt,room,this.id,this.avatar,this.location,0f,this.team);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {
            Lich.this.increaseStat("spellDamage",1);
            this.resetTarget();
        }

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return super.damaged(a,damage,attackData);
        }

        @Override
        public void attack(Actor a) {
            ExtensionCommands.attackActor(parentExt,room,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),false,true);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new PassiveAttack(this,a,false),300,TimeUnit.MILLISECONDS);
            this.attackCooldown = 1000;
        }

        @Override
        public void die(Actor a) {
            System.out.println(this.id + " has died! ");
            ExtensionCommands.knockOutActor(parentExt,room,this.id,a.getId(),40000);
            Lich.this.handleSkullyDeath();
            ExtensionCommands.destroyActor(parentExt,room,this.id);
        }

        @Override
        public void update(int msRan) {
            if(System.currentTimeMillis() - timeOfBirth >= 20*1000){
                this.die(this);
            }
            this.handleDamageQueue();
            this.location = this.getRelativePoint();
            if(this.attackCooldown > 0) this.reduceAttackCooldown();
            if(this.target == null){
                Point2D lichLocation = Lich.this.getRelativePoint(false);

                if(this.location.distance(lichLocation) > 3 && this.lastLichLocation.distance(lichLocation) > 0.01){
                    float deltaDistance = (float) (this.location.distance(lichLocation)-3f);
                    this.lastLichLocation = lichLocation;
                    this.move(Champion.getDistanceLine(new Line2D.Float(this.location,lichLocation),deltaDistance).getP2());
                    this.timeTraveled = 0.1f;
                }else{
                    this.timeTraveled+=0.1f;
                }
            }else{
                if(this.withinRange(this.target) && this.attackCooldown <= 0){
                    this.attack(this.target);
                }else if(!this.withinRange(this.target)){
                    if(this.target.getLocation().distance(this.lastTargetLocation) > 0.01f){
                        this.lastTargetLocation = this.target.getLocation();
                        this.move(this.target.getLocation());
                        this.timeTraveled = 0.1f;
                    }
                }
            }
        }

        public Point2D getRelativePoint(){ //Gets player's current location based on time
            Point2D rPoint = new Point2D.Float();
            if(movementLine == null) return this.location;
            float x2 = (float) movementLine.getX2();
            float y2 = (float) movementLine.getY2();
            float x1 = (float) movementLine.getX1();
            float y1 = (float) movementLine.getY1();
            Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
            double dist = movementLine.getP1().distance(movementLine.getP2());
            double time = dist/1.75f;
            double currentTime = this.timeTraveled;
            if(currentTime>time) currentTime=time;
            double currentDist = 1.75f*currentTime;
            float x = (float)(x1+(currentDist/dist)*(x2-x1));
            float y = (float)(y1+(currentDist/dist)*(y2-y1));
            rPoint.setLocation(x,y);
            if(dist != 0) return rPoint;
            else return this.location;
        }

        public void setTarget(Actor a){
            if(this.target == a) return;
            this.target = a;
            this.lastTargetLocation = a.getLocation();
            this.move(a.getLocation());
            this.timeTraveled = 0.1f;
        }

        public void resetTarget(){
            this.target = null;
            this.movementLine = new Line2D.Float(this.location,Lich.this.getRelativePoint(false));
            this.timeTraveled = 0.1f;
        }

        public Actor getTarget(){
            return this.target;
        }
    }

    private class PassiveAttack implements Runnable {

        Actor attacker;
        Actor target;
        boolean crit;

        PassiveAttack(Actor attacker, Actor target, boolean crit){
            this.attacker = attacker;
            this.target = target;
            this.crit = crit;
        }

        @Override
        public void run() {
            if(attacker.getClass() == Lich.class){
                double damage = this.attacker.getPlayerStat("attackDamage");
                if(crit) damage*=2;
                new Champion.DelayedAttack(parentExt,attacker,target,(int)damage,"basicAttack").run();
                Lich.this.setSkullyTarget(this.target);
            }else if(attacker.getClass() == Skully.class){
                double damage = 25d + (Lich.this.getPlayerStat("attackDamage")*0.8);
                Actor attacker = this.attacker;
                if(this.target.getActorType() == ActorType.PLAYER){
                    attacker = Lich.this;
                }
                new Champion.DelayedAttack(parentExt,attacker,target,(int)damage,"spell4").run();
            }
        }
    }

    private class LichCharm extends Projectile {

        public LichCharm(ATBPExtension parentExt, UserActor owner, Line2D path, float speed, float hitboxRadius, String id) {
            super(parentExt, owner, path, speed, hitboxRadius, id);
        }

        @Override
        protected void hit(Actor victim) {
            JsonNode spellData = parentExt.getAttackData(getAvatar(),"spell2");
            handleSpellVamp(getSpellDamage(spellData));
            ExtensionCommands.playSound(parentExt,room,"","sfx_lich_charm_shot_hit",victim.getLocation());
            ExtensionCommands.createWorldFX(parentExt,room,this.id,"lich_charm_explosion",id+"_charmExplosion",500,(float)this.location.getX(),(float)this.location.getY(),false,team,0f);
            victim.addToDamageQueue(Lich.this,getSpellDamage(spellData),spellData);
            victim.handleCharm(Lich.this,2000);
            destroy();
        }

    }

    private class LichAbilityRunnable extends AbilityRunnable{

        private boolean ultCasted = false;

        public LichAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        public LichAbilityRunnable(JsonNode spellData, int cooldown, int gCooldown, Point2D dest){
            super(3,spellData,cooldown,gCooldown,dest);
            this.ultCasted = true;
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
            if(this.ultCasted){ //Handle end of ult
                ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,getReducedCooldown(cooldown),gCooldown);
                Lich.this.ultTeleportsRemaining = 0;
                Lich.this.ultStarted = false;
                ultLocation = null;
                Lich.this.ultTime = -1;
            }else{
                Lich.this.ultTime = System.currentTimeMillis();
                Lich.this.ultStarted = true;
                Lich.this.ultTeleportsRemaining = 1;
                Lich.this.ultLocation = dest;
                canMove = true;
                ExtensionCommands.createWorldFX(parentExt,room,id,"lich_death_puddle",id+"_lichPool",5000,(float)dest.getX(),(float)dest.getY(),false,team,0f);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new LichAbilityRunnable(spellData,cooldown,gCooldown,dest),5000,TimeUnit.MILLISECONDS);
            }
        }

        @Override
        protected void spellPassive() {

        }
    }
}

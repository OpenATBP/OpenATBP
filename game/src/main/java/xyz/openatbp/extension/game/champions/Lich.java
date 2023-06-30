package xyz.openatbp.extension.game.champions;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.Champion;
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

    public Lich(User u, ATBPExtension parentExt){
        super(u,parentExt);
        lastSkullySpawn = 0;
    }

    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest) {
        if (skully == null && System.currentTimeMillis() - lastSkullySpawn > getReducedCooldown(40000)) {
            this.spawnSkully();
        }
        switch (ability) { //TODO: Last left off - Q is broken and maybe healthRegen?
            case 1: //Q
                double statIncrease = this.speed * 0.25;
                this.handleEffect("speed", statIncrease, 6000, "lich_trail");
                qActivated = true;
                slimePath = new ArrayList<>();
                slimedEnemies = new HashMap<>();
                ExtensionCommands.createActorFX(parentExt, room, id, "lichking_deathmist", 6000, "lich_trail", true, "", true, false, team);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new TrailHandler(), 6000, TimeUnit.MILLISECONDS);
                break;
            case 2: //W
                break;
            case 3: //E
                break;
            case 4: //Passive
                break;
        }
    }

    @Override
    public void attack(Actor a){
        this.handleAttack(a);
        currentAutoAttack = SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new PassiveAttack(this,a),"lich_projectile"),500,TimeUnit.MILLISECONDS);
    }

    @Override
    public void die(Actor a){
        super.die(a);
        if(this.skully != null) this.setSkullyTarget(a);
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
                        JsonNode attackData = this.parentExt.getAttackData(this.avatar,"spell1");
                        if(slimedEnemies.containsKey(a.getId())){
                            if(System.currentTimeMillis() - slimedEnemies.get(a.getId()) >= 1000){
                                System.out.println(a.getId() + " getting slimed!");
                                a.damaged(this,getSpellDamage(attackData),attackData);
                                a.handleEffect("speed",a.getPlayerStat("speed")*-0.3,1500,"lich_slow");
                                slimedEnemies.put(a.getId(),System.currentTimeMillis());
                                break;
                            }
                        }else{
                            System.out.println(a.getId() + " getting slimed!");
                            a.damaged(this,getSpellDamage(attackData),attackData);
                            a.handleEffect("speed",a.getPlayerStat("speed")*-0.3,1500,"lich_slow");
                            slimedEnemies.put(a.getId(),System.currentTimeMillis());
                            break;
                        }
                    }
                }
            }
            if(this.slimePath.size() > 150) this.slimePath.remove(this.slimePath.size()-1);
        }
    }

    private void spawnSkully(){
        skully = new Skully();
        lastSkullySpawn = System.currentTimeMillis();
    }

    public void setSkullyTarget(Actor a){
        this.skully.setTarget(a);
    }

    private void handleSkullyDeath(){
        ExtensionCommands.actorAbilityResponse(parentExt,player,"passive",true,getReducedCooldown(40000),2);
        this.skully = null;
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
            this.speed = 2.95f;
            this.attackRange = 2f;
            this.lastLichLocation = Lich.this.getRelativePoint(false);
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            ExtensionCommands.createActor(parentExt,room,this.id,this.avatar,this.location,0f,this.team);
        }

        @Override
        public boolean damaged(Actor a, int damage, JsonNode attackData) {
            return false;
        }

        @Override
        public void attack(Actor a) {
            ExtensionCommands.attackActor(parentExt,room,this.id,a.getId(), (float) a.getLocation().getX(), (float) a.getLocation().getY(),false,true);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new PassiveAttack(this,a),300,TimeUnit.MILLISECONDS);
            this.attackCooldown = 1000;
        }

        @Override
        public void die(Actor a) {
            ExtensionCommands.knockOutActor(parentExt,room,this.id,a.getId(),40000);
            Lich.this.handleSkullyDeath();
            ExtensionCommands.destroyActor(parentExt,room,this.id);
        }

        @Override
        public void update(int msRan) {
            if(System.currentTimeMillis() - timeOfBirth >= 20*1000){
                this.die(this);
            }
            this.location = this.getRelativePoint();
            if(this.attackCooldown > 0) this.reduceAttackCooldown();
            if(this.target == null){
                Point2D lichLocation = Lich.this.getRelativePoint(false);

                if(this.location.distance(lichLocation) > 3 && this.lastLichLocation.distance(lichLocation) > 0.01){
                    float deltaDistance = (float) (this.location.distance(lichLocation)-3f);
                    this.timeTraveled = 0.1f;
                    this.movementLine = Champion.getDistanceLine(new Line2D.Float(this.location,lichLocation),deltaDistance);
                    this.lastLichLocation = lichLocation;
                    ExtensionCommands.moveActor(parentExt,room,this.id,this.movementLine.getP1(),this.movementLine.getP2(), (float) this.speed,true);
                }else{
                    this.timeTraveled+=0.1f;
                }
            }else{
                if(this.withinRange(this.target) && this.attackCooldown <= 0){
                    this.attack(this.target);
                }else if(!this.withinRange(this.target)){
                    if(this.target.getLocation().distance(this.lastTargetLocation) > 0.01f){
                        this.lastTargetLocation = this.target.getLocation();
                        this.movementLine = new Line2D.Float(this.location,this.target.getLocation());
                        this.timeTraveled = 0.1f;
                        ExtensionCommands.moveActor(parentExt,room,this.id,this.movementLine.getP1(),this.movementLine.getP2(), (float) this.speed,true);
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
            this.target = a;
            this.lastTargetLocation = a.getLocation();
            this.movementLine = new Line2D.Float(this.location,a.getLocation());
            this.timeTraveled = 0.1f;
            ExtensionCommands.moveActor(parentExt,room,this.id,this.location,a.getLocation(), (float) this.speed,true);
        }

        public void resetTarget(){
            this.target = null;
            this.movementLine = new Line2D.Float(this.location,Lich.this.getRelativePoint(false));
            this.timeTraveled = 0.1f;
        }
    }

    private class PassiveAttack implements Runnable {

        Actor attacker;
        Actor target;

        PassiveAttack(Actor attacker, Actor target){
            this.attacker = attacker;
            this.target = target;
        }

        @Override
        public void run() {
            if(attacker.getClass() == Lich.class){
                JsonNode attackData = parentExt.getAttackData("lich","basicAttack");
                if(this.target.damaged(this.attacker, (int) this.attacker.getPlayerStat("attackDamage"),attackData)){
                    Lich.this.skully.resetTarget();
                }else{
                    Lich.this.setSkullyTarget(this.target);
                }
            }else if(attacker.getClass() == Skully.class){
                JsonNode attackData = parentExt.getAttackData("lich","spell4");
                double damage = 25d + (Lich.this.getPlayerStat("attackDamage")*0.8);
                if(this.target.damaged(Lich.this,(int)damage,attackData)){
                    Lich.this.increaseStat("spellDamage",1);
                    Skully me = (Skully) this.attacker;
                    me.resetTarget();
                }
            }
        }
    }
}

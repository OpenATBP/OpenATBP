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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BubbleGum extends UserActor {

    private int gumStacks;
    private long lastGum;
    //icon_pb_p0 - icon_pb_p3
    private String currentIcon = "icon_pb_p0";
    private boolean potionActivated = false;
    private Point2D potionLocation;
    private long potionSpawn;
    private List<Turret> turrets;
    private int turretNum = 0;
    private boolean bombPlaced;
    private Point2D bombLocation;
    private final static boolean DEBUG = false;

    public BubbleGum(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        this.turrets = new ArrayList<>(2);
        ExtensionCommands.addStatusIcon(parentExt,player,"Sticky Sweet", "Gum up your enemies", currentIcon, 0f);
    }

    @Override
    public void attack(Actor a){
        SmartFoxServer.getInstance().getTaskScheduler().schedule(new RangedAttack(a, new PassiveAttack(this,a,this.handleAttack(a)),"bubblegum_projectile","weapon_holder"),500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan){ //TODO: Working on PB Passive like the icons
        super.update(msRan);
        int gum = Character.getNumericValue(currentIcon.charAt(currentIcon.length()-1));
        if(System.currentTimeMillis() - lastGum >= 3000){
            if(gumStacks > 0) gumStacks--;
            this.lastGum = System.currentTimeMillis();
        }
        if(gumStacks != gum){
            ExtensionCommands.removeStatusIcon(parentExt,player,"Sticky Sweet");
            currentIcon = "icon_pb_p" + gumStacks;
            float duration = 3000f;
            if(gumStacks == 0) duration = 0f;
            ExtensionCommands.addStatusIcon(parentExt,player,"Sticky Sweet", "Gum up your enemies", currentIcon, duration);
        }
        if(potionActivated){
            if(System.currentTimeMillis() - potionSpawn >= 3000){
                potionActivated = false;
                potionSpawn = -1;
                potionLocation = null;
            }else{
                List<Actor> affectedActors = Champion.getActorsInRadius(parentExt.getRoomHandler(room.getId()),potionLocation,2f);
                for(Actor a : affectedActors){
                    if(a.getTeam() != this.team){
                        JsonNode spellData = this.parentExt.getAttackData("princessbubblegum","spell1");
                        double damage = this.getSpellDamage(spellData)/10f;
                        a.addToDamageQueue(this,damage,spellData);
                        if(isNonStructure(a)) a.addState(ActorState.SLOWED,0.3d,2000,null,false);
                    }else if(a.getId().equalsIgnoreCase(this.id)){
                        this.addEffect("speed",this.getStat("speed")*0.4d,2000,null,false);
                        ExtensionCommands.createActorFX(this.parentExt,this.room,this.id,"statusEffect_speed",2000,this.id+"_pbQSpeed",true,"Bip01 Footsteps",true,false,this.team);
                    }
                }
            }
        }
        ArrayList<Turret> turrets = new ArrayList<>(this.turrets); //To remove concurrent exceptions
        for(Turret t : turrets){
            t.update(msRan);
        }
    }
    @Override
    public void useAbility(int ability, JsonNode spellData, int cooldown, int gCooldown, int castDelay, Point2D dest){
        switch(ability){
            case 1: //Q
                this.canCast[0] = false;
                this.stopMoving();
                String potionVo = (this.avatar.equals("princessbubblegum_skin_prince")) ? "vo/vo_gumball_potion" : (this.avatar.contains("young")) ? "vo/vo_bubblegum_young_potion" : "vo/vo_bubblegum_potion";
                ExtensionCommands.playSound(parentExt,room,id,potionVo,this.location);
                ExtensionCommands.createWorldFX(parentExt,room,id,"fx_target_ring_2",id+"_potionArea",3000+castDelay,(float)dest.getX(),(float)dest.getY(),true,this.team,0f);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new PBAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"q",true,getReducedCooldown(cooldown),gCooldown);
                break;
            case 2: //W
                this.canCast[1] = false;
                this.stopMoving();
                String turretVo = (this.avatar.equals("princessbubblegum_skin_prince")) ? "vo/vo_gumball_turret" : (this.avatar.contains("young")) ? "vo/vo_bubblegum_young_turret" : "vo/vo_bubblegum_turret";
                ExtensionCommands.playSound(parentExt,room,id,turretVo,this.location);
                this.spawnTurret(dest);
                ExtensionCommands.actorAbilityResponse(parentExt,player,"w",true,getReducedCooldown(cooldown),gCooldown);
                SmartFoxServer.getInstance().getTaskScheduler().schedule(new PBAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),castDelay,TimeUnit.MILLISECONDS);
                break;
            case 3: //E
                if(!this.bombPlaced){
                    this.canCast[2] = false;
                    this.stopMoving();
                    String bombVo = (this.avatar.equals("princessbubblegum_skin_prince")) ? "vo/vo_gumball_bomb_hup" : (this.avatar.contains("young")) ? "vo/vo_bubblegum_young_bomb_hup" : "vo/vo_bubblegum_bomb_hup";
                    ExtensionCommands.playSound(this.parentExt,this.room,this.id,bombVo,this.location);
                    ExtensionCommands.createWorldFX(parentExt,room,id,"fx_target_ring_3",id+"_bombArea",4000, (float)dest.getX(), (float)dest.getY(),true,this.team,0f);
                    ExtensionCommands.createWorldFX(parentExt,room,id,"bubblegum_bomb_trap",this.id+"_bomb",4000,(float)dest.getX(),(float)dest.getY(),false,this.team,0f);
                    this.bombLocation = dest;
                    Runnable bombDelay = () -> {
                        bombPlaced = true;
                        canCast[2] = true;
                    };
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(bombDelay, 750, TimeUnit.MILLISECONDS);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(new PBAbilityRunnable(ability,spellData,cooldown,gCooldown,dest),4000,TimeUnit.MILLISECONDS);
                }else{
                    this.useBomb(getReducedCooldown(cooldown),gCooldown);
                }
                break;
        }
    }

    protected void useBomb(int cooldown, int gCooldown){
        for(Actor a : Champion.getActorsInRadius(this.parentExt.getRoomHandler(this.room.getId()),this.bombLocation,3f)){
            if((a.getTeam() != this.team || a.getId().equalsIgnoreCase(this.id)) && a.getActorType() != ActorType.BASE && a.getActorType() != ActorType.TOWER){
                a.knockback(this.bombLocation);
                JsonNode spellData = parentExt.getAttackData("peebles","spell3");
                if(a.getTeam() != this.team) a.addToDamageQueue(this,getSpellDamage(spellData),spellData);
                else if(a.getId().equalsIgnoreCase(this.id)){
                    ExtensionCommands.actorAnimate(parentExt,room,this.id,"spell3b",325,false);
                    Runnable animationDelay = () -> ExtensionCommands.actorAnimate(parentExt,room,id,"spell3c",350,false);
                    SmartFoxServer.getInstance().getTaskScheduler().schedule(animationDelay,325,TimeUnit.MILLISECONDS);
                }
            }
        }
        String useBombVo = (this.avatar.equals("princessbubblegum_skin_prince")) ? "vo/vo_gumball_turret" : (this.avatar.contains("young")) ? "vo/vo_bubblegum_young_bomb_grunt" : "vo/vo_bubblegum_bomb_grunt";
        ExtensionCommands.playSound(parentExt,room,this.id,useBombVo,this.location);
        ExtensionCommands.removeFx(parentExt,room,id+"_bomb");
        ExtensionCommands.removeFx(parentExt,room,id+"_bombArea");
        ExtensionCommands.playSound(parentExt,room,"","sfx_bubblegum_bomb",bombLocation);
        ExtensionCommands.createWorldFX(parentExt,room,id,"bubblegum_bomb_explosion",id+"_bombExplosion",1500,(float)bombLocation.getX(),(float)bombLocation.getY(),false,team,0f);
        ExtensionCommands.actorAbilityResponse(parentExt,player,"e",true,cooldown,gCooldown);
        this.bombPlaced = false;
        this.bombLocation = null;
    }

    @Override
    public void die(Actor a){
        super.die(a);
        for(Turret t : turrets){
            t.die(a);
        }
        this.turrets = new ArrayList<>();
    }

    private void spawnTurret(Point2D dest){
        Turret t = null;
        if(this.turrets != null && this.turrets.size() == 2){
            this.turrets.get(0).die(this);
            this.turrets.remove(0);
             t = new Turret(dest, this.turretNum);
        }else if(this.turrets != null){
            t = new Turret(dest,this.turretNum);
        }
        if(this.turrets != null){
            this.turretNum++;
            this.turrets.add(t);
            this.parentExt.getRoomHandler(this.room.getId()).addCompanion(t);
        }
    }

    public void handleTurretDeath(Turret t){
        this.turrets.remove(t);
    }

    private class PBAbilityRunnable extends AbilityRunnable {

        public PBAbilityRunnable(int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            canCast[0] = true;
            potionActivated = true;
            potionLocation = dest;
            potionSpawn = System.currentTimeMillis();
            ExtensionCommands.playSound(parentExt,room,"","sfx_bubblegum_potion_aoe",dest);
            ExtensionCommands.createWorldFX(parentExt,room,id,"bubblegum_ground_aoe",id+"_potionAoe",3000,(float)dest.getX(),(float)dest.getY(),false,team,0f);
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            if(bombPlaced){
                useBomb(getReducedCooldown(cooldown),gCooldown);
            }
        }

        @Override
        protected void spellPassive() {

        }
    }

    private class PassiveAttack implements Runnable { //TODO: Maybe extend DelayedAttack?

        Actor attacker;
        Actor target;
        boolean crit;

        PassiveAttack(Actor a, Actor t, boolean crit){
            this.attacker = a;
            this.target = t;
            this.crit = crit;
        }

        @Override
        public void run() {
            double damage = this.attacker.getPlayerStat("attackDamage");
            if(crit) damage*=2;
            new Champion.DelayedAttack(parentExt,this.attacker,this.target,(int)damage,"basicAttack").run();
            if(this.target.getActorType() == ActorType.PLAYER || DEBUG){
                gumStacks++;
                lastGum = System.currentTimeMillis();
                if(gumStacks > 3){
                    gumStacks = 3;
                }
                double change = 0.25d / (4-gumStacks);
                double targetAttackSpeed = this.target.getStat("attackSpeed");
                double getReducedSpeed = this.target.getTempStat("attackSpeed");
                double newDamage = (targetAttackSpeed*change);
                if(getReducedSpeed / targetAttackSpeed >= 0.25) newDamage = 0;
                System.out.println("PB is gumming up by " + newDamage);
                this.target.addEffect("attackSpeed",newDamage,3000,null,true);
            }
        }
    }

    private class Turret extends Actor {
        private long timeOfBirth;
        private Actor target;
        private boolean dead = false;
        private String iconName;

        Turret(Point2D location, int turretNum){
            this.room = BubbleGum.this.room;
            this.parentExt = BubbleGum.this.parentExt;
            this.currentHealth = BubbleGum.this.maxHealth;
            this.maxHealth = BubbleGum.this.maxHealth;
            this.location = location;
            this.avatar = "princessbubblegum_turret";
            this.id = "turret"+turretNum + "_" + BubbleGum.this.id;
            this.team = BubbleGum.this.team;
            this.timeOfBirth = System.currentTimeMillis();
            this.actorType = ActorType.COMPANION;
            this.stats = this.initializeStats();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            this.setStat("attackDamage",BubbleGum.this.getStat("attackDamage"));
            this.timeOfBirth = System.currentTimeMillis();
            this.iconName = "Turret #" + turretNum;
            ExtensionCommands.addStatusIcon(parentExt,player,iconName,"Turret placed!","icon_pb_s2",60000f);
            ExtensionCommands.createActor(parentExt,room,this.id,this.avatar,this.location,0f,this.team);
            ExtensionCommands.playSound(parentExt,room,this.id,"sfx_bubblegum_turret_spawn",this.location);
            ExtensionCommands.createWorldFX(parentExt,room,this.id,"fx_target_ring_3",this.id+"_ring", 60000, (float)this.location.getX(),(float)this.location.getY(),true,this.team,0f);
            this.addState(ActorState.IMMUNITY,0d,1000*60*15,null,false);
        }

        @Override
        public void handleKill(Actor a, JsonNode attackData) {
            this.target = null;
            this.findTarget();
        }

        @Override
        public void attack(Actor a) {
            float time = (float) (a.getLocation().distance(this.location) / 10f);
            ExtensionCommands.playSound(parentExt,room,this.id,"sfx_bubblegum_turret_shoot",this.location);
            ExtensionCommands.createProjectileFX(parentExt,room,"bubblegum_turret_projectile",this.id,a.getId(),"Bip01","targetNode",time);
            SmartFoxServer.getInstance().getTaskScheduler().schedule(new Champion.DelayedAttack(parentExt,this,a,10+(int)this.getPlayerStat("attackDamage"),"turretAttack"),(int)time*1000,TimeUnit.MILLISECONDS);
            BubbleGum.this.handleLifeSteal();
            this.attackCooldown = this.getPlayerStat("attackSpeed");
        }

        @Override
        public void die(Actor a) {
            this.dead = true;
            this.currentHealth = 0;
            ExtensionCommands.removeStatusIcon(parentExt,player,iconName);
            ExtensionCommands.removeFx(parentExt,room,this.id+"_ring");
            ExtensionCommands.destroyActor(parentExt,room,this.id);
            this.parentExt.getRoomHandler(this.room.getId()).removeCompanion(this);
        }

        @Override
        public void update(int msRan) {
            this.handleDamageQueue();
            if(this.dead) return;
            if(System.currentTimeMillis() - this.timeOfBirth >= 60000){
                this.die(this);
                BubbleGum.this.handleTurretDeath(this);
                return;
            }
            if(this.attackCooldown > 0) this.reduceAttackCooldown();
            if(this.target != null && this.target.getHealth() > 0){
                if(this.withinRange(this.target) && this.canAttack()) this.attack(this.target);
                else if(!this.withinRange(this.target)){
                    this.target = null;
                    this.findTarget();
                }
            }else{
                if(this.target != null && this.target.getHealth() <= 0) this.target = null;
                this.findTarget();
            }
        }

        public void findTarget(){
            List<Actor> potentialTargets = this.parentExt.getRoomHandler(this.room.getId()).getActors();
            List<Actor> users = potentialTargets.stream().filter(a -> a.getActorType() == ActorType.PLAYER).collect(Collectors.toList());
            for(Actor ua : users){
                if(ua.getTeam() != this.team && this.withinRange(ua) && ua.getHealth() > 0){
                    this.target = ua;
                    break;
                }
            }
            if(this.target == null){
                for(Actor a : potentialTargets){
                    if(a.getTeam() != this.team && this.withinRange(a) && a.getHealth() > 0){
                        this.target = a;
                        break;
                    }
                }
            }
        }

        @Override
        public void setTarget(Actor a) {
            this.target = a;
        }
    }
}

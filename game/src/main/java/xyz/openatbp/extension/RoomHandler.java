package xyz.openatbp.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import org.bson.Document;
import org.bson.conversions.Bson;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.*;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;

import java.awt.geom.Point2D;
import java.util.*;

public class RoomHandler implements Runnable{
    private ATBPExtension parentExt;
    private Room room;
    private ArrayList<Minion> minions;
    private ArrayList<Tower> towers;
    private ArrayList<UserActor> players;
    private List<Projectile> activeProjectiles;
    private List<Monster> campMonsters;
    private List<Actor> companions;
    private Base[] bases = new Base[2];
    private int mSecondsRan = 0;
    private int secondsRan = 0;
    private int[] altarStatus = {0,0,0};
    private HashMap<String,Integer> cooldowns = new HashMap<>();
    private int currentMinionWave = 0;
    private GumballGuardian[] guardians;
    private boolean gameOver = false;
    public RoomHandler(ATBPExtension parentExt, Room room){
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        this.towers = new ArrayList<>();
        this.players = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        HashMap<String, Point2D> towers0 = MapData.getTowerData(room.getGroupId(),0);
        HashMap<String, Point2D> towers1 = MapData.getTowerData(room.getGroupId(),1);
        for(String key : towers0.keySet()){
            towers.add(new Tower(parentExt,room,key,0,towers0.get(key)));
        }
        for(String key : towers1.keySet()){
            towers.add(new Tower(parentExt,room,key,1,towers1.get(key)));
        }
        bases[0] = new Base(parentExt, room,0);
        bases[1] = new Base(parentExt, room, 1);
        this.guardians = new GumballGuardian[]{new GumballGuardian(parentExt, room, 0),new GumballGuardian(parentExt,room,1)};
        for(User u : room.getUserList()){
            players.add(Champion.getCharacterClass(u,parentExt));
            //ExtensionCommands.createActor(this.parentExt,u,"testMonster2","bot_jake",new Point2D.Float(0f,0f),0f,2);
            //ExtensionCommands.createActor(this.parentExt,u,"testMonster3","bot_iceking",new Point2D.Float(0f,0f),0f,2);
        }
        //ExtensionCommands.createActor(this.parentExt,room,"testMonster","bot_finn",new Point2D.Float(0f,0f),0f,2);
        this.activeProjectiles = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        this.companions = new ArrayList<>();
        //this.campMonsters = GameManager.initializeCamps(parentExt,room);

    }
    @Override
    public void run() {
        if(this.gameOver) return;
        mSecondsRan+=100;
        if(mSecondsRan % 1000 == 0){ // Handle every second
            try{
                if(secondsRan % 60 == 0){
                    this.printActors();
                }
                secondsRan++;
                if(secondsRan == (60*7) + 30){
                    ExtensionCommands.playSound(parentExt,room,"global","announcer/time_half",new Point2D.Float(0f,0f));
                }else if(secondsRan == (60*13)){
                    ExtensionCommands.playSound(parentExt,room,"global","announcer/time_low",new Point2D.Float(0f,0f));
                    ExtensionCommands.playSound(parentExt,room,"music","music/music_time_low",new Point2D.Float(0f,0f));
                }else if(secondsRan == 15*60){
                    ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
                    int blueScore = scoreObject.getInt("blue");
                    int purpleScore = scoreObject.getInt("purple");
                    if(blueScore > purpleScore) this.gameOver(1);
                    else if(purpleScore > blueScore) this.gameOver(0);
                    else this.gameOver(-1);
                    return;
                }
                if(room.getUserList().size() == 0) parentExt.stopScript(room.getId()); //If no one is in the room, stop running.
                else{
                    handleAltars();
                    ExtensionCommands.updateTime(parentExt,this.room,mSecondsRan);
                }
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                for(String s : GameManager.SPAWNS){ //Check all mob/health spawns for how long it's been since dead
                    if(s.length()>3){
                        int spawnRate = 45; //Mob spawn rate
                        if(s.equalsIgnoreCase("keeoth")) spawnRate = 120;
                        else if(s.equalsIgnoreCase("ooze")) spawnRate = 90;
                        if(spawns.getInt(s) == spawnRate){ //Mob timers will be set to 0 when killed or health when taken
                            spawnMonster(s);
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }else{
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }
                    }else{
                        int time = spawns.getInt(s);
                        if((this.secondsRan <= 91 && time == 90) || (this.secondsRan > 91 && time == 60)){
                            spawnHealth(s);
                        }
                        else if((this.secondsRan <= 91 && time < 90) || (this.secondsRan > 91 && time < 60)){
                            time++;
                            spawns.putInt(s,time);
                        }
                    }
                }
                handleCooldowns();

                int minionWave = secondsRan/30;
                if(minionWave != this.currentMinionWave){
                    int minionNum = secondsRan % 10;
                    if(minionNum == 5) this.currentMinionWave = minionWave;
                    if(minionNum <= 4){
                        this.addMinion(1,minionNum,minionWave,0);
                        this.addMinion(0,minionNum,minionWave,0);
                        this.addMinion(1,minionNum,minionWave,1);
                        this.addMinion(0,minionNum,minionWave,1);
                    }else if(minionNum == 5){
                        for(int i = 0; i < 2; i++){ //i = lane
                            for(int g = 0; g < 2; g++){
                                if(!this.hasSuperMinion(i,g) && this.canSpawnSupers(g)) this.addMinion(g,minionNum,minionWave,i);
                            }
                        }
                    }
                }

            }catch(Exception e){
                e.printStackTrace();
            }
        }
        try{
            this.handleFountain();
            for(UserActor u : players){ //Tracks player location
                u.update(mSecondsRan);
            }
            for(Projectile p : activeProjectiles){ //Handles skill shots
                p.update(this);
            }
            activeProjectiles.removeIf(Projectile::isDestroyed);
            handleHealth();
            for(Minion m : minions){ //Handles minion behavior
                m.update(mSecondsRan);
            }
            minions.removeIf(m -> (m.getHealth()<=0));
            for(Monster m : campMonsters){
                m.update(mSecondsRan);
            }
            campMonsters.removeIf(m -> (m.getHealth()<=0));
            for(Tower t : towers){
                t.update(mSecondsRan);
                if(t.getHealth() <= 0 && (t.getTowerNum() == 0 || t.getTowerNum() == 3)) bases[t.getTeam()].unlock();
            }
            for(GumballGuardian g : this.guardians){
                g.update(mSecondsRan);
            }
            towers.removeIf(t -> (t.getHealth()<=0));

            bases[0].update(mSecondsRan);
            bases[1].update(mSecondsRan);
            if(this.room.getUserList().size() == 0) parentExt.stopScript(this.room.getId());
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public Tower findTower(String id){
        for(Tower t : towers){
            if(t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public Minion findMinion(String id){
        for(Minion m : minions){
            if(m.getId().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public List<Actor> getCompanions(){
        return this.companions;
    }

    public void addMinion(int team, int type, int wave, int lane){
        Minion m = new Minion(parentExt,room, team, type, wave,lane);
        minions.add(m);
    }

    public Base getOpposingTeamBase(int team){
        if(team == 0) return bases[1];
        else return  bases[0];
    }

    private void handleHealth(){
        for(String s : GameManager.SPAWNS){
            if(s.length() == 3){
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                if(spawns.getInt(s) == 91){
                    for(UserActor u : players){
                        Point2D currentPoint = u.getLocation();
                        if(insideHealth(currentPoint,getHealthNum(s))){
                            int team = u.getTeam();
                            Point2D healthLoc = getHealthLocation(getHealthNum(s));
                            ExtensionCommands.removeFx(parentExt,room,s+"_fx");
                            ExtensionCommands.createActorFX(parentExt,room,String.valueOf(u.getId()),"picked_up_health_cyclops",2000,s+"_fx2",true,"",false,false,team);
                            ExtensionCommands.playSound(parentExt,u.getRoom(),"","sfx_health_picked_up",healthLoc);
                            if(!u.hasTempStat("healthRegen")) u.changeHealth(100);
                            u.handleEffect("healthRegen",20d,5000,"cyclopsTears");
                            //Champion.giveBuff(parentExt,u.getUser(), Buff.HEALTH_PACK);
                            spawns.putInt(s,0);
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean insideHealth(Point2D pLoc, int health){
        Point2D healthLocation = getHealthLocation(health);
        double hx = healthLocation.getX();
        double hy = healthLocation.getY();
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px-hx,2) + Math.pow(pz-hy,2));
        return dist<=0.5;
    }

    private Point2D getHealthLocation(int num){
        float x = MapData.L2_BOT_BLUE_HEALTH[0];
        float z = MapData.L2_BOT_BLUE_HEALTH[1];
        // num = 1
        switch(num){
            case 0:
                z*=-1;
                break;
            case 2:
                x = MapData.L2_LEFT_HEALTH[0];
                z = MapData.L2_LEFT_HEALTH[1];
                break;
            case 3:
                x*=-1;
                z*=-1;
                break;
            case 4:
                x*=-1;
                break;
            case 5:
                x = MapData.L2_LEFT_HEALTH[0]*-1;
                z = MapData.L2_LEFT_HEALTH[1];
                break;
        }
        return new Point2D.Float(x,z);
    }

    private int getHealthNum(String id){
        switch(id){
            case "ph2": //Purple team bot
                return 4;
            case "ph1": //Purple team top
                return 3;
            case "ph3": // Purple team mid
                return 5;
            case "bh2": // Blue team bot
                return 1;
            case "bh1": // Blue team top
                return 0;
            case "bh3": //Blue team mid
                return 2;
        }
        return -1;
    }

    private void spawnMonster(String monster){
        System.out.println("Spawning: " + monster);
        String map = room.getGroupId();
            float x = 0;
            float z = 0;
            String actor = monster;
            if(monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("owls")){
                char[] abc = {'a','b','c'};
                for(int i = 0; i < 3; i++){ //Gnomes and owls have three different mobs so need to be spawned in triplets
                    if(monster.equalsIgnoreCase("gnomes")){
                        actor="gnome_"+abc[i];
                        x = (float)MapData.GNOMES[i].getX();
                        z = (float)MapData.GNOMES[i].getY();
                        campMonsters.add(new Monster(parentExt,room,MapData.GNOMES[i],actor));
                    }else{
                        actor="ironowl_"+abc[i];
                        x = (float)MapData.OWLS[i].getX();
                        z = (float)MapData.OWLS[i].getY();
                        campMonsters.add(new Monster(parentExt,room,MapData.OWLS[i],actor));
                    }
                    ExtensionCommands.createActor(this.parentExt,this.room,actor,actor,new Point2D.Float(x,z),0f,2);
                }
            }else if(monster.length()>3){
                switch(monster){
                    case "hugwolf":
                        x = MapData.HUGWOLF[0];
                        z = MapData.HUGWOLF[1];
                        campMonsters.add(new Monster(parentExt,room,MapData.HUGWOLF,actor));
                        break;
                    case "grassbear":
                        x = MapData.GRASS[0];
                        z = MapData.GRASS[1];
                        campMonsters.add(new Monster(parentExt,room,MapData.GRASS,actor));
                        break;
                    case "keeoth":
                        x = MapData.L2_KEEOTH[0];
                        z = MapData.L2_KEEOTH[1];
                        campMonsters.add(new Keeoth(parentExt,room,MapData.L2_KEEOTH,actor));
                        break;
                    case "ooze":
                        x = MapData.L2_OOZE[0];
                        z = MapData.L2_OOZE[1];
                        actor = "goomonster";
                        campMonsters.add(new GooMonster(parentExt,room,MapData.L2_OOZE,actor));
                        break;
                }
                ExtensionCommands.createActor(this.parentExt,this.room,actor,actor,new Point2D.Float(x,z),0f,2);
            }
    }
    private void spawnHealth(String id){
        int healthNum = getHealthNum(id);
        Point2D healthLocation = getHealthLocation(healthNum);
        int effectTime = (15*60-secondsRan)*1000;
        ExtensionCommands.createWorldFX(parentExt,this.room, "","pickup_health_cyclops",id+"_fx",effectTime,(float)healthLocation.getX(),(float)healthLocation.getY(),false,2,0f);
        room.getVariable("spawns").getSFSObjectValue().putInt(id,91);
    }

    private void handleAltars(){
        int[] altarChange = {0,0,0};
        boolean[] playerInside = {false,false,false};
        for(UserActor u : players){

            int team = u.getTeam();
            Point2D currentPoint = u.getLocation();
            for(int i = 0; i < 3; i++){ // 0 is top, 1 is mid, 2 is bot
                if(insideAltar(currentPoint,i)){
                    playerInside[i] = true;
                    if(team == 1) altarChange[i]--;
                    else altarChange[i]++;
                }
            }
        }
        for(int i = 0; i < 3; i++){
            if(altarChange[i] > 0) altarChange[i] = 1;
            else if(altarChange[i] < 0) altarChange[i] = -1;
            else if(altarChange[i] == 0 && !playerInside[i]){
                if(altarStatus[i]>0) altarChange[i]=-1;
                else if(altarStatus[i]<0) altarChange[i]=1;
            }
            int altarStat = Math.abs(altarStatus[i]);
            if(altarStat <= 4) altarStatus[i]+=altarChange[i];
            if(altarStat > 0 && altarStat < 5){
                String sound = "sfx_altar_"+altarStat;
                ExtensionCommands.playSound(parentExt,room,"",sound,getAltarLocation(i));
            }
            int team = 2;
            if(altarStatus[i]>0) team = 0;
            else team = 1;
            String altarId = "altar_"+i;
            if(Math.abs(altarStatus[i]) >= 5 && altarStatus[i] != 10){
                ExtensionCommands.playSound(parentExt,room,"","sfx_altar_locked",getAltarLocation(i));
                altarStatus[i]=10; //Locks altar
                if(i == 1) addScore(null,team,15);
                else addScore(null,team,10);
                cooldowns.put(altarId+"__"+"altar",180);
                ExtensionCommands.createActorFX(this.parentExt,this.room,altarId,"fx_altar_lock",1000*60*3,"fx_altar_lock"+i,false,"Bip01",false,true,team);
                int altarNum;
                if(i == 0) altarNum = 1;
                else if(i == 1) altarNum = 0;
                else altarNum = i;
                ExtensionCommands.updateAltar(this.parentExt,this.room,altarNum,team,true);
                for(UserActor u : this.players){
                    if(u.getTeam() == team){
                        try{
                            if(i == 1){
                                u.handleEffect("attackDamage",u.getPlayerStat("attackDamage")*0.25,1000*60,"attack_altar");
                                u.handleEffect("spellDamage",u.getPlayerStat("spellDamage")*0.25,1000*60,"attack_altar");
                            }else{
                                u.handleEffect("armor",u.getPlayerStat("armor")*0.5,1000*60,"defense_altar");
                                u.handleEffect("spellResist",u.getPlayerStat("spellResist")*0.5,1000*60,"defense_altar");
                            }
                            //cooldowns.put(u.getId()+"__buff__"+buffName,60);
                            ExtensionCommands.knockOutActor(parentExt,u.getUser(),altarId,u.getId(),180);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }else if(Math.abs(altarStatus[i])<=4 && altarStatus[i]!=0){
                int stage = Math.abs(altarStatus[i]);
                ExtensionCommands.createActorFX(this.parentExt,this.room,altarId,"fx_altar_"+stage,1000,"fx_altar_"+stage+i,false,"Bip01",false,true,team);
            }
        }
    }

    private boolean insideAltar(Point2D pLoc, int altar){
        double altar2_x = 0;
        double altar2_y = 0;
        if(altar == 0){
            altar2_x = MapData.L2_TOP_ALTAR[0];
            altar2_y = MapData.L2_TOP_ALTAR[1];
        }else if(altar == 2){
            altar2_x = MapData.L2_BOT_ALTAR[0];
            altar2_y = MapData.L2_BOT_ALTAR[1];
        }
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px-altar2_x,2) + Math.pow(pz-altar2_y,2));
        return dist<=2;
    }

    private Point2D getAltarLocation(int altar){
        double altar_x = 0d;
        double altar_y = 0d;
        if(altar == 0){
            altar_x = MapData.L2_TOP_ALTAR[0];
            altar_y = MapData.L2_TOP_ALTAR[1];
        }else if(altar == 2){
            altar_x = MapData.L2_BOT_ALTAR[0];
            altar_y = MapData.L2_BOT_ALTAR[1];
        }
        return new Point2D.Double(altar_x,altar_y);
    }
    public void addScore(UserActor earner,int team, int points){
        ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
        int blueScore = scoreObject.getInt("blue");
        int purpleScore = scoreObject.getInt("purple");
        int newBlueScore = blueScore;
        int newPurpleScore = purpleScore;
        if(team == 1) newBlueScore+=points;
        else newPurpleScore+=points;
        scoreObject.putInt("blue",newBlueScore);
        scoreObject.putInt("purple",newPurpleScore);
        ExtensionCommands.updateScores(this.parentExt,this.room,newPurpleScore,newBlueScore);
        if(newBlueScore > newPurpleScore && blueScore <= purpleScore){
            for(UserActor player : this.players){
                if(player.getTeam() == 1) ExtensionCommands.playSound(parentExt,player.getUser(),"global","announcer/gained_point_lead",new Point2D.Float(0f,0f));
                else ExtensionCommands.playSound(parentExt,player.getUser(),"global","announcer/lost_point_lead",new Point2D.Float(0f,0f));
            }
        }else if(newPurpleScore > newBlueScore && blueScore >= purpleScore){
            for(UserActor player : this.players){
                if(player.getTeam() == 0) ExtensionCommands.playSound(parentExt,player.getUser(),"global","announcer/gained_point_lead",new Point2D.Float(0f,0f));
                else ExtensionCommands.playSound(parentExt,player.getUser(),"global","announcer/lost_point_lead",new Point2D.Float(0f,0f));
            }
        }
        if(earner != null){
            earner.addGameStat("score",points);
        }
    }

    private void handleCooldowns(){ //Cooldown keys structure is id__cooldownType__value. Example for a buff cooldown could be lich__buff__attackDamage
        for(String key : cooldowns.keySet()){
            String[] keyVal = key.split("__");
            String id = keyVal[0];
            String cooldown = keyVal[1];
            String value = "";
            if(keyVal.length > 2) value = keyVal[2];
            int time = cooldowns.get(key)-1;
            if(time<=0){
                switch(cooldown){
                    case "altar":
                        for(User u : room.getUserList()){
                            int altarIndex = Integer.parseInt(id.split("_")[1]);
                            ISFSObject data = new SFSObject();
                            int altarNum = -1;
                            if(id.equalsIgnoreCase("altar_0")) altarNum = 1;
                            else if(id.equalsIgnoreCase("altar_1")) altarNum = 0;
                            else if(id.equalsIgnoreCase("altar_2")) altarNum = 2;
                            data.putInt("altar",altarNum);
                            data.putInt("team",2);
                            data.putBool("locked",false);
                            parentExt.send("cmd_altar_update",data,u);
                            altarStatus[altarIndex] = 0;
                        }
                        break;
                    case "buff":
                        System.out.println(cooldown + " still being read!");
                        break;
                }
                cooldowns.remove(key);
            }else{
                cooldowns.put(key,time);
            }
        }
    }

    public ArrayList<UserActor> getPlayers(){
        return this.players;
    }

    public UserActor getPlayer(String id){
        for(UserActor p : players){
            if(p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public void addProjectile(Projectile p){
        this.activeProjectiles.add(p);
    }

    public Minion getMinion(String id){
        for(Minion m : minions){
            if(m.getId().equalsIgnoreCase(id)) return m;
        }
        return  null;
    }

    public Tower getTower(String id){
        for(Tower t : towers){
            if(t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public Tower getTower(int num){
        for(Tower t : towers){
            if(t.getTowerNum() == num) return t;
        }
        return null;
    }

    private boolean hasSuperMinion(int lane, int team){
        for(Minion m : minions){
            if(m.getTeam() == team && m.getLane() == lane && m.getType().equalsIgnoreCase("super") && m.getHealth() > 0) return true;
        }
        return false;
    }

    public List<Actor> getActors(){
        List<Actor> actors = new ArrayList<>();
        actors.addAll(towers);
        actors.addAll(minions);
        Collections.addAll(actors, bases);
        actors.addAll(players);
        actors.addAll(campMonsters);
        actors.addAll(companions);
        actors.removeIf(a -> a.getHealth() <= 0);
        return actors;
    }

    public Actor getActor(String id){
        for(Actor a : this.getActors()){
            if(a.getId().equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    public List<Minion> getMinions(){
        return this.minions;
    }

    public List<Monster> getCampMonsters(){
        return this.campMonsters;
    }

    public List<Monster> getCampMonsters(String id){
        List<Monster> returnMonsters = new ArrayList<>(3);
        String type = id.split("_")[0];
        for(Monster m : this.campMonsters){
            if(!m.getId().equalsIgnoreCase(id) && m.getId().contains(type)){
                returnMonsters.add(m);
            }
        }
        return returnMonsters;
    }

    public void handleSpawnDeath(Actor a){
        System.out.println("The room has killed " + a.getId());
        String mons = a.getId().split("_")[0];

        for(String s : GameManager.SPAWNS){
            if(s.contains(mons)){
                if(s.contains("keeoth")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else if(s.contains("ooze")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else if(!s.contains("gnomes") && !s.contains("owls")){
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
                else {
                    for(Monster m : campMonsters){
                        if(!m.getId().equalsIgnoreCase(a.getId()) && m.getId().contains(mons)) return;
                    }
                    room.getVariable("spawns").getSFSObjectValue().putInt(s,0);
                    return;
                }
            }
        }
    }

    public void handleXPShare (UserActor a, int xp){
        a.addXP(xp);
        for(UserActor p : players){
            if(!p.getId().equalsIgnoreCase(a.getId()) && p.getTeam() == a.getTeam()){
                int newXP = (int)Math.floor(xp*0.2);
                p.addXP(newXP);
            }
        }
    }

    public void handleAssistXP (Actor a, Set<String> ids, double xp){ //TODO: I don't think this is working ! ! ! >:(
        if(a.getActorType() == ActorType.PLAYER){
            UserActor user = (UserActor) a;
            user.addXP((int) xp);
        }
        for(String id : ids){
            if(!id.equalsIgnoreCase(a.getId())){
                UserActor player = this.getPlayer(id);
                if(player != null) player.addXP((int)Math.round(xp/2)); //<-- classic dividing an integer hehe haha hehe
            }
        }
    }

    public int getAveragePlayerLevel(){
        int combinedPlayerLevel = 0;
        for(UserActor a : this.players){
            combinedPlayerLevel+=a.getLevel();
        }
        return combinedPlayerLevel/this.players.size();
    }

    public List<Tower> getTowers(){
        return this.towers;
    }
    private boolean canSpawnSupers(int team){
        for(Tower t : this.towers){
            if(t.getTeam() != team){
                if(t.getTowerNum() != 3 && t.getTowerNum() != 0 && t.getHealth() > 0) return false;
            }
        }
        return true;
    }

    public int getTeamNumber(String id, int team){
        int blue = 0;
        int purple = 0;
        for(UserActor ua : this.players){
            if(ua.getId().equalsIgnoreCase(id)){
                if(ua.getTeam() == 1) return blue;
                else return purple;
            }else{
                if(ua.getTeam() == 1) blue++;
                else purple++;
            }
        }
        return -1;
    }

    public void handleFountain(){
        Point2D blueCenter = new Point2D.Float(-50.16f,0f);
        List<Actor> blueTeam = Champion.getEnemyActorsInRadius(this,1,blueCenter,4f);
        for(Actor a : blueTeam){
            if(a.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) a;
                if(ua.getHealth() < ua.getMaxHealth()){
                    ua.setHealth(ua.getMaxHealth(),ua.getMaxHealth()); //TODO: Set to not automatically fully heal
                    ExtensionCommands.createActorFX(this.parentExt,this.room,ua.getId(),"fx_health_regen",3000,ua.getId()+"_fountainRegen",true,"Bip01",false,false,ua.getTeam());
                }
                ua.handleEffect("speed",2d,5000,"fountainSpeed");
            }
        }
        Point2D purpleCenter = new Point2D.Float(50.16f, 0f);
        List<Actor> purpleTeam = Champion.getEnemyActorsInRadius(this,0,purpleCenter,4f);
        for(Actor a : purpleTeam){ //I can optimize but that's future me's problem
            if(a.getActorType() == ActorType.PLAYER){
                UserActor ua = (UserActor) a;
                if(ua.getHealth() < ua.getMaxHealth()){
                    ua.setHealth(ua.getMaxHealth(),ua.getMaxHealth());
                    ExtensionCommands.createActorFX(this.parentExt,this.room,ua.getId(),"fx_health_regen",3000,ua.getId()+"_fountainRegen",true,"Bip01",false,false,ua.getTeam());
                }
                ua.handleEffect("speed",2d,5000,"fountainSpeed");
            }
        }
    }

    public UserActor getEnemyCharacter(String avatar, int team){
        for(UserActor ua : this.players){
            if(ua.getTeam() != team && ua.getAvatar().equalsIgnoreCase(avatar)) return ua;
        }
        return null;
    }

    public void gameOver(int winningTeam){
        try{
            this.gameOver = true;
            this.room.setProperty("state",2);
            ExtensionCommands.gameOver(parentExt,this.room,winningTeam);
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            for(UserActor ua : this.players){
                if(ua.getTeam() == winningTeam){
                    ExtensionCommands.playSound(parentExt,ua.getUser(),"global","announcer/victory");
                    ExtensionCommands.playSound(parentExt,ua.getUser(),"music","music/music_victory");
                }else{
                    ExtensionCommands.playSound(parentExt,ua.getUser(),"global","announcer/defeat");
                    ExtensionCommands.playSound(parentExt,ua.getUser(),"music","music/music_defeat");
                }
                String tegID = (String) ua.getUser().getSession().getProperty("tegid");
                Document data = playerData.find(eq("user.TEGid",tegID)).first();
                if(data != null){ //TODO: Complete all of these values
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode dataObj = mapper.readTree(data.toJson());
                    int wins = 0;
                    int points = 0;
                    int kills = (int) ua.getStat("kills");
                    int deaths = (int) ua.getStat("deaths");
                    int assists = (int) ua.getStat("assists");
                    int towers = 0;
                    int minions = 0;
                    int jungleMobs = 0;
                    int altars = 0;
                    int largestSpree = 0;
                    int largestMulti = 0;
                    int score = 0;

                    double win;
                    if(winningTeam == -1) win = 0.5d;
                    else if(ua.getTeam() == winningTeam) win = 1d;
                    else win = 0d;
                    int eloGain = ChampionData.getEloGain(ua,this.players,win);
                    if(ua.getTeam() == winningTeam) wins++;

                    int currentRankProgress = dataObj.get("player").get("rankProgress").asInt();
                    int rankIncrease = 0;
                    if(currentRankProgress >= 90){
                        currentRankProgress = 0;
                        rankIncrease++;
                    }
                    else currentRankProgress+=10;

                    if(ua.hasGameStat("score")) score+=ua.getGameStat("score");
                    if(ua.hasGameStat("towers")) towers+=ua.getGameStat("towers");
                    if(ua.hasGameStat("minions")) minions+=ua.getGameStat("minions");
                    if(ua.hasGameStat("jungleMobs")) jungleMobs+=ua.getGameStat("jungleMobs");

                    if(ua.hasGameStat("spree")){
                        int currentSpree = dataObj.get("player").get("largestSpree").asInt();
                        double gameSpree = ua.getGameStat("spree");
                        if(gameSpree > currentSpree) largestSpree = (int)gameSpree;
                    }

                    if(ua.hasGameStat("largestMulti")){
                        int currentMulti = dataObj.get("player").get("largestMulti").asInt();
                        double gameMulti = ua.getGameStat("largestMulti");
                        if(gameMulti > currentMulti) largestMulti = (int)gameMulti;
                    }

                    Bson updates = Updates.combine(
                            Updates.inc("player.playsPVP",1),
                            Updates.inc("player.elo",eloGain),
                            Updates.set("player.rankProgress",currentRankProgress),
                            Updates.inc("player.winsPVP",wins),
                            Updates.inc("player.points",points), //Always zero I have no idea what this is for?
                            Updates.inc("player.coins",100),
                            Updates.inc("player.kills",kills),
                            Updates.inc("player.deaths",deaths),
                            Updates.inc("player.assists",assists),
                            Updates.inc("player.towers",towers),
                            Updates.inc("player.minions",minions),
                            Updates.inc("player.jungleMobs",jungleMobs),
                            Updates.inc("player.altars",altars),
                            Updates.inc("player.scoreTotal",score),
                            Updates.inc("player.rank",rankIncrease),
                            Updates.set("player.largestSpree",largestSpree),
                            Updates.set("player.largestMulti",largestMulti)
                    );
                    UpdateOptions options = new UpdateOptions().upsert(true);
                    System.out.println(playerData.updateOne(data,updates,options));
                }
            }
            parentExt.stopScript(room.getId());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void handlePlayerDC(User user){
        if(this.players.size() == 1) return;
        try{
            UserActor player = this.getPlayer(String.valueOf(user.getId()));
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            Document data = playerData.find(eq("user.TEGid",(String)user.getSession().getProperty("tegid"))).first();
            if(data != null){
                ObjectMapper mapper = new ObjectMapper();
                JsonNode dataObj = mapper.readTree(data.toJson());
                double disconnects = dataObj.get("player").get("disconnects").asInt();
                double playsPVP = dataObj.get("player").get("playsPVP").asInt();
                double dcPercent = disconnects/playsPVP;
                double elo = dataObj.get("player").get("elo").asInt();
                double newElo = elo*(1-dcPercent);

                Bson updates = Updates.combine(
                        Updates.inc("player.disconnects",1),
                        Updates.set("player.elo",(int)newElo)
                );
                UpdateOptions options = new UpdateOptions().upsert(true);
                System.out.println(playerData.updateOne(data,updates,options));
            }
            int team = player.getTeam();
            this.players.removeIf(p -> p.getId().equalsIgnoreCase(String.valueOf(user.getId())));
            int teamMembersLeft = 0;
            for(UserActor p : players){
                if(p.getTeam() == team){
                    teamMembersLeft++;
                    break;
                }
            }
            int oppositeTeam = 0;
            if(team == 0) oppositeTeam = 1;
            if(teamMembersLeft == 0) this.gameOver(oppositeTeam);
            else{
                for(UserActor p : this.players){
                    if(p.getTeam() == team){
                        p.handleDCBuff(true);
                    }else p.handleDCBuff(false);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void addCompanion(Actor a){
        this.companions.add(a);
    }

    public void removeCompanion(Actor a){
        this.companions.remove(a);
    }

    public void printActors(){
        for(Actor a : this.getActors()){
            System.out.println("ROOM: " + this.room.getId() + " |  TYPE: " + a.getActorType().toString() + " | ID: " + a.getId() + " | " + a.getHealth());
        }
    }
}

package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.UserActor;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;

public class RoomHandler implements Runnable{
    private ATBPExtension parentExt;
    private Room room;
    private ArrayList<Minion> minions;
    private ArrayList<Tower> towers;
    private ArrayList<UserActor> players;
    private Base[] bases = new Base[2];
    private int mSecondsRan = 0;
    private int secondsRan = 0;
    private int[] altarStatus = {0,0,0};
    private HashMap<String,Integer> cooldowns = new HashMap<>();
    public RoomHandler(ATBPExtension parentExt, Room room){
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        towers = new ArrayList<>();
        this.players = new ArrayList<>();
        HashMap<String, Point2D> towers0 = MapData.getTowerData(room.getGroupId(),0);
        HashMap<String, Point2D> towers1 = MapData.getTowerData(room.getGroupId(),1);
        for(String key : towers0.keySet()){
            towers.add(new Tower(key,0,towers0.get(key)));
        }
        for(String key : towers1.keySet()){
            towers.add(new Tower(key,1,towers1.get(key)));
        }
        bases[0] = new Base(0);
        bases[1] = new Base(1);
        for(User u : room.getUserList()){
            players.add(new UserActor(u));
        }
    }
    @Override
    public void run() {
        if(mSecondsRan % 1000 == 0){ // Handle every second
            try{
                if(room.getUserList().size() == 0) parentExt.stopScript(room.getId()); //If no one is in the room, stop running.
                else{
                    handleAltars();
                    handleHealthRegen();
                }
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                for(String s : GameManager.SPAWNS){ //Check all mob/health spawns for how long it's been since dead
                    if(s.length()>3){
                        int spawnRate = 45;
                        if(s.equalsIgnoreCase("keeoth")) spawnRate = 120;
                        else if(s.equalsIgnoreCase("ooze")) spawnRate = 90;
                        if(spawns.getInt(s) == spawnRate){ //Mob timers will be set to 0 when killed or health when taken
                            spawnMonster(s);
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }else{
                            spawns.putInt(s,spawns.getInt(s)+1);
                        }
                    }else{
                        System.out.println("Checking health! " + s);
                        int time = spawns.getInt(s);
                        if(time == 10){
                            spawnHealth(s);
                        }
                        else if(time < 91){
                            time++;
                            spawns.putInt(s,time);
                        }
                    }
                }
                handleCooldowns();
                secondsRan++;
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        try{
            mSecondsRan+=100;
            for(UserActor u : players){ //Tracks player location
                float x = (float) u.getLocation().getX();
                float z = (float) u.getLocation().getY();
                Point2D currentPoint = u.getRelativePoint();
                if(currentPoint.getX() != x && currentPoint.getY() != z){
                    u.updateMovementTime();
                }
            }
            handleHealth();
            for(Minion m : minions){ //Handles minion behavior
                switchcase:
                switch(m.getState()){
                    case 0: // MOVING
                        if(m.getState() == 0 && (m.getPathIndex() < 10 & m.getPathIndex()>= 0) && m.getDesiredPath() != null && ((Math.abs(m.getDesiredPath().distance(m.getRelativePoint())) < 0.2) || Double.isNaN(m.getDesiredPath().getX()))){
                            m.arrived();
                            m.move(parentExt);
                        }else{
                            m.addTravelTime(0.1f);
                        }
                        for(Minion minion : minions){ // Check other minions
                            if(m.getTeam() != minion.getTeam() && m.getLane() == minion.getLane()){
                                if(m.nearEntity(minion.getRelativePoint()) && m.facingEntity(minion.getRelativePoint())){
                                    if(m.getTarget() == null){
                                        m.setTarget(parentExt, minion.getId());
                                        break switchcase;
                                    }
                                }
                            }
                        }
                        Base opposingBase = getOpposingTeamBase(m.getTeam());
                        if(opposingBase.isUnlocked() && m.nearEntity(opposingBase.getLocation(),1.8f)){
                            if(m.getTarget() == null){
                                m.setTarget(parentExt,opposingBase.getId());
                                m.moveTowardsActor(parentExt,opposingBase.getLocation());
                                break;
                            }
                        }
                        for(Tower t: towers){
                            if(t.getTeam() != m.getTeam() && m.nearEntity(t.getLocation())){ //Minion prioritizes towers over players
                                m.setTarget(parentExt,t.getId());
                                m.moveTowardsActor(parentExt,t.getLocation());
                                break switchcase;
                            }
                        }
                        for(UserActor u : players){
                            int health = u.getHealth();
                            int userTeam = u.getTeam();
                            Point2D currentPoint = u.getRelativePoint();
                            if(m.getTeam() != userTeam && health > 0 && m.nearEntity(currentPoint) && m.facingEntity(currentPoint)){
                                if(m.getTarget() == null){ //This will probably always be true.
                                    m.setTarget(parentExt,u.getId());
                                    ExtensionCommands.setTarget(parentExt,u.getUser(),m.getId(), u.getId());
                                }
                            }
                        }
                        break;
                    case 1: //PLAYER TARGET
                        UserActor target = getPlayer(m.getTarget());
                        Point2D currentPoint = target.getRelativePoint();
                        int health = target.getHealth();
                        if(!m.nearEntity(currentPoint) || health <= 0){ //Resets the minion's movement if it loses the target
                            m.setState(0);
                            m.move(parentExt);
                        }else{
                            if (m.withinAttackRange(currentPoint) || m.getAttackCooldown() < 300) {
                                m.stopMoving(parentExt);
                                if(m.canAttack()) {
                                    m.attack(parentExt,currentPoint);
                                }else{
                                    m.reduceAttackCooldown();
                                }
                            }else{
                                m.moveTowardsActor(parentExt,currentPoint);
                                if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                            }
                        }
                        break;
                    case 2: // MINION TARGET
                        Minion targetMinion = findMinion(m.getTarget());
                        if(targetMinion != null && (m.withinAttackRange(targetMinion.getRelativePoint()) || m.getAttackCooldown() < 300)){
                            if(!m.isAttacking()){
                                m.stopMoving(parentExt);
                                m.setAttacking(true);
                            }
                            if(m.canAttack()){
                                if(targetMinion.getHealth() > 0){
                                    m.attack(parentExt, targetMinion);
                                }else{ //Handles tower death and resets minion on tower kill
                                    m.setState(0);
                                    m.move(parentExt);
                                }
                            }else{
                                m.reduceAttackCooldown();
                            }
                        }else if(targetMinion != null){
                            m.moveTowardsActor(parentExt,targetMinion.getRelativePoint()); //TODO: Optimize so it's not sending a lot of commands
                            if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                        }else{
                            m.setState(0);
                            m.move(parentExt);
                        }
                        break;
                    case 3: // TOWER TARGET
                        Tower targetTower = findTower(m.getTarget());
                        if(targetTower != null && (m.withinAttackRange(targetTower.getLocation()) || m.getAttackCooldown() < 300)){
                            if(!m.isAttacking()){
                                m.stopMoving(parentExt);
                                m.setAttacking(true);
                            }
                            if(m.canAttack()){
                                if(targetTower.getHealth() > 0){
                                    m.attack(parentExt, targetTower);
                                }else{ //Handles tower death and resets minion on tower kill
                                    if(targetTower.getTowerNum() == 0 || targetTower.getTowerNum() == 3) bases[targetTower.getTeam()].unlock();
                                    m.setState(0);
                                    m.move(parentExt);
                                    break;
                                }
                            }else{
                                m.reduceAttackCooldown();
                            }
                        }else if(targetTower != null){
                            m.addTravelTime(0.1f);
                            //m.moveTowardsActor(parentExt,targetTower.getLocation()); //TODO: Optimize so it's not sending a lot of commands
                            if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                        }else{
                            m.setState(0);
                            m.move(parentExt);
                            break;
                        }
                        break;
                    case 4: // BASE TARGET
                        Base targetBase = getOpposingTeamBase(m.getTeam());
                        if(targetBase != null && (m.withinAttackRange(targetBase.getLocation(),1.8f) || m.getAttackCooldown() < 300)){
                            if(!m.isAttacking()){
                                m.stopMoving(parentExt);
                                m.setAttacking(true);
                            }
                            if(m.canAttack()){
                                if(targetBase.getHealth() > 0){
                                    m.attack(parentExt,targetBase);
                                }else{ //Handles base death and ends game
                                    parentExt.stopScript(room.getId());
                                }
                            }else{
                                m.reduceAttackCooldown();
                            }
                        }else if(targetBase != null){
                            m.addTravelTime(0.1f);
                            if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                        }
                        break;
                }
            }
            minions.removeIf(m -> (m.getHealth()<=0));
            towers.removeIf(t -> (t.getHealth()<=0));
            //TODO: Add minion waves
            if(mSecondsRan == 5000){
                this.addMinion(1,0,0,1);
                //this.addMinion(0,0,0,1);
            }else if(mSecondsRan == 7000){
                this.addMinion(1,1,0,1);
                //this.addMinion(0,1,0,1);
            }else if(mSecondsRan == 9000){
                //this.addMinion(0,2,0);
            }
            if(this.room.getUserList().size() == 0) parentExt.stopScript(this.room.getId());
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    private Tower findTower(String id){
        for(Tower t : towers){
            if(t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    private Minion findMinion(String id){
        for(Minion m : minions){
            if(m.getId().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public void addMinion(int team, int type, int wave, int lane){
        Minion m = new Minion(room, team, type, wave,lane);
        minions.add(m);
        for(User u : room.getUserList()){
            ExtensionCommands.createActor(parentExt,u,m.creationObject());
        }
        m.move(parentExt);
    }

    private Base getOpposingTeamBase(int team){
        if(team == 0) return bases[1];
        else return  bases[0];
    }

    private void handleHealth(){
        for(String s : GameManager.SPAWNS){
            if(s.length() == 3){
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                if(spawns.getInt(s) == 91){
                    for(UserActor u : players){
                        Point2D currentPoint = u.getRelativePoint();
                        if(insideHealth(currentPoint,getHealthNum(s))){
                            int team = u.getTeam();
                            Point2D healthLoc = getHealthLocation(getHealthNum(s));
                            ExtensionCommands.removeFx(parentExt,room,s+"_fx");
                            ExtensionCommands.createActorFX(parentExt,room,String.valueOf(u.getId()),"picked_up_health_cyclops",2000,s+"_fx2",true,"",false,false,team);
                            ExtensionCommands.playSound(parentExt,u.getUser(),"sfx_health_picked_up",healthLoc);
                            Champion.updateHealth(parentExt,u.getUser(),100);
                            Champion.giveBuff(parentExt,u.getUser(), Buff.HEALTH_PACK);
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
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        String map = room.getGroupId();
        for(User u : users){
            ISFSObject monsterObject = new SFSObject();
            ISFSObject monsterSpawn = new SFSObject();
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
                    }else{
                        actor="ironowl_"+abc[i];
                        x = (float)MapData.OWLS[i].getX();
                        z = (float)MapData.OWLS[i].getY();
                    }
                    monsterObject.putUtfString("id",actor);
                    monsterObject.putUtfString("actor",actor);
                    monsterObject.putFloat("rotation",0);
                    monsterSpawn.putFloat("x",x);
                    monsterSpawn.putFloat("y",0);
                    monsterSpawn.putFloat("z",z);
                    monsterObject.putSFSObject("spawn_point",monsterSpawn);
                    monsterObject.putInt("team",2);
                    parentExt.send("cmd_create_actor",monsterObject,u);
                }
            }else if(monster.length()>3){
                switch(monster){
                    case "hugwolf":
                        x = MapData.HUGWOLF[0];
                        z = MapData.HUGWOLF[1];
                        break;
                    case "grassbear":
                        x = MapData.GRASS[0];
                        z = MapData.GRASS[1];
                        break;
                    case "keeoth":
                        x = MapData.L2_KEEOTH[0];
                        z = MapData.L2_KEEOTH[1];
                        break;
                    case "ooze":
                        x = MapData.L2_OOZE[0];
                        z = MapData.L2_OOZE[1];
                        actor = "ooze_monster";
                        break;
                }
                monsterObject.putUtfString("id",actor);
                monsterObject.putUtfString("actor",actor);
                monsterObject.putFloat("rotation",0);
                monsterSpawn.putFloat("x",x);
                monsterSpawn.putFloat("y",0);
                monsterSpawn.putFloat("z",z);
                monsterObject.putSFSObject("spawn_point",monsterSpawn);
                monsterObject.putInt("team",2);
                parentExt.send("cmd_create_actor",monsterObject,u);
            }
        }
    }
    private void spawnHealth(String id){
        int healthNum = getHealthNum(id);
        Point2D healthLocation = getHealthLocation(healthNum);
        for(User u : room.getUserList()){
            int effectTime = (15*60-secondsRan)*1000;
            ExtensionCommands.createWorldFX(parentExt,u, String.valueOf(u.getId()),"pickup_health_cyclops",id+"_fx",effectTime,(float)healthLocation.getX(),(float)healthLocation.getY(),false,2,0f);
        }
        room.getVariable("spawns").getSFSObjectValue().putInt(id,91);
    }

    private void handleAltars(){
        int[] altarChange = {0,0,0};
        boolean[] playerInside = {false,false,false};
        for(UserActor u : players){

            int team = u.getTeam();
            Point2D currentPoint = u.getRelativePoint();
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
            if(Math.abs(altarStatus[i]) <= 5) altarStatus[i]+=altarChange[i];
            for(UserActor u : players){
                int team = 2;
                if(altarStatus[i]>0) team = 0;
                else team = 1;
                String altarId = "altar_"+i;
                if(Math.abs(altarStatus[i]) == 6){ //Lock altar
                    altarStatus[i]=10; //Locks altar
                    if(i == 1) addScore(team,15,u.getUser());
                    else addScore(team,10,u.getUser());
                    cooldowns.put(altarId+"__"+"altar",180);
                    ISFSObject data2 = new SFSObject();
                    data2.putUtfString("id",altarId);
                    data2.putUtfString("bundle","fx_altar_lock");
                    data2.putInt("duration",1000*60*3);
                    data2.putUtfString("fx_id","fx_altar_lock"+i);
                    data2.putBool("parent",false);
                    data2.putUtfString("emit",altarId);
                    data2.putBool("orient",false);
                    data2.putBool("highlight",true);
                    data2.putInt("team",team);
                    parentExt.send("cmd_create_actor_fx",data2,u.getUser());
                    ISFSObject data = new SFSObject();
                    int altarNum = -1;
                    if(i == 0) altarNum = 1;
                    else if(i == 1) altarNum = 0;
                    else if(i == 2) altarNum = i;
                    data.putInt("altar",altarNum);
                    data.putInt("team",team);
                    data.putBool("locked",true);
                    parentExt.send("cmd_altar_update",data,u.getUser());
                    if(u.getTeam()==team){
                        ISFSObject data3 = new SFSObject();
                        String buffName;
                        String buffDescription;
                        String icon;
                        String bundle;
                        if(i == 1){
                            buffName = "Attack Altar" +i + " Buff";
                            buffDescription = "Gives you a burst of attack damage!";
                            icon = "icon_altar_attack";
                            bundle = "altar_buff_offense";
                        }else{
                            buffName = "Defense Altar" + i + " Buff";
                            buffDescription = "Gives you defense!";
                            icon = "icon_altar_armor";
                            bundle = "altar_buff_defense";
                        }
                        data3.putUtfString("name",buffName);
                        data3.putUtfString("desc",buffDescription);
                        data3.putUtfString("icon",icon);
                        data3.putFloat("duration",1000*60);
                        parentExt.send("cmd_add_status_icon",data3,u.getUser());
                        cooldowns.put(u.getId()+"__buff__"+buffName,60);
                        ISFSObject data4 = new SFSObject();
                        data4.putUtfString("id",String.valueOf(u.getId()));
                        data4.putUtfString("bundle",bundle);
                        data4.putInt("duration",1000*60);
                        data4.putUtfString("fx_id",bundle+u.getId());
                        data4.putBool("parent",true);
                        data4.putUtfString("emit",String.valueOf(u.getId()));
                        data4.putBool("orient",true);
                        data4.putBool("highlight",true);
                        data4.putInt("team",team);
                        parentExt.send("cmd_create_actor_fx",data4,u.getUser());
                        ISFSObject data5 = new SFSObject();
                        data5.putUtfString("id",altarId);
                        data5.putUtfString("attackerId",String.valueOf(u.getId()));
                        data5.putInt("deathTime",180);
                        parentExt.send("cmd_knockout_actor",data5,u.getUser());
                        ExtensionCommands.updateActorData(parentExt,u.getUser(),ChampionData.addXP(u.getUser(),101,parentExt));
                    }
                }else if(Math.abs(altarStatus[i])<=5 && altarStatus[i]!=0){ //Update altar
                    int stage = Math.abs(altarStatus[i]);
                    ISFSObject data = new SFSObject();
                    data.putUtfString("id",altarId);
                    data.putUtfString("bundle","fx_altar_"+stage);
                    data.putInt("duration",1000);
                    data.putUtfString("fx_id","fx_altar_"+stage+i);
                    data.putBool("parent",false);
                    data.putUtfString("emit",altarId);
                    data.putBool("orient",false);
                    data.putBool("highlight",true);
                    data.putInt("team",team);
                    parentExt.send("cmd_create_actor_fx",data,u.getUser());
                }
            }
        }
    }
    private Point2D getRelativePoint(ISFSObject playerLoc){ //Gets player's current location based on time
        Point2D rPoint = new Point2D.Float();
        float x2 = playerLoc.getFloat("x");
        float y2 = playerLoc.getFloat("z");
        float x1 = playerLoc.getSFSObject("p1").getFloat("x");
        float y1 = playerLoc.getSFSObject("p1").getFloat("z");
        Line2D movementLine = new Line2D.Double(x1,y1,x2,y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        double time = dist/playerLoc.getFloat("speed");
        double currentTime = playerLoc.getFloat("time") + 0.1;
        if(currentTime>time) currentTime=time;
        double currentDist = playerLoc.getFloat("speed")*currentTime;
        float x = (float)(x1+(currentDist/dist)*(x2-x1));
        float y = (float)(y1+(currentDist/dist)*(y2-y1));
        rPoint.setLocation(x,y);
        return rPoint;
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
    private void addScore(int team, int points, User user){
        ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
        int blueScore = scoreObject.getInt("blue");
        int purpleScore = scoreObject.getInt("purple");
        if(team == 0) blueScore+=points;
        else purpleScore+=points;
        scoreObject.putInt("blue",blueScore);
        scoreObject.putInt("purple",purpleScore);
        ISFSObject pointData = new SFSObject();
        pointData.putInt("teamA",blueScore);
        pointData.putInt("teamB",purpleScore);
        parentExt.send("cmd_update_score",pointData,user);
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
                        ISFSObject data = new SFSObject();
                        data.putUtfString("name",value);
                        parentExt.send("cmd_remove_status_icon",data,room.getUserById(Integer.parseInt(id)));
                        break;
                }
                cooldowns.remove(key);
            }else{
                cooldowns.put(key,time);
            }
        }
    }

    private void handleHealthRegen(){
        for(User u : room.getUserList()){
            ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
            if(stats.getInt("currentHealth") > 0 && stats.getInt("currentHealth") < stats.getInt("maxHealth")){
                double healthRegen = stats.getDouble("healthRegen");
                Champion.updateHealth(parentExt,u,(int)healthRegen);
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
}

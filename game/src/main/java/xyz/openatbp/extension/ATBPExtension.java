package xyz.openatbp.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.SFSExtension;
import xyz.openatbp.extension.evthandlers.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.reqhandlers.*;

import java.awt.geom.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ATBPExtension extends SFSExtension {
    HashMap<String, JsonNode> actorDefinitions = new HashMap<>(); //Contains all xml definitions for the characters
    //TODO: Change Vectors to Point2D
    HashMap<String, JsonNode> itemDefinitions = new HashMap<>();
    ArrayList<Vector<Float>>[] mapColliders; //Contains all vertices for the practice map
    ArrayList<Vector<Float>>[] mainMapColliders; //Contains all vertices for the main map
    ArrayList<Path2D> mapPaths; //Contains all line paths of the colliders for the practice map
    ArrayList<Path2D> mainMapPaths; //Contains all line paths of the colliders for the main map
    ArrayList<ScheduledFuture<?>> tasks = new ArrayList(); //Contains all recurring tasks for each room
    ArrayList<ScheduledFuture<?>> miniTasks = new ArrayList();
    public void init() {
        this.addEventHandler(SFSEventType.USER_JOIN_ROOM, JoinRoomEventHandler.class);
        this.addEventHandler(SFSEventType.USER_JOIN_ZONE, JoinZoneEventHandler.class);
        this.addEventHandler(SFSEventType.USER_LOGIN, UserLoginEventHandler.class);
        this.addEventHandler(SFSEventType.ROOM_ADDED, RoomCreatedEventHandler.class);
        this.addEventHandler(SFSEventType.USER_DISCONNECT, UserDisconnect.class);

        this.addRequestHandler("req_hit_actor", HitActorHandler.class);
        this.addRequestHandler("req_keep_alive", Stub.class);
        this.addRequestHandler("req_goto_room", GotoRoomHandler.class);
        this.addRequestHandler("req_move_actor", MoveActorHandler.class);
        this.addRequestHandler("req_delayed_login", Stub.class);
        this.addRequestHandler("req_buy_item", Stub.class);
        this.addRequestHandler("req_pickup_item", Stub.class);
        this.addRequestHandler("req_do_actor_ability", DoActorAbilityHandler.class);
        this.addRequestHandler("req_console_message", Stub.class);
        this.addRequestHandler("req_mini_map_message", PingHandler.class);
        this.addRequestHandler("req_use_spell_point", SpellPointHandler.class);
        this.addRequestHandler("req_reset_spell_points", SpellPointHandler.class);
        this.addRequestHandler("req_toggle_auto_level", AutoLevelHandler.class);
        this.addRequestHandler("req_client_ready", ClientReadyHandler.class);
        this.addRequestHandler("req_dump_player", Stub.class);
        this.addRequestHandler("req_auto_target", Stub.class);
        this.addRequestHandler("req_admin_command", Stub.class);
        this.addRequestHandler("req_spam", Stub.class);

        try {
            loadDefinitions();
            loadColliders();
            loadItems();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        trace("ATBP Extension loaded");
    }

    @Override
    public void destroy(){ //Destroys all room tasks to prevent memory leaks
        super.destroy();
        for(ScheduledFuture<?> t : tasks){
            if(t != null) t.cancel(true);
        }
        for(ScheduledFuture<?> t : miniTasks){
            if(t != null) t.cancel(true);
        }
    }

    private void loadDefinitions() throws IOException { //Reads xml files and turns them into JsonNodes
        File path = new File(getCurrentFolder() + "/definitions");
        File[] files = path.listFiles();
        ObjectMapper mapper = new XmlMapper();
        for(File f : files){
            JsonNode node = mapper.readTree(f);
            actorDefinitions.put(f.getName().replace(".xml",""),node);
        }
    }

    private void loadItems() throws IOException {
        File path = new File(getCurrentFolder()+"/items");
        File[] files = path.listFiles();
        ObjectMapper mapper = new ObjectMapper();
        for(File f : files){
            JsonNode node = mapper.readTree(f);
            itemDefinitions.put(f.getName().replace(".json",""),node);
        }
    }

    private void loadColliders() throws IOException { //Reads json files and turns them into JsonNodes
        File practiceMap = new File(getCurrentFolder()+"/colliders/practice.json");
        File mainMap = new File(getCurrentFolder()+"/colliders/main.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(practiceMap);
        ArrayNode colliders = (ArrayNode) node.get("SceneColliders").get("collider");
        mapColliders = new ArrayList[colliders.size()];
        mapPaths = new ArrayList(colliders.size());
        for(int i = 0; i < colliders.size(); i++){ //Reads all colliders and makes a list of their vertices
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList(vertices.size());
            for(int g = 0; g < vertices.size(); g++){
                if(g == 0){
                    path.moveTo(vertices.get(g).get("x").asDouble(),vertices.get(g).get("z").asDouble());
                }else{ //Draws lines from each vertex to make a shape
                    path.lineTo(vertices.get(g).get("x").asDouble(),vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<Float>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            mapPaths.add(path);
            mapColliders[i] = vecs;
        }
        //Process main map. This can probably be optimized.
        node = mapper.readTree(mainMap);
        colliders = (ArrayNode) node.get("SceneColliders").get("collider");
        mainMapColliders = new ArrayList[colliders.size()];
        mainMapPaths = new ArrayList(colliders.size());
        for(int i = 0; i < colliders.size(); i++){
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList(vertices.size());
            for(int g = 0; g < vertices.size(); g++){
                if(g == 0){
                    path.moveTo(vertices.get(g).get("x").asDouble(),vertices.get(g).get("z").asDouble());
                }else{
                    path.lineTo(vertices.get(g).get("x").asDouble(),vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<Float>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            mainMapPaths.add(path);
            mainMapColliders[i] = vecs;
        }
    }

    public ArrayList<Vector<Float>>[] getColliders(String map){
        if(map.equalsIgnoreCase("practice")) return mapColliders;
        else return mainMapColliders;
    }

    public ArrayList<Path2D> getMapPaths(String map){
        if(map.equalsIgnoreCase("practice")) return mainMapPaths;
        else return mainMapPaths;
    }

    public JsonNode getDefintion(String actorName){
        return actorDefinitions.get(actorName);
    }

    public HashMap<String, JsonNode> getDefinitions(){
        return actorDefinitions;
    }

    public JsonNode getActorStats(String actorName){
        JsonNode node = actorDefinitions.get(actorName);
        if(node.has("MonoBehaviours")){
            if(node.get("MonoBehaviours").has("ActorData")){
                if(node.get("MonoBehaviours").get("ActorData").has("actorStats")){
                    return node.get("MonoBehaviours").get("ActorData").get("actorStats");
                }
            }

        }
        return null;
    }

    public void startScripts(Room room){ //Creates a new task scheduler for a room
        tasks.add(SmartFoxServer.getInstance().getTaskScheduler().scheduleAtFixedRate(new MatchScripts(room,tasks.size()),1,1, TimeUnit.SECONDS));
        miniTasks.add(SmartFoxServer.getInstance().getTaskScheduler().scheduleAtFixedRate(new MiniScripts(room,miniTasks.size()),100,100,TimeUnit.MILLISECONDS));
    }

    public void stopScript(int val){ //Stops a task scheduler when room is deleted
        trace("Stopping script!");
        tasks.get(val).cancel(true);
        tasks.remove(val);
        miniTasks.get(val).cancel(true);
        miniTasks.remove(val);
    }

    private class MatchScripts implements Runnable{
        private Room room;
        private int secondsRan = 0;
        private int aValue;
        private int[] altarStatus = {0,0,0};
        private HashMap<String,Integer> cooldowns = new HashMap<>();

        public MatchScripts(Room room, int aValue){
            this.room = room;
            this.aValue = aValue;
        }
        @Override
        public void run() {
            try{
                if(room.getUserList().size() == 0) stopScript(aValue); //If no one is in the room, stop running.
                else{
                    handleAltars();
                    handleHealthRegen();
                }
               // trace("Running passively! " + secondsRan);
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
                            trace("Spawning health!");
                            spawnHealth(s);
                        }
                        else if(time < 91){
                            trace(s + " time remaining: " + time);
                            time++;
                            spawns.putInt(s,time);
                        }
                        else{
                            trace(s + " time remaining: " + time);
                        }
                    }
                }
                handleCooldowns();
                secondsRan++;
            }catch(Exception e){
                e.printStackTrace();
            }
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
                        send("cmd_create_actor",monsterObject,u);
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
                    send("cmd_create_actor",monsterObject,u);
                }
            }
        }
        private void spawnHealth(String id){
            int healthNum = getHealthNum(id);
            Point2D healthLocation = getHealthLocation(healthNum);
            for(User u : room.getUserList()){
                int effectTime = (15*60-secondsRan)*1000;
                ExtensionCommands.createWorldFX(ATBPExtension.this,u, String.valueOf(u.getId()),"pickup_health_cyclops",id+"_fx",effectTime,(float)healthLocation.getX(),(float)healthLocation.getY(),false,2,0f);
            }
            room.getVariable("spawns").getSFSObjectValue().putInt(id,91);
        }

        private void handleAltars(){
            int[] altarChange = {0,0,0};
            boolean[] playerInside = {false,false,false};
            for(User u : room.getUserList()){

                int team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
                Point2D currentPoint = getRelativePoint(u.getVariable("location").getSFSObjectValue());
                for(int i = 0; i < 3; i++){ // 0 is top, 1 is mid, 2 is bot
                    if(insideAltar(currentPoint,i)){
                        playerInside[i] = true;
                        trace("inside altar!");
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
                for(User u : room.getPlayersList()){
                    int team = 2;
                    if(altarStatus[i]>0) team = 0;
                    else team = 1;
                    String altarId = "altar_"+i;
                    if(Math.abs(altarStatus[i]) == 6){ //Lock altar
                        trace("Updating altar!");
                        altarStatus[i]=10; //Locks altar
                        if(i == 1) addScore(team,15,u);
                        else addScore(team,10,u);
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
                        send("cmd_create_actor_fx",data2,u);
                        ISFSObject data = new SFSObject();
                        int altarNum = -1;
                        if(i == 0) altarNum = 1;
                        else if(i == 1) altarNum = 0;
                        else if(i == 2) altarNum = i;
                        data.putInt("altar",altarNum);
                        data.putInt("team",team);
                        data.putBool("locked",true);
                        send("cmd_altar_update",data,u);
                        if(Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"))==team){
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
                            send("cmd_add_status_icon",data3,u);
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
                            send("cmd_create_actor_fx",data4,u);
                            ISFSObject data5 = new SFSObject();
                            data5.putUtfString("id",altarId);
                            data5.putUtfString("attackerId",String.valueOf(u.getId()));
                            data5.putInt("deathTime",180);
                            send("cmd_knockout_actor",data5,u);
                            ExtensionCommands.updateActorData(ATBPExtension.this,u,ChampionData.addXP(u,101,ATBPExtension.this));
                        }
                    }else if(Math.abs(altarStatus[i])<=5 && altarStatus[i]!=0){ //Update altar
                        int stage = Math.abs(altarStatus[i]);
                        trace("Updating altar status! " + stage);
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
                        send("cmd_create_actor_fx",data,u);
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

        private void addScore(int team, int points, List<User> users){
            int blueScore = room.getVariable("score").getSFSObjectValue().getInt("blue");
            int purpleScore = room.getVariable("score").getSFSObjectValue().getInt("purple");
            if(team == 0) blueScore+=points;
            else purpleScore+=points;
            ISFSObject pointData = new SFSObject();
            pointData.putInt("teamA",blueScore);
            pointData.putInt("teamB",purpleScore);
            for(User u : users){
                send("cmd_update_score",pointData,u);
            }
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
            send("cmd_update_score",pointData,user);
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
                                send("cmd_altar_update",data,u);
                                altarStatus[altarIndex] = 0;
                            }
                            break;
                        case "buff":
                            ISFSObject data = new SFSObject();
                            data.putUtfString("name",value);
                            send("cmd_remove_status_icon",data,room.getUserById(Integer.parseInt(id)));
                            break;
                    }
                    cooldowns.remove(key);
                }else{
                    cooldowns.put(key,time);
                }
            }
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

        private void handleHealthRegen(){
            for(User u : room.getUserList()){
                ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
                if(stats.getInt("currentHealth") < stats.getInt("maxHealth")){
                    double healthRegen = stats.getDouble("healthRegen");
                    Champion.updateHealth(ATBPExtension.this,u,(int)healthRegen);
                }

            }
        }

    }
    private class MiniScripts implements Runnable{
        private Room room;
        private int index;
        private ArrayList<Minion> minions;
        private ArrayList<Tower> towers;
        private Base[] bases = new Base[2];
        private int mSecondsRan = 0;
        public MiniScripts(Room room, int index){
            this.room = room;
            this.index = index;
            this.minions = new ArrayList<>();
            towers = new ArrayList<>();
            HashMap<String,Point2D> towers0 = MapData.getTowerData(room.getGroupId(),0);
            HashMap<String, Point2D> towers1 = MapData.getTowerData(room.getGroupId(),1);
            for(String key : towers0.keySet()){
                towers.add(new Tower(key,0,towers0.get(key)));
            }
            for(String key : towers1.keySet()){
                towers.add(new Tower(key,1,towers1.get(key)));
            }
            bases[0] = new Base(0);
            bases[1] = new Base(1);
        }
        @Override
        public void run() {
            try{
                mSecondsRan+=100;
                for(User u : room.getUserList()){ //Tracks player location
                    float x = u.getVariable("location").getSFSObjectValue().getFloat("x");
                    float z = u.getVariable("location").getSFSObjectValue().getFloat("z");
                    Point2D currentPoint = getRelativePoint(u.getVariable("location").getSFSObjectValue());
                    if(currentPoint.getX() != x && currentPoint.getY() != z){
                        u.getVariable("location").getSFSObjectValue().putFloat("time",u.getVariable("location").getSFSObjectValue().getFloat("time")+0.1f);
                    }
                }
                handleHealth();
                for(Minion m : minions){ //Handles minion behavior
                    switchcase:
                        switch(m.getState()){
                            case 0: // MOVING
                                if(m.getState() == 0 && (m.getPathIndex() < 10 & m.getPathIndex()>= 0) && m.getDesiredPath() != null && ((Math.abs(m.getDesiredPath().distance(m.getRelativePoint())) < 0.2) || Double.isNaN(m.getDesiredPath().getX()))){
                                    m.arrived();
                                    m.move(ATBPExtension.this);
                                }else{
                                    m.addTravelTime(0.1f);
                                }
                                for(Minion minion : minions){
                                    if(m.getTeam() != minion.getTeam() && m.getLane() == minion.getLane()){
                                        if(m.nearEntity(minion.getRelativePoint()) && m.facingEntity(minion.getRelativePoint())){
                                            if(m.getTarget() == null){
                                                m.setTarget(ATBPExtension.this, minion.getId());
                                                break switchcase;
                                            }
                                        }
                                    }
                                }
                                Base opposingBase = getOpposingTeamBase(m.getTeam());
                                if(opposingBase.isUnlocked() && m.nearEntity(opposingBase.getLocation(),1.8f)){
                                    if(m.getTarget() == null){
                                        m.setTarget(ATBPExtension.this,opposingBase.getId());
                                        m.moveTowardsActor(ATBPExtension.this,opposingBase.getLocation());
                                        break;
                                    }
                                }
                                for(Tower t: towers){
                                    if(t.getTeam() != m.getTeam() && m.nearEntity(t.getLocation())){ //Minion prioritizes towers over players
                                        m.setTarget(ATBPExtension.this,t.getId());
                                        m.moveTowardsActor(ATBPExtension.this,t.getLocation());
                                        break switchcase;
                                    }
                                }
                                for(User u : room.getUserList()){
                                    int userTeam = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
                                    Point2D currentPoint = getRelativePoint(u.getVariable("location").getSFSObjectValue());
                                    if(m.getTeam() != userTeam && m.nearEntity(currentPoint) && m.facingEntity(currentPoint)){
                                        if(m.getTarget() == null){ //This will probably always be true.
                                            m.setTarget(ATBPExtension.this,String.valueOf(u.getId()));
                                            ExtensionCommands.setTarget(ATBPExtension.this,u,m.getId(), String.valueOf(u.getId()));
                                        }
                                    }
                                }
                                break;
                            case 1: //PLAYER TARGET
                                Point2D currentPoint = getRelativePoint(room.getUserById(Integer.parseInt(m.getTarget())).getVariable("location").getSFSObjectValue());
                                if(!m.nearEntity(currentPoint)){ //Resets the minion's movement if it loses the target
                                    m.setState(0);
                                    m.move(ATBPExtension.this);
                                }else{
                                    if (m.withinAttackRange(currentPoint) || m.getAttackCooldown() < 300) {
                                        m.stopMoving(ATBPExtension.this);
                                        if(m.canAttack()) {
                                            m.attack(ATBPExtension.this,currentPoint);
                                        }else{
                                            m.reduceAttackCooldown();
                                        }
                                    }else{
                                        m.moveTowardsActor(ATBPExtension.this,currentPoint);
                                        if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                                    }
                                }
                                break;
                            case 2: // MINION TARGET
                                Minion targetMinion = findMinion(m.getTarget());
                                if(targetMinion != null && (m.withinAttackRange(targetMinion.getRelativePoint()) || m.getAttackCooldown() < 300)){
                                    if(!m.isAttacking()){
                                        m.stopMoving(ATBPExtension.this);
                                        m.setAttacking(true);
                                    }
                                    if(m.canAttack()){
                                        if(targetMinion.getHealth() > 0){
                                            m.attack(ATBPExtension.this, targetMinion);
                                        }else{ //Handles tower death and resets minion on tower kill
                                            m.setState(0);
                                            m.move(ATBPExtension.this);
                                        }
                                    }else{
                                        m.reduceAttackCooldown();
                                    }
                                }else if(targetMinion != null){
                                    trace("Distance: " + m.getRelativePoint().distance(targetMinion.getRelativePoint()));
                                    m.moveTowardsActor(ATBPExtension.this,targetMinion.getRelativePoint()); //TODO: Optimize so it's not sending a lot of commands
                                    if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                                }else{
                                    m.setState(0);
                                    m.move(ATBPExtension.this);
                                }
                                break;
                            case 3: // TOWER TARGET
                                Tower targetTower = findTower(m.getTarget());
                                if(targetTower != null && (m.withinAttackRange(targetTower.getLocation()) || m.getAttackCooldown() < 300)){
                                    if(!m.isAttacking()){
                                        m.stopMoving(ATBPExtension.this);
                                        m.setAttacking(true);
                                    }
                                    if(m.canAttack()){
                                            if(targetTower.getHealth() > 0){
                                                m.attack(ATBPExtension.this, targetTower);
                                            }else{ //Handles tower death and resets minion on tower kill
                                                if(targetTower.getTowerNum() == 0 || targetTower.getTowerNum() == 3) bases[targetTower.getTeam()].unlock();
                                                m.setState(0);
                                                m.move(ATBPExtension.this);
                                                break;
                                            }
                                    }else{
                                        m.reduceAttackCooldown();
                                    }
                                }else if(targetTower != null){
                                    trace("Distance: " + m.getRelativePoint().distance(targetTower.getLocation()));
                                    m.addTravelTime(0.1f);
                                    //m.moveTowardsActor(ATBPExtension.this,targetTower.getLocation()); //TODO: Optimize so it's not sending a lot of commands
                                    if(m.getAttackCooldown() > 300) m.reduceAttackCooldown();
                                }else{
                                    m.setState(0);
                                    m.move(ATBPExtension.this);
                                    break;
                                }
                                break;
                            case 4: // BASE TARGET
                                Base targetBase = getOpposingTeamBase(m.getTeam());
                                if(targetBase != null && (m.withinAttackRange(targetBase.getLocation(),1.8f) || m.getAttackCooldown() < 300)){
                                    if(!m.isAttacking()){
                                        m.stopMoving(ATBPExtension.this);
                                        m.setAttacking(true);
                                    }
                                    if(m.canAttack()){
                                        if(targetBase.getHealth() > 0){
                                            m.attack(ATBPExtension.this,targetBase);
                                        }else{ //Handles base death and ends game
                                            stopScript(index);
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
                    trace("Adding minion!");
                    this.addMinion(1,0,0,1);
                    //this.addMinion(0,0,0,1);
                }else if(mSecondsRan == 7000){
                    this.addMinion(1,1,0,1);
                    //this.addMinion(0,1,0,1);
                }else if(mSecondsRan == 9000){
                    //this.addMinion(0,2,0);
                }
                if(this.room.getUserList().size() == 0) stopScript(this.index);
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
                ExtensionCommands.createActor(ATBPExtension.this,u,m.creationObject());
            }
            m.move(ATBPExtension.this);
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

        private Base getOpposingTeamBase(int team){
            if(team == 0) return bases[1];
            else return  bases[0];
        }

        private void handleHealth(){
            for(String s : GameManager.SPAWNS){
                if(s.length() == 3){
                    ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                    if(spawns.getInt(s) == 91){
                        for(User u : room.getUserList()){
                            Point2D currentPoint = getRelativePoint(u.getVariable("location").getSFSObjectValue());
                            if(insideHealth(currentPoint,getHealthNum(s))){
                                int team = Integer.parseInt(u.getVariable("player").getSFSObjectValue().getUtfString("team"));
                                Point2D healthLoc = getHealthLocation(getHealthNum(s));
                                ExtensionCommands.removeFx(ATBPExtension.this,room,s+"_fx");
                                ExtensionCommands.createActorFX(ATBPExtension.this,room,String.valueOf(u.getId()),"picked_up_health_cyclops",2000,s+"_fx2",true,"",false,false,team);
                                ExtensionCommands.playSound(ATBPExtension.this,u,"sfx_health_picked_up",healthLoc);
                                Champion.updateHealth(ATBPExtension.this,u,100);
                                Champion.giveBuff(ATBPExtension.this,u, Buff.HEALTH_PACK);
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

        private ArrayList<User> getNearbyPlayers(Point2D source){
            ArrayList<User> users = new ArrayList<>();
            for(User u : room.getUserList()){
                Point2D currentLocation = getRelativePoint(u.getVariable("location").getSFSObjectValue());
                if(source.distance(currentLocation) <= 5) users.add(u);
            }
            return users;
        }
    }
}

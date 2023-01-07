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
import xyz.openatbp.extension.reqhandlers.*;

import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ATBPExtension extends SFSExtension {
    HashMap<String, JsonNode> actorDefinitions = new HashMap<>(); //Contains all xml definitions for the characters
    //TODO: Change Vectors to Point2D
    ArrayList<Vector<Float>>[] mapColliders; //Contains all vertices for the practice map
    ArrayList<Vector<Float>>[] mainMapColliders; //Contains all vertices for the main map
    ArrayList<Path2D> mapPaths; //Contains all line paths of the colliders for the practice map
    ArrayList<Path2D> mainMapPaths; //Contains all line paths of the colliders for the main map
    ArrayList<ScheduledFuture<?>> tasks = new ArrayList(); //Contains all recurring tasks for each room
    public void init() {
        this.addEventHandler(SFSEventType.USER_JOIN_ROOM, JoinRoomEventHandler.class);
        this.addEventHandler(SFSEventType.USER_JOIN_ZONE, JoinZoneEventHandler.class);
        this.addEventHandler(SFSEventType.USER_LOGIN, UserLoginEventHandler.class);
        this.addEventHandler(SFSEventType.ROOM_ADDED, RoomCreatedEventHandler.class);
        this.addEventHandler(SFSEventType.USER_DISCONNECT, UserDisconnect.class);

        this.addRequestHandler("req_hit_actor", Stub.class);
        this.addRequestHandler("req_keep_alive", Stub.class);
        this.addRequestHandler("req_goto_room", GotoRoomHandler.class);
        this.addRequestHandler("req_move_actor", MoveActorHandler.class);
        this.addRequestHandler("req_delayed_login", Stub.class);
        this.addRequestHandler("req_buy_item", Stub.class);
        this.addRequestHandler("req_pickup_item", Stub.class);
        this.addRequestHandler("req_do_actor_ability", Stub.class);
        this.addRequestHandler("req_console_message", Stub.class);
        this.addRequestHandler("req_mini_map_message", PingHandler.class);
        this.addRequestHandler("req_use_spell_point", Stub.class);
        this.addRequestHandler("req_reset_spell_points", Stub.class);
        this.addRequestHandler("req_toggle_auto_level", Stub.class);
        this.addRequestHandler("req_client_ready", ClientReadyHandler.class);
        this.addRequestHandler("req_dump_player", Stub.class);
        this.addRequestHandler("req_auto_target", Stub.class);
        try {
            loadDefinitions();
            loadColliders();
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
    }

    public void stopScript(int val){ //Stops a task scheduler when room is deleted
        trace("Stopping script!");
        tasks.get(val).cancel(true);
        tasks.remove(val);
    }

    private class MatchScripts implements Runnable{
        private Room room;
        private int secondsRan = 0;
        private int aValue;

        public MatchScripts(Room room, int aValue){
            this.room = room;
            this.aValue = aValue;
        }
        @Override
        public void run() {

            try{
                if(room.getUserList().size() == 0) stopScript(aValue); //If no one is in the room, stop running.
                else{
                    List<User> users = room.getUserList();
                    for(User u : users){ //Check all player's movements. Will be used for altars
                        String name = u.getVariable("player").getSFSObjectValue().getUtfString("name");
                        float x = u.getVariable("location").getSFSObjectValue().getFloat("x");
                        float z = u.getVariable("location").getSFSObjectValue().getFloat("z");
                        trace(name + " at " + x + "," + z);

                    }
                }
                trace("Running passively! " + secondsRan);
                ISFSObject mobSpawns = room.getVariable("spawns").getSFSObjectValue();
                for(String s : GameManager.SPAWNS){ //Check all mob/health spawns for how long it's been since dead
                    int spawnRate = 45;
                    if(s.equalsIgnoreCase("keeoth")) spawnRate = 120;
                    else if(s.equalsIgnoreCase("ooze")) spawnRate = 90;
                    if(mobSpawns.getInt(s) == spawnRate){ //Mob timers will be set to 0 when killed or health when taken
                        spawnMonster(s);
                        mobSpawns.putInt(s,mobSpawns.getInt(s)+1);
                    }else{
                        mobSpawns.putInt(s,mobSpawns.getInt(s)+1);
                    }
                }
                secondsRan++;
            }catch(Exception e){
                trace(e.toString());
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
    }
}

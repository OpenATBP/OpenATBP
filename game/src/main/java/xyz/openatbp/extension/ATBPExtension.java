package xyz.openatbp.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.extensions.SFSExtension;
import xyz.openatbp.extension.evthandlers.*;
import xyz.openatbp.extension.reqhandlers.*;

import java.awt.geom.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ATBPExtension extends SFSExtension {
    HashMap<String, JsonNode> actorDefinitions = new HashMap<>(); //Contains all xml definitions for the characters
    //TODO: Change Vectors to Point2D
    HashMap<String, JsonNode> itemDefinitions = new HashMap<>();
    ArrayList<Vector<Float>>[] mapColliders; //Contains all vertices for the practice map
    ArrayList<Vector<Float>>[] mainMapColliders; //Contains all vertices for the main map

    ArrayList<Vector<Float>>[] brushColliders;
    ArrayList<Path2D> mapPaths; //Contains all line paths of the colliders for the practice map
    ArrayList<Path2D> mainMapPaths; //Contains all line paths of the colliders for the main map

    ArrayList<Path2D> brushPaths;
    HashMap<String, List<String>> tips = new HashMap<>();

    HashMap<Integer, RoomHandler> roomHandlers = new HashMap<>();
    HashMap<Integer, ScheduledFuture<?>> roomTasks = new HashMap<>();
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
        this.addRequestHandler("req_auto_target", AutoTargetHandler.class);
        this.addRequestHandler("req_admin_command", Stub.class);
        this.addRequestHandler("req_spam", Stub.class);

        try {
            loadDefinitions();
            loadColliders();
            loadItems();
            loadTips();
            loadBrushes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        trace("ATBP Extension loaded");
    }

    @Override
    public void destroy(){ //Destroys all room tasks to prevent memory leaks
        super.destroy();
        for(Integer key : roomTasks.keySet()){
            if(roomTasks.get(key) != null) roomTasks.get(key).cancel(true);
        }
    }

    private void loadDefinitions() throws IOException { //Reads xml files and turns them into JsonNodes
        File path = new File(getCurrentFolder() + "/definitions");
        File[] files = path.listFiles();
        ObjectMapper mapper = new XmlMapper();
        for(File f : files){
            if(f.getName().contains("xml")){
                JsonNode node = mapper.readTree(f);
                actorDefinitions.put(f.getName().replace(".xml",""),node);
            }
        }
    }

    private void loadItems() throws IOException {
        File path = new File(getCurrentFolder()+"/data/items");
        File[] files = path.listFiles();
        ObjectMapper mapper = new ObjectMapper();
        for(File f : files){
            JsonNode node = mapper.readTree(f);
            itemDefinitions.put(f.getName().replace(".json",""),node);
        }
    }

    private void loadTips() throws IOException {
        File tipsFile = new File(getCurrentFolder() +"/data/tips.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tipsJson = mapper.readTree(tipsFile);
        ArrayNode categoryNode = (ArrayNode) tipsJson.get("gameTips").get("category");
        for(JsonNode category : categoryNode){
            String jsonString = mapper.writeValueAsString(category.get("tip")).replace("[","").replace("]","").replaceAll("\"","");
            String[] tips = jsonString.split(",");
            this.tips.put(category.get("name").asText(),Arrays.asList(tips));
        }
    }

    private void loadColliders() throws IOException { //Reads json files and turns them into JsonNodes
        File practiceMap = new File(getCurrentFolder()+"/data/colliders/practice.json");
        File mainMap = new File(getCurrentFolder()+"/data/colliders/main.json");
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

    public JsonNode getDefinition(String actorName){
        return actorDefinitions.get(actorName);
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

    public JsonNode getActorData(String actorName){
        trace("Getting data for " + actorName);
        JsonNode node = actorDefinitions.get(actorName);
        if(node.has("MonoBehaviours")){
            if(node.get("MonoBehaviours").has("ActorData")) return node.get("MonoBehaviours").get("ActorData");
        }
        return null;
    }

    public int getActorXP(String actorName){
        JsonNode node = getActorData(actorName).get("scriptData");
        return node.get("xp").asInt();
    }

    public int getHealthScaling(String actorName){
        return getActorData(actorName).get("scriptData").get("healthScaling").asInt();
    }

    public JsonNode getAttackData(String actorName, String attack){
        try{
            return this.getActorData(actorName).get(attack);
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public String getDisplayName(String actorName){
        return this.getActorData(actorName).get("playerData").get("playerDisplayName").asText();
    }

    public String getRandomTip(String type){
        List<String> tips = this.tips.get(type);
        int num = (int)(Math.random()*(tips.size()-1));
        return tips.get(num);
    }

    public void startScripts(Room room){ //Creates a new task scheduler for a room
        RoomHandler handler = new RoomHandler(this,room);
        roomHandlers.put(room.getId(),handler);
        roomTasks.put(room.getId(),SmartFoxServer.getInstance().getTaskScheduler().scheduleAtFixedRate(handler,100,100,TimeUnit.MILLISECONDS));
    }

    public void stopScript(int roomId){ //Stops a task scheduler when room is deleted
        trace("Stopping script!");
        roomTasks.get(roomId).cancel(true);
        roomTasks.remove(roomId);
        roomHandlers.remove(roomId);
    }

    public RoomHandler getRoomHandler(int roomId){
        return roomHandlers.get(roomId);
    }

    private void loadBrushes() throws IOException { //Reads json files and turns them into JsonNodes
        File mainMap = new File(getCurrentFolder()+"/data/colliders/brush.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(mainMap);
        ArrayNode colliders = (ArrayNode) node.get("BrushAreas").get("brush");
        brushColliders = new ArrayList[colliders.size()];
        brushPaths = new ArrayList<>(colliders.size());
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
            brushPaths.add(path);
            brushColliders[i] = vecs;
        }
    }

    public ArrayList<Path2D> getBrushPaths(){
        return this.brushPaths;
    }

    public Path2D getBrush(int num){
        return this.brushPaths.get(num);
    }
}

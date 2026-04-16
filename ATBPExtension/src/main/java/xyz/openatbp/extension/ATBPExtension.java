package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.SFSExtension;
import com.smartfoxserver.v2.util.TaskScheduler;

import xyz.openatbp.extension.evthandlers.*;
import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.RoomGroup;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.reqhandlers.*;

public class ATBPExtension extends SFSExtension {
    HashMap<String, JsonNode> actorDefinitions =
            new HashMap<>(); // Contains all xml definitions for the characters
    HashMap<String, JsonNode> itemDefinitions = new HashMap<>();

    Point2D[] mainMapBoundaries;
    List<Point2D[]> mainMapObstacles = new ArrayList<>();

    Point2D[] practiceMapBoundaries;
    List<Point2D[]> practiceMapObstacles = new ArrayList<>();

    Map<String, StressLogger> commandStressLog = new HashMap<>();
    ArrayList<Vector<Float>>[] brushColliders;
    ArrayList<Vector<Float>>[] practiceBrushColliders;

    ArrayList<Path2D> battleLabBrushPaths;
    ArrayList<Path2D> candyStreetsBrushPaths;
    HashMap<String, List<String>> tips = new HashMap<>();

    HashMap<String, RoomHandler> roomHandlers = new HashMap<>();
    MongoClient mongoClient;
    MongoDatabase database;
    MongoCollection<Document> playerDatabase;
    MongoCollection<Document> championDatabase;
    MongoCollection<Document> matchHistoryDatabase;

    TaskScheduler taskScheduler;

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
        this.taskScheduler = getApi().getNewScheduler(2);
        Properties props = getConfigProperties();
        if (!props.containsKey("mongoURI"))
            throw new RuntimeException(
                    "Mongo URI not set. Please create config.properties in the extension folder and define it.");

        try {
            mongoClient = MongoClients.create(props.getProperty("mongoURI"));
            database = mongoClient.getDatabase("openatbp");
            playerDatabase = database.getCollection("users");
            championDatabase = database.getCollection("champions");
            matchHistoryDatabase = database.getCollection("matches");
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
    public void destroy() { // Destroys all room tasks to prevent memory leaks
        for (String key : roomHandlers.keySet()) {
            if (roomHandlers.get(key) != null) roomHandlers.get(key).stopScript(true);
        }
        super.destroy();
    }

    private void loadDefinitions()
            throws IOException { // Reads xml files and turns them into JsonNodes
        File path = new File(getCurrentFolder() + "/definitions");
        File[] files = path.listFiles();
        ObjectMapper mapper = new XmlMapper();
        assert files != null;
        for (File f : files) {
            if (f.getName().contains("xml")) {
                JsonNode node = mapper.readTree(f);
                actorDefinitions.put(f.getName().replace(".xml", ""), node);
            }
        }
    }

    private void loadItems() throws IOException {
        File path = new File(getCurrentFolder() + "/data/items");
        File[] files = path.listFiles();
        ObjectMapper mapper = new ObjectMapper();
        assert files != null;
        for (File f : files) {
            JsonNode node = mapper.readTree(f);
            itemDefinitions.put(f.getName().replace(".json", ""), node);
        }
    }

    public TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    private void loadTips() throws IOException {
        File tipsFile = new File(getCurrentFolder() + "/data/tips.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tipsJson = mapper.readTree(tipsFile);
        ArrayNode categoryNode = (ArrayNode) tipsJson.get("gameTips").get("category");
        for (JsonNode category : categoryNode) {
            String jsonString =
                    mapper.writeValueAsString(category.get("tip"))
                            .replace("[", "")
                            .replace("]", "")
                            .replaceAll("\"", "");
            String[] tips = jsonString.split(",");
            this.tips.put(category.get("name").asText(), Arrays.asList(tips));
        }
    }

    private List<Point2D[]> getMapObstaclesFromFile(GameMap map) throws IOException {
        List<Point2D[]> obstacleList = new ArrayList<>();
        String filePath = getCurrentFolder();

        if (map == GameMap.BATTLE_LAB) {
            filePath += "/data/colliders/mainMapObstacles.json";
        } else {
            filePath += "/data/colliders/practiceMapObstacles.json";
        }

        File mapObstacles = new File(filePath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode obstacleNode = mapper.readTree(mapObstacles);

        JsonNode obstacles = obstacleNode.get("obstacles");

        for (JsonNode obstacle : obstacles) {
            JsonNode vertices = obstacle.get("vertices");

            Point2D[] points = getPoint2DArrFromJson(vertices);
            obstacleList.add(points);
        }
        return obstacleList;
    }

    private Point2D[] getMapBoundariesFromFile(GameMap map) throws IOException {
        String filePath = getCurrentFolder();
        if (map == GameMap.BATTLE_LAB) {
            filePath += "/data/colliders/mainMapBoundaries.json";
        } else {
            filePath += "/data/colliders/practiceMapBoundaries.json";
        }

        File mapBoundary = new File(filePath);

        ObjectMapper m = new ObjectMapper();
        JsonNode nod = m.readTree(mapBoundary);
        JsonNode boundaries = nod.get("boundaries");

        return getPoint2DArrFromJson(boundaries);
    }

    private Point2D[] getPoint2DArrFromJson(JsonNode boundaries) {
        Point2D[] boundariesList = new Point2D[boundaries.size()];

        int a = 0;

        for (JsonNode pointNode : boundaries) {
            double x = pointNode.get("x").asDouble();
            double y = pointNode.get("z").asDouble();
            boundariesList[a++] = new Point2D.Double(x, y);
        }
        return boundariesList;
    }

    private void loadColliders() throws IOException {
        mainMapBoundaries = getMapBoundariesFromFile(GameMap.BATTLE_LAB);
        mainMapObstacles = getMapObstaclesFromFile(GameMap.BATTLE_LAB);

        practiceMapBoundaries = getMapBoundariesFromFile(GameMap.CANDY_STREETS);
        practiceMapObstacles = getMapObstaclesFromFile(GameMap.CANDY_STREETS);
    }

    public Point2D[] getMainMapBoundary() {
        return this.mainMapBoundaries;
    }

    public List<Point2D[]> getMainMapObstacles() {
        return this.mainMapObstacles;
    }

    public JsonNode getDefinition(String actorName) {
        return actorDefinitions.get(actorName);
    }

    public JsonNode getActorStats(String actorName) {
        JsonNode node = actorDefinitions.get(actorName);
        if (node.has("MonoBehaviours")) {
            if (node.get("MonoBehaviours").has("ActorData")) {
                if (node.get("MonoBehaviours").get("ActorData").has("actorStats")) {
                    return node.get("MonoBehaviours").get("ActorData").get("actorStats");
                }
            }
        }
        return null;
    }

    public JsonNode getActorData(String actorName) {
        JsonNode node = actorDefinitions.get(actorName);
        if (node != null && node.has("MonoBehaviours")) {
            if (node.get("MonoBehaviours").has("ActorData"))
                return node.get("MonoBehaviours").get("ActorData");
        }
        return null;
    }

    public int getActorXP(String actorName) {
        JsonNode node = getActorData(actorName).get("scriptData");
        return node.get("xp").asInt();
    }

    public int getHealthScaling(String actorName) {
        return getActorData(actorName).get("scriptData").get("healthScaling").asInt();
    }

    public JsonNode getAttackData(String actorName, String attack) {
        try {
            if (actorName.contains("turret")) {
                return this.getActorData("princessbubblegum").get("spell2");
            }
            return this.getActorData(actorName).get(attack);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDisplayName(String actorName) {
        return this.getActorData(actorName).get("playerData").get("playerDisplayName").asText();
    }

    public String getRandomTip(String type) {
        List<String> tips = this.tips.get(type);
        int num = (int) (Math.random() * (tips.size() - 1));
        return tips.get(num);
    }

    public void startScripts(Room room) { // Creates a new task scheduler for a room
        if (!this.roomHandlers.containsKey(room.getName())) {
            String groupId = room.getGroupId();
            RoomGroup roomGroup = GameManager.getRoomGroupEnum(groupId);

            int HP_RATE = MapData.NORMAL_HP_SPAWN_RATE;
            String[] pSpawns = GameManager.L1_SPAWNS;
            String rName = room.getName();

            RoomHandler rh;

            switch (roomGroup) {
                case TUTORIAL:
                    rh =
                            new TutorialRoomHandler(
                                    this, room, practiceMapBoundaries, practiceMapObstacles);
                    roomHandlers.put(rName, rh);
                    rh.initPlayers();
                    break;

                case PRACTICE:
                case CUSTOM_CANDY_STREETS:
                    rh =
                            new PracticeRoomHandler(
                                    this,
                                    room,
                                    pSpawns,
                                    HP_RATE,
                                    practiceMapBoundaries,
                                    practiceMapObstacles);
                    roomHandlers.put(rName, rh);
                    rh.initPlayers();
                    break;

                case PVB:
                    rh = new PVBRoomHandler(this, room, mainMapBoundaries, mainMapObstacles);
                    roomHandlers.put(rName, rh);
                    rh.initPlayers();
                    break;

                case RANKED:
                case CUSTOM_BATTLE_LAB:
                    rh = new MainMapRoomHandler(this, room, mainMapBoundaries, mainMapObstacles);
                    roomHandlers.put(rName, rh);
                    rh.initPlayers();
                    break;
            }
            Console.debugLog("Starting script for room " + room.getName());
        }
    }

    public void stopScript(
            String roomId, boolean abort) { // Stops a task scheduler when room is deleted
        if (!roomHandlers.containsKey(roomId)) return;
        Console.debugLog("Stopping rooom: " + roomId);
        roomHandlers.get(roomId).stopScript(abort);
        roomHandlers.remove(roomId);
    }

    public boolean roomHandlerExists(String roomId) {
        return this.roomHandlers.containsKey(roomId);
    }

    public RoomHandler getRoomHandler(String roomId) {
        return roomHandlers.get(roomId);
    }

    private void loadBrushes()
            throws IOException { // Reads json files and turns them into JsonNodes
        File mainMap = new File(getCurrentFolder() + "/data/colliders/brush.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(mainMap);
        ArrayNode colliders = (ArrayNode) node.get("BrushAreas").get("brush");
        brushColliders = new ArrayList[colliders.size()];
        battleLabBrushPaths = new ArrayList<>(colliders.size());
        for (int i = 0;
                i < colliders.size();
                i++) { // Reads all colliders and makes a list of their vertices
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList<>(vertices.size());
            for (int g = 0; g < vertices.size(); g++) {
                if (g == 0) {
                    path.moveTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                } else { // Draws lines from each vertex to make a shape
                    path.lineTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            battleLabBrushPaths.add(path);
            brushColliders[i] = vecs;
        }

        File practiceMap = new File(getCurrentFolder() + "/data/colliders/practiceBrush.json");
        JsonNode node2 = mapper.readTree(practiceMap);
        ArrayNode colliders2 = (ArrayNode) node2.get("BrushAreas").get("brush");
        practiceBrushColliders = new ArrayList[colliders2.size()];
        candyStreetsBrushPaths = new ArrayList<>(colliders2.size());
        for (int i = 0;
                i < colliders2.size();
                i++) { // Reads all colliders and makes a list of their vertices
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders2.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList<>(vertices.size());
            for (int g = 0; g < vertices.size(); g++) {
                if (g == 0) {
                    path.moveTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                } else { // Draws lines from each vertex to make a shape
                    path.lineTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            candyStreetsBrushPaths.add(path);
            practiceBrushColliders[i] = vecs;
        }
    }

    public ArrayList<Path2D> getBrushPaths(GameMap gameMap) {
        if (gameMap == GameMap.BATTLE_LAB) return battleLabBrushPaths;
        else return candyStreetsBrushPaths;
    }

    public Path2D getBrush(int num, ArrayList<Path2D> brushPaths) {
        return brushPaths.get(num);
    }

    public int getBrushNum(Point2D loc, ArrayList<Path2D> brushPaths) {
        for (int i = 0; i < brushPaths.size(); i++) {
            Path2D p = brushPaths.get(i);
            if (p.contains(loc)) return i;
        }
        return -1;
    }

    @Override
    public void send(String cmdName, ISFSObject params, User recipient) {
        super.send(cmdName, params, recipient);
        /*
        if (this.commandStressLog.containsKey(cmdName)) {
            this.commandStressLog.get(cmdName).update(params);
        } else this.commandStressLog.put(cmdName, new StressLogger(cmdName));

         */
    }

    public boolean isBrushOccupied(RoomHandler room, UserActor a, ArrayList<Path2D> brushPaths) {
        try {
            int brushNum = this.getBrushNum(a.getLocation(), brushPaths);
            if (brushNum == -1) return false;
            Path2D brush = brushPaths.get(brushNum);
            for (UserActor ua : room.getPlayers()) {
                if (ua.getTeam() != a.getTeam() && brush.contains(ua.getLocation())) return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public MongoCollection<Document> getPlayerDatabase() {
        return this.playerDatabase;
    }

    public MongoCollection<Document> getChampionDatabase() {
        return this.championDatabase;
    }

    public MongoCollection<Document> getMatchHistoryDatabase() {
        return this.matchHistoryDatabase;
    }

    public int getElo(String tegID) {
        try {
            Document playerData = this.playerDatabase.find(eq("user.TEGid", tegID)).first();
            if (playerData != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode data = mapper.readTree(playerData.toJson());
                return data.get("player").get("elo").asInt();
            } else return -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}

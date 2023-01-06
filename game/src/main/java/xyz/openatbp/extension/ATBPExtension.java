package xyz.openatbp.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.extensions.SFSExtension;
import org.w3c.dom.Element;
import xyz.openatbp.extension.evthandlers.*;
import xyz.openatbp.extension.reqhandlers.*;

import java.awt.*;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ATBPExtension extends SFSExtension {
    HashMap<String, JsonNode> actorDefinitions = new HashMap<>();
    ArrayList<Vector<Float>>[] mapColliders;
    ArrayList<Vector<Float>>[] mainMapColliders;
    ArrayList<Path2D> mapPaths;
    ArrayList<Path2D> mainMapPaths;
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

    private void loadDefinitions() throws IOException {
        File path = new File(getCurrentFolder() + "/definitions");
        File[] files = path.listFiles();
        ObjectMapper mapper = new XmlMapper();
        for(File f : files){
            JsonNode node = mapper.readTree(f);
            actorDefinitions.put(f.getName().replace(".xml",""),node);
        }
    }

    private void loadColliders() throws IOException {
        File practiceMap = new File(getCurrentFolder()+"/colliders/practice.json");
        File mainMap = new File(getCurrentFolder()+"/colliders/main.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(practiceMap);
        ArrayNode colliders = (ArrayNode) node.get("SceneColliders").get("collider");
        mapColliders = new ArrayList[colliders.size()];
        mapPaths = new ArrayList(colliders.size());
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
            mapPaths.add(path);
            mapColliders[i] = vecs;
        }
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
}

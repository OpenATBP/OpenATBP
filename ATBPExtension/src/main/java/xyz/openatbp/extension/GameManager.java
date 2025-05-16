package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.RoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSRoomVariable;
import com.smartfoxserver.v2.entities.variables.SFSUserVariable;
import com.smartfoxserver.v2.entities.variables.UserVariable;
import com.smartfoxserver.v2.exceptions.SFSVariableException;

import xyz.openatbp.extension.game.actors.UserActor;

public class GameManager {

    // bh1 = Blue Health 1 ph1 = Purple Health 1. Numbers refer to top,bottom,and outside
    // respectively.
    public static final String[] L2_SPAWNS = {
        "bh1",
        "bh2",
        "bh3",
        "ph1",
        "ph2",
        "ph3",
        "keeoth",
        "goomonster",
        "hugwolf",
        "gnomes",
        "ironowls",
        "grassbear"
    };

    public static final String[] L1_SPAWNS = {
        "bh1", "ph1", "gnomes", "ironowls",
    };

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void addPlayer(
            Room room, ATBPExtension parentExt) { // Sends player info to client
        for (User user : room.getUserList()) {
            ISFSObject playerInfo = user.getVariable("player").getSFSObjectValue();
            int id = user.getId();
            String name = playerInfo.getUtfString("name");
            String champion = playerInfo.getUtfString("avatar");
            int team = playerInfo.getInt("team");
            String backpack = playerInfo.getUtfString("backpack");
            String tid = playerInfo.getUtfString("tegid");
            int elo = parentExt.getElo(tid);
            boolean tournamentEligible = playerInfo.getBool("isTournamentEligible");
            ExtensionCommands.addUser(
                    parentExt,
                    room,
                    id,
                    name,
                    champion,
                    team,
                    tid,
                    backpack,
                    elo,
                    tournamentEligible);
        }
    }

    public static void loadPlayers(
            Room room, ATBPExtension parentExt) { // Loads the map for everyone
        String groupID = room.getGroupId();
        for (User u : room.getUserList()) {
            ISFSObject data = new SFSObject();
            if (groupID.equals("Practice")
                    || groupID.equals("Tutorial")
                    || (room.getMaxUsers() <= 4
                            && !room.getName().contains("1p")
                            && !(room.getMaxUsers() == 1 && room.getName().contains("custom")))) {
                data.putUtfString("set", "AT_1L_Arena");
            } else {
                data.putUtfString("set", "AT_2L_Arena");
            }
            int maxUsers = room.getMaxUsers();
            int userSize = room.getUserList().size();
            data.putUtfString("soundtrack", "music_main1");
            data.putInt("roomId", room.getId());
            data.putUtfString("roomName", room.getName());
            data.putInt("capacity", maxUsers);
            data.putInt("botCount", maxUsers - userSize);
            parentExt.send("cmd_load_room", data, u);
        }
    }

    public static boolean playersLoaded(ArrayList<User> users, int gameSize) {
        int num = 0;
        for (User u : users) {
            if (u.getProperty("joined") != null && (boolean) u.getProperty("joined")) num++;
        }
        return num == gameSize;
    }

    public static boolean playersReady(Room room) { // Checks if all clients are ready
        int ready = 0;
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for (int i = 0; i < users.size(); i++) {
            // Console.debugLog(users.get(i).getSession());
            if (users.get(i).getSession().getProperty("ready") == null) return false;
            if ((boolean) users.get(i).getSession().getProperty("ready")) ready++;
        }
        return ready == users.size();
    }

    public static void sendAllUsers(
            ATBPExtension parentExt, ISFSObject data, String cmd, Room room) {
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        for (int i = 0; i < users.size(); i++) {
            parentExt.send(cmd, data, users.get(i));
        }
    }

    public static void initializeGame(Room room, ATBPExtension parentExt)
            throws SFSVariableException {
        room.setProperty("state", 2);
        int blueNum = 0;
        int purpleNum = 0;
        initializeMap(room, parentExt);
        for (User u : room.getUserList()) {
            ISFSObject playerInfo = u.getVariable("player").getSFSObjectValue();
            int team = playerInfo.getInt("team");
            float px = 0f;
            float pz = 0f;
            if (team == 0) {
                if (room.getGroupId().equals("Practice") || room.getGroupId().equals("Tutorial")) {
                    px = (float) MapData.L1_PURPLE_SPAWNS[purpleNum].getX();
                    pz = (float) MapData.L1_PURPLE_SPAWNS[purpleNum].getY();
                } else {
                    px = (float) MapData.L2_PURPLE_SPAWNS[purpleNum].getX();
                    pz = (float) MapData.L2_PURPLE_SPAWNS[purpleNum].getY();
                }
                purpleNum++;
            }
            if (team == 1) {
                if (room.getGroupId().equals("Practice") || room.getGroupId().equals("Tutorial")) {
                    px = (float) MapData.L1_PURPLE_SPAWNS[blueNum].getX() * -1;
                    pz = (float) MapData.L1_PURPLE_SPAWNS[blueNum].getY();
                } else {
                    px = (float) MapData.L2_PURPLE_SPAWNS[blueNum].getX() * -1;
                    pz = (float) MapData.L2_PURPLE_SPAWNS[blueNum].getY();
                }
                blueNum++;
            }
            String id = String.valueOf(u.getId());
            String actor = playerInfo.getUtfString("avatar");
            Point2D location = new Point2D.Float(px, pz);
            ExtensionCommands.createActor(parentExt, room, id, actor, location, 0f, team);

            ISFSObject updateData = new SFSObject();
            updateData.putUtfString("id", String.valueOf(u.getId()));
            int champMaxHealth =
                    parentExt
                            .getActorStats(playerInfo.getUtfString("avatar"))
                            .get("health")
                            .asInt();
            updateData.putInt("currentHealth", champMaxHealth);
            updateData.putInt("maxHealth", champMaxHealth);
            updateData.putDouble("pHealth", 1);
            updateData.putInt("xp", 0);
            updateData.putDouble("pLevel", 0);
            updateData.putInt("level", 1);
            updateData.putInt("availableSpellPoints", 1);
            updateData.putLong("timeSinceBasicAttack", 0);
            // SP_CATEGORY 1-5 TBD
            updateData.putInt("sp_category1", 0);
            updateData.putInt("sp_category2", 0);
            updateData.putInt("sp_category3", 0);
            updateData.putInt("sp_category4", 0);
            updateData.putInt("sp_category5", 0);
            updateData.putInt("deaths", 0);
            updateData.putInt("assists", 0);
            JsonNode actorStats = parentExt.getActorStats(playerInfo.getUtfString("avatar"));
            for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                updateData.putDouble(k, actorStats.get(k).asDouble());
            }
            UserVariable userStat = new SFSUserVariable("stats", updateData);
            u.setVariable(userStat);
            ExtensionCommands.updateActorData(parentExt, room, updateData);
        }
        try { // Sets all the room variables once the game is about to begin
            setRoomVariables(room);
        } catch (SFSVariableException e) { // TODO: Disconnect all players if this fails
            e.printStackTrace();
        }

        for (User u : room.getUserList()) {
            ISFSObject data = new SFSObject();
            parentExt.send("cmd_match_starting", data, u); // Starts the game for everyone
        }

        String sound;
        if (room.getGroupId().equals("Tutorial")) {
            sound = "announcer/tut_intro";
        } else {
            sound = "announcer/welcome";
        }
        ExtensionCommands.playSound(parentExt, room, "global", sound, new Point2D.Float(0, 0));
        ExtensionCommands.playSound(
                parentExt,
                room,
                "music",
                "",
                new Point2D.Float(0, 0)); // turn off char select music
        parentExt.startScripts(room); // Starts the background scripts for the game
    }

    private static void setRoomVariables(Room room) throws SFSVariableException {
        ISFSObject spawnTimers = new SFSObject();
        String[] spawns;

        switch (room.getGroupId()) {
            case "Tutorial":
            case "Practice":
                spawns = GameManager.L1_SPAWNS;
                break;

            default:
                spawns = GameManager.L2_SPAWNS;
                break;
        }

        for (String s : spawns) { // Adds in spawn timers for all mobs/health. AKA time dead
            spawnTimers.putInt(s, 0);
        }
        ISFSObject teamScore = new SFSObject();
        teamScore.putInt("blue", 0);
        teamScore.putInt("purple", 0);
        List<RoomVariable> variables = getRoomVariables(teamScore, spawnTimers);
        room.setVariables(variables);
    }

    private static List<RoomVariable> getRoomVariables(
            ISFSObject teamScore, ISFSObject spawnTimers) {
        ISFSObject mapData = new SFSObject();
        mapData.putBool("blueUnlocked", false);
        mapData.putBool("purpleUnlocked", false);
        RoomVariable scoreVar = new SFSRoomVariable("score", teamScore);
        List<RoomVariable> variables = new ArrayList<>();
        RoomVariable spawnVar = new SFSRoomVariable("spawns", spawnTimers);
        RoomVariable mapVar = new SFSRoomVariable("map", mapData);
        variables.add(scoreVar);
        variables.add(spawnVar);
        variables.add(mapVar);
        return variables;
    }

    private static void initializeMap(Room room, ATBPExtension parentExt) {
        String groupId = room.getGroupId();
        ExtensionCommands.createActor(parentExt, room, MapData.getBaseActorData(0, groupId));
        ExtensionCommands.createActor(parentExt, room, MapData.getBaseActorData(1, groupId));

        spawnTowers(room, parentExt);
        spawnAltars(room, parentExt);
        spawnHealth(room, parentExt);

        GameModeSpawns.spawnGuardianForMode(parentExt, room, 0);
        GameModeSpawns.spawnGuardianForMode(parentExt, room, 1);
    }

    private static void spawnTowers(Room room, ATBPExtension parentExt) {
        GameModeSpawns.spawnTowersForMode(room, parentExt);
    }

    private static void spawnAltars(Room room, ATBPExtension parentExt) {
        GameModeSpawns.spawnAltarsForMode(room, parentExt);
    }

    private static void spawnHealth(Room room, ATBPExtension parentExt) {
        GameModeSpawns.spawnHealthForMode(room, parentExt);
    }

    public static JsonNode getTeamData(
            ATBPExtension parentExt,
            HashMap<User, UserActor> dcPlayers,
            int team,
            Room room,
            boolean isRankedMatch,
            boolean tutorialCoins,
            int winningTeam) {
        final String[] STATS = {
            "damageDealtChamps",
            "damageReceivedPhysical",
            "damageReceivedSpell",
            "spree",
            "damageReceivedTotal",
            "damageDealtSpell",
            "score",
            "timeDead",
            "damageDealtTotal",
            "damageDealtPhysical"
        };
        /*
        Stats:
            damageDealtChamps
            damageReceivedPhysical
            damageReceivedSpell
            name?
            spree
            deaths
            damageReceivedTotal
            assists
            jungleMobs?
            kills
            minions?
            damageDealtSpell
            score
            timeDead
            healthPickUps?
            playerName?
            damageDealtTotal
            damageDealtPhysical

         */
        int coins = isRankedMatch ? 80 : tutorialCoins ? 700 : 0;
        if (team == winningTeam) coins += 20;
        ObjectNode node = objectMapper.createObjectNode();
        for (User u : room.getUserList()) {
            UserActor ua =
                    parentExt.getRoomHandler(room.getName()).getPlayer(String.valueOf(u.getId()));
            if (ua.getTeam() == team) {
                ObjectNode player = objectMapper.createObjectNode();
                ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                player.put("id", u.getId());
                for (String s : STATS) {
                    if (ua.hasGameStat(s)) player.put(s, ua.getGameStat(s));
                }
                player.put("name", playerVar.getUtfString("name"));
                player.put("kills", ua.getStat("kills"));
                player.put("deaths", ua.getStat("deaths"));
                player.put("assists", ua.getStat("assists"));
                player.put("playerName", ua.getFrame());
                player.put("myElo", (double) playerVar.getInt("elo"));
                player.put("coins", coins);
                player.put(
                        "prestigePoints", 10); // Just going to have this be a flat amount for now
                node.set(String.valueOf(u.getId()), player);
            }
        }
        if (!dcPlayers.isEmpty()) {
            for (Map.Entry<User, UserActor> entry : dcPlayers.entrySet()) {
                UserActor ua = entry.getValue();
                if (ua.getTeam() == team) {
                    ObjectNode player = objectMapper.createObjectNode();
                    User u = entry.getKey();
                    ISFSObject playerVar = u.getVariable("player").getSFSObjectValue();
                    for (String s : STATS) {
                        if (ua.hasGameStat(s)) player.put(s, ua.getGameStat(s));
                    }
                    player.put("name", playerVar.getUtfString("name"));
                    player.put("kills", ua.getStat("kills"));
                    player.put("deaths", ua.getStat("deaths"));
                    player.put("assists", ua.getStat("assists"));
                    player.put("playerName", ua.getFrame());
                    player.put("myElo", (double) playerVar.getInt("elo"));
                    player.put("coins", coins);
                    player.put(
                            "prestigePoints",
                            10); // Just going to have this be a flat amount for now
                    node.set(String.valueOf(u.getId()), player);
                }
            }
        }
        return node;
    }

    public static JsonNode getGlobalTeamData(
            ATBPExtension parentExt, HashMap<User, UserActor> dcPlayers, Room room) {
        double killsA = 0;
        double killsB = 0;
        double deathsA = 0;
        double deathsB = 0;
        double assistsA = 0;
        double assistsB = 0;
        double scoreA = 0;
        double scoreB = 0;

        for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
            if (ua.getTeam() == 0) {
                killsA += ua.getStat("kills");
                deathsA += ua.getStat("deaths");
                assistsA += ua.getStat("assists");
            } else {
                killsB += ua.getStat("kills");
                deathsB += ua.getStat("deaths");
                assistsB += ua.getStat("assists");
            }
        }
        if (!dcPlayers.isEmpty()) {
            for (Map.Entry<User, UserActor> entry : dcPlayers.entrySet()) {
                UserActor ua = entry.getValue();
                if (ua.getTeam() == 0) {
                    killsA += ua.getStat("kills");
                    deathsA += ua.getStat("deaths");
                    assistsA += ua.getStat("assists");
                } else {
                    killsB += ua.getStat("kills");
                    deathsB += ua.getStat("deaths");
                    assistsB += ua.getStat("assists");
                }
            }
        }
        ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
        int blueScore = scoreObject.getInt("blue");
        int purpleScore = scoreObject.getInt("purple");

        scoreA += purpleScore;
        scoreB += blueScore;

        ObjectNode node = objectMapper.createObjectNode();
        node.put("killsA", killsA);
        node.put("killsB", killsB);
        node.put("deathsA", deathsA);
        node.put("deathsB", deathsB);
        node.put("scoreA", scoreA);
        node.put("scoreB", scoreB);
        node.put("assistsA", assistsA);
        node.put("assistsB", assistsB);
        return node;
    }
}

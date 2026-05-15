package xyz.openatbp.extension;

import static xyz.openatbp.extension.TutorialRoomHandler.TUTORIAL_COINS;

import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.entities.variables.*;
import com.smartfoxserver.v2.exceptions.SFSVariableException;

import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.RoomGroup;
import xyz.openatbp.extension.game.actors.Actor;
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

    public static final String[] ARAM_SPAWNS = {
        "bh1", "ph1", "gnomes", "ironowls", "keeoth", "goomonster", "hugwolf", "grassbear"
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

    public static String getMapAssetBundleName(String groupID) {
        GameMap gameMap = getMap(getRoomGroupEnum(groupID));
        if (gameMap == GameMap.CANDY_STREETS) return "AT_1L_Arena";
        else return "AT_2L_Arena";
    }

    public static void loadPlayers(
            Room room, ATBPExtension parentExt) { // Loads the map for everyone
        String groupID = room.getGroupId();
        for (User u : room.getUserList()) {
            ISFSObject data = new SFSObject();

            String s1 = getMapAssetBundleName(groupID);
            data.putUtfString("set", s1);

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

    public static GameMap getMap(RoomGroup roomGroup) {
        switch (roomGroup) {
            case RANKED:
            case PVB:
            case CUSTOM_BATTLE_LAB:
                return GameMap.BATTLE_LAB;
            default:
                return GameMap.CANDY_STREETS;
        }
    }

    public static RoomGroup getRoomGroupEnum(String roomGroupID) {
        switch (roomGroupID) {
            case "CUSTOM_BATTLE_LAB":
                return RoomGroup.CUSTOM_BATTLE_LAB;

            case "CUSTOM_CANDY_STREETS":
                return RoomGroup.CUSTOM_CANDY_STREETS;

            case "PVB":
                return RoomGroup.PVB;

            case "PRACTICE":
                return RoomGroup.PRACTICE;

            case "TUTORIAL":
                return RoomGroup.TUTORIAL;

            default:
                return RoomGroup.RANKED;
        }
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

    private static void initializePlayer(
            ATBPExtension parentExt,
            User u,
            Room room,
            ISFSObject playerInfo,
            int spawnNum,
            boolean createActor)
            throws SFSVariableException {

        GameMap gameMap = getMap(getRoomGroupEnum(room.getGroupId()));

        boolean practiceMap = gameMap == GameMap.CANDY_STREETS;
        int team = playerInfo.getInt("team");

        float px = 0f;
        float pz = 0f;
        if (team == 0) {
            if (practiceMap) {
                px = (float) MapData.L1_PURPLE_SPAWNS[spawnNum].getX();
                pz = (float) MapData.L1_PURPLE_SPAWNS[spawnNum].getY();
            } else {
                px = (float) MapData.L2_PURPLE_SPAWNS[spawnNum].getX();
                pz = (float) MapData.L2_PURPLE_SPAWNS[spawnNum].getY();
            }
        }

        if (team == 1) {
            if (practiceMap) {
                px = (float) MapData.L1_PURPLE_SPAWNS[spawnNum].getX() * -1;
                pz = (float) MapData.L1_PURPLE_SPAWNS[spawnNum].getY();
            } else {
                px = (float) MapData.L2_PURPLE_SPAWNS[spawnNum].getX() * -1;
                pz = (float) MapData.L2_PURPLE_SPAWNS[spawnNum].getY();
            }
        }
        if (createActor) {
            String id = String.valueOf(u.getId());
            String actor = playerInfo.getUtfString("avatar");
            Point2D location = new Point2D.Float(px, pz);
            ExtensionCommands.createActor(parentExt, room, id, actor, location, 0f, team);
        }

        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", String.valueOf(u.getId()));
        int champMaxHealth =
                parentExt.getActorStats(playerInfo.getUtfString("avatar")).get("health").asInt();
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

        // UPDATE LOCATION
        ISFSObject playerLoc = u.getVariable("location").getSFSObjectValue();
        playerLoc.getSFSObject("p1").putFloat("x", px);
        playerLoc.getSFSObject("p1").putFloat("z", pz);
        UserVariable locationVar = new SFSUserVariable("location", playerLoc);
        u.setVariable(locationVar);

        JsonNode actorStats = parentExt.getActorStats(playerInfo.getUtfString("avatar"));
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            updateData.putDouble(k, actorStats.get(k).asDouble());
        }
        UserVariable userStat = new SFSUserVariable("stats", updateData);
        u.setVariable(userStat);
        ExtensionCommands.updateActorData(parentExt, room, updateData);
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

            if (team == 0) {
                initializePlayer(parentExt, u, room, playerInfo, purpleNum, true);
                purpleNum++;
            } else {
                initializePlayer(parentExt, u, room, playerInfo, blueNum, true);
                blueNum++;
            }
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

        ExtensionCommands.playSound(
                parentExt, room, "global", "announcer/welcome", new Point2D.Float(0, 0));
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
        // NEXUS SPAWNS
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

    public static int getGameOverCoins(int team, int winningTeam, RoomGroup roomGroup) {
        // SHOP CURRENCY
        switch (roomGroup) {
            case RANKED:
                return team == winningTeam ? 100 : 75;
            case PVB:
                return team == winningTeam ? 40 : 20;
            case PRACTICE:
                return team == winningTeam ? 25 : 10;
        }
        return 0;
    }

    public static int getGameOverPrestigePoints(int team, int winningTeam, RoomGroup roomGroup) {
        // ACCOUNT XP
        switch (roomGroup) {
            case RANKED:
                return team == winningTeam ? 40 : 20;
            case PVB:
                return team == winningTeam ? 20 : 10;
            case PRACTICE:
                return team == winningTeam ? 10 : 5;
        }
        return 0;
    }

    public static JsonNode getTeamData(
            HashMap<Integer, Actor> endGameChampions,
            int team,
            Room room,
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

        RoomGroup roomGroup = getRoomGroupEnum(room.getGroupId());
        int coins = getGameOverCoins(team, winningTeam, roomGroup);
        int prestigePoints = getGameOverPrestigePoints(team, winningTeam, roomGroup);
        if (tutorialCoins) coins = TUTORIAL_COINS;

        ObjectNode node = objectMapper.createObjectNode();

        for (Actor a : endGameChampions.values()) {

            if (a.getTeam() == team) {
                ObjectNode endGameChampion = objectMapper.createObjectNode();

                endGameChampion.put("id", Integer.parseInt(a.getId()));

                for (String s : STATS) {
                    if (a.hasGameStat(s)) endGameChampion.put(s, a.getGameStat(s));
                }

                int elo = 0; // bot elo
                if (a instanceof UserActor) {
                    elo = ((UserActor) a).getElo();
                }

                endGameChampion.put("name", a.getDisplayName());
                endGameChampion.put("kills", a.getStat("kills"));
                endGameChampion.put("deaths", a.getStat("deaths"));
                endGameChampion.put("assists", a.getStat("assists"));
                endGameChampion.put("playerName", a.getFrame());
                endGameChampion.put("myElo", elo);
                endGameChampion.put("coins", coins);
                endGameChampion.put("prestigePoints", prestigePoints);
                node.set(a.getId(), endGameChampion);
            }
        }
        return node;
    }

    public static JsonNode getGlobalTeamData(Room room, Map<Integer, Actor> endGameChampions) {
        double killsA = 0;
        double killsB = 0;
        double deathsA = 0;
        double deathsB = 0;
        double assistsA = 0;
        double assistsB = 0;
        double scoreA = 0;
        double scoreB = 0;

        for (Actor a : endGameChampions.values()) {
            if (a.getTeam() == 0) {
                killsA += a.getStat("kills");
                deathsA += a.getStat("deaths");
                assistsA += a.getStat("assists");
            } else {
                killsB += a.getStat("kills");
                deathsB += a.getStat("deaths");
                assistsB += a.getStat("assists");
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

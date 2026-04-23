package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.util.TaskScheduler;

import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.game.effects.ModifierIntent;
import xyz.openatbp.extension.game.effects.ModifierType;
import xyz.openatbp.extension.pathfinding.PathFinder;

public abstract class RoomHandler implements Runnable {
    protected static final int ALTAR_LOCK_TIME_SEC = 90;
    protected static final int NON_JG_BOSS_SPAWN_RATE = 45;
    protected static final int KEEOTH_SPAWN_RATE = 120;
    protected static final int GOO_SPAWN_RATE = 90;
    protected static final int MONSTER_DEBUG_SPAWN_RATE = 3;
    public static final double ALTAR_BUFF = 0.25;
    public static final double GOO_ALTAR_BUFF = 37.5;
    public static final int ALTAR_BUFF_DURATION = 60000;
    public static final int GOO_ALTAR_CAPTURE_EXP = 25;
    protected final String[] SPAWNS;
    protected final int HP_SPAWN_RATE_SEC;

    protected ATBPExtension parentExt;
    protected Room room;
    protected ArrayList<Minion> minions;
    protected ArrayList<UserActor> players;
    protected List<Monster> campMonsters;
    protected Base[] bases = new Base[2];
    protected GumballGuardian[] guardians = new GumballGuardian[2];
    protected ArrayList<BaseTower> baseTowers = new ArrayList<>();
    protected ArrayList<Tower> towers;
    protected boolean gameOver = false;
    protected boolean playMainMusic = false;
    protected boolean playTowerMusic = false;
    protected int mSecondsRan = 0;
    protected int secondsRan = 0;
    protected int[] altarStatus = {0, 0, 0};
    protected int[] purpleInstantCaptureCounter = {0, 0, 0};
    protected int[] blueInstantCaptureCounter = {0, 0, 0};
    protected HashMap<String, Integer> cooldowns = new HashMap<>();
    protected HashMap<String, Long> destroyedIds = new HashMap<>();
    protected List<String> createdActorIds = new ArrayList<>();
    protected static boolean monsterDebug = false;
    protected static boolean xpDebug = false;
    protected static final int FOUNTAIN_HEAL = 250; // every 0.5s
    protected int currentMinionWave = 0;
    protected List<Projectile> activeProjectiles = new ArrayList<>();
    protected ScheduledFuture<?> scriptHandler;
    protected int dcWeight = 0;
    protected float FOUNTAIN_RADIUS = 4f;
    protected List<Actor> companions = new ArrayList<>();
    protected HashMap<Integer, Actor> endGameChampions = new HashMap<>();
    protected boolean tutorialCoins = false;
    protected boolean ranked;
    protected final List<Bot> bots = new ArrayList<>();

    private static final AtomicLong ACTOR_ID_COUNTER = new AtomicLong(0);
    protected List<Actor> championsWithFirstDcBuff = new ArrayList<>();
    protected List<Actor> championsWithSecondDcBuff = new ArrayList<>();

    protected PathFinder pathFinder;

    private enum PointLeadTeam {
        PURPLE,
        BLUE
    }

    private PointLeadTeam pointLeadTeam;

    private boolean isAnnouncingKill = false;
    private static final int SINGLE_KILL_COOLDOWN = 3000;
    private long lastSingleKillAnnouncement = 0;

    public RoomHandler(
            ATBPExtension parentExt,
            Room room,
            String[] spawns,
            int HP_SPAWN_RATE,
            Point2D[] mapBoundary,
            List<Point2D[]> obstacles) {
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        this.towers = new ArrayList<>();
        this.players = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        this.SPAWNS = spawns;
        this.HP_SPAWN_RATE_SEC = HP_SPAWN_RATE;
        this.ranked = GameManager.getRoomGroupEnum(room.getGroupId()).equals(RoomGroup.RANKED);

        Properties props = parentExt.getConfigProperties();
        monsterDebug = Boolean.parseBoolean(props.getProperty("monsterDebug", "false"));
        xpDebug = Boolean.parseBoolean(props.getProperty("xpDebug", "false"));
        bases[0] = new Base(parentExt, room, 0);
        bases[1] = new Base(parentExt, room, 1);
        guardians[0] = new GumballGuardian(parentExt, room, 0);
        guardians[1] = new GumballGuardian(parentExt, room, 1);
        this.campMonsters = new ArrayList<>();

        pathFinder = new PathFinder(mapBoundary, obstacles);
    }

    public void initPlayers() {
        for (User u : room.getUserList()) {
            UserActor ua = Champion.getCharacterClass(u, parentExt);
            players.add(ua);
            endGameChampions.put(u.getId(), ua);
        }
    }

    public void startScheduler() {
        TaskScheduler scheduler = parentExt.getTaskScheduler();
        scriptHandler = scheduler.scheduleAtFixedRate(this, 100, 100, TimeUnit.MILLISECONDS);
    }

    public abstract Point2D getHealthLocation(int num);

    public abstract void handleMinionSpawns();

    public abstract void handleAltars();

    public abstract Point2D getAltarLocation(int altar);

    public abstract int getAltarStatus(Point2D location);

    public abstract void handleAltarGameScore(int capturingTeam, int altarIndex);

    public void gameOver(int winningTeam) {
        if (this.gameOver) return;
        try {
            this.gameOver = true;
            this.room.setProperty("state", 3);
            ExtensionCommands.gameOver(
                    parentExt, room, endGameChampions, winningTeam, tutorialCoins);
            updateDBCoinsAndAccountXp(winningTeam);

            RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());

            if (roomGroup.equals(RoomGroup.RANKED)) {
                logChampionData(winningTeam);
                logMatchHistory(winningTeam);
            }
            for (UserActor ua : players) {
                if (ua.getTeam() == winningTeam) {
                    if (!GameManager.getRoomGroupEnum(room.getGroupId())
                            .equals(RoomGroup.TUTORIAL)) {
                        ExtensionCommands.playSound(
                                parentExt, ua.getUser(), "global", "announcer/victory");
                    }
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "music", "music/music_victory");
                } else {
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "global", "announcer/defeat");
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "music", "music/music_defeat");
                }

                if (roomGroup.equals(RoomGroup.RANKED)) {
                    MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
                    String tegID = (String) ua.getUser().getSession().getProperty("tegid");
                    Document data = playerData.find(eq("user.TEGid", tegID)).first();
                    if (data != null) {
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
                        if (winningTeam == -1) win = 0.5d;
                        else if (ua.getTeam() == winningTeam) win = 1d;
                        else win = 0d;
                        int eloGain = win > 0 ? 1 : 0;
                        int currentElo = dataObj.get("player").get("elo").asInt();
                        int currentTier = ChampionData.getTier(currentElo);
                        if (ChampionData.getTier(currentElo + eloGain) < currentTier
                                && currentElo != ChampionData.ELO_TIERS[currentTier] + 1) {
                            eloGain =
                                    (int) ((ChampionData.ELO_TIERS[currentTier] + 1) - currentElo);
                        }
                        for (double tierElo : ChampionData.ELO_TIERS) {
                            if (currentElo + eloGain + 1 == (int) tierElo) {
                                eloGain++;
                                break;
                            }
                        }
                        if (currentElo + eloGain < 0) eloGain = currentElo * -1;
                        if (ua.getTeam() == winningTeam) wins++;

                        boolean updateSpree = false;
                        boolean updateMulti = false;
                        boolean updateHighestScore = false;

                        if (ua.hasGameStat("score")) {
                            score += ua.getGameStat("score");
                            int currentHighestScore =
                                    dataObj.get("player").get("scoreHighest").asInt();
                            if (score > currentHighestScore) {
                                updateHighestScore = true;
                            }
                        }
                        if (ua.hasGameStat("towers")) towers += ua.getGameStat("towers");
                        if (ua.hasGameStat("minions")) minions += ua.getGameStat("minions");
                        if (ua.hasGameStat("jungleMobs"))
                            jungleMobs += ua.getGameStat("jungleMobs");

                        if (ua.hasGameStat("spree")) {
                            int currentSpree = dataObj.get("player").get("largestSpree").asInt();
                            double gameSpree = ua.getGameStat("spree");
                            if (gameSpree > currentSpree) {
                                updateSpree = true;
                                largestSpree = (int) gameSpree;
                            }
                        }

                        if (ua.hasGameStat("largestMulti")) {
                            int currentMulti = dataObj.get("player").get("largestMulti").asInt();
                            double gameMulti = ua.getGameStat("largestMulti");
                            if (gameMulti > currentMulti) {
                                updateMulti = true;
                                largestMulti = (int) gameMulti;
                            }
                        }
                        List<Bson> updateList = new ArrayList<>();

                        updateList.add(Updates.inc("player.playsPVP", 1));
                        updateList.add(
                                Updates.set(
                                        "player.tier", ChampionData.getTier(currentElo + eloGain)));
                        updateList.add(Updates.inc("player.elo", eloGain));
                        updateList.add(Updates.inc("player.winsPVP", wins));
                        updateList.add(
                                Updates.inc(
                                        "player.points",
                                        points)); // Always zero I have no idea what this is
                        // for?;
                        updateList.add(Updates.inc("player.kills", kills));
                        updateList.add(Updates.inc("player.deaths", deaths));
                        updateList.add(Updates.inc("player.assists", assists));
                        updateList.add(Updates.inc("player.towers", towers));
                        updateList.add(Updates.inc("player.minions", minions));
                        updateList.add(Updates.inc("player.jungleMobs", jungleMobs));
                        updateList.add(Updates.inc("player.altars", altars));
                        updateList.add(Updates.inc("player.scoreTotal", score));

                        if (updateSpree) {
                            updateList.add(Updates.set("player.largestSpree", largestSpree));
                        }
                        if (updateMulti) {
                            updateList.add(Updates.set("player.largestMulti", largestMulti));
                        }
                        if (updateHighestScore) {
                            updateList.add(Updates.set("player.scoreHighest", score));
                        }

                        Bson updates = Updates.combine(updateList);
                        UpdateOptions options = new UpdateOptions().upsert(true);
                        Console.debugLog(playerData.updateOne(data, updates, options));
                    }
                }
            }
            parentExt.stopScript(room.getName(), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void logMatchHistory(int winningTeam) {
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
        Console.debugLog("Started to log...");
        MongoCollection<Document> collection = this.parentExt.getMatchHistoryDatabase();
        ObjectId objId = new ObjectId();
        Document newDoc = new Document().append("_id", objId).append("winner", winningTeam);
        ArrayList<Bson> updateList = new ArrayList<>();
        Document teamA = new Document();
        Document teamB = new Document();
        for (UserActor ua : this.players) {
            Document pDoc = new Document();
            double win;
            if (winningTeam == -1) win = 0.5d;
            else if (ua.getTeam() == winningTeam) win = 1d;
            else win = 0d;
            pDoc.append("kills", ua.getStat("kills"));
            pDoc.append("deaths", ua.getStat("deaths"));
            pDoc.append("assists", ua.getStat("assists"));
            pDoc.append("champion", ua.getAvatar().split("_")[0]);
            for (String s : STATS) {
                if (ua.hasGameStat(s)) pDoc.append(s, ua.getGameStat(s));
            }
            pDoc.append(
                    "elo", ua.getUser().getVariable("player").getSFSObjectValue().getInt("elo"));
            pDoc.append("eloGain", win > 0 ? 1 : 0);
            if (ua.getTeam() == 0) teamA.append(ua.getDisplayName(), pDoc);
            else teamB.append(ua.getDisplayName(), pDoc);
        }
        updateList.add(Updates.set("0", teamA));
        updateList.add(Updates.set("1", teamB));
        Bson updates = Updates.combine(updateList);
        Console.debugLog(collection.updateOne(newDoc, updates, new UpdateOptions().upsert(true)));
        for (UserActor ua : this.players) {
            String tegID = (String) ua.getUser().getSession().getProperty("tegid");
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            Document data = playerData.find(eq("user.TEGid", tegID)).first();
            if (data != null) {
                try {
                    List<Bson> updateList2 = new ArrayList<>();
                    updateList2.add(Updates.addToSet("history", objId));
                    Bson updates2 = Updates.combine(updateList2);
                    UpdateOptions options = new UpdateOptions().upsert(true);
                    Console.debugLog(playerData.updateOne(data, updates2, options));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected String generateActorId(String prefix) {
        return prefix + "_" + ACTOR_ID_COUNTER.incrementAndGet();
    }

    public void handlePlayerDC(User user) {
        if (getChampions().size() == 1) return;
        try {
            UserActor player = this.getPlayer(String.valueOf(user.getId()));
            player.destroy();
            players.removeIf(p -> p.getId().equalsIgnoreCase(String.valueOf(user.getId())));

            handleDcBuffAndGameOver();

            if (ranked) {
                MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
                Document data =
                        playerData
                                .find(
                                        eq(
                                                "user.TEGid",
                                                (String) user.getSession().getProperty("tegid")))
                                .first();

                if (data != null && this.secondsRan >= 5) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode dataObj = mapper.readTree(data.toJson());
                    double disconnects = dataObj.get("player").get("disconnects").asInt();
                    double playsPVP = dataObj.get("player").get("playsPVP").asInt();
                    double dcPercent = disconnects / playsPVP;
                    double elo = dataObj.get("player").get("elo").asInt();
                    double newElo = elo * (1 - dcPercent);

                    Bson updates =
                            Updates.combine(
                                    Updates.inc("player.disconnects", 1),
                                    Updates.set("player.elo", (int) newElo));
                    UpdateOptions options = new UpdateOptions().upsert(true);
                    Console.debugLog(playerData.updateOne(data, updates, options));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addCompanion(Actor a) {
        this.companions.add(a);
    }

    public void removeCompanion(Actor a) {
        this.companions.remove(a);
    }

    public void addProjectile(Projectile p) {
        this.activeProjectiles.add(p);
    }

    public abstract HashMap<Integer, Point2D> getFountainsCenter();

    public PathFinder getPathFinder() {
        return pathFinder;
    }

    public List<Actor> getActors() {
        List<Actor> actors = new ArrayList<>();
        actors.addAll(towers);
        actors.addAll(baseTowers);
        actors.addAll(minions);
        Collections.addAll(actors, bases);
        actors.addAll(players);
        actors.addAll(bots);
        actors.addAll(campMonsters);
        actors.addAll(companions);
        actors.removeIf(a -> a.getHealth() <= 0);
        return actors;
    }

    public List<Actor> getActorsInRadius(Point2D center, float radius) {
        List<Actor> actors = getActors();
        return actors.stream()
                .filter(a -> a.getLocation().distance(center) <= radius)
                .collect(Collectors.toList());
    }

    public List<Actor> getEligibleActors(
            int team,
            boolean teamFilter,
            boolean hpFilter,
            boolean towerFilter,
            boolean baseFilter) {
        List<Actor> actors = getActors();
        return actors.stream()
                .filter(a -> !hpFilter || a.getHealth() > 0)
                .filter(a -> !teamFilter || a.getTeam() != team)
                .filter(a -> !towerFilter || a.getActorType() != ActorType.TOWER)
                .filter(a -> !baseFilter || a.getActorType() != ActorType.BASE)
                .collect(Collectors.toList());
    }

    public Map<Integer, Actor> getEndGameChampions() {
        return endGameChampions;
    }

    public ScheduledFuture<?> getScriptHandler() {
        return this.scriptHandler;
    }

    protected void updateDBCoinsAndAccountXp(int winningTeam) throws IOException {
        for (UserActor ua : players) {
            RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());
            int coinsGained = GameManager.getGameOverCoins(ua.getTeam(), winningTeam, roomGroup);
            int prestigePointsGained =
                    GameManager.getGameOverPrestigePoints(ua.getTeam(), winningTeam, roomGroup);

            if (coinsGained == 0 || prestigePointsGained == 0) return;

            MongoCollection<Document> playerData = parentExt.getPlayerDatabase();
            String tegID = (String) ua.getUser().getSession().getProperty("tegid");
            Document data = playerData.find(eq("user.TEGid", tegID)).first();

            if (data != null) {
                Document playerObject = data.get("player", Document.class);
                if (playerObject != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode dataNode = mapper.readTree(data.toJson());

                    int rankIncrement = 0;

                    int currentRankProgress = dataNode.get("player").get("rankProgress").asInt();

                    if (currentRankProgress + prestigePointsGained >= 100) {
                        rankIncrement = 1;
                        currentRankProgress = 0;
                    } else {
                        currentRankProgress += prestigePointsGained;
                    }

                    List<Bson> updateList = new ArrayList<>();
                    updateList.add(Updates.inc("player.coins", coinsGained));
                    updateList.add(Updates.inc("player.rank", rankIncrement));
                    updateList.add(Updates.set("player.rankProgress", currentRankProgress));
                    Bson updates = Updates.combine(updateList);
                    UpdateOptions options = new UpdateOptions().upsert(true);
                    Console.debugLog(playerData.updateOne(data, updates, options));
                }
            }
        }
    }

    protected void logChampionData(int winningTeam) {

        for (UserActor ua : this.players) {
            String champion = ua.getChampionName(ua.getAvatar());
            MongoDatabase db = parentExt.database;
            MongoIterable<String> collections = db.listCollectionNames();
            ArrayList<String> collectionStrings = new ArrayList<>();
            for (String collection : collections) {
                collectionStrings.add(collection);
            }
            if (!collectionStrings.contains("champions")) {
                createChampionsCollectionsIfNotPresent(db);
            }
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            MongoCollection<Document> champData = this.parentExt.getChampionDatabase();
            Document data = champData.find(eq("champion", champion)).first();
            if (data != null) {
                List<Bson> updateList = new ArrayList<>();
                updateList.add(Updates.inc("playsPVP", 1));
                updateList.add(Updates.inc("winsPVP", ua.getTeam() == winningTeam ? 1 : 0));
                updateList.add(Updates.inc("kills", (int) ua.getStat("kills")));
                updateList.add(Updates.inc("deaths", (int) ua.getStat("deaths")));
                updateList.add(Updates.inc("assists", (int) ua.getStat("assists")));
                if (ua.hasGameStat("damageDealtChamps"))
                    updateList.add(
                            Updates.inc("damage", (int) ua.getGameStat("damageDealtChamps")));
                Bson updates = Updates.combine(updateList);
                UpdateOptions options = new UpdateOptions().upsert(true);
                Console.debugLog(champData.updateOne(data, updates, options));
                String tegID = (String) ua.getUser().getSession().getProperty("tegid");
                Document pData = playerData.find(eq("user.TEGid", tegID)).first();
                if (pData != null) {
                    List<Bson> updateList2 = new ArrayList<>();
                    updateList2.add(Updates.inc("champion." + champion + ".playsPVP", 1));
                    updateList2.add(
                            Updates.inc(
                                    "champion." + champion + ".winsPVP",
                                    ua.getTeam() == winningTeam ? 1 : 0));
                    updateList2.add(
                            Updates.inc(
                                    "champion." + champion + ".kills", (int) ua.getStat("kills")));
                    updateList2.add(
                            Updates.inc(
                                    "champion." + champion + ".deaths",
                                    (int) ua.getStat("deaths")));
                    updateList2.add(
                            Updates.inc(
                                    "champion." + champion + ".assists",
                                    (int) ua.getStat("assists")));
                    if (ua.hasGameStat("damageDealtChamps"))
                        updateList2.add(
                                Updates.inc(
                                        "champion." + champion + ".damage",
                                        (int) ua.getGameStat("damageDealtChamps")));
                    Bson updates2 = Updates.combine(updateList2);
                    UpdateOptions options2 = new UpdateOptions().upsert(true);
                    Console.debugLog(playerData.updateOne(pData, updates2, options2));
                }
            }
        }
    }

    protected void handleDcBuffAndGameOver() {
        List<Actor> purpleTeam = new ArrayList<>(getChampions());
        List<Actor> blueTeam = new ArrayList<>(getChampions());

        purpleTeam.removeIf(c -> c.getTeam() != 0);
        blueTeam.removeIf(c -> c.getTeam() != 1);

        int purpleTeamSize = purpleTeam.size();
        int blueTeamSize = blueTeam.size();

        int winningTeam = -1;
        if (purpleTeamSize == 0) winningTeam = 1;
        else if (blueTeamSize == 0) winningTeam = 0;

        if (winningTeam != -1) {
            gameOver(winningTeam);
            return;
        }

        this.dcWeight = purpleTeamSize - blueTeamSize;

        int teamDif = Math.abs(purpleTeamSize - blueTeamSize);
        List<Actor> smallerTeam = purpleTeamSize <= blueTeamSize ? purpleTeam : blueTeam;

        // --- Tier 1 buff (diff >= 1) ---
        if (teamDif >= 1) {
            for (Actor c : smallerTeam) {
                if (!championsWithFirstDcBuff.contains(c)) {
                    c.applyDCBuff(1);
                    championsWithFirstDcBuff.add(c);
                }
            }
            championsWithFirstDcBuff.removeIf(
                    c -> {
                        if (!smallerTeam.contains(c)) {
                            c.removeDCBuff(1);
                            return true;
                        }
                        return false;
                    });
        } else {
            for (Actor c : championsWithFirstDcBuff) {
                c.removeDCBuff(1);
            }
            championsWithFirstDcBuff.clear();
        }

        // --- Tier 2 buff (diff >= 2, stacks on top of tier 1) ---
        if (teamDif >= 2) {
            for (Actor c : smallerTeam) {
                if (!championsWithSecondDcBuff.contains(c)) {
                    c.applyDCBuff(2);
                    championsWithSecondDcBuff.add(c);
                }
            }
            championsWithSecondDcBuff.removeIf(
                    c -> {
                        if (!smallerTeam.contains(c)) {
                            c.removeDCBuff(2);
                            return true;
                        }
                        return false;
                    });
        } else {
            for (Actor c : championsWithSecondDcBuff) {
                c.removeDCBuff(2);
            }
            championsWithSecondDcBuff.clear();
        }
    }

    protected void handleSpawns() {
        ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
        for (String s : SPAWNS) {
            if (s.length() > 3) {
                int spawnRate = getMonsterSpawnRate(s);

                if (spawns.getInt(s) == spawnRate - 1) {
                    // Mob timers will be set to 0 when killed or health when taken
                    spawnMonster(s);
                    spawns.putInt(s, spawns.getInt(s) + 1);
                } else {
                    spawns.putInt(s, spawns.getInt(s) + 1);
                }
            } else {
                handleHealthPackSpawns(spawns, s);
            }
        }
    }

    private void removeStaleCampMonster(String monsterType) {
        campMonsters.removeIf(m -> m.getId().startsWith(monsterType) && m.isDead());
    }

    public void spawnMonster(String monster) {
        float x = 0;
        float z = 0;
        String avatar = monster;

        if (monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("ironowls")) {
            char[] abc = {'a', 'b', 'c'};
            for (int i = 0; i < 3; i++) {
                if (monster.equalsIgnoreCase("gnomes")) {
                    avatar = "gnome_" + abc[i];
                    x = (float) MapData.L2_GNOMES[i].getX();
                    z = (float) MapData.L2_GNOMES[i].getY();

                } else {
                    avatar = "ironowl_" + abc[i];
                    x = (float) MapData.L2_OWLS[i].getX();
                    z = (float) MapData.L2_OWLS[i].getY();
                }

                final String mobId = generateActorId(avatar);
                final String finalAvatar = avatar;

                // remove first for safety
                campMonsters.removeIf(m -> m.getId().equalsIgnoreCase(mobId));

                Point2D spawnLoc = new Point2D.Float(x, z);
                ExtensionCommands.createActor(parentExt, room, mobId, avatar, spawnLoc, 0f, 2);
                campMonsters.add(new Monster(parentExt, room, mobId, spawnLoc, finalAvatar));
            }
        } else if (monster.length() > 3) {
            String mobId = monster;

            switch (monster) {
                case "hugwolf":
                    {
                        removeStaleCampMonster("hugwolf");
                        mobId = generateActorId(avatar);
                        x = MapData.HUGWOLF[0];
                        z = MapData.HUGWOLF[1];
                        Point2D spawnLoc = new Point2D.Float(x, z);
                        campMonsters.add(
                                new Monster(parentExt, room, mobId, MapData.HUGWOLF, avatar));
                        ExtensionCommands.createActor(
                                parentExt, room, mobId, avatar, spawnLoc, 0f, 2);
                        break;
                    }
                case "grassbear":
                    {
                        removeStaleCampMonster("grassbear");
                        mobId = generateActorId(avatar);
                        x = MapData.GRASSBEAR[0];
                        z = MapData.GRASSBEAR[1];
                        Point2D spawnLoc = new Point2D.Float(x, z);
                        campMonsters.add(
                                new Monster(parentExt, room, mobId, MapData.GRASSBEAR, avatar));
                        ExtensionCommands.createActor(
                                parentExt, room, mobId, avatar, spawnLoc, 0f, 2);
                        break;
                    }
                case "keeoth":
                    {
                        removeStaleCampMonster("keeoth");
                        mobId = generateActorId(avatar);
                        x = MapData.L2_KEEOTH[0];
                        z = MapData.L2_KEEOTH[1];
                        Point2D spawnLoc = new Point2D.Float(x, z);
                        campMonsters.add(
                                new Keeoth(parentExt, room, mobId, MapData.L2_KEEOTH, avatar));
                        ExtensionCommands.createActor(
                                parentExt, room, mobId, avatar, spawnLoc, 0f, 2);
                        break;
                    }
                case "goomonster":
                    {
                        removeStaleCampMonster("goomonster");
                        avatar = "goomonster"; // keep this before generateActorId so ID reflects
                        // correct name
                        mobId = generateActorId(avatar);
                        x = MapData.L2_GOOMONSTER[0];
                        z = MapData.L2_GOOMONSTER[1];
                        Point2D spawnLoc = new Point2D.Float(x, z);
                        campMonsters.add(
                                new GooMonster(
                                        parentExt, room, mobId, MapData.L2_GOOMONSTER, avatar));
                        ExtensionCommands.createActor(
                                parentExt, room, mobId, avatar, spawnLoc, 0f, 2);
                        break;
                    }
            }
        }
    }

    public void handleSpawnDeath(Actor a) {
        String monster = a.getId().split("_")[0];
        RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());
        GameMap map = GameManager.getMap(roomGroup);

        String[] spawns = map == GameMap.BATTLE_LAB ? GameManager.L1_SPAWNS : GameManager.L2_SPAWNS;

        for (String s : spawns) {
            if (!s.contains(monster)) continue;

            if ((!s.contains("gnomes") && !s.contains("owls")) || tripletCampCleared(monster)) {
                room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                return;
            }
        }
    }

    public boolean tripletCampCleared(String monsterName) {
        List<Monster> triplet =
                getCampMonsters().stream()
                        .filter(m -> m.getId().contains(monsterName))
                        .collect(Collectors.toList());
        triplet.removeIf(Actor::isDead);
        return triplet.isEmpty();
    }

    protected void handleHealthPackSpawns(ISFSObject spawns, String s) {
        int time = spawns.getInt(s);

        if (time == HP_SPAWN_RATE_SEC - 1) {
            spawnHealth(s);
            spawns.putInt(s, time + 1);
        } else if (time != HP_SPAWN_RATE_SEC + 1) {
            time += 1;
            spawns.putInt(s, time);
        }
    }

    private int getMonsterSpawnRate(String s) {
        int spawnRate;

        switch (s) {
            case "keeoth":
                spawnRate = KEEOTH_SPAWN_RATE;
                break;

            case "goomonster":
                spawnRate = GOO_SPAWN_RATE;
                break;

            default:
                spawnRate = NON_JG_BOSS_SPAWN_RATE;
                break;
        }

        if (monsterDebug) spawnRate = MONSTER_DEBUG_SPAWN_RATE;
        return spawnRate;
    }

    protected void handleHealth() {
        for (String s : SPAWNS) {
            if (s.length() == 3) {
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                if (spawns.getInt(s) == HP_SPAWN_RATE_SEC + 1) {
                    List<Actor> actorsToCheck = new ArrayList<>(players);
                    actorsToCheck.addAll(bots);

                    for (Actor a : actorsToCheck) {
                        Point2D currentPoint = a.getLocation();
                        if (insideHealth(currentPoint, getHealthNum(s)) && a.getHealth() > 0) {
                            int team = a.getTeam();
                            Point2D healthLoc = getHealthLocation(getHealthNum(s));
                            ExtensionCommands.removeFx(parentExt, room, s + "_fx");
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    a.getId(),
                                    "picked_up_health_cyclops",
                                    2000,
                                    s + "_fx2",
                                    true,
                                    "",
                                    false,
                                    false,
                                    team);
                            ExtensionCommands.playSound(
                                    parentExt, a.getRoom(), "", "sfx_health_picked_up", healthLoc);
                            a.handleCyclopsHealing();
                            spawns.putInt(s, 0);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void createChampionsCollectionsIfNotPresent(MongoDatabase db) {
        String[] avatars = {
            "billy",
            "bmo",
            "cinnamonbun",
            "finn",
            "fionna",
            "flameprincess",
            "gunter",
            "hunson",
            "iceking",
            "jake",
            "lemongrab",
            "lich",
            "lsp",
            "magicman",
            "marceline",
            "neptr",
            "peppermintbutler",
            "princessbubblegum",
            "rattleballs"
        };
        db.createCollection("champions");
        MongoCollection<Document> champions = db.getCollection("champions");
        for (String avatar : avatars) {
            Document champDocument =
                    new Document("champion", avatar)
                            .append("playsPVP", 0)
                            .append("winsPVP", 0)
                            .append("kills", 0)
                            .append("deaths", 0)
                            .append("assists", 0)
                            .append("damage", 0);
            champions.insertOne(champDocument);
        }
    }

    public int getDcWeight() {
        return this.dcWeight;
    }

    public void run() {
        if (this.gameOver) return;
        if (!this.parentExt.roomHandlerExists(this.room.getName())
                && !this.scriptHandler.isCancelled()) {
            this.scriptHandler.cancel(false);
            return;
        }
        if (this.scriptHandler.isCancelled()) return;

        mSecondsRan += 100;

        List<String> keysToRemove = new ArrayList<>(this.destroyedIds.size());
        Set<String> keys = this.destroyedIds.keySet();
        for (String k : keys) {
            if (System.currentTimeMillis() - this.destroyedIds.get(k) >= 1000) keysToRemove.add(k);
        }
        for (String k : keysToRemove) {
            this.destroyedIds.remove(k);
        }

        if (mSecondsRan % 1000 == 0) { // Handle every second
            try {
                if (secondsRan % 60 == 0) {
                    this.printActors();
                }

                secondsRan++;

                if (secondsRan % 5 == 0) {
                    this.handlePassiveXP();
                    if (!isAnnouncingKill) {
                        announcePointLead();
                    }
                }
                if (secondsRan == 1
                        || this.playMainMusic && secondsRan < (60 * 13 + 1) && !this.gameOver) {
                    playMainMusic(parentExt, room);
                    this.playMainMusic = false;
                }
                if (playTowerMusic) {
                    playTowerMusic();
                    this.playTowerMusic = false;
                }
                if (secondsRan == (60 * 7) + 31) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/time_half",
                            new Point2D.Float(0f, 0f));
                } else if (secondsRan == (60 * 13 + 1)) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/time_low",
                            new Point2D.Float(0f, 0f));
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "music",
                            "music/music_time_low",
                            new Point2D.Float(0f, 0f));
                }
                if (secondsRan == 15 * 60 + 1) {
                    ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
                    int blueScore = scoreObject.getInt("blue");
                    int purpleScore = scoreObject.getInt("purple");
                    int winningTeam;
                    if (blueScore != purpleScore) {
                        winningTeam = purpleScore > blueScore ? 0 : 1;
                    } else {
                        winningTeam = getWinnerWhenTie();
                    }
                    this.gameOver(winningTeam);
                    return;
                }
                if (room.getUserList().isEmpty()) {
                    parentExt.stopScript(room.getName(), true);
                } else {
                    handleAltars();
                    int timeToSendToClient = mSecondsRan - 1000;

                    ExtensionCommands.updateTime(parentExt, room, timeToSendToClient);
                }

                handleSpawns();
                handleMinionSpawns();
                handleCooldowns();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        handleHealth();

        if (mSecondsRan % 500 == 0) {
            announceKills();
            handleFountain();
        }

        try {
            List<UserActor> playersCopy = new ArrayList<>(players);
            for (UserActor u : playersCopy) {
                u.update(mSecondsRan);
            }
        } catch (Exception e) {
            Console.logWarning("USER ACTOR UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (Bot b : bots) {
                if (b != null) {
                    b.update(mSecondsRan);
                }
            }
        } catch (Exception e) {
            Console.logWarning("BOT UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            List<Actor> companionsCopy = new ArrayList<>(companions);
            for (Actor a : companionsCopy) {
                if (a != null) {
                    a.update(mSecondsRan);
                }
            }
        } catch (Exception e) {
            Console.logWarning("COMPANION UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            List<Projectile> projectilesCopy = new ArrayList<>(this.activeProjectiles);
            for (Projectile p : projectilesCopy) { // Handles skill shots
                p.update(this);
            }
            activeProjectiles.removeIf(Projectile::isDestroyed);
        } catch (Exception e) {
            Console.logWarning("PROJECTILE UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (Minion m : minions) {
                m.update(mSecondsRan);
            }
            minions.removeIf(m -> (m.getHealth() <= 0));
        } catch (Exception e) {
            Console.logWarning("MINION UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (Monster m : campMonsters) {
                m.update(mSecondsRan);
            }
            campMonsters.removeIf(m -> (m.getHealth() <= 0));
        } catch (Exception e) {
            Console.logWarning("MONSTER UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (Tower t : towers) {
                t.update(mSecondsRan);
                if (t.getHealth() <= 0) {
                    if (mSecondsRan < 1000 * 60 * 13) this.playTowerMusic = true;
                    for (BaseTower b : baseTowers) {
                        if (b.getTeam() == t.getTeam() && !b.isUnlocked()) {
                            b.unlockBaseTower();
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Console.logWarning("TOWER UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (BaseTower b : baseTowers) {
                b.update(mSecondsRan);
                if (b.getHealth() <= 0) {
                    if (mSecondsRan < 1000 * 60 * 13) this.playTowerMusic = true;
                    bases[b.getTeam()].unlock();
                }
            }
        } catch (Exception e) {
            Console.logWarning("BASE TOWER UPDATE EXCEPTION");
            e.printStackTrace();
        }
        towers.removeIf(t -> (t.getHealth() <= 0));
        baseTowers.removeIf(b -> (b.getHealth() <= 0));

        try {
            for (GumballGuardian g : this.guardians) {
                g.update(mSecondsRan);
            }
        } catch (Exception e) {
            Console.logWarning("GUARDIAN UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            bases[0].update(mSecondsRan);
            bases[1].update(mSecondsRan);
        } catch (Exception e) {
            Console.logWarning("BASE UPDATE EXCEPTION");
            e.printStackTrace();
        }
    }

    public ATBPExtension getParentExt() {
        return this.parentExt;
    }

    public void stopScript(boolean abort) {
        if (abort) ExtensionCommands.abortGame(parentExt, this.room);
        this.scriptHandler.cancel(false);
    }

    protected void handleAltarsForMode(int numAltars) {
        for (int i = 0; i < numAltars; i++) {
            int currentStatus = this.altarStatus[i];

            if (currentStatus != 10) {
                int currentStage = Math.abs(this.altarStatus[i]);
                int deficit;
                int MAX_STAGE = 5;
                Point2D altarLocation = getAltarLocation(i);
                int FX_DURATION = 1000 * 60 * 15;

                String action = determineAltarAction(i);

                if (!action.isEmpty()) {
                    switch (action) {
                        case "purpleFastDecrease":
                        case "blueFastDecrease":
                            resetAltar(i);
                            break;

                        case "purpleDecrease":
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, "altar_" + i + currentStage);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    "",
                                    "sfx_altar_" + currentStage,
                                    altarLocation);
                            this.altarStatus[i]--;
                            if (this.purpleInstantCaptureCounter[i] > 0)
                                this.purpleInstantCaptureCounter[i] = 0;
                            break;

                        case "blueDecrease":
                            ExtensionCommands.removeFx(
                                    this.parentExt, this.room, "altar_" + i + currentStage);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    "",
                                    "sfx_altar_" + currentStage,
                                    altarLocation);
                            this.altarStatus[i]++;
                            if (this.blueInstantCaptureCounter[i] > 0)
                                this.blueInstantCaptureCounter[i] = 0;
                            break;

                        case "purpleFasterIncrease":
                            this.altarStatus[i] += 2;
                            createAltarFX(i, currentStage, 2, FX_DURATION, altarLocation, 0);
                            break;

                        case "blueFasterIncrease":
                            this.altarStatus[i] -= 2;
                            createAltarFX(i, currentStage, 2, FX_DURATION, altarLocation, 1);
                            break;

                        case "purpleCounterIncrease":
                            this.purpleInstantCaptureCounter[i]++;
                            break;

                        case "blueCounterIncrease":
                            this.blueInstantCaptureCounter[i]++;
                            break;

                        case "purpleInstantCapture":
                            deficit = MAX_STAGE - currentStage;
                            this.altarStatus[i] += deficit;
                            ExtensionCommands.playSound(
                                    this.parentExt, this.room, "", "sfx_altar_5", altarLocation);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    "altar_" + i,
                                    "fx_altar_5",
                                    FX_DURATION,
                                    "altar_" + i + 5,
                                    false,
                                    "Bip001",
                                    false,
                                    true,
                                    0);
                            break;

                        case "blueInstantCapture":
                            deficit = (MAX_STAGE - currentStage) * -1;
                            this.altarStatus[i] += deficit;
                            ExtensionCommands.playSound(
                                    this.parentExt, this.room, "", "sfx_altar_5", altarLocation);
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    "altar_" + i,
                                    "fx_altar_5",
                                    FX_DURATION,
                                    "altar_" + i + 5,
                                    false,
                                    "Bip001",
                                    false,
                                    true,
                                    1);
                            break;

                        case "purpleIncrease":
                            this.altarStatus[i]++;
                            createAltarFX(i, currentStage, 1, FX_DURATION, altarLocation, 0);
                            break;

                        case "blueIncrease":
                            this.altarStatus[i]--;
                            createAltarFX(i, currentStage, 1, FX_DURATION, altarLocation, 1);
                            break;
                    }
                    if (Math.abs(this.altarStatus[i]) >= 5) {
                        int capturingTeam = this.altarStatus[i] > 0 ? 0 : 1;
                        this.altarStatus[i] = 10; // Locks altar
                        final int capturedAltarIndex = i;
                        handleAltarGameScore(capturingTeam, capturedAltarIndex);
                        Runnable captureAltar =
                                () ->
                                        captureAltar(
                                                capturedAltarIndex,
                                                capturingTeam,
                                                "altar_" + capturedAltarIndex);
                        this.parentExt
                                .getTaskScheduler()
                                .schedule(captureAltar, 400, TimeUnit.MILLISECONDS);
                        if (this.purpleInstantCaptureCounter[i] > 0)
                            this.purpleInstantCaptureCounter[i] = 0;
                        if (this.blueInstantCaptureCounter[i] > 0)
                            this.blueInstantCaptureCounter[i] = 0;
                    }
                }
            }
        }
    }

    private String determineAltarAction(int altarIndex) {
        // hierarchy: fast status decrease, status decrease, faster increase, instant counter
        // increase, instant capture, status
        // increase
        Point2D altarLocation = getAltarLocation(altarIndex);
        List<Actor> actorsInRadius = Champion.getActorsInRadius(this, altarLocation, 2);

        List<Actor> eligibleActors =
                actorsInRadius.stream()
                        .filter(a -> a instanceof UserActor || a instanceof Bot)
                        .collect(Collectors.toList());

        List<Actor> bluePlayers = new ArrayList<>();
        List<Actor> purplePlayers = new ArrayList<>();

        for (Actor eA : eligibleActors) {
            if (eA.getTeam() == 0 && eA.getHealth() > 0 && !eA.isDead()) {
                purplePlayers.add(eA);
            } else if (eA.getTeam() == 1 && eA.getHealth() > 0 && !eA.isDead()) {
                bluePlayers.add(eA);
            }
        }

        int purpleCount = purplePlayers.size();
        int blueCount = bluePlayers.size();
        int status = this.altarStatus[altarIndex];
        int purpleCounter = this.purpleInstantCaptureCounter[altarIndex];
        int blueCounter = this.blueInstantCaptureCounter[altarIndex];

        if (purpleCount - blueCount > 1 && status < 0) {
            return "purpleFastDecrease";
        } else if (blueCount - purpleCount > 1 && status > 0) {
            return "blueFastDecrease";
        }

        if (purpleCount - blueCount == 1 && status < 0) {
            return "blueDecrease";
        } else if (blueCount - purpleCount == 1 && status > 0) {
            return "purpleDecrease";
        }

        if (purpleCount == 0 && status > 0) {
            return "purpleDecrease";
        } else if (blueCount == 0 && status < 0) {
            return "blueDecrease";
        }

        if (purpleCount - blueCount > 1 && status > 0) {
            return "purpleFasterIncrease";
        } else if (blueCount - purpleCount > 1 && status < 0) {
            return "blueFasterIncrease";
        }

        if (purpleCount - blueCount > 1 && status == 0) {
            return purpleCounter < 1 ? "purpleCounterIncrease" : "purpleInstantCapture";
        } else if (blueCount - purpleCount > 1 && status == 0) {
            return blueCounter < 1 ? "blueCounterIncrease" : "blueInstantCapture";
        }

        if (purpleCount - blueCount == 1) {
            return "purpleIncrease";
        } else if (blueCount - purpleCount == 1) {
            return "blueIncrease";
        }

        return "";
    }

    private void createAltarFX(
            int altarIndex,
            int currentStage,
            int increment,
            int fxDuration,
            Point2D altarLocation,
            int team) {
        int cappedFxStage = Math.min(currentStage + increment, 5);
        // if current stage + increment is bigger than 5, default to 5 to create proper FX
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                "altar_" + altarIndex,
                "fx_altar_" + cappedFxStage,
                fxDuration,
                "altar_" + altarIndex + cappedFxStage,
                false,
                "Bip001",
                false,
                true,
                team);
        ExtensionCommands.playSound(
                this.parentExt, this.room, "", "sfx_altar_" + cappedFxStage, altarLocation);
    }

    private void resetAltar(int altarIndex) {
        int currentStage = Math.abs(this.altarStatus[altarIndex]);
        for (int i = 1; i <= currentStage; i++) {
            ExtensionCommands.removeFx(this.parentExt, this.room, ("altar_" + altarIndex) + i);
        }
        this.altarStatus[altarIndex] = 0;
        if (this.purpleInstantCaptureCounter[altarIndex] > 0)
            this.purpleInstantCaptureCounter[altarIndex] = 0;
        if (this.blueInstantCaptureCounter[altarIndex] > 0)
            this.blueInstantCaptureCounter[altarIndex] = 0;
    }

    public void captureAltar(int i, int team, String altarId) {
        ExtensionCommands.playSound(parentExt, room, "", "sfx_altar_locked", getAltarLocation(i));
        for (int n = 1; n < 6; n++) {
            ExtensionCommands.removeFx(this.parentExt, this.room, altarId + n);
        }
        List<UserActor> gooUsers = new ArrayList<>();
        for (UserActor ua : this.players) {
            if (ua.getTeam() == team
                    && ChampionData.getJunkLevel(ua, "junk_5_bubblegums_googoomama") > 0)
                gooUsers.add(ua);
        }
        if (i == 1) addScore(null, team, 15 + (gooUsers.size() * 5));
        else addScore(null, team, 10 + (gooUsers.size() * 5));
        cooldowns.put(i + "__" + "altar", ALTAR_LOCK_TIME_SEC);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                altarId,
                "fx_altar_lock",
                ALTAR_LOCK_TIME_SEC * 1000,
                "fx_altar_lock" + i,
                false,
                "Bip01",
                false,
                true,
                team);
        int altarNum = getAltarNum(i);

        ExtensionCommands.updateAltar(this.parentExt, this.room, altarNum, team, true);

        handleAltarCaptureBuff(i, team, altarId, gooUsers);
    }

    protected void handleAltarCaptureBuff(
            int i, int team, String altarId, List<UserActor> gooUsers) {

        try {
            String killerId = null;

            for (Actor a : getActors()) {
                if ((a instanceof UserActor || a instanceof Bot) && a.getTeam() == team) {

                    int DAMAGE_ALTAR = 1;
                    boolean hasGooBuff = a instanceof UserActor && gooUsers.contains(a);

                    if (killerId == null) killerId = a.getId();
                    String stat1;
                    String stat2;

                    String fxId;
                    String icon;
                    String desc;

                    if (i == DAMAGE_ALTAR) {
                        stat1 = "attackDamage";
                        stat2 = "spellDamage";

                        fxId = "altar_buff_offense";
                        icon = "icon_altar_attack";
                        desc = "altar2_description";

                    } else {
                        stat1 = "armor";
                        stat2 = "spellResist";

                        fxId = "altar_buff_defense";
                        icon = "icon_altar_armor";
                        desc = "altar1_description";
                    }

                    double percent = ALTAR_BUFF;
                    if (hasGooBuff) percent = GOO_ALTAR_BUFF;

                    a.getEffectManager()
                            .addEffect(
                                    a.getId() + "_altar_buff1",
                                    stat1,
                                    percent,
                                    ModifierType.MULTIPLICATIVE,
                                    ModifierIntent.BUFF,
                                    ALTAR_BUFF_DURATION,
                                    fxId,
                                    a.getId() + fxId,
                                    "");
                    a.getEffectManager()
                            .addEffect(
                                    a.getId() + "_altar_buff2",
                                    stat2,
                                    percent,
                                    ModifierType.MULTIPLICATIVE,
                                    ModifierIntent.BUFF,
                                    ALTAR_BUFF_DURATION);
                    if (a instanceof UserActor) {
                        UserActor ua = (UserActor) a;

                        Champion.handleStatusIcon(parentExt, ua, icon, desc, ALTAR_BUFF_DURATION);

                        if (hasGooBuff) {
                            ua.addXP(GOO_ALTAR_CAPTURE_EXP);
                        }
                    }
                }
            }
            ExtensionCommands.knockOutActor(
                    parentExt, room, altarId, killerId, ALTAR_LOCK_TIME_SEC);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private int getAltarNum(int i) {
        int altarNum;
        String groupId = room.getGroupId();
        GameMap gameMap = GameManager.getMap(GameManager.getRoomGroupEnum(groupId));

        if (gameMap == GameMap.BATTLE_LAB) {
            altarNum = i == 0 ? 1 : i == 1 ? 0 : i;
        } else {
            // altar num 0 - top
            // altar num 2 - bot
            // i 0 - bot
            // i 1 = top
            altarNum = i == 0 ? 2 : 0;
        }
        return altarNum;
    }

    private void handlePassiveXP() {
        double purpleLevel = 0;
        double blueLevel = 0;
        int purpleCount = 0;
        int blueCount = 0;
        for (UserActor player : this.players) {
            if (player.getTeam() == 0) {
                purpleLevel += player.getLevel();
                purpleCount++;
            } else if (player.getTeam() == 1) {
                blueLevel += player.getLevel();
                blueCount++;
            }
        }
        // Get the average level of players
        purpleLevel = (int) Math.floor(purpleLevel / ((double) purpleCount));
        blueLevel = (int) Math.floor(blueLevel / ((double) blueCount));
        for (UserActor player : this.players) {
            int additionalXP =
                    2; // Get more XP if you are below the average level of the enemy and get less
            // xp if you are above.
            if (player.getTeam() == 0) additionalXP *= (blueLevel - player.getLevel());
            else if (player.getTeam() == 1) additionalXP *= (purpleLevel - player.getLevel());
            if (purpleLevel == 0 || blueLevel == 0 || additionalXP < 0) additionalXP = 0;
            player.addXP(2 + additionalXP + (xpDebug ? 100 : 0));
        }
    }

    public void playMainMusic(ATBPExtension parentExt, Room room) {
        String[] mainMusicStings = {"music_main1", "music_main2", "music_main3"};
        Random random = new Random();
        int index = random.nextInt(3);
        String musicName = mainMusicStings[index];
        int duration = 0;
        switch (musicName) { // subtract 1 second from each, so they don't loop for a brief moment
            case "music_main1":
                duration = 1000 * 129;
                break;
            case "music_main2":
                duration = 1000 * 177;
                break;
            case "music_main3":
                duration = 1000 * 139;
                break;
        }
        ExtensionCommands.playSound(
                parentExt, room, "music", "music/" + musicName, new Point2D.Float(0, 0));
        Runnable musicEnd = () -> this.playMainMusic = true;
        parentExt.getTaskScheduler().schedule(musicEnd, duration, TimeUnit.MILLISECONDS);
    }

    public void playTowerMusic() {
        this.playMainMusic = false;
        String[] towerDownStings = {
            "sting_towerdown1",
            "sting_towerdown2",
            "sting_towerdown3",
            "sting_towerdown4",
            "sting_towerdown5"
        };
        Random random = new Random();
        int index = random.nextInt(5);
        String stingName = towerDownStings[index];
        int duration = 0;
        switch (stingName) {
            case "sting_towerdown1":
                duration = 13500;
                break;
            case "sting_towerdown2":
            case "sting_towerdown3":
                duration = 7000;
                break;
            case "sting_towerdown4":
                duration = 13000;
                break;
            case "sting_towerdown5":
                duration = 7500;
        }
        ExtensionCommands.playSound(
                parentExt, room, "music", "music/" + stingName, new Point2D.Float(0, 0));
        Runnable stingEnd = () -> this.playMainMusic = true;
        parentExt.getTaskScheduler().schedule(stingEnd, duration, TimeUnit.MILLISECONDS);
    }

    private void announceForTeam(Actor killer, int team, String announcerLine, boolean singleKill) {
        List<UserActor> users = new ArrayList<>(players);

        UserActor killerUA = getPlayer(killer.getId());
        if (killerUA != null) users.remove(killerUA);

        if (singleKill) {
            Actor killedChampion =
                    killer.getKilledChampions().get(killer.getKilledChampions().size() - 1);
            UserActor killedUa = getPlayer(killedChampion.getId());

            if (killedUa != null) {
                users.remove(killedUa);
            }
        }

        for (UserActor ua : users) {
            if (ua.getTeam() == team) {
                ExtensionCommands.playSound(parentExt, ua.getUser(), "global", announcerLine);
            }
        }
    }

    private void announceKills() {
        for (Actor a : getChampions()) {
            if (a.getShouldTriggerAnnouncer()) {
                a.setShouldTriggerAnnouncer(false);
                handleAnnouncerBoolean();

                int multi = a.getMultiKill();
                int spree = a.getKillingSpree();

                if (multi > 1) {
                    announceMultiOrSpree(
                            a,
                            multi,
                            ChampionData.ALLY_MULTIES,
                            ChampionData.ENEMY_MULTIES,
                            ChampionData.OWN_MULTIES);

                } else if (spree > 2) {
                    announceMultiOrSpree(
                            a,
                            spree,
                            ChampionData.ALLY_SPREES,
                            ChampionData.ENEMY_SPREES,
                            ChampionData.OWN_SPREES);

                } else {
                    announceSingleKill(a);
                }
            }
        }
    }

    private void announceMultiOrSpree(
            Actor killer, int stat, String[] allySounds, String[] enemySounds, String[] ownSounds) {
        int index = Math.min(stat, allySounds.length - 1);

        UserActor killerUa = getPlayer(killer.getId());
        if (killerUa != null) {
            ExtensionCommands.playSound(
                    parentExt, killerUa.getUser(), "global", "announcer/" + ownSounds[index]);
        }

        String allySound = allySounds[index];
        String enemySound = enemySounds[index];

        announceForTeam(killer, killer.getTeam(), "announcer/" + allySound, false);
        announceForTeam(killer, killer.getOppositeTeam(), "announcer/" + enemySound, false);
    }

    private void announceSingleKill(Actor killer) {
        if (System.currentTimeMillis() - lastSingleKillAnnouncement >= SINGLE_KILL_COOLDOWN) {
            lastSingleKillAnnouncement = System.currentTimeMillis();

            UserActor killerUa = getPlayer(killer.getId());
            if (killerUa != null) {
                ExtensionCommands.playSound(
                        parentExt, killerUa.getUser(), "global", "announcer/you_defeated_enemy");
            }

            Actor killedChampion =
                    killer.getKilledChampions().get(killer.getKilledChampions().size() - 1);
            UserActor killedUa = getPlayer(killedChampion.getId());

            if (killedUa != null) {
                ExtensionCommands.playSound(
                        parentExt, killedUa.getUser(), "global", "announcer/you_are_defeated");
            }

            announceForTeam(killer, killer.getTeam(), "announcer/enemy_defeated", true);
            announceForTeam(killer, killer.getOppositeTeam(), "announcer/ally_defeated", true);
        }
    }

    private void handleAnnouncerBoolean() {
        isAnnouncingKill = true;
        Runnable resetIsAnnouncingKill = () -> isAnnouncingKill = false;
        parentExt.getTaskScheduler().schedule(resetIsAnnouncingKill, 500, TimeUnit.MILLISECONDS);
    }

    protected int getWinnerWhenTie() {
        int purpleKills = 0;
        int blueKills = 0;
        int purpleDeaths = 0;
        int blueDeaths = 0;
        int purpleAssists = 0;
        int blueAssists = 0;

        List<Tower> purpleTowers =
                towers.stream().filter(t -> t.getTeam() == 0).collect(Collectors.toList());
        List<Tower> blueTowers =
                towers.stream().filter(t -> t.getTeam() == 1).collect(Collectors.toList());

        int pTowersNum = purpleTowers.size();
        int bTowersNum = blueTowers.size();

        for (UserActor ua : players) {
            if (ua.getTeam() == 0) {
                purpleKills += ua.getStat("kills");
                purpleDeaths += ua.getStat("deaths");
                purpleAssists += ua.getStat("assists");
            } else {
                blueKills += ua.getStat("kills");
                blueDeaths += ua.getStat("deaths");
                blueAssists += ua.getStat("assists");
            }
        }

        if (pTowersNum != bTowersNum) {
            return pTowersNum > bTowersNum ? 0 : 1;
        } else if (purpleKills != blueKills) {
            return purpleKills > blueKills ? 0 : 1;
        } else if (purpleDeaths != blueDeaths) {
            return purpleDeaths < blueDeaths ? 0 : 1;
        } else if (purpleAssists != blueAssists) {
            return purpleAssists > blueAssists ? 0 : 1;
        } else {
            Random random = new Random();
            return random.nextInt(2); // coin flip
        }
    }

    public void handleFountain() {
        HashMap<Integer, Point2D> centers = getFountainsCenter();

        for (Map.Entry<Integer, Point2D> entry : centers.entrySet()) {
            for (UserActor ua :
                    Champion.getUserActorsInRadius(this, entry.getValue(), FOUNTAIN_RADIUS)) {
                if (ua.getTeam() == entry.getKey()
                        && ua.getHealth() != ua.getMaxHealth()
                        && ua.getHealth() > 0) {
                    ua.changeHealth(FOUNTAIN_HEAL);
                    ExtensionCommands.createActorFX(
                            this.parentExt,
                            this.room,
                            ua.getId(),
                            "fx_health_regen",
                            3000,
                            ua.getId() + "_fountainRegen",
                            true,
                            "Bip01",
                            false,
                            false,
                            ua.getTeam());
                }
            }
        }
    }

    protected boolean insideHealth(Point2D pLoc, int health) {
        Point2D healthLocation = getHealthLocation(health);
        double hx = healthLocation.getX();
        double hy = healthLocation.getY();
        if (hx == 0) return false;
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px - hx, 2) + Math.pow(pz - hy, 2));
        return dist <= 0.7;
    }

    protected void spawnHealth(String id) {
        int healthNum = getHealthNum(id);
        Point2D healthLocation = getHealthLocation(healthNum);
        int effectTime = (15 * 60 - secondsRan) * 1000;
        if (healthLocation.getX() != 0) {
            ExtensionCommands.createWorldFX(
                    parentExt,
                    this.room,
                    "",
                    "pickup_health_cyclops",
                    id + "_fx",
                    effectTime,
                    (float) healthLocation.getX(),
                    (float) healthLocation.getY(),
                    false,
                    2,
                    0f);
        }
    }

    public void addScore(Actor earner, int team, int points) {
        ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
        int blueScore = scoreObject.getInt("blue");
        int purpleScore = scoreObject.getInt("purple");
        int newBlueScore = blueScore;
        int newPurpleScore = purpleScore;
        if (team == 1) newBlueScore += points;
        else newPurpleScore += points;
        scoreObject.putInt("blue", newBlueScore);
        scoreObject.putInt("purple", newPurpleScore);
        ExtensionCommands.updateScores(this.parentExt, this.room, newPurpleScore, newBlueScore);
        if (earner != null) {
            earner.addGameStat("score", points);
        }
    }

    protected void announcePointLead() {
        int purpleScore = room.getVariable("score").getSFSObjectValue().getInt("purple");
        int blueScore = room.getVariable("score").getSFSObjectValue().getInt("blue");

        if (pointLeadTeam == null && blueScore != purpleScore
                || (pointLeadTeam == PointLeadTeam.PURPLE && blueScore > purpleScore)
                || (pointLeadTeam == PointLeadTeam.BLUE && purpleScore > blueScore)) {

            String purpleSound;
            String blueSound;

            if (purpleScore > blueScore) {
                pointLeadTeam = PointLeadTeam.PURPLE;
                purpleSound = "gained_point_lead";
                blueSound = "lost_point_lead";
            } else {
                pointLeadTeam = PointLeadTeam.BLUE;
                purpleSound = "lost_point_lead";
                blueSound = "gained_point_lead";
            }

            for (UserActor ua : this.getPlayers()) {
                if (ua.getTeam() == 0) {
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "global", "announcer/" + purpleSound);
                } else {
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "global", "announcer/" + blueSound);
                }
            }
        }
    }

    protected void
            handleCooldowns() { // Cooldown keys structure is id__cooldownType__value. Example for a
        // buff
        // cooldown could be lich__buff__attackDamage
        Set<String> keys = new HashSet<>(cooldowns.keySet());
        for (String key : keys) {
            String[] keyVal = key.split("__");
            String id = keyVal[0];
            String cooldown = keyVal[1];
            int time = cooldowns.get(key) - 1;
            if (time <= 0) {
                switch (cooldown) {
                    case "altar":
                        for (User u : room.getUserList()) {
                            ISFSObject data = new SFSObject();

                            int altarIndex = Integer.parseInt(id);
                            int altarNum = getAltarNum(altarIndex);

                            data.putInt("altar", altarNum);
                            data.putInt("team", 2);
                            data.putBool("locked", false);
                            parentExt.send("cmd_altar_update", data, u);
                            altarStatus[altarIndex] = 0;
                        }
                        break;
                    case "buff":
                        Console.logWarning(cooldown + " still being read!");
                        break;
                }
                cooldowns.remove(key);
            } else {
                cooldowns.put(key, time);
            }
        }
    }

    protected int getHealthNum(String id) {
        switch (id) {
            case "ph2": // Purple team bot
                return 4;
            case "ph1": // Purple team top
                return 3;
            case "ph3": // Purple team mid
                return 5;
            case "bh2": // Blue team bot
                return 1;
            case "bh1": // Blue team top
                return 0;
            case "bh3": // Blue team mid
                return 2;
        }
        return -1;
    }

    protected boolean hasSuperMinion(int lane, int team) {
        for (Minion m : minions) {
            if (m.getTeam() == team
                    && m.getLane() == lane
                    && m.getType() == Minion.MinionType.SUPER
                    && m.getHealth() > 0) return true;
        }
        return false;
    }

    public ArrayList<UserActor> getPlayers() {
        return new ArrayList<>(this.players);
    }

    public List<Actor> getChampions() {
        List<Actor> champions = new ArrayList<>();
        champions.addAll(bots);
        champions.addAll(players);
        return champions;
    }

    public Tower findTower(String id) {
        for (Tower t : towers) {
            if (t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public Minion findMinion(String id) {
        for (Minion m : minions) {
            if (m.getId().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public void addMinion(GameMap map, int team, int minionNum, int wave, int lane) {
        Minion m = new Minion(parentExt, room, map, team, minionNum, wave, lane);
        minions.add(m);
    }

    public Base getOpposingTeamBase(int team) {
        if (team == 0) return bases[1];
        else return bases[0];
    }

    public UserActor getPlayer(String id) {
        for (UserActor p : players) {
            if (p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public Minion getMinion(String id) {
        for (Minion m : minions) {
            if (m.getId().equalsIgnoreCase(id)) return m;
        }
        return null;
    }

    public Tower getTower(String id) {
        for (Tower t : towers) {
            if (t.getId().equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public Tower getTower(int num) {
        for (Tower t : towers) {
            if (t.getTowerNum() == num) return t;
        }
        return null;
    }

    public Actor getActor(String id) {
        for (Actor a : this.getActors()) {
            if (a.getId().equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    public List<Minion> getMinions() {
        return new ArrayList<>(minions);
    }

    public List<Minion> getMinions(int team, int lane) {
        List<Minion> teamMinions = new ArrayList<>();
        List<Minion> allMinions = new ArrayList<>(this.minions);
        for (Minion m : allMinions) {
            if (m.getTeam() == team && m.getLane() == lane) teamMinions.add(m);
        }
        return teamMinions;
    }

    public List<Monster> getCampMonsters() {
        return new ArrayList<>(this.campMonsters);
    }

    public List<Tower> getTowers() {
        return new ArrayList<>(this.towers);
    }

    public List<Actor> getBases() {
        List<Actor> bases = new ArrayList<>();
        bases.add(this.bases[0]);
        bases.add(this.bases[1]);
        return bases;
    }

    public List<BaseTower> getBaseTowers() {
        return new ArrayList<>(this.baseTowers);
    }

    public List<Actor> getBots() {
        return new ArrayList<>(this.bots);
    }

    public int getAverageChampionLevel() {
        if (getChampions().isEmpty()) return 1;

        int combinedPlayerLevel = 0;
        for (Actor a : getChampions()) {
            combinedPlayerLevel += a.getLevel();
        }
        return combinedPlayerLevel / getChampions().size();
    }

    public boolean canSpawnSupers(int team) {
        for (Tower t : this.towers) {
            if (t.getTeam() != team) {
                if (t.getTowerNum() != 3 && t.getTowerNum() != 0 && t.getHealth() > 0) return false;
            }
        }
        return true;
    }

    public int getTeamNumber(String id, int team) {
        int blue = 0;
        int purple = 0;
        for (UserActor ua : this.players) {
            if (ua.getId().equalsIgnoreCase(id)) {
                if (ua.getTeam() == 1) return blue;
                else return purple;
            } else {
                if (ua.getTeam() == 1) blue++;
                else purple++;
            }
        }
        return -1;
    }

    public UserActor getEnemyChampion(int team, String championName) {
        for (UserActor ua : this.players) {
            if (ua.getTeam() != team) {
                String playerAvatar = ua.getAvatar();
                if (ua.getChampionName(playerAvatar).equalsIgnoreCase(championName)) return ua;
            }
        }
        return null;
    }

    public void printActors() {
        for (Actor a : this.getActors()) {
            Console.log(
                    "ROOM: "
                            + this.room.getName()
                            + " |  TYPE: "
                            + a.getActorType().toString()
                            + " | ID: "
                            + (a.getActorType() == ActorType.PLAYER
                                    ? a.getDisplayName()
                                    : a.getId())
                            + " | "
                            + a.getHealth());
        }
    }

    public void addDestroyedId(String id) {
        this.destroyedIds.put(id, System.currentTimeMillis());
        this.createdActorIds.remove(id);
    }

    public boolean hasDestroyedId(String id) {
        return this.destroyedIds.containsKey(id);
    }

    public void addActorId(String id) {
        this.createdActorIds.add(id);
    }

    public boolean hasActorId(String id) {
        return this.createdActorIds.contains(id);
    }

    public boolean isPracticeMap() {
        RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());
        return GameManager.getMap(roomGroup) == GameMap.CANDY_STREETS;
    }

    public List<Projectile> getActiveProjectiles() {
        return this.activeProjectiles;
    }
}

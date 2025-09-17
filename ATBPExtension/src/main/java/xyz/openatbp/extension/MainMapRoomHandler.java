package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;

public class MainMapRoomHandler extends RoomHandler {
    public static final int BASE_ACCOUNT_EXP_VALUE = 10;
    public static final int WINNER_ACCOUNT_EXP_INCREASE = 5;
    private HashMap<User, UserActor> dcPlayers = new HashMap<>();
    private List<Actor> companions = new ArrayList<>();
    private final boolean IS_RANKED_MATCH = this.room.getGroupId().equals("PVP");

    public MainMapRoomHandler(ATBPExtension parentExt, Room room) {
        super(parentExt, room, GameManager.L2_SPAWNS, MapData.NORMAL_HP_SPAWN_RATE);
        baseTowers.add(new BaseTower(parentExt, room, "purple_tower3", 0));
        baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));

        HashMap<String, Point2D> towers0 = MapData.getMainMapTowerData(0);
        HashMap<String, Point2D> towers1 = MapData.getMainMapTowerData(1);

        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }
    }

    @Override
    public void handleMinionSpawns() {
        int minionWave = secondsRan / 30;
        if (minionWave != this.currentMinionWave) {
            int minionNum = secondsRan % 10;
            if (minionNum == 4) this.currentMinionWave = minionWave;
            if (minionNum <= 3) {
                this.addMinion(1, minionNum, minionWave, 0);
                this.addMinion(0, minionNum, minionWave, 0);
                this.addMinion(1, minionNum, minionWave, 1);
                this.addMinion(0, minionNum, minionWave, 1);

            } else if (minionNum == 4) {
                for (int i = 0; i < 2; i++) { // i = lane
                    for (int g = 0; g < 2; g++) {
                        if (!this.hasSuperMinion(i, g) && this.canSpawnSupers(g))
                            this.addMinion(g, minionNum, minionWave, i);
                    }
                }
            }
        }
    }

    @Override
    public void handleAltars() {
        handleAltarsForMode(3);
    }

    @Override
    public Point2D getAltarLocation(int altar) {
        double altar_x = 0d;
        double altar_y = 0d;

        if (altar == 0) {
            altar_x = MapData.L2_TOP_ALTAR[0];
            altar_y = MapData.L2_TOP_ALTAR[1];
        } else if (altar == 2) {
            altar_x = MapData.L2_BOT_ALTAR[0];
            altar_y = MapData.L2_BOT_ALTAR[1];
        }
        return new Point2D.Double(altar_x, altar_y);
    }

    @Override
    public int getAltarStatus(Point2D location) {
        Point2D botAltar = new Point2D.Float(MapData.L2_BOT_ALTAR[0], MapData.L2_BOT_ALTAR[1]);
        Point2D midAltar = new Point2D.Float(0f, 0f);
        if (location.equals(botAltar)) return this.altarStatus[2];
        else if (location.equals(midAltar)) return this.altarStatus[1];
        else return this.altarStatus[0];
    }

    @Override
    public void handleAltarGameScore(int capturingTeam, int altarIndex) {
        if (IS_RANKED_MATCH) {
            Point2D altarLocation = getAltarLocation(altarIndex);
            List<UserActor> userActorsInRadius =
                    Champion.getUserActorsInRadius(this, altarLocation, 2);

            List<UserActor> eligiblePlayers =
                    userActorsInRadius.stream()
                            .filter(ua -> ua.getTeam() == capturingTeam)
                            .filter(ua -> ua.getHealth() > 0)
                            .collect(Collectors.toList());

            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            for (UserActor ua : eligiblePlayers) {
                if (altarIndex == 1) {
                    ua.addGameStat("score", 15);
                } else {
                    ua.addGameStat("score", 10);
                }

                String tegID = (String) ua.getUser().getSession().getProperty("tegid");
                Bson filter = eq("user.TEGid", tegID);
                Bson updateOperation = Updates.inc("player.altars", 1);
                UpdateOptions options = new UpdateOptions().upsert(true);
                Console.debugLog(playerData.updateOne(filter, updateOperation, options));
            }
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

    @Override
    public void gameOver(int winningTeam) {
        if (this.gameOver) return;
        try {
            this.gameOver = true;
            this.room.setProperty("state", 3);
            ExtensionCommands.gameOver(
                    parentExt, room, dcPlayers, winningTeam, IS_RANKED_MATCH, false);
            if (IS_RANKED_MATCH) {
                logChampionData(winningTeam);
                logMatchHistory(winningTeam);
            }
            for (UserActor ua : this.players) {
                if (ua.getTeam() == winningTeam) {
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "global", "announcer/victory");
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "music", "music/music_victory");
                } else {
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "global", "announcer/defeat");
                    ExtensionCommands.playSound(
                            parentExt, ua.getUser(), "music", "music/music_defeat");
                }

                if (IS_RANKED_MATCH) {
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

                        int currentRankProgress = dataObj.get("player").get("rankProgress").asInt();
                        int accountExpGained = BASE_ACCOUNT_EXP_VALUE;
                        int rankIncrease = 0;
                        if (ua.getTeam() == winningTeam)
                            accountExpGained += WINNER_ACCOUNT_EXP_INCREASE;

                        if (currentRankProgress + accountExpGained >= 100) {
                            rankIncrease++;
                            currentRankProgress = 0;
                        } else {
                            currentRankProgress += accountExpGained;
                        }

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
                        updateList.add(Updates.inc("player.rank", rankIncrease));
                        updateList.add(Updates.set("player.rankProgress", currentRankProgress));
                        updateList.add(Updates.inc("player.winsPVP", wins));
                        updateList.add(
                                Updates.inc(
                                        "player.points",
                                        points)); // Always zero I have no idea what this is
                        // for?;
                        updateList.add(
                                Updates.inc(
                                        "player.coins", ua.getTeam() == winningTeam ? 100 : 80));
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

    @Override
    public void spawnMonster(String monster) {
        float x = 0;
        float z = 0;
        String actor = monster;
        if (monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("ironowls")) {
            char[] abc = {'a', 'b', 'c'};
            for (int i = 0;
                    i < 3;
                    i++) { // Gnomes and owls have three different mobs so need to be spawned in
                // triplets
                if (monster.equalsIgnoreCase("gnomes")) {
                    actor = "gnome_" + abc[i];
                    x = (float) MapData.L2_GNOMES[i].getX();
                    z = (float) MapData.L2_GNOMES[i].getY();

                } else {
                    actor = "ironowl_" + abc[i];
                    x = (float) MapData.L2_OWLS[i].getX();
                    z = (float) MapData.L2_OWLS[i].getY();
                }
                Point2D spawnLoc = new Point2D.Float(x, z);
                campMonsters.add(new Monster(parentExt, room, spawnLoc, actor));
                ExtensionCommands.createActor(
                        this.parentExt, this.room, actor, actor, spawnLoc, 0f, 2);
                ExtensionCommands.moveActor(
                        this.parentExt, this.room, actor, spawnLoc, spawnLoc, 5f, false);
            }
        } else if (monster.length() > 3) {
            switch (monster) {
                case "hugwolf":
                    x = MapData.HUGWOLF[0];
                    z = MapData.HUGWOLF[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.HUGWOLF, actor));
                    break;
                case "grassbear":
                    x = MapData.GRASSBEAR[0];
                    z = MapData.GRASSBEAR[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.GRASSBEAR, actor));
                    break;
                case "keeoth":
                    x = MapData.L2_KEEOTH[0];
                    z = MapData.L2_KEEOTH[1];
                    campMonsters.add(new Keeoth(parentExt, room, MapData.L2_KEEOTH, actor));
                    break;
                case "goomonster":
                    x = MapData.L2_GOOMONSTER[0];
                    z = MapData.L2_GOOMONSTER[1];
                    actor = "goomonster";
                    campMonsters.add(new GooMonster(parentExt, room, MapData.L2_GOOMONSTER, actor));
                    break;
            }
            Point2D spawnLoc = new Point2D.Float(x, z);
            ExtensionCommands.createActor(this.parentExt, this.room, actor, actor, spawnLoc, 0f, 2);
            ExtensionCommands.moveActor(
                    this.parentExt, this.room, actor, spawnLoc, spawnLoc, 5f, false);
        }
    }

    @Override
    public void handleSpawnDeath(Actor a) {
        // Console.debugLog("The room has killed " + a.getId());
        String mons = a.getId().split("_")[0];

        for (String s : GameManager.L2_SPAWNS) {
            if (s.contains(mons)) {
                if (s.contains("keeoth")) {
                    room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                    return;
                } else if (s.contains("goomonster")) {
                    room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                    return;
                } else if (!s.contains("gnomes") && !s.contains("owls")) {
                    room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                    return;
                } else {
                    for (Monster m : campMonsters) {
                        if (!m.getId().equalsIgnoreCase(a.getId())
                                && m.getId().contains(mons)
                                && m.getHealth() > 0) {
                            return;
                        }
                    }
                    room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                    return;
                }
            }
        }
    }

    @Override
    public Point2D getHealthLocation(int num) {
        float x = MapData.L2_BOT_BLUE_HEALTH[0];
        float z = MapData.L2_BOT_BLUE_HEALTH[1];
        switch (num) {
            case 0:
                z *= -1;
                break;
            case 2:
                x = MapData.L2_LEFT_HEALTH[0];
                z = MapData.L2_LEFT_HEALTH[1];
                break;
            case 3:
                x *= -1;
                z *= -1;
                break;
            case 4:
                x *= -1;
                break;
            case 5:
                x = MapData.L2_LEFT_HEALTH[0] * -1;
                z = MapData.L2_LEFT_HEALTH[1];
                break;
        }
        return new Point2D.Float(x, z);
    }

    @Override
    public void handlePlayerDC(User user) {
        if (this.players.size() == 1) return;
        try {
            UserActor player = this.getPlayer(String.valueOf(user.getId()));
            if (player.getTeam() == 0) this.dcWeight--;
            else if (player.getTeam() == 1) this.dcWeight++;
            this.dcPlayers.put(user, player);
            player.destroy();
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            Document data =
                    playerData
                            .find(eq("user.TEGid", (String) user.getSession().getProperty("tegid")))
                            .first();
            if (data != null && IS_RANKED_MATCH && this.secondsRan >= 5) {
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
            int team = player.getTeam();
            this.players.removeIf(p -> p.getId().equalsIgnoreCase(String.valueOf(user.getId())));
            int teamMembersLeft = 0;
            for (UserActor p : players) {
                if (p.getTeam() == team) {
                    teamMembersLeft++;
                    break;
                }
            }
            int purpleTeamSize = 0;
            int blueTeamSize = 0;
            for (UserActor p : players) {
                if (p.getTeam() == 0) {
                    purpleTeamSize++;
                } else if (p.getTeam() == 1) {
                    blueTeamSize++;
                }
            }
            int teamSizeDiff = blueTeamSize - purpleTeamSize;
            int oppositeTeam = 0;
            if (team == 0) oppositeTeam = 1;
            if (teamMembersLeft == 0) this.gameOver(oppositeTeam);
            else {
                for (UserActor p : this.players) {
                    if (purpleTeamSize == 3 && blueTeamSize == 2) {
                        if (p.getTeam() == team) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == 3 && blueTeamSize == 1) {
                        if (p.getTeam() == team) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == 2 && blueTeamSize == 1) {
                        if (p.getTeam() != team) {
                            p.handleDCBuff(teamSizeDiff, true);
                        } else if (p.getTeam() == 1) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == 2 && blueTeamSize == 3) {
                        if (p.getTeam() == team) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == 1 && blueTeamSize == 3) {
                        if (p.getTeam() == team) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == 1 && blueTeamSize == 2) {
                        if (p.getTeam() != team) {
                            p.handleDCBuff(teamSizeDiff, true);
                        } else if (p.getTeam() == 0) {
                            p.handleDCBuff(teamSizeDiff, false);
                        }
                    } else if (purpleTeamSize == blueTeamSize) {
                        p.handleDCBuff(teamSizeDiff, false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Actor> getCompanions() {
        return this.companions;
    }

    public void addCompanion(Actor a) {
        this.companions.add(a);
    }

    public void removeCompanion(Actor a) {
        this.companions.remove(a);
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

    public void addProjectile(Projectile p) {
        this.activeProjectiles.add(p);
    }

    @Override
    public HashMap<Integer, Point2D> getFountainsCenter() {
        Point2D blueCenter = new Point2D.Float(50.16f, 0f);
        Point2D purpleCenter = new Point2D.Float(-50.16f, 0f);

        HashMap<Integer, Point2D> centers = new HashMap<>();
        centers.put(0, purpleCenter);
        centers.put(1, blueCenter);
        return centers;
    }

    @Override
    public List<Actor> getActors() {
        List<Actor> actors = new ArrayList<>();
        actors.addAll(towers);
        actors.addAll(baseTowers);
        actors.addAll(minions);
        Collections.addAll(actors, bases);
        actors.addAll(players);
        actors.addAll(campMonsters);
        actors.addAll(companions);
        actors.removeIf(a -> a.getHealth() <= 0);
        return actors;
    }

    @Override
    public List<Actor> getActorsInRadius(Point2D center, float radius) {
        List<Actor> actorsInRadius = new ArrayList<>();
        actorsInRadius.addAll(towers);
        actorsInRadius.addAll(baseTowers);
        actorsInRadius.addAll(minions);
        Collections.addAll(actorsInRadius, bases);
        actorsInRadius.addAll(players);
        actorsInRadius.addAll(campMonsters);
        actorsInRadius.addAll(companions);
        actorsInRadius.removeIf(a -> a.getHealth() <= 0);
        return actorsInRadius.stream()
                .filter(a -> a.getLocation().distance(center) <= radius)
                .collect(Collectors.toList());
    }

    @Override
    public List<Actor> getEnemiesInPolygon(int team, Path2D polygon) {
        List<Actor> enemiesInPolygon = new ArrayList<>();
        enemiesInPolygon.addAll(towers);
        enemiesInPolygon.addAll(baseTowers);
        enemiesInPolygon.addAll(minions);
        Collections.addAll(enemiesInPolygon, bases);
        enemiesInPolygon.addAll(players);
        enemiesInPolygon.addAll(campMonsters);
        enemiesInPolygon.addAll(companions);
        enemiesInPolygon.removeIf(a -> a.getHealth() <= 0);
        return enemiesInPolygon.stream()
                .filter(a -> a.getTeam() != team)
                .filter(a -> polygon.contains(a.getLocation()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Actor> getNonStructureEnemies(int team) {
        List<Actor> nonStructureEnemies = new ArrayList<>();
        nonStructureEnemies.addAll(towers);
        nonStructureEnemies.addAll(baseTowers);
        nonStructureEnemies.addAll(minions);
        Collections.addAll(nonStructureEnemies, bases);
        nonStructureEnemies.addAll(players);
        nonStructureEnemies.addAll(campMonsters);
        nonStructureEnemies.addAll(companions);
        nonStructureEnemies.removeIf(a -> a.getHealth() <= 0);
        return nonStructureEnemies.stream()
                .filter(a -> a.getTeam() != team)
                .filter(a -> a.getActorType() != ActorType.TOWER)
                .filter(a -> a.getActorType() != ActorType.BASE)
                .collect(Collectors.toList());
    }

    @Override
    public List<Actor> getEligibleActors(
            int team,
            boolean teamFilter,
            boolean hpFilter,
            boolean towerFilter,
            boolean baseFilter) {
        List<Actor> eligibleActors = new ArrayList<>();
        eligibleActors.addAll(towers);
        eligibleActors.addAll(baseTowers);
        eligibleActors.addAll(minions);
        Collections.addAll(eligibleActors, bases);
        eligibleActors.addAll(players);
        eligibleActors.addAll(campMonsters);
        eligibleActors.addAll(companions);
        return eligibleActors.stream()
                .filter(a -> !hpFilter || a.getHealth() > 0)
                .filter(a -> !teamFilter || a.getTeam() != team)
                .filter(a -> !towerFilter || a.getActorType() != ActorType.TOWER)
                .filter(a -> !baseFilter || a.getActorType() != ActorType.BASE)
                .collect(Collectors.toList());
    }
}

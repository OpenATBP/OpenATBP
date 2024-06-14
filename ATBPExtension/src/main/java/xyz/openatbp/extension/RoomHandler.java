package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;

public class RoomHandler implements Runnable {
    private HashMap<User, UserActor> dcPlayers;
    private ATBPExtension parentExt;
    private Room room;
    private ArrayList<Minion> minions;
    private ArrayList<UserActor> players;
    private List<Projectile> activeProjectiles;
    private List<Monster> campMonsters;
    private List<Actor> companions;
    private Base[] bases = new Base[2];
    private ArrayList<BaseTower> baseTowers;
    private ArrayList<Tower> towers;
    private int mSecondsRan = 0;
    private int secondsRan = 0;
    private int[] altarStatus = {0, 0, 0};
    private int[] purpleFastCaptureCounter = {0, 0, 0};
    private int[] blueFastCaptureCounter = {0, 0, 0};
    private HashMap<String, Integer> cooldowns = new HashMap<>();
    private int currentMinionWave = 0;
    private GumballGuardian[] guardians;
    private boolean gameOver = false;
    private HashMap<String, Long> destroyedIds = new HashMap<>();
    private List<String> createdActorIds = new ArrayList<>();
    private static boolean monsterDebug = false;
    private static boolean xpDebug = false;
    private boolean practiceMap;
    private boolean playMainMusic = false;
    private boolean playTowerMusic = false;
    private long lastPointLeadTime = 0;
    private static final int FOUNTAIN_HEAL = 250; // every 0.5s
    private ScheduledFuture<?> scriptHandler;

    public RoomHandler(ATBPExtension parentExt, Room room) {
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        this.towers = new ArrayList<>();
        this.baseTowers = new ArrayList<>();
        this.players = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        this.practiceMap =
                room.getGroupId().equalsIgnoreCase("practice")
                        || (room.getName().contains("custom") && room.getMaxUsers() == 2);
        this.dcPlayers = new HashMap<>();
        Properties props = parentExt.getConfigProperties();
        monsterDebug = Boolean.parseBoolean(props.getProperty("monsterDebug", "false"));
        xpDebug = Boolean.parseBoolean(props.getProperty("xpDebug", "false"));
        HashMap<String, Point2D> towers0;
        HashMap<String, Point2D> towers1;
        if (!this.practiceMap) {
            towers0 = MapData.getMainMapTowerData(0);
            towers1 = MapData.getMainMapTowerData(1);
            baseTowers.add(new BaseTower(parentExt, room, "purple_tower3", 0));
            baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));
        } else {
            towers0 = MapData.getPTowerActorData(0);
            towers1 = MapData.getPTowerActorData(1);
            baseTowers.add(new BaseTower(parentExt, room, "purple_tower0", 0));
            baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));
        }
        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }
        bases[0] = new Base(parentExt, room, 0);
        bases[1] = new Base(parentExt, room, 1);
        this.guardians =
                new GumballGuardian[] {
                    new GumballGuardian(parentExt, room, 0), new GumballGuardian(parentExt, room, 1)
                };
        for (User u : room.getUserList()) {
            players.add(Champion.getCharacterClass(u, parentExt));
            // ExtensionCommands.createActor(this.parentExt,u,"testMonster2","bot_jake",new
            // Point2D.Float(0f,0f),0f,2);
            // ExtensionCommands.createActor(this.parentExt,u,"testMonster3","bot_iceking",new
            // Point2D.Float(0f,0f),0f,2);
        }
        // ExtensionCommands.createActor(this.parentExt,room,"testMonster","bot_finn",new
        // Point2D.Float(0f,0f),0f,2);
        this.activeProjectiles = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        this.companions = new ArrayList<>();
        // this.campMonsters = GameManager.initializeCamps(parentExt,room);
        /*
        for (Node[] nodes : parentExt.mainMapNodes) {
            for (Node n : nodes) {
                if (n.isSolid()) n.display(parentExt, room);
            }
        }
         */
        this.scriptHandler =
                parentExt
                        .getTaskScheduler()
                        .scheduleAtFixedRate(this, 100, 100, TimeUnit.MILLISECONDS);
    }

    public ATBPExtension getParentExt() {
        return this.parentExt;
    }

    public void stopScript(boolean abort) {
        if (abort) ExtensionCommands.abortGame(parentExt, this.room);
        this.scriptHandler.cancel(false);
    }

    @Override
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
                }
                if (mSecondsRan == 1000
                        || this.playMainMusic && secondsRan < (60 * 13) && !this.gameOver) {
                    playMainMusic(parentExt, room);
                    this.playMainMusic = false;
                }
                if (playTowerMusic) {
                    playTowerMusic();
                    this.playTowerMusic = false;
                }
                if (secondsRan == (60 * 7) + 30) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/time_half",
                            new Point2D.Float(0f, 0f));
                } else if (secondsRan == (60 * 13)) {
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
                } else if (secondsRan == 15 * 60) {
                    ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
                    int blueScore = scoreObject.getInt("blue");
                    int purpleScore = scoreObject.getInt("purple");
                    if (blueScore > purpleScore) this.gameOver(1);
                    else if (purpleScore > blueScore) this.gameOver(0);
                    else this.gameOver(-1);
                    return;
                }
                if (room.getUserList().size() == 0)
                    parentExt.stopScript(
                            room.getName(), true); // If no one is in the room, stop running.
                else {
                    handleAltars();
                    ExtensionCommands.updateTime(parentExt, this.room, mSecondsRan);
                }
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                for (String s :
                        GameManager.SPAWNS) { // Check all mob/health spawns for how long it's been
                    // since dead
                    if (s.length() > 3) {
                        int spawnRate = 45; // Mob spawn rate
                        if (s.equalsIgnoreCase("keeoth")) spawnRate = 120;
                        else if (s.equalsIgnoreCase("goomonster")) spawnRate = 90;
                        if (monsterDebug) spawnRate = 10;
                        if (spawns.getInt(s)
                                == spawnRate) { // Mob timers will be set to 0 when killed or health
                            // when taken
                            spawnMonster(s);
                            spawns.putInt(s, spawns.getInt(s) + 1);
                        } else {
                            spawns.putInt(s, spawns.getInt(s) + 1);
                        }
                    } else {
                        int time = spawns.getInt(s);
                        if ((this.secondsRan <= 91 && time == 90)
                                || (this.secondsRan > 91 && time == 60)) {
                            spawnHealth(s);
                        } else if ((this.secondsRan <= 91 && time < 90)
                                || (this.secondsRan > 91 && time < 60)) {
                            time++;
                            spawns.putInt(s, time);
                        }
                    }
                }
                handleCooldowns();

                int minionWave = secondsRan / 30;
                if (minionWave != this.currentMinionWave) {
                    int minionNum = secondsRan % 10;
                    if (minionNum == 4) this.currentMinionWave = minionWave;
                    if (minionNum <= 3) {
                        this.addMinion(1, minionNum, minionWave, 0);
                        this.addMinion(0, minionNum, minionWave, 0);
                        if (!this.practiceMap) {
                            this.addMinion(1, minionNum, minionWave, 1);
                            this.addMinion(0, minionNum, minionWave, 1);
                        }
                    } else if (minionNum == 4) {
                        for (int i = 0; i < 2; i++) { // i = lane
                            if (this.practiceMap && i == 1) break;
                            for (int g = 0; g < 2; g++) {
                                if (!this.hasSuperMinion(i, g) && this.canSpawnSupers(g))
                                    this.addMinion(g, minionNum, minionWave, i);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mSecondsRan % 500 == 0) {
            handleFountain();
        }
        try {
            for (UserActor u : players) { // Tracks player location
                u.update(mSecondsRan);
            }
        } catch (Exception e) {
            Console.logWarning("USER ACTOR UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            List<Projectile> projectileList = new ArrayList<>(this.activeProjectiles);
            for (Projectile p : projectileList) { // Handles skill shots
                p.update(this);
            }
            activeProjectiles.removeIf(Projectile::isDestroyed);
        } catch (Exception e) {
            Console.logWarning("PROJECTILE UPDATE EXCEPTION");
            e.printStackTrace();
        }

        try {
            for (Minion m : minions) { // Handles minion behavior
                // minionPathHelper.addRect((float)m.getLocation().getX()+49.75f,(float)m.getLocation().getY()+30.25f,0.5f,0.5f);
                m.update(mSecondsRan);
            }
            minions.removeIf(m -> (m.getHealth() <= 0));
        } catch (Exception e) {
            Console.logWarning("MINION UPDATE EXCEPTION");
            e.printStackTrace();
        }
        handleHealth();
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
                    if (this.practiceMap) {
                        if (t.getTowerNum() == 0 || t.getTowerNum() == 3)
                            bases[t.getTeam()].unlock();
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
        if (this.room.getUserList().size() == 0) parentExt.stopScript(this.room.getName(), true);
    }

    private void handlePassiveXP() {
        double purpleLevel = 0;
        double blueLevel = 0;
        for (UserActor player : this.players) {
            if (player.getTeam() == 0) purpleLevel += player.getLevel();
            else if (player.getTeam() == 1) blueLevel += player.getLevel();
        }
        // Get the average level of players
        purpleLevel = (int) Math.floor(purpleLevel / ((double) this.players.size() / 2));
        blueLevel = (int) Math.floor(blueLevel / ((double) this.players.size() / 2));
        for (UserActor player : this.players) {
            int additionalXP =
                    1; // Get more XP if you are below the average level of the enemy and get less
            // xp if you are above.
            if (player.getTeam() == 0) additionalXP *= (blueLevel - player.getLevel());
            else if (player.getTeam() == 1) additionalXP *= (purpleLevel - player.getLevel());
            if (purpleLevel == 0 || blueLevel == 0 || additionalXP < 0) additionalXP = 0;
            player.addXP(3 + additionalXP + (xpDebug ? 100 : 0));
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

    public List<Actor> getCompanions() {
        return this.companions;
    }

    public void addMinion(int team, int minionNum, int wave, int lane) {
        Minion m = new Minion(parentExt, room, team, minionNum, wave, lane);
        minions.add(m);
    }

    public Base getOpposingTeamBase(int team) {
        if (team == 0) return bases[1];
        else return bases[0];
    }

    public int getAltarStatus(Point2D location) {
        // main mode: 0 - top, 1 - mid, 2 - bot
        // practice mode: 0 - top, 1 - bot

        if (!this.practiceMap) {
            Point2D botAltar = new Point2D.Float(MapData.L2_BOT_ALTAR[0], MapData.L2_BOT_ALTAR[1]);
            Point2D midAltar = new Point2D.Float(0f, 0f);
            if (location.equals(botAltar)) return this.altarStatus[2];
            else if (location.equals(midAltar)) return this.altarStatus[1];
            else return this.altarStatus[0];
        } else {
            Point2D topAltar = new Point2D.Float(0f, MapData.L1_AALTAR_Z);
            if (location.equals(topAltar)) return this.altarStatus[0];
            else return this.altarStatus[1];
        }
    }

    private void handleHealth() {
        for (String s : GameManager.SPAWNS) {
            if (s.length() == 3) {
                ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
                if (spawns.getInt(s) == 91) {
                    for (UserActor u : players) {
                        Point2D currentPoint = u.getLocation();
                        if (insideHealth(currentPoint, getHealthNum(s)) && u.getHealth() > 0) {
                            int team = u.getTeam();
                            Point2D healthLoc = getHealthLocation(getHealthNum(s));
                            ExtensionCommands.removeFx(parentExt, room, s + "_fx");
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    String.valueOf(u.getId()),
                                    "picked_up_health_cyclops",
                                    2000,
                                    s + "_fx2",
                                    true,
                                    "",
                                    false,
                                    false,
                                    team);
                            ExtensionCommands.playSound(
                                    parentExt, u.getRoom(), "", "sfx_health_picked_up", healthLoc);
                            u.handleCyclopsHealing();
                            spawns.putInt(s, 0);
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean insideHealth(Point2D pLoc, int health) {
        Point2D healthLocation = getHealthLocation(health);
        double hx = healthLocation.getX();
        double hy = healthLocation.getY();
        if (hx == 0) return false;
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px - hx, 2) + Math.pow(pz - hy, 2));
        return dist <= 0.7;
    }

    private Point2D getHealthLocation(int num) {
        float x = MapData.L2_BOT_BLUE_HEALTH[0];
        float z = MapData.L2_BOT_BLUE_HEALTH[1];
        // num = 1
        if (!this.practiceMap) {
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
        } else {
            if (num == 0) {
                x = MapData.L1_BLUE_HEALTH_X;
                z = MapData.L1_BLUE_HEALTH_Z;
            } else if (num == 1) {
                x = MapData.L1_BLUE_HEALTH_X * -1;
                z = MapData.L1_BLUE_HEALTH_Z * -1;
            } else {
                x = 0;
                z = 0;
            }
        }
        return new Point2D.Float(x, z);
    }

    private int getHealthNum(String id) {
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

    private void spawnMonster(String monster) {
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
                    if (!this.practiceMap) {
                        x = (float) MapData.L2_GNOMES[i].getX();
                        z = (float) MapData.L2_GNOMES[i].getY();
                    } else {
                        x = (float) MapData.L1_GNOMES[i].getX();
                        z = (float) MapData.L1_GNOMES[i].getY();
                    }
                } else {
                    actor = "ironowl_" + abc[i];
                    if (!this.practiceMap) {
                        x = (float) MapData.L2_OWLS[i].getX();
                        z = (float) MapData.L2_OWLS[i].getY();
                    } else {
                        x = (float) MapData.L1_OWLS[i].getX();
                        z = (float) MapData.L1_OWLS[i].getY();
                    }
                }
                Point2D spawnLoc = new Point2D.Float(x, z);
                campMonsters.add(new Monster(parentExt, room, spawnLoc, actor));
                ExtensionCommands.createActor(
                        this.parentExt, this.room, actor, actor, spawnLoc, 0f, 2);
                ExtensionCommands.moveActor(
                        this.parentExt, this.room, actor, spawnLoc, spawnLoc, 5f, false);
            }
        } else if (monster.length() > 3 && !this.practiceMap) {
            switch (monster) {
                case "hugwolf":
                    x = MapData.HUGWOLF[0];
                    z = MapData.HUGWOLF[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.HUGWOLF, actor));
                    break;
                case "grassbear":
                    x = MapData.GRASS[0];
                    z = MapData.GRASS[1];
                    campMonsters.add(new Monster(parentExt, room, MapData.GRASS, actor));
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

    private void spawnHealth(String id) {
        int healthNum = getHealthNum(id);
        Point2D healthLocation = getHealthLocation(healthNum);
        int effectTime = (15 * 60 - secondsRan) * 1000;
        if (healthLocation.getX() != 0)
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
        room.getVariable("spawns").getSFSObjectValue().putInt(id, 91);
    }

    private void handleAltars() {
        // main mode: 0 - top, 1 - mid, 2 - bot
        // practice mode: 0 - top, 1 - bot
        if (!this.practiceMap) {
            handleAltarsForMode(3);
        } else {
            handleAltarsForMode(2);
        }
    }

    private void handleAltarsForMode(int numAltars) {
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
                            if (this.purpleFastCaptureCounter[i] > 0)
                                this.purpleFastCaptureCounter[i] = 0;
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
                            if (this.blueFastCaptureCounter[i] > 0)
                                this.blueFastCaptureCounter[i] = 0;
                            break;

                        case "purpleCounterIncrease":
                            this.purpleFastCaptureCounter[i]++;
                            break;

                        case "blueCounterIncrease":
                            this.blueFastCaptureCounter[i]++;
                            break;

                        case "purpleFastCapture":
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

                        case "blueFastCapture":
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
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    "altar_" + i,
                                    "fx_altar_" + (currentStage + 1),
                                    FX_DURATION,
                                    "altar_" + i + (currentStage + 1),
                                    false,
                                    "Bip001",
                                    false,
                                    true,
                                    0);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    "",
                                    "sfx_altar_" + (currentStage + 1),
                                    altarLocation);
                            break;

                        case "blueIncrease":
                            this.altarStatus[i]--;
                            ExtensionCommands.createActorFX(
                                    this.parentExt,
                                    this.room,
                                    "altar_" + i,
                                    "fx_altar_" + (currentStage + 1),
                                    FX_DURATION,
                                    "altar_" + i + (currentStage + 1),
                                    false,
                                    "Bip001",
                                    false,
                                    true,
                                    1);
                            ExtensionCommands.playSound(
                                    this.parentExt,
                                    this.room,
                                    "",
                                    "sfx_altar_" + (currentStage + 1),
                                    altarLocation);
                            break;
                    }
                    if (Math.abs(this.altarStatus[i]) >= 5) {
                        int capturingTeam = this.altarStatus[i] > 0 ? 0 : 1;
                        this.altarStatus[i] = 10; // Locks altar
                        final int capturedAltarIndex = i;
                        Runnable captureAltar =
                                () ->
                                        captureAltar(
                                                capturedAltarIndex,
                                                capturingTeam,
                                                "altar_" + capturedAltarIndex);
                        this.parentExt
                                .getTaskScheduler()
                                .schedule(captureAltar, 400, TimeUnit.MILLISECONDS);
                        if (this.purpleFastCaptureCounter[i] > 0)
                            this.purpleFastCaptureCounter[i] = 0;
                        if (this.blueFastCaptureCounter[i] > 0) this.blueFastCaptureCounter[i] = 0;

                        MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
                        for (UserActor ua :
                                Champion.getUserActorsInRadius(this, altarLocation, 2)) {
                            if (ua.getTeam() == capturingTeam && ua.getHealth() > 0) {
                                addAltarGameScore(ua, capturedAltarIndex);
                                String tegID =
                                        (String) ua.getUser().getSession().getProperty("tegid");
                                Bson filter = eq("user.TEGid", tegID);
                                Bson updateOperation = Updates.inc("player.altars", 1);
                                UpdateOptions options = new UpdateOptions().upsert(true);
                                Console.debugLog(
                                        playerData.updateOne(filter, updateOperation, options));
                            }
                        }
                    }
                }
            }
        }
    }

    private String determineAltarAction(int altarIndex) {
        // hierarchy: fast status decrease, status decrease, fast capture/counter increase, status
        // increase
        Point2D altarLocation = getAltarLocation(altarIndex);
        List<UserActor> uasInArea = Champion.getUserActorsInRadius(this, altarLocation, 2);
        List<UserActor> purplePlayers = new ArrayList<>();
        List<UserActor> bluePlayers = new ArrayList<>();

        for (UserActor ua : uasInArea) {
            if (ua.getTeam() == 0 && ua.getHealth() > 0) {
                purplePlayers.add(ua);
            } else if (ua.getTeam() == 1 && ua.getHealth() > 0) {
                bluePlayers.add(ua);
            }
        }

        int purpleCount = purplePlayers.size();
        int blueCount = bluePlayers.size();
        int status = this.altarStatus[altarIndex];
        int purpleCounter = this.purpleFastCaptureCounter[altarIndex];
        int blueCounter = this.blueFastCaptureCounter[altarIndex];

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

        if (purpleCount - blueCount > 1 && status >= 0) {
            return purpleCounter < 1 ? "purpleCounterIncrease" : "purpleFastCapture";
        } else if (blueCount - purpleCount > 1 && status <= 0) {
            return blueCounter < 1 ? "blueCounterIncrease" : "blueFastCapture";
        }

        if (purpleCount - blueCount == 1) {
            return "purpleIncrease";
        } else if (blueCount - purpleCount == 1) {
            return "blueIncrease";
        }

        return "";
    }

    private void resetAltar(int altarIndex) {
        int currentStage = Math.abs(this.altarStatus[altarIndex]);
        for (int i = 1; i <= currentStage; i++) {
            ExtensionCommands.removeFx(this.parentExt, this.room, ("altar_" + altarIndex) + i);
        }
        this.altarStatus[altarIndex] = 0;
        if (this.purpleFastCaptureCounter[altarIndex] > 0)
            this.purpleFastCaptureCounter[altarIndex] = 0;
        if (this.blueFastCaptureCounter[altarIndex] > 0)
            this.blueFastCaptureCounter[altarIndex] = 0;
    }

    private void captureAltar(int i, int team, String altarId) {
        ExtensionCommands.playSound(parentExt, room, "", "sfx_altar_locked", getAltarLocation(i));
        for (int n = 1; n < 6; n++) {
            ExtensionCommands.removeFx(this.parentExt, this.room, altarId + n);
        }
        if (i == 1) addScore(null, team, 15);
        else addScore(null, team, 10);
        cooldowns.put(altarId + "__" + "altar", 180);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                altarId,
                "fx_altar_lock",
                1000 * 60 * 3,
                "fx_altar_lock" + i,
                false,
                "Bip01",
                false,
                true,
                team);
        int altarNum;
        if (!this.practiceMap) {
            altarNum = i == 0 ? 1 : i == 1 ? 0 : i;
        } else {
            altarNum = i == 1 ? 2 : i;
        }
        ExtensionCommands.updateAltar(this.parentExt, this.room, altarNum, team, true);
        for (UserActor u : this.players) {
            if (u.getTeam() == team) {
                try {
                    if (i == 1) {
                        u.addEffect(
                                "attackDamage",
                                u.getStat("attackDamage") * 0.25d,
                                1000 * 60,
                                "altar_buff_offense",
                                "");
                        u.addEffect("spellDamage", u.getStat("spellDamage") * 0.25d, 1000 * 60);
                        Champion.handleStatusIcon(
                                parentExt, u, "icon_altar_attack", "altar2_description", 1000 * 60);
                    } else {
                        double addArmor = u.getStat("armor") * 0.5d;
                        double addMR = u.getStat("spellResist") * 0.5d;
                        if (addArmor == 0) addArmor = 5d;
                        if (addMR == 0) addMR = 5d;
                        u.addEffect("armor", addArmor, 1000 * 60, "altar_buff_defense", "");
                        u.addEffect("spellResist", addMR, 1000 * 60);
                        Champion.handleStatusIcon(
                                parentExt, u, "icon_altar_armor", "altar1_description", 1000 * 60);
                    }
                    // cooldowns.put(u.getId()+"__buff__"+buffName,60);
                    ExtensionCommands.knockOutActor(
                            parentExt, u.getUser(), altarId, u.getId(), 180);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addAltarGameScore(UserActor player, int altarIndex) {
        if (this.practiceMap) {
            player.addGameStat("score", 10);
        } else {
            if (altarIndex == 1) {
                player.addGameStat("score", 15);
            } else {
                player.addGameStat("score", 10);
            }
        }
    }

    private boolean insideAltar(Point2D pLoc, int altar) {
        double altar2_x = 0;
        double altar2_y = 0;
        if (!this.practiceMap) {
            if (altar == 0) {
                altar2_x = MapData.L2_TOP_ALTAR[0];
                altar2_y = MapData.L2_TOP_ALTAR[1];
            } else if (altar == 2) {
                altar2_x = MapData.L2_BOT_ALTAR[0];
                altar2_y = MapData.L2_BOT_ALTAR[1];
            }
        } else {
            if (altar == 2) {
                altar2_y = MapData.L1_DALTAR_Z;
            } else if (altar == 1) {
                altar2_y = MapData.L1_AALTAR_Z;
            } else return false;
        }
        double px = pLoc.getX();
        double pz = pLoc.getY();
        double dist = Math.sqrt(Math.pow(px - altar2_x, 2) + Math.pow(pz - altar2_y, 2));
        return dist <= 2;
    }

    private Point2D getAltarLocation(int altar) {
        double altar_x = 0d;
        double altar_y = 0d;
        if (!this.practiceMap) {
            if (altar == 0) {
                altar_x = MapData.L2_TOP_ALTAR[0];
                altar_y = MapData.L2_TOP_ALTAR[1];
            } else if (altar == 2) {
                altar_x = MapData.L2_BOT_ALTAR[0];
                altar_y = MapData.L2_BOT_ALTAR[1];
            }
        } else {
            altar_y = altar == 0 ? MapData.L1_AALTAR_Z : MapData.L1_DALTAR_Z;
        }
        return new Point2D.Double(altar_x, altar_y);
    }

    public void addScore(UserActor earner, int team, int points) {
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
        if (newBlueScore > newPurpleScore && blueScore <= purpleScore) {
            for (UserActor player : this.players) {
                Runnable playLeadSound =
                        () -> {
                            if (player.getTeam() == 1) {
                                ExtensionCommands.playSound(
                                        parentExt,
                                        player.getUser(),
                                        "global",
                                        "announcer/gained_point_lead",
                                        new Point2D.Float(0f, 0f));
                            } else {
                                ExtensionCommands.playSound(
                                        parentExt,
                                        player.getUser(),
                                        "global",
                                        "announcer/lost_point_lead",
                                        new Point2D.Float(0f, 0f));
                            }
                            lastPointLeadTime = System.currentTimeMillis();
                        };
                parentExt
                        .getTaskScheduler()
                        .schedule(playLeadSound, getLeadRemainingTime(), TimeUnit.MILLISECONDS);
            }
        } else if (newPurpleScore > newBlueScore && blueScore >= purpleScore) {
            for (UserActor player : this.players) {
                Runnable playLeadSound2 =
                        () -> {
                            if (player.getTeam() == 0) {
                                ExtensionCommands.playSound(
                                        parentExt,
                                        player.getUser(),
                                        "global",
                                        "announcer/gained_point_lead",
                                        new Point2D.Float(0f, 0f));
                            } else {
                                ExtensionCommands.playSound(
                                        parentExt,
                                        player.getUser(),
                                        "global",
                                        "announcer/lost_point_lead",
                                        new Point2D.Float(0f, 0f));
                            }
                            lastPointLeadTime = System.currentTimeMillis();
                        };
                parentExt
                        .getTaskScheduler()
                        .schedule(playLeadSound2, getLeadRemainingTime(), TimeUnit.MILLISECONDS);
            }
        }
        if (earner != null) {
            earner.addGameStat("score", points);
        }
    }

    private void
            handleCooldowns() { // Cooldown keys structure is id__cooldownType__value. Example for a
        // buff
        // cooldown could be lich__buff__attackDamage
        Set<String> keys = new HashSet<>(cooldowns.keySet());
        for (String key : keys) {
            String[] keyVal = key.split("__");
            String id = keyVal[0];
            String cooldown = keyVal[1];
            String value = "";
            if (keyVal.length > 2) value = keyVal[2];
            int time = cooldowns.get(key) - 1;
            if (time <= 0) {
                switch (cooldown) {
                    case "altar":
                        for (User u : room.getUserList()) {
                            int altarIndex = Integer.parseInt(id.split("_")[1]);
                            ISFSObject data = new SFSObject();
                            int altarNum = -1;
                            if (id.equalsIgnoreCase("altar_0")) altarNum = 1;
                            else if (id.equalsIgnoreCase("altar_1")) altarNum = 0;
                            else if (id.equalsIgnoreCase("altar_2")) altarNum = 2;
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

    private int getLeadRemainingTime() {
        int elapsedTime = (int) (System.currentTimeMillis() - lastPointLeadTime);
        if (elapsedTime < 5000) {
            return 5000 - elapsedTime;
        }
        return 0;
    }

    public ArrayList<UserActor> getPlayers() {
        return this.players;
    }

    public UserActor getPlayer(String id) {
        for (UserActor p : players) {
            if (p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public void addProjectile(Projectile p) {
        this.activeProjectiles.add(p);
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

    private boolean hasSuperMinion(int lane, int team) {
        for (Minion m : minions) {
            if (m.getTeam() == team
                    && m.getLane() == lane
                    && m.getType() == Minion.MinionType.SUPER
                    && m.getHealth() > 0) return true;
        }
        return false;
    }

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

    public Actor getActor(String id) {
        for (Actor a : this.getActors()) {
            if (a.getId().equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    public List<Minion> getMinions() {
        return this.minions;
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
        return this.campMonsters;
    }

    public List<Monster> getCampMonsters(String id) {
        List<Monster> returnMonsters = new ArrayList<>(3);
        String type = id.split("_")[0];
        for (Monster m : this.campMonsters) {
            if (!m.getId().equalsIgnoreCase(id) && m.getId().contains(type)) {
                returnMonsters.add(m);
            }
        }
        return returnMonsters;
    }

    public void handleSpawnDeath(Actor a) {
        // Console.debugLog("The room has killed " + a.getId());
        String mons = a.getId().split("_")[0];

        for (String s : GameManager.SPAWNS) {
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

    @Deprecated
    public void handleAssistXP(
            Actor a,
            Set<UserActor> actors,
            double xp) { // TODO: I don't think this is working ! ! ! >:(
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor user = (UserActor) a;
            user.addXP((int) xp);
        }
        for (UserActor actor : actors) {
            if (!actor.getId().equalsIgnoreCase(a.getId())
                    && actor.getActorType() == ActorType.PLAYER) {
                actor.addXP((int) Math.round(xp / 2d)); // <-- classic dividing an integer hehe haha
                // hehe --- I FIXED IT DON'T WORRY :D
            }
        }
    }

    public int getAveragePlayerLevel() {
        int combinedPlayerLevel = 0;
        for (UserActor a : this.players) {
            combinedPlayerLevel += a.getLevel();
        }
        return combinedPlayerLevel / this.players.size();
    }

    public List<Tower> getTowers() {
        return this.towers;
    }

    private boolean canSpawnSupers(int team) {
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

    public void handleFountain() {
        HashMap<Integer, Point2D> centers = getIntegerPoint2DHashMap();

        for (Map.Entry<Integer, Point2D> entry : centers.entrySet()) {
            for (UserActor ua : Champion.getUserActorsInRadius(this, entry.getValue(), 4f)) {
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

    private HashMap<Integer, Point2D> getIntegerPoint2DHashMap() {
        Point2D blueCenter;
        Point2D purpleCenter;
        float practiceBlueX = MapData.L1_GUARDIAN_X;
        float practiceBlueZ = MapData.L1_BLUE_GUARDIAN_AREA_Z;
        float practicePurpleX = MapData.L1_GUARDIAN_X * -1;
        float practicePurpleZ = MapData.L1_PURPLE_GUARDIAN_AREA_Z;

        Point2D mainPurpleCenter = new Point2D.Float(-50.16f, 0f);
        Point2D mainBlueCenter = new Point2D.Float(50.16f, 0f);
        Point2D practicePurpleCenter = new Point2D.Float(practicePurpleX, practicePurpleZ);
        Point2D practiceBlueCenter = new Point2D.Float(practiceBlueX, practiceBlueZ);

        purpleCenter = this.practiceMap ? practicePurpleCenter : mainPurpleCenter;
        blueCenter = this.practiceMap ? practiceBlueCenter : mainBlueCenter;

        HashMap<Integer, Point2D> centers = new HashMap<>();
        centers.put(0, purpleCenter);
        centers.put(1, blueCenter);
        return centers;
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

    public void gameOver(int winningTeam) {
        if (this.gameOver) return;
        try {
            this.gameOver = true;
            this.room.setProperty("state", 3);
            boolean isRankedMatch =
                    this.room.getMaxUsers() == 6 && !this.room.getName().contains("custom");
            ExtensionCommands.gameOver(
                    parentExt, this.room, this.dcPlayers, winningTeam, isRankedMatch);
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

                if (isRankedMatch) {
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
                        int eloGain = ChampionData.getEloGain(ua, this.players, win);
                        int currentElo = dataObj.get("player").get("elo").asInt();
                        for (double tierElo : ChampionData.ELO_TIERS) {
                            if (currentElo + eloGain + 1 == (int) tierElo) {
                                eloGain++;
                                break;
                            }
                        }
                        if (currentElo + eloGain < 0) eloGain = currentElo * -1;
                        if (ua.getTeam() == winningTeam) wins++;

                        int currentRankProgress = dataObj.get("player").get("rankProgress").asInt();
                        int rankIncrease = 0;
                        if (currentRankProgress >= 90) {
                            currentRankProgress = 0;
                            rankIncrease++;
                        } else currentRankProgress += 10;

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

                        if (this.room.getMaxUsers() == 6
                                && this.room.getGroupId().equalsIgnoreCase("PVP")) {
                            updateList.add(Updates.inc("player.playsPVP", 1));
                            updateList.add(
                                    Updates.set(
                                            "player.tier",
                                            ChampionData.getTier(currentElo + eloGain)));
                            updateList.add(Updates.inc("player.elo", eloGain));
                            updateList.add(Updates.inc("player.rank", rankIncrease));
                            updateList.add(Updates.set("player.rankProgress", currentRankProgress));
                            updateList.add(Updates.inc("player.winsPVP", wins));
                            updateList.add(
                                    Updates.inc(
                                            "player.points",
                                            points)); // Always zero I have no idea what this is
                            // for?;
                            updateList.add(Updates.inc("player.coins", 100));
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

    public void handlePlayerDC(User user) {
        if (this.players.size() == 1) return;
        try {
            UserActor player = this.getPlayer(String.valueOf(user.getId()));
            this.dcPlayers.put(user, player);
            player.destroy();
            MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
            Document data =
                    playerData
                            .find(eq("user.TEGid", (String) user.getSession().getProperty("tegid")))
                            .first();
            if (data != null && room.getGroupId().equalsIgnoreCase("PVP")) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode dataObj = mapper.readTree(data.toJson());
                double disconnects = dataObj.get("player").get("disconnects").asInt();
                double playsPVP = dataObj.get("player").get("playsPVP").asInt();
                double dcPercent = disconnects / playsPVP;
                double elo = dataObj.get("player").get("elo").asInt();
                double newElo = elo * (1 - dcPercent);
                switch ((int) (newElo + 1)) {
                    case 1:
                    case 100:
                    case 200:
                    case 500:
                        newElo++;
                }

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
        return this.practiceMap;
    }
}

package xyz.openatbp.extension;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;

public abstract class RoomHandler implements Runnable {
    protected ATBPExtension parentExt;
    protected Room room;
    protected ArrayList<Minion> minions;
    protected ArrayList<UserActor> players;
    protected List<Monster> campMonsters;
    protected Base[] bases = new Base[2];
    protected GumballGuardian[] guardians = new GumballGuardian[2];
    protected ArrayList<Tower> towers;
    protected boolean gameOver = false;
    protected boolean playMainMusic = false;
    protected boolean playTowerMusic = false;
    protected int mSecondsRan = 0;
    protected int secondsRan = 0;
    protected int[] altarStatus = {0, 0, 0};
    protected int[] purpleFastCaptureCounter = {0, 0, 0};
    protected int[] blueFastCaptureCounter = {0, 0, 0};
    protected HashMap<String, Integer> cooldowns = new HashMap<>();
    protected HashMap<String, Long> destroyedIds = new HashMap<>();
    protected List<String> createdActorIds = new ArrayList<>();
    protected static boolean monsterDebug = false;
    protected static boolean xpDebug = false;
    protected long lastPointLeadTime = 0;
    protected static final int FOUNTAIN_HEAL = 250; // every 0.5s
    protected int currentMinionWave = 0;
    protected ArrayList<BaseTower> baseTowers = new ArrayList<>();
    protected List<Projectile> activeProjectiles = new ArrayList<>();
    protected ScheduledFuture<?> scriptHandler;

    public RoomHandler(ATBPExtension parentExt, Room room) {
        this.parentExt = parentExt;
        this.room = room;
        this.minions = new ArrayList<>();
        this.towers = new ArrayList<>();
        this.players = new ArrayList<>();
        this.campMonsters = new ArrayList<>();
        Properties props = parentExt.getConfigProperties();
        monsterDebug = Boolean.parseBoolean(props.getProperty("monsterDebug", "false"));
        xpDebug = Boolean.parseBoolean(props.getProperty("xpDebug", "false"));
        bases[0] = new Base(parentExt, room, 0);
        bases[1] = new Base(parentExt, room, 1);
        guardians[0] = new GumballGuardian(parentExt, room, 0);
        guardians[1] = new GumballGuardian(parentExt, room, 1);
        for (User u : room.getUserList()) {
            players.add(Champion.getCharacterClass(u, parentExt));
        }
        this.campMonsters = new ArrayList<>();
        this.scriptHandler =
                parentExt
                        .getTaskScheduler()
                        .scheduleAtFixedRate(this, 100, 100, TimeUnit.MILLISECONDS);
    }

    public abstract void handleSpawns();

    public abstract void handleMinionSpawns();

    public abstract void handleAltars();

    public abstract Point2D getAltarLocation(int altar);

    public abstract int getAltarStatus(Point2D location);

    public abstract void handleAltarGameScore(int capturingTeam, int altarIndex);

    public abstract void handleHealth();

    public abstract void gameOver(int winningTeam);

    public abstract void spawnMonster(String monster);

    public abstract void handleSpawnDeath(Actor a);

    public abstract Point2D getHealthLocation(int num);

    public abstract void handlePlayerDC(User user);

    public abstract void addCompanion(Actor a);

    public abstract void removeCompanion(Actor a);

    public abstract void addProjectile(Projectile p);

    public abstract HashMap<Integer, Point2D> getFountainsCenter();

    public abstract List<Actor> getActors();

    public abstract List<Actor> getActorsInRadius(Point2D center, float radius);

    public abstract List<Actor> getEnemiesInPolygon(int team, Path2D polygon);

    public abstract List<Actor> getNonStructureEnemies(int team);

    public abstract List<Actor> getEligibleActors(
            int team,
            boolean teamFilter,
            boolean hpFilter,
            boolean towerFilter,
            boolean baseFilter);

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
                if (secondsRan == 1
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
                if (room.getUserList().isEmpty())
                    parentExt.stopScript(
                            room.getName(), true); // If no one is in the room, stop running.
                else {
                    handleAltars();
                    ExtensionCommands.updateTime(parentExt, this.room, mSecondsRan);
                }
                handleSpawns();
                handleMinionSpawns();
                handleCooldowns();

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
        if (this.room.getUserList().isEmpty()) parentExt.stopScript(this.room.getName(), true);
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
                        if (this.purpleFastCaptureCounter[i] > 0)
                            this.purpleFastCaptureCounter[i] = 0;
                        if (this.blueFastCaptureCounter[i] > 0) this.blueFastCaptureCounter[i] = 0;
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

    public void captureAltar(int i, int team, String altarId) {
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
        int altarNum = getAltarNum(i);

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

    private int getAltarNum(int i) { // TODO: probably useless for anything else
        int altarNum;
        String groupId = this.room.getGroupId();
        if (groupId.equals("PVP") || groupId.equals("PVE")) {
            altarNum = i == 0 ? 1 : i == 1 ? 0 : i;
        } else {
            altarNum = i == 1 ? 2 : i;
        }
        return altarNum;
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

    public void handleFountain() {
        HashMap<Integer, Point2D> centers = getFountainsCenter();

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

    protected void
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

    public void addMinion(int team, int minionNum, int wave, int lane) {
        Minion m = new Minion(parentExt, room, team, minionNum, wave, lane);
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

    protected boolean hasSuperMinion(int lane, int team) {
        for (Minion m : minions) {
            if (m.getTeam() == team
                    && m.getLane() == lane
                    && m.getType() == Minion.MinionType.SUPER
                    && m.getHealth() > 0) return true;
        }
        return false;
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

    protected boolean canSpawnSupers(int team) {
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
        return room.getGroupId().equals("Practice") || room.getGroupId().equals("Tutorial");
    }
}

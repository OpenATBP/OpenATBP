package xyz.openatbp.extension;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;

public class PracticeRoomHandler extends RoomHandler {

    private HashMap<User, UserActor> dcPlayers = new HashMap<>();
    private List<Actor> companions = new ArrayList<>();
    private Point2D BOT_LOCATION = new Point2D.Float(-47.5f, -4);
    private Bot finnBot;

    public PracticeRoomHandler(ATBPExtension parentExt, Room room) {
        super(parentExt, room);
        HashMap<String, Point2D> towers0 = MapData.getPTowerActorData(0);
        HashMap<String, Point2D> towers1 = MapData.getPTowerActorData(1);
        baseTowers.add(new BaseTower(parentExt, room, "purple_tower0", 0));
        baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));

        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }

        ExtensionCommands.createWorldFX(
                parentExt,
                room,
                "bot_ring",
                "fx_aggrorange_3",
                "bot_ring" + room,
                1000 * 60 * 15,
                (float) BOT_LOCATION.getX(),
                (float) BOT_LOCATION.getY(),
                false,
                1,
                0f);

        finnBot = new Bot(parentExt, room, "finn", 1, BOT_LOCATION, 1);
    }

    @Override
    public void run() {
        super.run();
        if (finnBot != null) {
            finnBot.update(mSecondsRan);
        }
    }

    @Override
    public void handleSpawns() {
        ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
        for (String s :
                GameManager.L2_SPAWNS) { // Check all mob/health spawns for how long it's been
            // since dead
            if (s.length() > 3) {
                int spawnRate = 45; // Mob spawn rate
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
                if ((this.secondsRan <= 91 && time == 90) || (this.secondsRan > 91 && time == 60)) {
                    spawnHealth(s);
                } else if ((this.secondsRan <= 91 && time < 90)
                        || (this.secondsRan > 91 && time < 60)) {
                    time++;
                    spawns.putInt(s, time);
                }
            }
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
            } else if (minionNum == 4) {
                for (int g = 0; g < 2; g++) {
                    if (!this.hasSuperMinion(0, g) && this.canSpawnSupers(g))
                        this.addMinion(g, minionNum, minionWave, 0);
                }
            }
        }
    }

    @Override
    public void handleAltars() {
        handleAltarsForMode(2);
    }

    @Override
    public Point2D getAltarLocation(int altar) {
        double altar_x = 0d;
        double altar_y;
        altar_y = altar == 0 ? MapData.L1_AALTAR_Z : MapData.L1_DALTAR_Z;
        return new Point2D.Double(altar_x, altar_y);
    }

    @Override
    public int getAltarStatus(Point2D location) {
        Point2D topAltar = new Point2D.Float(0f, MapData.L1_AALTAR_Z);
        if (location.equals(topAltar)) return this.altarStatus[0];
        else return this.altarStatus[1];
    }

    @Override
    public void handleAltarGameScore(int capturingTeam, int altarIndex) {}

    @Override
    public void handleHealth() {
        for (String s : GameManager.L2_SPAWNS) {
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

    @Override
    public void gameOver(int winningTeam) {
        if (this.gameOver) return;
        try {
            this.gameOver = true;
            this.room.setProperty("state", 3);
            ExtensionCommands.gameOver(
                    parentExt, this.room, this.dcPlayers, winningTeam, false, false);
            // logChampionData(winningTeam);
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void spawnMonster(String monster) {
        float x;
        float z;
        String actor;
        if (monster.equalsIgnoreCase("gnomes") || monster.equalsIgnoreCase("ironowls")) {
            char[] abc = {'a', 'b', 'c'};
            for (int i = 0;
                    i < 3;
                    i++) { // Gnomes and owls have three different mobs so need to be spawned in
                // triplets
                if (monster.equalsIgnoreCase("gnomes")) {
                    actor = "gnome_" + abc[i];
                    x = (float) MapData.L1_GNOMES[i].getX();
                    z = (float) MapData.L1_GNOMES[i].getY();
                } else {
                    actor = "ironowl_" + abc[i];
                    x = (float) MapData.L1_OWLS[i].getX();
                    z = (float) MapData.L1_OWLS[i].getY();
                }
                Point2D spawnLoc = new Point2D.Float(x, z);
                campMonsters.add(new Monster(parentExt, room, spawnLoc, actor));
                ExtensionCommands.createActor(
                        this.parentExt, this.room, actor, actor, spawnLoc, 0f, 2);
                ExtensionCommands.moveActor(
                        this.parentExt, this.room, actor, spawnLoc, spawnLoc, 5f, false);
            }
        }
    }

    @Override
    public void handleSpawnDeath(Actor a) {
        // Console.debugLog("The room has killed " + a.getId());
        String mons = a.getId().split("_")[0];

        for (String s : GameManager.L2_SPAWNS) {
            if (s.contains(mons)) {
                if (s.contains("gnomes") || s.contains("owls")) {
                    for (Monster m : campMonsters) {
                        if (!m.getId().equalsIgnoreCase(a.getId())
                                && m.getId().contains(mons)
                                && m.getHealth() > 0) {
                            return;
                        }
                    }
                }
                room.getVariable("spawns").getSFSObjectValue().putInt(s, 0);
                return;
            }
        }
    }

    @Override
    public Point2D getHealthLocation(int num) {
        float x;
        float z;
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
        return new Point2D.Float(x, z);
    }

    @Override
    public void handlePlayerDC(User user) {
        if (this.players.size() == 1) return;
        UserActor player = this.getPlayer(String.valueOf(user.getId()));
        this.dcPlayers.put(user, player);
        player.destroy();
        this.players.removeIf(p -> p.getId().equalsIgnoreCase(String.valueOf(user.getId())));

        int team = player.getTeam();
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
    }

    @Override
    public void addCompanion(Actor a) {
        this.companions.add(a);
    }

    @Override
    public void removeCompanion(Actor a) {
        this.companions.remove(a);
    }

    @Override
    public void addProjectile(Projectile p) {
        this.activeProjectiles.add(p);
    }

    @Override
    public HashMap<Integer, Point2D> getFountainsCenter() {
        float practiceBlueX = MapData.L1_GUARDIAN_X;
        float practiceBlueZ = MapData.L1_BLUE_GUARDIAN_AREA_Z;
        float practicePurpleX = MapData.L1_GUARDIAN_X * -1;
        float practicePurpleZ = MapData.L1_PURPLE_GUARDIAN_AREA_Z;

        Point2D purpleCenter = new Point2D.Float(practicePurpleX, practicePurpleZ);
        Point2D blueCenter = new Point2D.Float(practiceBlueX, practiceBlueZ);

        HashMap<Integer, Point2D> centers = new HashMap<>();
        centers.put(0, purpleCenter);
        centers.put(1, blueCenter);
        return centers;
    }

    @Override
    public List<Actor> getActors() {
        List<Actor> actors = new ArrayList<>();
        if (finnBot != null) actors.add(finnBot);
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
        if (finnBot != null) actorsInRadius.add(finnBot);
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
        if (finnBot != null) enemiesInPolygon.add(finnBot);
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
        if (finnBot != null) nonStructureEnemies.add(finnBot);
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
        if (finnBot != null) eligibleActors.add(finnBot);
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

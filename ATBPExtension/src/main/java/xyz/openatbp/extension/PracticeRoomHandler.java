package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.*;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.RoomGroup;
import xyz.openatbp.extension.game.actors.*;

public class PracticeRoomHandler extends RoomHandler {

    private HashMap<User, UserActor> dcPlayers = new HashMap<>();

    private Bot bot;

    public PracticeRoomHandler(
            ATBPExtension parentExt,
            Room room,
            String[] SPAWNS,
            int HP_SPAWN_RATE,
            Point2D[] mapBoundary,
            List<Point2D[]> obstacles) {
        super(parentExt, room, SPAWNS, HP_SPAWN_RATE, mapBoundary, obstacles);
        HashMap<String, Point2D> towers0 = MapData.getPTowerActorData(0);
        HashMap<String, Point2D> towers1 = MapData.getPTowerActorData(1);
        baseTowers.add(new BaseTower(parentExt, room, "purple_tower0", 0));
        baseTowers.add(new BaseTower(parentExt, room, "blue_tower3", 1));

        this.FOUNTAIN_RADIUS = 6f;

        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }

        if (room.getGroupId().equals(RoomGroup.PRACTICE.name())) {
            List<ISFSObject> botProfiles = (List<ISFSObject>) room.getProperty("botProfiles");
            if (botProfiles != null) {
                ISFSObject botProfile = botProfiles.get(0);

                int botId = botProfile.getInt("botId");

                Bot b =
                        GameModeSpawns.createSpecificBot(
                                parentExt,
                                room,
                                botId,
                                botProfile.getUtfString("name"),
                                botProfile.getUtfString("avatar"),
                                botProfile.getInt("team"),
                                botProfile.getUtfString("backpack"),
                                GameMap.CANDY_STREETS);

                if (b != null) {
                    bot = b;
                    companions.add(bot);
                    endGameChampions.put(botId, bot);
                }
            }
        }
    }

    @Override
    public void run() {
        super.run();
        try {
            if (bot != null) {
                bot.update(mSecondsRan);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleMinionSpawns() {
        int minionWave = secondsRan / 30;
        if (minionWave != this.currentMinionWave) {
            int minionNum = secondsRan % 10;
            if (minionNum == 4) this.currentMinionWave = minionWave;
            if (minionNum <= 3) {
                this.addMinion(GameMap.CANDY_STREETS, 1, minionNum, minionWave, 0);
                this.addMinion(GameMap.CANDY_STREETS, 0, minionNum, minionWave, 0);
            } else if (minionNum == 4) {
                for (int g = 0; g < 2; g++) {
                    if (!this.hasSuperMinion(0, g) && this.canSpawnSupers(g))
                        this.addMinion(GameMap.CANDY_STREETS, g, minionNum, minionWave, 0);
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
        altar_y = altar == 1 ? MapData.L1_AALTAR_Z : MapData.L1_DALTAR_Z;
        return new Point2D.Double(altar_x, altar_y);
    }

    @Override
    public int getAltarStatus(Point2D location) {
        Point2D topAltar = new Point2D.Float(0f, MapData.L1_AALTAR_Z);
        if (location.equals(topAltar)) return this.altarStatus[1];
        else return this.altarStatus[0];
    }

    @Override
    public void handleAltarGameScore(int capturingTeam, int altarIndex) {}

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

        for (String s : SPAWNS) {
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
        } else if (num == 3) {
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
}

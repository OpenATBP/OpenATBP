package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.actors.*;

public class TutorialRoomHandler extends RoomHandler {

    private static final Point2D MOVE_DESTINATION = new Point2D.Float(-49, 3);
    private static final Point2D SUPER_MINION_SPAWN = new Point2D.Float(-47, 4);
    private static final Point2D SUPER_MINION_SPAWN2 = new Point2D.Float(-44, 4.5f);
    private static final Point2D MOVE_DESTINATION2 = new Point2D.Float(-2.8f, 0.1f);
    private TutorialSuperMinion superMinion;
    private UserActor tutorialPlayer;
    private boolean playerMovedOutOfBase = false;
    private boolean basicAttackPerformed = false;
    private boolean wAbilityUsed = false;
    private boolean battleJunkLeveledUp = false;
    private boolean playerMovedToMapCenter = false;
    private boolean startCountingMinions = false;
    private int minionNum = 0;
    private List<Minion> minionsToDestroy = new ArrayList<>();
    private boolean enemyMinionsDestroyed = false;
    private TutorialBot jakeBot;

    public TutorialRoomHandler(ATBPExtension parentExt, Room room) {
        super(parentExt, room, GameManager.L1_SPAWNS, MapData.NORMAL_HP_SPAWN_RATE);
        HashMap<String, Point2D> towers0 = MapData.getPTowerActorData(0);
        HashMap<String, Point2D> towers1 = MapData.getPTowerActorData(1);
        for (String key : towers0.keySet()) {
            towers.add(new Tower(parentExt, room, key, 0, towers0.get(key)));
        }
        for (String key : towers1.keySet()) {
            towers.add(new Tower(parentExt, room, key, 1, towers1.get(key)));
        }
        ExtensionCommands.towerDown(parentExt, this.room, 0);
        ExtensionCommands.towerDown(parentExt, this.room, 3);

        tutorialPlayer = players.get(0);

        if (tutorialPlayer != null) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    tutorialPlayer.getId(),
                    "player_help",
                    5000,
                    tutorialPlayer.getId() + "questionMark",
                    true,
                    "displayBar",
                    false,
                    false,
                    tutorialPlayer.getTeam());
            tutorialPlayer.setCanMove(false);
            tutorialPlayer.setCanCast(false, false, false);
        }
    }

    @Override
    public void handleMinionSpawns() {
        if (minionNum < 4) {
            this.addMinion(0, minionNum, 1, 0);
            this.addMinion(1, minionNum, 1, 0);
            minionNum++;
        }
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
                secondsRan++;

                if (secondsRan == 8) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/tut_move1",
                            new Point2D.Float(0, 0));

                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            tutorialPlayer.getId(),
                            "fx_aggrorange_6",
                            tutorialPlayer.getId() + "moveDest",
                            1000 * 60 * 15,
                            (float) MOVE_DESTINATION.getX(),
                            (float) MOVE_DESTINATION.getY(),
                            false,
                            tutorialPlayer.getTeam(),
                            0f);

                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            tutorialPlayer.getId(),
                            "tut_arrow1",
                            tutorialPlayer.getId() + "moveArrow",
                            1000 * 60 * 15,
                            (float) MOVE_DESTINATION.getX(),
                            (float) MOVE_DESTINATION.getY(),
                            false,
                            tutorialPlayer.getTeam(),
                            0f);

                    tutorialPlayer.setCanMove(true);
                }

                if (secondsRan == 15 * 60) {
                    ISFSObject scoreObject = room.getVariable("score").getSFSObjectValue();
                    int blueScore = scoreObject.getInt("blue");
                    int purpleScore = scoreObject.getInt("purple");
                    if (blueScore > purpleScore) this.gameOver(1);
                    else if (purpleScore > blueScore) this.gameOver(0);
                    else this.gameOver(-1);
                    return;
                }
                if (room.getUserList().isEmpty()) {
                    parentExt.stopScript(
                            room.getName(), true); // If no one is in the room, stop running.
                } else {
                    handleAltars();
                    handleSpawns();
                }
                if (currentMinionWave == 1) {
                    handleMinionSpawns();
                }
                handleSpawns();
                handleCooldowns();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (tutorialPlayer.getLocation().distance(MOVE_DESTINATION) <= 1.5
                && !playerMovedOutOfBase) {
            playerMovedOutOfBase = true;
            tutorialPlayer.setCanMove(false);
            removeWayPoint();
            createCompleteStepFX();
            superMinion = new TutorialSuperMinion(parentExt, room, SUPER_MINION_SPAWN, 1);
            ExtensionCommands.playSound(
                    parentExt,
                    room,
                    "global",
                    "announcer/tut_attack_basic",
                    new Point2D.Float(0f, 0f));

            Runnable allowMoving = () -> tutorialPlayer.setCanMove(true);
            parentExt.getTaskScheduler().schedule(allowMoving, 7000, TimeUnit.MILLISECONDS);
        }

        if (superMinion != null
                && superMinion.getNum() == 1
                && superMinion.getHealth() <= 0
                && !basicAttackPerformed) {
            basicAttackPerformed = true;
            createCompleteStepFX();
            superMinion = new TutorialSuperMinion(parentExt, room, SUPER_MINION_SPAWN2, 2);
            ExtensionCommands.playSound(
                    parentExt,
                    room,
                    "global",
                    "announcer/tut_attack_spell",
                    new Point2D.Float(0, 0));

            tutorialPlayer.setCanMove(false);
            Runnable enableMoving =
                    () -> {
                        tutorialPlayer.setCanMove(true);
                        tutorialPlayer.setCanCast(false, true, false);
                    };
            parentExt.getTaskScheduler().schedule(enableMoving, 7000, TimeUnit.MILLISECONDS);
        }

        if (superMinion != null
                && superMinion.getNum() == 2
                && superMinion.getHealth() <= 0
                && !wAbilityUsed) {
            wAbilityUsed = true;
            tutorialPlayer.setCanCast(true, true, true);
            createCompleteStepFX();
            ExtensionCommands.playSound(
                    parentExt, room, "global", "announcer/tut_levelup1", new Point2D.Float(0, 0));
        }

        if (ChampionData.getTotalSpentPoints(tutorialPlayer) > 1 && !battleJunkLeveledUp) {
            battleJunkLeveledUp = true;
            this.currentMinionWave = 1;
            createCompleteStepFX();
            ExtensionCommands.playSound(
                    parentExt,
                    room,
                    "global",
                    "announcer/tut_follow_minions1",
                    new Point2D.Float(0, 0));

            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    tutorialPlayer.getId(),
                    "fx_aggrorange_6",
                    tutorialPlayer.getId() + "moveDest",
                    1000 * 60 * 15,
                    (float) MOVE_DESTINATION2.getX(),
                    (float) MOVE_DESTINATION2.getY(),
                    false,
                    tutorialPlayer.getTeam(),
                    0f);

            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    tutorialPlayer.getId(),
                    "tut_arrow1",
                    tutorialPlayer.getId() + "moveArrow",
                    1000 * 60 * 15,
                    (float) MOVE_DESTINATION2.getX(),
                    (float) MOVE_DESTINATION2.getY(),
                    false,
                    tutorialPlayer.getTeam(),
                    0f);
        }

        if (tutorialPlayer.getLocation().distance(MOVE_DESTINATION2) <= 1.5
                && !playerMovedToMapCenter) {
            playerMovedToMapCenter = true;
            removeWayPoint();
            createCompleteStepFX();
            ExtensionCommands.playSound(
                    parentExt,
                    room,
                    "global",
                    "announcer/tut_defeat_minions1",
                    new Point2D.Float(0, 0));
        }

        if (minions.size() >= 8 && !startCountingMinions) {
            startCountingMinions = true;
            minionsToDestroy =
                    minions.stream()
                            .filter(m -> m.getTeam() != tutorialPlayer.getTeam())
                            .collect(Collectors.toList());
        }

        if (startCountingMinions && !enemyMinionsDestroyed) {
            minionsToDestroy.removeIf(m -> m.getHealth() <= 0);
        }

        if (startCountingMinions && minionsToDestroy.isEmpty() && !enemyMinionsDestroyed) {
            enemyMinionsDestroyed = true;
            createCompleteStepFX();
            ExtensionCommands.playSound(
                    parentExt, room, "global", "announcer/tut_tower1", new Point2D.Float(0, 0));
        }

        if (mSecondsRan % 500 == 0) {
            handleFountain();
        }

        if (superMinion != null) {
            try {
                superMinion.update(mSecondsRan);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (jakeBot != null) {
            try {
                jakeBot.update(mSecondsRan);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                if (t.getTeam() != tutorialPlayer.getTeam() && t.getHealth() <= 0) {
                    bases[1].unlock();
                    createCompleteStepFX();
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/tut_enemy_base",
                            new Point2D.Float(0, 0));

                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            "global",
                            "announcer/tut_enemy_champ",
                            new Point2D.Float(0, 0));

                    Runnable spawnBot =
                            () -> {
                                jakeBot =
                                        new TutorialBot(
                                                parentExt, room, 1, new Point2D.Float(48, 0));
                                jakeBot.setLocation(new Point2D.Float(48, 0));
                                ExtensionCommands.snapActor(
                                        parentExt,
                                        room,
                                        jakeBot.getId(),
                                        jakeBot.getLocation(),
                                        new Point2D.Float(48, 0),
                                        true);
                            };

                    parentExt.getTaskScheduler().schedule(spawnBot, 5000, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            Console.logWarning("TOWER UPDATE EXCEPTION");
            e.printStackTrace();
        }
        towers.removeIf(t -> (t.getHealth() <= 0));

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

    private void removeWayPoint() {
        ExtensionCommands.removeFx(parentExt, room, tutorialPlayer.getId() + "moveDest");
        ExtensionCommands.removeFx(parentExt, room, tutorialPlayer.getId() + "moveArrow");
    }

    private void createCompleteStepFX() {
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                tutorialPlayer.getId(),
                "player_help",
                2000,
                tutorialPlayer.getId() + "questionMark",
                true,
                "displayBar",
                false,
                false,
                tutorialPlayer.getTeam());

        ExtensionCommands.playSound(
                parentExt,
                room,
                tutorialPlayer.getId(),
                "tut_sfx_success",
                new Point2D.Float(0, 0));
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
        return 0;
    }

    @Override
    public void handleAltarGameScore(int capturingTeam, int altarIndex) {}

    @Override
    public void gameOver(int winningTeam) {
        if (this.gameOver) return;
        try {
            this.gameOver = true;
            this.room.setProperty("state", 3);
            HashMap<User, UserActor> dcPlayers = new HashMap<>();
            boolean tutorialCoins = false;
            if (winningTeam == 0) {
                ExtensionCommands.playSound(
                        parentExt, tutorialPlayer.getUser(), "global", "announcer/tut_congrats");
                MongoCollection<Document> playerData = this.parentExt.getPlayerDatabase();
                String tegID = (String) tutorialPlayer.getUser().getSession().getProperty("tegid");
                Bson filter = eq("user.TEGid", tegID);
                Document playerDoc = playerData.find(filter).first();

                if (playerDoc != null) {
                    Document playerObject = playerDoc.get("player", Document.class);

                    if (playerObject != null) {
                        Integer winsBots = playerObject.getInteger("winsBots");
                        if (winsBots != null && winsBots == 0) {
                            tutorialCoins = true;
                            List<Bson> updateList = new ArrayList<>();
                            updateList.add(Updates.inc("player.winsBots", 1));
                            updateList.add(Updates.inc("player.coins", 700));

                            Bson updates = Updates.combine(updateList);
                            UpdateOptions options = new UpdateOptions().upsert(false);
                            UpdateResult result = playerData.updateOne(filter, updates, options);

                            Console.debugLog(result);
                        }
                    }
                }
            }
            ExtensionCommands.gameOver(
                    parentExt, room, dcPlayers, winningTeam, false, tutorialCoins);
            parentExt.stopScript(room.getName(), false);
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
    public void handleSpawnDeath(Actor a) {}

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
        UserActor player = this.getPlayer(String.valueOf(user.getId()));
        player.destroy();
        this.players.removeIf(p -> p.getId().equalsIgnoreCase(String.valueOf(user.getId())));
    }

    @Override
    public void addCompanion(Actor a) {}

    @Override
    public void removeCompanion(Actor a) {}

    @Override
    public void addProjectile(Projectile p) {}

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
        if (jakeBot != null) actors.add(jakeBot);
        if (superMinion != null) actors.add(superMinion);
        actors.addAll(towers);
        actors.addAll(minions);
        Collections.addAll(actors, bases);
        actors.addAll(players);
        actors.addAll(campMonsters);
        actors.removeIf(a -> a.getHealth() <= 0);
        return actors;
    }

    @Override
    public List<Actor> getActorsInRadius(Point2D center, float radius) {
        List<Actor> actorsInRadius = new ArrayList<>();
        if (jakeBot != null) actorsInRadius.add(jakeBot);
        if (superMinion != null) actorsInRadius.add(superMinion);
        actorsInRadius.addAll(towers);
        actorsInRadius.addAll(minions);
        Collections.addAll(actorsInRadius, bases);
        actorsInRadius.addAll(players);
        actorsInRadius.addAll(campMonsters);
        actorsInRadius.removeIf(a -> a.getHealth() <= 0);
        return actorsInRadius.stream()
                .filter(a -> a.getLocation().distance(center) <= radius)
                .collect(Collectors.toList());
    }

    @Override
    public List<Actor> getEnemiesInPolygon(int team, Path2D polygon) {
        List<Actor> enemiesInPolygon = new ArrayList<>();
        if (jakeBot != null) enemiesInPolygon.add(jakeBot);
        if (superMinion != null) enemiesInPolygon.add(superMinion);
        enemiesInPolygon.addAll(towers);
        enemiesInPolygon.addAll(minions);
        Collections.addAll(enemiesInPolygon, bases);
        enemiesInPolygon.addAll(players);
        enemiesInPolygon.addAll(campMonsters);
        enemiesInPolygon.removeIf(a -> a.getHealth() <= 0);
        return enemiesInPolygon.stream()
                .filter(a -> a.getTeam() != team)
                .filter(a -> polygon.contains(a.getLocation()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Actor> getNonStructureEnemies(int team) {
        List<Actor> nonStructureEnemies = new ArrayList<>();
        if (jakeBot != null) nonStructureEnemies.add(jakeBot);
        if (superMinion != null) nonStructureEnemies.add(superMinion);
        nonStructureEnemies.addAll(towers);
        nonStructureEnemies.addAll(minions);
        Collections.addAll(nonStructureEnemies, bases);
        nonStructureEnemies.addAll(players);
        nonStructureEnemies.addAll(campMonsters);
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
        if (jakeBot != null) eligibleActors.add(jakeBot);
        if (superMinion != null) eligibleActors.add(superMinion);
        eligibleActors.addAll(towers);
        eligibleActors.addAll(minions);
        Collections.addAll(eligibleActors, bases);
        eligibleActors.addAll(players);
        eligibleActors.addAll(campMonsters);
        return eligibleActors.stream()
                .filter(a -> !hpFilter || a.getHealth() > 0)
                .filter(a -> !teamFilter || a.getTeam() != team)
                .filter(a -> !towerFilter || a.getActorType() != ActorType.TOWER)
                .filter(a -> !baseFilter || a.getActorType() != ActorType.BASE)
                .collect(Collectors.toList());
    }
}

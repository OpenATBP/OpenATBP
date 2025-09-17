package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;

import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Monster;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;

public class AramRoomHandler extends PracticeRoomHandler {
    private Monster blueMonster;
    private Monster purpleMonster;
    private int blueMonsterTime = !monsterDebug ? 30 : 10;
    private int purpleMonsterTime = !monsterDebug ? 30 : 10; // Right
    private String[] monsters = {
        "gnome_a", "grassbear", "goomonster", "keeoth", "ironowl_a", "hugwolf"
    };

    public AramRoomHandler(ATBPExtension parentExt, Room room) {
        super(parentExt, room, GameManager.ARAM_SPAWNS, MapData.ARAM_HP_SPAWN_RATE);
        Console.debugLog("Aram room handler activated!");
    }

    @Override
    public void run() {
        try {
            super.run();
            if (mSecondsRan % 1000 == 0) {
                blueMonsterTime--;
                purpleMonsterTime--;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void handleSpawns() {
        try {
            if (blueMonsterTime <= 0 && blueMonster == null) {
                blueMonster = summonMonster(monsters[(int) (Math.random() * monsters.length)], 1);
                blueMonsterTime = !monsterDebug ? 120 : 20;
            } else if (blueMonster != null
                    && blueMonsterTime <= 0
                    && blueMonster.getHealth() == blueMonster.getMaxHealth()) {
                blueMonster.die(blueMonster);
            }
            if (purpleMonsterTime <= 0 && purpleMonster == null) {
                purpleMonster = summonMonster(monsters[(int) (Math.random() * monsters.length)], 0);
                purpleMonsterTime = !monsterDebug ? 120 : 20;
            } else if (purpleMonster != null
                    && purpleMonsterTime <= 0
                    && purpleMonster.getHealth() == purpleMonster.getMaxHealth()) {
                purpleMonster.die(purpleMonster);
            }
            ISFSObject spawns = room.getVariable("spawns").getSFSObjectValue();
            for (String s : SPAWNS) {
                handleHealthPackSpawns(spawns, s);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private Monster summonMonster(String s, int team) {
        try {
            Monster m;
            switch (s) {
                case "keeoth":
                    m =
                            new Keeoth(
                                    this.parentExt,
                                    this.room,
                                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                                    s,
                                    s + team);
                    break;
                case "goomonster":
                    m =
                            new GooMonster(
                                    this.parentExt,
                                    this.room,
                                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                                    s,
                                    s + team);
                    break;
                default:
                    m =
                            new Monster(
                                    this.parentExt,
                                    this.room,
                                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                                    s,
                                    s + team);
            }
            this.campMonsters.add(m);
            ExtensionCommands.createActor(
                    this.parentExt,
                    this.room,
                    s + team,
                    s,
                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                    0f,
                    2);
            ExtensionCommands.moveActor(
                    this.parentExt,
                    this.room,
                    s + team,
                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                    team == 0 ? MapData.L1_OWLS[1] : MapData.L1_GNOMES[1],
                    5f,
                    false);
            return m;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    @Override
    public void handleSpawnDeath(Actor a) {
        try {
            int spawnTime = !monsterDebug ? 30 : 10;
            if (a.getAvatar().equalsIgnoreCase("keeoth")) spawnTime += 30;
            else if (a.getAvatar().equalsIgnoreCase("goomonster")) spawnTime += 15;
            if (a == blueMonster) {
                blueMonster = null;
                blueMonsterTime = spawnTime;
            } else if (a == purpleMonster) {
                purpleMonster = null;
                purpleMonsterTime = spawnTime;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

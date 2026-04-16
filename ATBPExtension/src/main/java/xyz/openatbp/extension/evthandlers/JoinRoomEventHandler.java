package xyz.openatbp.extension.evthandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.game.RoomGroup;

public class JoinRoomEventHandler extends BaseServerEventHandler {

    private static final int BOT_ID_START = 10000;
    private final Random rand = new Random();
    private final String[] botChampions = {"finn", "iceking", "jake", "lemongrab"};
    private final String[] botDisplayNames = {
        "FINN BOT", "ICE KING BOT", "JAKE BOT", "LEMONGRAB BOT"
    };

    @Override
    public void handleServerEvent(ISFSEvent event) { // Initialize everything
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User sender = (User) event.getParameter(SFSEventParam.USER);
        Console.debugLog(sender.getName() + " has joined room!");
        sender.setProperty("joined", true);
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        int maxPlayers = room.getMaxUsers();
        if (GameManager.playersLoaded(users, maxPlayers) && (int) room.getProperty("state") == 0) {
            // GENERATE BOTS
            if (room.getGroupId().equals(RoomGroup.PVB.name())) {
                createBotProfiles(parentExt, room, 3, rand);
            }

            if (room.getGroupId().equals(RoomGroup.PRACTICE.name())) {
                createBotProfiles(parentExt, room, 1, rand);
            }

            // If all players have loaded into the room
            room.setProperty("state", 1);
            GameManager.addPlayer(room, parentExt); // Add users to the game
            GameManager.loadPlayers(room, parentExt); // Load the players into the map
        }
    }

    private void createBotProfiles(ATBPExtension parentExt, Room room, int botsNum, Random rand) {
        List<ISFSObject> botProfiles = new ArrayList<>();
        List<String> botAvatars = new ArrayList<>();

        for (int i = 0; i < botsNum; i++) {
            int index;
            do {
                index = rand.nextInt(botChampions.length);
            } while (botAvatars.contains(botChampions[index]));

            botAvatars.add(botChampions[index]);

            int botId = BOT_ID_START + i;
            String name = botDisplayNames[index];
            String champion = botChampions[index];
            int team = 1;
            String tegId = "bot_" + champion + "_" + botId;
            String backpack = "belt_champions";

            ISFSObject botProfile = new SFSObject();
            botProfile.putInt("botId", botId);
            botProfile.putUtfString("tegId", tegId);
            botProfile.putUtfString("name", name);
            botProfile.putUtfString("avatar", champion);
            botProfile.putInt("team", team);
            botProfile.putUtfString("backpack", backpack);
            botProfiles.add(botProfile);

            ExtensionCommands.addUser(
                    parentExt, room, botId, name, champion, team, tegId, backpack, 0, false);
        }

        room.setProperty("botProfiles", botProfiles);
    }
}

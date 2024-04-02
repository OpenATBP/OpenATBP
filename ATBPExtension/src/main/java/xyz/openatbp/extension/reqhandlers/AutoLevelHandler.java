package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.UserActor;

public class AutoLevelHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        UserActor ua =
                parentExt
                        .getRoomHandler(sender.getLastJoinedRoom().getId())
                        .getPlayer(String.valueOf(sender.getId()));
        sender.getVariable("champion")
                .getSFSObjectValue()
                .putBool("autoLevel", params.getBool("enabled"));
        int availableSpellPoints = (int) ua.getStat("availableSpellPoints");
        if (params.getBool("enabled") && availableSpellPoints > 0) {
            int level = ua.getLevel();
            int startPoint = level - availableSpellPoints;
            int[] buildPath = ChampionData.getBuildPath(ua.getAvatar(), ua.getBackpack());
            for (int i = 0; i < availableSpellPoints; i++) {
                int category = buildPath[startPoint + i];
                int categoryPoints = (int) ua.getStat("sp_category" + category);
                int spentPoints =
                        ChampionData.getTotalSpentPoints(ua); // How many points have been used
                boolean works = false;
                if (categoryPoints + 1 < 3) works = true;
                else if (categoryPoints + 1 == 3)
                    works =
                            spentPoints + 1
                                    >= 4; // Can't get a third level without spending 4 points
                else if (categoryPoints + 1 == 4)
                    works =
                            spentPoints + 1
                                    >= 6; // Can't get a fourth level without spending 6 points
                if (works) {
                    ExtensionCommands.updateActorData(
                            parentExt,
                            sender.getLastJoinedRoom(),
                            ChampionData.useSpellPoint(sender, "category" + category, parentExt));
                } else {
                    for (int j : buildPath) {
                        category = j;
                        if (categoryPoints + 1 < 3) works = true;
                        else if (categoryPoints + 1 == 3)
                            works =
                                    spentPoints + 1
                                            >= 4; // Can't get a third level without spending 4
                        // points
                        else if (categoryPoints + 1 == 4)
                            works =
                                    spentPoints + 1
                                            >= 6; // Can't get a fourth level without spending 6
                        // points
                        if (works) {
                            ExtensionCommands.updateActorData(
                                    parentExt,
                                    sender.getLastJoinedRoom(),
                                    ChampionData.useSpellPoint(
                                            sender, "category" + category, parentExt));
                            break;
                        }
                    }
                }
            }
        }
    }
}

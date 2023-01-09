package xyz.openatbp.extension.reqhandlers;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ChampionData;
import xyz.openatbp.extension.ExtensionCommands;

public class AutoLevelHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(User sender, ISFSObject params) {
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        sender.getVariable("champion").getSFSObjectValue().putBool("autoLevel",params.getBool("enabled"));
        int availableSpellPoints = sender.getVariable("stats").getSFSObjectValue().getInt("availableSpellPoints");
        if(params.getBool("enabled") && availableSpellPoints>0){
            System.out.println("Points existing!");
            System.out.println("Backpack: " + sender.getVariable("player").getSFSObjectValue().getUtfString("backpack"));
            int level = sender.getVariable("stats").getSFSObjectValue().getInt("level");
            int startPoint = level-availableSpellPoints;
            int[] buildPath = ChampionData.getBuildPath(sender.getVariable("player").getSFSObjectValue().getUtfString("avatar"),sender.getVariable("player").getSFSObjectValue().getUtfString("backpack"));
            System.out.println("BuildPath: " + buildPath.toString());
            System.out.println("Starting point!: " + startPoint);
            for(int i = 0; i < availableSpellPoints; i++){
                System.out.println("Index: " + (startPoint+i));
                int category = buildPath[startPoint+i];
                int categoryPoints = sender.getVariable("stats").getSFSObjectValue().getInt("sp_category"+category);
                int spentPoints = ChampionData.getTotalSpentPoints(sender); //How many points have been used
                boolean works = false;
                if(categoryPoints+1 < 3) works = true;
                else if(categoryPoints+1 == 3) works = spentPoints+1>=4; //Can't get a third level without spending 4 points
                else if(categoryPoints+1 == 4) works = spentPoints+1>=6; //Can't get a fourth level without spending 6 points
                if(works){
                    System.out.println("Works!");
                    ExtensionCommands.updateActorData(parentExt,sender,ChampionData.useSpellPoint(sender,"category"+category,parentExt));
                }else{
                    System.out.println("not working!");
                    for(int g = 0; i < buildPath.length; g++){
                        category = buildPath[g];
                        if(categoryPoints+1 < 3) works = true;
                        else if(categoryPoints+1 == 3) works = spentPoints+1>=4; //Can't get a third level without spending 4 points
                        else if(categoryPoints+1 == 4) works = spentPoints+1>=6; //Can't get a fourth level without spending 6 points
                        if(works){
                            ExtensionCommands.updateActorData(parentExt,sender,ChampionData.useSpellPoint(sender,"category"+category,parentExt));
                            break;
                        }
                    }
                }
            }
        }
    }
}

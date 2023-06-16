package xyz.openatbp.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.game.champions.UserActor;

import java.util.ArrayList;

//TODO: More clearly separate this from Champion.class and make functions void (or move into UserActor functions)

public class ChampionData {

    private static final int[] XP_LEVELS = {100,200,300,400,500,600,700,800,900};

    public static int getXPLevel(int xp){
        for(int i = 0; i < XP_LEVELS.length; i++){
            if(xp < XP_LEVELS[i]) return i+1;
        }
        return -1;
    }
    public static ISFSObject updateChampionStat(User user, String stat, int value){
        int currentStat = user.getVariable("stats").getSFSObjectValue().getInt(stat);
        user.getVariable("stats").getSFSObjectValue().putInt(stat,currentStat+value);
        return user.getVariable("stats").getSFSObjectValue();
    }

    public static ISFSObject setChampionStat(User user, String stat, int value){
        user.getVariable("stats").getSFSObjectValue().putInt(stat,value);
        return user.getVariable("stats").getSFSObjectValue();
    }

    public static ISFSObject addXP(UserActor a, int xp, ATBPExtension parentExt){
        User user = a.getUser();
        int level = user.getVariable("stats").getSFSObjectValue().getInt("level");
        int currentXp = user.getVariable("stats").getSFSObjectValue().getInt("xp")+xp;
        if(hasLeveledUp(user,xp)){
            level++;
            levelUpCharacter(parentExt, user);
        }
        double levelCap2 = XP_LEVELS[level-1];
        double newXP = levelCap2-currentXp;
        double pLevel = (100-newXP)/100;
        user.getVariable("stats").getSFSObjectValue().putInt("level",level);
        user.getVariable("stats").getSFSObjectValue().putDouble("pLevel",pLevel);
        user.getVariable("stats").getSFSObjectValue().putInt("xp",currentXp);
        ISFSObject toUpdate = new SFSObject();
        toUpdate.putInt("level",level);
        toUpdate.putDouble("pLevel",pLevel);
        toUpdate.putInt("xp",currentXp);
        toUpdate.putUtfString("id", String.valueOf(user.getId()));
        return toUpdate;
    }

    public static boolean hasLeveledUp(User user, int xp){
        int level = user.getVariable("stats").getSFSObjectValue().getInt("level");
        int currentXp = user.getVariable("stats").getSFSObjectValue().getInt("xp")+xp;
        int levelCap = XP_LEVELS[level-1];
        return currentXp>=levelCap;
    }

    public static ISFSObject useSpellPoint(User user, String category, ATBPExtension parentExt){ //TODO: Switch to using UserActor stats
        ISFSObject toUpdate = new SFSObject();
        ISFSObject stats = user.getVariable("stats").getSFSObjectValue();
        int spellPoints = user.getVariable("stats").getSFSObjectValue().getInt("availableSpellPoints");
        int categoryPoints = user.getVariable("stats").getSFSObjectValue().getInt("sp_"+category);
        int spentPoints = getTotalSpentPoints(user); //How many points have been used
        boolean works = false;
        if(spellPoints>0){
            if(categoryPoints+1 < 3) works = true;
            else if(categoryPoints+1 == 3) works = spentPoints+1>=4; //Can't get a third level without spending 4 points
            else if(categoryPoints+1 == 4) works = spentPoints+1>=6; //Can't get a fourth level without spending 6 points
            else System.out.println("Failed everything!");
        }else{
            System.out.println("Not enough skill points!");
        }
        if(works){
            spellPoints--;
            categoryPoints++;
            String backpack = user.getVariable("player").getSFSObjectValue().getUtfString("backpack");
            String[] inventory = getBackpackInventory(parentExt, backpack);
            int cat = Integer.parseInt(String.valueOf(category.charAt(category.length()-1))); //Gets category by looking at last number in the string
            ArrayNode itemStats = getItemStats(parentExt,inventory[cat-1]);
            for(JsonNode stat : getItemPointVal(itemStats,categoryPoints)){
                if(stat.get("point").asInt() == categoryPoints){
                    double currentStat = user.getVariable("stats").getSFSObjectValue().getDouble(stat.get("stat").asText());
                    int packStat = stat.get("value").asInt();
                    if(stat.get("stat").asText().equalsIgnoreCase("health")){ //Health is tracked through 4 stats (health, currentHealth, maxHealth, and pHealth)
                        UserActor ua = parentExt.getRoomHandler(user.getLastJoinedRoom().getId()).getPlayer(String.valueOf(user.getId()));
                        int maxHealth = ua.getMaxHealth();
                        double pHealth = ua.getPHealth();
                        if(pHealth>1) pHealth=1;
                        maxHealth+=packStat;
                        int currentHealth = (int) Math.floor(pHealth*maxHealth);
                        ua.setHealth(currentHealth,maxHealth);
                        /*
                        stats.putInt("currentHealth",currentHealth);
                        stats.putInt("maxHealth",maxHealth);
                        toUpdate.putInt("currentHealth",currentHealth);
                        toUpdate.putInt("maxHealth",maxHealth);
                        toUpdate.putDouble("pHealth",pHealth);
                        stats.putDouble(stat.get("stat").asText(),maxHealth);
                        toUpdate.putDouble(stat.get("stat").asText(),maxHealth);

                         */
                    }else{
                        user.getVariable("stats").getSFSObjectValue().putDouble(stat.get("stat").asText(),currentStat+packStat);
                        toUpdate.putDouble(stat.get("stat").asText(),currentStat+packStat);
                    }
                }
            }
            user.getVariable("stats").getSFSObjectValue().putInt("availableSpellPoints",spellPoints);
            user.getVariable("stats").getSFSObjectValue().putInt("sp_"+category,categoryPoints);
            toUpdate.putInt("sp_"+category,categoryPoints);
            toUpdate.putInt("availableSpellPoints",spellPoints);
            toUpdate.putUtfString("id", String.valueOf(user.getId()));
            return toUpdate;
        }else{
            System.out.println("Failed!: " + category);
        }
        return null;
    }

    public static int getTotalSpentPoints(User u){
        int totalUsedPoints = 0;
        ISFSObject stats = u.getVariable("stats").getSFSObjectValue();
        for(int i = 0; i < 5; i++){
            totalUsedPoints+=stats.getInt("sp_category"+(i+1));
        }
        return totalUsedPoints;
    }

    public static ISFSObject resetSpellPoints(User user, ATBPExtension parentExt){
        ISFSObject toUpdate = new SFSObject();
        ISFSObject stats = user.getVariable("stats").getSFSObjectValue();
        int spellPoints = user.getVariable("stats").getSFSObjectValue().getInt("availableSpellPoints");
        int newPoints = 0;
        for(int i = 0; i < 5; i++){
            int categoryPoints = user.getVariable("stats").getSFSObjectValue().getInt("sp_category"+(i+1));
            newPoints+=categoryPoints;
            user.getVariable("stats").getSFSObjectValue().putInt("sp_category"+(i+1),0);
            toUpdate.putInt("sp_category"+(i+1),0);
            String backpack = user.getVariable("player").getSFSObjectValue().getUtfString("backpack");
            String[] inventory = getBackpackInventory(parentExt, backpack);
            ArrayNode itemStats = getItemStats(parentExt,inventory[i]);
            for(JsonNode stat : getItemPointVal(itemStats,categoryPoints)){
                if(stat.get("point").asInt() >= categoryPoints){
                    double currentStat = user.getVariable("stats").getSFSObjectValue().getDouble(stat.get("stat").asText());
                    int packStat = stat.get("value").asInt();
                    if(stat.get("stat").asText().equalsIgnoreCase("health")){
                        int maxHealth = stats.getInt("maxHealth");
                        double pHealth = stats.getDouble("pHealth");
                        if(pHealth>1) pHealth=1;
                        maxHealth-=packStat;
                        int currentHealth = (int) Math.floor(pHealth*maxHealth);
                        stats.putInt("currentHealth",currentHealth);
                        stats.putInt("maxHealth",maxHealth);
                        toUpdate.putInt("currentHealth",currentHealth);
                        toUpdate.putInt("maxHealth",maxHealth);
                        toUpdate.putDouble("pHealth",pHealth);
                        stats.putDouble(stat.get("stat").asText(),maxHealth);
                        toUpdate.putDouble(stat.get("stat").asText(),maxHealth);
                    }
                    user.getVariable("stats").getSFSObjectValue().putDouble(stat.get("stat").asText(),currentStat-packStat);
                    toUpdate.putDouble(stat.get("stat").asText(), currentStat-packStat);
                }
            }
        }
        if(spellPoints+newPoints>1) spellPoints--;
        spellPoints+=newPoints;
        user.getVariable("stats").getSFSObjectValue().putInt("availableSpellPoints",spellPoints);
        toUpdate.putInt("availableSpellPoints",spellPoints);
        toUpdate.putUtfString("id", String.valueOf(user.getId()));
        return toUpdate;
    }

    public static String[] getBackpackInventory(ATBPExtension parentExt,String backpack){
        JsonNode pack = parentExt.getDefintion(backpack).get("junk");
        System.out.println(backpack);
        System.out.println(pack.toString());
        String[] itemNames = new String[5];
        for(int i = 0; i < 5; i++){
            itemNames[i] = pack.get("slot"+(i+1)).get("junk_id").asText();
        }
        return itemNames;
    }

    public static ArrayNode getItemStats(ATBPExtension parentExt, String item){
        System.out.println(item);
        JsonNode itemObj = parentExt.itemDefinitions.get(item).get("junk").get("mods");
        ArrayNode mods = (ArrayNode) itemObj.get("mod");
        return mods;
    }

    private static ArrayList<JsonNode> getItemPointVal(ArrayNode mods, int category){
        ArrayList<JsonNode> stats = new ArrayList<>();
        for(JsonNode m : mods){
            if(m.get("point").asInt() == category){
                stats.add(m);
            }else if(m.get("point").asInt() > category) break;
        }
        return stats;
    }

    public static void levelUpCharacter(ATBPExtension parentExt, User user){
        ISFSObject stats = user.getVariable("stats").getSFSObjectValue();
        ISFSObject toUpdate = new SFSObject();
        int level = stats.getInt("level");
        for(String k : stats.getKeys()){
            if(k.contains("PerLevel")){
                double levelStat = stats.getDouble(k)*level;
                double currentStat = stats.getDouble(k.replace("PerLevel",""));
                if(k.contains("health")){
                    int maxHealth = stats.getInt("maxHealth");
                    double pHealth = stats.getDouble("pHealth");
                    if(pHealth>1) pHealth=1;
                    maxHealth+=levelStat;
                    int currentHealth = (int) Math.floor(pHealth*maxHealth);
                    stats.putInt("currentHealth",currentHealth);
                    stats.putInt("maxHealth",maxHealth);
                    toUpdate.putInt("currentHealth",currentHealth);
                    toUpdate.putInt("maxHealth",maxHealth);
                    toUpdate.putDouble("pHealth",pHealth);
                    stats.putDouble(k.replace("PerLevel",""),maxHealth);
                    toUpdate.putDouble(k.replace("PerLevel",""),maxHealth);
                }else{
                    stats.putDouble(k.replace("PerLevel",""),levelStat+currentStat);
                    toUpdate.putDouble(k.replace("PerLevel",""),levelStat+currentStat);
                }
            }
        }
        int spellPoints = stats.getInt("availableSpellPoints")+1;
        stats.putInt("availableSpellPoints",spellPoints);
        toUpdate.putUtfString("id", String.valueOf(user.getId()));
        if(user.getVariable("champion").getSFSObjectValue().getBool("autoLevel")){
            level++;
            int[] buildPath = getBuildPath(user.getVariable("player").getSFSObjectValue().getUtfString("avatar"),user.getVariable("player").getSFSObjectValue().getUtfString("backpack"));
            int category = buildPath[level-1];
            int categoryPoints = user.getVariable("stats").getSFSObjectValue().getInt("sp_category"+category);
            int spentPoints = getTotalSpentPoints(user); //How many points have been used
            boolean works = false;
            if(categoryPoints+1 < 3) works = true;
            else if(categoryPoints+1 == 3) works = spentPoints+1>=4; //Can't get a third level without spending 4 points
            else if(categoryPoints+1 == 4) works = spentPoints+1>=6; //Can't get a fourth level without spending 6 points
            if(works){
                System.out.println("Auto Leveling!");
                ExtensionCommands.updateActorData(parentExt,user,useSpellPoint(user,"category"+category,parentExt));
            }else{
                System.out.println("CategoryPoints: " + categoryPoints);
                System.out.println("SpentPoints: " + spentPoints);
                for(int i = 0; i < buildPath.length; i++){
                    category = buildPath[i];
                    if(categoryPoints+1 < 3) works = true;
                    else if(categoryPoints+1 == 3) works = spentPoints+1>=4; //Can't get a third level without spending 4 points
                    else if(categoryPoints+1 == 4) works = spentPoints+1>=6; //Can't get a fourth level without spending 6 points
                    if(works){
                        ExtensionCommands.updateActorData(parentExt,user,useSpellPoint(user,"category"+category,parentExt));
                        break;
                    }
                }
            }
        }else{
            toUpdate.putInt("availableSpellPoints",spellPoints);
        }
        ExtensionCommands.updateActorData(parentExt,user,toUpdate);
    }

    public static int[] getBuildPath(String actor, String backpack){
        int[] buildPath = {1,1,2,2,1,1,2,2,5,5};
        String avatar = "";
        if(actor.contains("skin")){
            avatar = actor.split("_")[0];
        }
        switch(avatar){
            case "billy":
            case "cinnamonbun":
                if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2, 2, 3, 3, 2, 2, 3, 3, 4, 4};
                }else if(backpack.equalsIgnoreCase("belt_sorcerous_satchel")){
                    buildPath = new int[]{2,2,4,4,2,2,4,4,3,3};
                }
                break;
            case "bmo":
                if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1, 1, 3, 3, 1, 1, 3, 3, 2, 2};
                }
                break;
            case "finn":
                if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }
                break;
            case "fionna":
                if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bindle_of_bravery")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,4,4};
                }else if(backpack.equalsIgnoreCase("belt_fridjitsu")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }
                break;
            case "flame":
                if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,2,2};
                }
                break;
            case "gunter":
                if(backpack.equalsIgnoreCase("belt_fridjitsu")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,2,2};
                }else if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,2,2};
                }
                break;
            case "iceking":
                if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_fridjitsu")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,2,2};
                }
                break;
            case "jake":
                if(backpack.equalsIgnoreCase("belt_sorcerous_satchel")){
                    buildPath = new int[]{2,2,4,4,2,2,4,4,3,3};
                }else if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }
                break;
            case "lemongrab":
                if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }
                else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_candy_monarch")){
                    buildPath = new int[]{2,2,4,4,2,2,4,4,3,3};
                }else if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,5,5};
                }
                break;
            case "lich":
                if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }
                break;
            case "lsp":
                if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }
                else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }
                break;
            case "magicman":
                if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,2,2};
                }
                break;
            case "marceline":
                if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }else if(backpack.equalsIgnoreCase("belt_sorcerous_satchel")){
                    buildPath = new int[]{2,2,4,4,2,2,4,4,3,3};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }
                break;
            case "neptr":
                if(backpack.equalsIgnoreCase("belt_ultimate_wizard")){
                    buildPath = new int[]{2,2,3,3,2,2,3,3,4,4};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_sorcerous_satchel")){
                    buildPath = new int[]{2,2,4,4,2,2,4,4,3,3};
                }
                break;
            case "peppermintbutler":
                if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,5,5};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_candy_monarch")){
                    buildPath = new int[]{1,1,4,4,1,1,4,4,2,2};
                }else if(backpack.equalsIgnoreCase("belt_fridjitsu")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }
                break;
            case "princessbubblegum":
                if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }
                break;
            case "rattleballs":
                if(backpack.equalsIgnoreCase("belt_techno_tank")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_bella_noche")){
                    buildPath = new int[]{1,1,3,3,1,1,3,3,2,2};
                }else if(backpack.equalsIgnoreCase("belt_billys_bag")){
                    buildPath = new int[]{1,1,5,5,1,1,5,5,3,3};
                }else if(backpack.equalsIgnoreCase("belt_champions")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_candy_monarch")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }else if(backpack.equalsIgnoreCase("belt_hewers_haversack")){
                    buildPath = new int[]{1,1,2,2,1,1,2,2,3,3};
                }
                break;
        }
        return buildPath;
    }
}

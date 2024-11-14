package xyz.openatbp.extension;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.game.actors.UserActor;

// TODO: More clearly separate this from Champion.class
//  and make functions void (or move into UserActor functions)

public class ChampionData {

    private static final int[] XP_LEVELS = {100, 210, 350, 520, 720, 950, 1210, 1500, 1820, 2170};
    public static final double[] ELO_TIERS = {0, 1149, 1350, 1602};
    public static final double MAX_ELO = 2643;

    public static int getXPLevels(int index) {
        if (index < 0) return XP_LEVELS[0];
        else if (index >= XP_LEVELS.length) return XP_LEVELS[XP_LEVELS.length - 1];
        else return XP_LEVELS[index];
    }

    public static int getXPLevel(int xp) {
        for (int i = 0; i < XP_LEVELS.length; i++) {
            if (xp < XP_LEVELS[i]) return i + 1;
        }
        return -1;
    }

    public static int getLevelXP(int level) {
        if (level == 0) return 0;
        return XP_LEVELS[level - 1];
    }

    public static int getJunkLevel(UserActor ua, String item) {
        String[] items = ChampionData.getBackpackInventory(ua.getParentExt(), ua.getBackpack());
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(item)) {
                int cat = i + 1;
                return (int) ua.getStat("sp_category" + cat);
            }
        }
        return -1;
    }

    public static double getCustomJunkStat(UserActor ua, String item) {
        int level = getJunkLevel(ua, item);
        if (level > 0) {
            double[] stats = new double[4];
            switch (item) {
                case "junk_3_battle_moon":
                case "junk_5_ghost_pouch":
                    stats = new double[] {0.03, 0.05, 0.1, 0.15};
                    break;
                case "junk_4_future_crystal":
                    stats = new double[] {0.25, 0.4, 0.5, 0.75};
                    break;
                case "junk_1_numb_chucks":
                    stats = new double[] {-0.1, -0.15, -0.2, -0.3};
                    break;
                case "junk_4_antimagic_cube":
                    stats = new double[] {-25, -50, -75, -100};
                    break;
                case "junk_2_peppermint_tank":
                    stats = new double[] {0.05, 0.1, 0.15, 0.25};
                    break;
                case "junk_4_flame_cloak":
                    stats = new double[] {0.05, 0.07, 0.1, 0.1};
                    break;
                case "junk_1_demon_blood_sword":
                case "junk_1_grass_sword":
                    stats = new double[] {0.15, 0.2, 0.25, 0.3};
                    break;
                case "junk_2_electrode_gun":
                    stats = new double[] {0.25, 0.3, 0.4, 0.5};
                    break;
                case "junk_2_simon_petrikovs_glasses":
                    stats = new double[] {50d, 75d, 100d, 150d};
                    break;
            }
            return stats[level - 1];
        }
        return -1;
    }

    public static ISFSObject useSpellPoint(
            User user,
            String category,
            ATBPExtension parentExt) { // TODO: Switch to using UserActor stats
        ISFSObject toUpdate = new SFSObject();
        // Console.debugLog("Using spell point!");
        UserActor ua =
                parentExt
                        .getRoomHandler(user.getLastJoinedRoom().getName())
                        .getPlayer(String.valueOf(user.getId()));
        int spellPoints = (int) ua.getStat("availableSpellPoints");
        int categoryPoints = (int) ua.getStat("sp_" + category);
        int spentPoints = getTotalSpentPoints(ua); // How many points have been used
        boolean works = false;
        if (spellPoints > 0) {
            if (categoryPoints + 1 < 3) works = true;
            else if (categoryPoints + 1 == 3)
                works = spentPoints >= 4; // Can't get a third level without spending 4 points
            else if (categoryPoints + 1 == 4)
                works = spentPoints >= 6; // Can't get a fourth level without spending 6 points
        }
        if (works) {
            spellPoints--;
            categoryPoints++;
            String backpack = ua.getBackpack();
            String[] inventory = getBackpackInventory(parentExt, backpack);
            int cat =
                    Integer.parseInt(
                            String.valueOf(
                                    category.charAt(
                                            category.length()
                                                    - 1))); // Gets category by looking at last
            // number in the string
            ArrayNode itemStats = getItemStats(parentExt, inventory[cat - 1]);
            Map<String, Double> previousValues = new HashMap<>();
            for (JsonNode stat : getItemPointVal(itemStats, categoryPoints)) {
                if (stat.get("point").asInt() == categoryPoints - 1) {
                    previousValues.put(stat.get("stat").asText(), stat.get("value").asDouble());
                }
                if (stat.get("point").asInt() == categoryPoints) {
                    double previousValue = 0;
                    if (previousValues.containsKey(stat.get("stat").asText())) {
                        previousValue = previousValues.get(stat.get("stat").asText());
                    }
                    double packStat = stat.get("value").asDouble() - previousValue;
                    if (stat.get("stat")
                            .asText()
                            .equalsIgnoreCase(
                                    "health")) { // Health is tracked through 4 stats (health,
                        // currentHealth,
                        // maxHealth, and pHealth)
                        int maxHealth = ua.getMaxHealth();
                        double pHealth = ua.getPHealth();
                        if (pHealth > 1) pHealth = 1;
                        maxHealth += packStat;
                        int currentHealth = (int) Math.floor(pHealth * maxHealth);
                        ua.setHealth(currentHealth, maxHealth);
                        /*
                        stats.putInt("currentHealth",currentHealth);
                        stats.putInt("maxHealth",maxHealth);
                        toUpdate.putInt("currentHealth",currentHealth);
                        toUpdate.putInt("maxHealth",maxHealth);
                        toUpdate.putDouble("pHealth",pHealth);
                        stats.putDouble(stat.get("stat").asText(),maxHealth);
                        toUpdate.putDouble(stat.get("stat").asText(),maxHealth);

                         */
                    } else if (stat.get("stat").asText().equalsIgnoreCase("attackRange")) {
                        packStat = stat.get("value").asDouble();
                        double defaultAttackRange =
                                parentExt
                                        .getActorStats(ua.getAvatar())
                                        .get("attackRange")
                                        .asDouble();
                        ua.setStat(stat.get("stat").asText(), defaultAttackRange * packStat);
                    } else {
                        ua.increaseStat(stat.get("stat").asText(), packStat);
                    }
                }
            }
            ua.setStat("availableSpellPoints", spellPoints);
            ua.setStat("sp_" + category, categoryPoints);
            toUpdate.putInt("sp_" + category, categoryPoints);
            toUpdate.putInt("availableSpellPoints", spellPoints);
            toUpdate.putUtfString("id", String.valueOf(user.getId()));
            return toUpdate;
        }
        return null;
    }

    public static int getTotalSpentPoints(UserActor ua) {
        int totalUsedPoints = 0;
        for (int i = 0; i < 5; i++) {
            totalUsedPoints += ua.getStat("sp_category" + (i + 1));
        }
        return totalUsedPoints;
    }

    public static ISFSObject resetSpellPoints(User user, ATBPExtension parentExt) {
        UserActor ua =
                parentExt
                        .getRoomHandler(user.getLastJoinedRoom().getName())
                        .getPlayer(String.valueOf(user.getId()));
        ISFSObject toUpdate = new SFSObject();
        int spellPoints = (int) ua.getStat("availableSpellPoints");
        int newPoints = 0;
        for (int i = 0; i < 5; i++) {
            int categoryPoints = (int) ua.getStat("sp_category" + (i + 1));
            newPoints += categoryPoints;
            ua.setStat("sp_category" + (i + 1), 0);
            toUpdate.putInt("sp_category" + (i + 1), 0);
            String backpack = ua.getBackpack();
            String[] inventory = getBackpackInventory(parentExt, backpack);
            ArrayNode itemStats = getItemStats(parentExt, inventory[i]);
            for (JsonNode stat : getItemPointVal(itemStats, categoryPoints)) {
                if (stat.get("point").asInt() == categoryPoints) {
                    double packStat = stat.get("value").asDouble();
                    if (stat.get("stat").asText().equalsIgnoreCase("health")) {
                        double maxHealth = ua.getMaxHealth();
                        double pHealth = ua.getPHealth();
                        if (pHealth > 1) pHealth = 1;
                        maxHealth -= packStat;
                        double currentHealth = (int) Math.floor(pHealth * maxHealth);
                        ua.setHealth((int) currentHealth, (int) maxHealth);
                    } else if (stat.get("stat").asText().equalsIgnoreCase("attackRange")) {
                        ua.setStat(stat.get("stat").asText(), ua.getStat("attackRange") / packStat);
                    } else {
                        ua.increaseStat(stat.get("stat").asText(), packStat * -1);
                    }
                }
            }
        }
        if (spellPoints + newPoints > 1) spellPoints--;
        spellPoints += newPoints;
        ua.setStat("availableSpellPoints", spellPoints);
        toUpdate.putInt("availableSpellPoints", spellPoints);
        toUpdate.putUtfString("id", String.valueOf(user.getId()));
        return toUpdate;
    }

    public static String[] getBackpackInventory(ATBPExtension parentExt, String backpack) {
        JsonNode pack = parentExt.getDefinition(backpack).get("junk");
        String[] itemNames = new String[5];
        for (int i = 0; i < 5; i++) {
            itemNames[i] = pack.get("slot" + (i + 1)).get("junk_id").asText();
        }
        return itemNames;
    }

    public static ArrayNode getItemStats(ATBPExtension parentExt, String item) {
        JsonNode itemObj = parentExt.itemDefinitions.get(item).get("junk").get("mods");
        return (ArrayNode) itemObj.get("mod");
    }

    private static ArrayList<JsonNode> getItemPointVal(ArrayNode mods, int category) {
        ArrayList<JsonNode> stats = new ArrayList<>();
        for (JsonNode m : mods) {
            if (m.get("point").asInt() == category || m.get("point").asInt() == category - 1) {
                stats.add(m);
            } else if (m.get("point").asInt() > category) break;
        }
        return stats;
    }

    public static void levelUpCharacter(ATBPExtension parentExt, UserActor ua) {
        User user = ua.getUser();
        Map<String, Double> playerStats = ua.getStats();
        int level = ua.getLevel();
        for (String k : playerStats.keySet()) {
            if (k.contains("PerLevel")) {
                String stat = k.replace("PerLevel", "");
                double levelStat = playerStats.get(k);
                if (k.equalsIgnoreCase("healthPerLevel")) {
                    ua.setHealth(
                            (int) ((ua.getMaxHealth() + levelStat) * ua.getPHealth()),
                            (int) (ua.getMaxHealth() + levelStat));
                } else if (k.contains("attackSpeed")) {
                    ua.increaseStat(stat, (levelStat * -1));
                } else {
                    ua.increaseStat(stat, levelStat);
                }
            }
        }
        ISFSObject toUpdate = new SFSObject();
        Map<String, Double> stats = ua.getStats();
        int spellPoints = (int) (stats.get("availableSpellPoints") + 1);
        ua.setStat("availableSpellPoints", spellPoints);
        toUpdate.putUtfString("id", String.valueOf(user.getId()));
        if (user.getVariable("champion").getSFSObjectValue().getBool("autoLevel")) {
            int[] buildPath = getBuildPath(ua.getAvatar(), ua.getBackpack());
            int category = buildPath[level - 1];
            Console.debugLog(ua.getDisplayName() + " leveling category: " + category);
            int categoryPoints = (int) ua.getStat("sp_category" + category);
            int spentPoints = getTotalSpentPoints(ua); // How many points have been used
            boolean works = false;
            if (categoryPoints + 1 < 3) works = true;
            else if (categoryPoints + 1 == 3)
                works = spentPoints >= 4; // Can't get a third level without spending 4 points
            else if (categoryPoints + 1 == 4)
                works = spentPoints >= 6; // Can't get a fourth level without spending 6 points
            if (works) {
                ExtensionCommands.updateActorData(
                        parentExt,
                        ua.getRoom(),
                        useSpellPoint(user, "category" + category, parentExt));
            } else {
                for (int i = 0; i < buildPath.length; i++) {
                    category = buildPath[i];
                    if (categoryPoints + 1 < 3) works = true;
                    else if (categoryPoints + 1 == 3)
                        works =
                                spentPoints
                                        >= 4; // Can't get a third level without spending 4 points
                    else if (categoryPoints + 1 == 4)
                        works =
                                spentPoints
                                        >= 6; // Can't get a fourth level without spending 6 points
                    if (works) {
                        ExtensionCommands.updateActorData(
                                parentExt,
                                ua.getRoom(),
                                useSpellPoint(user, "category" + category, parentExt));
                        break;
                    }
                }
            }
        } else {
            toUpdate.putInt("availableSpellPoints", spellPoints);
        }
        ExtensionCommands.updateActorData(parentExt, ua.getRoom(), toUpdate);
    }

    public static int getBaseAbilityCooldown(UserActor userActor, int abilityNumber) {
        String championName = userActor.getChampionName(userActor.getAvatar());
        int championLevel = ChampionData.getXPLevel(userActor.getXp());
        JsonNode abilityData =
                userActor.getParentExt().getAttackData(championName, "spell" + abilityNumber);
        int lv1Cooldown = abilityData.get("spellCoolDown").asInt();
        if (abilityNumber == 4) return lv1Cooldown;
        if (championLevel > 1) {
            int lv10Cooldown;
            switch (championName) {
                case "billy":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 12000 : 45000;
                    break;
                case "bmo":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 11000 : 37500;
                    break;
                case "cinnamonbun":
                    lv10Cooldown = abilityNumber == 1 ? 4500 : abilityNumber == 2 ? 14000 : 42000;
                    break;
                case "finn":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 10000 : 26000;
                    break;
                case "fionna":
                    lv10Cooldown = abilityNumber == 1 ? 7000 : abilityNumber == 2 ? 5000 : 60000;
                    break;
                case "flame":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 15000 : 45000;
                    break;
                case "gunter":
                    lv10Cooldown = abilityNumber == 1 ? 9000 : abilityNumber == 2 ? 5000 : 42000;
                    break;
                case "hunson":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 12000 : 35000;
                    break;
                case "iceking":
                    lv10Cooldown = abilityNumber == 1 ? 7000 : abilityNumber == 2 ? 8000 : 45000;
                    break;
                case "jake":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 12000 : 45000;
                    break;
                case "lemongrab":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 10000 : 35000;
                    break;
                case "lich":
                    lv10Cooldown = abilityNumber == 1 ? 10500 : abilityNumber == 2 ? 10000 : 45000;
                    break;
                case "lsp":
                    lv10Cooldown = abilityNumber == 1 ? 12000 : abilityNumber == 2 ? 12000 : 30000;
                    break;
                case "magicman":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 19000 : 40000;
                    break;
                case "marceline":
                    lv10Cooldown = abilityNumber == 1 ? 7000 : abilityNumber == 2 ? 8000 : 19000;
                    break;
                case "neptr":
                    lv10Cooldown = abilityNumber == 1 ? 6000 : abilityNumber == 2 ? 5000 : 42000;
                    break;
                case "peppermintbutler":
                    lv10Cooldown = abilityNumber == 1 ? 13000 : abilityNumber == 2 ? 13000 : 25000;
                    break;
                case "princessbubblegum":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 16000 : 40000;
                    break;
                case "rattleballs":
                    lv10Cooldown = abilityNumber == 1 ? 10000 : abilityNumber == 2 ? 14000 : 50000;
                    break;
                default:
                    lv10Cooldown = lv1Cooldown;
                    break;
            }
            if (championLevel < 10) {
                int startingLevel = 1;
                int maxLevel = 10;
                return (lv1Cooldown
                        + ((lv10Cooldown - lv1Cooldown) * (championLevel - startingLevel))
                                / maxLevel
                        - startingLevel);
            } else {
                return lv10Cooldown;
            }
        }
        return lv1Cooldown;
    }

    public static JsonNode getFlameCloakAttackData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("spellName", "Flame Cloak");
        node.put("spellDescription", "Oof ouch owie");
        node.put("spellShortDescription", "OWW");
        node.put("spellIconImage", "junk_4_flame_cloak");
        node.put("spellType", "SPELL");
        return node;
    }

    public static int[] getBuildPath(String actor, String backpack) {
        int[] buildPath = {1, 1, 2, 2, 1, 2, 1, 2, 5, 5};
        String avatar = actor;
        if (actor.contains("skin")) {
            avatar = actor.split("_")[0];
        }
        switch (avatar) {
            case "billy":
                if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_sorcerous_satchel")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                }
                break;
            default:
                Console.logWarning(avatar + " cannot auto level!");
                break;
        }
        return buildPath;
    }

    public static int getEloGain(UserActor ua, List<UserActor> players, double result) {
        double myElo = ua.getUser().getVariable("player").getSFSObjectValue().getInt("elo");
        double teamCount = 0;
        int myTeamCount = 0;
        double teamElo = 0;
        for (UserActor u : players) {
            if (u.getTeam() != ua.getTeam()) {
                double enemyElo =
                        u.getUser().getVariable("player").getSFSObjectValue().getInt("elo");
                teamCount++;
                teamElo += enemyElo;
            } else myTeamCount++;
        }
        int tier = getTier(myElo);
        double kFactor = 100;
        switch (tier) {
            case 1:
                kFactor = 50;
                break;
            case 2:
                kFactor = 35;
                break;
            case 3:
                kFactor = 25;
                break;
        }
        double averageEnemyElo = Math.round(teamElo / teamCount);
        if (myTeamCount < teamCount) averageEnemyElo += 100 * (teamCount - myTeamCount);
        double myProb = 1d / (1 + Math.pow(10, (averageEnemyElo - myElo) / 400));
        double eloGain = Math.round(kFactor * (result - myProb));
        Console.debugLog(ua.getDisplayName() + " ELO: " + myElo);
        Console.debugLog(ua.getDisplayName() + " TIER: " + tier);
        Console.debugLog(ua.getDisplayName() + " ENEMY ELO: " + averageEnemyElo);
        Console.debugLog(ua.getDisplayName() + " ELO PREDICTION: " + myProb);
        Console.debugLog(ua.getDisplayName() + " ELO GAIN: " + eloGain);
        return (int) eloGain;
    }

    public static int getTier(double elo) {
        for (int i = 0; i < ELO_TIERS.length; i++) {
            double val1 = i == 0 ? 0 : ELO_TIERS[i];
            double val2 = i + 1 == ELO_TIERS.length ? MAX_ELO : ELO_TIERS[i + 1];
            if (elo >= val1 && elo < val2) return i;
        }
        return 4;
    }

    public static boolean tierChanged(double originalElo, double eloGain) {
        return getTier(originalElo) != getTier(originalElo + eloGain);
    }

    public static String[] ALLY_MULTIES = {
        "", "", "ko_double_ally", "ko_triple_ally", "ko_quad_ally", "ko_penta_ally"
    };

    public static String[] ENEMY_MULTIES = {
        "", "", "ko_double_enemy", "ko_triple_enemy", "ko_quad_enemy", "ko_penta_enemy"
    };

    public static String[] OWN_MULTIES = {"", "", "ko_double", "ko_triple", "ko_quad", "ko_penta"};

    public static String[] ALLY_SPREES = {
        "",
        "",
        "",
        "kill1_ally_is_awesome",
        "kill2_ally_is_math",
        "kill3_ally_is_spicy",
        "kill4_ally_is_tops",
        "kill5_ally_is_animal",
        "kill7_ally_is_demon",
        "kill6_ally_is_god"
    };

    public static String[] ENEMY_SPREES = {
        "",
        "",
        "",
        "kill1_enemy_is_awesome",
        "kill2_enemy_is_math",
        "kill3_enemy_is_spicy",
        "kill4_enemy_is_tops",
        "kill5_enemy_is_animal",
        "kill7_enemy_is_demon",
        "kill6_enemy_is_god"
    };

    public static String[] OWN_SPREES = {
        "",
        "",
        "",
        "kill1_you_are_awesome",
        "kill2_you_are_math",
        "kill3_you_are_spicy",
        "kill4_you_are_tops",
        "kill5_you_are_animal",
        "kill7_you_are_demon",
        "kill6_you_are_god"
    };
}

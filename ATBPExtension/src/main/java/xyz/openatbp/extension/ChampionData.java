package xyz.openatbp.extension;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.game.actors.UserActor;

// TODO: More clearly separate this from Champion.class
//  and make functions void (or move into UserActor functions)

public class ChampionData {

    private static final int[] XP_LEVELS = {100, 210, 330, 460, 600, 670, 850, 1040, 1240, 1450};
    public static final double[] ELO_TIERS = {0, 1149, 1350, 1602};
    public static final double MAX_ELO = 2643;

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
            else Console.logWarning("Failed everything!");
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
        } else {
            Console.logWarning("Failed!: " + category);
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
                if (k.contains("health")) {
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
        String championName = userActor.getDefaultCharacterName(userActor.getAvatar());
        int championLevel = ChampionData.getXPLevel(userActor.getXp());
        JsonNode abilityData =
                userActor.getParentExt().getAttackData(championName, "spell" + abilityNumber);
        int lv1Cooldown = abilityData.get("spellCoolDown").asInt();
        if (championLevel > 1) {
            int lv10Cooldown;
            switch (championName) {
                case "billy":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 10000 : 45000;
                    break;
                case "bmo":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 11000 : 37500;
                    break;
                case "cinnamonbun":
                    lv10Cooldown = abilityNumber == 1 ? 4500 : abilityNumber == 2 ? 12000 : 42000;
                    break;
                case "finn":
                    lv10Cooldown = abilityNumber == 1 ? 8000 : abilityNumber == 2 ? 7000 : 26000;
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

    public static int[] getBuildPath(String actor, String backpack) {
        int[] buildPath = {1, 1, 2, 2, 1, 2, 1, 2, 5, 5};
        String avatar = actor;
        if (actor.contains("skin")) {
            avatar = actor.split("_")[0];
        }
        switch (avatar) {
            case "billy":
            case "cinnamonbun":
                if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_sorcerous_satchel")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                }
                break;
            case "bmo":
                if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                }
                break;
            case "finn":
                if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                }
                break;
            case "fionna":
                if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bindle_of_bravery")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_fridjitsu")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                }
                break;
            case "flame":
                if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 2, 2};
                }
                break;
            case "gunter":
                if (backpack.equalsIgnoreCase("belt_fridjitsu")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 2, 2};
                }
                break;
            case "iceking":
                if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_fridjitsu")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 2, 2};
                }
                break;
            case "jake":
                if (backpack.equalsIgnoreCase("belt_sorcerous_satchel")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                }
                break;
            case "lemongrab":
                if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_candy_monarch")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 5, 5};
                }
                break;
            case "lich":
                if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                }
                break;
            case "lsp":
                if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                }
                break;
            case "magicman":
                if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 2, 2};
                }
                break;
            case "marceline":
                if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_sorcerous_satchel")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                }
                break;
            case "neptr":
                if (backpack.equalsIgnoreCase("belt_ultimate_wizard")) {
                    buildPath = new int[] {2, 2, 3, 3, 2, 3, 2, 3, 4, 4};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_sorcerous_satchel")) {
                    buildPath = new int[] {2, 2, 4, 4, 2, 4, 2, 4, 3, 3};
                }
                break;
            case "peppermintbutler":
                if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 5, 5};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_candy_monarch")) {
                    buildPath = new int[] {1, 1, 4, 4, 1, 4, 1, 4, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_fridjitsu")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                }
                break;
            case "princessbubblegum":
                if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                }
                break;
            case "rattleballs":
                if (backpack.equalsIgnoreCase("belt_techno_tank")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_bella_noche")) {
                    buildPath = new int[] {1, 1, 3, 3, 1, 3, 1, 3, 2, 2};
                } else if (backpack.equalsIgnoreCase("belt_billys_bag")) {
                    buildPath = new int[] {1, 1, 5, 5, 1, 5, 1, 5, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_champions")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_candy_monarch")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
                } else if (backpack.equalsIgnoreCase("belt_hewers_haversack")) {
                    buildPath = new int[] {1, 1, 2, 2, 1, 2, 1, 2, 3, 3};
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
        double teamElo = 0;
        for (UserActor u : players) {
            if (u.getTeam() != ua.getTeam()) {
                double enemyElo =
                        u.getUser().getVariable("player").getSFSObjectValue().getInt("elo");
                teamCount++;
                teamElo += enemyElo;
            }
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
        double myProb = 1d / (1 + Math.pow(10, (averageEnemyElo - myElo) / 400));
        double eloGain = Math.round(kFactor * (result - myProb));
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

    private static String getDefeatedSound(boolean enemy, boolean ally) {
        if (!enemy && !ally) return "announcer/you_defeated_enemy";
        else if (ally) return "announcer/enemy_defeated";
        return "";
    }

    public static String getKOSoundEffect(
            boolean enemy, boolean ally, int multiKill, int killingSpree) {
        if (multiKill > 1) {
            String koSound = "announcer/ko_";
            switch (multiKill) {
                case 2:
                    koSound += "double";
                    break;
                case 3:
                    koSound += "triple";
                    break;
                case 4:
                    koSound += "quad";
                    break;
                default:
                    koSound += "penta";
                    break;
            }
            if (ally) koSound += "_ally";
            else if (enemy) koSound += "_enemy";
            return koSound;
        } else if (killingSpree > 1) {
            String koSound = "announcer/";
            String ko = "";
            if (!enemy && !ally) ko += "you_are";
            else if (enemy) ko += "enemy_is";
            else ko += "ally_is";
            switch (killingSpree) {
                case 2:
                    return getDefeatedSound(enemy, ally);
                case 3:
                    koSound += "kill1_" + ko + "_awesome";
                    break;
                case 4:
                    koSound += "kill2_" + ko + "_math";
                    break;
                case 5:
                    koSound += "kill3_" + ko + "_spicy";
                    break;
                case 6:
                    koSound += "kill4_" + ko + "_tops";
                    break;
                case 7:
                    koSound += "kill5_" + ko + "_animal";
                    break;
                case 8:
                    koSound += "kill7_" + ko + "_demon";
                    break;
                default:
                    koSound += "kill6_" + ko + "_god";
                    break;
            }
            return koSound;
        } else {
            return getDefeatedSound(enemy, ally);
        }
    }
}

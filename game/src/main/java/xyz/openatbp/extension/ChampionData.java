package xyz.openatbp.extension;

public class ChampionData {
    public static int getMaxHealth(String champion){
        switch(champion){ //TODO: Add all champions or read definition XML
            case "lich":
                return 350;
            default:
                return -1;

        }
    }


}

package xyz.openatbp.extension;

public class Console {
    private static final boolean DEBUG = false;
    private static final boolean WARNING = true;

    public static void log(Object line){
        System.out.println(line);
    }
    public static void debugLog(Object line){
        if (DEBUG){
            System.out.print("[DEBUG] ");
            System.out.println(line);
        }
    }
    public static void logWarning(Object line){
        if(WARNING){
            System.out.print("[WARNING] ");
            System.out.println(line);
        }
    }
}

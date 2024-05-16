package xyz.openatbp.extension;

public class StressLogger {
    private String command;
    private int count;
    private long lastUsed;

    public StressLogger(String command) {
        this.command = command;
        this.count = 1;
        this.lastUsed = System.currentTimeMillis();
    }

    public void update() {
        long timeDiff = System.currentTimeMillis() - this.lastUsed;
        if (timeDiff >= 50) this.count = 1;
        else this.count++;
        if (this.count >= (this.command.contains("actor_data") ? 200 : 100))
            Console.debugLog(
                    this.command
                            + " has been used "
                            + this.count
                            + " times and was last used "
                            + timeDiff
                            + " ms ago.");
        this.lastUsed = System.currentTimeMillis();
    }
}

package xyz.openatbp.extension;

import java.util.HashMap;
import java.util.Map;

import com.smartfoxserver.v2.entities.data.ISFSObject;

public class StressLogger {
    private String command;
    private int count;
    private long lastUsed;
    private Map<String, Integer> userCount;
    private Map<String, Long> userUsage;

    public StressLogger(String command) {
        this.command = command;
        this.count = 1;
        this.lastUsed = System.currentTimeMillis();
        this.userCount = new HashMap<>();
        this.userUsage = new HashMap<>();
    }

    public void update(ISFSObject params) {
        long timeDiff = System.currentTimeMillis() - this.lastUsed;
        if (this.userCount.size() != 0 || this.getTarget(params) != null) {
            String target = this.getTarget(params);
            if (target != null) {
                if (this.userCount.containsKey(target)) {
                    timeDiff = this.userUsage.get(target);
                    if (timeDiff >= 50) this.userCount.put(target, 1);
                    else {
                        this.userCount.put(target, this.userCount.get(target) + 1);
                    }
                } else {
                    this.userCount.put(target, 1);
                }
                this.userUsage.put(target, System.currentTimeMillis());
            }
            for (String user : this.userCount.keySet()) {
                if (this.userCount.get(user) >= 50)
                    Console.logWarning(
                            user
                                    + " has used "
                                    + this.command
                                    + " "
                                    + this.userCount.get(user)
                                    + " times! Last used "
                                    + this.userUsage.get(user)
                                    + " ms ago.");
            }
        } else {
            if (timeDiff >= 50) this.count = 1;
            else this.count++;
            if (this.count >= (this.command.contains("actor_data") ? 200 : 100))
                Console.logWarning(
                        this.command
                                + " has been used "
                                + this.count
                                + " times and was last used "
                                + timeDiff
                                + " ms ago.");
        }
        this.lastUsed = System.currentTimeMillis();
    }

    private String getTarget(ISFSObject params) {
        switch (this.command) {
            case "cmd_move_actor":
                return params.getUtfString("i");
            default:
                if (params.getUtfString("id") != null) return params.getUtfString("id");
        }
        return null;
    }
}

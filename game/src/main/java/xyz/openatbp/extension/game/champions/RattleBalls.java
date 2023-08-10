package xyz.openatbp.extension.game.champions;

import com.smartfoxserver.v2.entities.User;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.game.actors.UserActor;

public class RattleBalls extends UserActor {
    private boolean passiveActive = false;
    public RattleBalls(User u, ATBPExtension parentExt) {
        super(u, parentExt);
    }
}

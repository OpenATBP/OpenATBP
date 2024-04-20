package xyz.openatbp.extension.game;

import java.util.*;

import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class EffectHandler {
    private Actor parent;
    private String stat;
    private ActorState state;
    private Map<Long, Double> statLog;
    private FxHandler attachedFx;
    private double currentDelta;

    public EffectHandler(Actor parent, String stat) {
        this.parent = parent;
        this.stat = stat;
        this.statLog = new HashMap<>();
        this.currentDelta = 0;
        Console.debugLog("Effect Handler created for " + stat);
    }

    public EffectHandler(Actor parent, ActorState state) {
        this.parent = parent;
        this.state = state;
        this.statLog = new HashMap<>();
        this.currentDelta = 0;
        parent.setState(state, true);
        Console.debugLog("Effect Handler created for " + state.toString());
    }

    public void addEffect(double delta, int duration) {
        long endTime = System.currentTimeMillis() + duration;
        if (this.statLog.containsKey(endTime)) endTime++;
        this.statLog.put(endTime, delta);
        if (this.stat.equalsIgnoreCase("healthRegen"))
            this.parent.addFx("fx_health_regen", "", duration);
        this.handleEffect(delta);
    }

    public void addState(double delta, int duration) {
        long endTime = System.currentTimeMillis() + duration;
        if (this.statLog.containsKey(endTime)) endTime++;
        this.statLog.put(endTime, delta);
        if (delta != 0) this.handleState(delta);
        else this.handleState(duration);
    }

    @Deprecated
    public void addStatPercentage(double percentage, int duration) {
        long endTime = System.currentTimeMillis() + duration;
        if (this.statLog.containsKey(endTime)) endTime++;
        String stat = null;
        int inverse = 1;
        switch (this.state) {
            case SLOWED:
                stat = "speed";
                inverse = -1;
                break;
        }
        double statChange = 0d;
        if (stat != null) statChange = this.parent.getStat(stat) * percentage;
        this.statLog.put(endTime, statChange * inverse);
        if (this.state != null) this.handleState(statChange * inverse);
        else this.handleEffect(statChange * inverse);
    }

    private void handleState(double delta) {
        switch (this.state) {
            case SLOWED:
                this.updateStat("speed", this.parent.getStat("speed") * delta * -1);
                break;
        }
    }

    private void handleState(int duration) {
        switch (this.state) {
            case POLYMORPH:
                if (this.parent.getActorType() == ActorType.PLAYER) {
                    UserActor ua = (UserActor) this.parent;
                    ua.handlePolymorph(true, duration);
                } else Console.logWarning(this.parent.getId() + " got polymorphed somehow!");
                break;
            case STUNNED:
            case ROOTED:
                if (!this.parent.getState(ActorState.AIRBORNE)) this.parent.stopMoving();
                break;
        }
    }

    private void updateStat(double delta) {
        boolean update = false;
        if (Math.abs(delta) > Math.abs(this.currentDelta)) {
            this.currentDelta = delta;
            update = true;
        }
        // Console.debugLog("Setting currentDelta to " + this.currentDelta);
        if (this.parent.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) this.parent;
            if (ua.canMove() && this.stat.equalsIgnoreCase("speed"))
                ua.move(ua.getMovementLine().getP2());
            if (update) ua.updateStatMenu(this.stat);
        }
    }

    private void updateStat(String stat, double delta) {
        boolean update = false;
        if (Math.abs(delta) > Math.abs(this.currentDelta)) {
            this.currentDelta = delta;
            update = true;
        }
        // Console.debugLog("Updating currentDelta to " + this.currentDelta);
        if (this.parent.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) this.parent;
            if (ua.canMove() && stat.equalsIgnoreCase("speed"))
                ua.move(ua.getMovementLine().getP2());
            if (update) ua.updateStatMenu(stat);
        }
    }

    private void changeToNextBuff(long exclusionKey, String stat) {
        double highestValue = 0;
        Set<Long> keys = new HashSet<>(this.statLog.keySet());
        for (Long k : keys) {
            if (k != exclusionKey) {
                if (Math.abs(this.statLog.get(k)) > highestValue) {
                    highestValue = Math.abs(this.statLog.get(k));
                }
            }
        }
        this.currentDelta = highestValue;
        if (this.parent.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) this.parent;
            if (ua.canMove() && stat.equalsIgnoreCase("speed"))
                ua.move(ua.getMovementLine().getP2());
            ua.updateStatMenu(stat);
        }
    }

    private void handleStateEnd(long key) {
        Console.debugLog(this.state.toString() + " ending at " + key);
        switch (this.state) {
            case SLOWED:
                // this.updateStat("speed", parent.getStat("speed") * this.statLog.get(key));
                this.changeToNextBuff(key, "speed");
                break;
            case POLYMORPH:
                if (this.parent.getActorType() == ActorType.PLAYER) {
                    UserActor ua = (UserActor) this.parent;
                    ua.handlePolymorph(false, 0);
                }
                break;
        }
        this.statLog.remove(key);
    }

    public void endAllEffects() {
        List<Long> keys = new ArrayList<>(this.statLog.keySet());
        for (long k : keys) {
            if (this.stat == null) this.handleStateEnd(k);
            else this.handleEffectEnd(k);
        }
    }

    public boolean update() {
        List<Long> badKeys = new ArrayList<>();
        Set<Long> currentKeys = new HashSet<>(this.statLog.keySet());
        for (long key : currentKeys) {
            if (System.currentTimeMillis() >= key) {
                if (this.stat == null) this.handleStateEnd(key);
                else this.handleEffectEnd(key);
                badKeys.add(key);
            }
        }
        for (Long k : badKeys) {
            this.statLog.remove(k);
        }
        if (badKeys.size() > 0 && this.statLog.size() == 0) {
            if (this.state != null) this.parent.setState(state, false);
            return true;
        }
        return false;
    }

    private void handleEffect(double delta) {
        this.updateStat(delta);
    }

    private void handleEffectEnd(long key) {
        Console.debugLog(this.stat + " ending at " + key);
        this.changeToNextBuff(key, this.stat);
        this.statLog.remove(key);
    }

    public double getCurrentDelta() {
        // Console.debugLog("Current delta is " + this.currentDelta);
        return this.currentDelta;
    }
}

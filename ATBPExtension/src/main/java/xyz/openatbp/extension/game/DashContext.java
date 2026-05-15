package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;

public class DashContext {
    private Point2D origin;
    private Point2D dest;
    private float speed;
    private boolean canBeRedirected;
    private boolean triggerEndEffectOnRoot;
    private boolean isLeap;
    private Runnable onEnd;
    private Runnable onInterrupt;
    private Runnable onTick;

    private DashContext() {}

    public static class Builder {
        private final Point2D origin;
        private final Point2D dest;
        private final float speed;
        private boolean canBeRedirected = false;
        private boolean triggerEndEffectOnRoot = false;
        private boolean isLeap = false;
        private Runnable onEnd = null;
        private Runnable onInterrupt = null;
        private Runnable onTick = null;

        public Builder(Point2D origin, Point2D dest, float speed) {
            this.origin = origin;
            this.dest = dest;
            this.speed = speed;
        }

        public Builder canBeRedirected(boolean val) {
            this.canBeRedirected = val;
            return this;
        }

        public Builder triggerEndEffectOnRoot(boolean val) {
            this.triggerEndEffectOnRoot = val;
            return this;
        }

        public Builder isLeap(boolean val) {
            this.isLeap = val;
            return this;
        }

        public Builder onEnd(Runnable val) {
            this.onEnd = val;
            return this;
        }

        public Builder onInterrupt(Runnable val) {
            this.onInterrupt = val;
            return this;
        }

        public Builder onTick(Runnable val) {
            this.onTick = val;
            return this;
        }

        public DashContext build() {
            DashContext ctx = new DashContext();
            ctx.origin = origin;
            ctx.dest = dest;
            ctx.speed = speed;
            ctx.canBeRedirected = canBeRedirected;
            ctx.triggerEndEffectOnRoot = triggerEndEffectOnRoot;
            ctx.isLeap = isLeap;
            ctx.onEnd = onEnd;
            ctx.onInterrupt = onInterrupt;
            ctx.onTick = onTick;
            return ctx;
        }
    }

    public Point2D getOrigin() {
        return origin;
    }

    public Point2D getDest() {
        return dest;
    }

    public void setDest(Point2D dest) {
        this.dest = dest;
    }

    public float getSpeed() {
        return speed;
    }

    public boolean canBeRedirected() {
        return canBeRedirected;
    }

    public Runnable getOnInterrupt() {
        return onInterrupt;
    }

    public boolean isLeap() {
        return isLeap;
    }

    public Runnable getOnEnd() {
        return onEnd;
    }

    public boolean getTriggerEndEffectOnRoot() {
        return triggerEndEffectOnRoot;
    }

    public Runnable getOnTick() {
        return onTick;
    }
}

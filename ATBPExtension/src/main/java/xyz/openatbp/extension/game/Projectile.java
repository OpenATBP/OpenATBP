package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public abstract class Projectile {

    protected float timeTraveled = 0;
    protected Point2D destination;
    protected Point2D location;
    protected Point2D startingLocation;
    protected float speed;
    protected UserActor owner;
    protected String id;
    protected String projectileAsset;
    protected float hitbox;
    protected ATBPExtension parentExt;
    protected boolean destroyed = false;
    protected Line2D path;
    protected long startTime;
    protected double estimatedDuration;

    public Projectile(
            ATBPExtension parentExt,
            UserActor owner,
            Line2D path,
            float speed,
            float hitboxRadius,
            String projectileAsset) {
        this.parentExt = parentExt;
        this.owner = owner;
        this.startingLocation = path.getP1();
        this.destination = path.getP2();
        this.speed = speed;
        this.hitbox = hitboxRadius;
        this.location = path.getP1();
        this.projectileAsset = projectileAsset;
        this.id = owner.getId() + "_" + projectileAsset + (Math.floor(Math.random() * 100));
        this.path = path;
        this.startTime = System.currentTimeMillis();
        this.estimatedDuration = (path.getP1().distance(path.getP2()) / speed) * 1000f;
    }

    public Point2D getLocation() { // Gets projectile's current location based on time
        double currentTime = this.timeTraveled;
        Point2D rPoint = new Point2D.Float();
        float x2 = (float) this.destination.getX();
        float y2 = (float) this.destination.getY();
        float x1 = (float) this.startingLocation.getX();
        float y1 = (float) this.startingLocation.getY();
        Line2D movementLine = new Line2D.Double(x1, y1, x2, y2);
        double dist = movementLine.getP1().distance(movementLine.getP2());
        if (dist == 0) return this.startingLocation;
        double time = dist / speed;
        if (currentTime > time) currentTime = time;
        double currentDist = speed * currentTime;
        float x = (float) (x1 + (currentDist / dist) * (x2 - x1));
        float y = (float) (y1 + (currentDist / dist) * (y2 - y1));
        rPoint.setLocation(x, y);
        this.location = rPoint;
        return rPoint;
    }

    public void updateTimeTraveled() {
        this.timeTraveled += 0.1f;
    }

    public void update(RoomHandler roomHandler) {
        if (destroyed) return;
        this.updateTimeTraveled();
        Actor hitActor = this.checkPlayerCollision(roomHandler);
        if (hitActor != null) {
            this.hit(hitActor);
            return;
        }
        if (this.destination.distance(this.getLocation()) <= 0.01
                || System.currentTimeMillis() - this.startTime > this.estimatedDuration) {
            Console.debugLog("Projectile being destroyed in update!");
            this.destroy();
        }
    }

    public Actor checkPlayerCollision(RoomHandler roomHandler) {
        List<Actor> nonStructureEnemies = roomHandler.getNonStructureEnemies(owner.getTeam());
        for (Actor a : nonStructureEnemies) {
            double collisionRadius =
                    parentExt.getActorData(a.getAvatar()).get("collisionRadius").asDouble();
            if (a.getLocation().distance(location) <= hitbox + collisionRadius
                    && !a.getAvatar().equalsIgnoreCase("neptr_mine")) {
                return a;
            }
        }
        return null;
    }

    protected abstract void hit(Actor victim);

    public Point2D getDestination() {
        return this.destination;
    }

    public String getId() {
        return this.id;
    }

    public String getProjectileAsset() {
        return this.projectileAsset;
    }

    public double getEstimatedDuration() {
        return estimatedDuration;
    }

    public void destroy() {
        Console.debugLog("Projectile: " + id + " is being destroyed! " + this.destroyed);
        if (!destroyed) {
            ExtensionCommands.destroyActor(this.parentExt, owner.getRoom(), this.id);
        }
        this.destroyed = true;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    public int getTeam() {
        return this.owner.getTeam();
    }
}

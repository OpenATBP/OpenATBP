package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.actors.Actor;

public abstract class Projectile {

    protected float travelTimeMs = 0;
    protected Point2D destination;
    protected Point2D location;
    protected Point2D startingLocation;
    protected float speed;
    protected Actor owner;
    protected String id;
    protected String projectileAsset;
    protected float offsetDistance;
    protected float rectangeHeight;
    protected ATBPExtension parentExt;
    protected boolean destroyed = false;
    protected Line2D path;
    protected long startTime;
    protected double maxTravelTimeMs;
    private final boolean projectileDebug;

    public Projectile(
            ATBPExtension parentExt,
            Actor owner,
            Line2D path,
            float speed,
            float offsetDistance,
            float rectangeHeight,
            String projectileAsset) {
        this.parentExt = parentExt;
        this.owner = owner;
        this.startingLocation = path.getP1();
        this.destination = path.getP2();
        this.speed = speed;
        this.offsetDistance = offsetDistance;
        this.rectangeHeight = rectangeHeight;
        this.location = path.getP1();
        this.projectileAsset = projectileAsset;
        this.id = owner.getId() + "_" + projectileAsset + (Math.floor(Math.random() * 100));
        this.path = path;
        this.startTime = System.currentTimeMillis();
        this.maxTravelTimeMs =
                Math.max(1, (startingLocation.distance(destination) / speed) * 1000.0);
        Properties prop = parentExt.getConfigProperties();
        this.projectileDebug = Boolean.parseBoolean(prop.getProperty("projectileDebug", "false"));
    }

    public Point2D getLocation() {
        return location;
    }

    public float getSpeed() {
        return this.speed;
    }

    public float getOffsetDistance() {
        return offsetDistance;
    }

    public void setOffsetDistance(float offsetDistance) {
        this.offsetDistance = offsetDistance;
    }

    public void updateLocation() {
        travelTimeMs += 100;

        double progress = Math.min(travelTimeMs / maxTravelTimeMs, 1.0);
        double x =
                startingLocation.getX() + (destination.getX() - startingLocation.getX()) * progress;
        double y =
                startingLocation.getY() + (destination.getY() - startingLocation.getY()) * progress;

        location = new Point2D.Float((float) x, (float) y);
    }

    public void update(RoomHandler roomHandler) {
        if (destroyed) return;
        updateLocation();

        boolean atDestination =
                destination.distance(location) <= 0.01
                        || System.currentTimeMillis() - startTime > maxTravelTimeMs;

        if (!atDestination) { // to prevent atan2 undefined angle bug when projectile is at
            // destination
            Actor hitActor = checkPlayerCollision(roomHandler);
            if (hitActor != null) hit(hitActor);
        }

        if (atDestination) {
            Console.debugLog("Projectile being destroyed in update!");
            destroy();
        }
    }

    public Actor checkPlayerCollision(RoomHandler roomHandler) {
        float searchArea = offsetDistance * 2;
        List<Actor> actorsInRadius = roomHandler.getActorsInRadius(location, searchArea);

        float rectangleHeight = (speed * 0.1f) * 1.1f; // 10% overlap buffer

        AbilityShape projectileRectangle =
                AbilityShape.createRectangle(
                        location, destination, rectangleHeight, offsetDistance);

        if (projectileDebug) {
            projectileRectangle.displayVertices(
                    parentExt, owner.getRoom(), owner.getId(), owner.getTeam());
        }

        List<Actor> affectedActors = new ArrayList<>();
        for (Actor a : actorsInRadius) {
            if (projectileRectangle.contains(a.getLocation(), a.getCollisionRadius())
                    && isTargetable(a)) {
                affectedActors.add(a);
            }
        }

        float minDistance = Float.MAX_VALUE;
        Actor closestActor = null;
        for (Actor a : affectedActors) {
            float distance = (float) a.getLocation().distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                closestActor = a;
            }
        }
        return closestActor;
    }

    public boolean isTargetable(Actor a) {
        String avatar = a.getAvatar();
        return !avatar.equals("neptr_mine")
                && !avatar.equals("choosegoose_chest")
                && a.getActorType() != ActorType.TOWER
                && a.getTeam() != owner.getTeam();
    }

    protected abstract void hit(Actor victim);

    public String getId() {
        return this.id;
    }

    public String getProjectileAsset() {
        return this.projectileAsset;
    }

    public void destroy() {
        Console.debugLog("Projectile: " + id + " is being destroyed! " + this.destroyed);
        if (!destroyed) {
            ExtensionCommands.destroyActor(parentExt, owner.getRoom(), id);
        }
        destroyed = true;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    public int getTeam() {
        return this.owner.getTeam();
    }
}

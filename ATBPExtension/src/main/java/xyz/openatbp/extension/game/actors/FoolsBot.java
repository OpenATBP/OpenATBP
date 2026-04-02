package xyz.openatbp.extension.game.actors;

import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.SkinData;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class FoolsBot extends Bot {
    public FoolsBot(
            ATBPExtension parentExt, Room room, String avatar, int team, Point2D spawnPoint) {
        super(parentExt, room, avatar, team, spawnPoint);
    }

    @Override
    public void update(int msRan) {
        if (dead) return;
        handleDamageQueue();
        handleActiveEffects();

        if (this.enemy == null) {
            List<Actor> nearbyPlayers =
                    Champion.getEnemyActorsInRadius(
                            parentExt.getRoomHandler(room.getName()), this.team, this.location, 5f);
            double closestRange = 100d;
            UserActor enemyPlayer = null;
            for (Actor a : nearbyPlayers) {
                if (a instanceof UserActor) {
                    UserActor ua = (UserActor) a;
                    if (ua.getTeam() != this.team
                            && ua.getLocation().distance(this.location) < closestRange) {
                        closestRange = ua.getLocation().distance(this.location);
                        enemyPlayer = ua;
                    }
                }
            }
            this.enemy = enemyPlayer;
        }

        if (attackCooldown > 0) attackCooldown -= 100;

        if (isPolymorphed && System.currentTimeMillis() - lastPolymorphTime >= POLYMORPH_DURATION) {
            isPolymorphed = false;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }

        if (!isStopped() && canMove()) timeTraveled += 0.1f;
        location =
                MovementManager.getRelativePoint(
                        movementLine, getPlayerStat("speed"), timeTraveled);
        handlePathing();
        if (MOVEMENT_DEBUG)
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id + "moveDebug",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    false);

        if (qActive && System.currentTimeMillis() - lastQUse >= Q_DURATION) {
            qActive = false;
        }
        if (furyStacks > 0) {
            if (System.currentTimeMillis() - passiveStart >= PASSIVE_DURATION) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
            if (furyTarget.getHealth() <= 0) {
                ExtensionCommands.removeFx(
                        parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
                furyStacks = 0;
            }
        }

        if (ultActivated && System.currentTimeMillis() - eStartTime >= E_DURATION) {
            wallLines = null;
            wallsActivated = new boolean[] {false, false, false, false};
            ultActivated = false;
            finnUltRing = null;
        }

        if (ultActivated && wallLines != null) {
            for (int i = 0; i < wallLines.length; i++) {
                if (wallsActivated[i]) {
                    RoomHandler handler = parentExt.getRoomHandler(room.getName());
                    List<Actor> nonStructureEnemies = handler.getNonStructureEnemies(team);
                    for (Actor a : nonStructureEnemies) {
                        if (wallLines[i].ptSegDist(a.getLocation()) <= 0.5f) {
                            wallsActivated[i] = false;
                            JsonNode spellData = parentExt.getAttackData("finn", "spell3");
                            a.addState(ActorState.ROOTED, 0d, E_ROOT_DURATION);
                            a.addToDamageQueue(
                                    this,
                                    handlePassive(a, getSpellDamage(spellData)),
                                    spellData,
                                    false);
                            passiveStart = System.currentTimeMillis();
                            String direction = "north";
                            if (i == 1) direction = "east";
                            else if (i == 2) direction = "south";
                            else if (i == 3) direction = "west";

                            String wallDestroyedFX = SkinData.getFinnEDestroyFX(avatar, direction);
                            String wallDestroyedSFX = SkinData.getFinnEDestroySFX(avatar);
                            ExtensionCommands.removeFx(
                                    parentExt, room, id + "_" + direction + "Wall");
                            ExtensionCommands.createWorldFX(
                                    parentExt,
                                    room,
                                    id,
                                    wallDestroyedFX,
                                    id + "_wallDestroy_" + direction,
                                    1000,
                                    ultX,
                                    ultY,
                                    false,
                                    team,
                                    180f);
                            ExtensionCommands.playSound(
                                    parentExt, room, id, wallDestroyedSFX, location);
                            break;
                        }
                    }
                }
            }
        }

        if (msRan % 1000 == 0) {
            if (!testing) {
                int newDeath = 10 + ((msRan / 1000) / 60);
                if (newDeath != deathTime) deathTime = newDeath;
                if (currentHealth < maxHealth) regenHealth();
            }
        }

        if (msRan % 500 == 0) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            Point2D blueFountain = handler.getFountainsCenter().get(1);
            if (location.distance(blueFountain) <= TOWER_RANGE && getHealth() != getMaxHealth()) {
                changeHealth(FOUNTAIN_HEAL);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "fx_health_regen",
                        3000,
                        id + "_fountainRegen",
                        true,
                        "Bip01",
                        false,
                        false,
                        team);
            }
        }

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // bot actions
        RoomHandler handler = parentExt.getRoomHandler(room.getName());

        List<Actor> enemyActorsInRadius =
                Champion.getEnemyActorsInRadius(handler, team, location, 5f);

        if (System.currentTimeMillis() - enemyDmgTime <= 5000
                && !enemy.isDead()
                && shouldAttackTarget(enemy)) {
            // Console.debugLog("Attack Player");
            attemptAttack(enemy);
            return;
        }

        if ((System.currentTimeMillis() - lastAttackedByMinion <= 1000
                        && getPHealth() < HP_PERCENT_MINIONS)
                || System.currentTimeMillis() - lastAttackedByTower <= 1000) {
            run();
            return;
        }

        for (Actor a : enemyActorsInRadius) {
            if (a instanceof UserActor) {

                if (shouldAttackTarget(a) && a.getLocation().distance(location) < 5) {
                    if (canUseW(a)) useW(a);
                    if (canUseE(a) && a.getHealth() > 0) useE();
                    // Console.debugLog("Attack Player");
                    attemptAttack(a);
                    return;
                } else {
                    break;
                }
            }
        }
    }

    @Override
    public void die(Actor a) {
        dead = true;
        currentHealth = 0;
        canMove = false;

        if (qActive) {
            handleQDeath();
        }

        if (furyTarget != null) {
            ExtensionCommands.removeFx(parentExt, room, furyTarget.getId() + "_mark" + furyStacks);
        }

        furyStacks = 0;
        furyTarget = null;

        if (!getState(ActorState.AIRBORNE)) stopMoving();
        ExtensionCommands.knockOutActor(parentExt, room, id, a.getId(), deathTime);
    }

    public void setLevel(int level) {
        this.level = level;
        levelUpStats();
    }
}

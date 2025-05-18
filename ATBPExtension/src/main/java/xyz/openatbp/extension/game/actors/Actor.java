package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.IceKing;
import xyz.openatbp.extension.pathfinding.MovementManager;

public abstract class Actor {
    protected static final float KNOCKBACK_SPEED = 11;
    protected static final int FEAR_MOVING_DISTANCE = 3; // should be close to og, subject to change

    public enum AttackType {
        PHYSICAL,
        SPELL
    }

    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected Line2D movementLine;
    protected boolean dead = false;
    protected float timeTraveled;
    protected String id;
    protected Room room;
    protected int team;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected int level = 1;
    protected boolean canMove = true;
    protected double attackCooldown;
    protected ActorType actorType;
    protected Map<ActorState, Boolean> states = Champion.getBlankStates();
    protected String displayName = "FuzyBDragon";
    protected Map<String, Double> stats;
    protected List<ISFSObject> damageQueue = new ArrayList<>();
    protected Actor target;
    protected List<Point2D> path;
    protected int pathIndex = 1;
    protected int xpWorth;
    protected String bundle;
    protected boolean towerAggroCompanion = false;
    protected Map<String, EffectHandler> effectHandlers = new HashMap<>();
    protected Map<String, FxHandler> fxHandlers = new HashMap<>();
    protected UserActor charmer;
    protected long lastHitElectrodeGun = -1;

    public double getPHealth() {
        return currentHealth / maxHealth;
    }

    public int getHealth() {
        return (int) currentHealth;
    }

    public int getMaxHealth() {
        return (int) maxHealth;
    }

    public Point2D getLocation() {
        return this.location;
    }

    public String getId() {
        return this.id;
    }

    public int getTeam() {
        return this.team;
    }

    public int getOppositeTeam() {
        if (this.getTeam() == 1) return 0;
        else return 1;
    }

    public void setLocation(Point2D location) {
        this.location = location;
        this.movementLine = new Line2D.Float(location, location);
        this.timeTraveled = 0f;
    }

    public String getAvatar() {
        return this.avatar;
    }

    public ActorType getActorType() {
        return this.actorType;
    }

    public void reduceAttackCooldown() {
        this.attackCooldown -= 100;
    }

    public boolean withinRange(Actor a) {
        if (a.getActorType() == ActorType.BASE)
            return a.getLocation().distance(this.location) - 1.5f
                    <= this.getPlayerStat("attackRange");
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public void stopMoving() {
        this.movementLine = new Line2D.Float(this.location, this.location);
        this.timeTraveled = 0f;
        this.clearPath();
        ExtensionCommands.moveActor(
                parentExt, this.room, this.id, this.location, this.location, 5f, false);
    }

    protected boolean isStopped() {
        if (this.movementLine == null)
            this.movementLine = new Line2D.Float(this.location, this.location);
        if (this.path != null)
            return this.path.get(this.path.size() - 1).distance(this.location) <= 0.01d;
        return this.location.distance(this.movementLine.getP2()) < 0.01d;
    }

    public void setCanMove(boolean move) {
        this.canMove = move;
    }

    public double getSpeed() {
        return this.getPlayerStat("speed");
    }

    public void setState(ActorState state, boolean enabled) {
        this.states.put(state, enabled);
        ExtensionCommands.updateActorState(this.parentExt, this.room, this.id, state, enabled);
    }

    public double getStat(String stat) {
        return this.stats.get(stat);
    }

    public void move(Point2D destination) {
        if (!this.canMove()) {
            return;
        }
        this.movementLine = new Line2D.Float(this.location, destination);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                destination,
                (float) this.getPlayerStat("speed"),
                true);
    }

    public boolean isNotAMonster(Actor a) {
        return a.getActorType() != ActorType.MONSTER;
    }

    public void moveWithCollision(Point2D dest) {
        List<Point2D> path = new ArrayList<>();
        try {
            path =
                    MovementManager.getPath(
                            this.parentExt.getRoomHandler(this.room.getName()),
                            this.location,
                            dest);
        } catch (Exception e) {
            Console.logWarning(this.id + " could not form a path.");
        }
        if (path != null && path.size() > 2) {
            this.setPath(path);
        } else {
            Line2D testLine = new Line2D.Float(this.location, dest);
            Point2D newPoint =
                    MovementManager.getPathIntersectionPoint(
                            this.parentExt,
                            this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap(),
                            testLine);
            if (newPoint != null) {
                this.move(newPoint);
            } else this.move(dest);
        }
    }

    public boolean isPointAtEndOfPath(Point2D point) {
        if (this.path != null) return point.distance(this.path.get(this.path.size() - 1)) <= 0.5d;
        else return point.distance(this.movementLine.getP2()) <= 0.5d;
    }

    public void setPath(List<Point2D> path) {
        if (path.size() == 0) {
            this.path = null;
            return;
        }
        Line2D pathLine = new Line2D.Float(this.location, path.get(1));
        Point2D dest =
                MovementManager.getPathIntersectionPoint(
                        parentExt,
                        this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap(),
                        pathLine);
        if (dest == null) dest = path.get(1);
        this.path = path;
        this.pathIndex = 1;
        this.move(dest);
    }

    public void clearPath() {
        this.path = null;
        this.pathIndex = 1;
    }

    public int getXPWorth() {
        return this.xpWorth;
    }

    protected void handleActiveEffects() {
        List<String> badKeys = new ArrayList<>();
        for (String k : this.effectHandlers.keySet()) {
            if (this.effectHandlers.get(k).update()) badKeys.add(k);
        }
        for (String k : badKeys) {
            this.effectHandlers.remove(k);
        }
        List<String> badFx = new ArrayList<>();
        for (String k : this.fxHandlers.keySet()) {
            if (this.fxHandlers.get(k).update()) badFx.add(k);
        }
        for (String k : badFx) {
            this.fxHandlers.remove(k);
        }
    }

    public void addFx(String fxId, String emit, int duration) {
        if (this.fxHandlers.get(fxId) != null) {
            this.fxHandlers.get(fxId).addFx(duration);
        } else {
            this.fxHandlers.put(fxId, new FxHandler(this, fxId, emit, duration));
        }
    }

    public void removeFx(String fxId) {
        if (this.fxHandlers.get(fxId) != null) {
            this.fxHandlers.get(fxId).forceStopFx();
        }
    }

    public void addEffect(String stat, double delta, int duration, String fxId, String emit) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (!this.effectHandlers.containsKey(stat))
            this.effectHandlers.put(stat, new EffectHandler(this, stat));
        this.effectHandlers.get(stat).addEffect(delta, duration);
        this.addFx(fxId, emit, duration);
    }

    public void addEffect(String stat, double delta, int duration) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (!this.effectHandlers.containsKey(stat))
            this.effectHandlers.put(stat, new EffectHandler(this, stat));
        this.effectHandlers.get(stat).addEffect(delta, duration);
    }

    public void addState(ActorState state, double delta, int duration) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (this.getState(ActorState.IMMUNITY) && this.isCC(state)) return;
        if (!this.effectHandlers.containsKey(state.toString()))
            this.effectHandlers.put(state.toString(), new EffectHandler(this, state));
        this.effectHandlers.get(state.toString()).addState(delta, duration);
    }

    public void addState(ActorState state, double delta, int duration, String fxId, String emit) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (this.getState(ActorState.IMMUNITY) && this.isCC(state)) return;
        if (!this.effectHandlers.containsKey(state.toString()))
            this.effectHandlers.put(state.toString(), new EffectHandler(this, state));
        this.effectHandlers.get(state.toString()).addState(delta, duration);
        this.addFx(fxId, emit, duration);
    }

    public void handleCharm(UserActor charmer, int duration) {
        if (!this.states.get(ActorState.CHARMED)
                && !this.states.get(ActorState.IMMUNITY)
                && !this.getId().contains("turret")
                && !this.getId().contains("decoy")) {
            // this.setState(ActorState.CHARMED, true);
            this.setTarget(charmer);
            this.movementLine = new Line2D.Float(this.location, charmer.getLocation());
            this.movementLine =
                    MovementManager.getColliderLine(this.parentExt, this.room, this.movementLine);
            this.timeTraveled = 0f;
            if (this.canMove()) this.moveWithCollision(this.movementLine.getP2());
            this.addState(ActorState.CHARMED, 0d, duration);
        }
    }

    public void moveTowardsCharmer(UserActor charmer) {
        int minVictimDist = 1;
        float dist = (float) this.location.distance(charmer.getLocation());
        if (canMove() && charmer.getHealth() > 0 && dist > minVictimDist) {
            Line2D movementLine =
                    Champion.getAbilityLine(
                            this.location, charmer.getLocation(), dist - minVictimDist);
            this.moveWithCollision(movementLine.getP2());
        }
    }

    public boolean hasMovementCC() {
        ActorState[] cc = {
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.ROOTED,
            ActorState.FEARED,
            ActorState.CHARMED
        };
        for (ActorState effect : cc) {
            if (this.states.get(effect)) return true;
        }
        return false;
    }

    public boolean hasAttackCC() {
        ActorState[] cc = {
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.BLINDED
        };
        for (ActorState effect : cc) {
            if (this.states.get(effect)) return true;
        }
        return false;
    }

    public void handleFear(Point2D source, int duration) {
        if (!this.states.get(ActorState.IMMUNITY)
                && !this.getId().contains("turret")
                && !this.getId().contains("decoy")) {
            this.target = null;
            if (!this.hasMovementCC()) {
                this.canMove = true;
                Line2D sourceToPlayer = new Line2D.Float(source, this.location);
                Line2D extendedLine = Champion.extendLine(sourceToPlayer, FEAR_MOVING_DISTANCE);
                this.moveWithCollision(extendedLine.getP2());
            }
            this.addState(ActorState.FEARED, 0d, duration);
        }
    }

    public boolean hasTempStat(String stat) {
        return this.effectHandlers.containsKey(stat);
    }

    public double getPlayerStat(String stat) {
        double currentStat = this.stats.get(stat);
        // Console.debugLog("Current Stat " + stat + " is " + currentStat);
        if (currentStat + this.getTempStat(stat) < 0) return 0; // Stat will never drop below 0
        return stat.equalsIgnoreCase("attackSpeed")
                ? getCappedAttackSpeed()
                : (currentStat + this.getTempStat(stat));
    }

    public double getTempStat(String stat) {
        double regularStat = 0d;
        if (this.effectHandlers.containsKey(stat))
            regularStat = this.effectHandlers.get(stat).getCurrentDelta();
        switch (stat) {
            case "speed":
                double slowStat = 0d;
                if (this.effectHandlers.containsKey(ActorState.SLOWED.toString())) {
                    slowStat =
                            this.effectHandlers.get(ActorState.SLOWED.toString()).getCurrentDelta();
                }
                return regularStat + slowStat;
        }
        // if (regularStat != 0) Console.debugLog("TempStat " + stat + " is " + regularStat);
        return regularStat;
    }

    private double getCappedAttackSpeed() {
        double currentAttackSpeed = this.stats.get("attackSpeed") + this.getTempStat("attackSpeed");
        return currentAttackSpeed < 500 ? 500 : currentAttackSpeed;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public abstract void handleKill(Actor a, JsonNode attackData);

    public void handleElectrodeGun(UserActor ua, Actor a, int damage, JsonNode attackData) {
        if (ChampionData.getJunkLevel(ua, "junk_2_electrode_gun") > 0) {
            if (Math.random() <= 0.25d) {
                for (Actor actor :
                        Champion.getActorsInRadius(
                                this.parentExt.getRoomHandler(this.room.getName()),
                                this.location,
                                2f)) {
                    if (actor.getTeam() == this.team
                            && !actor.getId().equalsIgnoreCase(this.id)
                            && actor.canHitWithElectrode()) {
                        a.hitWithElectrode();
                        actor.addToDamageQueue(
                                a,
                                damage * ChampionData.getCustomJunkStat(ua, "junk_2_electrode_gun"),
                                attackData,
                                false);
                        // TODO: Set different attack data for electrode gun damage
                        Console.debugLog("Damage from electrode gun!");
                    }
                }
            }
        }
    }

    public void hitWithElectrode() {
        this.lastHitElectrodeGun = System.currentTimeMillis();
    }

    public boolean canHitWithElectrode() {
        return System.currentTimeMillis() - this.lastHitElectrodeGun > 1000;
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (a.getClass() == IceKing.class && this.hasMovementCC()) damage *= 1.1;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                    && getAttackType(attackData) == AttackType.SPELL) {
                if (ua.getLocation().distance(this.location) < 2d) {
                    damage +=
                            (damage * ChampionData.getCustomJunkStat(ua, "junk_2_peppermint_tank"));
                    Console.debugLog("Increased damage from peppermint tank.");
                }
            }
            // this.handleElectrodeGun(ua, a, damage, attackData);
            if (ua.lichHandDamageApplies(this)) {
                Console.debugLog("Lich hand damage " + ua.getLichHandTimesHit());
                damage += ((double) damage * (0.1d * ua.getLichHandTimesHit()));
                Console.debugLog("New damage from Lich Hand: " + damage);
                ua.handleLichHandHit();
            } else if (ChampionData.getJunkLevel(ua, "junk_2_lich_hand") > 0
                    && (ua.getLichVictim() == null
                            || !ua.getLichVictim().equalsIgnoreCase(this.id))) {
                Console.debugLog("Setting Lich Victim");
                ua.setLichVictim(this.id);
            }
        }

        this.currentHealth -= damage;
        if (this.currentHealth <= 0) this.currentHealth = 0;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) this.currentHealth);
        updateData.putDouble("pHealth", this.getPHealth());
        updateData.putInt("maxHealth", (int) this.maxHealth);
        ExtensionCommands.updateActorData(parentExt, this.room, updateData);
        return this.currentHealth <= 0;
    }

    public void addToDamageQueue(
            Actor attacker, double damage, JsonNode attackData, boolean dotDamage) {
        if (this.currentHealth <= 0) return;
        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker.getActorType() == ActorType.PLAYER
                && this.getAttackType(attackData) == AttackType.SPELL
                && this.getActorType() != ActorType.TOWER
                && this.getActorType() != ActorType.BASE
                && !attackData.get("spellName").asText().equalsIgnoreCase("flame cloak")) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }

        if (attacker instanceof UserActor && getAttackType(attackData) == AttackType.PHYSICAL) {

            UserActor ua = (UserActor) attacker;
            if (ua.hasBackpackItem("junk_1_sai")) {
                int critChance = (int) ua.getPlayerStat("criticalChance");
                Long lastSaiProc = ua.getLastSaiProcTime();

                Random random = new Random();
                int randomNumber = random.nextInt(100);
                boolean proc = randomNumber < critChance;

                int SAI_CD = UserActor.SAI_PROC_COOLDOWN;

                if (proc && System.currentTimeMillis() - lastSaiProc >= SAI_CD) {
                    ua.setLastSaiProcTime(System.currentTimeMillis());
                    double delta = this.getPlayerStat("armor") * -((double) critChance / 100.0);

                    Console.debugLog("armor: " + this.getPlayerStat("armor"));
                    Console.debugLog("armor delta: " + delta);
                    this.addEffect("armor", delta, SAI_CD);
                }
            }
        }
    }

    public void addToDamageQueue(
            Actor attacker,
            double damage,
            JsonNode attackData,
            boolean dotDamage,
            String debugString) {
        if (this.currentHealth <= 0) return;
        if (attacker.getActorType() == ActorType.PLAYER)
            Console.debugLog(
                    attacker.getDisplayName()
                            + " is adding damage to "
                            + this.id
                            + " at "
                            + System.currentTimeMillis()
                            + " with "
                            + debugString);
        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker.getActorType() == ActorType.PLAYER
                && this.getAttackType(attackData) == AttackType.SPELL
                && this.getActorType() != ActorType.TOWER
                && this.getActorType() != ActorType.BASE) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }
    }

    public void handleDamageQueue() {
        List<ISFSObject> queue = new ArrayList<>(this.damageQueue);
        this.damageQueue = new ArrayList<>();
        if (this.currentHealth <= 0 || this.dead) {
            return;
        }
        for (ISFSObject data : queue) {
            Actor damager = (Actor) data.getClass("attacker");
            double damage = data.getDouble("damage");
            JsonNode attackData = (JsonNode) data.getClass("attackData");
            if (this.damaged(damager, (int) damage, attackData)) {
                if (damager.getId().contains("turret") || damager.getId().contains("skully")) {
                    int enemyTeam = damager.getTeam() == 0 ? 1 : 0;
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());

                    if (damager.getId().contains("turret")) {
                        damager = rh.getEnemyChampion(enemyTeam, "princessbubblegum");
                    } else if (damager.getId().contains("skully")) {
                        damager = rh.getEnemyChampion(enemyTeam, "lich");
                    }
                }
                damager.handleKill(this, attackData);
                this.die(damager);
                return;
            }
        }
    }

    public abstract void attack(Actor a);

    public abstract void die(Actor a);

    public abstract void update(int msRan);

    public void rangedAttack(Actor a) {
        System.out.println(this.id + " is using an undefined method.");
    }

    public Room getRoom() {
        return this.room;
    }

    public void changeHealth(int delta) {
        ISFSObject data = new SFSObject();
        this.currentHealth += delta;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public void handleStructureRegen(
            Long lastAction, int TIME_REQUIRED_TO_REGEN, float REGEN_VALUE) {
        if (System.currentTimeMillis() - lastAction >= TIME_REQUIRED_TO_REGEN
                && getHealth() != maxHealth) {
            int delta = (int) (getMaxHealth() * REGEN_VALUE);
            changeHealth(delta);
        }
    }

    public void heal(int delta) {
        this.changeHealth(delta);
    }

    public void setHealth(int currentHealth, int maxHealth) {
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        ISFSObject data = new SFSObject();
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        data.putInt("health", (int) this.maxHealth);
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public int getMitigatedDamage(double rawDamage, AttackType attackType, Actor attacker) {
        try {
            double armor = this.getPlayerStat("armor") - attacker.getPlayerStat("armorPenetration");
            double spellResist =
                    this.getPlayerStat("spellResist") - attacker.getPlayerStat("spellPenetration");
            if (armor < 0) armor = 0;
            if (spellResist < 0) spellResist = 0;
            if (armor > 65) armor = 65;
            if (spellResist > 65) spellResist = 65;
            double modifier;
            if (attackType == AttackType.PHYSICAL) {
                modifier = (100 - armor) / 100d; // Max Armor 80
            } else modifier = (100 - spellResist) / 100d; // Max Shields 70
            return (int) Math.round(rawDamage * modifier);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void removeEffects() {
        for (String k : this.effectHandlers.keySet()) {
            this.effectHandlers.get(k).endAllEffects();
        }
    }

    public void setStat(String key, double value) {
        this.stats.put(key, value);
    }

    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar);
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    public void snap(Point2D newLocation) {
        ExtensionCommands.snapActor(
                this.parentExt, this.room, this.id, this.location, newLocation, true);
        this.location = newLocation;
        this.timeTraveled = 0f;
        this.movementLine = new Line2D.Float(this.location, this.movementLine.getP2());
    }

    protected AttackType getAttackType(JsonNode attackData) {
        if (attackData.has("spellType")) return AttackType.SPELL;
        String type = attackData.get("attackType").asText();
        if (type.equalsIgnoreCase("physical")) return AttackType.PHYSICAL;
        else return AttackType.SPELL;
    }

    public ATBPExtension getParentExt() {
        return this.parentExt;
    }

    public void addDamageGameStat(UserActor ua, double value, AttackType type) {
        ua.addGameStat("damageDealtTotal", value);
        if (type == AttackType.PHYSICAL) ua.addGameStat("damageDealtPhysical", value);
        else ua.addGameStat("damageDealtSpell", value);
    }

    public boolean canMove() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED
                    || s == ActorState.AIRBORNE) {
                if (this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    public boolean getState(ActorState state) {
        return this.states.get(state);
    }

    private boolean isCC(ActorState state) {
        ActorState[] cc = {
            ActorState.SLOWED,
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.ROOTED,
            ActorState.BLINDED,
            ActorState.SILENCED,
            ActorState.FEARED,
            ActorState.CHARMED,
            ActorState.POLYMORPH
        };
        for (ActorState c : cc) {
            if (state == c) return true;
        }
        return false;
    }

    public boolean canAttack() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED
                    || s == ActorState.AIRBORNE
                    || s == ActorState.POLYMORPH) {
                if (this.states.get(s)) return false;
            }
        }
        if (this.attackCooldown < 0) this.attackCooldown = 0;
        return this.attackCooldown == 0;
    }

    public void knockback(Point2D source, float distance) {
        this.stopMoving();
        boolean isPracticeMap = this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap();
        Line2D originalLine = new Line2D.Double(source, this.location);
        if (MovementManager.insideAnyObstacle(this.parentExt, isPracticeMap, source)) {
            for (Point2D p : MovementManager.findAllPoints(originalLine)) {
                if (!MovementManager.insideAnyObstacle(this.parentExt, isPracticeMap, p)) {
                    originalLine = new Line2D.Double(p, this.location);
                    break;
                }
            }
        }
        Line2D knockBackLine = Champion.extendLine(originalLine, distance);
        Line2D actualKnockbackLine = new Line2D.Double(this.location, knockBackLine.getP2());
        Point2D finalPoint =
                MovementManager.getPathIntersectionPoint(
                        this.parentExt, isPracticeMap, actualKnockbackLine);
        if (finalPoint == null) finalPoint = knockBackLine.getP2();
        Line2D finalLine = new Line2D.Double(this.location, finalPoint);
        double time = this.location.distance(finalLine.getP2()) / KNOCKBACK_SPEED;
        int durationMs = (int) (time * 1000);
        this.addState(ActorState.AIRBORNE, 0d, durationMs);
        ExtensionCommands.knockBackActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                finalLine.getP2(),
                KNOCKBACK_SPEED,
                false);
        this.setLocation(finalLine.getP2());
    }

    public void handlePathing() {
        if (this.path != null && this.location.distance(this.movementLine.getP2()) <= 0.9d) {
            if (this.pathIndex + 1 < this.path.size()) {
                this.pathIndex++;
                this.move(this.path.get(this.pathIndex));
            } else {
                this.path = null;
            }
        }
    }

    public boolean isDead() {
        return this.dead;
    }

    protected boolean isPointNearDestination(Point2D p) {
        if (this.path != null) return this.path.get(this.path.size() - 1).distance(p) <= 0.2d;
        else return this.movementLine.getP2().distance(p) <= 0.2d;
    }

    public void handlePull(Point2D source, double pullDistance) {
        this.stopMoving();
        double distance = this.location.distance(source);
        if (distance < pullDistance) pullDistance = distance;
        Line2D pullingLine = Champion.getAbilityLine(this.location, source, (float) pullDistance);
        Point2D pullDestination = MovementManager.getDashPoint(this, pullingLine);
        double finalDistance = this.location.distance(pullDestination);
        double speed = finalDistance / 0.25;
        double pullTime = (finalDistance / speed) * 1000;
        this.addState(ActorState.AIRBORNE, 0d, (int) pullTime);
        ExtensionCommands.knockBackActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                pullDestination,
                (float) speed,
                true);
        this.setLocation(pullDestination);
    }

    public String getPortrait() {
        return this.getAvatar();
    }

    public String getFrame() {
        if (this.getActorType() == ActorType.PLAYER) {
            String[] frameComponents = this.getAvatar().split("_");
            if (frameComponents.length > 1) {
                return frameComponents[0];
            } else {
                return this.getAvatar();
            }
        } else {
            return this.getAvatar();
        }
    }

    public String getSkinAssetBundle() {
        return this.parentExt.getActorData(this.avatar).get("assetBundle").asText();
    }

    public abstract void setTarget(Actor a);
}

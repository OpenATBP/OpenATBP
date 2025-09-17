package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.champions.Fionna;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.pathfinding.MovementManager;

public class UserActor extends Actor {
    protected static final int DEMON_SWORD_AD_BUFF = 15;
    protected static final int DEMON_SWORD_SD_BUFF = 40;
    protected static final int NAIL_STACKS_PER_CHAMP = 7;
    protected static final int NAIL_STACKS_PER_NON_CHAMPS = 4;
    protected static final int LIGHTNING_SWORD_STACKS_PER_CHAMP = 7;
    protected static final int LIGHTNING_SWORD_STACKS_PER_NON_CHAMP = 4;
    protected static final double ROBE_CD_CHAMP_OR_JG_BOSS_KO = 1;
    protected static final double ROBE_CD_MINION_KO = 0.2;
    protected static final int DAMAGE_PER_NAIL_POINT = 30;
    protected static final int DAMAGE_PER_LIGHTNING_POINT = 55;
    protected static final int CDR_PER_ROBE_POINT = 10;
    protected static final int SAI_PROC_COOLDOWN = 3000;
    protected static final double GRASS_CRIT_INCREASE = 1.25d;
    protected static final double SPEED_BOOST_PER_ROBO_STACK = 0.05;
    protected static final double ROBO_SLOW_VALUE = 0.2;
    protected static final int ROBO_SLOW_DURATION = 2000;
    protected static final int ROBO_CD = 10000;
    protected static final int SIMON_GLASSES_RANGE = 5;

    protected static final int BASIC_ATTACK_DELAY = 500;
    protected static final double DASH_SPEED = 20d;
    protected static final int HEALTH_PACK_REGEN = 15;
    protected static final float DC_AD_BUFF = 1.2f;
    protected static final float DC_ARMOR_BUFF = 1.2f;
    protected static final float DC_SPELL_RESIST_BUFF = 1.2f;
    protected static final float DC_SPEED_BUFF = 1.15f;
    protected static final float DC_PD_BUFF = 1.2f;

    protected User player;
    protected boolean autoAttackEnabled = false;
    protected int xp = 0;
    private int deathTime = 10;
    private long timeKilled;
    protected Map<Actor, ISFSObject> aggressors = new HashMap<>();
    protected String backpack;
    private boolean futureCrystalActive = true;
    protected int magicNailStacks = 0;
    protected int lightningSwordStacks = 0;
    protected double robeStacks = 0;
    protected Map<String, Double> endGameStats = new HashMap<>();
    protected int killingSpree = 0;
    protected int multiKill = 0;
    protected long lastKilled = System.currentTimeMillis();
    protected int dcBuff = 0;
    protected boolean[] canCast = {true, true, true};
    protected Map<String, ScheduledFuture<?>> iconHandlers = new HashMap<>();
    protected int idleTime = 0;
    protected boolean changeTowerAggro = false;
    protected boolean isDashing = false;
    private long lastHit = 0;
    protected boolean isAutoAttacking = false;
    // Set debugging options via config.properties next to the extension jar
    protected static boolean movementDebug;
    private static boolean invincibleDebug;
    private static boolean abilityDebug;
    private static boolean speedDebug;
    private static boolean damageDebug;
    protected double hits = 0;
    private Point2D queuedDest = null;
    protected boolean pickedUpHealthPack = false;
    protected long healthPackPickUpTime = 0;
    protected boolean hasKeeothBuff = false;
    protected boolean hasGooBuff = false;
    protected long keeothBuffStartTime = 0;
    protected long gooBuffStartTime = 0;
    protected List<UserActor> killedPlayers = new ArrayList<>();
    protected long lastAutoTargetTime = 0;
    protected long stealthEmbargo = -1;
    private boolean moonVfxActivated = false;
    protected double glassesBuff = 0;
    protected long spellShieldCooldown = -1;
    protected boolean spellShieldActive = false;
    protected long iFrame = -1;
    protected String numbChuckVictim;
    protected boolean numbSlow = false;
    protected int roboStacks = 0;
    protected List<Monster.BuffType> activeMonsterBuffs = new ArrayList<>();
    protected Actor lichHandTarget;
    protected long lastLichHandStack = 0L;
    protected int lichHandStacks = 0;
    protected long lastAuto = -1;
    protected long lastSpell = -1;
    protected int fightKingStacks = 0;
    protected int cosmicStacks = 0;
    private boolean flameCloakEffectActivated = false;
    protected boolean hasGlassesPoint = false;
    protected Long lastSaiProcTime = 0L;
    protected Long lastZeldronBuff = 0L;
    protected Long lastRoboEffect = 0L;
    protected HashMap<UserActor, Integer> simonGlassesBuffProviders = new HashMap<>(2);

    // TODO: Add all stats into UserActor object instead of User Variables
    public UserActor(User u, ATBPExtension parentExt) {
        this.parentExt = parentExt;
        this.id = String.valueOf(u.getId());
        this.team = u.getVariable("player").getSFSObjectValue().getInt("team");
        player = u;
        this.avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        this.displayName = u.getVariable("player").getSFSObjectValue().getUtfString("name");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        this.location = new Point2D.Float(x, z);
        this.movementLine = new Line2D.Float(this.location, this.location);
        this.stats = this.initializeStats();
        this.attackCooldown = this.stats.get("attackSpeed");
        this.currentHealth = this.stats.get("health");
        this.maxHealth = this.currentHealth;
        this.room = u.getLastJoinedRoom();
        this.actorType = ActorType.PLAYER;
        this.backpack = u.getVariable("player").getSFSObjectValue().getUtfString("backpack");
        this.xpWorth = 25;

        for (String k : this.stats.keySet()) {
            if (k.contains("PerLevel")) {
                String stat = k.replace("PerLevel", "");
                double levelStat = this.stats.get(k);
                if (k.equalsIgnoreCase("healthPerLevel")) {
                    this.setHealth(
                            (int) ((this.getMaxHealth() + levelStat) * this.getPHealth()),
                            (int) (this.getMaxHealth() + levelStat));
                } else if (k.contains("attackSpeed")) {
                    this.increaseStat(stat, (levelStat * -1));
                } else {
                    this.increaseStat(stat, levelStat);
                }
            }
        }

        Properties props = parentExt.getConfigProperties();
        movementDebug = Boolean.parseBoolean(props.getProperty("movementDebug", "false"));
        invincibleDebug = Boolean.parseBoolean(props.getProperty("invincibleDebug", "false"));
        abilityDebug = Boolean.parseBoolean(props.getProperty("abilityDebug", "false"));
        speedDebug = Boolean.parseBoolean(props.getProperty("speedDebug", "false"));
        damageDebug = Boolean.parseBoolean(props.getProperty("damageDebug", "false"));
        if (movementDebug)
            ExtensionCommands.createActor(
                    this.parentExt,
                    this.room,
                    this.id + "_movementDebug",
                    "creep1",
                    this.location,
                    0f,
                    2);
        if (speedDebug) this.setStat("speed", 20);
        if (damageDebug) this.setStat("attackDamage", 1000);
    }

    @Override
    public void setStat(String stat, double value) {
        super.setStat(stat, value);
        if (!stat.toLowerCase().contains("sp") && !stat.equalsIgnoreCase("speed"))
            this.updateStatMenu(stat);
    }

    public void setAutoAttackEnabled(boolean enabled) {
        this.autoAttackEnabled = enabled;
    }

    public void setHasKeeothBuff(boolean hasBuff) {
        this.hasKeeothBuff = hasBuff;
    }

    public void setHasGooBuff(boolean hasBuff) {
        this.hasGooBuff = hasBuff;
    }

    public void setKeeothBuffStartTime(long keeothBuffStartTime) {
        this.keeothBuffStartTime = keeothBuffStartTime;
    }

    public void setGooBuffStartTime(long gooBuffStartTime) {
        this.gooBuffStartTime = gooBuffStartTime;
    }

    public void setCanCast(boolean q, boolean w, boolean e) {
        this.canCast[0] = q;
        this.canCast[1] = w;
        this.canCast[2] = e;
    }

    public void setLichHandTarget(Actor target) {
        this.lichHandTarget = target;
    }

    public Actor getLichHandTarget() {
        return this.lichHandTarget;
    }

    public void setLichHandStacks(int stacks) {
        this.lichHandStacks = stacks;
    }

    public int getLichHandStacks() {
        return this.lichHandStacks;
    }

    public void setLastLichHandStack(Long time) {
        this.lastLichHandStack = time;
    }

    public Long getLastLichHandStack() {
        return this.lastLichHandStack;
    }

    public void setLastSaiProcTime(Long time) {
        this.lastSaiProcTime = time;
    }

    public Long getLastSaiProcTime() {
        return this.lastSaiProcTime;
    }

    public void setLastZedronBuff(Long time) {
        this.lastZeldronBuff = time;
    }

    public Long getLastZeldronBuff() {
        return this.lastZeldronBuff;
    }

    protected Point2D getRelativePoint(
            boolean external) { // Gets player's current location based on time
        double currentTime;
        if (external) currentTime = this.timeTraveled + 0.1;
        else currentTime = this.timeTraveled;
        Point2D rPoint = new Point2D.Float();
        if (this.movementLine == null)
            this.movementLine = new Line2D.Float(this.location, this.location);
        float x2 = (float) this.movementLine.getX2();
        float y2 = (float) this.movementLine.getY2();
        float x1 = (float) this.movementLine.getX1();
        float y1 = (float) this.movementLine.getY1();
        double dist = this.movementLine.getP1().distance(this.movementLine.getP2());
        if (dist == 0) return this.movementLine.getP1();
        double speed = this.getPlayerStat("speed");
        double time = dist / speed;
        if (currentTime > time) currentTime = time;
        double currentDist = speed * currentTime;
        float x = (float) (x1 + (currentDist / dist) * (x2 - x1));
        float y = (float) (y1 + (currentDist / dist) * (y2 - y1));
        if (!this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap()) {
            if (x >= 52) x = 52;
            else if (x <= -52) x = -52;
        } else {
            if (x >= 62) x = 62;
            else if (x <= -62) x = -62;
        }

        if (y >= 30) y = 30;
        else if (y <= -30) y = -30;
        rPoint.setLocation(x, y);
        this.location = rPoint;
        if (!this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap()) {
            if (x >= 52 || x <= -52 || y >= 30 || y <= -30) this.stopMoving();
        } else {
            if (x >= 62 || x <= -62 || y >= 30 || y <= -30) this.stopMoving();
        }

        return rPoint;
    }

    @Override
    public Room getRoom() {
        return this.room;
    }

    public int getXp() {
        return this.xp;
    }

    public Map<String, Double> getStats() {
        return this.stats;
    }

    public double getStat(String stat) {
        return this.stats.get(stat);
    }

    public boolean[] getCanCast() {
        return canCast;
    }

    public int getMultiKill() {
        return multiKill;
    }

    public int getKillingSpree() {
        return killingSpree;
    }

    public long getLastKilled() {
        return lastKilled;
    }

    public void setLastKilled(Long time) {
        this.lastKilled = time;
    }

    public List<UserActor> getKilledPlayers() {
        return killedPlayers;
    }

    public void setPath(Point2D start, Point2D end) {
        this.movementLine = new Line2D.Float(start, end);
        this.timeTraveled = 0f;
    }

    public void setPath(Line2D path) {
        this.movementLine = path;
        this.timeTraveled = 0f;
    }

    public void updateMovementTime() {
        this.timeTraveled += 0.1f;
    }

    public User getUser() {
        return this.player;
    }

    public boolean getIsDashing() {
        return this.isDashing;
    }

    public boolean getIsAutoAttacking() {
        return this.isAutoAttacking;
    }

    public void move(ISFSObject params, Point2D destination) {
        Point2D orig = new Point2D.Float(params.getFloat("orig_x"), params.getFloat("orig_z"));
        this.location = orig;
        this.movementLine = new Line2D.Float(orig, destination);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                destination,
                (float) this.getPlayerStat("speed"),
                params.getBool("orient"));
    }

    public void addHit(boolean dotDamage) {
        if (!dotDamage) this.hits++;
        else this.hits += 0.2d;
    }

    protected JsonNode getSpellData(int spell) {
        JsonNode actorDef = parentExt.getDefinition(this.avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell" + spell);
    }

    public void preventStealth() {
        Console.debugLog("Prevent stealth");
        this.addState(ActorState.REVEALED, 0d, 3000);
        this.setState(ActorState.INVISIBLE, false);
        this.stealthEmbargo = System.currentTimeMillis() + 3000;
        if (this.roboStacks > 0) this.roboStacks = 0;
    }

    public void resetFightKingStacks() {
        Console.debugLog("Reset fight king stack");
        this.fightKingStacks = 0;
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "fight_king_icon");
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try {
            if (invincibleDebug) return false;
            if (this.dead) return true;
            if (a.getActorType() == ActorType.PLAYER) checkTowerAggro((UserActor) a);
            if (a.getActorType() == ActorType.COMPANION) {
                checkTowerAggroCompanion(a);
            }

            if (this.pickedUpHealthPack) {
                removeHealthPackEffect();
            }
            this.lastHit = System.currentTimeMillis();
            if (a.getActorType() == ActorType.TOWER) {
                ExtensionCommands.playSound(
                        this.parentExt,
                        this.room,
                        this.id,
                        "sfx_turret_shot_hits_you",
                        this.location);
            }

            if (roboStacks == 3) {
                resetRoboStacks();
                if (isNeitherStructureNorAlly(a)) {
                    lastRoboEffect = System.currentTimeMillis();
                    this.addState(ActorState.SLOWED, ROBO_SLOW_VALUE, ROBO_SLOW_DURATION);
                }
            }

            AttackType type = this.getAttackType(attackData);
            this.preventStealth();
            double moonChance = ChampionData.getCustomJunkStat(this, "junk_3_battle_moon");
            if (moonChance > 0) {
                if (Math.random() < moonChance) {
                    Console.debugLog("Moon blocked damage! Chance: " + moonChance);
                    ExtensionCommands.playSound(
                            this.parentExt,
                            this.room,
                            this.id,
                            "sfx_junk_battle_moon",
                            this.getRelativePoint(false));
                    return false;
                }
            }
            int newDamage = damage;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                this.addDamageGameStat(ua, newDamage, type);
                if (type == AttackType.SPELL
                        && ChampionData.getJunkLevel(ua, "junk_1_fight_king_sword") > 0) {
                    newDamage += 15 * fightKingStacks;
                    ua.resetFightKingStacks();
                }
                double cubeEffect = ChampionData.getCustomJunkStat(ua, "junk_4_antimagic_cube");
                if (!this.effectHandlers.containsKey("spellDamage")
                        && type == AttackType.SPELL
                        && cubeEffect != -1) {
                    this.addEffect("spellDamage", cubeEffect, 5000);
                    // TODO: Add icon for this effect
                    // TODO: Bug, seems to not apply consistently. Especially with dot / constant
                    // abilities.
                }
                if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                        && type == AttackType.SPELL) {
                    if (ua.getLocation().distance(this.location) < 2d) {
                        String item = "junk_2_peppermint_tank";
                        double junkStat = ChampionData.getCustomJunkStat(ua, item);
                        newDamage += (int) (newDamage * junkStat);
                    }
                }
                // this.handleElectrodeGun(ua, a, damage, attackData);

                if (this.maxHealth > ua.getMaxHealth()
                        && ChampionData.getJunkLevel(ua, "junk_3_globs_helmet") > 0) {
                    String item = "junk_3_globs_helmet";
                    double junkStat = ChampionData.getCustomJunkStat(ua, item);
                    newDamage += (int) (newDamage * junkStat);
                }

                if (type == AttackType.SPELL
                        && (this.spellShieldActive || System.currentTimeMillis() < iFrame)) {
                    if (this.spellShieldActive) triggerSpellShield();
                    return false;
                }
            }
            newDamage = this.getMitigatedDamage(newDamage, type, a);
            this.handleDamageTakenStat(type, newDamage);
            ExtensionCommands.damageActor(parentExt, this.room, this.id, newDamage);
            this.processHitData(a, attackData, newDamage);
            if (this.hasTempStat("healthRegen")) {
                this.effectHandlers.get("healthRegen").endAllEffects();
            }
            this.changeHealth(newDamage * -1);
            if (this.currentHealth > 0) return false;
            else {
                if (this.getClass() == Fionna.class) {
                    Fionna f = (Fionna) this;
                    if (f.ultActivated()) {
                        this.setHealth(1, (int) this.maxHealth);
                        return false;
                    }
                }
                if (this.futureCrystalActive
                        && ChampionData.getJunkLevel(this, "junk_4_future_crystal") > 0) {
                    if (Math.random()
                            < ChampionData.getCustomJunkStat(this, "junk_4_future_crystal")) {
                        this.futureCrystalActive = false;
                        int targetHealth = (int) Math.round(this.maxHealth * 0.3d);
                        this.changeHealth(targetHealth - this.getHealth());
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void triggerSpellShield() {
        this.spellShieldActive = false;
        this.spellShieldCooldown = System.currentTimeMillis() + 90000;
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_spellShield");
        ExtensionCommands.removeStatusIcon(
                this.parentExt, this.getUser(), "junk_4_grob_gob_glob_grod_name");
        this.iFrame = System.currentTimeMillis() + 500;
    }

    public double getAttackCooldown() {
        return this.attackCooldown;
    }

    public double handleGrassSwordProc(double damage) { // TODO: Add indicator or something
        return damage * GRASS_CRIT_INCREASE;
    }

    @Override
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
            this.preventStealth();
            this.setLastAuto();
            double critChance = this.getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    crit,
                    true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            double damage = this.getPlayerStat("attackDamage");
            if (crit) {
                damage *= this.getPlayerStat("criticalDamage");
                damage = this.handleGrassSwordProc(damage);
            }
            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, (int) damage, "basicAttack");
            try {
                String projectileFx =
                        this.parentExt
                                .getActorData(this.getAvatar())
                                .get("scriptData")
                                .get("projectileAsset")
                                .asText();
                if (projectileFx != null
                        && !projectileFx.isEmpty()
                        && !parentExt
                                .getActorData(this.avatar)
                                .get("attackType")
                                .asText()
                                .equalsIgnoreCase("MELEE")) {
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new RangedAttack(a, delayedAttack, projectileFx),
                                    BASIC_ATTACK_DELAY,
                                    TimeUnit.MILLISECONDS);
                } else {
                    parentExt
                            .getTaskScheduler()
                            .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
                }

            } catch (NullPointerException e) {
                // e.printStackTrace();
                parentExt
                        .getTaskScheduler()
                        .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void applyStopMovingDuringAttack() {
        if (this.parentExt.getActorData(this.getAvatar()).has("attackType")) {
            this.preventStealth();
            this.stopMoving();
            this.isAutoAttacking = true;
            Runnable resetIsAttacking = () -> this.isAutoAttacking = false;
            scheduleTask(resetIsAttacking, BASIC_ATTACK_DELAY);
        }
    }

    public void checkTowerAggro(UserActor ua) {
        if (isInTowerRadius(ua, false)) ua.changeTowerAggro = true;
    }

    public void checkTowerAggroCompanion(Actor a) {
        if (isInTowerRadius(a, false)) a.towerAggroCompanion = true;
    }

    public boolean isInTowerRadius(Actor a, boolean ownTower) {
        HashMap<String, Point2D> towers;
        List<Point2D> towerLocations = new ArrayList<>();
        HashMap<String, Point2D> baseTowers;
        String roomGroup = room.getGroupId();
        if (room.getGroupId().equalsIgnoreCase("practice")) {
            if (ownTower) {
                if (a.getTeam() == 1) {
                    towers = MapData.getPTowerActorData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                } else {
                    towers = MapData.getPTowerActorData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                }
            } else {
                if (a.getTeam() == 1) {
                    towers = MapData.getPTowerActorData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                } else {
                    towers = MapData.getPTowerActorData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                }
            }
        } else {
            if (ownTower) {
                if (a.getTeam() == 1) {
                    towers = MapData.getMainMapTowerData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                } else {
                    towers = MapData.getMainMapTowerData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                }
            } else {
                if (a.getTeam() == 1) {
                    towers = MapData.getMainMapTowerData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                } else {
                    towers = MapData.getMainMapTowerData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                }
            }
        }
        for (String key : baseTowers.keySet()) {
            towerLocations.add(baseTowers.get(key));
        }
        for (String key : towers.keySet()) {
            towerLocations.add(towers.get(key));
        }
        for (Point2D location : towerLocations) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            if (Champion.getActorsInRadius(handler, location, 6f).contains(a)) {
                return true;
            }
        }
        return false;
    }

    public Point2D dash(Point2D dest, boolean noClip, double dashSpeed) {
        this.isDashing = true;
        Point2D dashPoint =
                MovementManager.getDashPoint(this, new Line2D.Float(this.location, dest));
        if (dashPoint == null) dashPoint = this.location;
        if (movementDebug)
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "gnome_a",
                    this.id + "_test" + Math.random(),
                    5000,
                    (float) dashPoint.getX(),
                    (float) dashPoint.getY(),
                    false,
                    0,
                    0f);
        // if(noClip) dashPoint =
        // Champion.getTeleportPoint(this.parentExt,this.player,this.location,dest);
        double time = dashPoint.distance(this.location) / dashSpeed;
        int timeMs = (int) (time * 1000d);
        this.stopMoving(timeMs);
        Runnable setIsDashing = () -> this.isDashing = false;
        parentExt.getTaskScheduler().schedule(setIsDashing, timeMs, TimeUnit.MILLISECONDS);
        ExtensionCommands.moveActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                dashPoint,
                (float) dashSpeed,
                true);
        this.setLocation(dashPoint);
        this.target = null;
        return dashPoint;
    }

    public void dash(Point2D dest, double dashSpeed) {
        this.isDashing = true;
        if (movementDebug)
            ExtensionCommands.createWorldFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "gnome_a",
                    this.id + "_test" + Math.random(),
                    5000,
                    (float) dest.getX(),
                    (float) dest.getY(),
                    false,
                    0,
                    0f);
        // if(noClip) dashPoint =
        // Champion.getTeleportPoint(this.parentExt,this.player,this.location,dest);
        double time = dest.distance(this.location) / dashSpeed;
        int timeMs = (int) (time * 1000d);
        this.stopMoving(timeMs);
        Runnable setIsDashing = () -> this.isDashing = false;
        parentExt.getTaskScheduler().schedule(setIsDashing, timeMs, TimeUnit.MILLISECONDS);
        ExtensionCommands.moveActor(
                this.parentExt, this.room, this.id, this.location, dest, (float) dashSpeed, true);
        this.setLocation(dest);
        this.target = null;
    }

    protected boolean handleAttack(Actor a) {
        if (this.attackCooldown == 0) {
            double critChance = this.getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;
            boolean critAnimation = crit;
            String[] skinsWithNoCritAnimation = {
                "princessbubblegum_skin_hoth", "princessbubblegum_skin_warrior"
            };
            for (String skin : skinsWithNoCritAnimation) {
                if (this.avatar.equals(skin)) {
                    critAnimation = false;
                    break;
                }
            }
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    critAnimation,
                    true);
            this.attackCooldown = this.getPlayerStat("attackSpeed");
            this.preventStealth();
            this.setLastAuto();
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            return crit;
        }
        return false;
    }

    public void autoAttack(Actor a) {
        this.attack(a);
    }

    public void reduceAttackCooldown() {
        this.attackCooldown -= 100;
        if (this.attackCooldown < 0) this.attackCooldown = 0;
    }

    protected boolean isNeitherStructureNorAlly(Actor a) {
        return a.getTeam() != this.team
                && a.getActorType() != ActorType.TOWER
                && a.getActorType() != ActorType.BASE;
    }

    protected boolean isNeitherTowerNorAlly(Actor a) {
        return a.getActorType() != ActorType.TOWER && a.getTeam() != this.team;
    }

    @Deprecated
    public void updateXPWorth(
            String event) { // Deprecating for now instead of removal in case we want to revisit
        // this mechanic
        switch (event) {
            case "kill":
                this.xpWorth += 5;
                break;
            case "death":
                if (this.xpWorth > 25) this.xpWorth = 25;
                else this.xpWorth -= 5;
                break;
            case "assist":
                this.xpWorth += 2;
                break;
        }
        if (this.xpWorth < 10) this.xpWorth = 10;
        else if (xpWorth > 50) this.xpWorth = 50;
    }

    @Override
    public void handleFear(Point2D source, int duration) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (this.spellShieldActive) this.triggerSpellShield();
            return;
        }
        super.handleFear(source, duration);
    }

    @Override
    public void handlePull(Point2D source, double pullDistance) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (this.spellShieldActive) this.triggerSpellShield();
            return;
        }
        super.handlePull(source, pullDistance);
    }

    @Override
    public void knockback(Point2D source, float distance) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (this.spellShieldActive) this.triggerSpellShield();
            return;
        }
        super.knockback(source, distance);
    }

    @Override
    public void die(Actor a) {
        Console.debugLog(this.id + " has died! " + this.dead);
        try {
            if (this.dead) return;
            this.dead = true;
            // this.updateXPWorth("death");
            this.timeKilled = System.currentTimeMillis();
            this.canMove = false;
            if (!this.getState(ActorState.AIRBORNE)) this.stopMoving();
            if (this.hasKeeothBuff) disableKeeothBuff();
            if (this.hasGooBuff) disableGooBuff();

            if (a.getActorType() != ActorType.PLAYER) {
                ExtensionCommands.playSound(
                        parentExt, this.getUser(), "global", "announcer/you_are_defeated");
            }

            if (this.getState(ActorState.POLYMORPH)) {
                boolean swapAsset = true;
                if (this.getChampionName(this.getAvatar()).equalsIgnoreCase("marceline")
                        && this.getState(ActorState.TRANSFORMED)) swapAsset = false;
                if (swapAsset) {
                    ExtensionCommands.swapActorAsset(
                            this.parentExt, this.room, this.getId(), getSkinAssetBundle());
                }
                ExtensionCommands.removeFx(
                        this.parentExt, this.room, this.id + "_statusEffect_polymorph");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flambit_aoe");
                ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flambit_ring_");
                this.setState(ActorState.POLYMORPH, false);
            }
            this.setHealth(0, (int) this.maxHealth);
            this.target = null;
            this.killingSpree = 0;
            Actor realKiller = a;
            if (a.getActorType() != ActorType.PLAYER) {
                long lastAttacked = -1;
                UserActor lastAttacker = null;
                for (int i = 0; i < aggressors.size(); i++) {
                    Actor attacker = (Actor) aggressors.keySet().toArray()[i];
                    if (attacker.getActorType() == ActorType.PLAYER) {
                        long attacked = aggressors.get(attacker).getLong("lastAttacked");
                        if (lastAttacked == -1 || lastAttacked < attacked) {
                            lastAttacked = attacked;
                            lastAttacker = (UserActor) attacker;
                        }
                    }
                }
                if (lastAttacker != null) realKiller = lastAttacker;
            }
            ExtensionCommands.knockOutActor(
                    parentExt,
                    room,
                    String.valueOf(player.getId()),
                    realKiller.getId(),
                    this.deathTime);
            if (this.magicNailStacks > 0) {
                this.magicNailStacks /= 2;
                this.updateStatMenu("attackDamage");
            }
            if (this.lightningSwordStacks > 0) {
                this.lightningSwordStacks /= 2;
                this.updateStatMenu("spellDamage");
            }
            if (this.robeStacks > 0) {
                this.robeStacks /= 2;
                this.updateStatMenu("coolDownReduction");
            }
            try {
                ExtensionCommands.handleDeathRecap(
                        parentExt,
                        player,
                        this.id,
                        realKiller.getId(),
                        (HashMap<Actor, ISFSObject>) this.aggressors);
                this.increaseStat("deaths", 1);
                if (realKiller.getActorType() == ActorType.PLAYER) {
                    UserActor ua = (UserActor) realKiller;
                    ua.increaseStat("kills", 1);
                    this.parentExt
                            .getRoomHandler(this.room.getName())
                            .addScore(ua, ua.getTeam(), 25);
                }
                for (Actor actor : this.aggressors.keySet()) {
                    if (actor.getActorType() == ActorType.PLAYER
                            && !actor.getId().equalsIgnoreCase(realKiller.getId())) {
                        UserActor ua = (UserActor) actor;
                        // ua.updateXPWorth("assist");
                        ua.addXP(this.getXPWorth());
                        if (ChampionData.getJunkLevel(ua, "junk_5_ghost_pouch") > 0) {
                            ua.useGhostPouch();
                        }
                        ua.increaseStat("assists", 1);
                    }
                }
                // Set<String> buffKeys = this.activeBuffs.keySet();
            } catch (Exception e) {
                e.printStackTrace();
            }
            double timeDead = this.deathTime * 1000; // needs to be converted to ms for the client
            this.addGameStat("timeDead", timeDead);
            parentExt
                    .getTaskScheduler()
                    .schedule(
                            new Champion.RespawnCharacter(this), this.deathTime, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /*
       Acceptable Keys:
       availableSpellPoints: Integer
       sp_category1
       sp_category2
       sp_category3
       sp_category4
       sp_category5
       kills
       deaths
       assists
       attackDamage
       attackSpeed
       armor
       speed
       spellResist
       spellDamage
       criticalChance
       criticalDamage*
       lifeSteal
       armorPenetration
       coolDownReduction
       spellVamp
       spellPenetration
       attackRange
       healthRegen
    */

    private void disableKeeothBuff() {
        this.hasKeeothBuff = false;
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "keeoth_buff");
        ExtensionCommands.removeFx(
                this.parentExt, this.room, this.getId() + "_" + "jungle_buff_keeoth");
        String[] stats = {"lifeSteal", "spellVamp", "criticalChance"};
        this.updateStatMenu(stats);
    }

    private void disableGooBuff() {
        this.hasGooBuff = false;
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "goomonster_buff");
        ExtensionCommands.removeFx(
                this.parentExt, this.room, this.getId() + "_" + "jungle_buff_goo");
        this.updateStatMenu("speed");
    }

    public void updateStat(String key, double value) {
        this.stats.put(key, value);
        ExtensionCommands.updateActorData(
                this.parentExt, this.room, this.id, key, this.getPlayerStat(key));
    }

    public void increaseStat(String key, double num) {
        // Console.debugLog("Increasing " + key + " by " + num);
        this.stats.put(key, this.stats.get(key) + num);
        ExtensionCommands.updateActorData(
                this.parentExt, this.room, this.id, key, this.getPlayerStat(key));
    }

    protected boolean
            canRegenHealth() { // TODO: Does not account for health pots. Not sure if this should be
        // added for balance reasons.
        // regen works while in combat
        return ((this.currentHealth < this.maxHealth || this.getPlayerStat("healthRegen") < 0)
                && ChampionData.getJunkLevel(this, "junk_1_ax_bass") < 1);
    }

    public void queueMovement(Point2D newDest) {
        this.queuedDest = newDest;
    }

    public void scheduleTask(Runnable task, int delay) {
        parentExt.getTaskScheduler().schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void clearPath() {
        super.clearPath();
        this.queuedDest = null;
    }

    @Override
    public void update(int msRan) {
        this.handleDamageQueue();
        this.handleActiveEffects();
        if (this.dead) {
            if (this.currentHealth > 0
                    && System.currentTimeMillis() > this.timeKilled + (deathTime * 1500L))
                this.respawn();
            else return;
        }

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        String itemName = "junk_2_simon_petrikovs_glasses";

        for (UserActor ua : rh.getPlayers()) {
            boolean contains = simonGlassesBuffProviders.containsKey(ua);
            boolean isInRange =
                    ua.getLocation() != null
                            && location.distance(ua.getLocation()) <= SIMON_GLASSES_RANGE;

            boolean leveledGlasses = ChampionData.getJunkLevel(ua, itemName) > 0;

            if (ua.getTeam() == team
                    && leveledGlasses
                    && isInRange
                    && !contains
                    && !ua.equals(this)) {
                int pdValue = (int) ChampionData.getCustomJunkStat(ua, itemName);
                simonGlassesBuffProviders.put(ua, pdValue);
                glassesBuff += pdValue;

                String iconName = ua + "simon_glasses_buff";
                String desc = "Your Power Damage is increased by " + pdValue;

                ExtensionCommands.addStatusIcon(parentExt, getUser(), iconName, desc, itemName, 0f);
                updateStatMenu("spellDamage");
            }
        }

        Iterator<Map.Entry<UserActor, Integer>> iterator =
                simonGlassesBuffProviders.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UserActor, Integer> entry = iterator.next();
            UserActor userActor = entry.getKey();
            int pdValue = entry.getValue();
            int junkLevel = ChampionData.getJunkLevel(userActor, itemName);

            boolean isLocNull = userActor.getLocation() == null;

            if (isLocNull
                    || userActor.getLocation().distance(location) > SIMON_GLASSES_RANGE
                    || junkLevel < 1) {
                String iconName = userActor + "simon_glasses_buff";
                glassesBuff -= pdValue;

                updateStatMenu("spellDamage");

                ExtensionCommands.removeStatusIcon(parentExt, getUser(), iconName);
                iterator.remove();
            }
        }

        if (!this.moonVfxActivated
                && this.hasBackpackItem("junk_3_battle_moon")
                && ChampionData.getJunkLevel(this, "junk_3_battle_moon") > 0) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "fx_junk_battle_moon",
                    1000 * 60 * 15,
                    this.id + "_battlemoon",
                    true,
                    "Bip01 Head",
                    false,
                    false,
                    this.team);
            this.moonVfxActivated = true;
        } else if (this.moonVfxActivated
                && ChampionData.getJunkLevel(this, "junk_3_battle_moon") < 1) {
            this.moonVfxActivated = false;
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_battlemoon");
        }
        if (!this.spellShieldActive
                && ChampionData.getJunkLevel(this, "junk_4_grob_gob_glob_grod") > 0
                && System.currentTimeMillis() > this.spellShieldCooldown) {
            this.spellShieldActive = true;
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "spell_shield",
                    1000 * 60 * 15,
                    this.id + "_spellShield",
                    true,
                    "Bip001 Pelvis",
                    true,
                    false,
                    this.team);
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.getUser(),
                    "junk_4_grob_gob_glob_grod_name",
                    "junk_4_grob_gob_glob_grod_mod3",
                    "junk_4_grob_gob_glob_grod",
                    0f);
        }

        if (this.canMove()
                && !this.isAutoAttacking
                && !this.isDashing
                && this.queuedDest != null
                && this.target == null) { // TODO: This could probably just be merged into canMove
            this.moveWithCollision(this.queuedDest);
            this.queuedDest = null;
        }
        if (!this.isStopped()) {
            this.updateMovementTime();
        }
        if (this.hits > 0) {
            this.hits -= 0.1d;
        } else if (this.hits < 0) this.hits = 0;
        this.location = this.getRelativePoint(false);
        this.handlePathing();

        if (!this.flameCloakEffectActivated
                && ChampionData.getJunkLevel(this, "junk_4_flame_cloak") > 0) {
            ExtensionCommands.createActorFX(
                    this.parentExt,
                    this.room,
                    this.id,
                    "fx_target_ring_1.5",
                    1000 * 60 * 15,
                    this.id + "_flameCloak",
                    true,
                    "",
                    true,
                    true,
                    this.team);
            this.flameCloakEffectActivated = true;
        } else if (this.flameCloakEffectActivated
                && ChampionData.getJunkLevel(this, "junk_4_flame_cloak") == 0) {
            ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "_flameCloak");
            this.flameCloakEffectActivated = false;
        }

        if (movementDebug)
            ExtensionCommands.moveActor(
                    this.parentExt,
                    this.room,
                    this.id + "_movementDebug",
                    this.location,
                    this.location,
                    5f,
                    false);
        if (this.location.distance(this.movementLine.getP2()) <= 0.01f) {
            this.idleTime += 100;
        }
        boolean insideBrush = false;
        boolean isPracticeMap = rh.isPracticeMap();
        ArrayList<Path2D> brushPaths = parentExt.getBrushPaths(isPracticeMap);

        for (Path2D brush : brushPaths) {
            if (brush.contains(this.location)) {
                insideBrush = true;
                break;
            }
        }
        if (insideBrush) {
            if (!this.states.get(ActorState.BRUSH)) {
                Console.debugLog("BRUSH STATE ENABLED");

                ExtensionCommands.changeBrush(
                        parentExt, room, this.id, parentExt.getBrushNum(location, brushPaths));
                this.setState(ActorState.BRUSH, true);
                if (this.stealthEmbargo <= System.currentTimeMillis())
                    this.setState(ActorState.REVEALED, false);
            } else if (this.stealthEmbargo != -1
                    && this.stealthEmbargo <= System.currentTimeMillis()) {
                this.setState(ActorState.REVEALED, false);
                this.stealthEmbargo = -1;
            }
        } else {
            if (this.states.get(ActorState.BRUSH)) {
                Console.debugLog("BRUSH STATE DISABLED");

                this.setState(ActorState.BRUSH, false);
                if (!this.getState(ActorState.INVISIBLE)) this.setState(ActorState.REVEALED, true);
                ExtensionCommands.changeBrush(parentExt, room, this.id, -1);
            } else if (!this.states.get(ActorState.REVEALED)
                    && !this.states.get(ActorState.INVISIBLE)
                    && !this.states.get(ActorState.STEALTH)) {
                this.setState(ActorState.REVEALED, true);
            }
        }
        if (this.attackCooldown > 0) this.reduceAttackCooldown();
        if (this.target != null && invisOrInBrush(target)) this.target = null;
        if (this.target != null && this.target.getHealth() > 0) {
            if (this.withinRange(target) && this.canAttack()) {
                this.autoAttack(target);
            } else if (!this.withinRange(target) && this.canMove() && !this.isAutoAttacking) {
                if (!this.isPointAtEndOfPath(
                        this.target
                                .getLocation())) { // I put this here so it does not spam movement
                    // commands if their target hasn't moved
                    this.moveWithCollision(this.target.getLocation());
                }
            }
        } else {
            if (this.target != null) {
                if (this.target.getHealth() <= 0) {
                    this.target = null;
                }
            } else if (this.autoAttackEnabled
                    && System.currentTimeMillis() - lastAutoTargetTime > 2000
                    && idleTime > 500) {
                Actor closestTarget = null;
                double closestDistance = 1000;
                int aggroRange = parentExt.getActorStats(avatar).get("aggroRange").asInt();
                for (Actor a : Champion.getActorsInRadius(rh, this.location, aggroRange)) {
                    if (a.getTeam() != this.team
                            && a.getLocation().distance(this.location) < closestDistance) {
                        closestDistance = a.getLocation().distance(this.location);
                        closestTarget = a;
                    }
                }
                this.idleTime = 0;
                this.target = closestTarget;
                this.lastAutoTargetTime = System.currentTimeMillis();
            }
        }
        if (msRan % 1000 == 0) {

            if (ChampionData.getJunkLevel(this, "junk_4_flame_cloak") > 0) {
                for (Actor a : Champion.getActorsInRadius(rh, location, 1.5f)) {
                    if (a.getTeam() != this.team && isNeitherStructureNorAlly(a)) {
                        a.addToDamageQueue(
                                this,
                                this.maxHealth * 0.035d,
                                ChampionData.getFlameCloakAttackData(),
                                true);
                    }
                }
            }

            if (ChampionData.getJunkLevel(this, "junk_3_robo_suit") > 0) {
                boolean ready = System.currentTimeMillis() - lastRoboEffect >= ROBO_CD;

                if (this.roboStacks < 3 && ready) {
                    this.roboStacks++;
                    this.updateStatMenu("speed");

                    if (this.roboStacks == 3)
                        ExtensionCommands.createActorFX(
                                parentExt,
                                room,
                                id,
                                "statusEffect_speed",
                                1000 * 60 * 5,
                                id + "_roboSpeed",
                                true,
                                "",
                                true,
                                false,
                                team);
                }
            }

            if (this.fightKingStacks > 0
                    && System.currentTimeMillis() - this.lastAuto
                            >= ChampionData.getCustomJunkStat(this, "junk_1_fight_king_sword"))
                this.resetFightKingStacks();
            if (this.cosmicStacks > 0
                    && System.currentTimeMillis() - this.lastSpell
                            >= ChampionData.getCustomJunkStat(this, "junk_2_cosmic_gauntlet"))
                this.resetCosmicStacks();

            if (this.canRegenHealth()) {
                regenHealth();
            }
            if (this.pickedUpHealthPack
                    && System.currentTimeMillis() - this.healthPackPickUpTime >= 60000) {
                this.pickedUpHealthPack = false;
                this.updateStatMenu("healthRegen");
            }
            if (this.pickedUpHealthPack && this.getHealth() == this.maxHealth) {
                removeHealthPackEffect();
            }
            int newDeath = 10 + ((msRan / 1000) / 60);
            if (newDeath != this.deathTime) this.deathTime = newDeath;
            List<Actor> actorsToRemove = new ArrayList<Actor>(this.aggressors.keySet().size());
            for (Actor a : this.aggressors.keySet()) {
                ISFSObject damageData = this.aggressors.get(a);
                if (System.currentTimeMillis() > damageData.getLong("lastAttacked") + 5000)
                    actorsToRemove.add(a);
            }
            for (Actor a : actorsToRemove) {
                this.aggressors.remove(a);
            }
            if (System.currentTimeMillis() - this.lastKilled >= 10000) {
                if (this.multiKill != 0) {
                    if (this.hasGameStat("largestMulti")) {
                        double largestMulti = this.getGameStat("largestMulti");
                        if (this.multiKill > largestMulti)
                            this.setGameStat("largestMulti", this.multiKill);
                    } else this.setGameStat("largestMulti", this.multiKill);
                    this.multiKill = 0;
                }
            }
            if (this.hasTempStat("healthRegen")) {
                if (this.currentHealth == this.maxHealth) {
                    this.effectHandlers.get("healthRegen").endAllEffects();
                }
            }
        }
        if (this.changeTowerAggro && !isInTowerRadius(this, false)) this.changeTowerAggro = false;

        if (this.hasKeeothBuff && System.currentTimeMillis() - this.keeothBuffStartTime >= 90000) {
            disableKeeothBuff();
        }
        if (this.hasGooBuff && System.currentTimeMillis() - this.gooBuffStartTime >= 90000) {
            disableGooBuff();
        }
        if (this.getState(ActorState.CHARMED) && this.charmer != null) {
            moveTowardsCharmer(charmer);
        }
    }

    private void regenHealth() {
        double healthRegen = this.getPlayerStat("healthRegen");
        if (this.currentHealth + healthRegen <= 0) healthRegen = (this.currentHealth - 1) * -1;
        this.changeHealth((int) healthRegen);
    }

    private void removeHealthPackEffect() {
        ExtensionCommands.removeFx(this.parentExt, this.room, this.id + "healthPackFX");
        this.pickedUpHealthPack = false;
        this.updateStatMenu("healthRegen");
    }

    public void resetIdleTime() {
        this.idleTime = 0;
    }

    public boolean invisOrInBrush(Actor a) {
        ActorState[] states = {ActorState.INVISIBLE, ActorState.BRUSH};
        for (ActorState state : states) {
            if (a.getState(state)) return true;
        }
        return false;
    }

    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        if (gCooldown > 0) {
            this.stopMoving(gCooldown);
            parentExt
                    .getTaskScheduler()
                    .schedule(new MovementStopper(true), castDelay, TimeUnit.MILLISECONDS);
        } else {
            this.stopMoving();
        }
        if (this.getClass() == UserActor.class) {
            String abilityString = "q";
            int abilityIndex = 0;
            if (ability == 2) {
                abilityString = "w";
                abilityIndex = 1;
            } else if (ability == 3) {
                abilityString = "e";
                abilityIndex = 2;
            }
            ExtensionCommands.actorAbilityResponse(
                    this.parentExt,
                    this.getUser(),
                    abilityString,
                    this.canCast[abilityIndex],
                    getReducedCooldown(cooldown),
                    gCooldown);
            if (this.canCast[abilityIndex]) {
                this.canCast[abilityIndex] = false;
                int finalAbilityIndex = abilityIndex;
                Runnable castReset = () -> canCast[finalAbilityIndex] = true;
                parentExt
                        .getTaskScheduler()
                        .schedule(castReset, getReducedCooldown(cooldown), TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean canDash() {
        return !this.getState(ActorState.ROOTED);
    }

    @Override
    public boolean canMove() {
        for (ActorState s :
                this.states.keySet()) { // removed CHARMED state from here to make charmer following
            // possible (I hope it doesn't break anything :))
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.AIRBORNE) {
                if (this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    public boolean hasInterrupingCC() {
        ActorState[] states = {
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.POLYMORPH,
            ActorState.STUNNED,
            ActorState.AIRBORNE,
            ActorState.SILENCED
        };
        for (ActorState state : states) {
            if (this.getState(state)) return true;
        }
        return false;
    }

    public boolean hasDashAttackInterruptCC() {
        ActorState[] states = {
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.POLYMORPH,
            ActorState.FEARED,
            ActorState.SILENCED,
        };
        for (ActorState state : states) {
            if (this.getState(state)) return true;
        }
        return false;
    }

    public void setCanMove(boolean canMove) {
        this.canMove = canMove;
        if (this.canMove && this.states.get(ActorState.CHARMED))
            this.move(this.movementLine.getP2());
    }

    public void resetTarget() {
        this.target = null;
        ExtensionCommands.setTarget(this.parentExt, this.player, this.id, "");
    }

    public void setState(ActorState[] states, boolean stateBool) {
        for (ActorState s : states) {
            this.states.put(s, stateBool);
            ExtensionCommands.updateActorState(parentExt, this.room, id, s, stateBool);
        }
    }

    public void setTarget(Actor a) {
        this.target = a;
        ExtensionCommands.setTarget(this.parentExt, this.player, this.id, a.getId());
        if (this.states.get(ActorState.CHARMED)) {
            this.setPath(getRelativePoint(false), a.getLocation());
            if (this.canMove) this.move(this.movementLine.getP2());
        }
    }

    public boolean isState(ActorState state) {
        return this.states.get(state);
    }

    public boolean canUseAbility(int ability) {
        ActorState[] hinderingStates = {
            ActorState.POLYMORPH,
            ActorState.AIRBORNE,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.SILENCED,
            ActorState.STUNNED
        };
        for (ActorState s : hinderingStates) {
            if (this.states.get(s)) return false;
        }
        return this.canCast[ability - 1];
    }

    public String getChampionName(String avatar) {
        String[] avatarComponents = avatar.split("_");
        if (avatarComponents.length > 1) {
            return avatarComponents[0];
        } else {
            return avatar;
        }
    }

    public boolean isCastingDashAbility(String avatar, int ability) { // all chars except fp
        String defaultAvatar = getChampionName(avatar);
        switch (defaultAvatar) {
            case "billy":
            case "cinnamonbun":
            case "peppermintbutler":
            case "finn":
            case "choosegoose":
                if (ability == 2) return true;
                break;
            case "fionna":
            case "gunter":
            case "rattleballs":
                if (ability == 1) return true;
                break;
            case "magicman":
                if (ability == 3 || ability == 2) return true;
                break;
        }
        return false;
    }

    public Line2D getMovementLine() {
        return this.movementLine;
    }

    public void stopMoving(int delay) {
        this.stopMoving();
        this.canMove = false;
        if (delay > 0) {
            parentExt
                    .getTaskScheduler()
                    .schedule(new MovementStopper(true), delay, TimeUnit.MILLISECONDS);
        } else this.canMove = true;
    }

    public float getRotation(Point2D dest) { // lmao
        double dx = dest.getX() - this.location.getX();
        double dy = dest.getY() - this.location.getY();
        double angleRad = Math.atan2(dy, dx);
        return (float) Math.toDegrees(angleRad) * -1 + 90f;
    }

    public void handlePolymorph(boolean enable, int duration) {
        if (enable) {
            handleSwapToPoly(duration);
        } else {
            handleSwapFromPoly();
        }
    }

    public void handleSwapToPoly(int duration) {
        this.addState(ActorState.SLOWED, 0.3d, duration);
        ExtensionCommands.swapActorAsset(parentExt, this.room, this.id, "flambit");
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "statusEffect_polymorph",
                1000,
                this.id + "_statusEffect_polymorph",
                true,
                "",
                true,
                false,
                this.team);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "flambit_aoe",
                3000,
                this.id + "_flambit_aoe",
                true,
                "",
                true,
                false,
                this.team);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "fx_target_ring_2",
                3000,
                this.id + "_flambit_ring_",
                true,
                "",
                true,
                true,
                getOppositeTeam());
    }

    @Override
    public void addState(ActorState state, double delta, int duration) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            ActorState[] ccStates = {
                ActorState.BRUSH,
                ActorState.CLEANSED,
                ActorState.IMMUNITY,
                ActorState.INVINCIBLE,
                ActorState.INVISIBLE,
                ActorState.REVEALED,
                ActorState.STEALTH,
                ActorState.TRANSFORMED
            };
            for (ActorState s : ccStates) {
                if (state == s) {
                    super.addState(state, delta, duration);
                    return;
                }
            }
            if (this.spellShieldActive) {
                triggerSpellShield();
            }
            return;
        }
        super.addState(state, delta, duration);
    }

    @Override
    public void addState(ActorState state, double delta, int duration, String fxId, String emit) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            ActorState[] ccStates = {
                ActorState.BRUSH,
                ActorState.CLEANSED,
                ActorState.IMMUNITY,
                ActorState.INVINCIBLE,
                ActorState.INVISIBLE,
                ActorState.REVEALED,
                ActorState.STEALTH,
                ActorState.TRANSFORMED
            };
            for (ActorState s : ccStates) {
                if (state == s) {
                    super.addState(state, delta, duration, fxId, emit);
                    return;
                }
            }
            if (this.spellShieldActive) {
                triggerSpellShield();
            }
            return;
        }
        super.addState(state, delta, duration, fxId, emit);
    }

    public void handleSwapFromPoly() {
        String bundle = this.getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bundle);
    }

    @Override
    public void handleCharm(UserActor charmer, int duration) {
        if (this.spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (this.spellShieldActive) this.triggerSpellShield();
            return;
        }
        if (!this.states.get(ActorState.CHARMED) && !this.states.get(ActorState.IMMUNITY)) {
            this.charmer = charmer;
            this.addState(ActorState.CHARMED, 0d, duration);
        }
    }

    public void handleCyclopsHealing() {
        if (this.getHealth() != this.maxHealth && !this.pickedUpHealthPack) {
            this.heal((int) (this.getMaxHealth() * 0.15d));
        }
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.getId(),
                "fx_health_regen",
                60000,
                this.id + "healthPackFX",
                true,
                "",
                false,
                false,
                this.getTeam());
        this.pickedUpHealthPack = true;
        this.healthPackPickUpTime = System.currentTimeMillis();
        this.updateStatMenu("healthRegen");
    }

    public void respawn() {
        Point2D respawnPoint = getRespawnPoint();
        Console.debugLog(
                this.displayName
                        + " Respawning at: "
                        + respawnPoint.getX()
                        + ","
                        + respawnPoint.getY()
                        + " for team "
                        + this.team);
        this.location = respawnPoint;
        this.futureCrystalActive = true;
        this.movementLine = new Line2D.Float(respawnPoint, respawnPoint);
        this.timeTraveled = 0f;
        this.canMove = true;
        this.setHealth((int) this.maxHealth, (int) this.maxHealth);
        this.dead = false;
        this.removeEffects();
        ExtensionCommands.snapActor(
                this.parentExt, this.room, this.id, this.location, this.location, false);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx/sfx_champion_respawn", this.location);
        ExtensionCommands.respawnActor(this.parentExt, this.room, this.id);
        this.addEffect("speed", 2d, 5000, "statusEffect_speed", "targetNode");
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "champion_respawn_effect",
                1000,
                this.id + "_respawn",
                true,
                "Bip001",
                false,
                false,
                this.team);
    }

    protected void basicAttackReset() {
        attackCooldown = 500;
    }

    public boolean enhanceCrit() {
        return this.hasBackpackItem("junk_1_grass_sword") && this.getStat("sp_category1") > 0;
    }

    private Point2D getRespawnPoint() {
        int teamNumber =
                parentExt.getRoomHandler(this.room.getName()).getTeamNumber(this.id, this.team);
        Point2D respawnPoint;
        boolean isPractice = this.parentExt.getRoomHandler(this.room.getName()).isPracticeMap();
        respawnPoint =
                isPractice
                        ? MapData.L1_PURPLE_SPAWNS[teamNumber]
                        : MapData.L2_PURPLE_SPAWNS[teamNumber];
        if (this.team == 1 && respawnPoint.getX() < 0)
            respawnPoint = new Point2D.Double(respawnPoint.getX() * -1, respawnPoint.getY());
        return respawnPoint;
    }

    public void addXP(int xp) {
        if (this.level != 10) {
            double glassesModifier =
                    ChampionData.getCustomJunkStat(this, "junk_5_glasses_of_nerdicon");
            if (glassesModifier > 0) {
                xp *= (1 + glassesModifier);
            }
            this.xp += xp;
            HashMap<String, Double> updateData = new HashMap<>(3);
            int level = ChampionData.getXPLevel(this.xp);
            if (level != this.level) {
                this.level = level;
                this.xp = ChampionData.getLevelXP(level - 1);
                updateData.put("level", (double) this.level);
                ExtensionCommands.playSound(parentExt, this.player, this.id, "sfx_level_up_beam");
                ExtensionCommands.createActorFX(
                        this.parentExt,
                        this.room,
                        this.id,
                        "level_up_beam",
                        1000,
                        this.id + "_levelUpBeam",
                        true,
                        "",
                        true,
                        false,
                        this.team);
                ChampionData.levelUpCharacter(this.parentExt, this);
            }
            updateData.put("xp", (double) this.xp);
            updateData.put("pLevel", this.getPLevel());
            ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, updateData);
        }
    }

    public int getLevel() {
        return this.level;
    }

    public double getPLevel() {
        if (this.level == 10) return 0d;
        double lastLevelXP = ChampionData.getLevelXP(this.level - 1);
        double currentLevelXP = ChampionData.getLevelXP(this.level);
        double delta = currentLevelXP - lastLevelXP;
        return (this.xp - lastLevelXP) / delta;
    }

    private void processHitData(Actor a, JsonNode attackData, int damage) {
        if (a.getId().contains("turret"))
            a =
                    this.parentExt
                            .getRoomHandler(this.room.getName())
                            .getEnemyChampion(this.team, "princessbubblegum");
        if (a.getId().contains("skully"))
            a =
                    this.parentExt
                            .getRoomHandler(this.room.getName())
                            .getEnemyChampion(this.team, "lich");
        String precursor = "attack";
        if (attackData.has("spellName")) precursor = "spell";
        if (this.aggressors.containsKey(a)) {
            this.aggressors.get(a).putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject currentAttackData = this.aggressors.get(a);
            int tries = 0;
            for (String k : currentAttackData.getKeys()) {
                if (k.contains("attack")) {
                    ISFSObject attack0 = currentAttackData.getSFSObject(k);
                    if (attackData
                            .get(precursor + "Name")
                            .asText()
                            .equalsIgnoreCase(attack0.getUtfString("atkName"))) {
                        attack0.putInt("atkDamage", attack0.getInt("atkDamage") + damage);
                        this.aggressors.get(a).putSFSObject(k, attack0);
                        return;
                    } else tries++;
                }
            }
            String attackNumber = "";
            if (tries == 0) attackNumber = "attack1";
            else if (tries == 1) attackNumber = "attack2";
            else if (tries == 2) attackNumber = "attack3";
            ISFSObject attack1 = new SFSObject();
            attack1.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attack1.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attack1.putUtfString("atkType", attackType);
            attack1.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            this.aggressors.get(a).putSFSObject(attackNumber, attack1);
        } else {
            ISFSObject playerData = new SFSObject();
            playerData.putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject attackObj = new SFSObject();
            attackObj.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attackObj.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attackObj.putUtfString("atkType", attackType);
            attackObj.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            playerData.putSFSObject("attack1", attackObj);
            this.aggressors.put(a, playerData);
        }
    }

    public boolean isRegularAttack(JsonNode attackData) {
        if (attackData.has("spellName")
                && attackData.get("spellName").asText().equalsIgnoreCase("rattleballs_spell_1_name")
                && attackData.has("counterAttack")) {
            return false;
        }
        String[] spellNames = {"princess_bubblegum_spell_2_name", "lich_spell_4_name"};
        for (String name : spellNames) {
            if (attackData.has("spellName")
                    && attackData.get("spellName").asText().equalsIgnoreCase(name)) return false;
        }
        return true;
    }

    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        stats.put("availableSpellPoints", 1d);
        for (int i = 1; i < 6; i++) {
            stats.put("sp_category" + i, 0d);
        }
        stats.put("kills", 0d);
        stats.put("deaths", 0d);
        stats.put("assists", 0d);
        JsonNode actorStats = this.parentExt.getActorStats(this.getAvatar());
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    public String getBackpack() {
        return this.backpack;
    }

    public boolean hasBackpackItem(String item) {
        String[] items = ChampionData.getBackpackInventory(this.parentExt, this.backpack);
        for (String i : items) {
            if (i.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

    protected int getReducedCooldown(double cooldown) {
        if (abilityDebug) return 0;
        double cooldownReduction = this.getPlayerStat("coolDownReduction");
        double ratio = 1 - (cooldownReduction / 100);
        return (int) Math.round(cooldown * ratio);
    }

    public void handleSpellVamp(double damage, boolean dotDamage) {
        double spellVamp = this.getPlayerStat("spellVamp");
        if (this.hits != 0) {
            if (dotDamage) spellVamp /= this.hits;
            else spellVamp /= (this.hits * 2);
        }
        if (this.getPlayerStat("spellVamp") * 0.3 > spellVamp)
            spellVamp = this.getPlayerStat("spellVamp") * 0.3d;
        double percentage = spellVamp / 100;
        int healing = (int) Math.round(damage * percentage);
        // Console.debugLog(this.displayName + " is healing for " + healing + " HP!");
        this.changeHealth(healing);
    }

    public void handleLifeSteal() {
        double damage = this.getPlayerStat("attackDamage");
        double lifesteal = this.getPlayerStat("lifeSteal") / 100;
        this.changeHealth((int) Math.round(damage * lifesteal));
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("healthRegen")) {
            if (this.pickedUpHealthPack) return super.getPlayerStat(stat) + HEALTH_PACK_REGEN;
        }
        if (stat.equalsIgnoreCase("attackDamage")) {
            double attackDamage = super.getPlayerStat(stat);
            if (this.dcBuff == 2) attackDamage *= DC_AD_BUFF;
            attackDamage += (DEMON_SWORD_AD_BUFF * this.getMonsterBuffCount(stat));
            return attackDamage + this.magicNailStacks;

        } else if (stat.equalsIgnoreCase("armor")) {
            double armor = super.getPlayerStat(stat);
            if (this.dcBuff >= 1) armor *= DC_ARMOR_BUFF;
            return armor + (5 * this.getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("spellResist")) {
            double mr = super.getPlayerStat(stat);
            if (this.dcBuff >= 1) mr *= DC_SPELL_RESIST_BUFF;
            return mr + (5 * this.getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("speed")) {
            double speedBoost = SPEED_BOOST_PER_ROBO_STACK * this.roboStacks; // TODO: Make scalable
            if (speedBoost < 0) speedBoost = 0;
            if (this.dcBuff >= 1) return super.getPlayerStat(stat) * DC_SPEED_BUFF + speedBoost;
            return super.getPlayerStat(stat) + speedBoost;

        } else if (stat.equalsIgnoreCase("spellDamage")) {
            double spellDamage = super.getPlayerStat(stat);
            if (this.dcBuff == 2) spellDamage *= DC_PD_BUFF;
            if (glassesBuff != 0) spellDamage += glassesBuff;
            return spellDamage
                    + lightningSwordStacks
                    + (DEMON_SWORD_SD_BUFF * getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("coolDownReduction")) {
            return super.getPlayerStat(stat) + this.robeStacks;
        }
        return super.getPlayerStat(stat);
    }

    public void resetRoboStacks() {
        roboStacks = 0;
        ExtensionCommands.removeFx(parentExt, room, id + "_roboSpeed");
        updateStatMenu("speed");
    }

    public void useGhostPouch() {
        double healfactor = ChampionData.getCustomJunkStat(this, "junk_5_ghost_pouch");
        for (UserActor ua :
                Champion.getUserActorsInRadius(
                        this.parentExt.getRoomHandler(this.room.getName()), this.location, 5f)) {
            if (ua.getTeam() == this.team && !ua.getId().equalsIgnoreCase(this.id)) {
                Console.debugLog("Healed player from ghost pouch!");
                ua.changeHealth((int) (ua.maxHealth * healfactor));
                // TODO: Add effect / SFX
            }
        }
    }

    protected void handleMonsterBuff(Monster m) {
        Monster.BuffType buff = m.getBuffType();
        if (buff != Monster.BuffType.NONE) {
            this.activeMonsterBuffs.add(buff);
            Champion.handleStatusIcon(
                    this.parentExt, this, m.getAvatar(), m.getBuffDescription(), 1000 * 60);
            Runnable removeBuff =
                    () -> {
                        Console.debugLog("Removed monster buff");
                        activeMonsterBuffs.remove(buff);
                        Console.debugLog(activeMonsterBuffs);
                        updateMonsterStatMenu(buff);
                    };
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(removeBuff, 60, TimeUnit.SECONDS);
        }
        this.updateMonsterStatMenu(buff);
    }

    protected void updateMonsterStatMenu(Monster.BuffType buff) {
        switch (buff) {
            case OWL:
            case GNOME:
                this.updateStatMenu("attackDamage");
                this.updateStatMenu("spellDamage");
                break;
            case BEAR:
            case WOLF:
                this.updateStatMenu("spellResist");
                this.updateStatMenu("armor");
                break;
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (a.getActorType() == ActorType.PLAYER || a instanceof Bot) {
            this.killingSpree++;
            this.multiKill++;
            this.lastKilled = System.currentTimeMillis();
        }
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor killedUA = (UserActor) a;
            this.killedPlayers.add(killedUA);
            if (this.hasGameStat("spree")) {
                double endGameSpree = this.getGameStat("spree");
                if (this.killingSpree > endGameSpree) {
                    this.endGameStats.put("spree", (double) this.killingSpree);
                }
            } else {
                this.endGameStats.put("spree", (double) this.killingSpree);
            }
            if (ChampionData.getJunkLevel(this, "junk_5_ghost_pouch") > 0) {
                this.useGhostPouch();
            }
            if (ChampionData.getJunkLevel(this, "junk_1_ax_bass") > 0
                    && a.getActorType() == ActorType.PLAYER) {
                this.changeHealth((int) Math.round(this.maxHealth * 0.15d));
            }
            if (ChampionData.getJunkLevel(this, "junk_1_night_sword") > 0) {
                this.setState(ActorState.REVEALED, false);
                this.addState(ActorState.INVISIBLE, 0d, 2000);
                Runnable reveal =
                        () -> {
                            if (!this.getState(ActorState.BRUSH))
                                this.setState(ActorState.REVEALED, true);
                        };
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(reveal, 2000, TimeUnit.MILLISECONDS);
            }
        }
        if (ChampionData.getJunkLevel(this, "junk_1_magic_nail") > 0) addMagicNailStacks(a);
        if (ChampionData.getJunkLevel(this, "junk_2_lightning_sword") > 0)
            addLightningSwordStacks(a);
        if (ChampionData.getJunkLevel(this, "junk_4_wizard_robe") > 0) addRobeStacks(a);

        int additionalXP = 0;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            int levelDiff = ua.getLevel() - this.level;
            if (levelDiff > 0) additionalXP = 15 * levelDiff;
        } else if (a.getActorType() == ActorType.MONSTER) {
            if (ChampionData.getJunkLevel(this, "junk_1_demon_blood_sword") > 0) {
                additionalXP += ((double) a.getXPWorth() * 0.15d);
                Monster m = (Monster) a;
                this.handleMonsterBuff(m);
            }

        } else if (a.getActorType() == ActorType.MINION) {
            if (ChampionData.getJunkLevel(this, "junk_1_grape_juice_sword") > 0) {
                additionalXP += ((double) a.getXPWorth() * 0.1d);
            }
        }
        this.addXP(a.getXPWorth() + additionalXP);
        // if (a.getActorType() == ActorType.PLAYER) this.updateXPWorth("kill");
        if (a.getActorType() == ActorType.TOWER) {
            for (UserActor ua : this.parentExt.getRoomHandler(this.room.getName()).getPlayers()) {
                if (ua.getTeam() == this.team && !ua.getId().equalsIgnoreCase(this.id)) {
                    ua.addXP(a.getXPWorth() + additionalXP);
                }
            }
            return;
        }
        for (Actor actor :
                Champion.getActorsInRadius(
                        this.parentExt.getRoomHandler(this.room.getName()), this.location, 8f)) {
            if (actor.getActorType() == ActorType.PLAYER
                    && !actor.getId().equalsIgnoreCase(this.id)
                    && actor.getTeam() == this.team
                    && a.getActorType() != ActorType.PLAYER) {
                UserActor ua = (UserActor) actor;
                ua.addXP((int) Math.floor(a.getXPWorth()));
            }
        }
    }

    private void addMagicNailStacks(Actor killedActor) {
        int pointsPutIntoNail = (int) this.getStat("sp_category1");
        int amountOfStacks =
                killedActor.getActorType() == ActorType.PLAYER
                        ? NAIL_STACKS_PER_CHAMP
                        : NAIL_STACKS_PER_NON_CHAMPS;
        int stackCap = pointsPutIntoNail * DAMAGE_PER_NAIL_POINT;

        if (pointsPutIntoNail > 0) {
            if (magicNailStacks + amountOfStacks > stackCap) magicNailStacks = stackCap;
            else magicNailStacks += amountOfStacks;
            this.updateStatMenu("attackDamage");
        }
    }

    private void addLightningSwordStacks(Actor killedActor) {
        int pointsPutIntoNail = ChampionData.getJunkLevel(this, "junk_2_lightning_sword");
        int amountOfStacks =
                killedActor.getActorType() == ActorType.PLAYER
                        ? LIGHTNING_SWORD_STACKS_PER_CHAMP
                        : LIGHTNING_SWORD_STACKS_PER_NON_CHAMP;
        int stackCap = pointsPutIntoNail * DAMAGE_PER_LIGHTNING_POINT;

        if (pointsPutIntoNail > 0) {
            if (lightningSwordStacks + amountOfStacks > stackCap) lightningSwordStacks = stackCap;
            else lightningSwordStacks += amountOfStacks;
            this.updateStatMenu("spellDamage");
        }
    }

    private void addRobeStacks(Actor ka) {
        int pointsPutIntoRobe = ChampionData.getJunkLevel(this, "junk_4_wizard_robe");
        boolean champOrJgBoss =
                ka instanceof UserActor || ka instanceof Keeoth || ka instanceof GooMonster;

        double amountOfStacks = champOrJgBoss ? ROBE_CD_CHAMP_OR_JG_BOSS_KO : ROBE_CD_MINION_KO;
        int stackCap = pointsPutIntoRobe * CDR_PER_ROBE_POINT;

        if (pointsPutIntoRobe > 0) {
            if (robeStacks + amountOfStacks > stackCap) robeStacks = stackCap;
            else robeStacks += amountOfStacks;
            Console.debugLog("Robe stacks: " + this.robeStacks);
            this.updateStatMenu("coolDownReduction");
        }
    }

    public void addGameStat(String stat, double value) {
        if (this.endGameStats.containsKey(stat))
            this.endGameStats.put(stat, this.endGameStats.get(stat) + value);
        else this.setGameStat(stat, value);
    }

    public void setGameStat(String stat, double value) {
        this.endGameStats.put(stat, value);
    }

    public void addDamageGameStat(UserActor ua, double value, AttackType type) {
        super.addDamageGameStat(ua, value, type);
        ua.addGameStat("damageDealtChamps", value);
    }

    public void handleDamageTakenStat(AttackType type, double value) {
        this.addGameStat("damageReceivedTotal", value);
        if (type == AttackType.PHYSICAL) this.addGameStat("damageReceivedPhysical", value);
        else this.addGameStat("damageReceivedSpell", value);
    }

    public double getGameStat(String stat) {
        return this.endGameStats.get(stat);
    }

    public boolean hasGameStat(String stat) {
        return this.endGameStats.containsKey(stat);
    }

    public int getSpellDamage(JsonNode attackData, boolean singleTarget) {
        try {
            int damage =
                    (int)
                            Math.round(
                                    attackData.get("damage").asDouble()
                                            + (this.getPlayerStat("spellDamage")
                                                    * attackData.get("damageRatio").asDouble()));
            if (ChampionData.getJunkLevel(this, "junk_2_demonic_wishing_eye") > 0) {
                double chance = this.getPlayerStat("criticalChance") / 100d;
                if (!singleTarget) chance /= 2d;
                if (Math.random() < chance) {
                    Console.debugLog("Ability crit! Chance: " + chance);
                    damage *= 1.25;
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, "", "sfx/sfx_map_ping", this.location);
                }
            }
            return damage;
        } catch (Exception e) {
            e.printStackTrace();
            return attackData.get("damage").asInt();
        }
    }

    public void fireProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        double speed =
                parentExt.getActorStats(projectile.getProjectileAsset()).get("speed").asDouble();
        ExtensionCommands.createProjectile(
                parentExt,
                this.room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);
        this.parentExt.getRoomHandler(this.room.getName()).addProjectile(projectile);
    }

    public void fireMMProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        double speed =
                parentExt.getActorStats(projectile.getProjectileAsset()).get("speed").asDouble();
        ExtensionCommands.createProjectile(
                parentExt,
                this.room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);
        this.parentExt.getRoomHandler(this.room.getName()).addProjectile(projectile);
    }

    public void handleDCBuff(int teamSizeDiff, boolean removeSecondBuff) {
        String[] stats = {"armor", "spellResist", "speed"};
        String[] stats2 = {"attackDamage", "spellDamage"};
        if (removeSecondBuff) {
            this.dcBuff = 1;
            ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats2));
            ExtensionCommands.removeStatusIcon(parentExt, player, "DC Buff #2");
            ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff2");
            return;
        }
        switch (teamSizeDiff) {
            case 0:
                this.dcBuff = 0;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats));
                ExtensionCommands.removeStatusIcon(parentExt, player, "DC Buff #1");
                ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff1");
                break;
            case 1:
            case -1:
                this.dcBuff = 1;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats));
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "DC Buff #1",
                        "Some coward left the battle! Here's something to help even the playing field!",
                        "icon_parity",
                        0);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "disconnect_buff_duo",
                        1000 * 15 * 60,
                        id + "_dcbuff1",
                        true,
                        "",
                        false,
                        false,
                        team);
                break;
            case 2:
            case -2:
                this.dcBuff = 2;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats2));
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "DC Buff #2",
                        "You're the last one left, finish the mission",
                        "icon_parity2",
                        0);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "disconnect_buff_solo",
                        1000 * 15 * 60,
                        id + "_dcbuff2",
                        true,
                        "",
                        false,
                        false,
                        team);
                break;
        }
    }

    private HashMap<String, Double> getPlayerStats(String[] stats) {
        HashMap<String, Double> playerStats = new HashMap<>(stats.length);
        for (String s : stats) {
            playerStats.put(s, this.getPlayerStat(s));
        }
        return playerStats;
    }

    public void updateStatMenu(String stat) {
        // Console.debugLog("Updating stat menu: " + stat + " with " + this.getPlayerStat(stat));
        ExtensionCommands.updateActorData(
                this.parentExt, this.room, this.id, stat, this.getPlayerStat(stat));
    }

    protected void updateStatMenu(String[] stats) {
        for (String s : stats) {
            ExtensionCommands.updateActorData(
                    this.parentExt, this.room, this.id, s, this.getPlayerStat(s));
        }
    }

    public void cleanseEffects() {
        ActorState[] cleansedStats = {
            ActorState.SLOWED,
            ActorState.STUNNED,
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.BLINDED,
            ActorState.ROOTED,
            ActorState.CLEANSED
        };
        for (ActorState s : cleansedStats) {
            if (this.effectHandlers.containsKey(s.toString()))
                this.effectHandlers.get(s.toString()).endAllEffects();
        }
    }

    public void destroy() {
        this.dead = true;
        ExtensionCommands.destroyActor(this.parentExt, this.room, this.id);
    }

    public void setLastAuto() {
        this.lastAuto = System.currentTimeMillis();
        if (ChampionData.getJunkLevel(this, "junk_1_fight_king_sword") > 0) {
            if (this.fightKingStacks > 0)
                ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "fight_king_icon");
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "fight_king_icon",
                    "Your next ability is enhanced!",
                    "junk_1_fight_king_sword",
                    (int) ChampionData.getCustomJunkStat(this, "junk_1_fight_king_sword"));
            this.fightKingStacks++;
        }
    }

    public void setLastSpell() {
        this.lastSpell = System.currentTimeMillis();
        if (ChampionData.getJunkLevel(this, "junk_2_cosmic_gauntlet") > 0) {
            if (this.cosmicStacks > 0)
                ExtensionCommands.removeStatusIcon(
                        this.parentExt, this.player, "cosmic_gauntlet_icon");
            this.cosmicStacks++;
            ExtensionCommands.addStatusIcon(
                    this.parentExt,
                    this.player,
                    "cosmic_gauntlet_icon",
                    "Your next attack is empowered!",
                    "junk_2_cosmic_gauntlet",
                    (int) ChampionData.getCustomJunkStat(this, "junk_2_cosmic_gauntlet"));
        }
    }

    public int getCosmicStacks() {
        return this.cosmicStacks;
    }

    public void resetCosmicStacks() {
        this.cosmicStacks = 0;
        ExtensionCommands.removeStatusIcon(this.parentExt, this.player, "cosmic_gauntlet_icon");
    }

    public void logExceptionMessage(String avatar, int spellNum) {
        String characterName = getChampionName(avatar).toUpperCase();
        String message =
                String.format(
                        "EXCEPTION OCCURED DURING ABILITY EXECUTION! CHARACTER: %s, ABILITY: %d",
                        characterName, spellNum);
        Console.logWarning(message);
    }

    public void clearIconHandlers() {
        Set<String> iconNames = new HashSet<>(this.iconHandlers.keySet());
        for (String i : iconNames) {
            ExtensionCommands.removeStatusIcon(this.parentExt, this.player, i);
            this.iconHandlers.get(i).cancel(true);
        }
        this.iconHandlers = new HashMap<>();
    }

    @Override
    public void removeEffects() {
        super.removeEffects();
        this.clearIconHandlers();
    }

    public void addIconHandler(String iconName, ScheduledFuture<?> handler) {
        this.iconHandlers.put(iconName, handler);
    }

    public void removeIconHandler(String iconName) {
        this.iconHandlers.remove(iconName);
    }

    public void handleNumbChuckStacks(Actor a) {
        if (this.numbChuckVictim == null) {
            this.numbChuckVictim = a.getId();
            this.numbSlow = true;
            return;
        }
        if (a.getId().equalsIgnoreCase(this.numbChuckVictim)) {
            if (this.numbSlow) {
                a.addState(
                        ActorState.SLOWED,
                        ChampionData.getCustomJunkStat(this, "junk_1_numb_chucks"),
                        1500);
                Console.debugLog("Numb Chuck slow applied!");
            }
            this.numbSlow = !this.numbSlow;
        } else {
            this.numbChuckVictim = a.getId();
            this.numbSlow = true;
        }
    }

    public boolean hasGlassesPoint() {
        return this.hasGlassesPoint;
    }

    public void setGlassesPoint(boolean val) {
        this.hasGlassesPoint = val;
    }

    @Override
    public void heal(int delta) {
        if (ChampionData.getJunkLevel(this, "junk_1_ax_bass") > 0) return;
        super.heal(delta);
    }

    public int getMonsterBuffCount(String stat) {
        int count = 0;
        for (Monster.BuffType buff : this.activeMonsterBuffs) {
            switch (stat) {
                case "attackDamage":
                case "spellDamage":
                    if (buff == Monster.BuffType.OWL || buff == Monster.BuffType.GNOME) count++;
                    break;
                case "armor":
                case "spellResist":
                    if (buff == Monster.BuffType.BEAR || buff == Monster.BuffType.WOLF) count++;
                    break;
            }
        }
        // Console.debugLog("Monster count: " + count);
        return count;
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        public MovementStopper(boolean move) {
            this.move = move;
        }

        @Override
        public void run() {
            canMove = this.move;
        }
    }

    protected class RangedAttack implements Runnable {

        Actor target;
        Runnable attackRunnable;
        String projectile;
        String emitNode;

        public RangedAttack(Actor target, Runnable attackRunnable, String projectile) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
        }

        public RangedAttack(
                Actor target, Runnable attackRunnable, String projectile, String emitNode) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
            this.emitNode = emitNode;
        }

        @Override
        public void run() {
            String emit = "Bip01";
            if (this.emitNode != null) emit = this.emitNode;
            float time = (float) (target.getLocation().distance(location) / 10f);
            ExtensionCommands.createProjectileFX(
                    parentExt, room, projectile, id, target.getId(), emit, "targetNode", time);
            parentExt
                    .getTaskScheduler()
                    .schedule(attackRunnable, (int) (time * 1000), TimeUnit.MILLISECONDS);
        }
    }
}

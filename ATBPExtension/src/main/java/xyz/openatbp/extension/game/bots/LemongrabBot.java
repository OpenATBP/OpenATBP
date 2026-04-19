package xyz.openatbp.extension.game.bots;

import static xyz.openatbp.extension.game.champions.Lemongrab.*;

import java.awt.geom.Point2D;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;

public class LemongrabBot extends Bot {
    private int unacceptableLevels = 0;
    private long lastHit = -1;
    private boolean isCastingUlt = false;
    private boolean juice = false;
    private int ultDelay;

    private float W_MAX_CAST_RANGE = 7f;
    private float E_MAX_CAST_RANGE = 6f;
    private float Q_MAX_CAST_RANGE = 6f;

    public LemongrabBot(
            ATBPExtension parentExt,
            Room room,
            int botId,
            String avatar,
            String displayName,
            int team,
            BotMapConfig mapConfig) {
        super(parentExt, room, botId, avatar, displayName, team, mapConfig);

        this.qCooldownMs = 10000;
        this.wCooldownMs = 12000;
        this.eCooldownMs = 45000;

        this.qGCooldownMs = 750;
        this.wGCooldownMs = 1500;
        this.eGCooldownMs = 350;

        this.qCastDelayMS = 500;
        this.wCastDelayMS = 1250;
        this.eCastDelayMS = 1500;

        this.lowHpActionPHealth = 0.15;

        this.canWinUnderTowerLvDif = -3;
        this.canWinEReadyLvDif = 0;
        this.canWinQWReadyLvDif = -1;

        this.soloJungleLv = 2;
        this.soloJunglePHealth = 0.95;
        this.duoJungleLv = 2;
        this.duoJunglePHealth = 0.3;
        this.trioJunglePHeath = 0.25;
        this.closestPlayerLvDif = 0;

        this.fleeMinionsAttackedPHpPerLv = 0.05f;
        this.defAltarCaptureActionDist = 4f;
        this.playerAttackedLvDif = -1;
        this.junglingAlliesRadius = 8;

        this.botRole = BotRole.JUNGLER;
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        handleUpdatePassive();
    }

    public void handleUpdatePassive() {
        if (this.unacceptableLevels > 0
                && System.currentTimeMillis() - lastHit >= PASSIVE_STACK_DURATION) {
            this.unacceptableLevels--;
            this.lastHit = System.currentTimeMillis();
        }
    }

    @Override
    public void die(Actor a) {
        super.die(a);
        this.unacceptableLevels = 0;
    }

    @Override
    public boolean canMove() {
        if (this.isCastingUlt) return false;
        return super.canMove();
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (!this.dead
                && this.unacceptableLevels < 3
                && System.currentTimeMillis() - lastHit >= PASSIVE_COOLDOWN
                && this.getAttackType(attackData) == AttackType.SPELL) {
            this.unacceptableLevels++;

            String voiceLinePassive = "";
            switch (this.unacceptableLevels) {
                case 1:
                    voiceLinePassive = "vo/lemongrab_ooo";
                    break;
                case 2:
                    voiceLinePassive = "vo/lemongrab_haha";
                    break;
                case 3:
                    voiceLinePassive = "vo/lemongrab_unacceptable";
                    break;
            }
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, voiceLinePassive, this.location);
            this.lastHit = System.currentTimeMillis();
        }
        return super.damaged(a, damage, attackData);
    }

    @Override
    public void handleFightingAbilities() {
        if (target == null || !canAttack()) return;

        if (target.getActorType() == ActorType.TOWER) return;

        if (canUseQ()) {
            faceTarget(target);
            useQ(target.getLocation());
        }
        if (canUseW()) {
            faceTarget(target);
            useW(target.getLocation());
        }

        if (target instanceof UserActor && canUseE()) {
            faceTarget(target);
            useE(target.getLocation());
        }
    }

    @Override
    public void handleRetreatAbilities() {
        if (lastPlayerAttacker != null && canAttack()) {
            double distance = lastPlayerAttacker.getLocation().distance(location);

            if (canUseQ() && distance <= Q_MAX_CAST_RANGE) {
                faceTarget(lastPlayerAttacker);
                useQ(lastPlayerAttacker.getLocation());
            }

            if (canUseE() && getPHealth() <= 0.1 && distance <= E_MAX_CAST_RANGE) {
                faceTarget(lastPlayerAttacker);
                useE(lastPlayerAttacker.getLocation());
            }
        }
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
    }

    @Override
    public boolean canUseQ() {
        if (target != null
                && defaultAbilityCheck(1)
                && target.getLocation().distance(location) <= Q_MAX_CAST_RANGE) {
            if (target instanceof UserActor) return true;
            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            return Champion.getEnemyActorsInRadius(rh, team, target.getLocation(), 4f).size() > 1;
        }
        return false;
    }

    @Override
    public boolean canUseW() {
        if (defaultAbilityCheck(2)) {
            double distance = location.distance(target.getLocation());
            if (distance <= W_MAX_CAST_RANGE) {
                if (target instanceof UserActor) return true;
                RoomHandler rh = parentExt.getRoomHandler(room.getName());
                List<Actor> enemies =
                        Champion.getEnemyActorsInRadius(rh, team, target.getLocation(), W_RADIUS);
                enemies.removeIf(a -> a.getActorType() == ActorType.TOWER);
                return enemies.size() > 2;
            }
        }
        return false;
    }

    @Override
    public boolean canUseE() {
        return (defaultAbilityCheck(3))
                && (target.hasMovementCC()
                        || (target != null && target.getEffectManager().hasState(ActorState.SLOWED))
                                && target.getLocation().distance(location) <= E_MAX_CAST_RANGE);
    }

    @Override
    public void useQ(Point2D destination) {
        lastQUse = System.currentTimeMillis();
        globalCooldown = qGCooldownMs;

        stopMoving(qCastDelayMS);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell1", 2000, false);

        AbilityShape qTrapezoid =
                AbilityShape.createTrapezoid(
                        location,
                        destination,
                        Q_SPELL_RANGE,
                        Q_OFFSET_DISTANCE_BOTTOM,
                        Q_OFFSET_DISTANCE_TOP);

        JsonNode spellData = parentExt.getAttackData("lemongrab", "spell1");
        RoomHandler handler = this.parentExt.getRoomHandler(this.room.getName());
        List<Actor> nearbyEnemies =
                Champion.getEnemyActorsInRadius(handler, team, destination, Q_SPELL_RANGE);

        if (!nearbyEnemies.isEmpty()) {
            for (Actor a : nearbyEnemies) {
                if (isNonStructureEnemy(a)
                        && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                        && a.isNotLeaping()) {
                    a.getEffectManager()
                            .addState(
                                    ActorState.SLOWED,
                                    id + "_lemon_q_slow",
                                    Q_SLOW_PERCENT,
                                    Q_SLOW_DURATION);
                }

                if (a.getActorType() != ActorType.TOWER
                        && qTrapezoid.contains(a.getLocation(), a.getCollisionRadius())
                        && a.isNotLeaping()) {
                    double dmg = getSpellDamage(spellData);
                    a.addToDamageQueue(this, dmg, spellData, false);
                }
            }
        }
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "sfx_lemongrab_sound_sword", this.location);
        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "vo/vo_lemongrab_sound_sword", this.location);
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.id,
                "lemongrab_sonic_sword_effect",
                750,
                this.id + "_sonicSword",
                true,
                "Sword",
                true,
                false,
                this.team);
    }

    @Override
    public void useW(Point2D destination) {
        lastWUse = System.currentTimeMillis();
        globalCooldown = wGCooldownMs;

        stopMoving(wCastDelayMS);
        ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", 2000, false);

        ExtensionCommands.playSound(
                this.parentExt, this.room, this.id, "vo/vo_lemongrab_my_juice", this.location);
        ExtensionCommands.createWorldFX(
                this.parentExt,
                this.room,
                this.id,
                "lemongrab_ground_aoe_target",
                this.id + "wTarget",
                wCastDelayMS,
                (float) destination.getX(),
                (float) destination.getY(),
                true,
                this.team,
                0f);
        ExtensionCommands.playSound(parentExt, room, "", "sfx_lemongrab_my_juice", destination);
        Runnable delayedJuice = // ;]
                () -> {
                    if (getHealth() > 0) {
                        juice = true;
                        ExtensionCommands.createWorldFX(
                                parentExt,
                                room,
                                id,
                                "lemongrab_ground_juice_aoe",
                                id + "_wJuice",
                                2000,
                                (float) destination.getX(),
                                (float) destination.getY(),
                                false,
                                team,
                                0f);
                    }
                };
        JsonNode spellData = parentExt.getAttackData("lemongrab", "spell2");
        scheduleTask(delayedJuice, W_FX_DELAY);
        LemonBotAbilityRunnable runnable =
                new LemonBotAbilityRunnable(2, spellData, wCooldownMs, wGCooldownMs, destination);
        scheduleTask(runnable, W_DELAY);
    }

    @Override
    public void useE(Point2D destination) {
        lastEUse = System.currentTimeMillis();
        globalCooldown = eGCooldownMs;

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell3", 2000, false);

        stopMoving();
        this.isCastingUlt = true;
        ExtensionCommands.createWorldFX(
                this.parentExt,
                this.room,
                this.id,
                "fx_target_ring_2.5",
                this.id + "_jailRing",
                eCastDelayMS,
                (float) destination.getX(),
                (float) destination.getY(),
                true,
                this.team,
                0f);
        String voiceLine = "";
        switch (unacceptableLevels) {
            case 0:
                voiceLine = "lemongrab_dungeon_3hours";
                ultDelay = 1250;
                break;
            case 1:
                voiceLine = "lemongrab_dungeon_30days";
                ultDelay = 1000;
                break;
            case 2:
                voiceLine = "lemongrab_dungeon_12years";
                ultDelay = 750;
                break;
            case 3:
                voiceLine = "lemongrab_dungeon_1myears";
                ultDelay = 500;
                break;
        }
        ExtensionCommands.playSound(this.parentExt, this.room, this.id, voiceLine, this.location);

        JsonNode spellData = parentExt.getAttackData("lemongrab", "spell3");
        LemonBotAbilityRunnable runnable =
                new LemonBotAbilityRunnable(3, spellData, eCooldownMs, eGCooldownMs, destination);
        scheduleTask(runnable, ultDelay);
    }

    private class LemonBotAbilityRunnable extends AbilityRunnable {

        public LemonBotAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {}

        @Override
        protected void spellW() {
            if (juice) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "lemongrab_head_splash",
                        id + "_wHead",
                        500,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);

                RoomHandler handler = parentExt.getRoomHandler(room.getName());

                for (Actor a : Champion.getActorsInRadius(handler, dest, 2f)) {

                    double distance = a.getLocation().distance(dest);
                    double damage = getSpellDamage(spellData);

                    if (distance <= 1 && isNonStructureEnemy(a) && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.SILENCED,
                                        id + "_lemon_w_silence",
                                        0d,
                                        W_SILENCE_DURATION);
                    }

                    if (isNonStructureEnemy(a) && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.BLINDED,
                                        id + "_lemon_w_blind",
                                        0d,
                                        W_BLIND_DURATION);
                    }

                    if (distance <= 1
                            && a.getTeam() != team
                            && a.getActorType() != ActorType.TOWER) {
                        damage *= W_CENTER_DMG_MULTIPLIER;
                    }

                    if (a.getTeam() != team
                            && a.getActorType() != ActorType.TOWER
                            && a.isNotLeaping()) {
                        a.addToDamageQueue(LemongrabBot.this, damage, spellData, false);
                    }
                }
                juice = false;
            }
        }

        @Override
        protected void spellE() {
            isCastingUlt = false;
            if (getHealth() > 0) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "lemongrab_dungeon_hit",
                        id + "_jailHit",
                        1000,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                double damage = getSpellDamage(spellData);
                double duration = 2000d;
                damage *= (1d + (0.1d * unacceptableLevels));
                duration *= (1d + (0.1d * unacceptableLevels));
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, dest, 2.5f)) {

                    if (a.getTeam() != team
                            && a.getActorType() != ActorType.TOWER
                            && a.isNotLeaping()) {
                        a.addToDamageQueue(LemongrabBot.this, damage, spellData, false);
                    }

                    if ((a instanceof UserActor) && a.getTeam() != team && a.isNotLeaping()) {
                        a.getEffectManager()
                                .addState(
                                        ActorState.STUNNED,
                                        id + "_lemon_e_stun",
                                        0d,
                                        (int) duration);

                        if (!a.getEffectManager().hasState(ActorState.IMMUNITY)) {
                            ExtensionCommands.createActorFX(
                                    parentExt,
                                    room,
                                    a.getId(),
                                    "lemongrab_lemon_jail",
                                    (int) duration,
                                    a.getId() + "_jailed",
                                    true,
                                    "",
                                    true,
                                    false,
                                    team);
                        }
                    }
                }
                unacceptableLevels = 0;
            }
        }

        @Override
        protected void spellPassive() {}
    }

    @Override
    public void levelUpStats() {
        switch (level) {
            case 1:
                setStat("attackDamage", 53);
                setStat("armor", 21);
                setStat("attackSpeed", 1450);
                setStat("spellDamage", 22);
                setStat("spellResist", 26);
                setStat("healthRegen", 3);
                setHealth(getHealth(), 550);
                break;
            case 2:
                setStat("attackDamage", 56);
                setStat("armor", 22);
                setStat("attackSpeed", 1400);
                setStat("spellDamage", 24);
                setStat("spellResist", 27);
                setHealth(getHealth(), 600);
                setStat("healthRegen", 4);
                break;
            case 3:
                setStat("attackDamage", 59);
                setStat("armor", 23);
                setStat("attackSpeed", 1350);
                setStat("spellDamage", 26);
                setStat("spellResist", 28);
                setHealth(getHealth(), 650);
                setStat("healthRegen", 5);
                break;
            case 4:
                setStat("attackDamage", 62);
                setStat("armor", 24);
                setStat("attackSpeed", 1300);
                setStat("spellDamage", 28);
                setStat("spellResist", 29);
                setHealth(getHealth(), 700);
                setStat("healthRegen", 6);
                break;
            case 5:
                setStat("attackDamage", 65);
                setStat("armor", 25);
                setStat("attackSpeed", 1250);
                setStat("spellDamage", 30);
                setStat("spellResist", 30);
                setHealth(getHealth(), 750);
                setStat("healthRegen", 7);
                break;
            case 6:
                setStat("attackDamage", 68);
                setStat("armor", 26);
                setStat("attackSpeed", 1200);
                setStat("spellDamage", 32);
                setStat("spellResist", 31);
                setHealth(getHealth(), 800);
                setStat("healthRegen", 8);
                break;
            case 7:
                setStat("attackDamage", 71);
                setStat("armor", 27);
                setStat("attackSpeed", 1150);
                setStat("spellDamage", 34);
                setStat("spellResist", 32);
                setHealth(getHealth(), 850);
                setStat("healthRegen", 9);
                break;
            case 8:
                setStat("attackDamage", 74);
                setStat("armor", 28);
                setStat("attackSpeed", 1100);
                setStat("spellDamage", 36);
                setStat("spellResist", 33);
                setHealth(getHealth(), 900);
                setStat("healthRegen", 10);
                break;
            case 9:
                setStat("attackDamage", 77);
                setStat("armor", 29);
                setStat("attackSpeed", 1050);
                setStat("spellDamage", 38);
                setStat("spellResist", 34);
                setHealth(getHealth(), 950);
                setStat("healthRegen", 11);
                break;
            case 10:
                setStat("attackDamage", 80);
                setStat("armor", 30);
                setStat("attackSpeed", 1000);
                setStat("spellDamage", 40);
                setStat("spellResist", 35);
                setHealth(getHealth(), 1000);
                setStat("healthRegen", 12);
                break;
        }
    }
}

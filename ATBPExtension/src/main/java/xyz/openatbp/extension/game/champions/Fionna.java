package xyz.openatbp.extension.game.champions;

import java.awt.geom.Point2D;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.User;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.AbilityRunnable;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.UserActor;

public class Fionna extends UserActor {
    private static final double HP_REG_FIERCE = 0.02d;
    private static final double HP_REG_FEARLESS = 0.01d;
    private static final double SPEED_FIERCE = 0.2d;
    private static final double ATTACKSPEED_FIERCE = 0.2d;
    private static final double SPELLRESIST_FEARLESS = 0.3d;
    private static final int W_DURATION = 3000;
    private static final int E_DURATION = 6000;

    private enum SwordType {
        FIERCE,
        FEARLESS
    }

    private int dashesRemaining = 0;
    private long dashTime = -1;
    private SwordType swordType = SwordType.FEARLESS;
    private boolean ultActivated = false;
    private double previousAttackDamage;
    private int qTime;
    private double qCooldown;
    private long ultStartTime = 0;
    private ArrayList<Actor> actorsHitWithQ;
    private double previousSpellDamage;

    public Fionna(User u, ATBPExtension parentExt) {
        super(u, parentExt);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fionna_sword_blue",
                1000 * 60 * 15,
                this.id + "_fearless",
                true,
                "Bip001 Prop1",
                true,
                false,
                this.team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fionna_health_regen",
                1000 * 60 * 15,
                this.id + "_blueRegen",
                true,
                "Bip001 Prop1",
                true,
                false,
                this.team);
        ExtensionCommands.addStatusIcon(
                parentExt, player, "fionna_fearless", "FEARLESS", "icon_fionna_s2b", 0f);
        String[] statsToUpdate = {"healthRegen", "armor", "spellResist", "attackSpeed", "speed"};
        this.updateStatMenu(statsToUpdate);
        this.previousAttackDamage = this.getPlayerStat("attackDamage");
        this.previousSpellDamage = this.getPlayerStat("spellDamage");
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);
        if (this.ultActivated && System.currentTimeMillis() - this.ultStartTime >= E_DURATION) {
            this.ultActivated = false;
            ExtensionCommands.removeStatusIcon(parentExt, player, "fionna_ult");
        }
        if (System.currentTimeMillis() - dashTime >= W_DURATION && this.dashesRemaining > 0) {
            ExtensionCommands.removeStatusIcon(
                    parentExt, player, this.id + "_dash" + this.dashesRemaining);
            this.dashTime = -1;
            this.dashesRemaining = 0;
            double cooldown = this.actorsHitWithQ.isEmpty() ? qCooldown *= 0.7 : qCooldown;
            ExtensionCommands.actorAbilityResponse(parentExt, player, "q", true, (int) cooldown, 0);
        }
        if (this.getPlayerStat("attackDamage") != this.previousAttackDamage) {
            this.updateStatMenu("attackDamage");
            this.previousAttackDamage = this.getPlayerStat("attackDamage");
        }
        if (this.getPlayerStat("spellDamage") != this.previousSpellDamage) {
            this.updateStatMenu("spellDamage");
            this.previousSpellDamage = this.getPlayerStat("spellDamage");
        }
    }

    @Override
    protected boolean canRegenHealth() {
        return super.canRegenHealth() || this.getPlayerStat("healthRegen") < 0;
    }

    @Override
    public double getPlayerStat(String stat) {
        switch (stat) {
            case "healthRegen":
                if (this.swordType == SwordType.FIERCE)
                    return super.getPlayerStat(stat) - 2 - (maxHealth * HP_REG_FIERCE);
                else return super.getPlayerStat(stat) + (maxHealth * HP_REG_FEARLESS);
            case "speed":
                if (this.swordType == SwordType.FIERCE)
                    return super.getPlayerStat(stat) + (this.getStat("speed") * SPEED_FIERCE);
                break;
            case "attackSpeed":
                if (this.swordType == SwordType.FIERCE) {
                    double currentAttackSpeed = super.getPlayerStat(stat);
                    double modifier = (this.getStat("attackSpeed") * ATTACKSPEED_FIERCE);
                    return currentAttackSpeed - modifier < BASIC_ATTACK_DELAY
                            ? BASIC_ATTACK_DELAY
                            : currentAttackSpeed - modifier;
                }
                break;
            case "armor":
            case "spellResist":
                if (this.swordType == SwordType.FEARLESS)
                    return super.getPlayerStat(stat) + (this.getStat(stat) * SPELLRESIST_FEARLESS);
                break;
            case "attackDamage":
            case "spellDamage":
                if (this.target != null
                        && (this.target.getActorType() == ActorType.TOWER
                                || this.target.getActorType() == ActorType.BASE))
                    return super.getPlayerStat(stat);
                else return super.getPlayerStat(stat) + this.getPassiveAttackDamage(stat);
        }
        return super.getPlayerStat(stat);
    }

    @Override
    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        switch (ability) {
            case 1:
                this.canCast[0] = false;
                if (this.dashTime == -1) {
                    this.actorsHitWithQ = new ArrayList<>();
                    this.dashTime = System.currentTimeMillis();
                    this.dashesRemaining = 3;
                    this.qCooldown = getReducedCooldown(cooldown);
                }
                if (dashesRemaining > 0) {
                    this.dashTime = System.currentTimeMillis();
                    Point2D origLocation = this.location;
                    Point2D dashPoint = this.dash(dest, false, 15d);
                    double time = origLocation.distance(dashPoint) / 15d;
                    this.qTime = (int) (time * 1000);
                    this.dashesRemaining--;
                    if (this.dashesRemaining == 0) {
                        this.dashTime = -1;
                        if (this.actorsHitWithQ.isEmpty()) this.qCooldown *= 0.7d;
                        ExtensionCommands.actorAbilityResponse(
                                parentExt, player, "q", true, (int) qCooldown, gCooldown);
                        ExtensionCommands.actorAnimate(
                                parentExt, room, id, "spell1c", qTime, false);
                    } else if (this.dashesRemaining == 1) {
                        ExtensionCommands.actorAnimate(
                                parentExt, room, id, "spell1b", qTime, false);
                    }
                    if (this.dashesRemaining != 0) {
                        ExtensionCommands.playSound(
                                parentExt, room, this.id, "sfx_fionna_dash_small", this.location);
                        ExtensionCommands.addStatusIcon(
                                parentExt,
                                player,
                                this.id + "_dash" + this.dashesRemaining,
                                this.dashesRemaining + " dashes remaining!",
                                "icon_fionna_s1",
                                W_DURATION);
                    } else {
                        ExtensionCommands.playSound(
                                parentExt, room, this.id, "sfx_fionna_dash_large", this.location);
                    }
                    if (this.dashesRemaining != 2)
                        ExtensionCommands.removeStatusIcon(
                                parentExt, player, this.id + "_dash" + (this.dashesRemaining + 1));
                    ExtensionCommands.playSound(
                            parentExt, room, this.id, "sfx_fionna_dash_wind", this.location);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            this.id,
                            "fionna_trail",
                            qTime,
                            this.id + "_dashTrail" + dashesRemaining,
                            true,
                            "",
                            true,
                            false,
                            this.team);
                    int gruntNum = 3 - this.dashesRemaining;
                    ExtensionCommands.playSound(
                            parentExt, room, this.id, "fionna_grunt" + gruntNum, this.location);
                    scheduleTask(
                            abilityRunnable(ability, spellData, cooldown, gCooldown, dashPoint),
                            qTime);
                }
                break;
            case 2:
                this.canCast[1] = false;
                if (this.swordType == SwordType.FEARLESS) this.swordType = SwordType.FIERCE;
                else this.swordType = SwordType.FEARLESS;
                this.handleSwordAnimation();
                ExtensionCommands.actorAbilityResponse(
                        parentExt, player, "w", true, getReducedCooldown(cooldown), gCooldown);
                int delay = getReducedCooldown(cooldown);
                scheduleTask(abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay);
                break;
            case 3:
                this.canCast[2] = false;
                try {
                    // BUFF Patch 9/7/24 this.stopMoving(castDelay);
                    this.ultActivated = true;
                    this.ultStartTime = System.currentTimeMillis();
                    ExtensionCommands.addStatusIcon(
                            this.parentExt,
                            this.player,
                            "fionna_ult",
                            "fionna_spell_3_description",
                            "icon_fionna_s3",
                            E_DURATION);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "sfx_fionna_invuln", this.location);
                    ExtensionCommands.playSound(
                            this.parentExt, this.room, this.id, "fionna_ult", this.location);
                    if (getHealth() > 0) {
                        ExtensionCommands.createActorFX(
                                this.parentExt,
                                this.room,
                                this.id,
                                "fionna_invuln_fx",
                                E_DURATION,
                                this.id + "_ult",
                                true,
                                "",
                                true,
                                false,
                                this.team);
                    }
                } catch (Exception exception) {
                    logExceptionMessage(avatar, ability);
                    exception.printStackTrace();
                }
                ExtensionCommands.actorAbilityResponse(
                        this.parentExt,
                        this.player,
                        "e",
                        true,
                        getReducedCooldown(cooldown),
                        gCooldown);
                int delay1 = getReducedCooldown(cooldown);
                scheduleTask(
                        abilityRunnable(ability, spellData, cooldown, gCooldown, dest), delay1);
                break;
            case 4:
                break;
        }
    }

    private double getPassiveAttackDamage(String stat) {
        double missingPHealth = 1d - this.getPHealth();
        double modifier = (0.8d * missingPHealth);
        return this.getStat(stat) * modifier;
    }

    private void handleSwordAnimation() {
        if (this.swordType == SwordType.FEARLESS) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fionna_sword_blue",
                    1000 * 60 * 15,
                    this.id + "_fearless",
                    true,
                    "Bip001 Prop1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fionna_health_regen",
                    1000 * 60 * 15,
                    this.id + "_blueRegen",
                    true,
                    "Bip001 Prop1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.removeFx(parentExt, room, this.id + "_fierce");
            ExtensionCommands.removeFx(parentExt, room, this.id + "_attackUp");
            ExtensionCommands.addStatusIcon(
                    parentExt, player, "fionna_fearless", "FEARLESS", "icon_fionna_s2b", 0f);
            ExtensionCommands.removeStatusIcon(parentExt, player, "fionna_fierce");
            ExtensionCommands.playSound(
                    parentExt, player, this.id, "sfx_fionna_health_regen", this.location);

        } else {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fionna_sword_pink",
                    1000 * 60 * 15,
                    this.id + "_fierce",
                    true,
                    "Bip001 Prop1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fionna_attack_up",
                    1000 * 60 * 15,
                    this.id + "_attackUp",
                    true,
                    "Bip001 Prop1",
                    true,
                    false,
                    this.team);
            ExtensionCommands.removeFx(parentExt, room, this.id + "_fearless");
            ExtensionCommands.removeFx(parentExt, room, this.id + "_blueRegen");
            ExtensionCommands.addStatusIcon(
                    parentExt, player, "fionna_fierce", "FIERCE", "icon_fionna_s2a", 0f);
            ExtensionCommands.removeStatusIcon(parentExt, player, "fionna_fearless");
            ExtensionCommands.playSound(
                    parentExt, player, this.id, "sfx_fionna_attack_up", this.location);
        }
        String[] statsToUpdate = {"healthRegen", "armor", "spellResist", "attackSpeed", "speed"};
        this.updateStatMenu(statsToUpdate);
        this.move(this.movementLine.getP2());
    }

    public boolean ultActivated() {
        return this.ultActivated;
    }

    private FionnaAbilityRunnable abilityRunnable(
            int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
        return new FionnaAbilityRunnable(ability, spellData, cooldown, gCooldown, dest);
    }

    private class FionnaAbilityRunnable extends AbilityRunnable {

        public FionnaAbilityRunnable(
                int ability, JsonNode spellData, int cooldown, int gCooldown, Point2D dest) {
            super(ability, spellData, cooldown, gCooldown, dest);
        }

        @Override
        protected void spellQ() {
            if (dashesRemaining > 0) canCast[0] = true;
            int dashInt = dashesRemaining + 1;
            float range = 1f;
            String explosionFx = "fionna_dash_explode_small";
            if (dashInt == 1) {
                range = 2.5f;
                explosionFx = "fionna_dash_explode";
                Runnable enableQCasting = () -> canCast[0] = true;
                int delay = getReducedCooldown(qCooldown) - qTime;
                scheduleTask(enableQCasting, delay);
            }
            if (getHealth() > 0) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        explosionFx,
                        id + "_explosion" + dashInt,
                        1500,
                        (float) dest.getX(),
                        (float) dest.getY(),
                        false,
                        team,
                        0f);
                if (dashInt == 1) {
                    ExtensionCommands.createWorldFX(
                            parentExt,
                            room,
                            id,
                            "fx_target_ring_2.5",
                            id + "qCircle",
                            800,
                            (float) dest.getX(),
                            (float) dest.getY(),
                            true,
                            team,
                            0f);
                }
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, dest, range)) {
                    if (a.getTeam() != team
                            && a.getActorType() != ActorType.BASE
                            && a.getActorType() != ActorType.TOWER) {
                        actorsHitWithQ.add(a);
                        double damage = getSpellDamage(spellData, true);
                        a.addToDamageQueue(Fionna.this, damage, spellData, false);
                        if (dashInt == 1) a.addState(ActorState.SLOWED, 0.5d, 1000);
                    }
                }
            }
        }

        @Override
        protected void spellW() {
            canCast[1] = true;
        }

        @Override
        protected void spellE() {
            canCast[2] = true;
        }

        @Override
        protected void spellPassive() {}
    }
}

package xyz.openatbp.extension.game;

import java.util.HashMap;
import java.util.Map;

public class SkinData {
    private static final Map<String, String> BMOPassiveFX = new HashMap<>();
    private static final Map<String, String> BMOBasicAttackProjectile = new HashMap<>();
    private static final Map<String, String> BMOQCameraFX = new HashMap<>();
    private static final Map<String, String> BMOWPixelsFX = new HashMap<>();
    private static final Map<String, String> BMOWRemoteFX = new HashMap<>();
    private static final Map<String, String> BMOWExplodeFX = new HashMap<>();
    private static final Map<String, String> BMOEProjectileFX = new HashMap<>();

    static {
        BMOPassiveFX.put("bmo_skin_noir", "bmo_passive_noire");
        BMOBasicAttackProjectile.put("bmo_skin_noir", "bmo_projectile_noire");
        BMOQCameraFX.put("bmo_skin_noir", "bmo_camera_noire");
        BMOWPixelsFX.put("bmo_skin_noir", "bmo_pixels_aoe_noire");
        BMOWRemoteFX.put("bmo_skin_noir", "bmo_remote_spin_noire");
        BMOWExplodeFX.put("bmo_skin_noir", "bmo_pixels_aoe_explode_noire");
        BMOEProjectileFX.put("bmo_skin_noir", "projectile_bmo_bee_noire");
    }

    public static String getBMOPassiveFX(String avatar) {
        return BMOPassiveFX.getOrDefault(avatar, "bmo_passive");
    }

    public static String getBMOBasicAttackProjectile(String avatar) {
        return BMOBasicAttackProjectile.getOrDefault(avatar, "bmo_projectile");
    }

    public static String getBMOQCameraFX(String avatar) {
        return BMOQCameraFX.getOrDefault(avatar, "bmo_camera");
    }

    public static String getBMOWPixelsFX(String avatar) {
        return BMOWPixelsFX.getOrDefault(avatar, "bmo_pixels_aoe");
    }

    public static String getBMOWRemoteFX(String avatar) {
        return BMOWRemoteFX.getOrDefault(avatar, "bmo_remote_spin");
    }

    public static String getBMOWExplodeFX(String avatar) {
        return BMOWExplodeFX.getOrDefault(avatar, "bmo_pixels_aoe_explode");
    }

    public static String getBMOEProjectileFX(String avatar) {
        return BMOEProjectileFX.getOrDefault(avatar, "projectile_bmo_bee");
    }

    private static final Map<String, String> BubbleGumBasicAttackEmit = new HashMap<>();
    private static final Map<String, String> BubbleGumQVO = new HashMap<>();
    private static final Map<String, String> BubbleGumWVO = new HashMap<>();
    private static final Map<String, String> BubbleGumEVO = new HashMap<>();
    private static final Map<String, String> BubbleGumEGruntVO = new HashMap<>();

    static {
        BubbleGumBasicAttackEmit.put("princessbubblegum_skin_hoth", "Tube");
        BubbleGumQVO.put("princessbubblegum_skin_prince", "vo/vo_gumball_potion");
        BubbleGumQVO.put("princessbubblegum_skin_young", "vo/vo_bubblegum_young_potion");
        BubbleGumWVO.put("princessbubblegum_skin_prince", "vo/vo_gumball_turret");
        BubbleGumWVO.put("princessbubblegum_skin_young", "vo/vo_bubblegum_young_turret");
        BubbleGumEVO.put("princessbubblegum_skin_prince", "vo/vo_gumball_bomb_hup");
        BubbleGumEVO.put("princessbubblegum_skin_young", "vo/vo_bubblegum_young_bomb_hup");
        BubbleGumEGruntVO.put("princessbubblegum_skin_prince", "vo/vo_gumball_turret");
        BubbleGumEGruntVO.put("princessbubblegum_skin_young", "vo/vo_bubblegum_young_bomb_grunt");
    }

    public static String getBubbleGumBasicAttackEmit(String avatar) {
        return BubbleGumBasicAttackEmit.getOrDefault(avatar, "weapon_holder");
    }

    public static String getBubbleGumQVO(String avatar) {
        return BubbleGumQVO.getOrDefault(avatar, "vo/vo_bubblegum_potion");
    }

    public static String getBubbleGumWVO(String avatar) {
        return BubbleGumWVO.getOrDefault(avatar, "vo/vo_bubblegum_turret");
    }

    public static String getBubbleGumEVO(String avatar) {
        return BubbleGumEVO.getOrDefault(avatar, "vo/vo_bubblegum_bomb_hup");
    }

    public static String getBubbleGumEGruntVO(String avatar) {
        return BubbleGumEGruntVO.getOrDefault(avatar, "vo/vo_bubblegum_bomb_grunt");
    }

    private static final Map<String, String> FinnQFX = new HashMap<>();
    private static final Map<String, String> FinnQSFX = new HashMap<>();
    private static final Map<String, String> FinnQShatterFX = new HashMap<>();
    private static final Map<String, String> FinnQShatterSFX = new HashMap<>();
    private static final Map<String, String> FinnWFX = new HashMap<>();
    private static final Map<String, String> FinnWSFX = new HashMap<>();
    private static final Map<String, String> FinnECornerSwordsFX = new HashMap<>();
    private static final Map<String, String> FinnEWallDropSFX = new HashMap<>();
    private static final Map<String, String> FinnEDestroySFX = new HashMap<>();

    static {
        FinnQFX.put("finn_skin_guardian", "finn_guardian_shieldShimmer");
        FinnQSFX.put("finn_skin_guardian", "sfx_finn_guardian_shield");
        FinnQShatterFX.put("finn_skin_guardian", "finn_guardian_shieldShatter");
        FinnQShatterSFX.put("finn_skin_guardian", "sfx_finn_guardian_shield_shatter");
        FinnWFX.put("finn_skin_guardian", "finn_guardian_dash_fx");
        FinnWSFX.put("finn_skin_guardian", "sfx_finn_guardian_dash_attack");
        FinnECornerSwordsFX.put("finn_skin_guardian", "finn_guardian_wall_corner_swords");
        FinnEWallDropSFX.put("finn_skin_guardian", "sfx_finn_guardian_walls_drop");
        FinnEDestroySFX.put("finn_skin_guardian", "sfx_finn_guardian_wall_destroyed");
    }

    public static String getFinnQFX(String avatar) {
        return FinnQFX.getOrDefault(avatar, "finn_shieldShimmer");
    }

    public static String getFinnQSFX(String avatar) {
        return FinnQSFX.getOrDefault(avatar, "sfx_finn_shield");
    }

    public static String getFinnQShatterFX(String avatar) {
        return FinnQShatterFX.getOrDefault(avatar, "finn_shieldShatter");
    }

    public static String getFinnQShatterSFX(String avatar) {
        return FinnQShatterSFX.getOrDefault(avatar, "sfx_finn_shield_shatter");
    }

    public static String getFinnWFX(String avatar) {
        return FinnWFX.getOrDefault(avatar, "finn_dash_fx");
    }

    public static String getFinnWSFX(String avatar) {
        return FinnWSFX.getOrDefault(avatar, "sfx_finn_dash_attack");
    }

    public static String getFinnEWallFX(String avatar, String direction) {
        String bundle = "finn_";
        if (avatar.equals("finn_skin_guardian")) {
            bundle += "guardian_";
        }
        bundle += "wall_" + direction;
        return bundle;
    }

    public static String getFinnECornerSwordsFX(String avatar) {
        return FinnECornerSwordsFX.getOrDefault(avatar, "finn_wall_corner_swords");
    }

    public static String getFinnEDestroyFX(String avatar, String direction) {
        String bundle = "finn_";
        if (avatar.equals("finn_skin_guardian")) {
            bundle += "guardian_";
        }
        bundle += "wall_" + direction + "_destroy";
        return bundle;
    }

    public static String getFinnEWallDropSFX(String avatar) {
        return FinnEWallDropSFX.getOrDefault(avatar, "sfx_finn_walls_drop");
    }

    public static String getFinnEDestroySFX(String avatar) {
        return FinnEDestroySFX.getOrDefault(avatar, "finn_wall_destroyed");
    }

    private static final Map<String, String> IceKingQVO = new HashMap<>();
    private static final Map<String, String> IceKingWVO = new HashMap<>();
    private static final Map<String, String> IceKingEVO = new HashMap<>();

    static {
        IceKingQVO.put("iceking_skin_icequeen", "vo/vo_ice_queen_freeze");
        IceKingQVO.put("iceking_skin_young", "vo/vo_ice_king_young_freeze");
        IceKingWVO.put("iceking_skin_icequeen", "vo/vo_ice_queen_hailstorm");
        IceKingWVO.put("iceking_skin_young", "vo/vo_ice_king_young_hailstorm");
        IceKingEVO.put("iceking_skin_icequeen", "vo/vo_ice_queen_ultimate");
        IceKingEVO.put("iceking_skin_young", "vo/vo_ice_king_young_ultimate");
    }

    public static String getIceKingQVO(String avatar) {
        return IceKingQVO.getOrDefault(avatar, "vo/vo_ice_king_freeze");
    }

    public static String getIceKingWVO(String avatar) {
        return IceKingWVO.getOrDefault(avatar, "vo/vo_ice_king_hailstorm");
    }

    public static String getIceKingEVO(String avatar) {
        return IceKingEVO.getOrDefault(avatar, "vo/vo_ice_king_ultimate");
    }

    private static final Map<String, String> JakeQVO = new HashMap<>();
    private static final Map<String, String> JakeQSFX = new HashMap<>();
    private static final Map<String, String> JakeWFX = new HashMap<>();
    private static final Map<String, String> JakeWDustUpFX = new HashMap<>();
    private static final Map<String, String> JakeWVO = new HashMap<>();
    private static final Map<String, String> JakeWSFX = new HashMap<>();
    private static final Map<String, String> JakeEFX = new HashMap<>();
    private static final Map<String, String> JakeEVO = new HashMap<>();
    private static final Map<String, String> JakeESFX = new HashMap<>();
    private static final Map<String, String> JakeEStompFX = new HashMap<>();
    private static final Map<String, String> JakeEStompSFX = new HashMap<>();
    private static final Map<String, String> JakeEStomp1SFX = new HashMap<>();

    static {
        JakeQVO.put("jake_skin_cake", "vo/vo_cake_stretch");
        JakeQVO.put("jake_skin_randy", "vo/vo_jake_butternubs_stretch");
        JakeQSFX.put("jake_skin_guardian", "sfx_jake_guardian_stretch");
        JakeWFX.put("jake_skin_cake", "fx_cake_ball");
        JakeWFX.put("jake_skin_guardian", "fx_jake_guardian_ball");
        JakeWFX.put("jake_skin_randy", "fx_jake_butternubs_ball");
        JakeWFX.put("jake_skin_wizard", "fx_jake_wizard_ball");
        // JakeWFX.put("jake_skin_zombie", "fx_jake_zombie_ball"); TODO: GAME ASSET IS MISSING!!

        JakeWDustUpFX.put("jake_skin_cake", "cake_dust_up");
        JakeWDustUpFX.put("jake_skin_guardian", "jake_guardian_dust_up");
        JakeWVO.put("jake_skin_cake", "vo/vo_cake_ball");
        JakeWVO.put("jake_skin_randy", "vo/vo_jake_butternubs_ball");
        JakeWSFX.put("jake_skin_guardian", "sfx_jake_guardian_ball");
        JakeEFX.put("jake_skin_cake", "jake_cake_big");
        JakeEFX.put("jake_skin_guardian", "jake_guardian_big");
        JakeEFX.put("jake_skin_randy", "jake_butternubs_big");
        JakeEFX.put("jake_skin_wizard", "jake_wizard_big");
        JakeEFX.put("jake_skin_zombie", "jake_zombie_big");
        JakeEVO.put("jake_skin_cake", "vo/vo_cake_grow");
        JakeEVO.put("jake_skin_randy", "vo/vo_jake_butternubs_grow");
        JakeESFX.put("jake_skin_guardian", "sfx_jake_guardian_grow");
        JakeEStompFX.put("jake_skin_cake", "cake_stomp_fx");
        JakeEStompFX.put("jake_skin_guardian", "jake_guardian_stomp_fx");
        JakeEStompSFX.put("jake_skin_guardian", "sfx_jake_guardian_grow_stomp");
        JakeEStomp1SFX.put("jake_skin_guardian", "sfx_jake_guardian_grow_stomp1");
    }

    public static String getJakeQVO(String avatar) {
        return JakeQVO.getOrDefault(avatar, "vo/vo_jake_stretch");
    }

    public static String getJakeQSFX(String avatar) {
        return JakeQSFX.getOrDefault(avatar, "sfx_jake_stretch");
    }

    public static String getJakeWFX(String avatar) {
        return JakeWFX.getOrDefault(avatar, "fx_jake_ball");
    }

    public static String getJakeWDustUpFX(String avatar) {
        return JakeWDustUpFX.getOrDefault(avatar, "jake_dust_up");
    }

    public static String getJakeWVO(String avatar) {
        return JakeWVO.getOrDefault(avatar, "vo/vo_jake_ball");
    }

    public static String getJakeWSFX(String avatar) {
        return JakeWSFX.getOrDefault(avatar, "sfx_jake_ball");
    }

    public static String getJakeEFX(String avatar) {
        return JakeEFX.getOrDefault(avatar, "jake_big");
    }

    public static String getJakeEVO(String avatar) {
        return JakeEVO.getOrDefault(avatar, "vo/vo_jake_grow");
    }

    public static String getJakeESFX(String avatar) {
        return JakeESFX.getOrDefault(avatar, "sfx_jake_grow");
    }

    public static String getJakeEStompFX(String avatar) {
        return JakeEStompFX.getOrDefault(avatar, "jake_stomp_fx");
    }

    public static String getJakeEStompSFX(String avatar) {
        return JakeEStompSFX.getOrDefault(avatar, "sfx_jake_grow_stomp");
    }

    public static String getJakeEStomp1SFX(String avatar) {
        return JakeEStomp1SFX.getOrDefault(avatar, "sfx_jake_grow_stomp1");
    }

    private static final Map<String, String> LSPQVO = new HashMap<>();
    private static final Map<String, String> LSPWVO = new HashMap<>();
    private static final Map<String, String> LSPEProjectile = new HashMap<>();
    private static final Map<String, String> LSPEVO = new HashMap<>();

    static {
        LSPQVO.put("lsp_skin_gummybuns", "vo/vo_lsp_gummybuns_drama_beam");
        LSPQVO.put("lsp_skin_lsprince", "vo/vo_lsprince_drama_beam");
        LSPWVO.put("lsp_skin_gummybuns", "vo/vo_lsp_gummybuns_lumps_aoe");
        LSPWVO.put("lsp_skin_lsprince", "vo/vo_lsprince_lumps_aoe");
        LSPEProjectile.put("lsp_skin_lsprince", "projectile_lsprince_ult");
        LSPEVO.put("lsp_skin_gummybuns", "vo/vo_lsp_gummybuns_cellphone_throw");
        LSPEVO.put("lsp_skin_lsprince", "vo/vo_lsprince_cellphone_throw");
    }

    public static String getLSPQVO(String avatar) {
        return LSPQVO.getOrDefault(avatar, "vo/vo_lsp_drama_beam");
    }

    public static String getLSPWVO(String avatar) {
        return LSPWVO.getOrDefault(avatar, "vo/vo_lsp_lumps_aoe");
    }

    public static String getLSPEProjectile(String avatar) {
        return LSPEProjectile.getOrDefault(avatar, "projectile_lsp_ult");
    }

    public static String getLSPEVO(String avatar) {
        return LSPEVO.getOrDefault(avatar, "vo/vo_lsp_cellphone_throw");
    }

    private static final Map<String, String> MarcelineQBeastVO = new HashMap<>();
    private static final Map<String, String> MarcelineQVampireVO = new HashMap<>();
    private static final Map<String, String> MarcelineWVO = new HashMap<>();
    private static final Map<String, String> MarcelineEVampireVO = new HashMap<>();
    private static final Map<String, String> MarcelineEBeastVO = new HashMap<>();

    // marceline_skin_marshall marceline_skin_young
    static {
        MarcelineQBeastVO.put("marceline_skin_marshall", "vo/vo_marshall_lee_projectile_beast");
        MarcelineQVampireVO.put("marceline_skin_marshall", "vo/vo_marshall_lee_projectile_human");
        MarcelineQVampireVO.put("marceline_skin_young", "vo/vo_marceline_young_projectile_human");
        MarcelineWVO.put("marceline_skin_marshall", "vo/vo_marshall_lee_blood_mist");
        MarcelineWVO.put("marceline_skin_young", "vo/vo_marceline_young_blood_mist");
        MarcelineEBeastVO.put("marceline_skin_marshall", "vo/marshall_lee_morph_to_beast");
        MarcelineEVampireVO.put("marceline_skin_marshall", "vo/marshall_lee_morph_to_human");
    }

    public static String getMarcelineQVO(String avatar, String form) {
        switch (form) {
            case "VAMPIRE":
                return MarcelineQVampireVO.getOrDefault(avatar, "vo/vo_marceline_projectile_human");

            case "BEAST":
                return MarcelineQBeastVO.getOrDefault(avatar, "vo/vo_marceline_projectile_beast");
        }
        return "";
    }

    public static String getMarcelineWVO(String avatar) {
        return MarcelineWVO.getOrDefault(avatar, "vo/vo_marceline_blood_mist");
    }

    public static String getMarcelineEBeastVO(String avatar) {
        return MarcelineEBeastVO.getOrDefault(avatar, "vo/marceline_morph_to_beast");
    }

    public static String getMarcelineEVampireVO(String avatar) {
        return MarcelineEVampireVO.getOrDefault(avatar, "vo/marceline_morph_to_human");
    }

    private static final String[] NeptrMoveSFX = {
        "sfx_neptr_move", "neptr_racing_move", "neptr_racing_move_fast"
    };
    private static final Map<String, String> NeptrMoveEndSFX = new HashMap<>();
    private static final Map<String, String> NeptrPassiveSFX = new HashMap<>();

    static {
        NeptrMoveEndSFX.put("neptr_skin_racing", "neptr_racing_move_stop");
        NeptrPassiveSFX.put("neptr_skin_racing", "neptr_racing_passive");
    }

    public static String getNeptrMoveSFX(String avatar, boolean passiveActive) {
        if (avatar.equals("neptr_skin_racing") && passiveActive) return NeptrMoveSFX[2];
        else if (avatar.equals("neptr_skin_racing")) return NeptrMoveSFX[1];
        else return NeptrMoveSFX[0];
    }

    public static String getNeptrMoveEndSFX(String avatar) {
        return NeptrMoveEndSFX.getOrDefault(avatar, "sfx_neptr_move_end");
    }

    public static String getNeptrPassiveSFX(String avatar) {
        return NeptrPassiveSFX.getOrDefault(avatar, "sfx_neptr_passive");
    }

    private static final Map<String, String> PeppermintButlerWHohoVO = new HashMap<>();
    private static final Map<String, String> PeppermintButlerWBeholdVO = new HashMap<>();
    private static final Map<String, String> PeppermintButlerEVO = new HashMap<>();

    static {
        PeppermintButlerWHohoVO.put("peppermintbutler_skin_zombie", "vo/vo_pepbut_zombie_hoho");
        PeppermintButlerWBeholdVO.put("peppermintbutler_skin_zombie", "vo/vo_pepbut_zombie_behold");
        PeppermintButlerEVO.put("peppermintbutler_skin_zombie", "vo/vo_pepbut_zombie_feral_hiss");
    }

    public static String getPeppermintButlerWHohoVO(String avatar) {
        return PeppermintButlerWHohoVO.getOrDefault(avatar, "vo/vo_pepbut_hoho");
    }

    public static String getPeppermintButlerWBeholdVO(String avatar) {
        return PeppermintButlerWBeholdVO.getOrDefault(avatar, "vo/vo_pepbut_behold");
    }

    public static String getPeppermintButlerEVO(String avatar) {
        return PeppermintButlerEVO.getOrDefault(avatar, "vo/vo_pepbut_feral_hiss");
    }

    private static final Map<String, String> RattleBallsQTrailFX = new HashMap<>();
    private static final Map<String, String> RattleBallsQDustFX = new HashMap<>();
    private static final Map<String, String> RattleBallsQEndSFX = new HashMap<>();
    private static final Map<String, String> RattleBallsQCounterFX = new HashMap<>();
    private static final Map<String, String> RattleBallsQCounterSFX = new HashMap<>();
    private static final Map<String, String> RattleBallsWFX = new HashMap<>();
    private static final Map<String, String> RattleBallsWSFX = new HashMap<>();
    private static final Map<String, String> RattleBallsESpinCycleSFX = new HashMap<>();
    private static final Map<String, String> RattleBallsESwordSpinFX = new HashMap<>();
    private static final Map<String, String> RattleBallsESparklesFX = new HashMap<>();

    static {
        RattleBallsQTrailFX.put("rattleballs_skin_spidotron", "rattleballs_luchador_dash_trail");
        RattleBallsQDustFX.put("rattleballs_skin_spidotron", "rattleballs_luchador_dash_dust");
        RattleBallsQEndSFX.put(
                "rattleballs_skin_spidotron", "sfx_rattleballs_luchador_counter_attack");
        RattleBallsQCounterFX.put("rattleballs_skin_spidotron", "rattleballs_luchador_dash_hit");
        RattleBallsQCounterSFX.put(
                "rattleballs_skin_spidotron", "sfx_rattleballs_luchador_counter_attack_crit");
        RattleBallsWFX.put("rattleballs_skin_spidotron", "rattleballs_luchador_pull");
        RattleBallsWSFX.put("rattleballs_skin_spidotron", "sfx_rattleballs_luchador_pull");
        RattleBallsESwordSpinFX.put(
                "rattleballs_skin_spidotron", "rattleballs_luchador_sword_spin");
        RattleBallsESpinCycleSFX.put(
                "rattleballs_skin_spidotron", "sfx_rattleballs_luchador_spin_cycle");
        RattleBallsESparklesFX.put(
                "rattleballs_skin_spidotron", "rattleballs_luchador_sword_sparkles");
    }

    public static String getRattleBallsQTrailFX(String avatar) {
        return RattleBallsQTrailFX.getOrDefault(avatar, "rattleballs_dash_trail");
    }

    public static String getRattleBallsQDustFX(String avatar) {
        return RattleBallsQDustFX.getOrDefault(avatar, "rattleballs_dash_dust");
    }

    public static String getRattleBallsQEndSFX(String avatar) {
        return RattleBallsQEndSFX.getOrDefault(avatar, "sfx_rattleballs_spin");
    }

    public static String getRattleBallsQCounterFX(String avatar) {
        return RattleBallsQCounterFX.getOrDefault(avatar, "rattleballs_dash_hit");
    }

    public static String getRattleBallsQCounterSFX(String avatar) {
        return RattleBallsQCounterSFX.getOrDefault(avatar, "sfx_rattleballs_counter_stance");
    }

    public static String getRattleBallsWFX(String avatar) {
        return RattleBallsWFX.getOrDefault(avatar, "rattleballs_pull");
    }

    public static String getRattleBallsWSFX(String avatar) {
        return RattleBallsWSFX.getOrDefault(avatar, "sfx_rattleballs_pull");
    }

    public static String getRattleBallsESwordSpinFX(String avatar) {
        return RattleBallsESwordSpinFX.getOrDefault(avatar, "rattleballs_sword_spin");
    }

    public static String getRattleBallsESpinCycleSFX(String avatar) {
        return RattleBallsESpinCycleSFX.getOrDefault(avatar, "sfx_rattleballs_spin_cycle");
    }

    public static String getRattleBallsESparklesFX(String avatar) {
        return RattleBallsESparklesFX.getOrDefault(avatar, "rattleballs_sword_sparkles");
    } // doesn't seem to work correctly with any emit node :(
}

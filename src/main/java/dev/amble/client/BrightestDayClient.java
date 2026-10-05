package dev.amble.client;

import dev.amble.client.effects.ArmedPose;
import dev.amble.client.effects.BeamEffects;
import dev.amble.client.effects.RemoteAim;
import dev.amble.client.effects.HealBeamEffects;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.ConstructClient;
import dev.amble.client.effects.ElementAura;
import dev.amble.client.compat.IrisCompat;
import dev.amble.client.compat.ReplaySnapshot;
import dev.amble.client.effects.AbilityClient;
import dev.amble.client.effects.attacks.area.BarrageEffects;
import dev.amble.client.effects.attacks.area.NovaEffects;
import dev.amble.client.effects.attacks.area.SlamEffects;
import dev.amble.client.effects.attacks.projectile.ChainBoltEffects;
import dev.amble.client.effects.attacks.projectile.DiscEffects;
import dev.amble.client.effects.attacks.projectile.LanceEffects;
import dev.amble.client.effects.attacks.projectile.ProjectileBursts;
import dev.amble.client.effects.attacks.projectile.SwarmEffects;
import dev.amble.client.effects.attacks.utility.GrappleEffects;
import dev.amble.client.effects.attacks.utility.LumberjackEffects;
import dev.amble.client.effects.attacks.utility.OreProbeEffects;
import dev.amble.client.effects.attacks.weapon.FistEffects;
import dev.amble.client.effects.attacks.weapon.TurretEffects;
import dev.amble.client.effects.attacks.weapon.WhipEffects;
import dev.amble.client.effects.drill.DrillEffects;
import dev.amble.client.effects.drill.PlacedDrillEffects;
import dev.amble.client.effects.glide.GlideEffects;
import dev.amble.client.effects.AcidEffects;
import dev.amble.client.effects.ConcussiveEffects;
import dev.amble.client.effects.LightOrbEffects;
import dev.amble.client.effects.RingInput;
import dev.amble.client.effects.ScanEffects;
import dev.amble.client.effects.SpotlightEffects;
import dev.amble.client.effects.SculptClient;
import dev.amble.client.wheel.PowerWheel;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.TractorEffects;
import dev.amble.client.effects.WallEffects;
import dev.amble.client.forge.ForgeClient;
import dev.amble.client.flight.FlightAnimations;
import dev.amble.client.flight.FlightAnimator;
import dev.amble.client.flight.AileronRolls;
import dev.amble.client.team.ClientTeams;
import dev.amble.client.space.SpaceHud;
import dev.amble.client.space.SpaceRenderer;
import dev.amble.client.flight.FlightTrail;
import dev.amble.client.hud.FlightSpeedHud;
import dev.amble.client.hud.RingChargeHud;
import dev.amble.client.render.LanternBlockEntityRenderer;
import dev.amble.client.render.GlowAura;
import dev.amble.client.render.LanternSuit;
import dev.amble.client.render.SuitGlowLayer;
import dev.amble.client.render.SlottedRingLayer;
import dev.amble.client.screens.LanternButtons;
import dev.amble.client.screens.LanternScreen;
import dev.amble.core.BrightestDayBlockEntityTypes;
import dev.amble.core.BrightestDayMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

public class BrightestDayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BrightestDayKeybinds.init();
        FlightAnimator.init();
        FlightAnimations.init();
        FlightTrail.init();
        AileronRolls.init();
        ClientTeams.init();
        SpaceRenderer.init();
        SpaceHud.init();
        BlastEffects.init();
        ShieldEffects.init();
        WallEffects.init();
        BeamEffects.init();
        RemoteAim.init();
        HealBeamEffects.init();
        ArmedPose.init();
        ElementAura.init();
        TractorEffects.init();
        ScanEffects.init();
        GlowAura.init();
        LanternSuit.init();
        ForgeClient.init();
        ConstructClient.init();
        SculptClient.init();
        PowerWheel.init();
        RingInput.init();
        AbilityClient.init();
        ConcussiveEffects.init();
        NovaEffects.init();
        SlamEffects.init();
        BarrageEffects.init();
        FistEffects.init();
        WhipEffects.init();
        TurretEffects.init();
        ProjectileBursts.init();
        SwarmEffects.init();
        LanceEffects.init();
        ChainBoltEffects.init();
        DiscEffects.init();
        DrillEffects.init();
        PlacedDrillEffects.init();
        GlideEffects.init();
        GrappleEffects.init();
        LumberjackEffects.init();
        OreProbeEffects.init();
        AcidEffects.init();
        LightOrbEffects.init();
        SpotlightEffects.init();
        ReplaySnapshot.init();
        IrisCompat.init();
        LanternButtons.init();
        RingChargeHud.init();
        FlightSpeedHud.init();
        MenuScreens.register(BrightestDayMenus.LANTERN, LanternScreen::new);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                helper.register(new SlottedRingLayer(avatarRenderer));
                helper.register(new SuitGlowLayer(avatarRenderer));
            }
        });
        registerBlockEntityRenderers();
    }

    private void registerBlockEntityRenderers() {
        BlockEntityRendererRegistry.register(BrightestDayBlockEntityTypes.LANTERN_BLOCK_ENTITY_TYPE, LanternBlockEntityRenderer::new);
    }
}

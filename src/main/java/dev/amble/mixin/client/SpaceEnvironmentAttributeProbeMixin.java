package dev.amble.mixin.client;

import dev.amble.client.space.PlanetSurfaces;
import dev.amble.client.space.SpaceSkyRenderer;
import dev.amble.core.space.Cosmos;
import dev.amble.core.space.SpaceTravel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeProbe.class)
public class SpaceEnvironmentAttributeProbeMixin {

    @Unique
    private static final Vector3fc SPACE_BLACK = new Vector3f(0.0F, 0.0F, 0.0F);

    @Unique
    private static final Vector3fc ANTIMATTER_RED = new Vector3f(0.07F, 0.0F, 0.016F);

    @Unique
    private static final float NIGHT_SHADE = 0.06F;

    @Unique
    private static final int EARTH_SKY = 0x78A7FF;

    @Unique
    private static final int EARTH_FOG = 0xC0D8FF;

    @Unique
    private static final int EARTH_CLOUDS = 0xFFFFFF;

    @Shadow
    private Level level;

    @Shadow
    private Vec3 position;

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void brightestday$atmosphereFade(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
        if (this.level == null || this.position == null || !this.level.isClientSide())
            return;

        Object value = cir.getReturnValue();
        Cosmos.Planet planet = Cosmos.planetAt(this.level.dimension(), this.position.x);
        if (planet != null) {
            float daylight = SpaceSkyRenderer.surfaceDaylight((ClientLevel) this.level, this.position, partialTick);
            float shade = NIGHT_SHADE + (1.0F - NIGHT_SHADE) * daylight;
            if (attribute == EnvironmentAttributes.SKY_COLOR) value = brightestday$rgb(planet.sky() != 0 ? planet.sky() : EARTH_SKY).mul(shade);
            else if (attribute == EnvironmentAttributes.FOG_COLOR) value = brightestday$rgb(planet.fog() != 0 ? planet.fog() : EARTH_FOG).mul(shade);
            else if (attribute == EnvironmentAttributes.CLOUD_COLOR) value = new Vector4f(brightestday$rgb(planet.clouds() != 0 ? planet.clouds() : EARTH_CLOUDS).mul(shade), PlanetSurfaces.atmosphere(planet.style()) == 0 ? 0.0F : 0.9F);
            else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) value = 0.0F;
            else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) value = new Vector4f(((Vector4fc) value).x(), ((Vector4fc) value).y(), ((Vector4fc) value).z(), 0.0F);
            cir.setReturnValue(value);
        }

        float t = SpaceTravel.atmosphereFade(this.level, this.position.y);
        if (t <= 0.0F)
            return;

        if (attribute == EnvironmentAttributes.SKY_COLOR || attribute == EnvironmentAttributes.FOG_COLOR) {
            Cosmos.Sector sector = Cosmos.sector(this.level.dimension());
            cir.setReturnValue(new Vector3f((Vector3fc) value).lerp(sector != null && sector.antimatter() ? ANTIMATTER_RED : SPACE_BLACK, t));
        } else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
            Vector4f glow = new Vector4f((Vector4fc) value);
            glow.w *= 1.0F - t;
            cir.setReturnValue(glow);
        } else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS && planet == null) {
            cir.setReturnValue(Math.max((Float) value, t));
        } else if (attribute == EnvironmentAttributes.CLOUD_COLOR && SpaceTravel.isSpace(this.level)) {
            Vector4f clouds = new Vector4f((Vector4fc) value);
            clouds.w = 0;
            cir.setReturnValue(clouds);
        }
    }

    @Unique
    private static Vector3f brightestday$rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
}

package dev.amble.core.visuals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.Optional;

public record InsigniaAnchor(float x, float y, float distance, int echoes, float scale) {
    public static final float WIDTH = 16.0F;
    public static final float HEIGHT = 32.0F;
    public static final float STEP = 0.5F;
    public static final float MAX_DISTANCE = 0.8F;
    public static final float DISTANCE_STEP = 0.05F;
    public static final int MAX_ECHOES = 2;
    public static final float MIN_SCALE = 0.25F;
    public static final float MAX_SCALE = 2.0F;
    public static final float SCALE_STEP = 0.05F;
    private static final float LEGACY_X = 4.0F;
    private static final float LEGACY_Y = 8.0F;
    public static final InsigniaAnchor DEFAULT = new InsigniaAnchor(8.0F, 9.5F, 0.3F, 0, 1.0F);

    public enum Region {
        HEAD(4, 0, 12, 8, 0.0F, 0.0F, -4.0F),
        BODY(4, 8, 12, 20, 0.0F, 0.0F, -2.0F),
        RIGHT_LEG(4, 20, 8, 32, -1.9F, 12.0F, -2.0F),
        LEFT_LEG(8, 20, 12, 32, 1.9F, 12.0F, -2.0F),
        RIGHT_ARM(0, 8, 4, 20, -5.0F, 2.0F, -2.0F),
        LEFT_ARM(12, 8, 16, 20, 5.0F, 2.0F, -2.0F);

        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final float pivotX;
        public final float pivotY;
        public final float front;

        Region(int left, int top, int right, int bottom, float pivotX, float pivotY, float front) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.front = front;
        }

        boolean contains(float x, float y) {
            return x >= this.left && x <= this.right && y >= this.top && y <= this.bottom;
        }

        float distanceSqr(float x, float y) {
            float dx = x - Mth.clamp(x, this.left, this.right);
            float dy = y - Mth.clamp(y, this.top, this.bottom);
            return dx * dx + dy * dy;
        }

        public float localX(float x) {
            return x - WIDTH * 0.5F - this.pivotX;
        }

        public float localY(float y) {
            return y - LEGACY_Y - this.pivotY;
        }

        public static Region at(float x, float y) {
            Region best = BODY;
            float closest = Float.MAX_VALUE;
            for (Region region : values()) {
                if (region.contains(x, y)) return region;
                float distance = region.distanceSqr(x, y);
                if (distance < closest) {
                    closest = distance;
                    best = region;
                }
            }
            return best;
        }
    }

    public static final Codec<InsigniaAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("front_x").forGetter(anchor -> Optional.of(anchor.x)),
            Codec.FLOAT.optionalFieldOf("front_y").forGetter(anchor -> Optional.of(anchor.y)),
            Codec.FLOAT.optionalFieldOf("x").forGetter(anchor -> Optional.empty()),
            Codec.FLOAT.optionalFieldOf("y").forGetter(anchor -> Optional.empty()),
            Codec.FLOAT.optionalFieldOf("distance", DEFAULT.distance).forGetter(InsigniaAnchor::distance),
            Codec.INT.optionalFieldOf("echoes", DEFAULT.echoes).forGetter(InsigniaAnchor::echoes),
            Codec.FLOAT.optionalFieldOf("scale", DEFAULT.scale).forGetter(InsigniaAnchor::scale)
    ).apply(instance, (frontX, frontY, legacyX, legacyY, distance, echoes, scale) -> new InsigniaAnchor(
            frontX.orElseGet(() -> legacyX.map(x -> x + LEGACY_X).orElse(DEFAULT.x)),
            frontY.orElseGet(() -> legacyY.map(y -> y + LEGACY_Y).orElse(DEFAULT.y)),
            distance, echoes, scale)));

    public static final StreamCodec<ByteBuf, InsigniaAnchor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, InsigniaAnchor::x,
            ByteBufCodecs.FLOAT, InsigniaAnchor::y,
            ByteBufCodecs.FLOAT, InsigniaAnchor::distance,
            ByteBufCodecs.VAR_INT, InsigniaAnchor::echoes,
            ByteBufCodecs.FLOAT, InsigniaAnchor::scale,
            InsigniaAnchor::new
    );

    public Region region() {
        return Region.at(this.x, this.y);
    }

    public InsigniaAnchor withPosition(float x, float y) {
        return new InsigniaAnchor(x, y, this.distance, this.echoes, this.scale);
    }

    public InsigniaAnchor withDistance(float distance) {
        return new InsigniaAnchor(this.x, this.y, distance, this.echoes, this.scale);
    }

    public InsigniaAnchor withEchoes(int echoes) {
        return new InsigniaAnchor(this.x, this.y, this.distance, echoes, this.scale);
    }

    public InsigniaAnchor withScale(float scale) {
        return new InsigniaAnchor(this.x, this.y, this.distance, this.echoes, scale);
    }

    public InsigniaAnchor sanitized() {
        float x = snap(this.x, STEP, 0.0F, WIDTH);
        float y = snap(this.y, STEP, 0.0F, HEIGHT);
        Region region = Region.at(x, y);
        return new InsigniaAnchor(Mth.clamp(x, region.left, region.right), Mth.clamp(y, region.top, region.bottom),
                snap(this.distance, DISTANCE_STEP, 0.0F, MAX_DISTANCE), Mth.clamp(this.echoes, 0, MAX_ECHOES), snap(this.scale, SCALE_STEP, MIN_SCALE, MAX_SCALE));
    }

    private static float snap(float value, float step, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Mth.clamp(Math.round(value / step) * step, min, max);
    }
}

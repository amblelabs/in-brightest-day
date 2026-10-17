package dev.amble.core.progression;

import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public record Milestone(String key, LanternCorps corps, int tier, int goal, Trigger trigger, Condition condition, Mode mode, @Nullable String singleplayer) {

    public enum Mode {
        ACCUMULATE,
        REACH,
        DISTINCT,
        STREAK
    }

    @FunctionalInterface
    public interface Condition {
        boolean test(ServerPlayer player, Context context);
    }

    public record Context(@Nullable LivingEntity target, String detail, boolean ring, boolean feared) {
        public static final Context NONE = new Context(null, "", false, false);

        public static Context of(@Nullable LivingEntity target) {
            return new Context(target, "", false, false);
        }

        public static Context of(String detail) {
            return new Context(null, detail, false, false);
        }
    }

    public boolean multiplayerOnly() {
        return this.singleplayer != null;
    }

    public String translationKey() {
        return "milestone.brightestday." + this.key;
    }
}

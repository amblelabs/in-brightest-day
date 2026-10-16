package dev.amble.client.effects;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

public class SafeAdjustmentModifier extends AdjustmentModifier {
    private boolean ready;

    public SafeAdjustmentModifier(Function<String, Optional<PartModifier>> source) {
        super(source);
    }

    @Override
    public void tick(AnimationData state) {
        super.tick(state);
        this.ready = state != null;
    }

    @Override
    public void setupAnim(AnimationData state) {
        super.setupAnim(state);
        this.ready = state != null;
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone bone) {
        if (this.ready) {
            super.get3DTransform(bone);
            return;
        }
        boolean enabled = this.enabled;
        this.enabled = false;
        try {
            super.get3DTransform(bone);
        } finally {
            this.enabled = enabled;
        }
    }
}

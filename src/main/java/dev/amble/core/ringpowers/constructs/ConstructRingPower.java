package dev.amble.core.ringpowers.constructs;

import dev.amble.core.mannequin.Mannequins;
import com.mojang.serialization.MapCodec;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.CorpsSynergy;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public abstract class ConstructRingPower extends RingPower<Unit> {

    protected ConstructRingPower(Identifier id) {
        this(id, CorpsArsenal.SHARED);
    }

    protected ConstructRingPower(Identifier id, Set<LanternCorps> corps) {
        super(id, corps, MapCodec.unitCodec(Unit.INSTANCE));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.CONSTRUCT;
    }

    @Override
    public Unit createData() {
        return Unit.INSTANCE;
    }

    public int cost(int radius) {
        return this.useCost();
    }

    public boolean usesSize() {
        return false;
    }

    public int minSize() {
        return 1;
    }

    public int maxSize() {
        return 1;
    }

    public int defaultSize() {
        return this.minSize();
    }

    public int empoweredMaxSize() {
        return this.maxSize();
    }

    public int maxSize(Player player) {
        if (CorpsSynergy.empoweredByHope(player)) return this.empoweredMaxSize();
        if (CorpsSynergy.weakenedByHope(player)) return CorpsSynergy.weakenSize(this.minSize(), this.maxSize());
        return this.maxSize();
    }

    public int clampSize(int size) {
        return Mth.clamp(size, this.minSize(), this.maxSize());
    }

    public int clampSize(Player player, int size) {
        return Mth.clamp(size, this.minSize(), this.maxSize(player));
    }

    protected int costSize(int size) {
        return Mth.clamp(size, this.minSize(), this.empoweredMaxSize());
    }

    public Component describeSize(int size) {
        return Component.literal(String.valueOf(this.clampSize(size)));
    }

    public boolean usesGesture() {
        return false;
    }

    public boolean sustained(Player player) {
        return this.sustained();
    }

    public boolean sustained() {
        return false;
    }

    public int chargeTicks() {
        return 25;
    }

    public int releaseTicks() {
        return -1;
    }

    public abstract void fire(ServerPlayer player, int radius, int color);

    public record Aim(Vec3 eye, Vec3 look, Vec3 end, @Nullable Entity entity) {}

    public static Aim aim(ServerPlayer player, double range) {
        return aim(player, range, ClipContext.Fluid.NONE);
    }

    public static Aim aim(ServerPlayer player, double range, ClipContext.Fluid fluid) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, fluid, player));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();

        AABB searchArea = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, searchArea,
                entity -> !Mannequins.isHologram(entity) && entity != player && !entity.isSpectator() && entity.isPickable(), 0.3F);
        if (entityHit != null) return new Aim(eye, look, entityHit.getLocation(), entityHit.getEntity());
        return new Aim(eye, look, end, null);
    }
}

package dev.amble.core.hand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HandPoses {
    public static final int CUSTOM_SLOTS = 4;
    public static final int MAX_NAME = 16;

    public record Custom(int slot, String name, HandPose pose) {
        public static final Codec<Custom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, CUSTOM_SLOTS - 1).fieldOf("slot").forGetter(Custom::slot),
                Codec.string(0, MAX_NAME).fieldOf("name").forGetter(Custom::name),
                HandPose.CODEC.fieldOf("pose").forGetter(Custom::pose)
        ).apply(instance, Custom::new));
    }

    public record Selection(HandDirection direction, int pose) {
        public static final Selection DEFAULT = new Selection(HandDirection.FORWARD, HandPreset.FIST.ordinal());
    }

    public static final AttachmentType<List<Custom>> CUSTOM = AttachmentRegistry.<List<Custom>>builder()
            .initializer(List::of)
            .persistent(Custom.CODEC.listOf())
            .copyOnDeath()
            .syncWith(ByteBufCodecs.fromCodec(Custom.CODEC.listOf()), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(BrightestDay.id("hand_poses"));

    private static final Map<UUID, Selection> SERVER = new ConcurrentHashMap<>();
    private static volatile Selection client = Selection.DEFAULT;

    public static void init() {
        ServerPlayerEvents.LEAVE.register(player -> SERVER.remove(player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER.clear());
    }

    public static int poseCount() {
        return HandPreset.values().length + CUSTOM_SLOTS;
    }

    public static Selection selection(Player player) {
        return player.level().isClientSide() ? client : SERVER.getOrDefault(player.getUUID(), Selection.DEFAULT);
    }

    public static void select(Player player, int direction, int pose) {
        SERVER.put(player.getUUID(), new Selection(HandDirection.byId(direction), Mth.clamp(pose, 0, poseCount() - 1)));
    }

    public static Selection client() {
        return client;
    }

    public static void setClient(Selection selection) {
        client = selection;
    }

    public static List<Custom> customs(Player player) {
        return player.getAttachedOrElse(CUSTOM, List.of());
    }

    public static Optional<Custom> custom(Player player, int slot) {
        for (Custom custom : customs(player)) if (custom.slot() == slot) return Optional.of(custom);
        return Optional.empty();
    }

    public static HandPose resolve(Player player, int pose) {
        HandPreset[] presets = HandPreset.values();
        if (pose < presets.length) return presets[Math.max(0, pose)].pose();
        return custom(player, pose - presets.length).map(Custom::pose).orElse(HandPreset.FIST.pose());
    }

    public static Component label(Player player, int pose) {
        HandPreset[] presets = HandPreset.values();
        if (pose < presets.length) return presets[Math.max(0, pose)].label();
        int slot = pose - presets.length;
        return custom(player, slot).filter(custom -> !custom.name().isBlank()).<Component>map(custom -> Component.literal(custom.name()))
                .orElse(Component.translatable("gui.brightestday.hand.custom_slot", slot + 1));
    }

    public static void save(Player player, int slot, String name, HandPose pose) {
        if (slot < 0 || slot >= CUSTOM_SLOTS) return;
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME) trimmed = trimmed.substring(0, MAX_NAME);
        List<Custom> customs = new ArrayList<>(customs(player));
        customs.removeIf(custom -> custom.slot() == slot);
        customs.add(new Custom(slot, trimmed, pose.sanitized()));
        player.setAttached(CUSTOM, List.copyOf(customs));
    }

    private HandPoses() {}
}

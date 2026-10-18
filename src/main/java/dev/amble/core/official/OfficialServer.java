package dev.amble.core.official;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OfficialServer {
    public static final String NAME = "In Brightest Day - Official Server";
    public static final String IP = "168.100.161.33:25578";
    private static final String PREFIX = "brightestday:roster:";
    private static final int MAX_MEMBERS = 300;

    public record Member(UUID id, String name, Optional<LanternCorps> corps, int color) {}

    public static Member member(ServerPlayer player) {
        Optional<LanternCorps> corps = PowerRingItem.getWornCorps(player);
        return new Member(player.getUUID(), player.getGameProfile().name(), corps, corps.isPresent() ? CorpsColors.of(player) : 0);
    }

    public static Component tag(List<Member> members) {
        StringBuilder builder = new StringBuilder(PREFIX);
        for (int i = 0; i < Math.min(members.size(), MAX_MEMBERS); i++) {
            Member member = members.get(i);
            if (i > 0) builder.append(';');
            builder.append(member.id()).append(',').append(member.name()).append(',')
                    .append(member.corps().map(LanternCorps::getSerializedName).orElse("-")).append(',')
                    .append(Integer.toHexString(member.color() & 0xFFFFFF));
        }
        String encoded = builder.toString();
        return Component.literal(" ").withStyle(Style.EMPTY.withInsertion(encoded));
    }

    public static Optional<List<Member>> read(Component description) {
        List<String> found = new ArrayList<>();
        description.visit((style, text) -> {
            String insertion = style.getInsertion();
            if (insertion != null && insertion.startsWith(PREFIX)) found.add(insertion.substring(PREFIX.length()));
            return Optional.empty();
        }, Style.EMPTY);
        if (found.isEmpty()) return Optional.empty();

        List<Member> members = new ArrayList<>();
        for (String entry : found.getFirst().split(";")) {
            String[] parts = entry.split(",");
            if (parts.length != 4) continue;
            try {
                members.add(new Member(UUID.fromString(parts[0]), parts[1], corps(parts[2]), Integer.parseInt(parts[3], 16)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.of(members);
    }

    private static Optional<LanternCorps> corps(String name) {
        for (LanternCorps corps : LanternCorps.values()) {
            if (corps.getSerializedName().equals(name)) return Optional.of(corps);
        }
        return Optional.empty();
    }

    private OfficialServer() {}
}

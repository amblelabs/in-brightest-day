package dev.amble.core.progression;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.OrangeHoard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RingOdds {
    private static final int PASS = 0x7DFF9A;
    private static final int FAIL = 0xFF6A5A;
    private static final int NOTE = 0xB8B8B8;

    private final List<Component> lines = new ArrayList<>();
    private boolean blocked;

    private RingOdds() {}

    public static List<Component> report(ServerPlayer player, LanternCorps corps) {
        RingOdds odds = new RingOdds();
        odds.lines.add(Component.literal(player.getScoreboardName() + ": " + corps.getSerializedName() + " ring").withStyle(ChatFormatting.BOLD).withColor(corps.color()));
        String verdict = odds.evaluate(player, corps);
        odds.lines.add(Component.literal("Likelihood: " + verdict).withStyle(ChatFormatting.BOLD).withColor(odds.blocked ? FAIL : PASS));
        return odds.lines;
    }

    private void check(boolean ok, String text) {
        if (!ok) this.blocked = true;
        this.lines.add(Component.literal((ok ? "  + " : "  - ") + text).withColor(ok ? PASS : FAIL));
    }

    private void note(String text) {
        this.lines.add(Component.literal("    " + text).withColor(NOTE));
    }

    private void common(ServerPlayer player, LanternCorps corps) {
        check(BrightestDayAttachments.getRing(player).isEmpty() && !RingBonds.bonded(player), "Not wearing or bonded to another ring");
        check(CorpsCaps.admits(player, corps), "Corps has room (" + CorpsCaps.bearers(player.level().getServer(), corps).size() + "/" + CorpsCaps.cap(corps) + " bearers)");
        Optional<Emotion> against = EmotionGates.blocker(corps);
        against.ifPresent(emotion -> check(SpectrumMeters.get(player, emotion) <= EmotionGates.BLOCK,
                name(emotion) + " not blocking (" + SpectrumMeters.get(player, emotion) + "/" + EmotionGates.BLOCK + ")"));
    }

    private void meter(ServerPlayer player, Emotion emotion, int needed) {
        int value = SpectrumMeters.get(player, emotion);
        check(value >= needed, name(emotion) + " " + value + "/" + needed);
    }

    private String evaluate(ServerPlayer player, LanternCorps corps) {
        BrightestDayConfig config = BrightestDayConfig.get();
        MinecraftServer server = player.level().getServer();
        switch (corps) {
            case GREEN -> {
                common(player, corps);
                meter(player, Emotion.WILL, config.greenRingWill);
                check(EmotionGates.willLead(player) >= EmotionGates.GREEN_MARGIN, "Will beats Fear by " + EmotionGates.willLead(player) + "/" + EmotionGates.GREEN_MARGIN);
                int slots = RingOffers.greenSlots(server);
                int bearers = RingOffers.onlineGreen(server);
                check(bearers < slots, "Green slot open (" + bearers + "/" + slots + " online bearers for " + server.getPlayerList().getPlayerCount() + " players)");
                float chance = RingOffers.greenChance(SpectrumMeters.get(player, Emotion.WILL));
                note("Rolled once a minute and when Will first reaches " + config.greenRingWill + ", 10% there rising to 95% at max Will");
                return this.blocked ? "0%" : percent(chance) + " per minute";
            }
            case RED -> {
                common(player, corps);
                meter(player, Emotion.RAGE, config.redOfferRage);
                long cooldown = RingOffers.redCooldown(player);
                check(cooldown == 0L, cooldown == 0L ? "No recent refusal" : "Refused recently, " + (cooldown / 1200L + 1) + " min left");
                float perMinute = config.redOfferDailyChance / RingOffers.MINUTES_PER_DAY;
                double perDay = 1.0 - Math.pow(1.0 - perMinute, RingOffers.MINUTES_PER_DAY);
                note("Rolled once a minute at " + percent(perMinute) + " per roll");
                return this.blocked ? "0%" : percent(perDay) + " per in-game day";
            }
            case ORANGE -> {
                check(BrightestDayAttachments.getRing(player).isEmpty() && !RingBonds.bonded(player), "Not wearing or bonded to another ring");
                Optional<Emotion> against = EmotionGates.blocker(corps);
                against.ifPresent(emotion -> check(SpectrumMeters.get(player, emotion) <= EmotionGates.BLOCK,
                        name(emotion) + " not blocking (" + SpectrumMeters.get(player, emotion) + "/" + EmotionGates.BLOCK + ")"));
                int hoard = OrangeHoard.value(player);
                check(hoard >= config.orangeHoardValue, "Carrying a hoard " + hoard + "/" + config.orangeHoardValue);
                check(!OrangeDuels.involved(server, player.getUUID()), "Not already in an orange challenge");
                float chance = Math.min(0.5F, SpectrumMeters.get(player, Emotion.AVARICE) / 2000.0F);
                if (!CorpsCaps.admits(player, corps)) {
                    long cooldown = OrangeDuels.cooldown(player);
                    long grace = OrangeDuels.grace(server);
                    check(cooldown == 0L, cooldown == 0L ? "No challenge cooldown" : "Challenge cooldown, " + (cooldown / 1200L + 1) + " min left");
                    check(grace == 0L, grace == 0L ? "Holder can be challenged" : "Holder grace, " + (grace / 1200L + 1) + " min left");
                    note("The ring is taken: accepting it starts a duel with its bearer");
                }
                note("Rolled each time diamond ore or ancient debris is mined");
                return this.blocked ? "0%" : percent(chance) + " per diamond or debris mined";
            }
            case BLUE -> {
                common(player, corps);
                Pilgrimage.State state = Pilgrimage.get(player);
                check(state.complete(), "Pilgrimage " + state.next() + "/" + Math.max(state.total(), BlueSanctuary.shrineCount()) + " shrines");
                note("Granted when blessed at the Blue Sanctuary");
                return this.blocked ? "0%" : "100% at the sanctuary";
            }
            case YELLOW -> {
                common(player, corps);
                meter(player, Emotion.FEAR, Emotion.GATE);
                note("Forged at a Spectrum Forge (netherite, gold, parallax shard)");
                return this.blocked ? "0%" : "100% when forged";
            }
            case STAR_SAPPHIRE -> {
                common(player, corps);
                meter(player, Emotion.LOVE, Emotion.GATE);
                note("Forged at a Zamaronian Crystal (diamond, gold, amethyst, Zamaron crystal)");
                return this.blocked ? "0%" : "100% when forged";
            }
            case INDIGO -> {
                common(player, corps);
                Optional<UUID> current = WorldProgress.get(server).indigoOne();
                if (current.isPresent()) {
                    boolean self = current.get().equals(player.getUUID());
                    check(self, self ? "You are the Indigo One" : "An Indigo One is already chosen");
                    return this.blocked ? "0%" : "100% (re-gifted daily while ringless)";
                }
                int online = server.getPlayerList().getPlayerCount();
                check(online >= config.indigoMinPlayers, "Enough players online " + online + "/" + config.indigoMinPlayers);
                meter(player, Emotion.COMPASSION, Emotion.GATE);
                note("The first ringless player to reach the compassion threshold is chosen");
                return this.blocked ? "0%" : "100% on reaching it first";
            }
            case WHITE -> {
                int lowest = EmotionGates.lowest(player);
                int spread = EmotionGates.spread(player);
                check(lowest >= EmotionGates.BALANCE_FLOOR, "Every emotion at " + EmotionGates.BALANCE_FLOOR + "+ (lowest " + lowest + ")");
                check(spread <= EmotionGates.BALANCE_SPREAD, "Spectrum balanced, spread " + spread + "/" + EmotionGates.BALANCE_SPREAD);
                note("Fused by dropping all seven spectrum rings together");
                return this.blocked ? "0%" : "100% when fused";
            }
            default -> {
                check(false, "No ring can be earned for this corps");
                return "0%";
            }
        }
    }

    private static String name(Emotion emotion) {
        String id = emotion.getSerializedName();
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }

    private static String percent(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100.0);
    }
}

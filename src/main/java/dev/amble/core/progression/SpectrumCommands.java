package dev.amble.core.progression;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class SpectrumCommands {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(value -> Component.literal("Unknown value: " + value));

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal(BrightestDay.MOD_ID)
                        .then(admin("meter")
                                .then(Commands.literal("get").then(player().then(emotion().executes(SpectrumCommands::meterGet))))
                                .then(Commands.literal("set").then(player().then(emotion().then(amount(0, Emotion.MAX).executes(context -> meterChange(context, false))))))
                                .then(Commands.literal("add").then(player().then(emotion().then(amount(-Emotion.MAX, Emotion.MAX).executes(context -> meterChange(context, true)))))))
                        .then(admin("rank")
                                .then(Commands.literal("get").then(player().executes(SpectrumCommands::rankGet)))
                                .then(Commands.literal("set").then(player().then(corps().then(Commands.argument("rank", IntegerArgumentType.integer(1, RingRanks.MAX_RANK))
                                        .executes(SpectrumCommands::rankSet))))))
                        .then(admin("milestone")
                                .then(Commands.literal("complete").then(player().then(corps().executes(SpectrumCommands::milestoneComplete))))
                                .then(Commands.literal("reroll").then(player().then(corps().executes(SpectrumCommands::milestoneReroll))))
                                .then(Commands.literal("list").then(player().executes(SpectrumCommands::milestoneList))))
                        .then(admin("progress")
                                .then(Commands.literal("reset").then(player().executes(SpectrumCommands::progressReset))))
                        .then(admin("ring")
                                .then(Commands.literal("give").then(player().then(corps().executes(SpectrumCommands::ringGive))))
                                .then(Commands.literal("take").then(player().executes(SpectrumCommands::ringTake))))
                        .then(admin("charge")
                                .then(Commands.literal("fill").then(player().executes(context -> chargeSet(context, -1))))
                                .then(Commands.literal("set").then(player().then(amount(0, BrightestDayComponents.MAX_POWER)
                                        .executes(context -> chargeSet(context, IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(admin("offer")
                                .then(Commands.literal("force").then(player().then(corps().executes(context -> offer(context, true)))))
                                .then(player().then(corps().executes(context -> offer(context, false)))))
                        .then(admin("meteor")
                                .then(Commands.literal("drop").executes(context -> meteorDrop(context, null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> meteorDrop(context, BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(admin("sanctuary")
                                .then(Commands.literal("locate").executes(SpectrumCommands::sanctuaryLocate))
                                .then(Commands.literal("tp").executes(SpectrumCommands::sanctuaryTeleport))
                                .then(Commands.literal("build").executes(SpectrumCommands::sanctuaryBuild)))
                        .then(admin("indigo")
                                .then(Commands.literal("set").then(player().executes(SpectrumCommands::indigoSet)))
                                .then(Commands.literal("clear").executes(SpectrumCommands::indigoClear)))
                        .then(admin("battery")
                                .then(Commands.literal("list").executes(SpectrumCommands::batteryList))
                                .then(Commands.literal("activate").executes(SpectrumCommands::batteryActivate)))
                        .then(admin("pilgrimage")
                                .then(Commands.literal("status").then(player().executes(SpectrumCommands::pilgrimageStatus)))
                                .then(Commands.literal("set").then(player().then(amount(0, 1000).executes(SpectrumCommands::pilgrimageSet))))
                                .then(Commands.literal("reset").then(player().executes(SpectrumCommands::pilgrimageReset))))
                        .then(admin("info").then(player().executes(SpectrumCommands::info)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
        return Commands.argument("player", EntityArgument.player());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> amount(int min, int max) {
        return Commands.argument("amount", IntegerArgumentType.integer(min, max));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name, String... values) {
        return Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(values, builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> emotion() {
        return word("emotion", Arrays.stream(Emotion.values()).map(Emotion::getSerializedName).toArray(String[]::new));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> corps() {
        return word("corps", Arrays.stream(LanternCorps.values()).map(LanternCorps::getSerializedName).toArray(String[]::new));
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }

    private static Emotion emotion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, "emotion");
        for (Emotion emotion : Emotion.values()) if (emotion.getSerializedName().equals(value)) return emotion;
        throw UNKNOWN.create(value);
    }

    private static LanternCorps corps(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, "corps");
        for (LanternCorps corps : LanternCorps.values()) if (corps.getSerializedName().equals(value)) return corps;
        throw UNKNOWN.create(value);
    }

    private static int reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int meterGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        Emotion emotion = emotion(context);
        int value = SpectrumMeters.get(player, emotion);
        context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " " + emotion.getSerializedName() + ": " + value + "/" + Emotion.MAX)
                .withColor(emotion.corps().color()), false);
        return value;
    }

    private static int meterChange(CommandContext<CommandSourceStack> context, boolean relative) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        Emotion emotion = emotion(context);
        int amount = IntegerArgumentType.getInteger(context, "amount");
        SpectrumMeters.set(player, emotion, relative ? SpectrumMeters.get(player, emotion) + amount : amount);
        return reply(context, Component.literal("Set " + player.getScoreboardName() + "'s " + emotion.getSerializedName() + " to " + SpectrumMeters.get(player, emotion)));
    }

    private static int rankGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        MutableComponent text = Component.literal(player.getScoreboardName() + " ranks:");
        for (Emotion emotion : Emotion.values()) {
            LanternCorps corps = emotion.corps();
            text.append(Component.literal(" " + corps.getSerializedName() + "=" + RingRanks.rank(player, corps)).withColor(corps.color()));
        }
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    private static int rankSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = corps(context);
        int rank = IntegerArgumentType.getInteger(context, "rank");
        RingRanks.setRank(player, corps, rank);
        return reply(context, Component.literal("Set " + player.getScoreboardName() + "'s " + corps.getSerializedName() + " rank to " + rank));
    }

    private static int milestoneComplete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = corps(context);
        if (!RingRanks.force(player, corps)) {
            context.getSource().sendFailure(Component.literal(player.getScoreboardName() + " has no milestone left for " + corps.getSerializedName()));
            return 0;
        }
        return reply(context, Component.literal("Completed the current " + corps.getSerializedName() + " milestone for " + player.getScoreboardName()));
    }

    private static int milestoneReroll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = corps(context);
        RingRanks.reroll(player, corps);
        return reply(context, Component.literal("Rerolled " + player.getScoreboardName() + "'s " + corps.getSerializedName() + " milestones"));
    }

    private static int milestoneList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        RingRanks.assign(player);
        RingRanks.Ranks ranks = RingRanks.get(player);
        for (LanternCorps corps : LanternCorps.values()) {
            if (!Milestones.has(corps)) continue;
            MutableComponent line = Component.literal(corps.getSerializedName() + " (rank " + ranks.rank(corps) + "):").withColor(corps.color());
            for (int tier = Milestones.FIRST_TIER; tier <= Milestones.LAST_TIER; tier++) {
                ranks.milestone(corps, tier).ifPresent(milestone -> line.append(Component.literal(" " + milestone.key() + " " + ranks.counter(milestone.key()) + "/" + milestone.goal())));
            }
            context.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int progressReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        SpectrumMeters.reset(player);
        RingRanks.reset(player);
        return reply(context, Component.literal("Reset meters and ranks for " + player.getScoreboardName()));
    }

    private static int ringGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = corps(context);
        ItemStack ring = RingOffers.forge(corps, player);
        if (BrightestDayAttachments.getRing(player).isEmpty()) BrightestDayAttachments.setRing(player, ring);
        else if (!player.addItem(ring)) player.drop(ring, false, Prediction.SERVER_ONLY);
        return reply(context, Component.literal("Gave " + player.getScoreboardName() + " a " + corps.getSerializedName() + " ring"));
    }

    private static int ringTake(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        RingBonds.release(player.level().getServer(), BrightestDayAttachments.getRing(player));
        BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
        return reply(context, Component.literal("Removed " + player.getScoreboardName() + "'s ring"));
    }

    private static int chargeSet(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        ItemStack ring = BrightestDayAttachments.getRing(player);
        LanternCorps corps = PowerRingItem.getCorps(ring).orElse(null);
        if (corps == null) {
            context.getSource().sendFailure(Component.literal(player.getScoreboardName() + " has no ring"));
            return 0;
        }
        int value = amount < 0 ? RingRanks.capacity(player, corps) : Math.min(amount, RingRanks.capacity(player, corps));
        ring.set(BrightestDayComponents.POWER_TYPE, value);
        BrightestDayAttachments.setRing(player, ring);
        return reply(context, Component.literal("Set " + player.getScoreboardName() + "'s charge to " + value));
    }

    private static int offer(CommandContext<CommandSourceStack> context, boolean force) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = corps(context);
        ItemStack worn = BrightestDayAttachments.getRing(player);
        if (!force && (!worn.isEmpty() || RingBonds.bonded(player))) {
            context.getSource().sendFailure(Component.literal(player.getScoreboardName() + " already has a bound ring (use force to replace it)"));
            return 0;
        }
        if (force) {
            if (!worn.isEmpty()) {
                BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
                RingBonds.release(player.level().getServer(), worn);
                if (!player.addItem(worn)) player.drop(worn, false, Prediction.SERVER_ONLY);
            }
            RingBonds.forget(player);
        }
        RingOffers.forceOffer(player, corps);
        return reply(context, Component.literal("Offered " + corps.getSerializedName() + " to " + player.getScoreboardName() + (force && !worn.isEmpty() ? ", replacing their ring" : "")));
    }

    private static int meteorDrop(CommandContext<CommandSourceStack> context, BlockPos pos) {
        ServerLevel level = context.getSource().getServer().overworld();
        if (ZamaronMeteor.falling()) {
            context.getSource().sendFailure(Component.literal("A meteor is already falling"));
            return 0;
        }
        BlockPos target = pos != null ? pos : BlockPos.containing(context.getSource().getPosition());
        ZamaronMeteor.drop(level, target);
        return reply(context, Component.literal("Meteor inbound at " + target.toShortString()));
    }

    private static int sanctuaryLocate(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlueSanctuary.locate(context.getSource().getServer().overworld());
        boolean built = WorldProgress.get(context.getSource().getServer()).sanctuaryBuilt();
        context.getSource().sendSuccess(() -> Component.literal("Blue sanctuary: " + pos.getX() + ", " + pos.getZ() + (built ? " (built)" : " (not yet built)"))
                .withColor(LanternCorps.BLUE.color()), false);
        return 1;
    }

    private static int sanctuaryTeleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getServer().overworld();
        BlockPos pos = BlueSanctuary.locate(level);
        BlockPos outside = pos.south(22);
        level.getChunk(outside.getX() >> 4, outside.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outside.getX(), outside.getZ());
        player.teleportTo(level, outside.getX() + 0.5, y, outside.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        return reply(context, Component.literal("Teleported to the blue sanctuary"));
    }

    private static int sanctuaryBuild(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlueSanctuary.buildNow(context.getSource().getServer().overworld());
        return reply(context, Component.literal("Built the blue sanctuary at " + pos.toShortString()));
    }

    private static int indigoSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        WorldProgress.update(context.getSource().getServer(), state -> state.withIndigoOne(player.getUUID()));
        return reply(context, Component.literal(player.getScoreboardName() + " is now Indigo-1"));
    }

    private static int indigoClear(CommandContext<CommandSourceStack> context) {
        WorldProgress.update(context.getSource().getServer(), WorldProgress::withoutIndigoOne);
        return reply(context, Component.literal("Cleared Indigo-1"));
    }

    private static int batteryList(CommandContext<CommandSourceStack> context) {
        var batteries = WorldProgress.get(context.getSource().getServer()).batteries();
        if (batteries.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No central power batteries"), false);
            return 0;
        }
        for (WorldProgress.Battery battery : batteries) {
            context.getSource().sendSuccess(() -> Component.literal(battery.corps().getSerializedName() + " @ " + battery.pos().toShortString()
                    + (battery.active() ? " (active)" : " (incomplete)")).withColor(battery.corps().color()), false);
        }
        return batteries.size();
    }

    private static int batteryActivate(CommandContext<CommandSourceStack> context) {
        WorldProgress.update(context.getSource().getServer(), state -> state.withBatteries(state.batteries().stream().map(battery -> battery.withHealth(WorldProgress.Battery.MAX_HEALTH).withLit(true).withActive(true)).toList()));
        CentralPowerBattery.broadcast(context.getSource().getServer());
        return reply(context, Component.literal("Activated all registered batteries"));
    }

    private static int pilgrimageStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        Pilgrimage.State state = Pilgrimage.get(player);
        String status = state.complete() ? "complete" : state.active() ? "walking" : "not started";
        context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " pilgrimage: " + status + " (" + state.next() + "/" + BlueSanctuary.shrineCount()
                + " shrines, " + WorldProgress.get(context.getSource().getServer()).shrines().size() + " built)").withColor(LanternCorps.BLUE.color()), false);
        return state.next();
    }

    private static int pilgrimageSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        Pilgrimage.set(player, IntegerArgumentType.getInteger(context, "amount"));
        return reply(context, Component.literal("Set " + player.getScoreboardName() + "'s pilgrimage to shrine " + Pilgrimage.get(player).next()));
    }

    private static int pilgrimageReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        Pilgrimage.reset(player);
        return reply(context, Component.literal("Reset " + player.getScoreboardName() + "'s pilgrimage"));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = target(context);
        LanternCorps corps = PowerRingItem.getCorps(BrightestDayAttachments.getRing(player)).orElse(null);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("— " + player.getScoreboardName() + " —").withStyle(ChatFormatting.BOLD), false);
        if (corps == null) {
            source.sendSuccess(() -> Component.literal("No ring"), false);
        } else {
            int charge = PowerRingItem.getRingPower(BrightestDayAttachments.getRing(player));
            String locked = RingRanks.locked(player, corps).stream().map(power -> power.id().getPath()).collect(Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("Corps: " + corps.getSerializedName() + "  Rank " + RingRanks.rank(player, corps) + "/" + RingRanks.MAX_RANK
                    + "  Charge " + charge + "/" + RingRanks.capacity(player, corps)).withColor(corps.color()), false);
            source.sendSuccess(() -> Component.literal("Locked: " + (locked.isEmpty() ? "none" : locked)).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Battery required: " + CentralPowerBattery.required(corps)
                    + (CentralPowerBattery.required(corps) ? " (active: " + CentralPowerBattery.active(source.getServer(), corps) + ")" : "")).withStyle(ChatFormatting.GRAY), false);
        }
        MutableComponent meters = Component.literal("Meters:");
        for (Emotion emotion : Emotion.values()) {
            meters.append(Component.literal(" " + emotion.getSerializedName() + "=" + SpectrumMeters.get(player, emotion)).withColor(emotion.corps().color()));
        }
        source.sendSuccess(() -> meters, false);
        source.sendSuccess(() -> Component.literal("Indigo-1: " + IndigoOne.isIndigoOne(player) + "  Blessed by hope: "
                + WorldProgress.get(source.getServer()).blessedAt(player.getUUID()).isPresent()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private SpectrumCommands() {}
}

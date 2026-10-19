package dev.amble.core.progression;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.rings.ConstructRings;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CorpsLeaders {
    private static final long VOTE_TICKS = 2400L;

    public static final AttachmentType<Map<LanternCorps, UUID>> LEADERS = AttachmentRegistry.<Map<LanternCorps, UUID>>builder()
            .initializer(Map::of)
            .persistent(Codec.unboundedMap(LanternCorps.CODEC, UUIDUtil.CODEC))
            .buildAndRegister(BrightestDay.id("corps_leaders"));

    public static final AttachmentType<List<UUID>> EXILED = AttachmentRegistry.<List<UUID>>builder()
            .initializer(List::of)
            .persistent(UUIDUtil.CODEC.listOf())
            .buildAndRegister(BrightestDay.id("corps_exiled"));

    private static final Map<LanternCorps, Vote> VOTES = new EnumMap<>(LanternCorps.class);

    private static final class Vote {
        final LanternCorps corps;
        final UUID target;
        final String targetName;
        final UUID initiator;
        final long endsAt;
        final Set<UUID> yes = new HashSet<>();
        final Set<UUID> no = new HashSet<>();

        Vote(LanternCorps corps, ServerPlayer target, ServerPlayer initiator, long endsAt) {
            this.corps = corps;
            this.target = target.getUUID();
            this.targetName = target.getScoreboardName();
            this.initiator = initiator.getUUID();
            this.endsAt = endsAt;
            this.yes.add(initiator.getUUID());
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CorpsLeaders::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VOTES.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            List<UUID> exiled = server.overworld().getAttachedOrElse(EXILED, List.of());
            if (!exiled.contains(handler.player.getUUID())) return;
            List<UUID> remaining = new ArrayList<>(exiled);
            remaining.remove(handler.player.getUUID());
            server.overworld().setAttached(EXILED, List.copyOf(remaining));
            strip(handler.player, null);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("corps")
                        .then(Commands.literal("info").executes(CorpsLeaders::info))
                        .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(CorpsLeaders::kick)))
                        .then(Commands.literal("vote").then(Commands.argument("choice", StringArgumentType.word())
                                .suggests((context, builder) -> builder.suggest("yes").suggest("no").buildFuture())
                                .executes(CorpsLeaders::vote)))));
    }

    public static Optional<UUID> leader(MinecraftServer server, LanternCorps corps) {
        return Optional.ofNullable(server.overworld().getAttachedOrElse(LEADERS, Map.of()).get(corps));
    }

    public static boolean isLeader(Player player, LanternCorps corps) {
        MinecraftServer server = player.level().getServer();
        return server != null && leader(server, corps).filter(player.getUUID()::equals).isPresent();
    }

    public static void claim(ServerPlayer player, LanternCorps corps) {
        MinecraftServer server = player.level().getServer();
        if (leader(server, corps).isPresent() || worn(player) != corps) return;
        setLeader(server, corps, player.getUUID());
        announce(server, corps, Component.translatable("message.brightestday.corps.leader", player.getDisplayName(), corps.displayName()).withStyle(ChatFormatting.BOLD));
    }

    private static void setLeader(MinecraftServer server, LanternCorps corps, @Nullable UUID leader) {
        Map<LanternCorps, UUID> leaders = new EnumMap<>(LanternCorps.class);
        leaders.putAll(server.overworld().getAttachedOrElse(LEADERS, Map.of()));
        if (leader == null) leaders.remove(corps);
        else leaders.put(corps, leader);
        server.overworld().setAttached(LEADERS, Map.copyOf(leaders));
    }

    private static @Nullable LanternCorps worn(Player player) {
        ItemStack ring = BrightestDayAttachments.getRing(player);
        if (ring.isEmpty() || ConstructRings.temporary(ring)) return null;
        return PowerRingItem.getCorps(ring).orElse(null);
    }

    private static List<ServerPlayer> members(MinecraftServer server, LanternCorps corps) {
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (worn(player) == corps) members.add(player);
        }
        return members;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LanternCorps corps = worn(player);
        if (corps == null) {
            context.getSource().sendFailure(Component.translatable("message.brightestday.corps.no_ring"));
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        Component leader = leader(server, corps).map(id -> name(server, id)).orElse(Component.translatable("message.brightestday.corps.no_leader"));
        context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.info", corps.displayName(), leader, members(server, corps).size()).withColor(corps.color()), false);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer caller = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        LanternCorps corps = worn(caller);
        MinecraftServer server = caller.level().getServer();
        if (corps == null) {
            context.getSource().sendFailure(Component.translatable("message.brightestday.corps.no_ring"));
            return 0;
        }
        if (target == caller || worn(target) != corps) {
            context.getSource().sendFailure(Component.translatable("message.brightestday.corps.not_member", target.getDisplayName(), corps.displayName()));
            return 0;
        }
        if (VOTES.containsKey(corps)) {
            context.getSource().sendFailure(Component.translatable("message.brightestday.corps.vote_running"));
            return 0;
        }
        Vote vote = new Vote(corps, target, caller, server.overworld().getGameTime() + VOTE_TICKS);
        VOTES.put(corps, vote);
        MutableComponent call = Component.translatable("message.brightestday.corps.vote_called", caller.getDisplayName(), target.getDisplayName(), corps.displayName()).withColor(corps.color());
        if (isLeader(target, corps)) call.append(Component.literal(" ")).append(Component.translatable("message.brightestday.corps.vote_unanimous").withStyle(ChatFormatting.ITALIC));
        for (ServerPlayer member : members(server, corps)) {
            member.sendSystemMessage(call);
            if (member != target) member.sendSystemMessage(ballot(corps));
        }
        if (!evaluate(server, vote, false)) target.sendSystemMessage(Component.translatable("message.brightestday.corps.vote_against_you").withStyle(ChatFormatting.ITALIC).withColor(corps.color()));
        return 1;
    }

    private static Component ballot(LanternCorps corps) {
        Component yes = Component.translatable("message.brightestday.corps.vote_yes").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/corps vote yes")));
        Component no = Component.translatable("message.brightestday.corps.vote_no").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/corps vote no")));
        return Component.empty().append(yes).append(Component.literal("  ")).append(no);
    }

    private static int vote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String choice = StringArgumentType.getString(context, "choice");
        LanternCorps corps = worn(player);
        Vote vote = corps == null ? null : VOTES.get(corps);
        if (vote == null || vote.target.equals(player.getUUID()) || !choice.equals("yes") && !choice.equals("no")) {
            context.getSource().sendFailure(Component.translatable("message.brightestday.corps.no_vote"));
            return 0;
        }
        boolean yes = choice.equals("yes");
        (yes ? vote.yes : vote.no).add(player.getUUID());
        (yes ? vote.no : vote.yes).remove(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(yes ? "message.brightestday.corps.voted_yes" : "message.brightestday.corps.voted_no").withColor(vote.corps.color()), false);
        evaluate(player.level().getServer(), vote, false);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (VOTES.isEmpty() || server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        for (Vote vote : List.copyOf(VOTES.values())) evaluate(server, vote, now >= vote.endsAt);
    }

    private static boolean evaluate(MinecraftServer server, Vote vote, boolean expired) {
        Set<UUID> voters = new HashSet<>();
        for (ServerPlayer member : members(server, vote.corps)) {
            if (!member.getUUID().equals(vote.target)) voters.add(member.getUUID());
        }
        long yes = vote.yes.stream().filter(voters::contains).count();
        long no = vote.no.stream().filter(voters::contains).count();
        boolean unanimous = leader(server, vote.corps).filter(vote.target::equals).isPresent();
        long needed = unanimous ? voters.size() : voters.size() / 2 + 1;
        if (needed > 0 && yes >= needed) {
            VOTES.remove(vote.corps);
            pass(server, vote);
            return true;
        }
        if (expired || no > voters.size() - needed || voters.isEmpty()) {
            VOTES.remove(vote.corps);
            announce(server, vote.corps, Component.translatable("message.brightestday.corps.vote_failed", vote.targetName, yes, needed));
            return true;
        }
        return false;
    }

    private static void pass(MinecraftServer server, Vote vote) {
        announce(server, vote.corps, Component.translatable("message.brightestday.corps.vote_passed", vote.targetName, vote.corps.displayName()).withStyle(ChatFormatting.BOLD));
        if (leader(server, vote.corps).filter(vote.target::equals).isPresent()) {
            ServerPlayer successor = server.getPlayerList().getPlayer(vote.initiator);
            setLeader(server, vote.corps, successor != null && worn(successor) == vote.corps ? vote.initiator : null);
            if (successor != null && worn(successor) == vote.corps) {
                announce(server, vote.corps, Component.translatable("message.brightestday.corps.leader", successor.getDisplayName(), vote.corps.displayName()).withStyle(ChatFormatting.BOLD));
            }
        }
        ServerPlayer target = server.getPlayerList().getPlayer(vote.target);
        if (target != null) {
            strip(target, vote.corps);
            return;
        }
        List<UUID> exiled = new ArrayList<>(server.overworld().getAttachedOrElse(EXILED, List.of()));
        if (!exiled.contains(vote.target)) exiled.add(vote.target);
        server.overworld().setAttached(EXILED, List.copyOf(exiled));
    }

    private static void strip(ServerPlayer player, @Nullable LanternCorps corps) {
        LanternCorps worn = worn(player);
        if (worn == null || corps != null && worn != corps) return;
        RingBonds.release(player.level().getServer(), BrightestDayAttachments.getRing(player));
        BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
        player.sendSystemMessage(Component.translatable("message.brightestday.corps.kicked", worn.displayName()).withStyle(ChatFormatting.BOLD).withColor(worn.color()));
    }

    private static void announce(MinecraftServer server, LanternCorps corps, Component message) {
        Component colored = message.copy().withColor(corps.color());
        for (ServerPlayer member : members(server, corps)) member.sendSystemMessage(colored);
    }

    private static Component name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getDisplayName();
        return server.services().nameToIdCache().get(id).map(profile -> Component.literal(profile.name())).orElse(Component.literal("?"));
    }

    private CorpsLeaders() {}
}

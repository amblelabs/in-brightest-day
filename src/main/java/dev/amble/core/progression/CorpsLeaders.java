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
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.Container;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CorpsLeaders {
    private static final long VOTE_TICKS = 2400L;
    private static final String LEADER_MARK = "\u2605 ";

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
            removeRings(handler.player, "message.brightestday.corps.rings_removed", null);
        });
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
        UUID previous = server.overworld().getAttachedOrElse(LEADERS, Map.of()).get(corps);
        server.overworld().setAttached(LEADERS, Map.copyOf(leaders));
        if (previous != null) refreshTab(server, previous);
        if (leader != null) refreshTab(server, leader);
    }

    public static @Nullable LanternCorps worn(Player player) {
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

    public static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    public static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
                .withClickEvent(new ClickEvent.RunCommand("/" + BrightestDay.MOD_ID + " vote yes")));
        Component no = Component.translatable("message.brightestday.corps.vote_no").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/" + BrightestDay.MOD_ID + " vote no")));
        return Component.empty().append(yes).append(Component.literal("  ")).append(no);
    }

    public static int vote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
        if (target != null) removeRings(target, "message.brightestday.corps.kicked", vote.corps);
        else exile(server, vote.target);
    }

    public static int indigo(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        Optional<UUID> indigo = WorldProgress.get(server).indigoOne();
        Component message = indigo.map(id -> Component.translatable("message.brightestday.corps.indigo_one", name(server, id)))
                .orElse(Component.translatable("message.brightestday.corps.no_indigo_one"));
        context.getSource().sendSuccess(() -> message.copy().withColor(LanternCorps.INDIGO.color()), false);
        return indigo.isPresent() ? 1 : 0;
    }

    public static int showLeader(CommandContext<CommandSourceStack> context, LanternCorps corps) {
        MinecraftServer server = context.getSource().getServer();
        Component leader = leader(server, corps).map(id -> name(server, id)).orElse(Component.translatable("message.brightestday.corps.no_leader"));
        context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.leader_is", corps.displayName(), leader).withColor(corps.color()), false);
        return 1;
    }

    public static int assignLeader(CommandContext<CommandSourceStack> context, LanternCorps corps, Collection<NameAndId> profiles) {
        MinecraftServer server = context.getSource().getServer();
        NameAndId profile = profiles.iterator().next();
        setLeader(server, corps, profile.id());
        announce(server, corps, Component.translatable("message.brightestday.corps.leader", Component.literal(profile.name()), corps.displayName()).withStyle(ChatFormatting.BOLD));
        context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.leader_set", profile.name(), corps.displayName()).withColor(corps.color()), true);
        return 1;
    }

    public static int clearLeader(CommandContext<CommandSourceStack> context, LanternCorps corps) {
        setLeader(context.getSource().getServer(), corps, null);
        context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.leader_cleared", corps.displayName()).withColor(corps.color()), true);
        return 1;
    }

    public static int forceKick(CommandContext<CommandSourceStack> context, Collection<NameAndId> profiles) {
        MinecraftServer server = context.getSource().getServer();
        for (NameAndId profile : profiles) {
            dropLeadership(server, profile.id());
            ServerPlayer online = server.getPlayerList().getPlayer(profile.id());
            if (online != null) removeRings(online, "message.brightestday.corps.kicked_by_admin", null);
            else exile(server, profile.id());
            context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.kick_done", profile.name()), true);
        }
        return profiles.size();
    }

    public static int relinquish(CommandContext<CommandSourceStack> context, Collection<NameAndId> profiles) {
        MinecraftServer server = context.getSource().getServer();
        for (NameAndId profile : profiles) {
            dropLeadership(server, profile.id());
            ServerPlayer online = server.getPlayerList().getPlayer(profile.id());
            if (online != null) removeRings(online, "message.brightestday.corps.rings_removed", null);
            else exile(server, profile.id());
            Component state = Component.translatable(online != null ? "message.brightestday.corps.relinquished_now" : "message.brightestday.corps.relinquished_later", profile.name());
            context.getSource().sendSuccess(() -> state, true);
        }
        return profiles.size();
    }

    public static int disband(CommandContext<CommandSourceStack> context, LanternCorps corps) {
        MinecraftServer server = context.getSource().getServer();
        Set<UUID> bearers = new HashSet<>(RingBonds.bearers(server, corps));
        for (ServerPlayer player : members(server, corps)) bearers.add(player.getUUID());
        VOTES.remove(corps);
        setLeader(server, corps, null);
        announce(server, corps, Component.translatable("message.brightestday.corps.disbanded", corps.displayName()).withStyle(ChatFormatting.BOLD));
        for (UUID bearer : bearers) {
            ServerPlayer online = server.getPlayerList().getPlayer(bearer);
            if (online != null) removeRings(online, "message.brightestday.corps.rings_removed", null);
            else exile(server, bearer);
        }
        context.getSource().sendSuccess(() -> Component.translatable("message.brightestday.corps.disband_done", corps.displayName(), bearers.size()).withColor(corps.color()), true);
        return bearers.size();
    }

    public static @Nullable Component tabName(ServerPlayer player) {
        LanternCorps corps = worn(player);
        if (corps == null || !isLeader(player, corps)) return null;
        return Component.literal(LEADER_MARK).withColor(corps.color()).append(player.getDisplayName());
    }

    private static void refreshTab(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    private static void dropLeadership(MinecraftServer server, UUID id) {
        for (Map.Entry<LanternCorps, UUID> entry : Map.copyOf(server.overworld().getAttachedOrElse(LEADERS, Map.of())).entrySet()) {
            if (entry.getValue().equals(id)) setLeader(server, entry.getKey(), null);
        }
    }

    private static void exile(MinecraftServer server, UUID id) {
        RingBonds.forgetOwner(server, id);
        List<UUID> exiled = new ArrayList<>(server.overworld().getAttachedOrElse(EXILED, List.of()));
        if (!exiled.contains(id)) exiled.add(id);
        server.overworld().setAttached(EXILED, List.copyOf(exiled));
    }

    private static void removeRings(ServerPlayer player, String messageKey, @Nullable LanternCorps corps) {
        MinecraftServer server = player.level().getServer();
        ItemStack worn = BrightestDayAttachments.getRing(player);
        LanternCorps shown = corps != null ? corps : PowerRingItem.getCorps(worn).orElse(null);
        boolean removed = false;
        if (!worn.isEmpty()) {
            RingBonds.release(server, worn);
            BrightestDayAttachments.setRing(player, ItemStack.EMPTY);
            removed = true;
        }
        for (Container container : List.<Container>of(player.getInventory(), player.getEnderChestInventory())) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).getItem() instanceof PowerRingItem) {
                    container.setItem(slot, ItemStack.EMPTY);
                    removed = true;
                }
            }
        }
        if (player.containerMenu.getCarried().getItem() instanceof PowerRingItem) player.containerMenu.setCarried(ItemStack.EMPTY);
        RingBonds.forget(player);
        dropLeadership(server, player.getUUID());
        if (!removed) return;
        Component corpsName = shown == null ? Component.empty() : shown.displayName();
        player.sendSystemMessage(Component.translatable(messageKey, corpsName).withStyle(ChatFormatting.BOLD).withColor(shown == null ? 0xFFFFFF : shown.color()));
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

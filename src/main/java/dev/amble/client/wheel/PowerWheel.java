package dev.amble.client.wheel;

import dev.amble.BrightestDay;
import dev.amble.client.BrightestDayKeybinds;
import dev.amble.client.effects.ConstructClient;
import dev.amble.client.effects.SculptClient;
import dev.amble.core.drill.DrillMode;
import dev.amble.core.drill.DrillModes;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.DrillModeC2SPayload;
import dev.amble.core.networking.payloads.c2s.SelectAbilityC2SPayload;
import dev.amble.core.networking.payloads.c2s.SelectConstructC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.sculpt.SculptShape;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class PowerWheel {
    private static final double ROOT_RADIUS = 58.0;
    private static final double SUB_RADIUS = 42.0;
    private static final double DEAD_ZONE = 10.0;
    private static final double SUB_DEAD_ZONE = 9.0;
    private static final double SUBMENU_TRIGGER = ROOT_RADIUS + 6.0;
    private static final double SUBMENU_CLOSE = ROOT_RADIUS - 22.0;
    private static final double ROOT_REACH = ROOT_RADIUS + 16.0;
    private static final double SUB_REACH = SUB_RADIUS + 14.0;
    private static final int NODE_RADIUS = 11;
    private static final int NODE_GROWTH = 4;
    private static final int HUB_RADIUS = 19;
    private static final int OPEN_TICKS = 3;
    private static final float HOVER_SPEED = 0.55F;
    private static final double MOUSE_SCALE = 0.55;
    private static final int TAP_TICKS = 5;
    private static final double TAP_DISTANCE = 3.0;
    private static final int POINTER_DOTS = 7;
    private static final double POINTER_SPREAD = 7.0;
    private static final int TRAIL_SPACING = 5;
    public static final int HUD_MARGIN = 4;
    private static int indicatorTop = -1;
    private static final int HUD_PADDING = 4;

    private static final Map<RingPower<?>, Item> ICONS = Map.ofEntries(
            Map.entry(RingPowerRegistry.BLAST, Items.FIRE_CHARGE),
            Map.entry(RingPowerRegistry.BEAM, Items.END_ROD),
            Map.entry(RingPowerRegistry.HEAL_BEAM, Items.GLISTERING_MELON_SLICE),
            Map.entry(RingPowerRegistry.ENTITY_SHIELD, Items.HEART_OF_THE_SEA),
            Map.entry(RingPowerRegistry.AREA_SHIELD, Items.TURTLE_HELMET),
            Map.entry(RingPowerRegistry.WALL, Items.STONE_BRICKS),
            Map.entry(RingPowerRegistry.SCULPT, Items.AMETHYST_SHARD),
            Map.entry(RingPowerRegistry.LIGHT_ORB, Items.GLOWSTONE),
            Map.entry(RingPowerRegistry.DRILL, Items.DIAMOND_PICKAXE),
            Map.entry(RingPowerRegistry.GLIDER, Items.ELYTRA),
            Map.entry(RingPowerRegistry.GRAPPLING_HOOK, Items.TRIPWIRE_HOOK),
            Map.entry(RingPowerRegistry.LUMBERJACK, Items.IRON_AXE),
            Map.entry(RingPowerRegistry.BLOOD_HUNT, Items.REDSTONE),
            Map.entry(RingPowerRegistry.MEGAPHONE, Items.GOAT_HORN),
            Map.entry(RingPowerRegistry.CONSTRUCT_HORSE, Items.SADDLE),
            Map.entry(RingPowerRegistry.CONSTRUCT_BOAT, Items.BIRCH_BOAT),
            Map.entry(RingPowerRegistry.CONTAINMENT_SPHERE, Items.ENDER_PEARL),
            Map.entry(RingPowerRegistry.RING_COMPASS, Items.COMPASS),
            Map.entry(RingPowerRegistry.INSIGNIA, Items.GLOW_ITEM_FRAME),
            Map.entry(RingPowerRegistry.ORE_PROBE, Items.SPYGLASS),
            Map.entry(RingPowerRegistry.SWARM_MISSILES, Items.FIREWORK_ROCKET),
            Map.entry(RingPowerRegistry.PIERCING_LANCE, Items.SPECTRAL_ARROW),
            Map.entry(RingPowerRegistry.CHAIN_BOLT, Items.TRIDENT),
            Map.entry(RingPowerRegistry.BOOMERANG_DISC, Items.MUSIC_DISC_PIGSTEP),
            Map.entry(RingPowerRegistry.NOVA_BURST, Items.FIREWORK_STAR),
            Map.entry(RingPowerRegistry.GROUND_SLAM, Items.MACE),
            Map.entry(RingPowerRegistry.RAPID_BARRAGE, Items.PRISMARINE_SHARD),
            Map.entry(RingPowerRegistry.GIANT_FIST, Items.HEAVY_CORE),
            Map.entry(RingPowerRegistry.ENERGY_WHIP, Items.BREEZE_ROD),
            Map.entry(RingPowerRegistry.SENTRY_TURRET, Items.DISPENSER),
            Map.entry(RingPowerRegistry.TOOL_FORGE, Items.ANVIL),
            Map.entry(RingPowerRegistry.PLASMA_BURST, Items.MAGMA_CREAM),
            Map.entry(RingPowerRegistry.CRYSTAL_PRISON, Items.AMETHYST_CLUSTER),
            Map.entry(RingPowerRegistry.TRACTOR_BEAM, Items.LEAD),
            Map.entry(RingPowerRegistry.SCAN, Items.SPYGLASS),
            Map.entry(RingPowerRegistry.CONVERSION, Items.ECHO_SHARD),
            Map.entry(RingPowerRegistry.GATHER, Items.RECOVERY_COMPASS),
            Map.entry(RingPowerRegistry.BERSERK, Items.BLAZE_POWDER),
            Map.entry(RingPowerRegistry.COMMS, Items.BELL),
            Map.entry(RingPowerRegistry.CONCUSSIVE, Items.WIND_CHARGE),
            Map.entry(RingPowerRegistry.ACID, Items.MAGMA_CREAM)
    );
    private static final Map<SculptShape, Item> SHAPE_ICONS = Map.of(
            SculptShape.FREEFORM, Items.FEATHER,
            SculptShape.STAIRS, Items.OAK_STAIRS,
            SculptShape.TUBE, Items.HOPPER,
            SculptShape.CAGE, Items.IRON_BARS
    );
    private static final List<Group> GROUPS = List.of(
            new Group("construct_group.brightestday.attacks", Items.BLAZE_ROD, List.of("blast", "swarm_missiles", "piercing_lance", "chain_bolt", "boomerang_disc", "rapid_barrage", "nova_burst", "ground_slam", "plasma_burst", "crystal_prison")),
            new Group("construct_group.brightestday.weapons", Items.IRON_SWORD, List.of("giant_fist", "energy_whip", "sentry_turret")),
            new Group("construct_group.brightestday.utility", Items.COMPASS, List.of("glider", "grappling_hook", "light_orb", "lumberjack", "ore_probe", "megaphone", "construct_horse", "construct_boat", "ring_compass")),
            new Group("construct_group.brightestday.visuals", Items.GLOW_INK_SAC, List.of("insignia")),
            new Group("construct_group.brightestday.shield", Items.SHIELD, List.of("entity_shield", "area_shield", "containment_sphere"))
    );
    private static final Map<DrillMode, Item> DRILL_MODE_ICONS = Map.of(
            DrillMode.HOLD, Items.DIAMOND_PICKAXE,
            DrillMode.TUNNEL, Items.DIAMOND_SHOVEL
    );
    private static final Item FALLBACK_ICON = Items.NETHER_STAR;
    private static final Map<Item, ItemStack> STACKS = new HashMap<>();

    private static final PowerWheel CONSTRUCTS = new PowerWheel("construct_wheel", BrightestDayKeybinds.CYCLE_CONSTRUCT,
            PowerWheel::constructEntries, player -> ArmedRingPower.selectedConstruct(player).map(construct -> construct), PowerWheel::tapConstructs);
    private static final PowerWheel ABILITIES = new PowerWheel("ability_wheel", BrightestDayKeybinds.ABILITY_WHEEL,
            PowerWheel::abilityEntries, ArmedRingPower::selectedAbility, PowerWheel::tapAbilities);
    private static final List<PowerWheel> WHEELS = List.of(CONSTRUCTS, ABILITIES);

    private record Sub(Component name, Item icon, Runnable apply, BooleanSupplier current) {}

    private record Entry(Component name, Item icon, Set<RingPower<?>> powers, Runnable apply, List<Sub> subs) {
        Optional<Sub> currentSub() {
            return this.subs.stream().filter(sub -> sub.current().getAsBoolean()).findFirst();
        }
    }

    private final String id;
    private final KeyMapping key;
    private final Function<Player, List<Entry>> builder;
    private final Function<Player, Optional<RingPower<?>>> selection;
    private final Consumer<LocalPlayer> onTap;

    private List<Entry> entries = List.of();
    private boolean open;
    private int heldTicks;
    private double cursorX;
    private double cursorY;
    private double travelled;
    private int hoveredEntry = -1;
    private int hoveredSub = -1;
    private int submenu = -1;
    private boolean backHovered;
    private float[] rootHover = new float[0];
    private float[] rootHoverO = new float[0];
    private float[] subHover = new float[0];
    private float[] subHoverO = new float[0];

    private PowerWheel(String id, KeyMapping key, Function<Player, List<Entry>> builder,
                       Function<Player, Optional<RingPower<?>>> selection, Consumer<LocalPlayer> onTap) {
        this.id = id;
        this.key = key;
        this.builder = builder;
        this.selection = selection;
        this.onTap = onTap;
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> DrillModes.setClient(DrillMode.HOLD));
        ClientTickEvents.END_CLIENT_TICK.register(client -> WHEELS.forEach(wheel -> wheel.tick(client)));
        for (PowerWheel wheel : WHEELS) {
            HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id(wheel.id), wheel::extractWheel);
        }
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id("power_indicator"), PowerWheel::extractIndicator);
    }

    public static boolean isOpen() {
        return WHEELS.stream().anyMatch(wheel -> wheel.open);
    }

    public static boolean onMouse(double dx, double dy) {
        for (PowerWheel wheel : WHEELS) {
            if (wheel.open) {
                wheel.steer(dx, dy);
                return true;
            }
        }
        return false;
    }

    private void steer(double dx, double dy) {
        double scale = MOUSE_SCALE / Minecraft.getInstance().getWindow().getGuiScale();
        this.cursorX += dx * scale;
        this.cursorY += dy * scale;
        this.travelled += Math.hypot(dx * scale, dy * scale);
        this.clampCursor();
        this.updateHover();
    }

    private void clampCursor() {
        double centerX = this.submenu >= 0 ? nodeX(this.submenu, this.entries.size()) : 0.0;
        double centerY = this.submenu >= 0 ? nodeY(this.submenu, this.entries.size()) : 0.0;
        double reach = this.submenu >= 0 ? SUB_REACH : ROOT_REACH;
        double localX = this.cursorX - centerX;
        double localY = this.cursorY - centerY;
        double length = Math.hypot(localX, localY);
        if (length <= reach) return;
        this.cursorX = centerX + localX * reach / length;
        this.cursorY = centerY + localY * reach / length;
    }

    private boolean isCurrent(Player player, Entry entry) {
        return this.selection.apply(player).map(entry.powers()::contains).orElse(false);
    }

    private void tick(Minecraft client) {
        LocalPlayer player = client.player;
        this.entries = player == null ? List.of() : this.builder.apply(player);
        this.validate();

        boolean pressed = false;
        while (this.key.consumeClick()) pressed = true;
        if (client.gui.screen() != null) pressed = false;
        boolean down = player != null && client.gui.screen() == null && this.key.isDown();

        if (!this.open) {
            if (!pressed && !down) return;
            if (player == null) return;
            if (this.entries.isEmpty() || !down) {
                this.onTap.accept(player);
                return;
            }
            if (WHEELS.stream().anyMatch(wheel -> wheel != this && wheel.open)) return;
            this.openWheel();
            return;
        }

        if (down && !this.entries.isEmpty()) {
            this.heldTicks++;
            this.animateHover();
            return;
        }
        this.open = false;
        if (this.heldTicks <= TAP_TICKS && this.travelled < TAP_DISTANCE) {
            if (player != null) this.onTap.accept(player);
            return;
        }
        if (this.hoveredEntry < 0 || this.hoveredEntry >= this.entries.size()) return;

        Entry entry = this.entries.get(this.hoveredEntry);
        if (this.submenu == this.hoveredEntry && this.hoveredSub >= 0 && this.hoveredSub < entry.subs().size()) {
            entry.subs().get(this.hoveredSub).apply().run();
        } else if (!this.backHovered) {
            entry.apply().run();
        }
    }

    private void openWheel() {
        this.open = true;
        this.heldTicks = 0;
        this.travelled = 0.0;
        this.cursorX = 0.0;
        this.cursorY = 0.0;
        this.hoveredEntry = -1;
        this.hoveredSub = -1;
        this.submenu = -1;
        this.backHovered = false;
        this.rootHover = new float[this.entries.size()];
        this.rootHoverO = new float[this.entries.size()];
        this.subHover = new float[0];
        this.subHoverO = new float[0];
    }

    private void validate() {
        int count = this.entries.size();
        if (this.submenu >= count || this.submenu >= 0 && this.entries.get(this.submenu).subs().size() != this.subHover.length) {
            this.submenu = -1;
            this.hoveredSub = -1;
            this.backHovered = false;
            this.subHover = new float[0];
            this.subHoverO = new float[0];
        }
        if (this.hoveredEntry >= count) this.hoveredEntry = -1;
    }

    private void updateHover() {
        this.validate();
        int count = this.entries.size();
        if (count == 0) return;
        int previousEntry = this.hoveredEntry;
        int previousSub = this.hoveredSub;
        int previousMenu = this.submenu;

        if (this.submenu >= 0 && Math.hypot(this.cursorX, this.cursorY) < SUBMENU_CLOSE) {
            this.submenu = -1;
            this.hoveredSub = -1;
            this.backHovered = false;
        }

        if (this.submenu >= 0) {
            Entry entry = this.entries.get(this.submenu);
            double localX = this.cursorX - nodeX(this.submenu, count);
            double localY = this.cursorY - nodeY(this.submenu, count);
            this.hoveredEntry = this.submenu;
            this.hoveredSub = -1;
            this.backHovered = false;
            if (Math.hypot(localX, localY) >= SUB_DEAD_ZONE) {
                double angle = Math.toDegrees(Math.atan2(localY, localX));
                double back = backAngle(this.submenu, count);
                double step = 360.0 / (entry.subs().size() + 1);
                int slot = Math.floorMod((int) Math.round(Mth.wrapDegrees(angle - back) / step), entry.subs().size() + 1);
                if (slot == 0) this.backHovered = true;
                else this.hoveredSub = slot - 1;
            }
        } else {
            double radius = Math.hypot(this.cursorX, this.cursorY);
            this.hoveredEntry = radius < DEAD_ZONE ? -1 : rootSlot(Math.toDegrees(Math.atan2(this.cursorY, this.cursorX)), count);
            if (this.hoveredEntry >= 0 && radius >= SUBMENU_TRIGGER && !this.entries.get(this.hoveredEntry).subs().isEmpty()) {
                this.openSubmenu(this.hoveredEntry);
            }
        }

        if (this.submenu != previousMenu) {
            feedback(this.submenu >= 0 ? 1.4F : 1.0F, 0.18F);
        } else if (this.hoveredEntry != previousEntry || this.hoveredSub != previousSub) {
            if (this.hoveredEntry >= 0 || this.hoveredSub >= 0) feedback(1.7F + 0.05F * Math.max(this.hoveredSub, 0), 0.08F);
        }
    }

    private void openSubmenu(int index) {
        this.submenu = index;
        int subs = this.entries.get(index).subs().size();
        this.subHover = new float[subs];
        this.subHoverO = new float[subs];
        this.hoveredSub = -1;
        this.backHovered = false;
        this.clampCursor();
    }

    private void animateHover() {
        if (this.rootHover.length != this.entries.size()) {
            this.rootHover = new float[this.entries.size()];
            this.rootHoverO = new float[this.entries.size()];
        }
        for (int i = 0; i < this.rootHover.length; i++) {
            this.rootHoverO[i] = this.rootHover[i];
            float target = i == this.hoveredEntry ? 1.0F : 0.0F;
            this.rootHover[i] += (target - this.rootHover[i]) * HOVER_SPEED;
        }
        for (int j = 0; j < this.subHover.length; j++) {
            this.subHoverO[j] = this.subHover[j];
            float target = j == this.hoveredSub ? 1.0F : 0.0F;
            this.subHover[j] += (target - this.subHover[j]) * HOVER_SPEED;
        }
    }

    private static void feedback(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    private static double rootAngle(int index, int count) {
        return -90.0 + index * 360.0 / count;
    }

    private static int rootSlot(double angle, int count) {
        double step = 360.0 / count;
        return Math.floorMod((int) Math.round(Mth.wrapDegrees(angle + 90.0) / step), count);
    }

    private static double nodeX(int index, int count) {
        return Math.cos(Math.toRadians(rootAngle(index, count))) * ROOT_RADIUS;
    }

    private static double nodeY(int index, int count) {
        return Math.sin(Math.toRadians(rootAngle(index, count))) * ROOT_RADIUS;
    }

    private static double backAngle(int index, int count) {
        return rootAngle(index, count) + 180.0;
    }

    private static double subAngle(int parent, int count, int sub, int subs) {
        return backAngle(parent, count) + (sub + 1) * 360.0 / (subs + 1);
    }

    private record Group(String key, Item icon, List<String> members) {
        int indexOf(ConstructRingPower construct) {
            return this.members.indexOf(construct.id().getPath());
        }
    }

    private static List<Entry> constructEntries(Player player) {
        List<ConstructRingPower> owned = ArmedRingPower.constructs(player);
        List<Entry> built = new ArrayList<>();
        Map<Group, Integer> slots = new HashMap<>();
        Map<Group, List<ConstructRingPower>> members = new HashMap<>();

        for (ConstructRingPower construct : owned) {
            Group group = GROUPS.stream().filter(candidate -> candidate.indexOf(construct) >= 0).findFirst().orElse(null);
            if (group != null) {
                if (!slots.containsKey(group)) {
                    slots.put(group, built.size());
                    built.add(null);
                }
                members.computeIfAbsent(group, key -> new ArrayList<>()).add(construct);
            } else if (construct == RingPowerRegistry.DRILL) {
                List<Sub> modes = new ArrayList<>();
                for (DrillMode mode : DrillMode.values()) {
                    modes.add(new Sub(Component.translatable(mode.translationKey()), DRILL_MODE_ICONS.get(mode), () -> {
                        setDrillMode(mode);
                        selectConstruct(player, construct);
                    }, () -> DrillModes.client() == mode));
                }
                built.add(new Entry(Component.translatable(construct.getTranslationKey()), icon(construct), Set.of(construct),
                        () -> selectConstruct(player, construct), modes));
            } else if (construct == RingPowerRegistry.SCULPT) {
                List<Sub> shapes = new ArrayList<>();
                for (SculptShape shape : SculptShape.values()) {
                    shapes.add(new Sub(Component.translatable(shape.translationKey()), SHAPE_ICONS.get(shape), () -> {
                        SculptClient.setShape(shape);
                        selectConstruct(player, construct);
                    }, () -> SculptClient.shape() == shape));
                }
                built.add(new Entry(Component.translatable(construct.getTranslationKey()), icon(construct), Set.of(construct),
                        () -> selectConstruct(player, construct), shapes));
            } else {
                built.add(new Entry(Component.translatable(construct.getTranslationKey()), icon(construct), Set.of(construct),
                        () -> selectConstruct(player, construct), List.of()));
            }
        }

        slots.forEach((group, slot) -> built.set(slot, groupEntry(player, group, members.get(group))));
        return built;
    }

    private static Entry groupEntry(Player player, Group group, List<ConstructRingPower> constructs) {
        List<ConstructRingPower> ordered = new ArrayList<>(constructs);
        ordered.sort(Comparator.comparingInt(group::indexOf));
        List<Sub> subs = new ArrayList<>();
        for (ConstructRingPower construct : ordered) {
            subs.add(new Sub(Component.translatable(construct.getTranslationKey()), icon(construct),
                    () -> selectConstruct(player, construct), () -> ArmedRingPower.selectedConstruct(player).orElse(null) == construct));
        }
        Set<RingPower<?>> powers = Set.copyOf(ordered);
        Sub first = subs.getFirst();
        return new Entry(Component.translatable(group.key()), group.icon(), powers, () -> {
            Optional<ConstructRingPower> current = ArmedRingPower.selectedConstruct(player);
            if (current.isPresent() && powers.contains(current.get())) {
                selectConstruct(player, current.get());
            } else {
                first.apply().run();
            }
        }, List.copyOf(subs));
    }

    private static List<Entry> abilityEntries(Player player) {
        List<Entry> built = new ArrayList<>();
        for (RingPower<?> ability : ArmedRingPower.abilities(player)) {
            built.add(new Entry(Component.translatable(ability.getTranslationKey()), icon(ability), Set.of(ability),
                    () -> selectAbility(player, ability), List.of()));
        }
        return built;
    }

    private static void setDrillMode(DrillMode mode) {
        if (DrillModes.client() == mode) return;
        DrillModes.setClient(mode);
        ClientPlayNetworking.send(new DrillModeC2SPayload(mode.ordinal()));
    }

    private static void selectConstruct(Player player, ConstructRingPower construct) {
        boolean selected = !ArmedRingPower.isAbilityMode(player) && ArmedRingPower.selectedConstruct(player).orElse(null) == construct;
        if (!selected) ClientPlayNetworking.send(new SelectConstructC2SPayload(construct.id()));
    }

    private static void selectAbility(Player player, RingPower<?> ability) {
        boolean selected = ArmedRingPower.activeAbility(player).orElse(null) == ability;
        if (!selected) ClientPlayNetworking.send(new SelectAbilityC2SPayload(ability.id()));
    }

    private static void tapConstructs(LocalPlayer player) {
        Optional<ConstructRingPower> current = ArmedRingPower.selectedConstruct(player);
        if (ArmedRingPower.isAbilityMode(player) && current.isPresent()) {
            selectConstruct(player, current.get());
        } else {
            ConstructClient.cycle();
        }
    }

    private static void tapAbilities(LocalPlayer player) {
        List<RingPower<?>> abilities = ArmedRingPower.abilities(player);
        Optional<RingPower<?>> current = ArmedRingPower.selectedAbility(player);
        if (abilities.isEmpty() || current.isEmpty()) return;
        if (!ArmedRingPower.isAbilityMode(player)) {
            selectAbility(player, current.get());
            return;
        }
        selectAbility(player, abilities.get((abilities.indexOf(current.get()) + 1) % abilities.size()));
    }

    private static ItemStack stack(Item item) {
        return STACKS.computeIfAbsent(item, ItemStack::new);
    }

    private static Item icon(RingPower<?> power) {
        return ICONS.getOrDefault(power, FALLBACK_ICON);
    }


    private static int darken(int color, float factor) {
        return ARGB.color(255, Math.round(ARGB.red(color) * factor), Math.round(ARGB.green(color) * factor), Math.round(ARGB.blue(color) * factor));
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private void extractWheel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (!this.open || player == null || this.entries.isEmpty()) return;

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        float opening = ease(Mth.clamp((this.heldTicks + partialTicks) / OPEN_TICKS, 0.0F, 1.0F));
        int color = ARGB.opaque(CorpsColors.of(player));
        int count = this.entries.size();
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(0.6F + 0.4F * opening, 0.6F + 0.4F * opening);

        float rootFade = this.submenu >= 0 ? 0.45F : 1.0F;
        disc(graphics, 0, 0, (int) ROOT_RADIUS + NODE_RADIUS + 10, ARGB.color(Math.round(0x58 * opening), darken(color, 0.12F)));
        ring(graphics, 0, 0, (int) ROOT_RADIUS, 1, ARGB.color(Math.round(0x50 * rootFade * opening), color));

        for (int i = 0; i < count; i++) {
            Entry entry = this.entries.get(i);
            float hover = i < this.rootHover.length ? Mth.lerp(partialTicks, this.rootHoverO[i], this.rootHover[i]) : 0.0F;
            boolean parent = i == this.submenu;
            this.node(graphics, entry.icon(), nodeX(i, count), nodeY(i, count), parent ? 1.0F : hover, parent ? 1.0F : rootFade,
                    this.isCurrent(player, entry), !entry.subs().isEmpty(), rootAngle(i, count), color, opening);
        }

        if (this.submenu >= 0) {
            this.extractSubmenu(graphics, this.entries.get(this.submenu), partialTicks, color, opening);
        } else {
            this.pointer(graphics, 0.0, 0.0, ROOT_RADIUS, DEAD_ZONE, color, opening);
        }

        this.hub(graphics, player, color, opening);
        graphics.pose().popMatrix();

        Component label = this.label();
        if (label != null) {
            int labelY = centerY + (int) ((ROOT_RADIUS + NODE_RADIUS + 16) * (0.6F + 0.4F * opening));
            if (this.submenu >= 0 && nodeY(this.submenu, count) > 0) labelY = centerY - (int) (ROOT_RADIUS + NODE_RADIUS + 26);
            graphics.centeredText(client.font, label, centerX, labelY, ARGB.color(Math.round(255 * opening), 0xFFFFFF));
        }
    }

    private void extractSubmenu(GuiGraphicsExtractor graphics, Entry entry, float partialTicks, int color, float opening) {
        int count = this.entries.size();
        double originX = nodeX(this.submenu, count);
        double originY = nodeY(this.submenu, count);
        int subs = entry.subs().size();

        trail(graphics, 0.0, 0.0, originX, originY, ARGB.color(Math.round(0x90 * opening), color));
        disc(graphics, (int) Math.round(originX), (int) Math.round(originY), (int) SUB_RADIUS + NODE_RADIUS + 8, ARGB.color(Math.round(0x60 * opening), darken(color, 0.1F)));
        ring(graphics, (int) Math.round(originX), (int) Math.round(originY), (int) SUB_RADIUS, 1, ARGB.color(Math.round(0x50 * opening), color));

        double back = Math.toRadians(backAngle(this.submenu, count));
        int backX = (int) Math.round(originX + Math.cos(back) * SUB_RADIUS * 0.55);
        int backY = (int) Math.round(originY + Math.sin(back) * SUB_RADIUS * 0.55);
        int backColor = this.backHovered ? ARGB.color(0xE0, 0xFFFFFF) : ARGB.color(0x70, color);
        disc(graphics, backX, backY, this.backHovered ? 4 : 3, backColor);

        for (int j = 0; j < subs; j++) {
            Sub sub = entry.subs().get(j);
            double angle = subAngle(this.submenu, count, j, subs);
            float hover = j < this.subHover.length ? Mth.lerp(partialTicks, this.subHoverO[j], this.subHover[j]) : 0.0F;
            this.node(graphics, sub.icon(), originX + Math.cos(Math.toRadians(angle)) * SUB_RADIUS, originY + Math.sin(Math.toRadians(angle)) * SUB_RADIUS,
                    hover, 1.0F, sub.current().getAsBoolean(), false, angle, color, opening);
        }

        this.pointer(graphics, originX, originY, SUB_RADIUS, SUB_DEAD_ZONE, color, opening);
    }

    private void node(GuiGraphicsExtractor graphics, Item icon, double x, double y, float hover, float fade, boolean current, boolean hasSubs,
                      double angle, int color, float opening) {
        int cx = (int) Math.round(x);
        int cy = (int) Math.round(y);
        int radius = NODE_RADIUS + Math.round(NODE_GROWTH * hover);
        int fill = ARGB.srgbLerp(hover, darken(color, 0.28F), color);
        disc(graphics, cx, cy, radius, ARGB.color(Math.round((0xA8 + 0x40 * hover) * fade * opening), fill));
        ring(graphics, cx, cy, radius, 1, ARGB.color(Math.round((0x60 + 0x9F * hover) * fade * opening), ARGB.srgbLerp(hover, color, 0xFFFFFFFF)));
        if (current) ring(graphics, cx, cy, radius + 3, 1, ARGB.color(Math.round(0xD0 * fade * opening), color));

        if (hasSubs) {
            double outward = Math.toRadians(angle);
            for (int k = -1; k <= 1; k++) {
                double dotAngle = outward + k * 0.32;
                int dx = (int) Math.round(Math.cos(dotAngle) * (radius + 5));
                int dy = (int) Math.round(Math.sin(dotAngle) * (radius + 5));
                disc(graphics, cx + dx, cy + dy, 1, ARGB.color(Math.round(0xC0 * fade * opening), ARGB.srgbLerp(hover, color, 0xFFFFFFFF)));
            }
        }

        float scale = 1.0F + 0.35F * hover;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.item(stack(icon), -8, -8);
        graphics.pose().popMatrix();
    }

    private void pointer(GuiGraphicsExtractor graphics, double originX, double originY, double radius, double deadZone, int color, float opening) {
        double localX = this.cursorX - originX;
        double localY = this.cursorY - originY;
        double distance = Math.hypot(localX, localY);
        int cursorX = (int) Math.round(this.cursorX);
        int cursorY = (int) Math.round(this.cursorY);

        if (distance >= deadZone) {
            double angle = Math.atan2(localY, localX);
            for (int k = -POINTER_DOTS / 2; k <= POINTER_DOTS / 2; k++) {
                double dotAngle = angle + Math.toRadians(k * POINTER_SPREAD);
                float strength = 1.0F - Math.abs(k) / (POINTER_DOTS / 2.0F + 1.0F);
                int px = (int) Math.round(originX + Math.cos(dotAngle) * (radius - NODE_RADIUS - 6));
                int py = (int) Math.round(originY + Math.sin(dotAngle) * (radius - NODE_RADIUS - 6));
                disc(graphics, px, py, k == 0 ? 2 : 1, ARGB.color(Math.round(0xE0 * strength * opening), ARGB.srgbLerp(strength, color, 0xFFFFFFFF)));
            }
            trail(graphics, originX, originY, this.cursorX, this.cursorY, ARGB.color(Math.round(0x80 * opening), color));
        }

        disc(graphics, cursorX, cursorY, 4, ARGB.color(Math.round(0x70 * opening), color));
        disc(graphics, cursorX, cursorY, 2, ARGB.color(Math.round(0xF0 * opening), 0xFFFFFF));
    }

    private void hub(GuiGraphicsExtractor graphics, Player player, int color, float opening) {
        disc(graphics, 0, 0, HUB_RADIUS, ARGB.color(Math.round(0xC8 * opening), darken(color, 0.18F)));
        ring(graphics, 0, 0, HUB_RADIUS, 1, ARGB.color(Math.round(0xB0 * opening), color));
        ring(graphics, 0, 0, (int) DEAD_ZONE, 1, ARGB.color(Math.round(0x40 * opening), color));

        Item icon = this.hoveredIcon(player);
        if (icon == null) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.5F, 1.5F);
        graphics.item(stack(icon), -8, -8);
        graphics.pose().popMatrix();
    }

    private Item hoveredIcon(Player player) {
        if (this.hoveredEntry < 0 || this.hoveredEntry >= this.entries.size()) {
            for (Entry entry : this.entries) {
                if (this.isCurrent(player, entry)) return entry.currentSub().map(Sub::icon).orElse(entry.icon());
            }
            return null;
        }
        Entry entry = this.entries.get(this.hoveredEntry);
        if (this.hoveredSub >= 0 && this.hoveredSub < entry.subs().size()) return entry.subs().get(this.hoveredSub).icon();
        return entry.icon();
    }

    private Component label() {
        if (this.hoveredEntry < 0 || this.hoveredEntry >= this.entries.size()) {
            return this.travelled >= TAP_DISTANCE ? Component.translatable("gui.brightestday.wheel_cancel") : null;
        }
        Entry entry = this.entries.get(this.hoveredEntry);
        if (this.backHovered) return Component.translatable("gui.brightestday.wheel_back");
        if (this.hoveredSub >= 0 && this.hoveredSub < entry.subs().size()) {
            return Component.empty().append(entry.name()).append(" › ").append(entry.subs().get(this.hoveredSub).name());
        }
        return entry.name();
    }

    private static void trail(GuiGraphicsExtractor graphics, double fromX, double fromY, double toX, double toY, int color) {
        double length = Math.hypot(toX - fromX, toY - fromY);
        int steps = (int) (length / TRAIL_SPACING);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(Mth.lerp(t, fromX, toX));
            int y = (int) Math.round(Mth.lerp(t, fromY, toY));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
        if (ARGB.alpha(color) == 0) return;
        for (int dy = -radius; dy < radius; dy++) {
            double row = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(radius * radius - row * row, 0.0)));
            if (half > 0) graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int thickness, int color) {
        if (ARGB.alpha(color) == 0) return;
        int inner = radius - thickness;
        for (int dy = -radius; dy < radius; dy++) {
            double row = dy + 0.5;
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(radius * radius - row * row, 0.0)));
            int innerHalf = Math.abs(row) < inner ? (int) Math.round(Math.sqrt(inner * inner - row * row)) : 0;
            if (outerHalf <= innerHalf) continue;
            graphics.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, color);
            graphics.fill(cx + innerHalf, cy + dy, cx + outerHalf, cy + dy + 1, color);
        }
    }

    public static int indicatorTop() {
        return indicatorTop;
    }

    private static void extractIndicator(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        indicatorTop = -1;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || PowerRingItem.getWornCorps(player).isEmpty()) return;

        Item icon;
        Component title;
        Component detail = null;
        Optional<RingPower<?>> ability = ArmedRingPower.activeAbility(player);
        if (ability.isPresent()) {
            icon = icon(ability.get());
            title = Component.translatable(ability.get().getTranslationKey());
        } else {
            Optional<ConstructRingPower> selected = ArmedRingPower.selectedConstruct(player);
            if (selected.isEmpty()) return;

            ConstructRingPower construct = selected.get();
            Entry entry = CONSTRUCTS.entries.stream().filter(candidate -> candidate.powers().contains(construct)).findFirst().orElse(null);
            Optional<Sub> sub = entry == null ? Optional.empty() : entry.currentSub();
            icon = sub.map(Sub::icon).orElse(icon(construct));
            title = Component.translatable(construct.getTranslationKey());
            if ((construct == RingPowerRegistry.SCULPT || construct == RingPowerRegistry.DRILL) && sub.isPresent()) {
                title = Component.empty().append(title).append(" · ").append(sub.get().name());
            }
            if (construct.usesSize()) {
                detail = Component.translatable("hud.brightestday.construct_size", construct.describeSize(ConstructClient.size(construct)));
            }
        }

        Font font = client.font;
        int color = ARGB.opaque(CorpsColors.of(player));
        int textWidth = Math.max(font.width(title), detail == null ? 0 : font.width(detail));
        int width = HUD_PADDING * 3 + 16 + textWidth;
        int height = HUD_PADDING * 2 + 16;
        int left = HUD_MARGIN;
        int top = graphics.guiHeight() - HUD_MARGIN - height;
        indicatorTop = top;

        graphics.fill(left, top, left + width, top + height, ARGB.color(0x90, darken(color, 0.25F)));
        graphics.fill(left, top, left + 1, top + height, ARGB.color(0xFF, color));
        graphics.item(stack(icon), left + HUD_PADDING, top + HUD_PADDING);
        int textX = left + HUD_PADDING * 2 + 16;
        if (detail == null) {
            graphics.text(font, title, textX, top + HUD_PADDING + 4, 0xFFFFFFFF, true);
        } else {
            graphics.text(font, title, textX, top + HUD_PADDING - 1, 0xFFFFFFFF, true);
            graphics.text(font, detail, textX, top + HUD_PADDING + 9, 0xFFB8C4BA, true);
        }
    }
}

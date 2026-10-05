package dev.amble.datagen.providers;

import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.BrightestDayItems;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.sculpt.SculptShape;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

public class BrightestDayLanguageProvider extends FabricLanguageProvider {
    public BrightestDayLanguageProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(packOutput, registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registryLookup, TranslationBuilder builder) {
        builder.add(BrightestDayItems.GREEN_POWER_RING, "Green Power Ring");
        builder.add(BrightestDayItems.YELLOW_POWER_RING, "Yellow Power Ring");
        builder.add(BrightestDayItems.RED_POWER_RING, "Red Power Ring");
        builder.add(BrightestDayItems.ORANGE_POWER_RING, "Orange Power Ring");
        builder.add(BrightestDayItems.BLUE_POWER_RING, "Blue Power Ring");
        builder.add(BrightestDayItems.INDIGO_POWER_RING, "Indigo Power Ring");
        builder.add(BrightestDayItems.STAR_SAPPHIRE_POWER_RING, "Star Sapphire Power Ring");
        builder.add(BrightestDayItems.WHITE_POWER_RING, "White Power Ring");
        builder.add(BrightestDayItems.BLACK_POWER_RING, "Black Power Ring");
        builder.add(BrightestDayBlocks.GREEN_LANTERN_BLOCK, "Green Lantern");
        builder.add(BrightestDayBlocks.YELLOW_LANTERN_BLOCK, "Yellow Lantern");
        builder.add(BrightestDayBlocks.RED_LANTERN_BLOCK, "Red Lantern");
        builder.add(BrightestDayBlocks.ORANGE_LANTERN_BLOCK, "Orange Lantern");
        builder.add(BrightestDayBlocks.BLUE_LANTERN_BLOCK, "Blue Lantern");
        builder.add(BrightestDayBlocks.INDIGO_LANTERN_BLOCK, "Indigo Lantern");
        builder.add(BrightestDayBlocks.STAR_SAPPHIRE_LANTERN_BLOCK, "Star Sapphire Lantern");

        builder.add("itemGroup.brightestday.brightest_day", "In Brightest Day");
        builder.add("container.brightestday.lantern", "Ring Slot");
        builder.add("gui.brightestday.lantern", "Ring Slot");
        builder.add("gui.brightestday.ring_charge", "Charge: %s%%");
        builder.add("gui.brightestday.inventory", "Inventory");
        builder.add("gui.brightestday.no_ring", "No ring equipped");
        builder.add("gui.brightestday.wheel_cancel", "Cancel");
        builder.add("gui.brightestday.wheel_back", "Back");
        builder.add("block.brightestday.battery_light", "Central Power Battery Light");
        builder.add("celestial.brightestday.earth", "Earth");
        builder.add("celestial.brightestday.moon", "Moon");
        builder.add("celestial.brightestday.gas_giant", "Gas Giant");
        builder.add("celestial.brightestday.sun", "Sun");
        builder.add("celestial.brightestday.oa", "Oa");
        builder.add("celestial.brightestday.oa_moon", "Moon of Oa");
        builder.add("celestial.brightestday.wormhole_oa", "Wormhole to Oa");
        builder.add("celestial.brightestday.wormhole_earth", "Wormhole to Sector 2814");
        builder.add("config.brightestday.group.space", "Space");
        builder.add("config.brightestday.option.space_entry_height", "Space Entry Height");
        builder.add("config.brightestday.option.space_entry_height.desc", "How high a ring-flier must climb above Earth or Oa to break into space.");
        builder.add("config.brightestday.option.space_arrival_height", "Planet Arrival Height");
        builder.add("config.brightestday.option.space_arrival_height.desc", "The height you arrive at when flying into a planet from space.");
        builder.add("key.brightestday.team", "Lantern Team");
        builder.add("gui.brightestday.team.title", "Lantern Team");
        builder.add("gui.brightestday.team.your_team", "Your Team");
        builder.add("gui.brightestday.team.solo", "Invite a nearby lantern below to team up.");
        builder.add("gui.brightestday.team.no_ring", "Put on a power ring to team up.");
        builder.add("gui.brightestday.team.nearby", "Nearby Lanterns");
        builder.add("gui.brightestday.team.none", "No other ring-bearers within %s blocks.");
        builder.add("gui.brightestday.team.invite", "Invite");
        builder.add("gui.brightestday.team.pending", "Pending");
        builder.add("gui.brightestday.team.accept", "Accept");
        builder.add("gui.brightestday.team.leave", "Leave");
        builder.add("gui.brightestday.team.too_far", "Too far");
        builder.add("gui.brightestday.team.you", "You");
        builder.add("gui.brightestday.team.distance", "%sm");
        builder.add("gui.brightestday.team.hint", "Teammates are spared by each other's constructs, and seeking attacks ignore them.");
        builder.add("gui.brightestday.team.affinity.same", "Same Corps");
        builder.add("gui.brightestday.team.affinity.kindred", "Kindred");
        builder.add("gui.brightestday.team.affinity.aligned", "Aligned");
        builder.add("gui.brightestday.team.affinity.distant", "Distant");
        builder.add("gui.brightestday.team.affinity.opposed", "Opposed");
        builder.add("message.brightestday.team.invited", "%s of the %s wants to team up. Press %s to respond.");
        builder.add("message.brightestday.team.invite_sent", "Team invite sent to %s.");
        builder.add("message.brightestday.team.joined", "%s of the %s joined your team.");
        builder.add("message.brightestday.team.declined", "%s declined your team invite.");
        builder.add("message.brightestday.team.left", "%s left your team.");
        builder.add("message.brightestday.team.you_left", "You left your team.");
        builder.add("message.brightestday.team.full", "That team is full.");
        builder.add("death.attack.brightestday.ring_construct", "%1$s was struck down by a ring construct");
        builder.add("death.attack.brightestday.ring_construct.player", "%1$s was struck down by %2$s's ring");
        builder.add("config.brightestday.group.team", "Lantern Teams");
        builder.add("config.brightestday.option.team_invite_range", "Invite Range");
        builder.add("config.brightestday.option.team_invite_range.desc", "How close another ring-bearer must be to invite them to your team.");
        builder.add("config.brightestday.option.team_max_size", "Max Team Size");
        builder.add("config.brightestday.option.team_max_size.desc", "The most lanterns that can share one team.");
        builder.add("gui.brightestday.brightness", "Brightness: %s");
        builder.add("gui.brightestday.saturation", "Saturation: %s");
        builder.add("gui.brightestday.aura", "Aura");
        builder.add("gui.brightestday.suit", "Suit");
        builder.add("gui.brightestday.mask", "Mask");
        builder.add("gui.brightestday.mask_height", "Mask Height: %s");

        builder.add(LanternCorps.GREEN.getTranslationKey(), "Green Lantern Corps");
        builder.add(LanternCorps.BLUE.getTranslationKey(), "Blue Lantern Corps");
        builder.add(LanternCorps.YELLOW.getTranslationKey(), "Sinestro Corps");
        builder.add(LanternCorps.ORANGE.getTranslationKey(), "Orange Lanterns");
        builder.add(LanternCorps.RED.getTranslationKey(), "Red Lantern Corps");
        builder.add(LanternCorps.INDIGO.getTranslationKey(), "Indigo Tribe");
        builder.add(LanternCorps.STAR_SAPPHIRE.getTranslationKey(), "Star Sapphires");
        builder.add(LanternCorps.WHITE.getTranslationKey(), "White Lantern Corps");
        builder.add(LanternCorps.BLACK.getTranslationKey(), "Black Lantern Corps");

        builder.add(LanternCorps.GREEN.oathKey(), """
                In brightest day,
                In blackest night,
                No evil shall escape my sight.
                Let those who worship evil's might,
                Beware my power,
                Green Lantern's light!""");
        builder.add(LanternCorps.YELLOW.oathKey(), """
                In blackest day,
                In brightest night,
                Beware your fears made into light.
                Let those who try to stop what's right,
                Burn like my power,
                Sinestro's might!""");
        builder.add(LanternCorps.RED.oathKey(), """
                With blood and rage of crimson red,
                Ripped from a corpse so freshly dead,
                Together with our hellish hate,
                We'll burn you all, that is your fate!""");
        builder.add(LanternCorps.ORANGE.oathKey(), """
                What's mine is mine and mine and mine,
                And mine and mine and mine!
                Not yours!""");
        builder.add(LanternCorps.BLUE.oathKey(), """
                In fearful day,
                In raging night,
                With strong hearts full, our souls ignite.
                When all seems lost in the War of Light,
                Look to the stars,
                For hope burns bright!""");
        builder.add(LanternCorps.INDIGO.oathKey(), """
                Tor lorek san, nok var.
                Ter lantern ker, lok tar.
                Nok formorra, sorrow lo.
                Sen ker, sen lo, sen gorro.""");
        builder.add(LanternCorps.STAR_SAPPHIRE.oathKey(), """
                For hearts that feel an empty place,
                And lonely souls adrift in space,
                Take the love I have to give,
                Star Sapphire, through you we live!""");
        builder.add(LanternCorps.WHITE.oathKey(), """
                From the dark of death,
                To the light of birth,
                All that lives shall share this earth.
                By the white light's endless worth,
                I stand for life,
                The glow of rebirth!""");
        builder.add(LanternCorps.BLACK.oathKey(), """
                The blackest night falls from the skies,
                The darkness grows as all light dies,
                We crave your hearts and your demise,
                By my black hand, the dead shall rise!""");

        builder.add("message.brightestday.wrong_lantern", "This lantern only answers to the %s.");
        builder.add("message.brightestday.face_lantern", "Stand before the lantern's face to charge your ring.");
        builder.add("message.brightestday.ring_equipped", "%s ring equipped.");
        builder.add("message.brightestday.loyalty_wavers", "Your ring's loyalty wavers. Fall %s more time(s) and it will seek another bearer.");
        builder.add("message.brightestday.loyalty_departed", "Your ring has left you in search of a worthier bearer.");
        builder.add("message.brightestday.loyalty_chosen", "A ring has chosen you. Welcome to the %s.");
        builder.add("message.brightestday.loyalty_lantern_found", "Your Power Battery awaits at %s, %s, %s in %s.");
        builder.add("message.brightestday.loyalty_lantern_lost", "Your ring's Power Battery was lost. A new one has been forged for you.");
        builder.add("message.brightestday.arm_to_charge", "Raise your ring to the lantern to charge it.");
        builder.add("message.brightestday.no_constructs", "Your ring cannot manifest constructs.");
        builder.add("message.brightestday.nothing_to_heal", "No one to heal.");
        builder.add("message.brightestday.ring_depleted", "Your ring is out of charge.");
        builder.add("message.brightestday.hope_gained", "A Blue Lantern's hope empowers your ring.");
        builder.add("message.brightestday.hope_lost", "The Blue Lantern's hope fades from your ring.");
        builder.add("message.brightestday.will_gained", "A Green Lantern's will unlocks your ring.");
        builder.add("message.brightestday.will_lost", "Without a Green Lantern, your ring's power recedes.");
        builder.add("message.brightestday.dread_gained", "A Blue Lantern's hope weakens your ring.");
        builder.add("message.brightestday.dread_lost", "Your ring's strength returns.");
        builder.add("message.brightestday.stand_still_to_charge", "Stand still on the ground to charge your ring.");
        builder.add("subtitles.brightestday.ring.charge_5_percent", "Power ring charge at 5%");
        builder.add("message.brightestday.construct_selected", "Construct: %s");
        builder.add("message.brightestday.shield_radius", "Shield radius: %s");

        builder.add(RingPowerRegistry.FLIGHT.getTranslationKey(), "Flight");
        builder.add(RingPowerRegistry.ARMED.getTranslationKey(), "Armed");
        builder.add(RingPowerRegistry.LIGHT.getTranslationKey(), "Spotlight");
        builder.add(RingPowerRegistry.BLAST.getTranslationKey(), "Blast");
        builder.add(RingPowerRegistry.ENTITY_SHIELD.getTranslationKey(), "Bubble Shield");
        builder.add(RingPowerRegistry.AREA_SHIELD.getTranslationKey(), "Dome Shield");
        builder.add(RingPowerRegistry.BEAM.getTranslationKey(), "Beam");
        builder.add(RingPowerRegistry.HEAL_BEAM.getTranslationKey(), "Healing Beam");
        builder.add(RingPowerRegistry.WALL.getTranslationKey(), "Wall");
        builder.add("message.brightestday.construct_size", "%s size: %s");
        builder.add("message.brightestday.wall_blocked", "There's no room for a wall there.");
        builder.add(RingPowerRegistry.SCULPT.getTranslationKey(), "Sculpt");
        builder.add(SculptShape.FREEFORM.translationKey(), "Freeform");
        builder.add(SculptShape.STAIRS.translationKey(), "Stairs");
        builder.add(SculptShape.TUBE.translationKey(), "Tube");
        builder.add(SculptShape.CAGE.translationKey(), "Cage");
        builder.add("message.brightestday.sculpt_shape", "%s shape: %s");
        builder.add("message.brightestday.sculpt_limit", "Your construct is at its limit.");
        builder.add("message.brightestday.sculpt_cage_too_small", "Circle an area to raise a cage.");
        builder.add("message.brightestday.sculpt_fading", "Your ring can't hold your constructs together!");
        builder.add("hud.brightestday.sculpt_width", "Width: %s");
        builder.add("hud.brightestday.sculpt_hint", "Sneak + Scroll: shape");
        builder.add("hud.brightestday.construct_size", "Size: %s");
        builder.add("construct_group.brightestday.shield", "Shield");
        builder.add("construct_group.brightestday.attacks", "Attacks");
        builder.add("construct_group.brightestday.weapons", "Weapons");
        builder.add("construct_group.brightestday.utility", "Utility");
        builder.add("brightestday.ring_power.lumberjack", "Lumberjack");
        builder.add("brightestday.ring_power.ore_probe", "Ore Finder Probe");
        builder.add("message.brightestday.lumberjack_no_log", "Aim at a log to fell a tree.");
        builder.add("message.brightestday.lumberjack_not_tree", "That isn't a natural tree.");
        builder.add("message.brightestday.lumberjack_blocked", "You can't fell that tree here.");
        builder.add("message.brightestday.lumberjack_busy", "That tree is already being felled.");
        builder.add("brightestday.ring_power.grappling_hook", "Grappling Hook");
        builder.add("drill_mode.brightestday.hold", "Hold");
        builder.add("drill_mode.brightestday.tunnel", "Tunnel");
        builder.add("brightestday.ring_power.glider", "Glider");
        builder.add("message.brightestday.glider_granted", "Hard-light glider equipped: jump mid-air to glide.");
        builder.add("config.brightestday.group.glider", "Glider");
        builder.add("config.brightestday.option.glider_cost", "Charge Cost");
        builder.add("config.brightestday.option.glider_cost.desc", "Ring charge spent to give someone a glider.");
        builder.add("config.brightestday.option.glider_charge", "Charge-Up");
        builder.add("config.brightestday.option.glider_charge.desc", "Ticks the glider must charge before it's sent.");
        builder.add("config.brightestday.option.glider_duration", "Duration");
        builder.add("config.brightestday.option.glider_duration.desc", "How many ticks the glider lasts on its target (20 ticks = 1 second).");
        builder.add("config.brightestday.option.glider_range", "Range");
        builder.add("config.brightestday.option.glider_range.desc", "How far away in blocks you can send a glider.");
        builder.add("config.brightestday.option.glider_assist_cone", "Aim Assist Cone");
        builder.add("config.brightestday.option.glider_assist_cone.desc", "Angle in degrees around the crosshair within which the glider snaps to a target.");
        builder.add("config.brightestday.option.glider_mob_fall_speed", "Mob Fall Speed");
        builder.add("config.brightestday.option.glider_mob_fall_speed.desc", "The fastest a mob with a glider can fall, in blocks per tick.");
        builder.add("config.brightestday.group.flight", "Flight");
        builder.add("config.brightestday.option.flight_boost_multiplier", "Boost Multiplier");
        builder.add("config.brightestday.option.flight_boost_multiplier.desc", "How many times faster than your cruise speed boosting flies.");
        builder.add("config.brightestday.option.flight_max_boost_speed", "Max Boost Speed");
        builder.add("config.brightestday.option.flight_max_boost_speed.desc", "The fastest boosting can ever go, in blocks per tick (20 ticks = 1 second).");
        builder.add("config.brightestday.option.flight_drain", "Cruise Drain");
        builder.add("config.brightestday.option.flight_drain.desc", "Ring charge drained per second while flying at the default speed; slower and faster speeds scale this.");
        builder.add("config.brightestday.option.flight_boost_drain", "Boost Drain");
        builder.add("config.brightestday.option.flight_boost_drain.desc", "Ring charge drained per second while boosting at the default speed.");
        builder.add("config.brightestday.option.show_flight_speedometer", "Show Speedometer");
        builder.add("config.brightestday.option.show_flight_speedometer.desc", "Whether the flight speedometer appears while ring-flying.");
        builder.add("config.brightestday.option.show_flight_trails", "Show Flight Trails");
        builder.add("config.brightestday.option.show_flight_trails.desc", "Whether ring-fliers leave a glowing trail behind them.");
        builder.add("config.brightestday.option.aileron_rolls", "Aileron Rolls");
        builder.add("config.brightestday.option.aileron_rolls.desc", "Double-tap a strafe key while flying at speed to barrel roll in that direction.");
        builder.add("config.brightestday.option.aileron_roll_dodge", "Aileron Roll Dodge");
        builder.add("config.brightestday.option.aileron_roll_dodge.desc", "How hard an aileron roll pushes you sideways.");
        builder.add("config.brightestday.group.synergy", "Corps Synergy");
        builder.add("config.brightestday.option.synergy_link_radius", "Hope/Will Range");
        builder.add("config.brightestday.option.synergy_link_radius.desc", "How close Blue and Green Lanterns must be to empower each other.");
        builder.add("config.brightestday.option.synergy_dread_radius", "Hope Dread Range");
        builder.add("config.brightestday.option.synergy_dread_radius.desc", "How close a Blue Lantern must be to weaken Yellow and Red Lanterns.");
        builder.add("config.brightestday.group.loyalty", "Ring Loyalty");
        builder.add("config.brightestday.option.ring_loyalty_deaths", "Deaths Before Leaving");
        builder.add("config.brightestday.option.ring_loyalty_deaths.desc", "How many times a bearer can die before their ring leaves to find a worthier bearer. 0 means rings never leave.");
        builder.add("config.brightestday.option.ring_loyalty_search_radius", "Search Radius");
        builder.add("config.brightestday.option.ring_loyalty_search_radius.desc", "How far a departing ring flies to find a new bearer before seeking one anywhere in the world.");
        builder.add("brightestday.ring_power.drill", "Drill");
        builder.add("config.brightestday.group.drill", "Drill");
        builder.add("config.brightestday.option.drill_cost", "Charge Cost");
        builder.add("config.brightestday.option.drill_cost.desc", "Ring charge spent to start drilling.");
        builder.add("config.brightestday.option.drill_charge", "Charge-Up");
        builder.add("config.brightestday.option.drill_charge.desc", "Ticks spent charging before the drill starts.");
        builder.add("config.brightestday.option.drill_speed", "Drill Speed");
        builder.add("config.brightestday.option.drill_speed.desc", "Mining speed of the drill, comparable to a pickaxe's (netherite is 9). Larger bores split this across their width.");
        builder.add("config.brightestday.option.drill_drain", "Drain Per Second");
        builder.add("config.brightestday.option.drill_drain.desc", "Ring charge drained each second while drilling.");
        builder.add("config.brightestday.option.drill_drain_per_size", "Drain Per Size Step");
        builder.add("config.brightestday.option.drill_drain_per_size.desc", "Extra charge drained each second for each bore size above 1×1.");
        builder.add("config.brightestday.option.drill_placed_max", "Max Placed Drills");
        builder.add("config.brightestday.option.drill_placed_max.desc", "Placed drills one player can have active; the oldest is replaced.");
        builder.add("config.brightestday.option.drill_placed_distance", "Placed Drill Distance");
        builder.add("config.brightestday.option.drill_placed_distance.desc", "Blocks a placed drill bores forward before dissolving.");
        builder.add("config.brightestday.option.drill_placed_lifetime", "Placed Drill Lifetime");
        builder.add("config.brightestday.option.drill_placed_lifetime.desc", "Ticks a placed drill runs before dissolving.");
        builder.add("config.brightestday.option.drill_placed_travel", "Placed Drill Travel Time");
        builder.add("config.brightestday.option.drill_placed_travel.desc", "Minimum ticks a placed drill takes to advance one block.");
        builder.add("key.brightestday.flight_speed_up", "Flight Speed Up");
        builder.add("key.brightestday.flight_speed_down", "Flight Speed Down");
        builder.add("key.brightestday.flight_boost", "Flight Boost");
        builder.add("message.brightestday.flight_speed", "Flight Speed %s/%s · %s b/s");
        builder.add("hud.brightestday.flight_speed", "%s b/s");
        builder.add("hud.brightestday.flight_cruise", "Cruise %s");
        builder.add("hud.brightestday.flight_boost", "BOOST");
        builder.add("config.brightestday.category.attacks", "Attacks");
        builder.add("config.brightestday.group.swarm_missiles", "Swarm Missiles");
        builder.add("config.brightestday.option.swarm_cost", "Charge Cost");
        builder.add("config.brightestday.option.swarm_cost.desc", "Ring charge spent to launch a swarm volley.");
        builder.add("config.brightestday.option.swarm_charge", "Charge-Up");
        builder.add("config.brightestday.option.swarm_charge.desc", "Ticks spent charging before the volley launches.");
        builder.add("config.brightestday.option.swarm_darts", "Dart Count");
        builder.add("config.brightestday.option.swarm_darts.desc", "Number of homing darts launched per volley.");
        builder.add("config.brightestday.option.swarm_damage", "Damage");
        builder.add("config.brightestday.option.swarm_damage.desc", "Damage dealt by each dart on hit.");
        builder.add("config.brightestday.option.swarm_range", "Range");
        builder.add("config.brightestday.option.swarm_range.desc", "Distance darts search for targets and travel before fizzling.");
        builder.add("config.brightestday.option.swarm_cone", "Targeting Cone");
        builder.add("config.brightestday.option.swarm_cone.desc", "Angle of the cone in front of you that darts pick targets from.");
        builder.add("config.brightestday.option.swarm_knockback", "Knockback");
        builder.add("config.brightestday.option.swarm_knockback.desc", "Push applied to targets struck by a dart.");
        builder.add("config.brightestday.group.piercing_lance", "Piercing Lance");
        builder.add("config.brightestday.option.lance_cost", "Charge Cost");
        builder.add("config.brightestday.option.lance_cost.desc", "Ring charge spent to fire a piercing lance.");
        builder.add("config.brightestday.option.lance_charge", "Charge-Up");
        builder.add("config.brightestday.option.lance_charge.desc", "Ticks spent charging before the lance fires.");
        builder.add("config.brightestday.option.lance_damage", "Damage");
        builder.add("config.brightestday.option.lance_damage.desc", "Damage dealt to every target the lance passes through.");
        builder.add("config.brightestday.option.lance_range", "Range");
        builder.add("config.brightestday.option.lance_range.desc", "Maximum length of the lance.");
        builder.add("config.brightestday.option.lance_knockback", "Knockback");
        builder.add("config.brightestday.option.lance_knockback.desc", "Push applied to targets pierced by the lance.");
        builder.add("config.brightestday.group.chain_bolt", "Chain Bolt");
        builder.add("config.brightestday.option.chain_cost", "Charge Cost");
        builder.add("config.brightestday.option.chain_cost.desc", "Ring charge spent to fire a chain bolt.");
        builder.add("config.brightestday.option.chain_charge", "Charge-Up");
        builder.add("config.brightestday.option.chain_charge.desc", "Ticks spent charging before the bolt fires.");
        builder.add("config.brightestday.option.chain_damage", "Damage");
        builder.add("config.brightestday.option.chain_damage.desc", "Damage dealt to the first target struck.");
        builder.add("config.brightestday.option.chain_range", "Range");
        builder.add("config.brightestday.option.chain_range.desc", "Maximum distance to the first target.");
        builder.add("config.brightestday.option.chain_max_jumps", "Max Jumps");
        builder.add("config.brightestday.option.chain_max_jumps.desc", "Number of extra targets the bolt can arc to.");
        builder.add("config.brightestday.option.chain_falloff", "Damage Falloff");
        builder.add("config.brightestday.option.chain_falloff.desc", "Damage multiplier applied after each jump.");
        builder.add("config.brightestday.option.chain_jump_range", "Jump Range");
        builder.add("config.brightestday.option.chain_jump_range.desc", "Maximum distance the bolt can arc between targets.");
        builder.add("config.brightestday.option.chain_knockback", "Knockback");
        builder.add("config.brightestday.option.chain_knockback.desc", "Push applied to each target struck by the bolt.");
        builder.add("config.brightestday.group.boomerang_disc", "Boomerang Disc");
        builder.add("config.brightestday.option.disc_cost", "Charge Cost");
        builder.add("config.brightestday.option.disc_cost.desc", "Ring charge spent to throw a boomerang disc.");
        builder.add("config.brightestday.option.disc_charge", "Charge-Up");
        builder.add("config.brightestday.option.disc_charge.desc", "Ticks spent charging before the disc is thrown.");
        builder.add("config.brightestday.option.disc_damage", "Damage");
        builder.add("config.brightestday.option.disc_damage.desc", "Damage dealt each time the disc passes through a target.");
        builder.add("config.brightestday.option.disc_range", "Range");
        builder.add("config.brightestday.option.disc_range.desc", "Distance the disc flies before turning back.");
        builder.add("config.brightestday.option.disc_knockback", "Knockback");
        builder.add("config.brightestday.option.disc_knockback.desc", "Push applied to targets the disc hits.");
        builder.add("config.brightestday.group.nova_burst", "Nova Burst");
        builder.add("config.brightestday.option.nova_cost", "Charge Cost");
        builder.add("config.brightestday.option.nova_cost.desc", "Ring charge spent to release a nova burst.");
        builder.add("config.brightestday.option.nova_charge", "Charge-Up");
        builder.add("config.brightestday.option.nova_charge.desc", "Ticks spent charging before the burst releases.");
        builder.add("config.brightestday.option.nova_damage", "Damage");
        builder.add("config.brightestday.option.nova_damage.desc", "Damage dealt at the center of the burst, falling off with distance.");
        builder.add("config.brightestday.option.nova_radius", "Radius");
        builder.add("config.brightestday.option.nova_radius.desc", "Radius of the burst around you.");
        builder.add("config.brightestday.option.nova_knockback", "Knockback");
        builder.add("config.brightestday.option.nova_knockback.desc", "Outward push applied to targets caught in the burst.");
        builder.add("config.brightestday.group.ground_slam", "Ground Slam");
        builder.add("config.brightestday.option.slam_cost", "Charge Cost");
        builder.add("config.brightestday.option.slam_cost.desc", "Ring charge spent to perform a ground slam.");
        builder.add("config.brightestday.option.slam_charge", "Charge-Up");
        builder.add("config.brightestday.option.slam_charge.desc", "Ticks spent charging before you dive.");
        builder.add("config.brightestday.option.slam_base_damage", "Base Damage");
        builder.add("config.brightestday.option.slam_base_damage.desc", "Damage dealt by a slam from no height.");
        builder.add("config.brightestday.option.slam_damage_per_block", "Damage Per Block");
        builder.add("config.brightestday.option.slam_damage_per_block.desc", "Extra damage added for each block fallen.");
        builder.add("config.brightestday.option.slam_max_damage", "Max Damage");
        builder.add("config.brightestday.option.slam_max_damage.desc", "Upper limit on slam damage regardless of height.");
        builder.add("config.brightestday.option.slam_min_radius", "Min Radius");
        builder.add("config.brightestday.option.slam_min_radius.desc", "Shockwave radius of a slam from no height.");
        builder.add("config.brightestday.option.slam_max_radius", "Max Radius");
        builder.add("config.brightestday.option.slam_max_radius.desc", "Upper limit on shockwave radius regardless of height.");
        builder.add("config.brightestday.option.slam_radius_per_block", "Radius Per Block");
        builder.add("config.brightestday.option.slam_radius_per_block.desc", "Extra shockwave radius added for each block fallen.");
        builder.add("config.brightestday.option.slam_launch", "Launch");
        builder.add("config.brightestday.option.slam_launch.desc", "Upward force applied to targets caught in the shockwave.");
        builder.add("config.brightestday.group.rapid_barrage", "Rapid Barrage");
        builder.add("config.brightestday.option.barrage_cost", "Charge Cost");
        builder.add("config.brightestday.option.barrage_cost.desc", "Ring charge spent to start a barrage.");
        builder.add("config.brightestday.option.barrage_charge", "Charge-Up");
        builder.add("config.brightestday.option.barrage_charge.desc", "Ticks spent charging before the barrage starts.");
        builder.add("config.brightestday.option.barrage_damage", "Damage");
        builder.add("config.brightestday.option.barrage_damage.desc", "Damage dealt by each bolt.");
        builder.add("config.brightestday.option.barrage_range", "Range");
        builder.add("config.brightestday.option.barrage_range.desc", "Maximum distance each bolt travels.");
        builder.add("config.brightestday.option.barrage_fire_interval", "Fire Interval");
        builder.add("config.brightestday.option.barrage_fire_interval.desc", "Ticks between bolts while the barrage is held.");
        builder.add("config.brightestday.option.barrage_max_ticks", "Max Duration");
        builder.add("config.brightestday.option.barrage_max_ticks.desc", "Longest a barrage can be sustained, in ticks.");
        builder.add("config.brightestday.option.barrage_drain", "Drain Per Second");
        builder.add("config.brightestday.option.barrage_drain.desc", "Ring charge drained each second while firing.");
        builder.add("config.brightestday.option.barrage_spread", "Spread");
        builder.add("config.brightestday.option.barrage_spread.desc", "Random inaccuracy of each bolt, in degrees.");
        builder.add("config.brightestday.group.giant_fist", "Giant Fist");
        builder.add("config.brightestday.option.fist_cost", "Charge Cost");
        builder.add("config.brightestday.option.fist_cost.desc", "Ring charge spent to throw a giant fist.");
        builder.add("config.brightestday.option.fist_charge", "Charge-Up");
        builder.add("config.brightestday.option.fist_charge.desc", "Ticks spent charging before the punch.");
        builder.add("config.brightestday.option.fist_damage", "Damage");
        builder.add("config.brightestday.option.fist_damage.desc", "Damage dealt to targets struck by the fist.");
        builder.add("config.brightestday.option.fist_range", "Range");
        builder.add("config.brightestday.option.fist_range.desc", "Distance the fist travels forward.");
        builder.add("config.brightestday.option.fist_knockback", "Knockback");
        builder.add("config.brightestday.option.fist_knockback.desc", "Push applied to targets struck by the fist.");
        builder.add("config.brightestday.option.fist_impact_radius", "Impact Radius");
        builder.add("config.brightestday.option.fist_impact_radius.desc", "Radius of the shockwave when the fist hits a block.");
        builder.add("config.brightestday.option.fist_impact_damage", "Impact Damage");
        builder.add("config.brightestday.option.fist_impact_damage.desc", "Damage dealt by the impact shockwave.");
        builder.add("config.brightestday.group.energy_whip", "Energy Whip");
        builder.add("config.brightestday.option.whip_cost", "Charge Cost");
        builder.add("config.brightestday.option.whip_cost.desc", "Ring charge spent to crack the whip.");
        builder.add("config.brightestday.option.whip_charge", "Charge-Up");
        builder.add("config.brightestday.option.whip_charge.desc", "Ticks spent charging before the whip cracks.");
        builder.add("config.brightestday.option.whip_damage", "Damage");
        builder.add("config.brightestday.option.whip_damage.desc", "Damage dealt by the sweeping crack.");
        builder.add("config.brightestday.option.whip_length", "Length");
        builder.add("config.brightestday.option.whip_length.desc", "Reach of the sweeping crack.");
        builder.add("config.brightestday.option.whip_arc", "Arc");
        builder.add("config.brightestday.option.whip_arc.desc", "Width of the sweep, in degrees.");
        builder.add("config.brightestday.option.whip_knockback", "Knockback");
        builder.add("config.brightestday.option.whip_knockback.desc", "Sideways push applied by the sweeping crack.");
        builder.add("config.brightestday.option.whip_lash_length", "Lash Length");
        builder.add("config.brightestday.option.whip_lash_length.desc", "Reach of the sneaking lash that pulls a target in.");
        builder.add("config.brightestday.option.whip_lash_damage", "Lash Damage");
        builder.add("config.brightestday.option.whip_lash_damage.desc", "Damage dealt by the sneaking lash.");
        builder.add("config.brightestday.group.sentry_turret", "Sentry Turret");
        builder.add("config.brightestday.option.turret_cost", "Charge Cost");
        builder.add("config.brightestday.option.turret_cost.desc", "Ring charge spent to place a sentry turret.");
        builder.add("config.brightestday.option.turret_charge", "Charge-Up");
        builder.add("config.brightestday.option.turret_charge.desc", "Ticks spent charging before the turret is placed.");
        builder.add("config.brightestday.option.turret_lifetime", "Lifetime");
        builder.add("config.brightestday.option.turret_lifetime.desc", "Ticks a turret stays active before dissolving.");
        builder.add("config.brightestday.option.turret_max", "Max Turrets");
        builder.add("config.brightestday.option.turret_max.desc", "Turrets one player can have active; the oldest is replaced.");
        builder.add("config.brightestday.option.turret_range", "Range");
        builder.add("config.brightestday.option.turret_range.desc", "Distance a turret detects and fires at hostile mobs.");
        builder.add("config.brightestday.option.turret_fire_interval", "Fire Interval");
        builder.add("config.brightestday.option.turret_fire_interval.desc", "Ticks between turret shots.");
        builder.add("config.brightestday.option.turret_bolt_damage", "Bolt Damage");
        builder.add("config.brightestday.option.turret_bolt_damage.desc", "Damage dealt by each turret bolt.");
        builder.add("brightestday.ring_power.swarm_missiles", "Swarm Missiles");
        builder.add("brightestday.ring_power.piercing_lance", "Piercing Lance");
        builder.add("brightestday.ring_power.chain_bolt", "Chain Bolt");
        builder.add("brightestday.ring_power.boomerang_disc", "Boomerang Disc");
        builder.add("brightestday.ring_power.nova_burst", "Nova Burst");
        builder.add("brightestday.ring_power.ground_slam", "Ground Slam");
        builder.add("brightestday.ring_power.rapid_barrage", "Rapid Barrage");
        builder.add("brightestday.ring_power.giant_fist", "Giant Fist");
        builder.add("brightestday.ring_power.energy_whip", "Energy Whip");
        builder.add("brightestday.ring_power.sentry_turret", "Sentry Turret");
        builder.add("message.brightestday.ground_slam_airborne", "You must be airborne to slam.");
        builder.add("message.brightestday.sentry_turret_blocked", "No room to deploy a turret there.");
        builder.add("message.brightestday.drill_place_blocked", "Nothing to drill there.");
        builder.add("config.brightestday.option.blast_cost", "Charge Cost");
        builder.add("config.brightestday.option.blast_cost.desc", "Ring charge spent on each blast.");
        builder.add("brightestday.ring_power.self_heal", "Self-Healing");
        builder.add("config.brightestday.group.self_heal", "Self-Healing");
        builder.add("config.brightestday.option.self_heal_amount", "Heal Amount");
        builder.add("config.brightestday.option.self_heal_amount.desc", "Health restored on each self-heal pulse (2 = one heart).");
        builder.add("config.brightestday.option.self_heal_interval", "Heal Interval");
        builder.add("config.brightestday.option.self_heal_interval.desc", "Ticks between self-heal pulses while hurt (20 ticks = 1 second).");
        builder.add("config.brightestday.option.self_heal_drain", "Charge Drain");
        builder.add("config.brightestday.option.self_heal_drain.desc", "Ring charge drained per second while the ring is actively healing you.");
        builder.add("brightestday.ring_power.light_orb", "Light Orb");
        builder.add("message.brightestday.light_orb_blocked", "There's no room for a light there.");
        builder.add("block.brightestday.construct_light", "Construct Light");
        builder.add("config.brightestday.group.light", "Light");
        builder.add("config.brightestday.option.light_orb_cost", "Light Orb Cost");
        builder.add("config.brightestday.option.light_orb_cost.desc", "Ring charge spent to place a light orb.");
        builder.add("config.brightestday.option.light_orb_charge", "Light Orb Charge-Up");
        builder.add("config.brightestday.option.light_orb_charge.desc", "Ticks the light orb must charge before it's placed.");
        builder.add("config.brightestday.option.light_orb_upkeep", "Light Orb Upkeep");
        builder.add("config.brightestday.option.light_orb_upkeep.desc", "Ring charge drained per second for each size step of every standing light orb.");
        builder.add("config.brightestday.option.light_orb_max", "Max Light Orbs");
        builder.add("config.brightestday.option.light_orb_max.desc", "How many light orbs a player can have at once; the oldest dissolves to make room.");
        builder.add("config.brightestday.option.spotlight_range", "Spotlight Range");
        builder.add("config.brightestday.option.spotlight_range.desc", "How far in blocks the ring's spotlight reaches.");
        builder.add("key.brightestday.concussive_blast", "Concussive Blast");
        builder.add("key.brightestday.acid_vomit", "Acid Vomit (hold)");
        builder.add("brightestday.ring_power.concussive_blast", "Concussive Blast");
        builder.add("brightestday.ring_power.acid_vomit", "Acid Vomit");
        builder.add("gui.brightestday.eyes", "Glowing Eyes");
        builder.add("gui.brightestday.eyes.white", "White");
        builder.add("gui.brightestday.eyes.corps", "Corps Color");
        builder.add("gui.brightestday.eyes.clear", "Clear");
        builder.add("gui.brightestday.eyes.hint", "Left-click to paint, right-click to erase");
        builder.add("config.brightestday.group.concussive", "Concussive Blast");
        builder.add("config.brightestday.group.acid", "Acid Vomit");
        builder.add("config.brightestday.option.concussive_range", "Range");
        builder.add("config.brightestday.option.concussive_range.desc", "How far in blocks the concussive shockwave reaches.");
        builder.add("config.brightestday.option.concussive_cone", "Cone Width");
        builder.add("config.brightestday.option.concussive_cone.desc", "Full width in degrees of the shockwave's cone.");
        builder.add("config.brightestday.option.concussive_knockback", "Knockback");
        builder.add("config.brightestday.option.concussive_knockback.desc", "How hard the shockwave throws entities at point-blank range.");
        builder.add("config.brightestday.option.concussive_damage", "Damage");
        builder.add("config.brightestday.option.concussive_damage.desc", "Damage dealt to every entity caught in the shockwave.");
        builder.add("config.brightestday.option.concussive_cost", "Charge Cost");
        builder.add("config.brightestday.option.concussive_cost.desc", "Ring charge spent on each concussive blast.");
        builder.add("config.brightestday.option.concussive_cooldown", "Cooldown");
        builder.add("config.brightestday.option.concussive_cooldown.desc", "Ticks between concussive blasts (20 ticks = 1 second).");
        builder.add("config.brightestday.option.acid_range", "Range");
        builder.add("config.brightestday.option.acid_range.desc", "How far in blocks the acid stream reaches before falling away.");
        builder.add("config.brightestday.option.acid_damage", "Damage");
        builder.add("config.brightestday.option.acid_damage.desc", "Corrosion damage dealt every half second to anything in the stream.");
        builder.add("config.brightestday.option.acid_fire_seconds", "Burn Time");
        builder.add("config.brightestday.option.acid_fire_seconds.desc", "Seconds targets keep burning after being hit by acid.");
        builder.add("config.brightestday.option.acid_armor_wear", "Armor Corrosion");
        builder.add("config.brightestday.option.acid_armor_wear.desc", "Durability each worn armor piece loses per acid hit.");
        builder.add("config.brightestday.option.acid_drain", "Charge Drain");
        builder.add("config.brightestday.option.acid_drain.desc", "Ring charge drained per second while spewing acid.");
        builder.add("config.brightestday.title", "In Brightest Day");
        builder.add("config.brightestday.category.constructs", "Constructs");
        builder.add("config.brightestday.category.sculpt", "Sculpt");
        builder.add("config.brightestday.category.ring", "Ring");
        builder.add("config.brightestday.group.blast", "Blast");
        builder.add("config.brightestday.group.beam", "Beam");
        builder.add("config.brightestday.group.heal_beam", "Healing Beam");
        builder.add("config.brightestday.group.wall", "Wall");
        builder.add("config.brightestday.group.charge", "Charge-Up");
        builder.add("config.brightestday.group.charge.desc", "How many ticks each construct must charge before it fires (20 ticks = 1 second).");
        builder.add("config.brightestday.option.blast_range", "Range");
        builder.add("config.brightestday.option.blast_range.desc", "Maximum distance in blocks the blast travels before detonating.");
        builder.add("config.brightestday.option.blast_radius", "Blast Radius");
        builder.add("config.brightestday.option.blast_radius.desc", "Radius in blocks of the blast's splash damage and knockback.");
        builder.add("config.brightestday.option.blast_direct_damage", "Direct Hit Damage");
        builder.add("config.brightestday.option.blast_direct_damage.desc", "Extra damage dealt to the entity the blast hits directly.");
        builder.add("config.brightestday.option.blast_splash_damage", "Splash Damage");
        builder.add("config.brightestday.option.blast_splash_damage.desc", "Damage at the center of the blast, falling off toward the edge of the radius.");
        builder.add("config.brightestday.option.blast_knockback", "Knockback");
        builder.add("config.brightestday.option.blast_knockback.desc", "How hard the blast pushes entities away from the impact point.");
        builder.add("config.brightestday.option.blast_explosion_power", "Explosion Power");
        builder.add("config.brightestday.option.blast_explosion_power.desc", "Strength of the explosion used to break blocks at the impact point.");
        builder.add("config.brightestday.option.blast_breaks_blocks", "Breaks Blocks");
        builder.add("config.brightestday.option.blast_breaks_blocks.desc", "Whether the blast's explosion destroys blocks.");
        builder.add("config.brightestday.option.blast_assist_cone", "Aim Assist Cone");
        builder.add("config.brightestday.option.blast_assist_cone.desc", "Angle in degrees around the crosshair within which the blast snaps to grounded targets.");
        builder.add("config.brightestday.option.blast_homing_cone", "Homing Cone");
        builder.add("config.brightestday.option.blast_homing_cone.desc", "Angle in degrees around the crosshair within which the blast homes in on airborne targets.");
        builder.add("config.brightestday.option.beam_damage", "Damage");
        builder.add("config.brightestday.option.beam_damage.desc", "Damage the beam deals to its target every quarter second.");
        builder.add("config.brightestday.option.beam_max_ticks", "Max Duration");
        builder.add("config.brightestday.option.beam_max_ticks.desc", "How many ticks the beam can be sustained before it stops (20 ticks = 1 second).");
        builder.add("config.brightestday.option.beam_drain", "Charge Drain");
        builder.add("config.brightestday.option.beam_drain.desc", "Ring charge drained per second while the beam is active.");
        builder.add("config.brightestday.option.heal_beam_amount", "Heal Amount");
        builder.add("config.brightestday.option.heal_beam_amount.desc", "Health restored to the target every half second.");
        builder.add("config.brightestday.option.heal_beam_max_ticks", "Max Duration");
        builder.add("config.brightestday.option.heal_beam_max_ticks.desc", "How many ticks the healing beam can be sustained before it stops (20 ticks = 1 second).");
        builder.add("config.brightestday.option.heal_beam_drain", "Charge Drain");
        builder.add("config.brightestday.option.heal_beam_drain.desc", "Ring charge drained per second while the healing beam is active.");
        builder.add("config.brightestday.option.wall_lifetime", "Wall Lifetime");
        builder.add("config.brightestday.option.wall_lifetime.desc", "How many ticks a wall construct lasts before collapsing (20 ticks = 1 second).");
        builder.add("config.brightestday.option.sculpt_block_cost", "Block Cost");
        builder.add("config.brightestday.option.sculpt_block_cost.desc", "Ring charge spent for each block placed while sculpting.");
        builder.add("config.brightestday.option.sculpt_max_blocks", "Max Blocks per Sculpture");
        builder.add("config.brightestday.option.sculpt_max_blocks.desc", "Maximum number of blocks a single sculpture can contain.");
        builder.add("config.brightestday.option.sculpt_max_total_blocks", "Max Total Blocks");
        builder.add("config.brightestday.option.sculpt_max_total_blocks.desc", "Maximum blocks across all of a player's sculptures; the oldest sculptures dissolve to make room.");
        builder.add("config.brightestday.option.sculpt_blocks_per_upkeep", "Blocks per Upkeep Charge");
        builder.add("config.brightestday.option.sculpt_blocks_per_upkeep.desc", "Each point of upkeep charge sustains this many sculpted blocks.");
        builder.add("config.brightestday.option.charge_blast", "Blast");
        builder.add("config.brightestday.option.charge_blast.desc", "Ticks the blast must charge before firing.");
        builder.add("config.brightestday.option.charge_beam", "Beam");
        builder.add("config.brightestday.option.charge_beam.desc", "Ticks the beam must charge before firing.");
        builder.add("config.brightestday.option.charge_heal_beam", "Healing Beam");
        builder.add("config.brightestday.option.charge_heal_beam.desc", "Ticks the healing beam must charge before firing.");
        builder.add("config.brightestday.option.charge_entity_shield", "Bubble Shield");
        builder.add("config.brightestday.option.charge_entity_shield.desc", "Ticks the bubble shield must charge before casting.");
        builder.add("config.brightestday.option.charge_area_shield", "Dome Shield");
        builder.add("config.brightestday.option.charge_area_shield.desc", "Ticks the dome shield must charge before casting.");
        builder.add("config.brightestday.option.charge_wall", "Wall");
        builder.add("config.brightestday.option.charge_wall.desc", "Ticks the wall must charge before casting.");
        builder.add("block.brightestday.hard_light", "Hard Light");
        builder.add(RingPowerRegistry.TOOL_FORGE.getTranslationKey(), "Forge");

        builder.add("item.brightestday.construct_pickaxe", "Construct Pickaxe");
        builder.add("item.brightestday.construct_axe", "Construct Axe");
        builder.add("item.brightestday.construct_battleaxe", "Construct Battleaxe");
        builder.add("item.brightestday.construct_sword", "Construct Sword");
        builder.add("item.brightestday.construct_shovel", "Construct Shovel");
        builder.add("item.brightestday.construct_hoe", "Construct Hoe");
        builder.add("item.brightestday.construct_spear", "Construct Spear");
        builder.add("item.brightestday.construct_mace", "Construct Mace");
        builder.add("message.brightestday.forged", "Forged %s");
        builder.add("message.brightestday.forge_no_room", "No room for the construct.");
        builder.add("message.brightestday.unknown_pattern", "The ring doesn't recognize that pattern.");

        builder.add(RingPowerRegistry.TRACTOR_BEAM.getTranslationKey(), "Tractor Beam");
        builder.add(RingPowerRegistry.SCAN.getTranslationKey(), "Scan");

        builder.add("message.brightestday.nothing_to_scan", "Nothing to scan.");
        builder.add("message.brightestday.scan_cooldown", "Scanner recharging: %ss");
        builder.add("message.brightestday.raise_ring_first", "Raise your ring first.");
        builder.add("key.brightestday.ability_wheel", "Ability Wheel (hold)");
        builder.add("key.brightestday.flight", "Flight");
        builder.add("key.brightestday.raise_ring", "Raise Ring");
        builder.add("key.brightestday.toggle_light", "Toggle Spotlight");
        builder.add("key.brightestday.toggle_suit", "Toggle Suit");
        builder.add("key.brightestday.toggle_mask", "Toggle Mask");
        builder.add("key.brightestday.dismiss_construct", "Dismiss Construct");
        builder.add("message.brightestday.no_constructs_to_dismiss", "No constructs to dismiss.");
        builder.add("scan.brightestday.scanning", "SCANNING");
        builder.add("scan.brightestday.type", "Type: %s");
        builder.add("scan.brightestday.health", "Health: %s / %s");
        builder.add("scan.brightestday.armor", "Armor: %s");
        builder.add("scan.brightestday.attack", "Attack: %s");
        builder.add("scan.brightestday.speed", "Speed: %s blocks/s");
        builder.add("scan.brightestday.disposition", "Disposition: %s");
        builder.add("scan.brightestday.hostile", "Hostile");
        builder.add("scan.brightestday.neutral", "Neutral");
        builder.add("scan.brightestday.passive", "Passive");
        builder.add("scan.brightestday.effects", "Effects: %s");
        builder.add("scan.brightestday.owner", "Owner: %s");
        builder.add("scan.brightestday.corps", "Corps: %s (%s%%)");
        builder.add("scan.brightestday.ringless", "No ring detected");
        builder.add("scan.brightestday.distance", "Distance: %s blocks");
        builder.add("scan.brightestday.lantern", "Lantern of the %s");
        builder.add("scan.brightestday.id", "ID: %s");
        builder.add("scan.brightestday.hardness", "Hardness: %s");
        builder.add("scan.brightestday.blast_resistance", "Blast Resistance: %s");
        builder.add("scan.brightestday.tool", "Tool: %s");
        builder.add("scan.brightestday.tool.pickaxe", "Pickaxe");
        builder.add("scan.brightestday.tool.axe", "Axe");
        builder.add("scan.brightestday.tool.shovel", "Shovel");
        builder.add("scan.brightestday.tool.hoe", "Hoe");
        builder.add("scan.brightestday.tool.hand", "Any");
        builder.add("scan.brightestday.required", " (required)");
        builder.add("scan.brightestday.light", "Light: %s");
        builder.add("scan.brightestday.position", "Position: %s, %s, %s");

        builder.add("key.category.brightestday.main", "In Brightest Day");
        builder.add("key.brightestday.cycle_construct", "Construct Wheel (hold)");
    }
}

package dev.amble.core.ringpowers;

import dev.amble.core.ringpowers.constructs.AreaShieldConstruct;
import dev.amble.core.ringpowers.constructs.BulwarkConstruct;
import dev.amble.core.ringpowers.constructs.ConstructRingConstruct;
import dev.amble.core.ringpowers.constructs.GravityBallConstruct;
import dev.amble.core.ringpowers.constructs.GravityRepulsorConstruct;
import dev.amble.core.ringpowers.constructs.BeamConstruct;
import dev.amble.core.ringpowers.constructs.BlastConstruct;
import dev.amble.core.ringpowers.constructs.ConstructBoatConstruct;
import dev.amble.core.ringpowers.constructs.ConstructHorseConstruct;
import dev.amble.core.ringpowers.constructs.ContainmentSphereConstruct;
import dev.amble.core.ringpowers.constructs.RingCompassConstruct;
import dev.amble.core.ringpowers.constructs.InsigniaConstruct;
import dev.amble.core.ringpowers.constructs.BloodHuntConstruct;
import dev.amble.core.ringpowers.constructs.BoomerangDiscConstruct;
import dev.amble.core.ringpowers.constructs.ChainBoltConstruct;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.ringpowers.constructs.CrystalPrisonConstruct;
import dev.amble.core.ringpowers.constructs.DrillConstruct;
import dev.amble.core.ringpowers.constructs.EnergyWhipConstruct;
import dev.amble.core.ringpowers.constructs.EntityShieldConstruct;
import dev.amble.core.ringpowers.constructs.GiantFistConstruct;
import dev.amble.core.ringpowers.constructs.GliderConstruct;
import dev.amble.core.ringpowers.constructs.GrapplingHookConstruct;
import dev.amble.core.ringpowers.constructs.GroundSlamConstruct;
import dev.amble.core.ringpowers.constructs.HealBeamConstruct;
import dev.amble.core.ringpowers.constructs.LightOrbConstruct;
import dev.amble.core.ringpowers.constructs.LumberjackConstruct;
import dev.amble.core.ringpowers.constructs.MegaphoneConstruct;
import dev.amble.core.ringpowers.constructs.NovaBurstConstruct;
import dev.amble.core.ringpowers.constructs.OreProbeConstruct;
import dev.amble.core.ringpowers.constructs.PiercingLanceConstruct;
import dev.amble.core.ringpowers.constructs.PlasmaBurstConstruct;
import dev.amble.core.ringpowers.constructs.RapidBarrageConstruct;
import dev.amble.core.ringpowers.constructs.SculptConstruct;
import dev.amble.core.ringpowers.constructs.SentryTurretConstruct;
import dev.amble.core.ringpowers.constructs.SwarmMissilesConstruct;
import dev.amble.core.ringpowers.constructs.ToolForgeConstruct;
import dev.amble.core.ringpowers.constructs.WallConstruct;
import dev.amble.core.ringpowers.impl.AcidRingPower;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.BerserkRingPower;
import dev.amble.core.ringpowers.impl.CommsRingPower;
import dev.amble.core.ringpowers.impl.ConcussiveRingPower;
import dev.amble.core.ringpowers.impl.ConversionRingPower;
import dev.amble.core.ringpowers.impl.GatherRingPower;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import dev.amble.core.ringpowers.impl.LightRingPower;
import dev.amble.core.ringpowers.impl.ScanRingPower;
import dev.amble.core.ringpowers.impl.SelfHealRingPower;
import dev.amble.core.ringpowers.impl.TractorBeamRingPower;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

public final class RingPowerRegistry {
    private static final Map<Identifier, RingPower<?>> REGISTRY = new LinkedHashMap<>();

    public static final FlightRingPower FLIGHT = register(new FlightRingPower());
    public static final ArmedRingPower ARMED = register(new ArmedRingPower());
    public static final LightRingPower LIGHT = register(new LightRingPower());
    public static final BlastConstruct BLAST = register(new BlastConstruct());
    public static final BeamConstruct BEAM = register(new BeamConstruct());
    public static final HealBeamConstruct HEAL_BEAM = register(new HealBeamConstruct());
    public static final EntityShieldConstruct ENTITY_SHIELD = register(new EntityShieldConstruct());
    public static final AreaShieldConstruct AREA_SHIELD = register(new AreaShieldConstruct());
    public static final BulwarkConstruct BULWARK = register(new BulwarkConstruct());
    public static final ConstructRingConstruct CONSTRUCT_RING = register(new ConstructRingConstruct());
    public static final GravityBallConstruct GRAVITY_BALL = register(new GravityBallConstruct());
    public static final GravityRepulsorConstruct GRAVITY_REPULSOR = register(new GravityRepulsorConstruct());
    public static final WallConstruct WALL = register(new WallConstruct());
    public static final SculptConstruct SCULPT = register(new SculptConstruct());
    public static final LightOrbConstruct LIGHT_ORB = register(new LightOrbConstruct());
    public static final DrillConstruct DRILL = register(new DrillConstruct());
    public static final GliderConstruct GLIDER = register(new GliderConstruct());
    public static final GrapplingHookConstruct GRAPPLING_HOOK = register(new GrapplingHookConstruct());
    public static final LumberjackConstruct LUMBERJACK = register(new LumberjackConstruct());
    public static final OreProbeConstruct ORE_PROBE = register(new OreProbeConstruct());
    public static final SwarmMissilesConstruct SWARM_MISSILES = register(new SwarmMissilesConstruct());
    public static final PiercingLanceConstruct PIERCING_LANCE = register(new PiercingLanceConstruct());
    public static final ChainBoltConstruct CHAIN_BOLT = register(new ChainBoltConstruct());
    public static final BoomerangDiscConstruct BOOMERANG_DISC = register(new BoomerangDiscConstruct());
    public static final NovaBurstConstruct NOVA_BURST = register(new NovaBurstConstruct());
    public static final GroundSlamConstruct GROUND_SLAM = register(new GroundSlamConstruct());
    public static final RapidBarrageConstruct RAPID_BARRAGE = register(new RapidBarrageConstruct());
    public static final GiantFistConstruct GIANT_FIST = register(new GiantFistConstruct());
    public static final EnergyWhipConstruct ENERGY_WHIP = register(new EnergyWhipConstruct());
    public static final SentryTurretConstruct SENTRY_TURRET = register(new SentryTurretConstruct());
    public static final ToolForgeConstruct TOOL_FORGE = register(new ToolForgeConstruct());
    public static final PlasmaBurstConstruct PLASMA_BURST = register(new PlasmaBurstConstruct());
    public static final CrystalPrisonConstruct CRYSTAL_PRISON = register(new CrystalPrisonConstruct());
    public static final BloodHuntConstruct BLOOD_HUNT = register(new BloodHuntConstruct());
    public static final MegaphoneConstruct MEGAPHONE = register(new MegaphoneConstruct());
    public static final ConstructHorseConstruct CONSTRUCT_HORSE = register(new ConstructHorseConstruct());
    public static final ConstructBoatConstruct CONSTRUCT_BOAT = register(new ConstructBoatConstruct());
    public static final ContainmentSphereConstruct CONTAINMENT_SPHERE = register(new ContainmentSphereConstruct());
    public static final RingCompassConstruct RING_COMPASS = register(new RingCompassConstruct());
    public static final InsigniaConstruct INSIGNIA = register(new InsigniaConstruct());
    public static final TractorBeamRingPower TRACTOR_BEAM = register(new TractorBeamRingPower());
    public static final ScanRingPower SCAN = register(new ScanRingPower());
    public static final ConcussiveRingPower CONCUSSIVE = register(new ConcussiveRingPower());
    public static final AcidRingPower ACID = register(new AcidRingPower());
    public static final SelfHealRingPower SELF_HEAL = register(new SelfHealRingPower());
    public static final ConversionRingPower CONVERSION = register(new ConversionRingPower());
    public static final BerserkRingPower BERSERK = register(new BerserkRingPower());
    public static final CommsRingPower COMMS = register(new CommsRingPower());
    public static final GatherRingPower GATHER = register(new GatherRingPower());

    public static <T extends RingPower<?>> T register(T power) {
        if (REGISTRY.putIfAbsent(power.id(), power) != null) {
            throw new IllegalStateException("Duplicate ring power id: " + power.id());
        }
        return power;
    }

    public static Optional<RingPower<?>> get(Identifier id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    public static Collection<RingPower<?>> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static List<RingPower<?>> forCorps(LanternCorps corps, @Nullable LanternCorps borrowed) {
        return forCorps(corps, borrowed, Set.of());
    }

    public static List<RingPower<?>> forCorps(LanternCorps corps, @Nullable LanternCorps borrowed, Set<LanternCorps> mimicked) {
        return REGISTRY.values().stream()
                .filter(power -> power.isAvailableTo(corps)
                        || borrowed != null && power.isAvailableTo(borrowed)
                        || power instanceof ConstructRingPower && mimicked.stream().anyMatch(power::isAvailableTo))
                .toList();
    }

    public static List<RingPower<?>> forCorps(LanternCorps corps) {
        return REGISTRY.values().stream().filter(power -> power.isAvailableTo(corps)).toList();
    }

    public static void init() {}

    private RingPowerRegistry() {}
}

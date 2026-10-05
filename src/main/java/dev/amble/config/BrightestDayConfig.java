package dev.amble.config;

import dev.amble.BrightestDay;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;

public class BrightestDayConfig {
    public static final ConfigClassHandler<BrightestDayConfig> HANDLER = ConfigClassHandler.createBuilder(BrightestDayConfig.class)
            .id(BrightestDay.id("config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve(BrightestDay.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    private static final int CONFIG_VERSION = 2;

    @SerialEntry
    public int configVersion = 0;

    @SerialEntry
    public int blastCost = 60;
    @SerialEntry
    public double blastRange = 48.0;
    @SerialEntry
    public double blastRadius = 5.0;
    @SerialEntry
    public float blastDirectDamage = 6.0F;
    @SerialEntry
    public float blastSplashDamage = 4.0F;
    @SerialEntry
    public double blastKnockback = 1.8;
    @SerialEntry
    public float blastExplosionPower = 2.5F;
    @SerialEntry
    public boolean blastBreaksBlocks = true;
    @SerialEntry
    public double blastAssistConeDegrees = 6.0;
    @SerialEntry
    public double blastHomingConeDegrees = 22.0;

    @SerialEntry
    public float beamDamage = 5.0F;
    @SerialEntry
    public int beamMaxTicks = 140;
    @SerialEntry
    public int beamDrainPerSecond = 20;

    @SerialEntry
    public float healBeamAmount = 1.0F;
    @SerialEntry
    public int healBeamMaxTicks = 200;
    @SerialEntry
    public int healBeamDrainPerSecond = 10;

    @SerialEntry
    public int wallLifetimeTicks = 400;

    @SerialEntry
    public int sculptBlockCost = 1;
    @SerialEntry
    public int sculptMaxBlocks = 4096;
    @SerialEntry
    public int sculptMaxTotalBlocks = 16384;
    @SerialEntry
    public int sculptBlocksPerUpkeep = 128;

    @SerialEntry
    public int blastChargeTicks = 25;
    @SerialEntry
    public int beamChargeTicks = 12;
    @SerialEntry
    public int healBeamChargeTicks = 10;
    @SerialEntry
    public int entityShieldChargeTicks = 6;
    @SerialEntry
    public int areaShieldChargeTicks = 8;
    @SerialEntry
    public int wallChargeTicks = 6;

    @SerialEntry
    public double concussiveRange = 6.0;
    @SerialEntry
    public double concussiveConeDegrees = 45.0;
    @SerialEntry
    public double concussiveKnockback = 2.2;
    @SerialEntry
    public float concussiveDamage = 3.0F;
    @SerialEntry
    public int concussiveCost = 25;
    @SerialEntry
    public int concussiveCooldownTicks = 20;

    @SerialEntry
    public double acidRange = 6.0;
    @SerialEntry
    public float acidDamage = 2.0F;
    @SerialEntry
    public int acidFireSeconds = 3;
    @SerialEntry
    public int acidArmorWear = 2;
    @SerialEntry
    public int acidDrainPerSecond = 15;

    @SerialEntry
    public float selfHealAmount = 1.0F;
    @SerialEntry
    public int selfHealIntervalTicks = 10;
    @SerialEntry
    public int selfHealDrainPerSecond = 2;

    @SerialEntry
    public int lightOrbCost = 20;
    @SerialEntry
    public int lightOrbChargeTicks = 6;
    @SerialEntry
    public int lightOrbUpkeepPerSize = 1;
    @SerialEntry
    public int lightOrbMaxCount = 8;
    @SerialEntry
    public double spotlightRange = 48.0;

    @SerialEntry
    public int swarmCost = 120;
    @SerialEntry
    public int swarmChargeTicks = 20;
    @SerialEntry
    public int swarmDarts = 6;
    @SerialEntry
    public float swarmDamage = 3.0F;
    @SerialEntry
    public double swarmRange = 32.0;
    @SerialEntry
    public double swarmConeDegrees = 70.0;
    @SerialEntry
    public double swarmKnockback = 0.25;

    @SerialEntry
    public int lanceCost = 200;
    @SerialEntry
    public int lanceChargeTicks = 35;
    @SerialEntry
    public float lanceDamage = 16.0F;
    @SerialEntry
    public double lanceRange = 64.0;
    @SerialEntry
    public double lanceKnockback = 0.3;

    @SerialEntry
    public int chainCost = 140;
    @SerialEntry
    public int chainChargeTicks = 20;
    @SerialEntry
    public float chainDamage = 8.0F;
    @SerialEntry
    public double chainRange = 32.0;
    @SerialEntry
    public int chainMaxJumps = 5;
    @SerialEntry
    public float chainFalloff = 0.85F;
    @SerialEntry
    public double chainJumpRange = 8.0;
    @SerialEntry
    public double chainKnockback = 0.2;

    @SerialEntry
    public int discCost = 90;
    @SerialEntry
    public int discChargeTicks = 15;
    @SerialEntry
    public float discDamage = 5.0F;
    @SerialEntry
    public double discRange = 20.0;
    @SerialEntry
    public double discKnockback = 0.4;

    @SerialEntry
    public int novaCost = 400;
    @SerialEntry
    public int novaChargeTicks = 30;
    @SerialEntry
    public float novaDamage = 10.0F;
    @SerialEntry
    public double novaRadius = 7.0;
    @SerialEntry
    public double novaKnockback = 2.2;

    @SerialEntry
    public int slamCost = 200;
    @SerialEntry
    public int slamChargeTicks = 12;
    @SerialEntry
    public float slamBaseDamage = 6.0F;
    @SerialEntry
    public float slamDamagePerBlock = 0.25F;
    @SerialEntry
    public float slamMaxDamage = 14.0F;
    @SerialEntry
    public double slamMinRadius = 5.0;
    @SerialEntry
    public double slamMaxRadius = 9.0;
    @SerialEntry
    public double slamRadiusPerBlock = 0.2;
    @SerialEntry
    public double slamLaunch = 1.1;

    @SerialEntry
    public int barrageCost = 50;
    @SerialEntry
    public int barrageChargeTicks = 6;
    @SerialEntry
    public float barrageDamage = 2.5F;
    @SerialEntry
    public double barrageRange = 28.0;
    @SerialEntry
    public int barrageFireInterval = 5;
    @SerialEntry
    public int barrageMaxTicks = 200;
    @SerialEntry
    public int barrageDrainPerSecond = 40;
    @SerialEntry
    public double barrageSpreadDegrees = 1.8;

    @SerialEntry
    public int fistCost = 120;
    @SerialEntry
    public int fistChargeTicks = 15;
    @SerialEntry
    public float fistDamage = 12.0F;
    @SerialEntry
    public double fistRange = 7.0;
    @SerialEntry
    public double fistKnockback = 2.8;
    @SerialEntry
    public double fistImpactRadius = 2.5;
    @SerialEntry
    public float fistImpactDamage = 4.0F;

    @SerialEntry
    public int whipCost = 70;
    @SerialEntry
    public int whipChargeTicks = 8;
    @SerialEntry
    public float whipDamage = 6.0F;
    @SerialEntry
    public double whipLength = 8.0;
    @SerialEntry
    public float whipArcDegrees = 120.0F;
    @SerialEntry
    public double whipKnockback = 1.6;
    @SerialEntry
    public double whipLashLength = 10.0;
    @SerialEntry
    public float whipLashDamage = 3.0F;

    @SerialEntry
    public int turretCost = 250;
    @SerialEntry
    public int turretChargeTicks = 20;
    @SerialEntry
    public int turretLifetimeTicks = 400;
    @SerialEntry
    public int turretMaxCount = 2;
    @SerialEntry
    public double turretRange = 16.0;
    @SerialEntry
    public int turretFireInterval = 20;
    @SerialEntry
    public float turretBoltDamage = 3.0F;

    @SerialEntry
    public int drillCost = 25;
    @SerialEntry
    public int drillChargeTicks = 0;
    @SerialEntry
    public float drillSpeed = 12.0F;
    @SerialEntry
    public int drillDrainPerSecond = 15;
    @SerialEntry
    public int drillDrainPerSize = 10;
    @SerialEntry
    public int drillPlacedMaxCount = 2;
    @SerialEntry
    public int drillPlacedDistance = 24;
    @SerialEntry
    public int drillPlacedLifetimeTicks = 600;
    @SerialEntry
    public int drillPlacedTravelTicks = 4;

    @SerialEntry
    public double flightBoostMultiplier = 2.5;
    @SerialEntry
    public double flightMaxBoostSpeed = 5.0;
    @SerialEntry
    public int flightDrainPerSecond = 8;
    @SerialEntry
    public int flightBoostDrainPerSecond = 18;
    @SerialEntry
    public boolean showFlightSpeedometer = true;
    @SerialEntry
    public boolean showFlightTrails = true;
    @SerialEntry
    public boolean aileronRolls = true;
    @SerialEntry
    public double aileronRollDodge = 1.4;

    @SerialEntry
    public double synergyLinkRadius = 96.0;
    @SerialEntry
    public double synergyDreadRadius = 16.0;

    @SerialEntry
    public int ringLoyaltyDeaths = 3;
    @SerialEntry
    public double ringLoyaltySearchRadius = 512.0;

    @SerialEntry
    public double teamInviteRange = 64.0;
    @SerialEntry
    public int teamMaxSize = 6;

    @SerialEntry
    public double spaceEntryHeight = 1000.0;
    @SerialEntry
    public double spaceArrivalHeight = 330.0;

    @SerialEntry
    public int gliderCost = 150;
    @SerialEntry
    public int gliderChargeTicks = 15;
    @SerialEntry
    public int gliderDurationTicks = 1200;
    @SerialEntry
    public double gliderRange = 48.0;
    @SerialEntry
    public double gliderAssistConeDegrees = 12.0;
    @SerialEntry
    public double gliderMobFallSpeed = 0.12;

    public static BrightestDayConfig get() {
        return HANDLER.instance();
    }

    public static void load() {
        HANDLER.load();
        migrate(HANDLER.instance());
    }

    private static void migrate(BrightestDayConfig config) {
        if (config.configVersion >= CONFIG_VERSION) return;

        BrightestDayConfig defaults = new BrightestDayConfig();
        if (config.configVersion < 1) {
            config.blastCost = Math.min(config.blastCost, defaults.blastCost);
            config.novaCost = Math.max(config.novaCost, defaults.novaCost);
            config.flightDrainPerSecond = Math.min(config.flightDrainPerSecond, defaults.flightDrainPerSecond);
            config.flightBoostDrainPerSecond = Math.min(config.flightBoostDrainPerSecond, defaults.flightBoostDrainPerSecond);
        }
        if (config.configVersion < 2 && config.spaceEntryHeight == 384.0) config.spaceEntryHeight = defaults.spaceEntryHeight;
        config.configVersion = CONFIG_VERSION;
        HANDLER.save();
    }
}

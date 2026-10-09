package dev.amble.config;

import dev.amble.BrightestDay;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;

public class BrightestDayConfig extends Config {
    private static BrightestDayConfig instance;

    public BrightestDayConfig() {
        super(BrightestDay.id("server"), "", BrightestDay.MOD_ID, "server");
    }

    public ConfigGroup blast = new ConfigGroup("blast");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int blastCost = 150;
    @ValidatedDouble.Restrict(min = 8.0, max = 128.0)
    public double blastRange = 48.0;
    @ValidatedDouble.Restrict(min = 1.0, max = 16.0)
    public double blastRadius = 5.0;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float blastDirectDamage = 6.0F;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float blastSplashDamage = 3.0F;
    @ValidatedDouble.Restrict(min = 0.0, max = 5.0)
    public double blastKnockback = 1.8;
    @ValidatedFloat.Restrict(min = 0.0F, max = 8.0F)
    public float blastExplosionPower = 2.5F;
    public boolean blastBreaksBlocks = true;
    @ValidatedDouble.Restrict(min = 0.0, max = 45.0)
    public double blastAssistConeDegrees = 6.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 90.0)
    @ConfigGroup.Pop
    public double blastHomingConeDegrees = 22.0;

    public ConfigGroup beam = new ConfigGroup("beam");
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    public float beamDamage = 5.0F;
    @ValidatedInt.Restrict(min = 20, max = 600)
    public int beamMaxTicks = 140;
    @ValidatedInt.Restrict(min = 0, max = 200)
    public int beamDrainPerSecond = 30;
    @ValidatedDouble.Restrict(min = 8.0, max = 256.0)
    @ConfigGroup.Pop
    public double beamRange = 96.0;

    public ConfigGroup healBeam = new ConfigGroup("healBeam");
    @ValidatedFloat.Restrict(min = 0.0F, max = 10.0F)
    public float healBeamAmount = 1.0F;
    @ValidatedInt.Restrict(min = 20, max = 1200)
    public int healBeamMaxTicks = 200;
    @ValidatedInt.Restrict(min = 0, max = 200)
    @ConfigGroup.Pop
    public int healBeamDrainPerSecond = 10;

    public ConfigGroup wall = new ConfigGroup("wall");
    @ValidatedInt.Restrict(min = 0, max = 100)
    @ConfigGroup.Pop
    public int wallDrainPerSecond = 1;

    public ConfigGroup sculpt = new ConfigGroup("sculpt");
    @ValidatedInt.Restrict(min = 0, max = 20)
    public int sculptBlockCost = 1;
    @ValidatedInt.Restrict(min = 1, max = 65536, type = ValidatedNumber.WidgetType.TEXTBOX)
    public int sculptMaxBlocks = 4096;
    @ValidatedInt.Restrict(min = 1, max = 262144, type = ValidatedNumber.WidgetType.TEXTBOX)
    public int sculptMaxTotalBlocks = 16384;
    @ValidatedInt.Restrict(min = 1, max = 65536, type = ValidatedNumber.WidgetType.TEXTBOX)
    @ConfigGroup.Pop
    public int sculptBlocksPerUpkeep = 128;

    public ConfigGroup charge = new ConfigGroup("charge");
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int blastChargeTicks = 25;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int beamChargeTicks = 12;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int healBeamChargeTicks = 10;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int entityShieldChargeTicks = 6;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int areaShieldChargeTicks = 8;
    @ValidatedInt.Restrict(min = 0, max = 100)
    @ConfigGroup.Pop
    public int wallChargeTicks = 6;

    public ConfigGroup concussive = new ConfigGroup("concussive");
    @ValidatedDouble.Restrict(min = 1.0, max = 24.0)
    public double concussiveRange = 6.0;
    @ValidatedDouble.Restrict(min = 5.0, max = 180.0)
    public double concussiveConeDegrees = 45.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 6.0)
    public double concussiveKnockback = 2.2;
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    public float concussiveDamage = 3.0F;
    @ValidatedInt.Restrict(min = 0, max = 500)
    public int concussiveCost = 100;
    @ValidatedInt.Restrict(min = 0, max = 200)
    @ConfigGroup.Pop
    public int concussiveCooldownTicks = 20;

    public ConfigGroup flight = new ConfigGroup("flight");
    @ValidatedDouble.Restrict(min = 1.0, max = 5.0)
    public double flightBoostMultiplier = 2.5;
    @ValidatedDouble.Restrict(min = 1.0, max = 10.0)
    public double flightMaxBoostSpeed = 5.0;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int flightDrainPerSecond = 2;
    @ValidatedInt.Restrict(min = 0, max = 200)
    public int flightBoostDrainPerSecond = 5;
    public boolean aileronRolls = true;
    @ValidatedDouble.Restrict(min = 0.0, max = 16.0)
    @ConfigGroup.Pop
    public double aileronRollDistance = 8.0;

    public ConfigGroup synergy = new ConfigGroup("synergy");
    @ValidatedDouble.Restrict(min = 8.0, max = 256.0)
    public double synergyLinkRadius = 96.0;
    @ValidatedDouble.Restrict(min = 4.0, max = 128.0)
    @ConfigGroup.Pop
    public double synergyDreadRadius = 16.0;

    public ConfigGroup loyalty = new ConfigGroup("loyalty");
    @ValidatedInt.Restrict(min = 0, max = 20)
    public int ringLoyaltyDeaths = 0;
    @ValidatedDouble.Restrict(min = 16.0, max = 4096.0)
    public double ringLoyaltySearchRadius = 512.0;
    @ValidatedInt.Restrict(min = 0, max = 3600)
    public int ringRecallCooldownSeconds = 300;
    @ValidatedInt.Restrict(min = 1, max = 365)
    public int ringBondExpiryDays = 14;
    @ValidatedInt.Restrict(min = 1, max = 100)
    @ConfigGroup.Pop
    public int ringSyncDays = 14;

    public ConfigGroup team = new ConfigGroup("team");
    @ValidatedDouble.Restrict(min = 8.0, max = 256.0)
    public double teamInviteRange = 64.0;
    @ValidatedInt.Restrict(min = 2, max = 16)
    @ConfigGroup.Pop
    public int teamMaxSize = 6;

    public ConfigGroup glider = new ConfigGroup("glider");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int gliderCost = 150;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int gliderChargeTicks = 15;
    @ValidatedInt.Restrict(min = 20, max = 12000, type = ValidatedNumber.WidgetType.TEXTBOX)
    public int gliderDurationTicks = 1200;
    @ValidatedDouble.Restrict(min = 4.0, max = 128.0)
    public double gliderRange = 48.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 45.0)
    public double gliderAssistConeDegrees = 12.0;
    @ValidatedDouble.Restrict(min = 0.02, max = 1.0)
    @ConfigGroup.Pop
    public double gliderMobFallSpeed = 0.12;

    public ConfigGroup drill = new ConfigGroup("drill");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int drillCost = 25;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int drillChargeTicks = 0;
    @ValidatedFloat.Restrict(min = 1.0F, max = 60.0F)
    public float drillSpeed = 12.0F;
    @ValidatedInt.Restrict(min = 0, max = 200)
    public int drillDrainPerSecond = 15;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int drillDrainPerSize = 10;
    @ValidatedInt.Restrict(min = 1, max = 16)
    public int drillPlacedMaxCount = 2;
    @ValidatedInt.Restrict(min = 1, max = 128)
    public int drillPlacedDistance = 24;
    @ValidatedInt.Restrict(min = 20, max = 6000)
    public int drillPlacedLifetimeTicks = 600;
    @ValidatedInt.Restrict(min = 1, max = 40)
    @ConfigGroup.Pop
    public int drillPlacedTravelTicks = 4;

    public ConfigGroup light = new ConfigGroup("light");
    @ValidatedInt.Restrict(min = 0, max = 500)
    public int lightOrbCost = 20;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int lightOrbChargeTicks = 6;
    @ValidatedInt.Restrict(min = 0, max = 50)
    public int lightOrbUpkeepPerSize = 1;
    @ValidatedInt.Restrict(min = 1, max = 64)
    public int lightOrbMaxCount = 8;
    @ValidatedDouble.Restrict(min = 8.0, max = 128.0)
    @ConfigGroup.Pop
    public double spotlightRange = 48.0;

    public ConfigGroup acid = new ConfigGroup("acid");
    @ValidatedDouble.Restrict(min = 1.0, max = 16.0)
    public double acidRange = 6.0;
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    public float acidDamage = 2.0F;
    @ValidatedInt.Restrict(min = 0, max = 20)
    public int acidFireSeconds = 3;
    @ValidatedInt.Restrict(min = 0, max = 20)
    public int acidArmorWear = 2;
    @ValidatedInt.Restrict(min = 0, max = 200)
    @ConfigGroup.Pop
    public int acidDrainPerSecond = 15;

    public ConfigGroup selfHeal = new ConfigGroup("selfHeal");
    @ValidatedFloat.Restrict(min = 0.0F, max = 10.0F)
    public float selfHealAmount = 1.0F;
    @ValidatedInt.Restrict(min = 1, max = 200)
    public int selfHealIntervalTicks = 10;
    @ValidatedInt.Restrict(min = 0, max = 100)
    @ConfigGroup.Pop
    public int selfHealDrainPerSecond = 2;

    public ConfigGroup swarmMissiles = new ConfigGroup("swarmMissiles");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int swarmCost = 150;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int swarmChargeTicks = 20;
    @ValidatedInt.Restrict(min = 1, max = 16)
    public int swarmDarts = 6;
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    public float swarmDamage = 4.0F;
    @ValidatedDouble.Restrict(min = 4.0, max = 96.0)
    public double swarmRange = 32.0;
    @ValidatedDouble.Restrict(min = 5.0, max = 180.0)
    public double swarmConeDegrees = 70.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 3.0)
    @ConfigGroup.Pop
    public double swarmKnockback = 0.25;

    public ConfigGroup piercingLance = new ConfigGroup("piercingLance");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int lanceCost = 250;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int lanceChargeTicks = 28;
    @ValidatedFloat.Restrict(min = 0.0F, max = 60.0F)
    public float lanceDamage = 30.0F;
    @ValidatedDouble.Restrict(min = 8.0, max = 128.0)
    public double lanceRange = 64.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 3.0)
    @ConfigGroup.Pop
    public double lanceKnockback = 0.3;

    public ConfigGroup chainBolt = new ConfigGroup("chainBolt");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int chainCost = 150;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int chainChargeTicks = 20;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float chainDamage = 8.0F;
    @ValidatedDouble.Restrict(min = 4.0, max = 96.0)
    public double chainRange = 32.0;
    @ValidatedInt.Restrict(min = 0, max = 14)
    public int chainMaxJumps = 5;
    @ValidatedFloat.Restrict(min = 0.1F, max = 1.0F)
    public float chainFalloff = 0.85F;
    @ValidatedDouble.Restrict(min = 1.0, max = 24.0)
    public double chainJumpRange = 8.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 3.0)
    @ConfigGroup.Pop
    public double chainKnockback = 0.2;

    public ConfigGroup boomerangDisc = new ConfigGroup("boomerangDisc");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int discCost = 100;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int discChargeTicks = 15;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float discDamage = 11.0F;
    @ValidatedDouble.Restrict(min = 4.0, max = 64.0)
    public double discRange = 20.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 3.0)
    @ConfigGroup.Pop
    public double discKnockback = 0.4;

    public ConfigGroup novaBurst = new ConfigGroup("novaBurst");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int novaCost = 250;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int novaChargeTicks = 30;
    @ValidatedFloat.Restrict(min = 0.0F, max = 60.0F)
    public float novaDamage = 14.0F;
    @ValidatedDouble.Restrict(min = 1.0, max = 24.0)
    public double novaRadius = 7.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 6.0)
    @ConfigGroup.Pop
    public double novaKnockback = 2.2;

    public ConfigGroup groundSlam = new ConfigGroup("groundSlam");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int slamCost = 250;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int slamChargeTicks = 12;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float slamBaseDamage = 9.0F;
    @ValidatedFloat.Restrict(min = 0.0F, max = 2.0F)
    public float slamDamagePerBlock = 0.25F;
    @ValidatedFloat.Restrict(min = 0.0F, max = 60.0F)
    public float slamMaxDamage = 18.0F;
    @ValidatedDouble.Restrict(min = 1.0, max = 24.0)
    public double slamMinRadius = 5.0;
    @ValidatedDouble.Restrict(min = 1.0, max = 32.0)
    public double slamMaxRadius = 9.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 1.0)
    public double slamRadiusPerBlock = 0.2;
    @ValidatedDouble.Restrict(min = 0.0, max = 4.0)
    @ConfigGroup.Pop
    public double slamLaunch = 1.1;

    public ConfigGroup rapidBarrage = new ConfigGroup("rapidBarrage");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int barrageCost = 120;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int barrageChargeTicks = 6;
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    public float barrageDamage = 2.5F;
    @ValidatedDouble.Restrict(min = 4.0, max = 96.0)
    public double barrageRange = 28.0;
    @ValidatedInt.Restrict(min = 1, max = 40)
    public int barrageFireInterval = 5;
    @ValidatedInt.Restrict(min = 20, max = 1200)
    public int barrageMaxTicks = 200;
    @ValidatedInt.Restrict(min = 0, max = 200)
    public int barrageDrainPerSecond = 30;
    @ValidatedDouble.Restrict(min = 0.0, max = 15.0)
    @ConfigGroup.Pop
    public double barrageSpreadDegrees = 1.8;

    public ConfigGroup giantFist = new ConfigGroup("giantFist");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int fistCost = 150;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int fistChargeTicks = 15;
    @ValidatedFloat.Restrict(min = 0.0F, max = 60.0F)
    public float fistDamage = 18.0F;
    @ValidatedDouble.Restrict(min = 1.0, max = 24.0)
    public double fistRange = 7.0;
    @ValidatedDouble.Restrict(min = 0.0, max = 6.0)
    public double fistKnockback = 2.8;
    @ValidatedDouble.Restrict(min = 0.0, max = 8.0)
    public double fistImpactRadius = 2.5;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    @ConfigGroup.Pop
    public float fistImpactDamage = 4.0F;

    public ConfigGroup energyWhip = new ConfigGroup("energyWhip");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int whipCost = 100;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int whipChargeTicks = 8;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    public float whipDamage = 11.0F;
    @ValidatedDouble.Restrict(min = 2.0, max = 24.0)
    public double whipLength = 8.0;
    @ValidatedFloat.Restrict(min = 10.0F, max = 360.0F)
    public float whipArcDegrees = 120.0F;
    @ValidatedDouble.Restrict(min = 0.0, max = 6.0)
    public double whipKnockback = 1.6;
    @ValidatedDouble.Restrict(min = 2.0, max = 32.0)
    public double whipLashLength = 10.0;
    @ValidatedFloat.Restrict(min = 0.0F, max = 40.0F)
    @ConfigGroup.Pop
    public float whipLashDamage = 3.0F;

    public ConfigGroup sentryTurret = new ConfigGroup("sentryTurret");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int turretCost = 250;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int turretChargeTicks = 20;
    @ValidatedInt.Restrict(min = 20, max = 6000)
    public int turretLifetimeTicks = 400;
    @ValidatedInt.Restrict(min = 1, max = 16)
    public int turretMaxCount = 2;
    @ValidatedDouble.Restrict(min = 4.0, max = 64.0)
    public double turretRange = 16.0;
    @ValidatedInt.Restrict(min = 1, max = 200)
    public int turretFireInterval = 20;
    @ValidatedFloat.Restrict(min = 0.0F, max = 20.0F)
    @ConfigGroup.Pop
    public float turretBoltDamage = 3.0F;

    public ConfigGroup mounts = new ConfigGroup("mounts");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int horseCost = 60;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int horseDrainPerSecond = 2;
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int boatCost = 40;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int boatDrainPerSecond = 1;
    @ValidatedInt.Restrict(min = 0, max = 100)
    @ConfigGroup.Pop
    public int mountChargeTicks = 10;

    public ConfigGroup visuals = new ConfigGroup("visuals");
    @ValidatedInt.Restrict(min = 1, max = 120)
    @ConfigGroup.Pop
    public int insigniaDrainSeconds = 5;

    public ConfigGroup sphere = new ConfigGroup("sphere");
    @ValidatedInt.Restrict(min = 0, max = 1000)
    public int sphereCostPerRadius = 40;
    @ValidatedInt.Restrict(min = 0, max = 200)
    public int sphereDrainPerRadius = 3;
    @ValidatedInt.Restrict(min = 0, max = 100)
    public int sphereChargeTicks = 15;
    @ValidatedDouble.Restrict(min = 0.1, max = 4.0)
    @ConfigGroup.Pop
    public double breakFreeSpeed = 0.6;

    public ConfigGroup oath = new ConfigGroup("oath");
    public boolean oathRecognition = true;
    @ConfigGroup.Pop
    public String oathModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    public ConfigGroup plasma = new ConfigGroup("plasma");
    public int plasmaCost = 250;
    @ConfigGroup.Pop
    public int plasmaChargeTicks = 40;

    public ConfigGroup crystal = new ConfigGroup("crystal");
    public int crystalCost = 150;
    public int crystalChargeTicks = 15;
    public int crystalPrisonTicks = 80;
    @ConfigGroup.Pop
    public double crystalRange = 32.0;

    public ConfigGroup emotions = new ConfigGroup("emotions");
    public int rageKillPlayer = 150;
    public int rageKillAnimal = 25;
    public int rageHurtByPlayer = 5;
    public int avariceDebris = 60;
    public int avariceDiamond = 30;
    public int avariceEmerald = 10;
    public int fearStealthKill = 20;
    public int fearFeebleKill = 15;
    public int fearFlee = 5;
    public int fearAmbush = 12;
    public int fearFleeingKill = 15;
    public int fearFleeingKillPlayer = 60;
    public int willLowHit = 10;
    public int willOutclassKill = 80;
    public int willHostileMinute = 15;
    public int hopeCure = 100;
    public int hopeTrade = 5;
    public int hopePlant = 1;
    public int compassionRescue = 20;
    public int compassionGift = 15;
    public int loveBreed = 10;
    public int loveTame = 40;
    @ConfigGroup.Pop
    public int loveGift = 5;

    public ConfigGroup corpsCaps = new ConfigGroup("corpsCaps");
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int greenCap = 5;
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int orangeCap = 1;
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int yellowCap = 15;
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int redCap = 10;
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int blueCap = 20;
    @ValidatedInt.Restrict(min = 1, max = 100)
    public int indigoCap = 34;
    @ValidatedInt.Restrict(min = 1, max = 100)
    @ConfigGroup.Pop
    public int starSapphireCap = 25;

    public ConfigGroup corps = new ConfigGroup("corps");
    public int redOfferRage = 600;
    public float redOfferDailyChance = 0.25F;
    public int greenRingWill = 600;
    public int greenPlayersPerRing = 4;
    public int indigoMinPlayers = 3;
    @ValidatedInt.Restrict(min = 0, max = 30)
    public int indigoSlowSeconds = 3;
    @ValidatedInt.Restrict(min = 1, max = 5)
    public int indigoSlowLevel = 1;
    @ValidatedInt.Restrict(min = 1, max = 16)
    public int batteriesPerCorps = 1;
    public boolean batteryCeremony = true;
    public int batteryCeremonyMembers = 2;
    public int bluePathDistance = 2000;
    public int bluePathSpacing = 32;
    public int hopeShrine = 8;
    @ConfigGroup.Pop
    public int meteorNight = 3;
    @ValidatedInt.Restrict(min = 1, max = 365)
    public int meteorIntervalDays = 12;
    public static BrightestDayConfig get() {
        return instance;
    }

    public static void load() {
        instance = ConfigApiJava.registerAndLoadConfig(BrightestDayConfig::new, RegisterType.BOTH);
    }
}

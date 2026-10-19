package dev.amble.client.config;

import dev.amble.BrightestDay;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedFloat;

public class BrightestDayClientConfig extends Config {
    private static BrightestDayClientConfig instance;

    public BrightestDayClientConfig() {
        super(BrightestDay.id("client"), "", BrightestDay.MOD_ID, "client");
    }

    public boolean showFlightSpeedometer = true;
    public boolean showFlightTrails = true;
    public boolean showOathText = true;
    public boolean showRageVignette = true;
    public boolean showIndigoVignette = true;
    @ValidatedFloat.Restrict(min = 0.25F, max = 1.5F)
    public float effectsQuality = 1.0F;

    public static BrightestDayClientConfig get() {
        return instance;
    }

    public static void load() {
        instance = ConfigApiJava.registerAndLoadConfig(BrightestDayClientConfig::new, RegisterType.CLIENT);
    }
}

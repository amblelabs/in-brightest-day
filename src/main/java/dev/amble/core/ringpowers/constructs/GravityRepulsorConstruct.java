package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;

public class GravityRepulsorConstruct extends GravityBallConstruct {
    public GravityRepulsorConstruct() {
        super(BrightestDay.id("gravity_repulsor"), CorpsArsenal.exclusive(LanternCorps.YELLOW), true);
    }
}

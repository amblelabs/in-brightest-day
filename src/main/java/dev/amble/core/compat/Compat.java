package dev.amble.core.compat;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.CorpsColors;
import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class Compat {

    private static final Runnable PLACEHOLDERS = () -> Placeholders.registerCommon(
            BrightestDay.id("corps_color"),
            (ctx, arg) -> {
                if (!ctx.hasPlayer()) return PlaceholderResult.invalid("player required");
                Component input = Placeholders.COMMON_PLACEHOLDER_PARSER.parseComponent(arg, ParserContext.of(PlaceholderContext.COMMON_KEY, ctx));
                return PlaceholderResult.value(input.copy().withColor(CorpsColors.of(ctx.player())));
            }
    );

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api"))
            PLACEHOLDERS.run();
    }
}

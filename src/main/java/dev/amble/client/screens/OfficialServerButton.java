package dev.amble.client.screens;

import dev.amble.BrightestDay;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class OfficialServerButton {
    private static final String MULTIPLAYER = "menu.multiplayer";
    private static final int GAP = 4;
    private static final Identifier ICON = BrightestDay.id("textures/item/green_power_ring.png");
    private static final Identifier TOOLTIP_STYLE = BrightestDay.id("ring");

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;
            AbstractWidget multiplayer = null;
            for (AbstractWidget widget : Screens.getWidgets(screen)) {
                if (widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(MULTIPLAYER)) {
                    multiplayer = widget;
                    break;
                }
            }
            int x = multiplayer == null ? scaledWidth / 2 + 104 : multiplayer.getX() + multiplayer.getWidth() + GAP;
            int y = multiplayer == null ? scaledHeight / 4 + 72 : multiplayer.getY();
            Component label = Component.translatable("gui.brightestday.official.button");
            IconButton button = new IconButton(x, y, ICON, label, () -> client.gui.setScreen(new OfficialServerScreen(screen)));
            button.setTooltip(Tooltip.create(label, Optional.empty(), TOOLTIP_STYLE));
            Screens.getWidgets(screen).add(button);
        });
    }

    private OfficialServerButton() {}
}

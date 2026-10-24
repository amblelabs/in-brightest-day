package dev.amble.client.screens;

import dev.amble.BrightestDay;
import dev.amble.verification.BetaVerification;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
                if (widget.getMessage().getContents() instanceof TranslatableContents contents) {
                    if (contents.getKey().equals(MULTIPLAYER)) {
                        multiplayer = widget;
                        break;
                    }
                }
            }

            int x = multiplayer == null ? scaledWidth / 2 + 104 : multiplayer.getX() + multiplayer.getWidth() + GAP;
            int y = multiplayer == null ? scaledHeight / 4 + 72 : multiplayer.getY();

            Tooltip defaultLabel = Tooltip.create(Component.translatable("gui.brightestday.official.button"), Optional.empty(), TOOLTIP_STYLE);
            Tooltip betaTooltip = Tooltip.create(BetaVerification.TOOLTIP, Optional.empty(), TOOLTIP_STYLE);

            Component label = BetaVerification.isBetaLocked()
                    ? Component.translatable("gui.brightestday.official.button") : BetaVerification.PLAY;

            IconButton button = new IconButton(x, y, ICON, label, null);

            button.setOnPress(() -> {
                if (BetaVerification.isServerRunning()) {
                    // TODO: maybe use whatever minecraft is using? if it isn't using this ig?
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(new StringSelection(BetaVerification.getAuthUrl()), null);
                    return;
                }

                if (BetaVerification.isBetaLocked()) {
                    button.setTooltip(Tooltip.create(BetaVerification.BROWSER));

                    BetaVerification.startAndWaitForToken(valid -> {
                        button.setTooltip(valid ? defaultLabel : betaTooltip);
                    });

                    return;
                }

                client.gui.setScreen(new OfficialServerScreen(screen));
            });

            button.setTooltip(BetaVerification.isBetaLocked() ? betaTooltip : defaultLabel);
            Screens.getWidgets(screen).add(button);
        });
    }

    private OfficialServerButton() {}
}

package dev.amble.mixin.client.verification;

import dev.amble.verification.BetaVerification;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

@Mixin(value = TitleScreen.class, priority = 1001)
public class TitleScreenMixin {

    @Redirect(method = "createNormalMenuOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;", ordinal = 0))
    private Button.Builder initWidgetsNormal1(Component message, Button.OnPress onPress) {
        boolean beta = BetaVerification.isBetaLocked();

        Tooltip betaTooltip = Tooltip.create(BetaVerification.TOOLTIP);

        Button.Builder instance = Button.builder(BetaVerification.isBetaLocked() ? BetaVerification.PLAY : message, button -> {
            if (BetaVerification.isServerRunning()) {
                // TODO: maybe use whatever minecraft is using? if it isn't using this ig?
                StringSelection stringSelection = new StringSelection(BetaVerification.getAuthUrl());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                return;
            }

            if (BetaVerification.isBetaLocked()) {
                button.setMessage(BetaVerification.BROWSER);

                BetaVerification.startAndWaitForToken(valid -> {
                    if (valid) {
                        button.setTooltip(null);
                        button.setMessage(message);
                    } else {
                        button.setMessage(BetaVerification.PLAY);
                    }
                });

                return;
            }

            onPress.onPress(button);
        });

        if (beta)
            instance = instance.tooltip(betaTooltip);

        return instance;
    }
}

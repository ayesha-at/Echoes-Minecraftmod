package com.ayesha.echoes.hud;

import com.ayesha.echoes.echo.EchoManager;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.RecordingManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Top-left overlay: a recording timer while R is active, and an active-Echo
 * counter (x/3) always visible.
 *
 * Corrected against the actual 26.2 API after the first build attempt failed --
 * HudRenderCallback was removed in 26.1 in favour of HudElementRegistry and
 * VanillaHudElements, GuiGraphics was renamed to GuiGraphicsExtractor, and text
 * drawing goes through graphics.text(...) with full ARGB colours (the alpha
 * channel is required, or the text renders transparent).
 */
public final class EchoHud {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath("echoes", "hud");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RECORDING_RED = 0xFFFF5555;

    private EchoHud() {
    }

    public static void register() {
        // Attached just before the chat layer, same as the docs example -- this
        // HUD does not need to be in front of or behind anything in particular,
        // so "before chat" is a reasonable, uncontested spot.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LAYER_ID, EchoHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // NOTE: Options.hideGui (F1) moved in 26.2's GUI/HUD reorg. The
        // replacement was confirmed only via a community port's changelog, not
        // official docs, so this is the line worth checking first if it fails
        // to compile -- everything else in this file is docs-verified.
        if (client.gui.hud.isHidden()) {
            return;
        }

        int x = 6;
        int y = 6;

        EchoRecording current = RecordingManager.getInstance().getCurrentRecording();
        // isRecording() and getCurrentRecording() are separate reads of state
        // the recorder can change between them, so this checks the value it is
        // about to use rather than a flag that may already be stale.
        if (RecordingManager.getInstance().isRecording() && current != null) {
            String recText = String.format(
                    "REC %.1fs / %ds",
                    current.getDurationSeconds(),
                    EchoRecording.MAX_SECONDS
            );
            graphics.text(client.font, recText, x, y, RECORDING_RED, true);
            y += 10;
        }

        int active = EchoManager.getInstance().getActiveCount();
        String counterText = String.format("Echoes: %d / %d", active, EchoManager.MAX_ACTIVE_ECHOES);
        graphics.text(client.font, counterText, x, y, WHITE, true);
    }
}

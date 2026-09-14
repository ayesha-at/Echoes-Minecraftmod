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
 * Top-left overlay: recording timer while R is active, and an
 * active-Echo counter (x/3) always visible. Phase 4 HUD requirement.
 *
 * Corrected against the actual 26.2 API after the first build attempt
 * failed -- the old HudRenderCallback was removed in 26.1 in favour of
 * HudElementRegistry/VanillaHudElements (confirmed via the official Fabric
 * docs' HudRenderingEntrypoint.java example), GuiGraphics was renamed to
 * GuiGraphicsExtractor, and text drawing now goes through
 * graphics.text(...) with full ARGB colors (alpha channel required, or
 * the text renders transparent) rather than the old drawString(...) with
 * bare RGB.
 */
public final class EchoHud {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath("echoes", "hud");

    private static final int WHITE = 0xFFFFFFFF;

    private EchoHud() {
    }

    public static void register() {
        // Attach right before the chat layer, same as the docs example --
        // this HUD doesn't need to be in front of or behind anything in
        // particular, so "before chat" is just a reasonable, uncontested spot.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LAYER_ID, EchoHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // NOTE: Options.hideGui (F1) moved in 26.2's GUI/HUD reorg --
        // confirmed via a community 26.2 port that the replacement is
        // Gui.hud.isHidden(), reached here as client.gui.hud.isHidden().
        // This is the one line in this file NOT verified against official
        // docs (only a changelog note), so it's the one worth double
        // checking first if this specific line doesn't compile.
        if (client.gui.hud.isHidden()) {
            return;
        }

        int x = 6;
        int y = 6;

        if (RecordingManager.getInstance().isRecording()) {
            EchoRecording current = RecordingManager.getInstance().getCurrentRecording();
            String recText = String.format(
                    "REC %.1fs / %ds",
                    current.getDurationSeconds(),
                    EchoRecording.MAX_SECONDS
            );
            graphics.text(client.font, recText, x, y, 0xFFFF5555, true);
            y += 10;
        }

        int active = EchoManager.getInstance().getActiveCount();
        String counterText = String.format("Echoes: %d / %d", active, EchoManager.MAX_ACTIVE_ECHOES);
        graphics.text(client.font, counterText, x, y, WHITE, true);
    }
}

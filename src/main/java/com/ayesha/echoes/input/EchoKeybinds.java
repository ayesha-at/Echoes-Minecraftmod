package com.ayesha.echoes.input;

import com.ayesha.echoes.echo.EchoManager;
import com.ayesha.echoes.recording.RecordingManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Locked V1 keybinds (Phase 4): R = record, E = summon, H = clear.
 *
 * The Phase 2 (P, playback particle test) and Phase 2.5 (U, rendering
 * spike) debug keys are gone now that EchoManager/EchoEntity do the real
 * thing -- same as PlaybackTester and RenderingSpikeTester being deleted.
 *
 * KNOWN CONFLICT TO TEST FIRST: GLFW_KEY_E is vanilla's default Inventory
 * key. Unlike the Phase 2.5 O-vs-Social-Interactions clash (which silently
 * ate the keypress), the inventory screen isn't driven through the normal
 * KeyMapping-conflict system, so pressing E may open the inventory *and*
 * summon an Echo at the same time. If that happens in testing, the fix is
 * the same as last time: change the default here (or just rebind summon
 * via Options > Controls > EchoesLab) to a free key.
 */
public final class EchoKeybinds {

    private static KeyMapping recordKey;
    private static KeyMapping summonKey;
    private static KeyMapping clearKey;

    public static void register() {
        recordKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.echoes.record",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.MISC
        ));

        summonKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.echoes.summon",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_E,
                KeyMapping.Category.MISC
        ));

        clearKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.echoes.clear",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }

            while (recordKey.consumeClick()) {
                RecordingManager.getInstance().toggle(client.player);
            }
            while (summonKey.consumeClick()) {
                EchoManager.getInstance().spawn(
                        client.player,
                        RecordingManager.getInstance().getLastCompletedRecording()
                );
            }
            while (clearKey.consumeClick()) {
                EchoManager.getInstance().clearAll(client.player);
            }

            RecordingManager.getInstance().tick(client);
        });
    }
}

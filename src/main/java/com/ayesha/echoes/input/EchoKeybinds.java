package com.ayesha.echoes.input;

import com.ayesha.echoes.echo.EchoManager;
import com.ayesha.echoes.recording.RecordingManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Locked V1 keybinds (Phase 4, revised): R = record, Z = summon, H = clear.
 *
 * Summon was moved off E on purpose: GLFW_KEY_E is vanilla's default
 * Inventory key and isn't gated by the normal KeyMapping-conflict system,
 * so it would open the inventory and summon an Echo at the same time.
 * Z has no vanilla default binding, so it's safe out of the box -- players
 * can still rebind any of these via Options > Controls > EchoesLab.
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
                GLFW.GLFW_KEY_Z,
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

package com.ayesha.echoes;

import com.ayesha.echoes.echo.EchoEntities;
import com.ayesha.echoes.echo.EchoManager;
import com.ayesha.echoes.hud.EchoHud;
import com.ayesha.echoes.input.EchoKeybinds;
import com.ayesha.echoes.recording.RecordingManager;
import com.ayesha.echoes.rendering.EchoRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.server.level.ServerPlayer;

/**
 * Entry point. Connects the major systems -- deliberately contains no
 * recording, playback or rendering logic itself.
 *
 * PlayerBlockBreakEvents.AFTER only fires on a logical server, but V1 is
 * singleplayer-only, so the integrated server is guaranteed to be running in
 * this same process whenever a world is loaded; registering it from the client
 * initializer is safe.
 */
public final class EchoesMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EchoEntities.register();
        EntityRenderers.register(EchoEntities.ECHO, EchoRenderer::new);
        EchoKeybinds.register();
        EchoHud.register();

        // Clear stale bookkeeping on world (re)join. Echo entities from a
        // previous world are already gone with that world, so this drops
        // references rather than trying to despawn dead ones. RecordingManager
        // drops any half-finished recording but KEEPS the last completed one,
        // which is world-portable.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            EchoManager.getInstance().reset();
            RecordingManager.getInstance().reset();
        });

        // Same on disconnect, so quitting mid-recording cannot leave the HUD
        // showing REC all the way out to the main menu.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EchoManager.getInstance().reset();
            RecordingManager.getInstance().reset();
        });

        // Fires once per real block break, regardless of how the mouse was
        // held -- this replaced attack-key-edge polling, which missed any break
        // that happened during a continuous held click (e.g. mining through a
        // snow layer straight into the sand underneath it).
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingManager.getInstance().recordBreakEvent(serverPlayer, pos, state);
            }
        });
    }
}

package com.ayesha.echoes;

import com.ayesha.echoes.echo.EchoEntities;
import com.ayesha.echoes.echo.EchoManager;
import com.ayesha.echoes.hud.EchoHud;
import com.ayesha.echoes.input.EchoKeybinds;
import com.ayesha.echoes.rendering.EchoRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Entry point. Connects the major systems -- does not contain recording,
 * playback, or rendering logic itself.
 *
 * Phase 3/4: EchoManager is now the real spawn/despawn owner (registered
 * indirectly via EchoKeybinds), and EchoHud adds the recording-timer +
 * Echo-counter overlay. The (re)join hook resets EchoManager's tracked
 * list on world join -- a previous world's Echo entities are already gone
 * once that world/server is gone, so this is just clearing stale
 * bookkeeping, not trying to despawn dead references (Phase 4 edge case).
 */
public final class EchoesMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EchoEntities.register();
        EntityRenderers.register(EchoEntities.ECHO, EchoRenderer::new);
        EchoKeybinds.register();
        EchoHud.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EchoManager.getInstance().reset());
    }
}

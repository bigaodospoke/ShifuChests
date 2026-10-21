package com.shifuchests.client;

import com.shifuchests.network.EfeitoBauPayload;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.PreviewPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class ShifuChestsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Os handlers rodam na thread principal do jogo
        ClientPlayNetworking.registerGlobalReceiver(OpenRoulettePayload.TYPE, (payload, context) -> ClientPayloads.openRoulette(payload));
        ClientPlayNetworking.registerGlobalReceiver(PreviewPayload.TYPE, (payload, context) -> ClientPayloads.openPreview(payload));
        ClientPlayNetworking.registerGlobalReceiver(EfeitoBauPayload.TYPE, (payload, context) -> {
            if (context.client().level != null) EfeitosCliente.receber(payload, context.client().level);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) EfeitosCliente.tick(client.level);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(EfeitosCliente::renderFeixes);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EfeitosCliente.limpar());
    }
}

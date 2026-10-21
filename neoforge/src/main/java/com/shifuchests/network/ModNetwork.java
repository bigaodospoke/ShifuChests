package com.shifuchests.network;

import com.shifuchests.client.ClientPayloads;
import com.shifuchests.sorteio.RollManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Os handlers do cliente so sao carregados quando um pacote chega, nunca no servidor dedicado
        registrar.playToClient(OpenRoulettePayload.TYPE, OpenRoulettePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.openRoulette(payload));
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloads.openPreview(payload));
        registrar.playToServer(SkipPayload.TYPE, SkipPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) RollManager.skip(player);
        });
    }

    private ModNetwork() {
    }
}

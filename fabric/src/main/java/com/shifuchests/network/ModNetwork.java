package com.shifuchests.network;

import com.shifuchests.sorteio.RollManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetwork {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenRoulettePayload.TYPE, OpenRoulettePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(EfeitoBauPayload.TYPE, EfeitoBauPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SkipPayload.TYPE, SkipPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkipPayload.TYPE, (payload, context) -> RollManager.skip(context.player()));
    }

    private ModNetwork() {
    }
}

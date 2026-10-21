package com.shifuchests.client;

import com.shifuchests.client.screen.PreviewScreen;
import com.shifuchests.client.screen.RouletteScreen;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.PreviewPayload;
import net.minecraft.client.Minecraft;

/** Pacotes que chegam do servidor. So e carregada no cliente. */
public final class ClientPayloads {
    public static void openRoulette(OpenRoulettePayload payload) {
        Minecraft.getInstance().setScreen(new RouletteScreen(payload));
    }

    public static void openPreview(PreviewPayload payload) {
        Minecraft.getInstance().setScreen(new PreviewScreen(payload));
    }

    private ClientPayloads() {
    }
}

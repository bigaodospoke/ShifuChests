package com.shifuchests.client;

import com.shifuchests.ShifuChests;
import com.shifuchests.registry.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ShifuChests.MOD_ID, value = Dist.CLIENT)
public final class ShifuChestsClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.BAU_BLOCK_ENTITY.get(), BauRenderer::new);
    }

    private ShifuChestsClient() {
    }
}

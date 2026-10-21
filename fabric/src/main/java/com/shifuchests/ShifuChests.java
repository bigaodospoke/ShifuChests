package com.shifuchests;

import com.shifuchests.bau.Interacao;
import com.shifuchests.command.BausCommand;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.network.ModNetwork;
import com.shifuchests.sorteio.RollManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shifu Chests para Fabric (servidor Eclipse Cobblemon). Em vez de blocos proprios, qualquer bloco marcado com
 * /baus definir (normalmente um bau dourado do Cobblemon) vira uma caixa; as chaves sao itens do servidor
 * (ex.: as chaves do Eclipse Cobblemon Keys) marcados com o id da caixa.
 */
public final class ShifuChests implements ModInitializer {
    public static final String MOD_ID = "shifuchests";
    public static final Logger LOGGER = LoggerFactory.getLogger("ShifuChests");
    /** Fase que roda antes dos outros mods: entrega o premio antes de o Cobblemon salvar a equipe de quem saiu. */
    private static final ResourceLocation ANTES = ResourceLocation.fromNamespaceAndPath(MOD_ID, "antes");

    @Override
    public void onInitialize() {
        ModNetwork.register();
        Interacao.register();
        // Carrega depois que o Cobblemon carregou as especies (datapacks prontos)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ConfigManager.load(server.registryAccess()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BausCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(RollManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(ANTES, Event.DEFAULT_PHASE);
        ServerPlayConnectionEvents.DISCONNECT.register(ANTES, (handler, server) -> RollManager.deliverNow(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(RollManager::deliverAll);
    }
}

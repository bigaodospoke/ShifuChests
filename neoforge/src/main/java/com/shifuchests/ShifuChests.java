package com.shifuchests;

import com.shifuchests.command.BausCommand;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.network.ModNetwork;
import com.shifuchests.registry.ModRegistry;
import com.shifuchests.sorteio.RollManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ShifuChests.MOD_ID)
public final class ShifuChests {
    public static final String MOD_ID = "shifuchests";
    public static final Logger LOGGER = LoggerFactory.getLogger("ShifuChests");

    public ShifuChests(IEventBus modBus) {
        ModRegistry.register(modBus);
        modBus.addListener(ModNetwork::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        // Carrega depois que o Cobblemon carregou as especies (datapacks prontos)
        bus.addListener((ServerStartedEvent event) -> ConfigManager.load(event.getServer().registryAccess()));
        bus.addListener((RegisterCommandsEvent event) -> BausCommand.register(event.getDispatcher()));
        bus.addListener((ServerTickEvent.Post event) -> RollManager.tick(event.getServer()));
        // Prioridade alta: entrega o premio pendente antes do Cobblemon salvar a party de quem saiu
        bus.addListener(EventPriority.HIGHEST, (PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) RollManager.deliverNow(player);
        });
        bus.addListener((ServerStoppingEvent event) -> RollManager.deliverAll(event.getServer()));
    }
}

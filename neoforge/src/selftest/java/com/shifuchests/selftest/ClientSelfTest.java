package com.shifuchests.selftest;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauBlockEntity;
import com.shifuchests.bau.BauTier;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.config.Raridade;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.RewardDisplay;
import com.shifuchests.registry.ModRegistry;
import com.shifuchests.sorteio.RollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** gradlew runClientSelfTest: entra num mundo, roda a roleta e a vitrine e tira prints de cada etapa. */
@EventBusSubscriber(modid = ShifuChests.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("BausClientSelfTest");
    private static final int ROULETTE = 100;
    private static final int SPIN_TICKS = 140;
    private static int ticks;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("shifuchests.clientselftest")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        int t = ticks;

        if (t == 80) {
            mc.player.setYRot(0);
            mc.player.setXRot(28);
            onServer(mc, (server, player) -> {
                ServerLevel level = server.overworld();
                level.setDayTime(6000);
                for (BauTier tier : BauTier.values()) {
                    level.setBlockAndUpdate(chestPos(player, tier), ModRegistry.CHESTS.get(tier).get().defaultBlockState());
                }
            });
        }
        if (t == ROULETTE) {
            onServer(mc, (server, player) -> {
                // Roleta com o premio forcado para ver a comemoracao maxima
                List<RewardDisplay> base = RollManager.previewList(BauTier.PREMIUM);
                List<RewardDisplay> strip = new ArrayList<>();
                while (strip.size() < 54) strip.addAll(base);
                strip = new ArrayList<>(strip.subList(0, 54));
                RewardDisplay mewtwo = base.stream().filter(d -> d.species() != null && d.species().getPath().equals("mewtwo"))
                        .findFirst().orElse(base.get(0));
                List<String> aspects = new ArrayList<>(mewtwo.aspects());
                aspects.add("shiny");
                int winner = 48;
                strip.set(winner, new RewardDisplay(mewtwo.species(), aspects, mewtwo.icon(), mewtwo.name(), Raridade.MITICO, true, 70,
                        mewtwo.chance()));
                PacketDistributor.sendToPlayer(player, new OpenRoulettePayload(BauTier.PREMIUM,
                        ConfigManager.bau(BauTier.PREMIUM).displayName(), strip, winner, SPIN_TICKS, true, false));
                BlockPos pos = chestPos(player, BauTier.PREMIUM);
                player.serverLevel().blockEvent(pos, ModRegistry.CHESTS.get(BauTier.PREMIUM).get(), BauBlockEntity.EVENT_SPIN, SPIN_TICKS);
            });
        }
        shot(mc, t, ROULETTE + 9, "1_entrada");
        shot(mc, t, ROULETTE + 40, "2_girando");
        shot(mc, t, ROULETTE + 118, "3_freando");
        shot(mc, t, ROULETTE + 148, "4_parou");
        if (t == ROULETTE + 155) {
            onServer(mc, (server, player) -> {
                BlockPos pos = chestPos(player, BauTier.PREMIUM);
                player.serverLevel().blockEvent(pos, ModRegistry.CHESTS.get(BauTier.PREMIUM).get(), BauBlockEntity.EVENT_REVEAL,
                        BauBlockEntity.revealParam(Raridade.MITICO, true));
            });
        }
        shot(mc, t, ROULETTE + 163, "5_revelacao");
        shot(mc, t, ROULETTE + 200, "6_revelacao_final");
        if (t == ROULETTE + 210) mc.setScreen(null);
        shot(mc, t, ROULETTE + 214, "7_mundo_feixe");

        int preview = ROULETTE + 225;
        if (t == preview) onServer(mc, (server, player) -> RollManager.sendPreview(player, BauTier.PREMIUM));
        shot(mc, t, preview + 25, "8_previa");
        if (t == preview + 30) mc.setScreen(null);

        // Sorteio de verdade pelo caminho normal, olhando o bau no mundo
        int real = preview + 40;
        if (t == real) onServer(mc, (server, player) -> RollManager.start(player, BauTier.MEDIO, chestPos(player, BauTier.MEDIO), true));
        if (t == real + 3) mc.setScreen(null);
        shot(mc, t, real + 60, "9_mundo_tremendo");
        shot(mc, t, real + 162, "10_mundo_revelacao");
        if (t == real + 200) {
            LOG.info("CLIENT SELFTEST FIM");
            mc.stop();
        }
    }

    private static BlockPos chestPos(ServerPlayer player, BauTier tier) {
        return player.blockPosition().offset((tier.ordinal() - 1) * 2, 0, 4);
    }

    private static void shot(Minecraft mc, int t, int at, String name) {
        if (t != at) return;
        Consumer<net.minecraft.network.chat.Component> log = msg -> LOG.info("print {}: {}", name, msg.getString());
        Screenshot.grab(mc.gameDirectory, "shifuchests_" + name + ".png", mc.getMainRenderTarget(), log);
    }

    private static void onServer(Minecraft mc, java.util.function.BiConsumer<MinecraftServer, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(server, player);
        });
    }

    private ClientSelfTest() {
    }
}

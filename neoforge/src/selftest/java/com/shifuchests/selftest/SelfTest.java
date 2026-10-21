package com.shifuchests.selftest;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauTier;
import com.shifuchests.cobblemon.CobblemonBridge;
import com.shifuchests.config.BauConfig;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.config.Recompensa;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.PreviewPayload;
import com.shifuchests.network.RewardDisplay;
import com.shifuchests.registry.ModRegistry;
import com.shifuchests.sorteio.RollManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/** gradlew runSelfTest: sobe um servidor com o Cobblemon, testa o mod e desliga. */
@EventBusSubscriber(modid = ShifuChests.MOD_ID)
public final class SelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("BausSelfTest");
    private static int failures;
    private static int checks;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("shifuchests.selftest")) return;
        MinecraftServer server = event.getServer();
        try {
            run(server);
        } catch (Throwable t) {
            failures++;
            LOG.error("SELFTEST excecao", t);
        }
        LOG.info("SELFTEST RESULTADO: {} verificacoes, {} falhas", checks, failures);
        server.halt(false);
    }

    private static void run(MinecraftServer server) throws Exception {
        // 1. Configs de exemplo validas (todas as especies e itens existem)
        List<String> warnings = ConfigManager.load(server.registryAccess());
        check(warnings.isEmpty(), "configs sem avisos " + warnings);
        for (BauTier tier : BauTier.values()) {
            BauConfig config = ConfigManager.bau(tier);
            check(!config.isEmpty(), "bau " + tier.id + " tem recompensas (" + config.recompensas.size() + ")");
            double total = config.recompensas.stream().mapToDouble(Recompensa::chance).sum();
            check(Math.abs(total - 1) < 1e-6, "chances do " + tier.id + " somam 100% (" + total + ")");
        }

        // 2. Todo Pokemon dos baus e criado pelo Cobblemon
        for (BauTier tier : BauTier.values()) {
            for (Recompensa r : ConfigManager.bau(tier).recompensas) {
                if (r.tipo != Recompensa.Tipo.POKEMON) continue;
                Pokemon p = CobblemonBridge.create(r.pokemon, 12, true, null);
                boolean levelOk = r.pokemon.contains("level=") || p.getLevel() == 12;
                check(levelOk && p.getShiny() && p.getAspects().contains("shiny"),
                        tier.id + ": " + r.pokemon + " -> " + p.getSpecies().getResourceIdentifier() + " nv" + p.getLevel()
                                + " aspectos=" + p.getAspects());
            }
        }
        Pokemon alola = CobblemonBridge.create("vulpix alolan", 5, false, null);
        check(alola.getAspects().contains("alolan"), "vulpix alolan tem aspecto alolan " + alola.getAspects());
        check(CobblemonBridge.validateSpec("pokemonquenaoexiste") != null, "especie invalida e recusada: "
                + CobblemonBridge.validateSpec("pokemonquenaoexiste"));

        // 3. Vitrine + pacotes de rede (ida e volta)
        for (BauTier tier : BauTier.values()) {
            List<RewardDisplay> list = RollManager.previewList(tier);
            check(list.size() == ConfigManager.bau(tier).recompensas.size(), "previa do " + tier.id + " completa");
            PreviewPayload payload = new PreviewPayload(tier, ConfigManager.bau(tier).displayName(), list);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            PreviewPayload.STREAM_CODEC.encode(buf, payload);
            PreviewPayload back = PreviewPayload.STREAM_CODEC.decode(buf);
            check(back.rewards().equals(list) || sameNames(back.rewards(), list), "pacote de previa do " + tier.id + " ida e volta ("
                    + buf.writerIndex() + " bytes)");

            OpenRoulettePayload roulette = new OpenRoulettePayload(tier, payload.title(), list, list.size() - 1, 140, true, false);
            buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            OpenRoulettePayload.STREAM_CODEC.encode(buf, roulette);
            OpenRoulettePayload backRoulette = OpenRoulettePayload.STREAM_CODEC.decode(buf);
            check(backRoulette.winnerIndex() == roulette.winnerIndex() && backRoulette.strip().size() == list.size(),
                    "pacote da roleta do " + tier.id + " ida e volta");
        }

        // 4. Sorteio completo com um jogador falso, abrindo um bau de verdade no mundo
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("0f0f0f0f-0000-4000-8000-00000000babe"), "TesteBaus"));
        for (BauTier tier : BauTier.values()) {
            BlockPos pos = new BlockPos(tier.ordinal() * 2, 120, 0);
            level.setBlockAndUpdate(pos, ModRegistry.CHESTS.get(tier).get().defaultBlockState());
            check(level.getBlockEntity(pos) != null, "bau " + tier.id + " colocado com block entity");

            ItemStack key = new ItemStack(ModRegistry.KEYS.get(tier).get(), 3);
            for (int i = 0; i < 5; i++) {
                int partyBefore = partySize(player);
                int itemsBefore = inventoryCount(player);
                int keysBefore = key.getCount();
                if (key.isEmpty()) key = new ItemStack(ModRegistry.KEYS.get(tier).get(), 3);
                keysBefore = key.getCount();
                RollManager.openWithKey(player, tier, key, pos);
                check(RollManager.isRolling(player.getUUID()), tier.id + " sorteio " + i + " comecou");
                check(key.getCount() == keysBefore - 1, tier.id + " gastou 1 chave");
                RollManager.openWithKey(player, tier, key, pos);
                check(key.getCount() == keysBefore - 1, tier.id + " nao abre duas roletas ao mesmo tempo");
                RollManager.skip(player);
                check(!RollManager.isRolling(player.getUUID()), tier.id + " sorteio " + i + " entregue");
                LOG.info("  {} sorteio {}: party {} -> {}, itens {} -> {}", tier.id, i, partyBefore, partySize(player),
                        itemsBefore, inventoryCount(player));
            }
        }

        // 5. Comandos de admin: adicionar e remover
        int before = ConfigManager.bau(BauTier.NORMAL).recompensas.size();
        JsonObject reward = new JsonObject();
        reward.addProperty("pokemon", "magikarp level=3");
        reward.addProperty("peso", 5);
        List<String> addWarnings = ConfigManager.addReward(BauTier.NORMAL, reward, server.registryAccess());
        check(addWarnings.isEmpty() && ConfigManager.bau(BauTier.NORMAL).recompensas.size() == before + 1, "adicionar recompensa salva");
        ConfigManager.removeReward(BauTier.NORMAL, before + 1, server.registryAccess());
        check(ConfigManager.bau(BauTier.NORMAL).recompensas.size() == before, "remover recompensa salva");

        // 6. Comandos pelo console (ajuda na raiz e o atalho /shifuchests)
        var console = server.createCommandSourceStack();
        for (String command : List.of("baus", "shifuchests", "baus listar premium", "shifuchests listar normal", "baus recarregar")) {
            int result = server.getCommands().getDispatcher().execute(command, console);
            check(result > 0, "/" + command + " executa (" + result + ")");
        }

        JsonObject item = BauConfig.itemToJson(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 1), server.registryAccess());
        LOG.info("  item da mao vira: {}", item);
        check(item.get("item").getAsString().equals("minecraft:diamond_sword"), "item da mao vira JSON");

        // 7. Bau criado so pela config (arquivo novo na pasta), com chave e tipo gravado no bloco
        java.nio.file.Path arquivo = ConfigManager.dir().resolve("teste_custom.json");
        java.nio.file.Files.writeString(arquivo, "{\"nome\": \"&dBau Teste\", \"visual\": \"medio\", \"cor\": \"#AA00FF\", \"nivel_padrao\": 50,"
                + " \"recompensas\": [{\"pokemon\": \"mew min_perfect_ivs=3\", \"peso\": 1}, {\"pokemon\": \"mew shiny=yes min_perfect_ivs=3\", \"peso\": 0.1}, {\"pokemon\": \"mewtwo\", \"chance_shiny\": 1, \"peso\": 0.1}]}");
        try {
            ConfigManager.load(server.registryAccess());
            BauConfig custom = ConfigManager.bau("teste_custom");
            check(custom != null && custom.recompensas.size() == 3, "bau novo da pasta carregado");
            check(custom != null && custom.visual == BauTier.MEDIO && custom.cor == 0xAA00FF, "visual e cor do bau novo");
            check(ConfigManager.ids().contains("teste_custom") && ConfigManager.ids().iterator().next().equals("normal"),
                    "ids com os padrao primeiro");

            BlockPos pos = new BlockPos(10, 120, 0);
            // o mundo de teste fica salvo entre rodadas: tira o bau antigo (com tipo) antes de colocar o novo
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, ModRegistry.CHESTS.get(BauTier.MEDIO).get().defaultBlockState());
            com.shifuchests.bau.BauBlockEntity be = (com.shifuchests.bau.BauBlockEntity) level.getBlockEntity(pos);
            check(be != null && be.tipo().equals("medio"), "bau sem tipo vale o tier do bloco");
            be.setTipo("teste_custom");
            net.minecraft.nbt.CompoundTag salvo = be.saveWithoutMetadata(server.registryAccess());
            com.shifuchests.bau.BauBlockEntity copia = new com.shifuchests.bau.BauBlockEntity(pos, level.getBlockState(pos));
            copia.loadWithComponents(salvo, server.registryAccess());
            check(copia.tipo().equals("teste_custom"), "tipo do bau salvo e lido do NBT");

            ItemStack chave = com.shifuchests.bau.ChaveItem.criar(ModRegistry.KEYS.get(custom.visual).get(), custom.id, custom.isPadrao(),
                    custom.displayName(), custom.cor, 2);
            check(com.shifuchests.bau.ChaveItem.tipo(chave).equals("teste_custom"), "chave do bau novo guarda o id");
            check(com.shifuchests.bau.ChaveItem.tipo(new ItemStack(ModRegistry.KEYS.get(BauTier.MEDIO).get())).equals("medio"),
                    "chave padrao continua abrindo o bau padrao");

            RollManager.openWithKey(player, "teste_custom", chave, pos);
            check(RollManager.isRolling(player.getUUID()) && chave.getCount() == 1, "bau novo sorteia e gasta a chave");
            RollManager.skip(player);
            check(!RollManager.isRolling(player.getUUID()), "bau novo entrega");
            check(RollManager.previewList(custom).size() == 3, "previa do bau novo");
            check(RollManager.previewList(custom).stream().filter(d -> d.shiny()).count() == 2, "shiny aparece com modelo shiny na previa");

            int result = server.getCommands().getDispatcher().execute("baus listar teste_custom", console);
            check(result == 3, "/baus listar teste_custom executa (" + result + ")");
            result = server.getCommands().getDispatcher().execute("baus tipos", console);
            check(result == ConfigManager.baus().size(), "/baus tipos executa (" + result + ")");
        } finally {
            java.nio.file.Files.deleteIfExists(arquivo);
            ConfigManager.load(server.registryAccess());
        }
        check(ConfigManager.bau("teste_custom") == null, "bau some quando o arquivo e apagado");
    }

    private static boolean sameNames(List<RewardDisplay> a, List<RewardDisplay> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).name().getString().equals(b.get(i).name().getString())) return false;
            if (!java.util.Objects.equals(a.get(i).species(), b.get(i).species())) return false;
        }
        return true;
    }

    private static int partySize(FakePlayer player) {
        int count = 0;
        for (Pokemon ignored : Cobblemon.INSTANCE.getStorage().getParty(player)) count++;
        return count;
    }

    private static int inventoryCount(FakePlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) count += stack.getCount();
        return count;
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (ok) {
            LOG.info("  OK   {}", what);
        } else {
            failures++;
            LOG.error("  FALHA {}", what);
        }
    }

    private SelfTest() {
    }
}

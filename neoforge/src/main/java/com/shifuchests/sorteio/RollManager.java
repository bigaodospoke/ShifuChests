package com.shifuchests.sorteio;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauBlock;
import com.shifuchests.bau.BauBlockEntity;
import com.shifuchests.bau.BauTier;
import com.shifuchests.cobblemon.CobblemonBridge;
import com.shifuchests.config.BauConfig;
import com.shifuchests.config.ConfigGeral;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.config.Raridade;
import com.shifuchests.config.Recompensa;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.PreviewPayload;
import com.shifuchests.network.RewardDisplay;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Faz o sorteio no servidor (o cliente so desenha), guarda o premio enquanto a roleta gira e
 * entrega quando ela para. Se o jogador sair ou o servidor fechar no meio, entrega na hora.
 */
public final class RollManager {
    /** Quantos cartoes passam antes do premio: da a sensacao de velocidade. */
    private static final int WINNER_INDEX_MIN = 46;
    private static final int CARDS_AFTER_WINNER = 6;
    /** Folga para a revelacao aparecer na tela antes do item chegar no inventario. */
    private static final int DELIVERY_DELAY_TICKS = 8;
    private static final int PREVIEW_COOLDOWN_TICKS = 10;

    private static final Map<UUID, ActiveRoll> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> PREVIEW_COOLDOWN = new HashMap<>();
    /** Aparencia de cada recompensa nos cartoes "de enfeite" (criar um Pokemon a cada cartao seria caro). */
    private static final Map<Recompensa, RewardDisplay> DISPLAY_CACHE = new WeakHashMap<>();

    private record ActiveRoll(UUID playerId, ServerPlayer opener, String tipo, BauConfig config, Recompensa reward, @Nullable Pokemon pokemon,
                              RewardDisplay display, int deliverAt, @Nullable BlockPos chest,
                              @Nullable ResourceKey<Level> dimension, boolean test) {
    }

    // ---------------------------------------------------------------- abrir

    public static void openWithKey(ServerPlayer player, BauTier tier, ItemStack key, BlockPos chest) {
        openWithKey(player, tier.id, key, chest);
    }

    public static void openWithKey(ServerPlayer player, String tipo, ItemStack key, BlockPos chest) {
        ConfigGeral geral = ConfigManager.geral();
        if (start(player, tipo, chest, false) && !(player.isCreative() && !geral.consumirChaveNoCriativo)) {
            key.shrink(1);
        }
    }

    public static boolean start(ServerPlayer player, BauTier tier, @Nullable BlockPos chest, boolean test) {
        return start(player, tier.id, chest, test);
    }

    /**
     * Comeca uma roleta para o jogador.
     *
     * @param chest bau que foi aberto (para as animacoes no mundo), ou null se veio de comando
     * @param test  so mostra a animacao, nao entrega nada
     * @return false se nao deu para comecar (bau vazio, roleta em andamento, erro)
     */
    public static boolean start(ServerPlayer player, String tipo, @Nullable BlockPos chest, boolean test) {
        if (ACTIVE.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.shifuchests.ocupado").withStyle(ChatFormatting.RED), true);
            return false;
        }
        BauConfig config = ConfigManager.bau(tipo);
        if (config == null || config.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.shifuchests.vazio").withStyle(ChatFormatting.RED), true);
            return false;
        }

        RandomSource random = player.getRandom();
        Recompensa reward = config.roll(random);
        Pokemon pokemon;
        RewardDisplay winner;
        try {
            pokemon = reward.tipo == Recompensa.Tipo.POKEMON ? createPokemon(reward, config, player, random) : null;
            winner = pokemon != null ? pokemonDisplay(reward, pokemon) : staticDisplay(reward);
        } catch (Exception e) {
            ShifuChests.LOGGER.error("Erro ao preparar a recompensa '{}' do bau {}", reward.describe(), tipo, e);
            player.displayClientMessage(Component.translatable("message.shifuchests.erro").withStyle(ChatFormatting.RED), false);
            return false;
        }

        int winnerIndex = WINNER_INDEX_MIN + random.nextInt(5);
        List<RewardDisplay> strip = buildStrip(config, winner, winnerIndex, random);
        int duration = ConfigManager.geral().duracaoRoletaTicks();
        PacketDistributor.sendToPlayer(player, new OpenRoulettePayload(config.visual, config.displayName(), strip, winnerIndex,
                duration, ConfigManager.geral().permitirPular || test, test));

        ServerLevel level = player.serverLevel();
        ACTIVE.put(player.getUUID(), new ActiveRoll(player.getUUID(), player, tipo, config, reward, pokemon, winner,
                player.server.getTickCount() + duration + DELIVERY_DELAY_TICKS, chest,
                chest != null ? level.dimension() : null, test));

        if (chest != null && isChest(level, chest)) {
            BlockState state = level.getBlockState(chest);
            level.blockEvent(chest, state.getBlock(), BauBlockEntity.EVENT_SPIN, duration);
            playAt(level, chest, null, SoundEvents.IRON_TRAPDOOR_OPEN, 1.0F, 0.7F);
            playAt(level, chest, null, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
            playAt(level, chest, null, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
        }
        if (!test) {
            ShifuChests.LOGGER.info("{} abriu o bau {} e vai receber: {}{}", player.getGameProfile().getName(), tipo,
                    reward.describe(), winner.shiny() ? " (SHINY)" : "");
        }
        return true;
    }

    private static Pokemon createPokemon(Recompensa reward, BauConfig config, ServerPlayer player, RandomSource random) {
        double shinyChance = reward.chanceShiny != null ? reward.chanceShiny : config.chanceShiny;
        Integer level = reward.nivel != null ? Integer.valueOf(reward.nivel.roll(random)) : null;
        return CobblemonBridge.create(reward.pokemon, level, random.nextDouble() < shinyChance, player);
    }

    private static RewardDisplay pokemonDisplay(Recompensa reward, Pokemon pokemon) {
        Component name = reward.nome != null ? reward.nome : CobblemonBridge.speciesName(pokemon);
        return new RewardDisplay(CobblemonBridge.speciesId(pokemon), CobblemonBridge.aspects(pokemon), ItemStack.EMPTY,
                name, reward.raridade(), pokemon.getShiny(), pokemon.getLevel(), (float) reward.chance());
    }

    private static RewardDisplay staticDisplay(Recompensa reward) {
        Component name = reward.nome != null ? reward.nome : reward.item.getHoverName();
        return new RewardDisplay(null, List.of(), reward.item.copy(), name, reward.raridade(), false, 0, (float) reward.chance());
    }

    /** Aparencia "de vitrine" de uma recompensa (sem sortear shiny/nivel), usada nos outros cartoes e na previa. */
    private static RewardDisplay template(Recompensa reward) {
        if (reward.tipo != Recompensa.Tipo.POKEMON) return DISPLAY_CACHE.computeIfAbsent(reward, RollManager::staticDisplay);
        RewardDisplay cached = DISPLAY_CACHE.get(reward);
        if (cached != null) return cached;
        // Premio que sempre sai shiny (chance_shiny 1 ou "shiny=yes") aparece com o modelo shiny na previa e na roleta
        boolean sempreShiny = reward.chanceShiny != null && reward.chanceShiny >= 1.0;
        Pokemon pokemon = CobblemonBridge.create(reward.pokemon, null, sempreShiny, null);
        RewardDisplay display = pokemonDisplay(reward, pokemon);
        display = new RewardDisplay(display.species(), display.aspects(), display.icon(), display.name(), display.raridade(),
                display.shiny(), 0, display.chance());
        if (!CobblemonBridge.specIsRandom(reward.pokemon)) DISPLAY_CACHE.put(reward, display);
        return display;
    }

    private static List<RewardDisplay> buildStrip(BauConfig config, RewardDisplay winner, int winnerIndex, RandomSource random) {
        List<RewardDisplay> strip = new ArrayList<>(winnerIndex + CARDS_AFTER_WINNER + 1);
        for (int i = 0; i < winnerIndex + CARDS_AFTER_WINNER + 1; i++) {
            strip.add(i == winnerIndex ? winner : template(config.roll(random)));
        }
        // "Quase!": as vezes o premio mais raro do bau aparece colado no que saiu
        if (random.nextFloat() < 0.45F) {
            Recompensa rarest = config.recompensas.stream().min(Comparator.comparingDouble(Recompensa::chance)).orElse(null);
            if (rarest != null) strip.set(winnerIndex + (random.nextBoolean() ? 1 : -1), template(rarest));
        }
        return strip;
    }

    // ---------------------------------------------------------------- previa

    public static void sendPreview(ServerPlayer player, BauTier tier) {
        sendPreview(player, tier.id);
    }

    public static void sendPreview(ServerPlayer player, String tipo) {
        int now = player.server.getTickCount();
        Integer last = PREVIEW_COOLDOWN.get(player.getUUID());
        if (last != null && now - last < PREVIEW_COOLDOWN_TICKS && now >= last) return;
        PREVIEW_COOLDOWN.put(player.getUUID(), now);
        BauConfig config = ConfigManager.bau(tipo);
        if (config == null) {
            player.displayClientMessage(Component.translatable("message.shifuchests.vazio").withStyle(ChatFormatting.RED), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new PreviewPayload(config.visual, config.displayName(), previewList(config)));
    }

    /** Premios do bau na ordem da vitrine (mais raros primeiro). */
    public static List<RewardDisplay> previewList(BauTier tier) {
        return previewList(ConfigManager.bau(tier));
    }

    public static List<RewardDisplay> previewList(BauConfig config) {
        List<RewardDisplay> rewards = new ArrayList<>();
        List<Recompensa> sorted = new ArrayList<>(config.recompensas);
        // Os mais raros primeiro
        sorted.sort(Comparator.comparingDouble(Recompensa::chance));
        for (Recompensa reward : sorted) {
            try {
                rewards.add(template(reward));
            } catch (Exception e) {
                ShifuChests.LOGGER.warn("Nao foi possivel mostrar '{}' na previa", reward.describe(), e);
            }
        }
        return rewards;
    }

    /** Se o jogador tem uma roleta girando agora. */
    public static boolean isRolling(UUID player) {
        return ACTIVE.containsKey(player);
    }

    // ---------------------------------------------------------------- entrega

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        int now = server.getTickCount();
        for (ActiveRoll roll : List.copyOf(ACTIVE.values())) {
            if (now >= roll.deliverAt) deliver(server, roll);
        }
    }

    /** O jogador apertou para pular a animacao. */
    public static void skip(ServerPlayer player) {
        ActiveRoll roll = ACTIVE.get(player.getUUID());
        if (roll != null && (roll.test || ConfigManager.geral().permitirPular)) deliver(player.server, roll);
    }

    /** Jogador saindo: entrega antes que os dados dele sejam salvos. */
    public static void deliverNow(ServerPlayer player) {
        ActiveRoll roll = ACTIVE.get(player.getUUID());
        if (roll != null) deliver(player.server, roll);
        PREVIEW_COOLDOWN.remove(player.getUUID());
    }

    public static void deliverAll(MinecraftServer server) {
        for (ActiveRoll roll : List.copyOf(ACTIVE.values())) deliver(server, roll);
    }

    private static void deliver(MinecraftServer server, ActiveRoll roll) {
        if (ACTIVE.remove(roll.playerId) == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(roll.playerId);
        // Jogadores "falsos" de outros mods nao ficam na lista de jogadores
        if (player == null && !roll.opener.hasDisconnected() && !roll.opener.isRemoved()) player = roll.opener;
        if (player == null) {
            ShifuChests.LOGGER.error("Premio do bau {} perdido: jogador {} nao esta online ({})", roll.tipo, roll.playerId,
                    roll.reward.describe());
            return;
        }
        if (!roll.test) {
            try {
                give(server, player, roll);
            } catch (Exception e) {
                ShifuChests.LOGGER.error("Erro ao entregar '{}' para {}", roll.reward.describe(), player.getGameProfile().getName(), e);
                player.sendSystemMessage(Component.translatable("message.shifuchests.erro").withStyle(ChatFormatting.RED));
                return;
            }
        }
        celebrate(server, player, roll);
    }

    private static void give(MinecraftServer server, ServerPlayer player, ActiveRoll roll) {
        Recompensa reward = roll.reward;
        switch (reward.tipo) {
            case POKEMON -> CobblemonBridge.give(player, roll.pokemon);
            case ITEM -> {
                int left = reward.item.getCount();
                int max = Math.max(1, reward.item.getMaxStackSize());
                while (left > 0) {
                    ItemStack stack = reward.item.copyWithCount(Math.min(max, left));
                    left -= stack.getCount();
                    if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
                }
                player.inventoryMenu.broadcastChanges();
            }
            case COMANDO -> {
                for (String command : reward.comandos) {
                    String finalCommand = command
                            .replace("{jogador}", player.getGameProfile().getName())
                            .replace("{player}", player.getGameProfile().getName())
                            .replace("{uuid}", player.getStringUUID());
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), finalCommand);
                }
            }
        }
        ShifuChests.LOGGER.info("{} recebeu do bau {}: {}", player.getGameProfile().getName(), roll.tipo, reward.describe());
    }

    // ---------------------------------------------------------------- efeitos

    private static void celebrate(MinecraftServer server, ServerPlayer player, ActiveRoll roll) {
        RewardDisplay display = roll.display;
        Raridade raridade = display.raridade();
        MutableComponent rewardName = rewardName(display);

        if (!roll.test) {
            player.sendSystemMessage(Component.translatable("message.shifuchests.ganhou", rewardName).withStyle(ChatFormatting.GRAY));
        }

        // Efeitos no bau para quem esta em volta (o jogador ja tem os da tela)
        if (roll.chest != null && roll.dimension != null) {
            ServerLevel level = server.getLevel(roll.dimension);
            if (level != null && isChest(level, roll.chest)) {
                level.blockEvent(roll.chest, level.getBlockState(roll.chest).getBlock(), BauBlockEntity.EVENT_REVEAL,
                        BauBlockEntity.revealParam(raridade, display.shiny()));
                playAt(level, roll.chest, player, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);
                playAt(level, roll.chest, player, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
                if (raridade.atLeast(Raridade.EPICO)) playAt(level, roll.chest, player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                if (raridade.atLeast(Raridade.LENDARIO)) {
                    playAt(level, roll.chest, player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.5F, 0.8F);
                    playAt(level, roll.chest, player, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.5F, 1.0F);
                }
                if (display.shiny()) {
                    SoundEvent chime = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "particle.shiny_chime"));
                    playAt(level, roll.chest, player, chime != null ? chime : SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.0F);
                }
            }
        }

        ConfigGeral geral = ConfigManager.geral();
        boolean announce = geral.anunciarNoChat && !roll.test
                && (raridade.atLeast(geral.raridadeMinimaAnuncio) || (geral.anunciarShiny && display.shiny()));
        if (announce) {
            String key = display.shiny() ? "message.shifuchests.anuncio_shiny" : "message.shifuchests.anuncio";
            MutableComponent message = Component.literal("★ ").withColor(raridade.color)
                    .append(Component.translatable(key,
                            player.getDisplayName().copy().withStyle(ChatFormatting.WHITE),
                            roll.config.displayName().copy().withColor(roll.config.cor),
                            rewardName).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(raridade.displayName().copy().withColor(raridade.color))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static MutableComponent rewardName(RewardDisplay display) {
        MutableComponent name = display.name().copy().withColor(display.raridade().color);
        if (!display.isPokemon() && display.icon().getCount() > 1) {
            name = Component.literal(display.icon().getCount() + "x ").withColor(display.raridade().color).append(name);
        }
        if (display.isPokemon() && display.level() > 0) {
            name.append(Component.literal(" Nv. " + display.level()).withStyle(ChatFormatting.GRAY));
        }
        return name;
    }

    private static boolean isChest(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BauBlock;
    }

    /** Toca no bau para todo mundo perto, menos {@code except} (que ja ouve os sons da tela). */
    private static void playAt(ServerLevel level, BlockPos pos, @Nullable ServerPlayer except, SoundEvent sound, float volume, float pitch) {
        level.playSound(except, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void playAt(ServerLevel level, BlockPos pos, @Nullable ServerPlayer except,
                               net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
        playAt(level, pos, except, sound.value(), volume, pitch);
    }

    private RollManager() {
    }
}

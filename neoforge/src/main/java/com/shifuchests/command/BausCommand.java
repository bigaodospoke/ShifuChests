package com.shifuchests.command;

import com.shifuchests.bau.BauBlock;
import com.shifuchests.bau.BauBlockEntity;
import com.shifuchests.bau.ChaveItem;
import com.shifuchests.config.BauConfig;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.config.Raridade;
import com.shifuchests.config.Recompensa;
import com.shifuchests.registry.ModRegistry;
import com.shifuchests.sorteio.RollManager;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /baus (ou /shifuchests) - administracao dos baus sem precisar abrir os arquivos.
 * Tudo exige permissao de operador (nivel 2), menos /baus previa.
 */
public final class BausCommand {
    private static final SimpleCommandExceptionType BAU_INVALIDO = new SimpleCommandExceptionType(
            Component.literal("Bau desconhecido. Os baus sao os arquivos .json de config/shifuchests/ (use TAB para ver a lista)"));
    private static final SimpleCommandExceptionType RARIDADE_INVALIDA = new SimpleCommandExceptionType(
            Component.literal("Raridade desconhecida. Use: auto, comum, incomum, raro, epico, lendario ou mitico"));

    private static final SuggestionProvider<CommandSourceStack> BAUS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ConfigManager.ids(), builder);
    private static final SuggestionProvider<CommandSourceStack> RARIDADES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of("auto"), Arrays.stream(Raridade.values()).map(r -> r.id)), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("baus")
                .executes(BausCommand::help)
                .then(Commands.literal("previa").then(bau().executes(ctx -> {
                    RollManager.sendPreview(ctx.getSource().getPlayerOrException(), tier(ctx).id);
                    return 1;
                })))
                .then(Commands.literal("recarregar").requires(s -> s.hasPermission(2)).executes(BausCommand::reload))
                .then(Commands.literal("chave").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jogadores", EntityArgument.players()).then(bau()
                                .executes(ctx -> giveKeys(ctx, 1))
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 6400))
                                        .executes(ctx -> giveKeys(ctx, IntegerArgumentType.getInteger(ctx, "quantidade")))))))
                .then(Commands.literal("listar").requires(s -> s.hasPermission(2)).then(bau().executes(BausCommand::list)))
                .then(Commands.literal("adicionar").requires(s -> s.hasPermission(2)).then(bau()
                        .then(Commands.literal("pokemon").then(Commands.argument("peso", DoubleArgumentType.doubleArg(0.0001))
                                .then(Commands.argument("raridade", StringArgumentType.word()).suggests(RARIDADES)
                                        .then(Commands.argument("pokemon", StringArgumentType.greedyString())
                                                .executes(BausCommand::addPokemon)))))
                        .then(Commands.literal("item").then(Commands.argument("peso", DoubleArgumentType.doubleArg(0.0001))
                                .then(Commands.argument("raridade", StringArgumentType.word()).suggests(RARIDADES)
                                        .executes(BausCommand::addHeldItem))))))
                .then(Commands.literal("remover").requires(s -> s.hasPermission(2)).then(bau()
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(BausCommand::remove))))
                .then(Commands.literal("testar").requires(s -> s.hasPermission(2)).then(bau().executes(ctx -> {
                    RollManager.start(ctx.getSource().getPlayerOrException(), tier(ctx).id, null, true);
                    return 1;
                })))
                .then(Commands.literal("girar").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jogadores", EntityArgument.players()).then(bau().executes(BausCommand::spinFor))))
                .then(Commands.literal("bloco").requires(s -> s.hasPermission(2)).then(bau()
                        .executes(ctx -> giveChest(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> giveChest(ctx, EntityArgument.getPlayers(ctx, "jogadores"))))))
                .then(Commands.literal("definir").requires(s -> s.hasPermission(2)).then(bau().executes(BausCommand::setLookedAt)))
                .then(Commands.literal("tipos").requires(s -> s.hasPermission(2)).executes(BausCommand::listTypes)));
        // Mesmo comando com o nome do mod
        dispatcher.register(Commands.literal("shifuchests").executes(BausCommand::help).redirect(root));
    }

    /** /baus sozinho: lista os subcomandos que quem digitou pode usar. */
    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSystemMessage(Component.literal("=== Shifu Chests ===").withStyle(ChatFormatting.GOLD));
        helpLine(source, "/baus previa <bau>", "mostra os premios e as chances");
        if (source.hasPermission(2)) {
            helpLine(source, "/baus chave <jogador> <bau> [qtd]", "da chaves");
            helpLine(source, "/baus adicionar <bau> pokemon <peso> <raridade> <pokemon>", "ex.: ... 2 lendario charizard shiny=yes");
            helpLine(source, "/baus adicionar <bau> item <peso> <raridade>", "adiciona o item da sua mao");
            helpLine(source, "/baus listar <bau>", "premios, numeros e chances");
            helpLine(source, "/baus remover <bau> <numero>", "remove um premio");
            helpLine(source, "/baus recarregar", "le config/shifuchests/ de novo");
            helpLine(source, "/baus testar <bau>", "gira sem gastar chave e sem ganhar");
            helpLine(source, "/baus girar <jogador> <bau>", "abre uma roleta de graca");
            helpLine(source, "/baus bloco <bau> [jogador]", "da o bloco do bau ja com o tipo certo");
            helpLine(source, "/baus definir <bau>", "muda o tipo do bau que voce esta olhando");
            helpLine(source, "/baus tipos", "lista todos os baus da config");
            source.sendSystemMessage(Component.literal("<bau> = nome de um arquivo .json de config/shifuchests/. Guia: config/shifuchests/COMO_CONFIGURAR.txt")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    private static void helpLine(CommandSourceStack source, String command, String description) {
        String suggestion = command.substring(0, command.indexOf(' ', 6) > 0 ? command.indexOf(' ', 6) + 1 : command.length());
        source.sendSystemMessage(Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para escrever"))))
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> bau() {
        return Commands.argument("bau", StringArgumentType.word()).suggests(BAUS);
    }

    /** Config do bau escolhido no comando (erro se nao existe arquivo com esse nome). */
    private static BauConfig tier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig config = ConfigManager.bau(StringArgumentType.getString(ctx, "bau").toLowerCase(Locale.ROOT));
        if (config == null) throw BAU_INVALIDO.create();
        return config;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = ConfigManager.load(ctx.getSource().getServer().registryAccess());
        StringBuilder resumo = new StringBuilder();
        for (BauConfig config : ConfigManager.baus()) {
            if (!resumo.isEmpty()) resumo.append(", ");
            resumo.append(config.id).append(": ").append(config.recompensas.size());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Configuracao recarregada (" + resumo + " recompensas)")
                .withStyle(ChatFormatting.GREEN), true);
        sendWarnings(ctx.getSource(), warnings);
        return 1;
    }

    private static int giveKeys(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        BauConfig config = tier(ctx);
        Component nomeChave = ConfigManager.nomeChave(config.id);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "jogadores");
        for (ServerPlayer player : players) {
            int left = amount;
            while (left > 0) {
                ItemStack stack = ChaveItem.criar(ModRegistry.KEYS.get(config.visual).get(), config.id, config.isPadrao(),
                        config.displayName(), config.cor, Math.min(64, left));
                left -= stack.getCount();
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            player.displayClientMessage(Component.translatable("message.shifuchests.recebeu_chave", amount, nomeChave)
                    .withStyle(ChatFormatting.GREEN), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] " + amount + "x ").append(nomeChave)
                .append(" para " + players.size() + " jogador(es)"), true);
        return players.size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig config = tier(ctx);
        CommandSourceStack source = ctx.getSource();
        source.sendSystemMessage(Component.literal("=== ").withStyle(ChatFormatting.GOLD)
                .append(config.displayName().copy().withColor(config.cor))
                .append(Component.literal(" (" + config.recompensas.size() + " recompensas, arquivo config/shifuchests/" + config.id + ".json) ===")
                        .withStyle(ChatFormatting.GOLD)));
        int index = 0;
        for (Recompensa r : config.recompensas) {
            index++;
            MutableComponent line = Component.literal(index + ". ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(Locale.ROOT, "%.2f%% ", r.chance() * 100)).withStyle(ChatFormatting.WHITE))
                    .append(r.raridade().displayName().copy().withColor(r.raridade().color))
                    .append(Component.literal(" " + r.describe()).withStyle(ChatFormatting.GRAY));
            if (r.nome != null) line.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY)).append(r.nome)
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
            String removeCommand = "/baus remover " + config.id + " " + index;
            line.append(Component.literal(" [x]").withStyle(style -> style.withColor(ChatFormatting.RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, removeCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Remover esta recompensa")))));
            source.sendSystemMessage(line);
        }
        if (config.recompensas.isEmpty()) {
            source.sendSystemMessage(Component.literal("Nenhuma recompensa. Use /baus adicionar " + config.id + " ...")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return config.recompensas.size();
    }

    private static int addPokemon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig tier = tier(ctx);
        String pokemon = StringArgumentType.getString(ctx, "pokemon").trim();
        JsonObject reward = new JsonObject();
        reward.addProperty("pokemon", pokemon);
        // "pikachu level=20" -> o nivel fica no proprio texto; senao usa o nivel_padrao do bau
        return add(ctx, tier, reward);
    }

    private static int addHeldItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig tier = tier(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Segure na mao principal o item (com a quantidade) que vai ser o premio."));
            return 0;
        }
        JsonObject reward = BauConfig.itemToJson(held, ctx.getSource().getServer().registryAccess());
        return add(ctx, tier, reward);
    }

    private static int add(CommandContext<CommandSourceStack> ctx, BauConfig tier, JsonObject reward) throws CommandSyntaxException {
        double peso = DoubleArgumentType.getDouble(ctx, "peso");
        String raridadeArg = StringArgumentType.getString(ctx, "raridade");
        reward.addProperty("peso", peso);
        if (!raridadeArg.equalsIgnoreCase("auto")) {
            Raridade raridade = Raridade.byId(raridadeArg);
            if (raridade == null) throw RARIDADE_INVALIDA.create();
            reward.addProperty("raridade", raridade.id);
        }
        int before = tier.recompensas.size();
        try {
            List<String> warnings = ConfigManager.addReward(tier.id, reward, ctx.getSource().getServer().registryAccess());
            BauConfig config = ConfigManager.bau(tier.id);
            if (config.recompensas.size() > before) {
                Recompensa added = config.recompensas.get(config.recompensas.size() - 1);
                ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Adicionado ao " + tier.id + ": " + added.describe() + " (")
                        .append(String.format(Locale.ROOT, "%.2f%%, ", added.chance() * 100))
                        .append(added.raridade().displayName().copy().withColor(added.raridade().color))
                        .append(")").withStyle(ChatFormatting.GREEN), true);
            } else {
                ctx.getSource().sendFailure(Component.literal("A recompensa foi salva no arquivo mas e invalida e esta sendo ignorada:"));
            }
            sendWarnings(ctx.getSource(), warnings);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Erro ao salvar: " + e.getMessage()));
            return 0;
        }
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig tier = tier(ctx);
        int index = IntegerArgumentType.getInteger(ctx, "numero");
        BauConfig config = tier;
        if (index > config.recompensas.size()) {
            ctx.getSource().sendFailure(Component.literal("O bau " + tier.id + " so tem " + config.recompensas.size() + " recompensas."));
            return 0;
        }
        String description = config.recompensas.get(index - 1).describe();
        try {
            List<String> warnings = ConfigManager.removeReward(tier.id, index, ctx.getSource().getServer().registryAccess());
            ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Removido do " + tier.id + ": " + description)
                    .withStyle(ChatFormatting.YELLOW), true);
            sendWarnings(ctx.getSource(), warnings);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Erro ao salvar: " + e.getMessage()));
            return 0;
        }
    }

    private static int spinFor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig tier = tier(ctx);
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "jogadores")) {
            if (RollManager.start(player, tier.id, null, false)) count++;
        }
        int started = count;
        ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Roleta do " + tier.id + " aberta para " + started + " jogador(es)"), true);
        return count;
    }

    /** /baus bloco <bau> [jogadores]: bloco do bau com o tipo gravado (vale ao colocar no mundo). */
    private static int giveChest(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        BauConfig config = tier(ctx);
        for (ServerPlayer player : players) {
            ItemStack stack = new ItemStack(ModRegistry.CHEST_ITEMS.get(config.visual).get());
            if (!config.isPadrao()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", "shifuchests:bau");
                tag.putString(BauBlockEntity.TAG_TIPO, config.id);
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
                stack.set(DataComponents.ITEM_NAME, config.displayName().copy().withColor(config.cor));
            }
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Bloco do bau ").append(ConfigManager.nomeBau(config.id))
                .append(" para " + players.size() + " jogador(es)"), true);
        return players.size();
    }

    /** /baus definir <bau>: muda o tipo do bau que o jogador esta olhando. */
    private static int setLookedAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BauConfig config = tier(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HitResult hit = player.pick(8, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("Olhe para um bau (ate 8 blocos de distancia)."));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(player.level().getBlockState(pos).getBlock() instanceof BauBlock)
                || !(player.level().getBlockEntity(pos) instanceof BauBlockEntity be)) {
            ctx.getSource().sendFailure(Component.literal("Isso nao e um bau do Shifu Chests."));
            return 0;
        }
        be.setTipo(config.id);
        ctx.getSource().sendSuccess(() -> Component.literal("[Shifu Chests] Este bau agora e: ").append(ConfigManager.nomeBau(config.id))
                .append(Component.literal(" (" + config.id + ")").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    /** /baus tipos: todos os baus da config, com quantidade de premios. */
    private static int listTypes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSystemMessage(Component.literal("=== Baus (" + ConfigManager.baus().size() + ") ===").withStyle(ChatFormatting.GOLD));
        for (BauConfig config : ConfigManager.baus()) {
            source.sendSystemMessage(Component.literal(config.id + " ").withStyle(ChatFormatting.YELLOW)
                    .append(config.displayName().copy().withColor(config.cor))
                    .append(Component.literal(" - " + config.recompensas.size() + " premios, modelo " + config.visual.id
                            + (config.pago ? ", pago" : "")).withStyle(ChatFormatting.GRAY)));
        }
        return ConfigManager.baus().size();
    }

    private static void sendWarnings(CommandSourceStack source, List<String> warnings) {
        if (warnings.isEmpty()) return;
        source.sendSystemMessage(Component.literal("Avisos (" + warnings.size() + "):").withStyle(ChatFormatting.GOLD));
        for (String warning : warnings) {
            source.sendSystemMessage(Component.literal(" - " + warning).withStyle(ChatFormatting.YELLOW));
        }
    }

    private BausCommand() {
    }
}

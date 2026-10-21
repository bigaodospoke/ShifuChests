package com.shifuchests.config;

import com.shifuchests.cobblemon.CobblemonBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Um arquivo config/shifuchests/&lt;caixa&gt;.json ja validado. Cada arquivo e um tipo de caixa (o nome do arquivo e o id).
 * A caixa no mundo e qualquer bloco marcado com /baus definir; a chave e o item de "chave" com o id da caixa.
 */
public final class BauConfig {
    public static final int COR_PADRAO = 0xFFB300;
    public static final ResourceLocation CHAVE_PADRAO = ResourceLocation.withDefaultNamespace("tripwire_hook");

    /** Id da caixa = nome do arquivo sem .json (ex.: "miticos", "lendarios_1_3"). */
    public final String id;
    /** Cor RGB da caixa em textos, anuncios, roleta e efeitos ("cor" no arquivo). */
    public final int cor;
    @Nullable private final Component nome;
    /** Item usado como chave ("chave" no arquivo), ex.: hancik:lendary_key. */
    public final ResourceLocation chaveItem;
    /** Nome da caixa no Pebbles Crates: chaves antigas dela tambem abrem esta ("chave_pebbles"). */
    @Nullable public final String chavePebbles;
    /** Chance de shiny para recompensas Pokemon que nao definem a propria. */
    public final double chanceShiny;
    @Nullable public final Recompensa.NivelRange nivelPadrao;
    /** Marca a caixa como de chave vendida: bloqueia namespaces proibidos (geral.json). */
    public final boolean pago;
    public final List<Recompensa> recompensas;
    /** JSON do arquivo, usado pelos comandos para adicionar/remover e salvar. */
    final JsonObject json;

    /** "chave_brilho": true deixa a chave com brilho de encantamento (para diferenciar chaves que usam o mesmo item). */
    public boolean chaveBrilho() {
        return json.has("chave_brilho") && json.get("chave_brilho").getAsBoolean();
    }

    /**
     * Quantos IVs perfeitos (31) o Pokemon sorteado tem no minimo: "ivs_perfeitos" da recompensa ou, se ela nao
     * tiver, o do bau. 0 = IVs normais do Cobblemon.
     */
    public int ivsPerfeitos(Recompensa recompensa) {
        JsonObject origem = recompensa.json.has("ivs_perfeitos") ? recompensa.json : json;
        return origem.has("ivs_perfeitos") ? Math.max(0, Math.min(6, origem.get("ivs_perfeitos").getAsInt())) : 0;
    }
    private final double pesoTotal;

    private BauConfig(String id, int cor, @Nullable Component nome, ResourceLocation chaveItem, @Nullable String chavePebbles,
                      double chanceShiny, @Nullable Recompensa.NivelRange nivelPadrao, boolean pago, List<Recompensa> recompensas,
                      JsonObject json) {
        this.id = id;
        this.cor = cor;
        this.nome = nome;
        this.chaveItem = chaveItem;
        this.chavePebbles = chavePebbles;
        this.chanceShiny = chanceShiny;
        this.nivelPadrao = nivelPadrao;
        this.pago = pago;
        this.recompensas = Collections.unmodifiableList(recompensas);
        this.json = json;
        double total = 0;
        for (Recompensa r : recompensas) total += r.peso;
        this.pesoTotal = total;
        for (Recompensa r : recompensas) {
            r.chance = total > 0 ? r.peso / total : 0;
            r.raridade = r.raridadeManual != null ? r.raridadeManual : Raridade.fromChance(r.chance);
        }
    }

    public Component displayName() {
        return nome != null ? nome : Component.literal(id);
    }

    public boolean isEmpty() {
        return recompensas.isEmpty() || pesoTotal <= 0;
    }

    /** Sorteia uma recompensa pelo peso. */
    public Recompensa roll(RandomSource random) {
        double value = random.nextDouble() * pesoTotal;
        for (Recompensa r : recompensas) {
            value -= r.peso;
            if (value < 0) return r;
        }
        return recompensas.get(recompensas.size() - 1);
    }

    static BauConfig empty(String id) {
        JsonObject json = new JsonObject();
        json.add("recompensas", new JsonArray());
        return new BauConfig(id, COR_PADRAO, null, CHAVE_PADRAO, null, 0.01, null, false, new ArrayList<>(), json);
    }

    static BauConfig parse(String id, JsonObject json, HolderLookup.Provider registries, ConfigGeral geral,
                           List<String> warnings) {
        String file = id + ".json";
        int cor = COR_PADRAO;
        if (json.has("cor")) {
            try {
                cor = Integer.parseInt(json.get("cor").getAsString().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                warnings.add(file + ": cor invalida \"" + json.get("cor") + "\" (use \"#RRGGBB\")");
            }
        }
        Component nome = json.has("nome") ? Texto.parse(json.get("nome").getAsString()) : null;
        ResourceLocation chave = CHAVE_PADRAO;
        if (json.has("chave")) {
            ResourceLocation lida = ResourceLocation.tryParse(json.get("chave").getAsString());
            if (lida == null || BuiltInRegistries.ITEM.getOptional(lida).isEmpty()) {
                warnings.add(file + ": chave \"" + json.get("chave").getAsString() + "\" nao e um item que exista (usando o gancho)");
            } else {
                chave = lida;
            }
        }
        String chavePebbles = json.has("chave_pebbles") ? json.get("chave_pebbles").getAsString() : null;
        double chanceShiny = json.has("chance_shiny") ? clamp01(json.get("chance_shiny").getAsDouble()) : 0.01;
        Recompensa.NivelRange nivelPadrao = null;
        try {
            nivelPadrao = Recompensa.NivelRange.parse(json.get("nivel_padrao"));
        } catch (RuntimeException e) {
            warnings.add(file + ": nivel_padrao invalido: " + json.get("nivel_padrao"));
        }
        boolean pago = json.has("pago") && json.get("pago").getAsBoolean();

        List<Recompensa> recompensas = new ArrayList<>();
        JsonElement list = json.get("recompensas");
        if (list == null || !list.isJsonArray()) {
            warnings.add(file + ": falta a lista \"recompensas\"");
            json.add("recompensas", new JsonArray());
        } else {
            int index = 0;
            for (JsonElement element : list.getAsJsonArray()) {
                index++;
                if (!element.isJsonObject()) {
                    warnings.add(file + ": recompensa #" + index + " nao e um objeto { }");
                    continue;
                }
                try {
                    Recompensa r = parseRecompensa(element.getAsJsonObject(), registries, nivelPadrao);
                    String bloqueio = pago ? namespaceProibido(r, geral) : null;
                    if (bloqueio != null) {
                        warnings.add(file + ": recompensa #" + index + " ignorada: itens de \"" + bloqueio
                                + "\" nao podem ficar em caixa paga (veja geral.json)");
                        continue;
                    }
                    recompensas.add(r);
                } catch (RuntimeException e) {
                    warnings.add(file + ": recompensa #" + index + " ignorada: " + e.getMessage());
                }
            }
        }
        return new BauConfig(id, cor, nome, chave, chavePebbles, chanceShiny, nivelPadrao, pago, recompensas, json);
    }

    static Recompensa parseRecompensa(JsonObject obj, HolderLookup.Provider registries,
                                      @Nullable Recompensa.NivelRange nivelPadrao) {
        double peso = obj.has("peso") ? obj.get("peso").getAsDouble() : 1;
        if (peso <= 0) throw new IllegalArgumentException("peso precisa ser maior que 0");
        Raridade raridade = null;
        if (obj.has("raridade")) {
            raridade = Raridade.byId(obj.get("raridade").getAsString());
            if (raridade == null) throw new IllegalArgumentException("raridade desconhecida \"" + obj.get("raridade").getAsString()
                    + "\" (use comum, incomum, raro, epico, lendario ou mitico)");
        }
        Component nome = obj.has("nome") ? Texto.parse(obj.get("nome").getAsString()) : null;

        if (obj.has("pokemon")) {
            String spec = obj.get("pokemon").getAsString().trim();
            String erro = CobblemonBridge.validateSpec(spec);
            if (erro != null) throw new IllegalArgumentException(erro);
            Recompensa.NivelRange nivel = obj.has("nivel") ? Recompensa.NivelRange.parse(obj.get("nivel")) : nivelPadrao;
            Double chanceShiny = obj.has("chance_shiny") ? clamp01(obj.get("chance_shiny").getAsDouble()) : null;
            // Itens que vem junto com o Pokemon (ex.: a Mega Pedra dele)
            List<ItemStack> extras = new ArrayList<>();
            if (obj.has("com_itens")) {
                for (JsonElement e : obj.getAsJsonArray("com_itens")) extras.add(parseItem(e.getAsJsonObject(), registries));
            }
            return new Recompensa(Recompensa.Tipo.POKEMON, spec, nivel, chanceShiny, ItemStack.EMPTY, extras, List.of(),
                    nome, peso, raridade, obj);
        }
        if (obj.has("item")) {
            ItemStack stack = parseItem(obj, registries);
            List<ItemStack> extras = new ArrayList<>();
            if (obj.has("com_itens")) {
                for (JsonElement e : obj.getAsJsonArray("com_itens")) extras.add(parseItem(e.getAsJsonObject(), registries));
            }
            return new Recompensa(Recompensa.Tipo.ITEM, null, null, null, stack, extras, List.of(), nome, peso, raridade, obj);
        }
        if (obj.has("comando")) {
            List<String> comandos = new ArrayList<>();
            JsonElement c = obj.get("comando");
            if (c.isJsonArray()) {
                for (JsonElement e : c.getAsJsonArray()) comandos.add(stripSlash(e.getAsString()));
            } else {
                comandos.add(stripSlash(c.getAsString()));
            }
            if (comandos.isEmpty()) throw new IllegalArgumentException("\"comando\" esta vazio");
            ItemStack icone = new ItemStack(Items.PAPER);
            if (obj.has("icone")) {
                ResourceLocation id = ResourceLocation.parse(obj.get("icone").getAsString());
                icone = new ItemStack(BuiltInRegistries.ITEM.getOptional(id)
                        .orElseThrow(() -> new IllegalArgumentException("icone desconhecido: " + id)));
            }
            if (nome == null) nome = Component.literal(comandos.get(0));
            return new Recompensa(Recompensa.Tipo.COMANDO, null, null, null, icone, List.of(), comandos, nome, peso, raridade, obj);
        }
        throw new IllegalArgumentException("precisa ter \"pokemon\", \"item\" ou \"comando\"");
    }

    private static ItemStack parseItem(JsonObject obj, HolderLookup.Provider registries) {
        JsonObject codecJson = new JsonObject();
        codecJson.addProperty("id", obj.get("item").getAsString());
        codecJson.addProperty("count", 1);
        if (obj.has("componentes")) codecJson.add("components", obj.get("componentes"));
        ItemStack stack = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), codecJson)
                .getOrThrow(msg -> new IllegalArgumentException("item invalido (" + obj.get("item").getAsString() + "): " + msg));
        int quantidade = obj.has("quantidade") ? obj.get("quantidade").getAsInt() : 1;
        if (quantidade < 1) throw new IllegalArgumentException("quantidade precisa ser 1 ou mais");
        stack.setCount(quantidade);
        return stack;
    }

    /** Monta o JSON de uma recompensa de item a partir do item na mao (com encantamentos, nome etc). */
    public static JsonObject itemToJson(ItemStack stack, HolderLookup.Provider registries) {
        JsonObject encoded = ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),
                stack.copyWithCount(1)).getOrThrow().getAsJsonObject();
        JsonObject obj = new JsonObject();
        obj.addProperty("item", encoded.get("id").getAsString());
        obj.addProperty("quantidade", stack.getCount());
        if (encoded.has("components") && !encoded.getAsJsonObject("components").isEmpty()) {
            obj.add("componentes", encoded.get("components"));
        }
        return obj;
    }

    @Nullable
    private static String namespaceProibido(Recompensa r, ConfigGeral geral) {
        List<ItemStack> itens = new ArrayList<>(r.extras);
        if (r.tipo == Recompensa.Tipo.ITEM) itens.add(r.item);
        for (ItemStack stack : itens) {
            String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            if (geral.namespacesProibidosEmBausPagos.contains(namespace)) return namespace;
        }
        return null;
    }

    private static String stripSlash(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}

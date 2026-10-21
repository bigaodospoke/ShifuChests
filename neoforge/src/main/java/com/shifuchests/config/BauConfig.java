package com.shifuchests.config;

import com.shifuchests.bau.BauTier;
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
 * Um arquivo config/shifuchests/&lt;bau&gt;.json ja validado. Cada arquivo e um tipo de bau (o nome do arquivo
 * e o id); {@link #visual} diz qual dos tres modelos (normal, medio, premium) o bau e a chave usam.
 */
public final class BauConfig {
    /** Id do bau = nome do arquivo sem .json (ex.: "premium", "lendarios_1_2"). */
    public final String id;
    /** Modelo do bloco e da chave. */
    public final BauTier visual;
    /** Cor RGB do bau em textos e anuncios ("cor" no arquivo, senao a do modelo). */
    public final int cor;
    @Nullable private final Component nome;
    /** Chance de shiny para recompensas Pokemon que nao definem a propria. */
    public final double chanceShiny;
    @Nullable public final Recompensa.NivelRange nivelPadrao;
    /** Marca o bau como de chave vendida: bloqueia namespaces proibidos (geral.json). */
    public final boolean pago;
    public final List<Recompensa> recompensas;
    /** JSON do arquivo, usado pelos comandos para adicionar/remover e salvar. */
    final JsonObject json;
    private final double pesoTotal;

    private BauConfig(String id, BauTier visual, int cor, @Nullable Component nome, double chanceShiny,
                      @Nullable Recompensa.NivelRange nivelPadrao, boolean pago, List<Recompensa> recompensas, JsonObject json) {
        this.id = id;
        this.visual = visual;
        this.cor = cor;
        this.nome = nome;
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
        if (nome != null) return nome;
        BauTier padrao = BauTier.byId(id);
        return padrao != null ? padrao.chestName() : Component.literal(id);
    }

    /** Os tres baus que vem com o mod (normal, medio, premium) usam as chaves sem dados extras. */
    public boolean isPadrao() {
        return BauTier.byId(id) != null;
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
        BauTier visual = visualPadrao(id);
        return new BauConfig(id, visual, visual.color, null, 0.01, null, false, new ArrayList<>(), json);
    }

    private static BauTier visualPadrao(String id) {
        BauTier tier = BauTier.byId(id);
        return tier != null ? tier : BauTier.PREMIUM;
    }

    static BauConfig parse(String id, JsonObject json, HolderLookup.Provider registries, ConfigGeral geral,
                           List<String> warnings) {
        String file = id + ".json";
        BauTier visual = visualPadrao(id);
        if (json.has("visual")) {
            BauTier escolhido = BauTier.byId(json.get("visual").getAsString());
            if (escolhido != null) visual = escolhido;
            else warnings.add(file + ": visual desconhecido \"" + json.get("visual").getAsString() + "\" (use normal, medio ou premium)");
        }
        int cor = visual.color;
        if (json.has("cor")) {
            try {
                cor = Integer.parseInt(json.get("cor").getAsString().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                warnings.add(file + ": cor invalida \"" + json.get("cor") + "\" (use \"#RRGGBB\")");
            }
        }
        Component nome = json.has("nome") ? Texto.parse(json.get("nome").getAsString()) : null;
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
                                + "\" nao podem ficar em bau pago (veja geral.json)");
                        continue;
                    }
                    recompensas.add(r);
                } catch (RuntimeException e) {
                    warnings.add(file + ": recompensa #" + index + " ignorada: " + e.getMessage());
                }
            }
        }
        return new BauConfig(id, visual, cor, nome, chanceShiny, nivelPadrao, pago, recompensas, json);
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
            return new Recompensa(Recompensa.Tipo.POKEMON, spec, nivel, chanceShiny, ItemStack.EMPTY, List.of(),
                    nome, peso, raridade, obj);
        }
        if (obj.has("item")) {
            ItemStack stack = parseItem(obj, registries);
            return new Recompensa(Recompensa.Tipo.ITEM, null, null, null, stack, List.of(), nome, peso, raridade, obj);
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
            return new Recompensa(Recompensa.Tipo.COMANDO, null, null, null, icone, comandos, nome, peso, raridade, obj);
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
        if (r.tipo != Recompensa.Tipo.ITEM) return null;
        String namespace = BuiltInRegistries.ITEM.getKey(r.item.getItem()).getNamespace();
        return geral.namespacesProibidosEmBausPagos.contains(namespace) ? namespace : null;
    }

    private static String stripSlash(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}

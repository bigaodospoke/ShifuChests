package com.shifuchests.config;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Le e salva config/shifuchests/. Na primeira vez copia os arquivos de exemplo que vem no jar.
 * Cada arquivo .json da pasta (menos geral.json) e um tipo de bau, com o nome do arquivo como id.
 * Tudo roda no servidor: o cliente recebe so o que precisa para desenhar a roleta.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] DEFAULT_FILES = {"geral.json", "normal.json", "medio.json", "premium.json", "COMO_CONFIGURAR.txt"};

    private static ConfigGeral geral = ConfigGeral.defaults();
    /** Ids validos: letras minusculas, numeros e _ (viram nome de arquivo e argumento de comando). */
    public static final Pattern ID_VALIDO = Pattern.compile("[a-z0-9_]+");
    /** Na ordem: os tres padrao primeiro, depois os outros em ordem alfabetica. */
    private static final Map<String, BauConfig> BAUS = new LinkedHashMap<>();

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(ShifuChests.MOD_ID);
    }

    public static ConfigGeral geral() {
        return geral;
    }

    /** Config do bau, ou null se nao existe arquivo com esse id. */
    @org.jetbrains.annotations.Nullable
    public static BauConfig bau(String id) {
        return BAUS.get(id);
    }

    public static BauConfig bau(BauTier tier) {
        return BAUS.computeIfAbsent(tier.id, BauConfig::empty);
    }

    /** Nome do bau colorido (ou o id, se o arquivo nao existe). */
    public static net.minecraft.network.chat.Component nomeBau(String id) {
        BauConfig config = BAUS.get(id);
        return config != null ? config.displayName().copy().withColor(config.cor) : net.minecraft.network.chat.Component.literal(id);
    }

    /** Nome da chave que abre o bau {@code id}. */
    public static net.minecraft.network.chat.Component nomeChave(String id) {
        BauTier padrao = BauTier.byId(id);
        if (padrao != null) return padrao.keyName().copy().withColor(padrao.color);
        return net.minecraft.network.chat.Component.translatable("item.shifuchests.chave_de", nomeBau(id));
    }

    public static Collection<BauConfig> baus() {
        return BAUS.values();
    }

    public static Collection<String> ids() {
        return BAUS.keySet();
    }

    /** Recarrega tudo. Devolve os avisos (recompensas invalidas etc.) para mostrar a quem recarregou. */
    public static synchronized List<String> load(HolderLookup.Provider registries) {
        List<String> warnings = new ArrayList<>();
        copyDefaults(warnings);

        JsonObject geralJson = readObject(dir().resolve("geral.json"), warnings);
        geral = geralJson != null ? ConfigGeral.parse(geralJson, warnings) : ConfigGeral.defaults();

        BAUS.clear();
        for (String id : idsNaPasta(warnings)) {
            loadBau(id, registries, warnings);
        }
        warnings.forEach(w -> ShifuChests.LOGGER.warn("[config] {}", w));
        for (BauConfig config : BAUS.values()) {
            ShifuChests.LOGGER.info("Bau {}: {} recompensas", config.id, config.recompensas.size());
        }
        return warnings;
    }

    /** normal, medio e premium sempre existem; os outros sao os .json que estiverem na pasta. */
    private static List<String> idsNaPasta(List<String> warnings) {
        List<String> ids = new ArrayList<>();
        for (BauTier tier : BauTier.values()) ids.add(tier.id);
        List<String> outros = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json") && !n.equals("geral.json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .filter(id -> !ids.contains(id))
                    .forEach(id -> {
                        if (ID_VALIDO.matcher(id).matches()) outros.add(id);
                        else warnings.add(id + ".json ignorado: use so letras minusculas, numeros e _ no nome do arquivo");
                    });
        } catch (IOException e) {
            warnings.add("Nao foi possivel ler " + dir() + ": " + e.getMessage());
        }
        outros.sort(String::compareTo);
        ids.addAll(outros);
        return ids;
    }

    private static void loadBau(String id, HolderLookup.Provider registries, List<String> warnings) {
        JsonObject json = readObject(dir().resolve(id + ".json"), warnings);
        BAUS.put(id, json != null ? BauConfig.parse(id, json, registries, geral, warnings) : BauConfig.empty(id));
    }

    /** Adiciona uma recompensa no fim da lista do bau e salva o arquivo. */
    public static synchronized List<String> addReward(BauTier tier, JsonObject reward, HolderLookup.Provider registries) throws IOException {
        return addReward(tier.id, reward, registries);
    }

    public static synchronized List<String> addReward(String id, JsonObject reward, HolderLookup.Provider registries) throws IOException {
        BauConfig config = BAUS.computeIfAbsent(id, BauConfig::empty);
        JsonObject json = config.json;
        JsonArray list = json.has("recompensas") && json.get("recompensas").isJsonArray()
                ? json.getAsJsonArray("recompensas") : new JsonArray();
        list.add(reward);
        json.add("recompensas", list);
        return saveAndReload(id, json, registries);
    }

    /** Remove a recompensa numero {@code index} (comecando em 1, como no /baus listar). */
    public static synchronized List<String> removeReward(BauTier tier, int index, HolderLookup.Provider registries) throws IOException {
        return removeReward(tier.id, index, registries);
    }

    public static synchronized List<String> removeReward(String id, int index, HolderLookup.Provider registries) throws IOException {
        BauConfig config = BAUS.computeIfAbsent(id, BauConfig::empty);
        Recompensa target = config.recompensas.get(index - 1);
        JsonArray list = config.json.getAsJsonArray("recompensas");
        // Procura pelo objeto original: a lista do arquivo pode ter entradas invalidas que foram puladas
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target.json) {
                list.remove(i);
                break;
            }
        }
        return saveAndReload(id, config.json, registries);
    }

    private static List<String> saveAndReload(String id, JsonObject json, HolderLookup.Provider registries) throws IOException {
        Path file = dir().resolve(id + ".json");
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
        List<String> warnings = new ArrayList<>();
        loadBau(id, registries, warnings);
        return warnings;
    }

    private static JsonObject readObject(Path file, List<String> warnings) {
        if (!Files.exists(file)) {
            warnings.add(file.getFileName() + " nao existe");
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                warnings.add(file.getFileName() + ": o arquivo precisa comecar com {");
                return null;
            }
            return element.getAsJsonObject();
        } catch (Exception e) {
            warnings.add(file.getFileName() + ": JSON com erro (" + e.getMessage() + "). Confira virgulas e aspas.");
            return null;
        }
    }

    private static void copyDefaults(List<String> warnings) {
        try {
            Files.createDirectories(dir());
            for (String name : DEFAULT_FILES) {
                Path target = dir().resolve(name);
                if (Files.exists(target) && !name.endsWith(".txt")) continue;
                try (InputStream in = ShifuChests.class.getResourceAsStream("/shifuchests_padrao/" + name)) {
                    if (in == null) continue;
                    // O guia (.txt) e sempre atualizado; os .json so sao criados se nao existirem
                    Files.write(target, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            warnings.add("Nao foi possivel criar " + dir() + ": " + e.getMessage());
        }
    }

    private ConfigManager() {
    }
}

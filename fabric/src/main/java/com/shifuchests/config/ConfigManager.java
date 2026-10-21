package com.shifuchests.config;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.Chaves;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

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
 * Le e salva config/shifuchests/. Cada arquivo .json da pasta (menos geral.json) e um tipo de caixa, com o nome do
 * arquivo como id. Na primeira vez cria geral.json, o guia e (se a pasta nao tiver nenhuma caixa) tres exemplos.
 * Tudo roda no servidor: o cliente recebe so o que precisa para desenhar a roleta.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] EXEMPLOS = {"normal.json", "medio.json", "premium.json"};

    private static ConfigGeral geral = ConfigGeral.defaults();
    /** Ids validos: letras minusculas, numeros e _ (viram nome de arquivo e argumento de comando). */
    public static final Pattern ID_VALIDO = Pattern.compile("[a-z0-9_]+");
    /** Em ordem alfabetica do arquivo. */
    private static final Map<String, BauConfig> BAUS = new LinkedHashMap<>();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve(ShifuChests.MOD_ID);
    }

    public static ConfigGeral geral() {
        return geral;
    }

    /** Config da caixa, ou null se nao existe arquivo com esse id. */
    @Nullable
    public static BauConfig bau(String id) {
        return BAUS.get(id);
    }

    /** Caixa que aceita as chaves antigas do Pebbles Crates com este nome de caixa. */
    @Nullable
    public static BauConfig porChavePebbles(String crateName) {
        for (BauConfig config : BAUS.values()) {
            if (crateName.equals(config.chavePebbles)) return config;
        }
        return null;
    }

    /** Nome da caixa colorido (ou o id, se o arquivo nao existe). */
    public static Component nomeBau(String id) {
        BauConfig config = BAUS.get(id);
        return config != null ? config.displayName().copy().withColor(config.cor) : Component.literal(id);
    }

    /** Nome da chave que abre a caixa {@code id}. */
    public static Component nomeChave(String id) {
        BauConfig config = BAUS.get(id);
        return config != null ? Chaves.nome(config) : Component.literal("chave de " + id);
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
            ShifuChests.LOGGER.info("Caixa {}: {} recompensas", config.id, config.recompensas.size());
        }
        return warnings;
    }

    private static List<String> arquivosDeCaixa() throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json") && !n.equals("geral.json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .forEach(ids::add);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static List<String> idsNaPasta(List<String> warnings) {
        List<String> ids = new ArrayList<>();
        try {
            for (String id : arquivosDeCaixa()) {
                if (ID_VALIDO.matcher(id).matches()) ids.add(id);
                else warnings.add(id + ".json ignorado: use so letras minusculas, numeros e _ no nome do arquivo");
            }
        } catch (IOException e) {
            warnings.add("Nao foi possivel ler " + dir() + ": " + e.getMessage());
        }
        return ids;
    }

    private static void loadBau(String id, HolderLookup.Provider registries, List<String> warnings) {
        JsonObject json = readObject(dir().resolve(id + ".json"), warnings);
        BAUS.put(id, json != null ? BauConfig.parse(id, json, registries, geral, warnings) : BauConfig.empty(id));
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
            // O guia (.txt) e sempre atualizado; geral.json so e criado se nao existir
            copiar("COMO_CONFIGURAR.txt", true);
            copiar("geral.json", false);
            // Exemplos so numa pasta sem nenhuma caixa (no servidor ja vao as caixas de verdade)
            if (arquivosDeCaixa().isEmpty()) {
                for (String name : EXEMPLOS) copiar(name, false);
            }
        } catch (IOException e) {
            warnings.add("Nao foi possivel criar " + dir() + ": " + e.getMessage());
        }
    }

    private static void copiar(String name, boolean sobrescrever) throws IOException {
        Path target = dir().resolve(name);
        if (Files.exists(target) && !sobrescrever) return;
        try (InputStream in = ShifuChests.class.getResourceAsStream("/shifuchests_padrao/" + name)) {
            if (in != null) Files.write(target, in.readAllBytes());
        }
    }

    private ConfigManager() {
    }
}

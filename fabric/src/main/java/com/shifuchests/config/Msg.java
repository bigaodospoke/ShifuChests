package com.shifuchests.config;

import com.shifuchests.ShifuChests;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Mensagens que o servidor manda para o jogador: com o mod, o jogo usa a traducao dele; sem o mod (launcher
 * desatualizado), aparece o texto em portugues em vez do codigo "message.shifuchests...".
 */
public final class Msg {
    private static final Map<String, String> PT = new HashMap<>();

    static {
        try (InputStream in = ShifuChests.class.getResourceAsStream("/assets/shifuchests/lang/pt_br.json")) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e -> PT.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception e) {
            ShifuChests.LOGGER.warn("Nao foi possivel ler as mensagens em portugues", e);
        }
    }

    public static MutableComponent t(String key, Object... args) {
        return Component.translatableWithFallback(key, PT.getOrDefault(key, key), args);
    }

    private Msg() {
    }
}

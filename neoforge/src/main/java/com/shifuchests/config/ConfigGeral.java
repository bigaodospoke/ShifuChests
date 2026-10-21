package com.shifuchests.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** config/shifuchests/geral.json: opcoes que valem para todos os baus. */
public final class ConfigGeral {
    /** Quanto tempo a roleta gira antes de parar. */
    public final float duracaoRoletaSegundos;
    /** O jogador pode apertar ESPACO para pular a animacao. */
    public final boolean permitirPular;
    public final boolean anunciarNoChat;
    /** Premios desta raridade para cima sao anunciados para o servidor todo. */
    public final Raridade raridadeMinimaAnuncio;
    /** Pokemon shiny sempre e anunciado, seja qual for a raridade. */
    public final boolean anunciarShiny;
    /** Se false, jogador no criativo abre o bau sem gastar a chave. */
    public final boolean consumirChaveNoCriativo;
    /**
     * Itens destes namespaces sao bloqueados em baus marcados como "pago": true.
     * Ex.: a licenca do Mega Showdown proibe colocar o conteudo dele atras de chaves vendidas.
     */
    public final List<String> namespacesProibidosEmBausPagos;

    private ConfigGeral(float duracaoRoletaSegundos, boolean permitirPular, boolean anunciarNoChat,
                        Raridade raridadeMinimaAnuncio, boolean anunciarShiny, boolean consumirChaveNoCriativo,
                        List<String> namespacesProibidosEmBausPagos) {
        this.duracaoRoletaSegundos = duracaoRoletaSegundos;
        this.permitirPular = permitirPular;
        this.anunciarNoChat = anunciarNoChat;
        this.raridadeMinimaAnuncio = raridadeMinimaAnuncio;
        this.anunciarShiny = anunciarShiny;
        this.consumirChaveNoCriativo = consumirChaveNoCriativo;
        this.namespacesProibidosEmBausPagos = List.copyOf(namespacesProibidosEmBausPagos);
    }

    public static ConfigGeral defaults() {
        return new ConfigGeral(7.0F, true, true, Raridade.EPICO, true, false, List.of("mega_showdown"));
    }

    public int duracaoRoletaTicks() {
        return Math.round(duracaoRoletaSegundos * 20);
    }

    static ConfigGeral parse(JsonObject json, List<String> warnings) {
        ConfigGeral d = defaults();
        float duracao = d.duracaoRoletaSegundos;
        if (json.has("duracao_roleta_segundos")) {
            duracao = json.get("duracao_roleta_segundos").getAsFloat();
            if (duracao < 2 || duracao > 30) {
                warnings.add("geral.json: duracao_roleta_segundos deve ficar entre 2 e 30 (usando " + Math.max(2, Math.min(30, duracao)) + ")");
                duracao = Math.max(2, Math.min(30, duracao));
            }
        }
        Raridade minima = d.raridadeMinimaAnuncio;
        if (json.has("raridade_minima_anuncio")) {
            Raridade parsed = Raridade.byId(json.get("raridade_minima_anuncio").getAsString());
            if (parsed == null) warnings.add("geral.json: raridade_minima_anuncio desconhecida: " + json.get("raridade_minima_anuncio"));
            else minima = parsed;
        }
        List<String> namespaces = new ArrayList<>(d.namespacesProibidosEmBausPagos);
        if (json.has("namespaces_proibidos_em_baus_pagos")) {
            namespaces.clear();
            JsonElement element = json.get("namespaces_proibidos_em_baus_pagos");
            if (element.isJsonArray()) {
                for (JsonElement e : element.getAsJsonArray()) namespaces.add(e.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        return new ConfigGeral(duracao,
                bool(json, "permitir_pular", d.permitirPular),
                bool(json, "anunciar_no_chat", d.anunciarNoChat),
                minima,
                bool(json, "anunciar_shiny", d.anunciarShiny),
                bool(json, "consumir_chave_no_criativo", d.consumirChaveNoCriativo),
                namespaces);
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("duracao_roleta_segundos", duracaoRoletaSegundos);
        json.addProperty("permitir_pular", permitirPular);
        json.addProperty("anunciar_no_chat", anunciarNoChat);
        json.addProperty("raridade_minima_anuncio", raridadeMinimaAnuncio.id);
        json.addProperty("anunciar_shiny", anunciarShiny);
        json.addProperty("consumir_chave_no_criativo", consumirChaveNoCriativo);
        JsonArray namespaces = new JsonArray();
        namespacesProibidosEmBausPagos.forEach(namespaces::add);
        json.add("namespaces_proibidos_em_baus_pagos", namespaces);
        return json;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
}

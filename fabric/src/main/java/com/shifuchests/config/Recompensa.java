package com.shifuchests.config;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Uma linha de "recompensas" de um bau. Pode ser um Pokemon, um item ou comandos.
 * Instancias sao imutaveis; a raridade final (manual ou automatica) e calculada pelo {@link BauConfig}.
 */
public final class Recompensa {
    public enum Tipo { POKEMON, ITEM, COMANDO }

    public final Tipo tipo;
    /** Especie e propriedades no formato do Cobblemon, ex.: "pikachu", "vulpix alolan", "eevee shiny=yes". */
    @Nullable public final String pokemon;
    @Nullable public final NivelRange nivel;
    /** Chance de vir shiny (0..1). null = usa a chance do bau. */
    @Nullable public final Double chanceShiny;
    /** Item entregue (ITEM) ou icone mostrado na roleta (COMANDO). */
    public final ItemStack item;
    /** Itens entregues junto ("com_itens"), ex.: a Mega Pedra do Pokemon. */
    public final List<ItemStack> extras;
    public final List<String> comandos;
    /** Nome mostrado na roleta; null = nome do Pokemon/item. */
    @Nullable public final Component nome;
    public final double peso;
    @Nullable public final Raridade raridadeManual;
    /** JSON original, reescrito quando o arquivo e salvo pelos comandos. */
    public final JsonObject json;

    Raridade raridade = Raridade.COMUM;
    double chance;

    Recompensa(Tipo tipo, @Nullable String pokemon, @Nullable NivelRange nivel, @Nullable Double chanceShiny,
               ItemStack item, List<ItemStack> extras, List<String> comandos, @Nullable Component nome, double peso,
               @Nullable Raridade raridadeManual, JsonObject json) {
        this.tipo = tipo;
        this.pokemon = pokemon;
        this.nivel = nivel;
        this.chanceShiny = chanceShiny;
        this.item = item;
        this.extras = List.copyOf(extras);
        this.comandos = List.copyOf(comandos);
        this.nome = nome;
        this.peso = peso;
        this.raridadeManual = raridadeManual;
        this.json = json;
    }

    public Raridade raridade() {
        return raridade;
    }

    /** Chance real de sair (0..1), considerando o peso de todas as recompensas do bau. */
    public double chance() {
        return chance;
    }

    /** Descricao curta para listas e logs. */
    public String describe() {
        String base = switch (tipo) {
            case POKEMON -> "pokemon " + pokemon + (nivel != null ? " nv " + nivel : "");
            case ITEM -> "item " + item.getCount() + "x " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem());
            case COMANDO -> "comando " + String.join(" ; ", comandos);
        };
        for (ItemStack extra : extras) {
            base += " + " + extra.getCount() + "x " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(extra.getItem());
        }
        return base;
    }

    /** Faixa de nivel "10" ou "5-15". */
    public record NivelRange(int min, int max) {
        public int roll(net.minecraft.util.RandomSource random) {
            return min >= max ? min : min + random.nextInt(max - min + 1);
        }

        @Override
        public String toString() {
            return min == max ? Integer.toString(min) : min + "-" + max;
        }

        @Nullable
        public static NivelRange parse(@Nullable com.google.gson.JsonElement element) {
            if (element == null || element.isJsonNull()) return null;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                int value = clampLevel(element.getAsInt());
                return new NivelRange(value, value);
            }
            String text = element.getAsString().trim();
            String[] parts = text.split("\\s*-\\s*");
            int a = clampLevel(Integer.parseInt(parts[0]));
            int b = parts.length > 1 ? clampLevel(Integer.parseInt(parts[1])) : a;
            return new NivelRange(Math.min(a, b), Math.max(a, b));
        }

        private static int clampLevel(int level) {
            return Math.max(1, Math.min(1000, level));
        }
    }
}

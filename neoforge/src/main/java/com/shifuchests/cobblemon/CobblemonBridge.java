package com.shifuchests.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Todas as chamadas ao Cobblemon do lado do servidor ficam aqui. */
public final class CobblemonBridge {
    /** Devolve uma mensagem de erro se o texto do Pokemon for invalido, ou null se estiver ok. */
    @Nullable
    public static String validateSpec(String spec) {
        if (spec.isBlank()) return "\"pokemon\" esta vazio";
        try {
            PokemonProperties props = PokemonProperties.Companion.parse(spec);
            String species = props.getSpecies();
            if (species == null) return "nao achei a especie em \"" + spec + "\"";
            if (isRandom(species)) return null;
            if (resolveSpecies(species) == null) return "Pokemon desconhecido: \"" + species + "\" (use o nome em ingles, ex.: pikachu, mr_mime)";
            return null;
        } catch (Exception e) {
            return "texto de Pokemon invalido \"" + spec + "\": " + e.getMessage();
        }
    }

    public static boolean isRandom(String species) {
        return species.equalsIgnoreCase("random") || species.equalsIgnoreCase("cobblemon:random");
    }

    /** "random" muda a cada sorteio, entao nao da para guardar a aparencia em cache. */
    public static boolean specIsRandom(String spec) {
        String species = PokemonProperties.Companion.parse(spec).getSpecies();
        return species != null && isRandom(species);
    }

    @Nullable
    private static Species resolveSpecies(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) return PokemonSpecies.getByIdentifier(ResourceLocation.parse(lower));
        return PokemonSpecies.getByName(lower);
    }

    /**
     * Cria o Pokemon da recompensa.
     *
     * @param level nivel a usar se o texto nao tiver "level=..." (null = padrao do Cobblemon)
     * @param shiny resultado do sorteio de shiny; ignorado se o texto ja tiver "shiny=..."
     */
    public static Pokemon create(String spec, @Nullable Integer level, boolean shiny, @Nullable ServerPlayer player) {
        PokemonProperties props = PokemonProperties.Companion.parse(spec);
        if (props.getLevel() == null && level != null) props.setLevel(level);
        if (props.getShiny() == null) props.setShiny(shiny);
        return player != null ? props.create(player) : props.create();
    }

    /** Coloca na equipe; se estiver cheia, o proprio Cobblemon manda para o PC e avisa o jogador. */
    public static boolean give(ServerPlayer player, Pokemon pokemon) {
        return Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
    }

    public static ResourceLocation speciesId(Pokemon pokemon) {
        return pokemon.getSpecies().getResourceIdentifier();
    }

    public static List<String> aspects(Pokemon pokemon) {
        return List.copyOf(pokemon.getAspects());
    }

    public static Component speciesName(Pokemon pokemon) {
        return pokemon.getSpecies().getTranslatedName();
    }

    private CobblemonBridge() {
    }
}

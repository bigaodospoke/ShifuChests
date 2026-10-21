package com.shifuchests.network;

import com.shifuchests.config.Raridade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * O que o cliente precisa para desenhar um cartao da roleta: o modelo do Pokemon (especie + aspectos,
 * ex.: "shiny", "alolan") ou o icone do item, o nome e a raridade.
 */
public record RewardDisplay(
        @Nullable ResourceLocation species,
        List<String> aspects,
        ItemStack icon,
        Component name,
        Raridade raridade,
        boolean shiny,
        int level,
        float chance
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, RewardDisplay> STREAM_CODEC =
            StreamCodec.of(RewardDisplay::write, RewardDisplay::read);

    public boolean isPokemon() {
        return species != null;
    }

    public RewardDisplay withChance(float newChance) {
        return new RewardDisplay(species, aspects, icon, name, raridade, shiny, level, newChance);
    }

    private static void write(RegistryFriendlyByteBuf buf, RewardDisplay d) {
        buf.writeBoolean(d.species != null);
        if (d.species != null) buf.writeResourceLocation(d.species);
        buf.writeVarInt(d.aspects.size());
        for (String aspect : d.aspects) buf.writeUtf(aspect);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, d.icon);
        ComponentSerialization.STREAM_CODEC.encode(buf, d.name);
        buf.writeVarInt(d.raridade.ordinal());
        buf.writeBoolean(d.shiny);
        buf.writeVarInt(d.level);
        buf.writeFloat(d.chance);
    }

    private static RewardDisplay read(RegistryFriendlyByteBuf buf) {
        ResourceLocation species = buf.readBoolean() ? buf.readResourceLocation() : null;
        int aspectCount = buf.readVarInt();
        List<String> aspects = new ArrayList<>(aspectCount);
        for (int i = 0; i < aspectCount; i++) aspects.add(buf.readUtf());
        ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        Raridade raridade = Raridade.byOrdinal(buf.readVarInt());
        boolean shiny = buf.readBoolean();
        int level = buf.readVarInt();
        float chance = buf.readFloat();
        return new RewardDisplay(species, aspects, icon, name, raridade, shiny, level, chance);
    }
}

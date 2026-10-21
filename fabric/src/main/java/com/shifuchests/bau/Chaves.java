package com.shifuchests.bau;

import com.shifuchests.config.BauConfig;
import com.shifuchests.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * As chaves sao itens normais do servidor (no Eclipse, as chaves 3D do Eclipse Cobblemon Keys) com o id da caixa
 * em custom_data {"bau": id}. Chaves antigas do Pebbles Crates ({"CrateName": nome}) tambem abrem a caixa que tiver
 * "chave_pebbles" igual ao nome, para quem comprou antes da troca nao perder nada.
 */
public final class Chaves {
    public static final String TAG_BAU = "bau";
    public static final String TAG_BAU_NOME = "bau_nome";
    public static final String TAG_PEBBLES = "CrateName";

    /** Id da caixa que este item abre, ou "" se nao for chave. */
    public static String tipo(ItemStack stack) {
        if (stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag tag = data.copyTag();
        if (tag.contains(TAG_BAU)) return tag.getString(TAG_BAU);
        if (tag.contains(TAG_PEBBLES)) {
            BauConfig config = ConfigManager.porChavePebbles(tag.getString(TAG_PEBBLES));
            if (config != null) return config.id;
        }
        return "";
    }

    /** Monta {@code quantidade} chaves da caixa (pode passar do tamanho da pilha: quem entrega divide). */
    public static ItemStack criar(BauConfig config, int quantidade) {
        Item item = BuiltInRegistries.ITEM.getOptional(config.chaveItem).orElse(Items.TRIPWIRE_HOOK);
        ItemStack stack = new ItemStack(item, quantidade);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_BAU, config.id);
        tag.putString(TAG_BAU_NOME, config.displayName().getString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, nome(config));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Abre a ").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false))
                        .append(config.displayName().copy().withColor(config.cor)),
                Component.literal("Clique na caixa com a chave na mão").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(false)))));
        if (config.chaveBrilho()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /** "Chave da Caixa de Miticos", na cor da caixa. */
    public static Component nome(BauConfig config) {
        return Component.literal("Chave da ").append(config.displayName().copy()).withColor(config.cor);
    }

    private Chaves() {
    }
}

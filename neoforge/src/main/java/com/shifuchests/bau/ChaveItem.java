package com.shifuchests.bau;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Chave que abre o bau do mesmo tipo. As chaves dos tres baus padrao nao tem dados; as dos baus criados
 * na config usam o modelo do tier e guardam o id em custom_data {"bau": id, "bau_nome": nome}.
 */
public class ChaveItem extends Item {
    public static final String TAG_BAU = "bau";
    public static final String TAG_BAU_NOME = "bau_nome";

    public final BauTier tier;

    public ChaveItem(BauTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** Id do bau que esta chave abre. */
    public static String tipo(ItemStack stack) {
        CompoundTag tag = dados(stack);
        if (tag.contains(TAG_BAU)) return tag.getString(TAG_BAU);
        return stack.getItem() instanceof ChaveItem chave ? chave.tier.id : "";
    }

    /** Monta a chave de um bau: sem dados para os padrao, com id e nome para os outros. */
    public static ItemStack criar(Item item, String id, boolean padrao, Component nomeBau, int cor, int quantidade) {
        ItemStack stack = new ItemStack(item, quantidade);
        if (!padrao) {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_BAU, id);
            tag.putString(TAG_BAU_NOME, nomeBau.getString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(DataComponents.ITEM_NAME, Component.translatable("item.shifuchests.chave_de", nomeBau.copy()).withColor(cor));
        }
        return stack;
    }

    private static CompoundTag dados(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == BauTier.PREMIUM || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = dados(stack);
        Component bau = tag.contains(TAG_BAU_NOME) ? Component.literal(tag.getString(TAG_BAU_NOME)) : tier.chestName().copy().withColor(tier.color);
        tooltip.add(Component.translatable("tooltip.shifuchests.chave", bau).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shifuchests.chave.uso").withStyle(ChatFormatting.DARK_GRAY));
    }
}

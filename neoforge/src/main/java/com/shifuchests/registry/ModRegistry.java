package com.shifuchests.registry;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauBlock;
import com.shifuchests.bau.BauBlockEntity;
import com.shifuchests.bau.BauTier;
import com.shifuchests.bau.ChaveItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShifuChests.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShifuChests.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShifuChests.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShifuChests.MOD_ID);

    public static final Map<BauTier, DeferredBlock<BauBlock>> CHESTS = new EnumMap<>(BauTier.class);
    public static final Map<BauTier, DeferredItem<BlockItem>> CHEST_ITEMS = new EnumMap<>(BauTier.class);
    public static final Map<BauTier, DeferredItem<ChaveItem>> KEYS = new EnumMap<>(BauTier.class);

    static {
        for (BauTier tier : BauTier.values()) {
            DeferredBlock<BauBlock> chest = BLOCKS.registerBlock(tier.chestId(), props -> new BauBlock(tier, props),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            // Inquebravel no survival (como bedrock): so admin no criativo remove
                            .strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .lightLevel(state -> 6)
                            .pushReaction(PushReaction.BLOCK));
            CHESTS.put(tier, chest);
            CHEST_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(chest, new Item.Properties().rarity(itemRarity(tier))));
            KEYS.put(tier, ITEMS.registerItem(tier.keyId(), props -> new ChaveItem(tier, props),
                    new Item.Properties().stacksTo(64).rarity(itemRarity(tier))));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BauBlockEntity>> BAU_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("bau", () -> BlockEntityType.Builder.of(BauBlockEntity::new,
                    CHESTS.values().stream().map(DeferredBlock::get).toArray(BauBlock[]::new)).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("baus", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shifuchests"))
                    .icon(() -> new ItemStack(CHEST_ITEMS.get(BauTier.PREMIUM).get()))
                    .displayItems((params, output) -> {
                        for (BauTier tier : BauTier.values()) output.accept(CHEST_ITEMS.get(tier).get());
                        for (BauTier tier : BauTier.values()) output.accept(KEYS.get(tier).get());
                    })
                    .build());

    private static Rarity itemRarity(BauTier tier) {
        return switch (tier) {
            case NORMAL -> Rarity.COMMON;
            case MEDIO -> Rarity.RARE;
            case PREMIUM -> Rarity.EPIC;
        };
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }

    private ModRegistry() {
    }
}

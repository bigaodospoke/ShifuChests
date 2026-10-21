package com.shifuchests.bau;

import com.shifuchests.config.ConfigManager;
import com.shifuchests.registry.ModRegistry;
import com.shifuchests.sorteio.RollManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BauBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10.5, 14);

    public final BauTier tier;
    private final MapCodec<BauBlock> codec;

    public BauBlock(BauTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = MapCodec.unit(this);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // A fechadura do modelo fica para o norte: vira o bau de frente para quem colocou
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // O modelo e desenhado pelo BauRenderer, que faz o bau tremer e pular durante o sorteio
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ChaveItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // O cliente nao conhece os tipos de bau: quem decide se a chave serve e o servidor
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String tipoBau = tipo(level, pos);
            String tipoChave = ChaveItem.tipo(stack);
            if (!tipoChave.equals(tipoBau)) {
                player.displayClientMessage(Component.translatable("message.shifuchests.chave_errada",
                        ConfigManager.nomeBau(tipoChave), ConfigManager.nomeChave(tipoBau)).withStyle(ChatFormatting.RED), true);
            } else {
                RollManager.openWithKey(serverPlayer, tipoBau, stack, pos);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Chave na mao secundaria tambem vale
            String tipoBau = tipo(level, pos);
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() instanceof ChaveItem && ChaveItem.tipo(offhand).equals(tipoBau)) {
                RollManager.openWithKey(serverPlayer, tipoBau, offhand, pos);
            } else {
                RollManager.sendPreview(serverPlayer, tipoBau);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Tipo do bau nesta posicao (o tier do bloco se o block entity nao disser outro). */
    public String tipo(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BauBlockEntity be ? be.tipo() : tier.id;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData dados = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (dados == null || !dados.contains(BauBlockEntity.TAG_TIPO)) {
            tooltip.add(Component.translatable("tooltip.shifuchests.bau", tier.keyName().copy().withColor(tier.color))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.shifuchests.bau.preview").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BauBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.BAU_BLOCK_ENTITY.get(), BauBlockEntity::clientTick)
                : null;
    }
}

package com.shifuchests.mixin.client;

import com.shifuchests.client.EfeitosCliente;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faz a caixa (o bau dourado do Cobblemon, que tem renderer proprio) tremer e pular durante a roleta:
 * mexe na pose antes do renderer do bloco desenhar e devolve depois.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"))
    private <E extends BlockEntity> void shifuchests$antes(E blockEntity, float partialTick, PoseStack poseStack,
                                                            MultiBufferSource bufferSource, CallbackInfo ci) {
        EfeitosCliente.antesDeDesenhar(blockEntity, partialTick, poseStack);
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"))
    private <E extends BlockEntity> void shifuchests$depois(E blockEntity, float partialTick, PoseStack poseStack,
                                                             MultiBufferSource bufferSource, CallbackInfo ci) {
        EfeitosCliente.depoisDeDesenhar(poseStack);
    }
}

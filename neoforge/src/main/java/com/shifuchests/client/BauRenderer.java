package com.shifuchests.client;

import com.shifuchests.bau.BauBlockEntity;
import com.shifuchests.config.Raridade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Desenha o modelo do bau (no lugar da malha estatica) para poder animar:
 * treme cada vez mais enquanto a roleta gira, da um pulo na revelacao e solta um feixe de luz
 * na cor da raridade do premio.
 */
public class BauRenderer implements BlockEntityRenderer<BauBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public BauRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(BauBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
        float time = gameTime + partialTick;

        pose.pushPose();
        float shake = be.shakeIntensity(partialTick);
        float age = be.revealAge(partialTick);
        float lift = 0;
        float scale = 1;
        float spinY = 0;
        if (shake > 0) {
            // Treme como se algo quisesse sair, e flutua um pouco
            lift = shake * 0.12F + Mth.sin(time * 0.25F) * 0.02F * shake;
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 2.7F) * 6 * shake));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(time * 3.1F) * 4 * shake));
            pose.translate(-0.5, 0, -0.5);
        } else if (age >= 0) {
            // Pulo com um giro completo e "estica e encolhe" ao cair
            float jump = Mth.clamp(age / 14F, 0, 1);
            lift = Mth.sin(jump * Mth.PI) * 0.6F;
            spinY = easeOut(jump) * 360;
            float squash = age < 20 ? Mth.sin(Mth.clamp((age - 14) / 6F, 0, 1) * Mth.PI) * 0.15F : 0;
            scale = 1 + squash;
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(spinY));
            pose.scale(scale, 1 - squash * 0.8F, scale);
            pose.translate(-0.5, 0, -0.5);
        }
        pose.translate(0, lift, 0);

        BakedModel model = blockRenderer.getBlockModel(state);
        blockRenderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model,
                1, 1, 1, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();

        if (age >= 0 && be.revealRarity().atLeast(Raridade.RARO)) {
            // Feixe de luz que sobe e some aos poucos
            float fade = 1 - Mth.clamp((age - BauBlockEntity.REVEAL_TICKS + 25) / 25F, 0, 1);
            int height = (int) (Mth.clamp(age / 6F, 0, 1) * (be.revealRarity().atLeast(Raridade.LENDARIO) ? 256 : 48));
            int alpha = (int) (fade * 255);
            if (alpha > 8 && height > 0) {
                int color = (alpha << 24) | (be.revealRarity().color & 0xFFFFFF);
                BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1, gameTime,
                        0, height, color, 0.12F * fade + 0.03F, 0.2F * fade + 0.05F);
            }
        }
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    @Override
    public boolean shouldRenderOffScreen(BauBlockEntity be) {
        return be.revealAge(0) >= 0;
    }

    @Override
    public AABB getRenderBoundingBox(BauBlockEntity be) {
        // O feixe precisa continuar visivel quando o bau sai da tela
        return be.revealAge(0) >= 0 ? AABB.INFINITE : new AABB(be.getBlockPos()).inflate(0.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}

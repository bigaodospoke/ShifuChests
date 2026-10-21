package com.shifuchests.bau;

import com.shifuchests.config.Msg;
import com.shifuchests.ShifuChests;
import com.shifuchests.config.ConfigManager;
import com.shifuchests.sorteio.RollManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Clique e quebra dos blocos marcados como caixa. O bloco continua sendo o do Cobblemon (bau dourado), mas o clique
 * vira a roleta: com a chave certa na mao gira; sem chave mostra os premios. So admin no criativo quebra a caixa.
 */
public final class Interacao {
    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            // O cliente nao sabe quais blocos sao caixas: quem decide e o servidor (o bau nao abre o inventario dele)
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BausNoMundo.Entrada caixa = BausNoMundo.de(serverPlayer.server).em(level, pos);
            if (caixa == null) return InteractionResult.PASS;
            // A mao principal ja resolveu tudo (chave na mao secundaria tambem vale)
            if (hand == InteractionHand.OFF_HAND) return InteractionResult.SUCCESS;

            ItemStack principal = serverPlayer.getMainHandItem();
            ItemStack secundaria = serverPlayer.getOffhandItem();
            String tipoPrincipal = Chaves.tipo(principal);
            if (tipoPrincipal.equals(caixa.tipo())) {
                RollManager.openWithKey(serverPlayer, caixa.tipo(), principal, pos);
            } else if (Chaves.tipo(secundaria).equals(caixa.tipo())) {
                RollManager.openWithKey(serverPlayer, caixa.tipo(), secundaria, pos);
            } else if (!tipoPrincipal.isEmpty()) {
                player.displayClientMessage(Msg.t("message.shifuchests.chave_errada",
                        ConfigManager.nomeBau(tipoPrincipal), ConfigManager.nomeChave(caixa.tipo())).withStyle(ChatFormatting.RED), true);
            } else {
                RollManager.sendPreview(serverPlayer, caixa.tipo());
            }
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return true;
            if (BausNoMundo.de(serverPlayer.server).em(level, pos) == null) return true;
            if (player.isCreative() && player.hasPermissions(2)) return true;
            player.displayClientMessage(Msg.t("message.shifuchests.protegido").withStyle(ChatFormatting.RED), true);
            return false;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return;
            if (BausNoMundo.de(serverPlayer.server).remover(level, pos)) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) Hologramas.remover(serverLevel, pos);
                ShifuChests.LOGGER.info("{} quebrou a caixa em {} {}", player.getGameProfile().getName(), level.dimension().location(), pos.toShortString());
            }
        });
    }

    private Interacao() {
    }
}

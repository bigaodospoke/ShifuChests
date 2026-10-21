package com.shifuchests.network;

import com.shifuchests.ShifuChests;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> clientes perto da caixa: anima a caixa no mundo (o bloco nao e do mod, entao os efeitos vem por pacote).
 * GIRANDO: treme e solta a espiral por {@code duracao} ticks. REVELACAO: pula, explode e solta o feixe na cor da raridade.
 */
public record EfeitoBauPayload(BlockPos pos, int tipo, int duracao, int raridade, boolean shiny, int cor) implements CustomPacketPayload {
    public static final int GIRANDO = 1;
    public static final int REVELACAO = 2;

    public static final Type<EfeitoBauPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShifuChests.MOD_ID, "efeito_bau"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EfeitoBauPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.tipo);
                buf.writeVarInt(p.duracao);
                buf.writeVarInt(p.raridade);
                buf.writeBoolean(p.shiny);
                buf.writeInt(p.cor);
            },
            buf -> new EfeitoBauPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

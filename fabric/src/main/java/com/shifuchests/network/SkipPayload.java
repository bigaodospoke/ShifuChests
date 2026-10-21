package com.shifuchests.network;

import com.shifuchests.ShifuChests;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: o jogador pulou a animacao (ou ela terminou); entrega o premio agora. */
public record SkipPayload() implements CustomPacketPayload {
    public static final SkipPayload INSTANCE = new SkipPayload();
    public static final Type<SkipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShifuChests.MOD_ID, "pular"));
    public static final StreamCodec<ByteBuf, SkipPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

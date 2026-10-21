package com.shifuchests.network;

import com.shifuchests.ShifuChests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Servidor -> cliente: mostra o que pode sair da caixa, a chance de cada premio e o nome da chave. */
public record PreviewPayload(int color, Component title, Component keyName, List<RewardDisplay> rewards) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShifuChests.MOD_ID, "previa"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.color);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                ComponentSerialization.STREAM_CODEC.encode(buf, p.keyName);
                RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.rewards);
            },
            buf -> new PreviewPayload(
                    buf.readInt(),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

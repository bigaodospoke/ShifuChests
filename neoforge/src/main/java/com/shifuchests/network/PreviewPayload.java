package com.shifuchests.network;

import com.shifuchests.ShifuChests;
import com.shifuchests.bau.BauTier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Servidor -> cliente: mostra o que pode sair do bau e a chance de cada premio. */
public record PreviewPayload(BauTier tier, Component title, List<RewardDisplay> rewards) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShifuChests.MOD_ID, "previa"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.tier.ordinal());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.rewards);
            },
            buf -> new PreviewPayload(
                    BauTier.values()[buf.readVarInt()],
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

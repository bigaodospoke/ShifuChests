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

/** Servidor -> cliente: abre a roleta. A lista ja vem com o premio na posicao {@code winnerIndex}. */
public record OpenRoulettePayload(
        BauTier tier,
        Component title,
        List<RewardDisplay> strip,
        int winnerIndex,
        int durationTicks,
        boolean canSkip,
        boolean test
) implements CustomPacketPayload {
    public static final Type<OpenRoulettePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShifuChests.MOD_ID, "abrir_roleta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRoulettePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.tier.ordinal());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
                RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.strip);
                buf.writeVarInt(p.winnerIndex);
                buf.writeVarInt(p.durationTicks);
                buf.writeBoolean(p.canSkip);
                buf.writeBoolean(p.test);
            },
            buf -> new OpenRoulettePayload(
                    BauTier.values()[buf.readVarInt()],
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    RewardDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()));

    public RewardDisplay winner() {
        return strip.get(winnerIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

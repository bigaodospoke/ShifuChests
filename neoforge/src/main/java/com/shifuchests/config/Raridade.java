package com.shifuchests.config;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Raridade de uma recompensa. Define a cor do cartao na roleta, o tamanho da comemoracao
 * e se o premio e anunciado no chat. Se a recompensa nao disser a raridade, ela e calculada
 * pela chance (veja {@link #fromChance(double)}).
 */
public enum Raridade {
    COMUM("comum", 0xB8C2CC),
    INCOMUM("incomum", 0x5BD75B),
    RARO("raro", 0x4A9DFF),
    EPICO("epico", 0xB55CFF),
    LENDARIO("lendario", 0xFFB300),
    MITICO("mitico", 0xFF4D7A);

    public final String id;
    public final int color;

    Raridade(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("shifuchests.raridade." + id);
    }

    public boolean atLeast(Raridade other) {
        return ordinal() >= other.ordinal();
    }

    /** Raridade automatica: quanto menor a chance (0..1), mais rara. */
    public static Raridade fromChance(double chance) {
        if (chance >= 0.20) return COMUM;
        if (chance >= 0.08) return INCOMUM;
        if (chance >= 0.03) return RARO;
        if (chance >= 0.01) return EPICO;
        if (chance >= 0.002) return LENDARIO;
        return MITICO;
    }

    @Nullable
    public static Raridade byId(@Nullable String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase()
                .replace('é', 'e').replace('í', 'i').replace('á', 'a');
        for (Raridade r : values()) {
            if (r.id.equals(normalized) || r.name().equalsIgnoreCase(normalized)) return r;
        }
        return switch (normalized) {
            case "common" -> COMUM;
            case "uncommon" -> INCOMUM;
            case "rare" -> RARO;
            case "epic" -> EPICO;
            case "legendary" -> LENDARIO;
            case "mythic", "mythical", "mitica" -> MITICO;
            default -> null;
        };
    }

    public static Raridade byOrdinal(int ordinal) {
        Raridade[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }
}

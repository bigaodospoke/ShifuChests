package com.shifuchests.bau;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os tres baus do pack. O {@link #id} e o nome usado nos arquivos de configuracao e nos comandos;
 * o {@link #registryName} segue o nome dos modelos/texturas do pack (normal, medium, premium).
 */
public enum BauTier {
    NORMAL("normal", "normal", 0x5CE65C),
    MEDIO("medio", "medium", 0x4FC3FF),
    PREMIUM("premium", "premium", 0xFFB300);

    public final String id;
    public final String registryName;
    /** Cor RGB usada em textos, particulas e na roleta. */
    public final int color;

    BauTier(String id, String registryName, int color) {
        this.id = id;
        this.registryName = registryName;
        this.color = color;
    }

    public String chestId() {
        return registryName + "_chest";
    }

    public String keyId() {
        return registryName + "_key";
    }

    /** Nome traduzido do bau, ex.: "Bau Premium". */
    public Component chestName() {
        return Component.translatable("block.shifuchests." + chestId());
    }

    public Component keyName() {
        return Component.translatable("item.shifuchests." + keyId());
    }

    @Nullable
    public static BauTier byId(String id) {
        for (BauTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id) || tier.registryName.equalsIgnoreCase(id)) return tier;
        }
        return null;
    }
}

package com.shifuchests.bau;

import com.shifuchests.config.Raridade;
import com.shifuchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Guarda so o tipo do bau ("Tipo" no NBT; sem ele vale o tier do bloco). Fora isso existe para o servidor
 * mandar eventos de animacao (via blockEvent) para quem esta perto, e para o cliente desenhar o bau
 * tremendo, as particulas e o feixe de luz.
 */
public class BauBlockEntity extends BlockEntity {
    public static final int EVENT_SPIN = 1;
    public static final int EVENT_REVEAL = 2;
    public static final int REVEAL_TICKS = 80;

    public static final String TAG_TIPO = "Tipo";

    /** Id do bau (arquivo config/shifuchests/&lt;id&gt;.json); null = o tier do bloco (normal, medio, premium). */
    @Nullable private String tipo;

    // Estado de animacao (so no cliente)
    private long spinStart = Long.MIN_VALUE;
    private int spinDuration;
    private long revealStart = Long.MIN_VALUE;
    private Raridade revealRarity = Raridade.COMUM;
    private boolean revealShiny;

    public BauBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.BAU_BLOCK_ENTITY.get(), pos, state);
    }

    public BauTier tier() {
        return getBlockState().getBlock() instanceof BauBlock block ? block.tier : BauTier.NORMAL;
    }

    /** Id do bau que este bloco abre. */
    public String tipo() {
        return tipo != null ? tipo : tier().id;
    }

    public void setTipo(@Nullable String tipo) {
        this.tipo = tipo == null || tipo.equals(tier().id) ? null : tipo;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tipo != null) tag.putString(TAG_TIPO, tipo);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tipo = tag.contains(TAG_TIPO) ? tag.getString(TAG_TIPO) : null;
    }

    /** Parametro do evento de revelacao: raridade + bit de shiny. */
    public static int revealParam(Raridade rarity, boolean shiny) {
        return rarity.ordinal() | (shiny ? 0x100 : 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level != null && level.isClientSide) {
            long now = level.getGameTime();
            if (id == EVENT_SPIN) {
                spinStart = now;
                spinDuration = Math.max(1, param);
                revealStart = Long.MIN_VALUE;
            } else if (id == EVENT_REVEAL) {
                spinStart = Long.MIN_VALUE;
                revealStart = now;
                revealRarity = Raridade.byOrdinal(param & 0xFF);
                revealShiny = (param & 0x100) != 0;
                burst();
            }
        }
        // true no servidor faz o evento ser enviado para os clientes proximos
        return id == EVENT_SPIN || id == EVENT_REVEAL;
    }

    /** 0..1 enquanto a roleta gira, -1 fora disso. */
    public float spinProgress(float partialTick) {
        if (level == null || spinStart == Long.MIN_VALUE) return -1;
        float t = (level.getGameTime() - spinStart + partialTick) / spinDuration;
        return t >= 0 && t <= 1 ? t : -1;
    }

    /** Ticks desde a revelacao (com fracao), -1 se acabou. */
    public float revealAge(float partialTick) {
        if (level == null || revealStart == Long.MIN_VALUE) return -1;
        float age = level.getGameTime() - revealStart + partialTick;
        return age >= 0 && age <= REVEAL_TICKS ? age : -1;
    }

    public Raridade revealRarity() {
        return revealRarity;
    }

    public boolean revealShiny() {
        return revealShiny;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BauBlockEntity be) {
        RandomSource random = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.45, cz = pos.getZ() + 0.5;

        float spin = be.spinProgress(0);
        if (spin >= 0) {
            Vector3f color = rgb(be.tier().color);
            long time = level.getGameTime();
            // Espiral subindo em volta do bau, mais rapida e densa perto do fim
            int count = 1 + (int) (spin * 3);
            for (int i = 0; i < count; i++) {
                double angle = time * (0.35 + spin * 0.5) + i * (Math.PI * 2 / count);
                double radius = 0.75 - spin * 0.25;
                double y = cy - 0.3 + ((time + i * 7) % 20) / 20.0 * 1.4;
                level.addParticle(new DustParticleOptions(color, 0.9F),
                        cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius, 0, 0, 0);
            }
            // Runas sendo sugadas para dentro
            if (random.nextFloat() < 0.6F) {
                level.addParticle(ParticleTypes.ENCHANT, cx, cy + 0.6, cz,
                        (random.nextFloat() - 0.5) * 2.5, random.nextFloat() * 1.2, (random.nextFloat() - 0.5) * 2.5);
            }
            if (spin > 0.7F && random.nextFloat() < spin) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (random.nextFloat() - 0.5) * 0.8, cy + 0.3,
                        cz + (random.nextFloat() - 0.5) * 0.8, 0, 0.05, 0);
            }
        }

        float age = be.revealAge(0);
        if (age >= 0 && age < 50) {
            // Chafariz de luz na cor da raridade
            Vector3f color = rgb(be.revealRarity.color);
            int count = 1 + be.revealRarity.ordinal();
            for (int i = 0; i < count; i++) {
                level.addParticle(new DustParticleOptions(color, 1.2F),
                        cx + (random.nextFloat() - 0.5) * 0.4, cy + 0.5 + random.nextFloat() * 0.3,
                        cz + (random.nextFloat() - 0.5) * 0.4, 0, 0, 0);
            }
            if (be.revealRarity.atLeast(Raridade.EPICO) && random.nextFloat() < 0.5F) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy + 0.6, cz,
                        (random.nextFloat() - 0.5) * 0.15, 0.12 + random.nextFloat() * 0.1, (random.nextFloat() - 0.5) * 0.15);
            }
            if (be.revealShiny && random.nextFloat() < 0.7F) {
                level.addParticle(ParticleTypes.WAX_OFF, cx + (random.nextFloat() - 0.5) * 1.6, cy + random.nextFloat() * 1.5,
                        cz + (random.nextFloat() - 0.5) * 1.6, 0, 0, 0);
            }
        }
    }

    private void burst() {
        if (level == null) return;
        RandomSource random = level.random;
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.8, cz = worldPosition.getZ() + 0.5;
        Vector3f color = rgb(revealRarity.color);
        int amount = 25 + revealRarity.ordinal() * 15;
        for (int i = 0; i < amount; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double speed = 0.15 + random.nextDouble() * 0.25;
            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.abs(Math.cos(phi)) * speed + 0.1;
            double vz = Math.sin(phi) * Math.sin(theta) * speed;
            level.addParticle(ParticleTypes.FIREWORK, cx, cy, cz, vx, vy, vz);
            level.addParticle(new DustParticleOptions(color, 1.5F), cx + vx * 3, cy + vy * 3, cz + vz * 3, 0, 0, 0);
        }
        if (revealRarity.atLeast(Raridade.EPICO)) {
            for (int i = 0; i < 40 + revealRarity.ordinal() * 10; i++) {
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, cx, cy, cz,
                        (random.nextFloat() - 0.5) * 0.9, random.nextFloat() * 0.9, (random.nextFloat() - 0.5) * 0.9);
            }
        }
        if (revealShiny) {
            for (int i = 0; i < 30; i++) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy, cz,
                        (random.nextFloat() - 0.5) * 0.4, random.nextFloat() * 0.4, (random.nextFloat() - 0.5) * 0.4);
            }
        }
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
    }

    /** Usado pelo renderer: 0..1 de quanto o bau deve tremer. */
    public float shakeIntensity(float partialTick) {
        float spin = spinProgress(partialTick);
        return spin < 0 ? 0 : Mth.clamp(spin * spin * 1.2F, 0, 1);
    }
}

package com.shifuchests.bau;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quais blocos do mundo sao caixas e de que tipo (salvo em world/data/shifuchests_baus.dat).
 * Guarda tambem o bloco que estava la: se alguem trocar o bloco, a marcacao deixa de valer sozinha.
 */
public final class BausNoMundo extends SavedData {
    private static final String NOME = "shifuchests_baus";

    public record Entrada(ResourceKey<Level> dimensao, BlockPos pos, String tipo, ResourceLocation bloco) {
    }

    private final Map<String, Entrada> baus = new LinkedHashMap<>();

    public static BausNoMundo de(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BausNoMundo::new, BausNoMundo::carregar, null), NOME);
    }

    private static String chave(ResourceKey<Level> dimensao, BlockPos pos) {
        return dimensao.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Caixa nesta posicao, ou null. Se o bloco mudou, a marcacao antiga e apagada. */
    @Nullable
    public Entrada em(Level level, BlockPos pos) {
        Entrada e = baus.get(chave(level.dimension(), pos));
        if (e == null) return null;
        if (level.isLoaded(pos) && !BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(e.bloco())) {
            baus.remove(chave(level.dimension(), pos));
            setDirty();
            return null;
        }
        return e;
    }

    public void definir(Level level, BlockPos pos, String tipo) {
        ResourceLocation bloco = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        baus.put(chave(level.dimension(), pos), new Entrada(level.dimension(), pos.immutable(), tipo, bloco));
        setDirty();
    }

    public boolean remover(Level level, BlockPos pos) {
        boolean removido = baus.remove(chave(level.dimension(), pos)) != null;
        if (removido) setDirty();
        return removido;
    }

    public Collection<Entrada> todas() {
        return List.copyOf(baus.values());
    }

    private static BausNoMundo carregar(CompoundTag tag, HolderLookup.Provider registries) {
        BausNoMundo dados = new BausNoMundo();
        ListTag lista = tag.getList("baus", Tag.TAG_COMPOUND);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag t = lista.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dimensao")));
            BlockPos pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            dados.baus.put(chave(dim, pos), new Entrada(dim, pos, t.getString("tipo"), ResourceLocation.parse(t.getString("bloco"))));
        }
        return dados;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag lista = new ListTag();
        for (Entrada e : baus.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("dimensao", e.dimensao().location().toString());
            t.putInt("x", e.pos().getX());
            t.putInt("y", e.pos().getY());
            t.putInt("z", e.pos().getZ());
            t.putString("tipo", e.tipo());
            t.putString("bloco", e.bloco().toString());
            lista.add(t);
        }
        tag.put("baus", lista);
        return tag;
    }
}

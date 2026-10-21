package com.shifuchests.bau;

import com.shifuchests.config.BauConfig;
import com.shifuchests.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Nome flutuando em cima de cada caixa (entidade text_display do proprio Minecraft: aparece ate para quem esta sem o mod).
 * E criado no /baus definir, refeito no /baus recarregar e no /baus hologramas, e some quando a caixa deixa de existir.
 */
public final class Hologramas {
    public static final String TAG = "shifuchests_holo";

    public static void remover(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(0.5, 0, 0.5).expandTowards(0, 2.5, 0);
        for (Display.TextDisplay holo : level.getEntitiesOfClass(Display.TextDisplay.class, area, e -> e.getTags().contains(TAG))) {
            holo.discard();
        }
    }

    public static void criar(ServerLevel level, BlockPos pos, @Nullable BauConfig config) {
        remover(level, pos);
        if (config == null) return;
        Component texto = Component.empty()
                .append(config.displayName().copy().withStyle(s -> s.withColor(config.cor).withBold(true)))
                .append(Component.literal("\n"))
                .append(Component.literal("Clique com a chave").withStyle(ChatFormatting.GRAY));
        String json = Component.Serializer.toJson(texto, level.registryAccess());
        String nbt = "{text:'" + json.replace("\\", "\\\\").replace("'", "\\'") + "',billboard:\"center\",Tags:[\"" + TAG
                + "\"],shadow:1b,line_width:200,brightness:{sky:15,block:15}}";
        CommandSourceStack fonte = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput().withPermission(4);
        level.getServer().getCommands().performPrefixedCommand(fonte, String.format(Locale.ROOT,
                "summon minecraft:text_display %.3f %.3f %.3f %s", pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, nbt));
    }

    /** Refaz os nomes de todas as caixas que estao em chunks carregados. Devolve quantos refez. */
    public static int recriarTodos(MinecraftServer server) {
        int feitos = 0;
        for (BausNoMundo.Entrada e : BausNoMundo.de(server).todas()) {
            ServerLevel level = server.getLevel(e.dimensao());
            if (level == null || !level.isLoaded(e.pos())) continue;
            criar(level, e.pos(), ConfigManager.bau(e.tipo()));
            feitos++;
        }
        return feitos;
    }

    private Hologramas() {
    }
}

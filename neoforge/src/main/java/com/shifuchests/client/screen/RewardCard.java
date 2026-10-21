package com.shifuchests.client.screen;

import com.shifuchests.network.RewardDisplay;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Locale;

/** Um premio na tela: guarda o estado de animacao do modelo e sabe se desenhar. */
public final class RewardCard {
    private static final int FULL_BRIGHT = 15;

    public final RewardDisplay display;
    private final FloatingState state = new FloatingState();
    @Nullable private final RenderablePokemon renderable;

    public RewardCard(RewardDisplay display) {
        this.display = display;
        RenderablePokemon r = null;
        if (display.species() != null) {
            Species species = PokemonSpecies.getByIdentifier(display.species());
            if (species != null) r = new RenderablePokemon(species, new HashSet<>(display.aspects()), ItemStack.EMPTY);
        }
        this.renderable = r;
    }

    public int rarityColor() {
        return display.raridade().color;
    }

    /**
     * Desenha o cartao.
     *
     * @param highlight 0..1, quanto o cartao esta "aceso" (embaixo do marcador, mouse em cima)
     * @param showChance mostra a chance (tela de previa)
     */
    public void draw(GuiGraphics g, Font font, int x, int y, int w, int h, float partialTick, float highlight,
                     boolean showChance, float seconds) {
        int rarity = rarityColor();
        int top = ScreenFx.argb(255, ScreenFx.lerpColor(ScreenFx.darken(rarity, 0.32F), ScreenFx.darken(rarity, 0.5F), highlight));
        int bottom = ScreenFx.argb(255, ScreenFx.darken(rarity, 0.12F));
        ScreenFx.panel(g, x, y, w, h, ScreenFx.argb(255, ScreenFx.lerpColor(ScreenFx.darken(rarity, 0.8F), 0xFFFFFF, highlight * 0.6F)),
                top, bottom);
        // Faixa da raridade embaixo
        g.fill(x + 1, y + h - 4, x + w - 1, y + h - 1, ScreenFx.argb(255, rarity));
        // Reflexo diagonal
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h / 3, ScreenFx.argb(40 + (int) (highlight * 40), 0xFFFFFF), ScreenFx.argb(0, 0xFFFFFF));

        int contentSize = Math.min(w - 6, h - (showChance ? 26 : 18));
        float cx = x + w / 2F;
        float contentTop = y + 3;
        drawContent(g, font, cx, contentTop, contentSize, partialTick, 35 + Mth.sin(seconds * 0.8F + x * 0.05F) * 10, 1.0F);

        // Nome
        float nameY = y + h - (showChance ? 23 : 14);
        ScreenFx.fittedText(g, font, display.name(), cx, nameY, 0.75F, w - 4, 0xFFFFFFFF);
        if (showChance) {
            String chance = formatChance(display.chance());
            ScreenFx.centeredText(g, font, Component.literal(chance), cx, y + h - 13, 0.75F, ScreenFx.argb(255, rarity), true);
        }
        if (display.shiny()) {
            ScreenFx.star(g, x + w - 6, y + 6, 3 + Mth.sin(seconds * 6) * 0.8F, ScreenFx.argb(255, ScreenFx.rainbow(seconds, 0)));
        }
        int count = display.isPokemon() ? 0 : display.icon().getCount();
        if (count > 1) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            Component badge = Component.literal("x" + count);
            int bw = (int) Math.ceil(font.width(badge) * 0.75F) + 4;
            g.fill(x + 3, y + 3, x + 3 + bw, y + 12, ScreenFx.argb(170, 0x000000));
            ScreenFx.centeredText(g, font, badge, x + 3 + bw / 2F, y + 4.5F, 0.75F, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    /** Modelo 3D do Pokemon ou o item, centralizado numa caixa quadrada de lado {@code size}. */
    public void drawContent(GuiGraphics g, Font font, float cx, float top, float size, float partialTick, float yaw, float scale) {
        if (display.isPokemon()) {
            if (renderable == null) {
                ScreenFx.centeredText(g, font, Component.literal("?"), cx, top + size / 2 - 8, 2, 0xFFFFFFFF, true);
                return;
            }
            PoseStack pose = g.pose();
            pose.pushPose();
            // Mesma conta que o PC do Cobblemon usa (slot de 25px -> escala 2.5, modelo 4.5)
            float s = size / 10F * scale;
            pose.translate(cx, top + size * 0.04F + (1 - scale) * size * 0.5F, 150);
            pose.scale(s, s, 1);
            Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(13), (float) Math.toRadians(yaw), 0);
            PokemonGuiUtilsKt.drawProfilePokemon(renderable, pose, rotation, PoseType.PROFILE, state, partialTick, 4.5F,
                    ProfileTransformType.PROFILE, false, 1, 1, 1, 1, 0, 0, FULL_BRIGHT);
            pose.popPose();
        } else {
            ItemStack stack = display.icon();
            PoseStack pose = g.pose();
            pose.pushPose();
            float itemScale = size / 16F * 0.62F * scale;
            pose.translate(cx, top + size / 2F, 150);
            pose.scale(itemScale, itemScale, itemScale);
            // Sem o numero do vanilla: a quantidade aparece no selo do cartao / no nome
            g.renderItem(stack, -8, -8);
            pose.popPose();
        }
    }

    public static String formatChance(float chance) {
        float percent = chance * 100;
        if (percent >= 10) return String.format(Locale.ROOT, "%.1f%%", percent);
        if (percent >= 1) return String.format(Locale.ROOT, "%.2f%%", percent);
        return String.format(Locale.ROOT, "%.3f%%", percent);
    }
}

package com.shifuchests.client.screen;

import com.shifuchests.network.PreviewPayload;
import com.shifuchests.network.RewardDisplay;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Vitrine do bau: todos os premios possiveis, com a chance de cada um. */
public class PreviewScreen extends Screen {
    private static final int CARD_W = 62;
    private static final int CARD_H = 92;
    private static final int GAP = 6;

    private final PreviewPayload payload;
    private final List<RewardCard> cards = new ArrayList<>();
    private final int tierColor;
    private long openedAt = -1;
    private float scroll;
    private float scrollTarget;

    public PreviewScreen(PreviewPayload payload) {
        super(payload.title());
        this.payload = payload;
        this.tierColor = payload.tier().color;
        for (RewardDisplay display : payload.rewards()) cards.add(new RewardCard(display));
        ClientSounds.play(SoundEvents.CHEST_OPEN, 1.3F, 0.6F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int columns() {
        return Math.max(1, Math.min(7, (width - 40 + GAP) / (CARD_W + GAP)));
    }

    private int gridTop() {
        return 58;
    }

    private int gridBottom() {
        return height - 30;
    }

    private float maxScroll() {
        int rows = (cards.size() + columns() - 1) / columns();
        int contentHeight = rows * (CARD_H + GAP) - GAP;
        return Math.max(0, contentHeight - (gridBottom() - gridTop()));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        g.fillGradient(0, 0, width, height, 0xA005050A, 0xD0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (openedAt < 0) openedAt = now;
        float seconds = (now - openedAt) / 1000F;
        float intro = ScreenFx.easeOutQuart(seconds / 0.35F);
        scroll = Mth.lerp(0.35F, scroll, scrollTarget);

        super.render(g, mouseX, mouseY, partialTick);

        int cx = width / 2;
        ScreenFx.rays(g, cx, 26, width * 0.6F, 10, seconds * 0.2F, tierColor, 0.06F);
        ScreenFx.centeredText(g, font, title.copy(), cx, 12 - (1 - intro) * 10, 1.8F, ScreenFx.alpha(intro, tierColor), true);
        ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.previa"), cx, 34, 1.0F,
                ScreenFx.alpha(intro * 0.8F, 0xDDDDDD), true);

        if (cards.isEmpty()) {
            ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.previa.vazio"), cx, height / 2F, 1.2F,
                    0xFFAAAAAA, true);
        }

        int cols = columns();
        int gridW = cols * (CARD_W + GAP) - GAP;
        int left = cx - gridW / 2;
        int top = gridTop();
        g.enableScissor(0, top - 4, width, gridBottom() + 4);
        RewardCard hovered = null;
        for (int i = 0; i < cards.size(); i++) {
            int col = i % cols, row = i / cols;
            int x = left + col * (CARD_W + GAP);
            int y = top + row * (CARD_H + GAP) - Math.round(scroll);
            if (y + CARD_H < top - 4 || y > gridBottom() + 4) continue;
            // Entrada em cascata
            float delay = Math.min(0.6F, i * 0.03F);
            float cardIn = ScreenFx.easeOutBack((seconds - delay) / 0.35F);
            if (cardIn <= 0.01F) continue;
            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H
                    && mouseY >= top && mouseY <= gridBottom();
            if (hover) hovered = cards.get(i);
            float cardCx = x + CARD_W / 2F, cardCy = y + CARD_H / 2F;
            g.pose().pushPose();
            g.pose().translate(cardCx, cardCy - (hover ? 2 : 0), 0);
            float scale = cardIn * (hover ? 1.05F : 1F);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-cardCx, -cardCy, 0);
            if (hover) ScreenFx.glow(g, cardCx, cardCy, CARD_W * 0.9F, cards.get(i).rarityColor(), 0.4F);
            cards.get(i).draw(g, font, x, y, CARD_W, CARD_H, partialTick, hover ? 1 : 0.15F, true, seconds);
            g.pose().popPose();
        }
        g.disableScissor();

        // Barra de rolagem
        float max = maxScroll();
        if (max > 0) {
            int trackTop = top, trackBottom = gridBottom();
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(20, (int) (trackH * (trackH / (trackH + max))));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / max));
            int bx = left + gridW + 6;
            g.fill(bx, trackTop, bx + 2, trackBottom, 0x40FFFFFF);
            g.fill(bx, thumbY, bx + 2, thumbY + thumbH, ScreenFx.argb(200, tierColor));
        }

        Component keyName = Component.translatable("item.shifuchests." + payload.tier().keyId()).withColor(tierColor);
        ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.previa.dica", keyName), cx, height - 20, 1.0F,
                0xFFBBBBBB, true);

        if (hovered != null) {
            RewardDisplay d = hovered.display;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(d.name().copy().withColor(d.raridade().color));
            tooltip.add(d.raridade().displayName().copy().withColor(d.raridade().color));
            tooltip.add(Component.literal(RewardCard.formatChance(d.chance())).withColor(0xFFFFFF));
            if (d.shiny()) tooltip.add(Component.translatable("screen.shifuchests.shiny").withColor(ScreenFx.rainbow(seconds, 0)));
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * 40, 0, maxScroll());
        return true;
    }
}

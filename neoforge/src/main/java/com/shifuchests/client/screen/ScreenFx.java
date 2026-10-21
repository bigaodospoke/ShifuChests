package com.shifuchests.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Desenhos que o GuiGraphics nao tem: raios de luz, brilho redondo, estrelas e texto escalado. */
public final class ScreenFx {
    public static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(float alpha, int rgb) {
        return argb((int) (alpha * 255), rgb);
    }

    /** Mistura a cor com preto (factor 0) ou mantem (factor 1). */
    public static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    public static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Arco-iris que anda com o tempo (texto shiny). */
    public static int rainbow(float seconds, float offset) {
        float hue = (seconds * 0.35F + offset) % 1F;
        return Mth.hsvToRgb(hue, 0.55F, 1F);
    }

    /** Leque de raios saindo do centro, girando. Cor no centro, transparente na ponta. */
    public static void rays(GuiGraphics g, float cx, float cy, float radius, int count, float rotation, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        float width = (float) (Math.PI / count) * 0.55F;
        for (int i = 0; i < count; i++) {
            float angle = rotation + i * (float) (Math.PI * 2 / count);
            float x1 = cx + Mth.cos(angle - width) * radius, y1 = cy + Mth.sin(angle - width) * radius;
            float x2 = cx + Mth.cos(angle + width) * radius, y2 = cy + Mth.sin(angle + width) * radius;
            triangle(vc, m, cx, cy, x1, y1, x2, y2, argb((int) (alpha * 255), rgb), argb(0, rgb));
        }
    }

    /** Circulo de brilho: forte no centro, sumindo na borda. */
    public static void glow(GuiGraphics g, float cx, float cy, float radius, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int segments = 32;
        int inner = argb((int) (alpha * 255), rgb);
        int outer = argb(0, rgb);
        for (int i = 0; i < segments; i++) {
            float a1 = i * (float) (Math.PI * 2 / segments);
            float a2 = (i + 1) * (float) (Math.PI * 2 / segments);
            triangle(vc, m, cx, cy, cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius,
                    cx + Mth.cos(a2) * radius, cy + Mth.sin(a2) * radius, inner, outer);
        }
    }

    /** Estrela de 4 pontas (brilho de shiny / faisca). */
    public static void star(GuiGraphics g, float cx, float cy, float size, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        float thin = size * 0.28F;
        // 4 pontas como triangulos finos, cor solida
        triangle(vc, m, cx, cy - size, cx - thin, cy, cx + thin, cy, argb, argb);
        triangle(vc, m, cx, cy + size, cx + thin, cy, cx - thin, cy, argb, argb);
        triangle(vc, m, cx - size, cy, cx, cy + thin, cx, cy - thin, argb, argb);
        triangle(vc, m, cx + size, cy, cx, cy - thin, cx, cy + thin, argb, argb);
    }

    /** Triangulo como um quad degenerado (o RenderType.gui desenha quads). */
    private static void triangle(VertexConsumer vc, Matrix4f m, float cx, float cy, float x1, float y1, float x2, float y2,
                                 int centerColor, int edgeColor) {
        // Mesma orientacao do GuiGraphics.fill, senao o triangulo e descartado pelo culling
        float cross = (x1 - cx) * (y2 - cy) - (y1 - cy) * (x2 - cx);
        if (cross > 0) {
            float tx = x1, ty = y1;
            x1 = x2; y1 = y2;
            x2 = tx; y2 = ty;
        }
        vc.addVertex(m, cx, cy, 0).setColor(centerColor);
        vc.addVertex(m, x1, y1, 0).setColor(edgeColor);
        vc.addVertex(m, x2, y2, 0).setColor(edgeColor);
        vc.addVertex(m, cx, cy, 0).setColor(centerColor);
    }

    /** Borda de 1px. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Painel escuro com cantos "arredondados" de 1px e borda colorida. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int borderArgb, int fillTop, int fillBottom) {
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, fillTop, fillBottom);
        g.fill(x + 1, y, x + w - 1, y + 1, borderArgb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, borderArgb);
        g.fill(x, y + 1, x + 1, y + h - 1, borderArgb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, borderArgb);
    }

    public static void centeredText(GuiGraphics g, Font font, Component text, float cx, float y, float scale, int argb, boolean shadow) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y, 0);
        pose.scale(scale, scale, 1);
        FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font, seq, -font.width(seq) / 2, 0, argb, shadow);
        pose.popPose();
    }

    /** Texto centralizado que encolhe para caber em {@code maxWidth}. */
    public static void fittedText(GuiGraphics g, Font font, Component text, float cx, float y, float scale, float maxWidth, int argb) {
        float width = font.width(text) * scale;
        float finalScale = width > maxWidth ? scale * (maxWidth / width) : scale;
        centeredText(g, font, text, cx, y + (scale - finalScale) * font.lineHeight / 2, finalScale, argb, true);
    }

    public static float easeOutQuart(float t) {
        float inv = 1 - Mth.clamp(t, 0, 1);
        return 1 - inv * inv * inv * inv;
    }

    public static float easeOutBack(float t) {
        t = Mth.clamp(t, 0, 1);
        float c1 = 1.70158F, c3 = c1 + 1;
        float x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }

    public static float easeInOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private ScreenFx() {
    }
}

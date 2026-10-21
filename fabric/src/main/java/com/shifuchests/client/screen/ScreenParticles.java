package com.shifuchests.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Particulas desenhadas na propria tela: faiscas, estrelas e confete. */
public final class ScreenParticles {
    public enum Kind { SPARK, STAR, CONFETTI }

    private static final class Particle {
        Kind kind;
        float x, y, vx, vy, gravity, drag;
        float age, life, size, rotation, spin;
        int rgb;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    public boolean isEmpty() {
        return particles.isEmpty();
    }

    /** Explosao saindo de um ponto. */
    public void burst(float x, float y, int count, float speed, int[] colors, Kind kind) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float v = speed * (0.35F + random.nextFloat() * 0.65F);
            Particle p = add(kind, x, y, Mth.cos(angle) * v, Mth.sin(angle) * v - speed * 0.15F, colors[random.nextInt(colors.length)]);
            p.gravity = kind == Kind.CONFETTI ? 90 : 60;
            p.life = 0.9F + random.nextFloat() * 0.9F;
        }
    }

    /** Confete caindo do topo da tela. */
    public void rain(float width, int count, int[] colors) {
        for (int i = 0; i < count; i++) {
            Particle p = add(Kind.CONFETTI, random.nextFloat() * width, -10 - random.nextFloat() * 80,
                    (random.nextFloat() - 0.5F) * 30, 30 + random.nextFloat() * 50, colors[random.nextInt(colors.length)]);
            p.gravity = 25;
            p.drag = 0.4F;
            p.life = 3.5F + random.nextFloat() * 2;
        }
    }

    /** Brilho parado que pisca (em volta de um shiny). */
    public void twinkle(float cx, float cy, float radius, int rgb) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float dist = radius * (0.3F + random.nextFloat() * 0.7F);
        Particle p = add(Kind.STAR, cx + Mth.cos(angle) * dist, cy + Mth.sin(angle) * dist, 0, -6, rgb);
        p.life = 0.6F + random.nextFloat() * 0.5F;
        p.size = 2.5F + random.nextFloat() * 3;
    }

    private Particle add(Kind kind, float x, float y, float vx, float vy, int rgb) {
        Particle p = new Particle();
        p.kind = kind;
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.rgb = rgb;
        p.drag = 1.2F;
        p.life = 1;
        p.size = switch (kind) {
            case SPARK -> 1 + random.nextFloat() * 1.5F;
            case STAR -> 2 + random.nextFloat() * 3;
            case CONFETTI -> 2 + random.nextFloat() * 2;
        };
        p.rotation = random.nextFloat() * 360;
        p.spin = (random.nextFloat() - 0.5F) * 720;
        particles.add(p);
        return p;
    }

    public void update(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            float dragFactor = Math.max(0, 1 - p.drag * dt);
            p.vx *= dragFactor;
            p.vy = p.vy * dragFactor + p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rotation += p.spin * dt;
        }
    }

    public void render(GuiGraphics g) {
        PoseStack pose = g.pose();
        for (Particle p : particles) {
            float t = p.age / p.life;
            float alpha = t < 0.15F ? t / 0.15F : 1 - Math.max(0, (t - 0.6F) / 0.4F);
            int color = ScreenFx.alpha(alpha, p.rgb);
            switch (p.kind) {
                case SPARK -> {
                    float s = p.size * (1 - t * 0.5F);
                    g.fill((int) (p.x - s), (int) (p.y - s), (int) Math.ceil(p.x + s), (int) Math.ceil(p.y + s), color);
                }
                case STAR -> {
                    float pulse = Mth.sin(t * Mth.PI);
                    ScreenFx.star(g, p.x, p.y, p.size * (0.4F + pulse), color);
                }
                case CONFETTI -> {
                    pose.pushPose();
                    pose.translate(p.x, p.y, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(p.rotation));
                    // "Vira" o papel encolhendo uma das larguras
                    float flip = Math.abs(Mth.cos(p.rotation * 0.05F));
                    int w = Math.max(1, (int) (p.size * 1.6F * flip));
                    int h = Math.max(1, (int) p.size);
                    g.fill(-w, -h, w, h, color);
                    pose.popPose();
                }
            }
        }
    }
}

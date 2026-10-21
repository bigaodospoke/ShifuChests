package com.shifuchests.client.screen;

import com.shifuchests.config.Raridade;
import com.shifuchests.network.OpenRoulettePayload;
import com.shifuchests.network.RewardDisplay;
import com.shifuchests.network.SkipPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A roleta: uma fita de cartoes passa rapido por baixo do marcador e vai freando ate parar no premio
 * (que o servidor ja sorteou). Depois vem a revelacao com raios de luz, particulas, sons e o grito do Pokemon.
 */
public class RouletteScreen extends Screen {
    private static final int CARD_W = 62;
    private static final int CARD_H = 82;
    private static final int GAP = 5;
    private static final int STRIDE = CARD_W + GAP;
    /** Cartao que comeca embaixo do marcador. */
    private static final int START_INDEX = 4;
    private static final long INTRO_MS = 450;
    private static final long SETTLE_MS = 450;
    /** Pausa entre a roleta parar e a revelacao. */
    private static final long REVEAL_DELAY_MS = 750;
    private static final long CONTINUE_HINT_MS = 1400;
    private static final ResourceLocation SHINY_CHIME = ResourceLocation.fromNamespaceAndPath("cobblemon", "particle.shiny_chime");

    private final OpenRoulettePayload payload;
    private final List<RewardCard> cards = new ArrayList<>();
    private final RewardCard winner;
    private final int tierColor;
    private final long spinMs;
    private final long revealAt;
    /** Onde dentro do cartao vencedor o marcador para (suspense de "quase foi o do lado"). */
    private final float jitter;
    private final ScreenParticles particles = new ScreenParticles();
    private final ClientSounds sounds = new ClientSounds();

    private long openedAt = -1;
    private long skipShift;
    private long lastFrame;
    private int lastTickIndex = Integer.MIN_VALUE;
    private boolean stopped;
    private boolean revealed;
    private boolean skipSent;
    private float nextLegendaryBurst = 0.9F;

    public RouletteScreen(OpenRoulettePayload payload) {
        super(payload.title());
        this.payload = payload;
        for (RewardDisplay display : payload.strip()) cards.add(new RewardCard(display));
        this.winner = cards.get(payload.winnerIndex());
        this.tierColor = payload.tier().color;
        this.spinMs = payload.durationTicks() * 50L;
        this.revealAt = spinMs + REVEAL_DELAY_MS;
        RandomSource random = RandomSource.create();
        this.jitter = (random.nextFloat() - 0.5F) * CARD_W * 0.8F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long elapsed() {
        return Util.getMillis() - openedAt + skipShift;
    }

    /** Posicao do marcador ao longo da fita (px a partir do inicio do primeiro cartao). */
    private float markerOffset(long e) {
        float start = START_INDEX * STRIDE + CARD_W / 2F;
        float exact = payload.winnerIndex() * STRIDE + CARD_W / 2F;
        float target = exact + jitter;
        if (e < spinMs) return Mth.lerp(ScreenFx.easeOutQuart((float) e / spinMs), start, target);
        return Mth.lerp(ScreenFx.easeInOut((float) (e - spinMs) / SETTLE_MS), target, exact);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        long e = openedAt < 0 ? 0 : elapsed();
        float intro = Mth.clamp((float) e / INTRO_MS, 0, 1);
        g.fillGradient(0, 0, width, height, ScreenFx.alpha(0.55F * intro, 0x05050A), ScreenFx.alpha(0.85F * intro, 0x000000));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (openedAt < 0) {
            openedAt = now;
            lastFrame = now;
        }
        float dt = Mth.clamp((now - lastFrame) / 1000F, 0, 0.1F);
        lastFrame = now;
        long e = elapsed();
        float seconds = e / 1000F;
        sounds.update(e);
        particles.update(dt);

        super.render(g, mouseX, mouseY, partialTick);

        if (!revealed && e >= revealAt) startReveal();

        float revealFade = Mth.clamp((e - revealAt + 150) / 350F, 0, 1);
        if (revealFade < 1) renderSpin(g, e, seconds, partialTick, 1 - revealFade);
        if (revealed) {
            // Acima da fita (modelos em z 150, bordas em z 400)
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            renderReveal(g, e, seconds, partialTick);
            g.pose().popPose();
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 1200);
        particles.render(g);
        renderFooter(g, e, seconds);
        g.pose().popPose();
    }

    private void renderSpin(GuiGraphics g, long e, float seconds, float partialTick, float visibility) {
        float intro = ScreenFx.easeOutBack(Mth.clamp((float) e / INTRO_MS, 0, 1));
        int cx = width / 2;
        int cy = height / 2 - 4 + (int) ((1 - intro) * 40);
        int visibleW = Math.min(width - 24, STRIDE * 7 + 24);
        int px = cx - visibleW / 2;
        int py = cy - CARD_H / 2 - 9;
        int ph = CARD_H + 18;

        // Raios suaves atras da roleta
        ScreenFx.rays(g, cx, cy, width * 0.75F, 12, seconds * 0.25F, tierColor, 0.07F * visibility);

        // Titulo
        ScreenFx.centeredText(g, font, title.copy(), cx, py - 34, 1.6F, ScreenFx.alpha(visibility, tierColor), true);
        if (e < spinMs) {
            int dots = (int) (seconds * 3) % 4;
            Component spinning = Component.translatable("screen.shifuchests.girando").append(".".repeat(dots));
            ScreenFx.centeredText(g, font, spinning, cx, py - 15, 1.0F, ScreenFx.alpha(0.8F * visibility, 0xDDDDDD), true);
        }

        ScreenFx.panel(g, px, py, visibleW, ph, ScreenFx.alpha(visibility, tierColor),
                ScreenFx.alpha(0.92F * visibility, 0x14141E), ScreenFx.alpha(0.92F * visibility, 0x08080D));

        float offset = markerOffset(e);
        g.enableScissor(px + 1, py + 1, px + visibleW - 1, py + ph - 1);
        int first = Math.max(0, (int) ((offset - visibleW / 2F) / STRIDE) - 1);
        int last = Math.min(cards.size() - 1, (int) ((offset + visibleW / 2F) / STRIDE) + 1);
        for (int i = first; i <= last; i++) {
            float cardCenter = cx + (i * STRIDE + CARD_W / 2F - offset);
            float distance = Math.abs(cardCenter - cx);
            float highlight = 1 - Mth.clamp(distance / (STRIDE * 0.6F), 0, 1);
            float scale = 1 + highlight * 0.06F;
            if (stopped && i == payload.winnerIndex()) {
                float pulse = (Mth.sin(seconds * 10) + 1) / 2;
                scale += 0.04F * pulse;
                highlight = 1;
                ScreenFx.glow(g, cardCenter, cy, CARD_W * 0.95F, cards.get(i).rarityColor(), 0.55F * visibility);
            }
            g.pose().pushPose();
            g.pose().translate(cardCenter, cy, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-cardCenter, -cy, 0);
            cards.get(i).draw(g, font, Math.round(cardCenter - CARD_W / 2F), cy - CARD_H / 2, CARD_W, CARD_H, partialTick,
                    highlight, false, seconds);
            g.pose().popPose();
        }
        g.disableScissor();

        // Escurece as pontas da fita
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int fade = Math.min(70, visibleW / 4);
        for (int k = 0; k < fade; k += 2) {
            int a = (int) (215 * (1 - (float) k / fade) * visibility);
            g.fill(px + 1 + k, py + 1, px + 3 + k, py + ph - 1, ScreenFx.argb(a, 0x08080D));
            g.fill(px + visibleW - 3 - k, py + 1, px + visibleW - 1 - k, py + ph - 1, ScreenFx.argb(a, 0x08080D));
        }

        // Marcador dourado
        int marker = ScreenFx.alpha(visibility, 0xFFD54A);
        ScreenFx.glow(g, cx, py, 14, 0xFFD54A, 0.6F * visibility);
        ScreenFx.glow(g, cx, py + ph, 14, 0xFFD54A, 0.6F * visibility);
        g.fill(cx - 1, py - 4, cx + 1, py + ph + 4, marker);
        for (int k = 0; k < 6; k++) {
            g.fill(cx - (6 - k), py - 10 + k, cx + (6 - k), py - 9 + k, marker);
            g.fill(cx - (6 - k), py + ph + 9 - k, cx + (6 - k), py + ph + 10 - k, marker);
        }
        g.pose().popPose();

        // Som e faiscas a cada cartao que passa pelo marcador
        int index = (int) Math.floor(offset / STRIDE);
        if (e < spinMs && index != lastTickIndex) {
            if (lastTickIndex != Integer.MIN_VALUE) {
                float progress = (float) e / spinMs;
                ClientSounds.play(SoundEvents.NOTE_BLOCK_HAT, 1.0F + progress * 0.7F, 0.45F);
                particles.burst(cx, py - 4, 3, 70, new int[]{0xFFD54A, 0xFFFFFF}, ScreenParticles.Kind.SPARK);
                particles.burst(cx, py + ph + 4, 3, 70, new int[]{0xFFD54A, 0xFFFFFF}, ScreenParticles.Kind.SPARK);
            }
            lastTickIndex = index;
        }
        if (!stopped && e >= spinMs) {
            stopped = true;
            ClientSounds.play(SoundEvents.NOTE_BLOCK_BELL, 1.2F, 0.9F);
            ClientSounds.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            particles.burst(cx, cy, 18, 120, new int[]{winner.rarityColor(), 0xFFFFFF}, ScreenParticles.Kind.SPARK);
        }
    }

    private void startReveal() {
        revealed = true;
        stopped = true;
        Raridade raridade = winner.display.raridade();
        int color = raridade.color;
        float cx = width / 2F, cy = revealCenterY();
        long e = elapsed();

        int ord = raridade.ordinal();
        particles.burst(cx, cy, 50 + ord * 25, 150 + ord * 30, new int[]{color, 0xFFFFFF, ScreenFx.lerpColor(color, 0xFFFFFF, 0.5F)},
                ScreenParticles.Kind.SPARK);
        particles.burst(cx, cy, 10 + ord * 6, 110, new int[]{color, 0xFFFFFF}, ScreenParticles.Kind.STAR);
        if (raridade.atLeast(Raridade.EPICO)) {
            particles.rain(width, 60 + ord * 25, new int[]{color, 0xFFFFFF, 0xFFD54A, 0x5CE65C, 0x4FC3FF, 0xFF4D7A});
        }

        ClientSounds.play(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F);
        ClientSounds.play(SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 1.0F);
        if (raridade.atLeast(Raridade.RARO)) {
            float[] notes = {0.8F, 1.0F, 1.19F, 1.59F};
            for (int i = 0; i < notes.length; i++) {
                float pitch = notes[i];
                sounds.schedule(e + 90L * i, () -> ClientSounds.play(SoundEvents.NOTE_BLOCK_BELL, pitch, 0.8F));
            }
        }
        if (raridade.atLeast(Raridade.EPICO)) ClientSounds.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F);
        if (raridade.atLeast(Raridade.LENDARIO)) {
            ClientSounds.play(SoundEvents.TOTEM_USE, 1.2F, 0.35F);
            ClientSounds.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9F, 1.0F);
            sounds.schedule(e + 450, () -> ClientSounds.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0F, 1.0F));
        }
        if (winner.display.shiny()) {
            sounds.schedule(e + 200, () -> {
                if (!ClientSounds.playIfExists(SHINY_CHIME, 1.0F, 1.0F)) ClientSounds.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.0F);
            });
        }
        if (winner.display.isPokemon() && winner.display.species() != null) {
            ResourceLocation species = winner.display.species();
            ResourceLocation cry = ResourceLocation.fromNamespaceAndPath(species.getNamespace(), "pokemon." + species.getPath() + ".cry");
            sounds.schedule(e + 380, () -> ClientSounds.playIfExists(cry, 1.0F, 1.0F));
        }
    }

    /** Tamanho do premio na revelacao, cabendo em telas baixas (GUI grande). */
    private float revealSize() {
        return Mth.clamp(height * 0.36F, 56, 120);
    }

    /** Topo do bloco "VOCE GANHOU / premio / textos", centralizado acima do rodape. */
    private float revealTop() {
        RewardDisplay d = winner.display;
        float blockH = 24 + revealSize() + 4 + 22 + 15 + (d.shiny() ? 14 : 0) + (d.level() > 0 ? 11 : 0);
        return Math.max(4, (height - 24 - blockH) / 2);
    }

    private float revealCenterY() {
        return revealTop() + 24 + revealSize() / 2;
    }

    private void renderReveal(GuiGraphics g, long e, float seconds, float partialTick) {
        RewardDisplay d = winner.display;
        Raridade raridade = d.raridade();
        int color = raridade.color;
        float r = (e - revealAt) / 1000F;
        float appear = Mth.clamp(r / 0.35F, 0, 1);
        float pop = ScreenFx.easeOutBack(r / 0.55F);
        float size = revealSize();
        float top = revealTop();
        float cx = width / 2F;
        float cy = revealCenterY() + Mth.sin(r * 2.2F) * 2;

        g.fill(0, 0, width, height, ScreenFx.alpha(0.55F * appear, 0x000000));

        // Raios girando em sentidos opostos + brilho
        float raysRadius = Math.max(width, height) * 0.6F;
        ScreenFx.rays(g, cx, cy, raysRadius, 14 + raridade.ordinal() * 2, seconds * 0.35F, color, 0.32F * appear);
        ScreenFx.rays(g, cx, cy, raysRadius * 0.8F, 9, -seconds * 0.22F, 0xFFFFFF, 0.08F * appear);
        ScreenFx.glow(g, cx, cy, size * 0.75F + Mth.sin(r * 3) * 6, color, 0.6F * appear);
        if (d.shiny()) ScreenFx.glow(g, cx, cy, size * 0.5F, ScreenFx.rainbow(seconds, 0), 0.25F * appear);

        // O premio
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        winner.drawContent(g, font, cx, cy - size / 2, size, partialTick, 25 + Mth.sin(r * 0.9F) * 25, Math.max(0.01F, pop));
        g.pose().popPose();

        // Textos
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        float textIn = Mth.clamp((r - 0.15F) / 0.35F, 0, 1);
        ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.voce_ganhou"), cx,
                top + 2 - (1 - textIn) * 10, 1.8F, ScreenFx.alpha(textIn, ScreenFx.lerpColor(color, 0xFFFFFF, 0.35F)), true);

        float y = top + 24 + size + 4;
        Component name = d.name();
        if (!d.isPokemon() && d.icon().getCount() > 1) name = Component.literal(d.icon().getCount() + "x ").append(name);
        ScreenFx.fittedText(g, font, name, cx, y, 2.2F, width - 20, ScreenFx.alpha(textIn, 0xFFFFFF));
        y += 24;
        Component rarityText = Component.literal("★ ").append(raridade.displayName().copy()).append(" ★");
        ScreenFx.centeredText(g, font, rarityText, cx, y, 1.25F, ScreenFx.alpha(textIn, color), true);
        y += 15;
        if (d.shiny()) {
            ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.shiny"), cx, y, 1.2F,
                    ScreenFx.alpha(textIn, ScreenFx.rainbow(seconds, 0.2F)), true);
            y += 14;
        }
        if (d.isPokemon() && d.level() > 0) {
            ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.nivel", d.level()), cx, y, 1.0F,
                    ScreenFx.alpha(textIn, 0xBBBBBB), true);
        }
        g.pose().popPose();

        // Brilhos continuos
        if (d.shiny() && Math.random() < 0.35) particles.twinkle(cx, cy, size * 0.65F, ScreenFx.rainbow(seconds, (float) Math.random()));
        if (raridade.atLeast(Raridade.LENDARIO) && r > nextLegendaryBurst && r < 4) {
            nextLegendaryBurst += 0.8F;
            float bx = cx + (float) (Math.random() - 0.5) * width * 0.6F;
            float by = cy + (float) (Math.random() - 0.5) * height * 0.5F;
            particles.burst(bx, by, 30, 110, new int[]{color, 0xFFFFFF, 0xFFD54A}, ScreenParticles.Kind.SPARK);
            ClientSounds.play(SoundEvents.FIREWORK_ROCKET_BLAST_FAR, 1.0F + (float) Math.random() * 0.3F, 0.6F);
        }
    }

    private void renderFooter(GuiGraphics g, long e, float seconds) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        if (payload.test()) {
            ScreenFx.centeredText(g, font, Component.translatable("screen.shifuchests.teste"), width / 2F, 8, 1.0F, 0xFFFF5555, true);
        }
        Component hint = null;
        if (revealed && e - revealAt > CONTINUE_HINT_MS) {
            hint = Component.translatable("screen.shifuchests.continuar");
        } else if (!revealed && payload.canSkip() && e > INTRO_MS) {
            hint = Component.translatable("screen.shifuchests.pular");
        }
        if (hint != null) {
            float blink = 0.55F + 0.45F * (Mth.sin(seconds * 4) + 1) / 2;
            ScreenFx.centeredText(g, font, hint, width / 2F, height - 22, 1.0F, ScreenFx.alpha(blink, 0xCCCCCC), true);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ entrada

    private void skip() {
        if (revealed || openedAt < 0) return;
        long now = Util.getMillis() - openedAt + skipShift;
        if (now < revealAt) skipShift += revealAt - now;
        lastTickIndex = Integer.MIN_VALUE;
        sendSkip();
    }

    private void sendSkip() {
        if (skipSent) return;
        skipSent = true;
        PacketDistributor.sendToServer(SkipPayload.INSTANCE);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (revealed) {
                onClose();
            } else if (payload.canSkip()) {
                skip();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealed && elapsed() - revealAt > 600) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // Fechar no meio: se pode pular, o servidor entrega na hora; senao entrega quando o tempo acabar
        if (!revealed && payload.canSkip()) sendSkip();
        sounds.clear();
        super.onClose();
    }
}

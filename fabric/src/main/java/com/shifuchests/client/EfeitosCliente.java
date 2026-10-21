package com.shifuchests.client;

import com.shifuchests.config.Raridade;
import com.shifuchests.network.EfeitoBauPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Animacoes das caixas no mundo, so no cliente. O bloco e do Cobblemon (bau dourado), entao em vez de um renderer
 * proprio o mod mexe na pose de qualquer block entity que esteja numa caixa animada (mixin no BlockEntityRenderDispatcher):
 * treme enquanto a roleta gira, pula girando na revelacao; o feixe de luz e as particulas sao desenhados a parte.
 */
public final class EfeitosCliente {
    public static final int REVEAL_TICKS = 80;

    private static final class Efeito {
        long spinStart = Long.MIN_VALUE;
        int spinDuration = 1;
        long revealStart = Long.MIN_VALUE;
        Raridade raridade = Raridade.COMUM;
        boolean shiny;
        int cor = 0xFFB300;
    }

    private static final Map<BlockPos, Efeito> ATIVOS = new HashMap<>();
    /** Se a chamada de render atual empilhou uma pose (o render nao e reentrante, mas por garantia e uma pilha). */
    private static final Deque<Boolean> EMPILHOU = new ArrayDeque<>();

    public static void receber(EfeitoBauPayload payload, ClientLevel level) {
        BlockPos pos = payload.pos().immutable();
        Efeito e = ATIVOS.computeIfAbsent(pos, k -> new Efeito());
        long now = level.getGameTime();
        e.cor = payload.cor();
        if (payload.tipo() == EfeitoBauPayload.GIRANDO) {
            e.spinStart = now;
            e.spinDuration = Math.max(1, payload.duracao());
            e.revealStart = Long.MIN_VALUE;
        } else if (payload.tipo() == EfeitoBauPayload.REVELACAO) {
            e.spinStart = Long.MIN_VALUE;
            e.revealStart = now;
            e.raridade = Raridade.byOrdinal(payload.raridade());
            e.shiny = payload.shiny();
            burst(level, pos, e);
        }
    }

    public static void limpar() {
        ATIVOS.clear();
        EMPILHOU.clear();
    }

    /** 0..1 enquanto a roleta gira, -1 fora disso. */
    private static float spinProgress(Efeito e, long gameTime, float partialTick) {
        if (e.spinStart == Long.MIN_VALUE) return -1;
        float t = (gameTime - e.spinStart + partialTick) / e.spinDuration;
        return t >= 0 && t <= 1 ? t : -1;
    }

    /** Ticks desde a revelacao (com fracao), -1 se acabou. */
    private static float revealAge(Efeito e, long gameTime, float partialTick) {
        if (e.revealStart == Long.MIN_VALUE) return -1;
        float age = gameTime - e.revealStart + partialTick;
        return age >= 0 && age <= REVEAL_TICKS ? age : -1;
    }

    // ------------------------------------------------------------------ pose do bloco (mixin)

    public static void antesDeDesenhar(BlockEntity be, float partialTick, PoseStack pose) {
        Efeito e = ATIVOS.get(be.getBlockPos());
        if (e == null || be.getLevel() == null) {
            EMPILHOU.push(false);
            return;
        }
        long gameTime = be.getLevel().getGameTime();
        float time = gameTime + partialTick;
        float spin = spinProgress(e, gameTime, partialTick);
        float age = revealAge(e, gameTime, partialTick);
        if (spin < 0 && age < 0) {
            EMPILHOU.push(false);
            return;
        }
        pose.pushPose();
        EMPILHOU.push(true);
        float lift = 0;
        if (spin >= 0) {
            // Treme como se algo quisesse sair, e flutua um pouco
            float shake = Mth.clamp(spin * spin * 1.2F, 0, 1);
            lift = shake * 0.12F + Mth.sin(time * 0.25F) * 0.02F * shake;
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 2.7F) * 6 * shake));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(time * 3.1F) * 4 * shake));
            pose.translate(-0.5, 0, -0.5);
        } else {
            // Pulo com um giro completo e "estica e encolhe" ao cair
            float jump = Mth.clamp(age / 14F, 0, 1);
            lift = Mth.sin(jump * Mth.PI) * 0.6F;
            float spinY = easeOut(jump) * 360;
            float squash = age < 20 ? Mth.sin(Mth.clamp((age - 14) / 6F, 0, 1) * Mth.PI) * 0.15F : 0;
            float scale = 1 + squash;
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(spinY));
            pose.scale(scale, 1 - squash * 0.8F, scale);
            pose.translate(-0.5, 0, -0.5);
        }
        pose.translate(0, lift, 0);
    }

    public static void depoisDeDesenhar(PoseStack pose) {
        Boolean empilhou = EMPILHOU.poll();
        if (Boolean.TRUE.equals(empilhou)) pose.popPose();
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ------------------------------------------------------------------ particulas

    public static void tick(ClientLevel level) {
        if (ATIVOS.isEmpty()) return;
        RandomSource random = level.random;
        long time = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Efeito>> it = ATIVOS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Efeito> entry = it.next();
            BlockPos pos = entry.getKey();
            Efeito e = entry.getValue();
            float spin = spinProgress(e, time, 0);
            float age = revealAge(e, time, 0);
            boolean spinAcabou = e.spinStart == Long.MIN_VALUE || time - e.spinStart > e.spinDuration + 200;
            if (spin < 0 && age < 0 && spinAcabou) {
                it.remove();
                continue;
            }
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.45, cz = pos.getZ() + 0.5;
            if (spin >= 0) {
                Vector3f color = rgb(e.cor);
                // Espiral subindo em volta da caixa, mais rapida e densa perto do fim
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
            if (age >= 0 && age < 50) {
                // Chafariz de luz na cor da raridade
                Vector3f color = rgb(e.raridade.color);
                int count = 1 + e.raridade.ordinal();
                for (int i = 0; i < count; i++) {
                    level.addParticle(new DustParticleOptions(color, 1.2F),
                            cx + (random.nextFloat() - 0.5) * 0.4, cy + 0.5 + random.nextFloat() * 0.3,
                            cz + (random.nextFloat() - 0.5) * 0.4, 0, 0, 0);
                }
                if (e.raridade.atLeast(Raridade.EPICO) && random.nextFloat() < 0.5F) {
                    level.addParticle(ParticleTypes.END_ROD, cx, cy + 0.6, cz,
                            (random.nextFloat() - 0.5) * 0.15, 0.12 + random.nextFloat() * 0.1, (random.nextFloat() - 0.5) * 0.15);
                }
                if (e.shiny && random.nextFloat() < 0.7F) {
                    level.addParticle(ParticleTypes.WAX_OFF, cx + (random.nextFloat() - 0.5) * 1.6, cy + random.nextFloat() * 1.5,
                            cz + (random.nextFloat() - 0.5) * 1.6, 0, 0, 0);
                }
            }
        }
    }

    private static void burst(ClientLevel level, BlockPos pos, Efeito e) {
        RandomSource random = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.8, cz = pos.getZ() + 0.5;
        Vector3f color = rgb(e.raridade.color);
        int amount = 25 + e.raridade.ordinal() * 15;
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
        if (e.raridade.atLeast(Raridade.EPICO)) {
            for (int i = 0; i < 40 + e.raridade.ordinal() * 10; i++) {
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, cx, cy, cz,
                        (random.nextFloat() - 0.5) * 0.9, random.nextFloat() * 0.9, (random.nextFloat() - 0.5) * 0.9);
            }
        }
        if (e.shiny) {
            for (int i = 0; i < 30; i++) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy, cz,
                        (random.nextFloat() - 0.5) * 0.4, random.nextFloat() * 0.4, (random.nextFloat() - 0.5) * 0.4);
            }
        }
    }

    // ------------------------------------------------------------------ feixe de luz

    public static void renderFeixes(WorldRenderContext context) {
        if (ATIVOS.isEmpty() || context.world() == null) return;
        MultiBufferSource buffers = context.consumers();
        PoseStack pose = context.matrixStack();
        if (buffers == null || pose == null) return;
        long gameTime = context.world().getGameTime();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.camera().getPosition();
        for (Map.Entry<BlockPos, Efeito> entry : ATIVOS.entrySet()) {
            Efeito e = entry.getValue();
            float age = revealAge(e, gameTime, partialTick);
            if (age < 0 || !e.raridade.atLeast(Raridade.RARO)) continue;
            // Feixe que sobe e some aos poucos
            float fade = 1 - Mth.clamp((age - REVEAL_TICKS + 25) / 25F, 0, 1);
            int height = (int) (Mth.clamp(age / 6F, 0, 1) * (e.raridade.atLeast(Raridade.LENDARIO) ? 256 : 48));
            int alpha = (int) (fade * 255);
            if (alpha <= 8 || height <= 0) continue;
            BlockPos pos = entry.getKey();
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            int color = (alpha << 24) | (e.raridade.color & 0xFFFFFF);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1, gameTime,
                    0, height, color, 0.12F * fade + 0.03F, 0.2F * fade + 0.05F);
            pose.popPose();
        }
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
    }

    private EfeitosCliente() {
    }
}

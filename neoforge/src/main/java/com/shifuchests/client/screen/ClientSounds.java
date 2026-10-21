package com.shifuchests.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Sons tocados so para quem esta com a tela aberta, com agendamento em milissegundos. */
final class ClientSounds {
    private record Scheduled(long atMs, Runnable action) {
    }

    private final List<Scheduled> scheduled = new ArrayList<>();

    static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    /** Toca um som pelo id se ele existir (ex.: grito do Pokemon); devolve false se nao existir. */
    static boolean playIfExists(ResourceLocation id, float pitch, float volume) {
        if (Minecraft.getInstance().getSoundManager().getSoundEvent(id) == null) return false;
        play(SoundEvent.createVariableRangeEvent(id), pitch, volume);
        return true;
    }

    void schedule(long atMs, Runnable action) {
        scheduled.add(new Scheduled(atMs, action));
    }

    void update(long nowMs) {
        Iterator<Scheduled> it = scheduled.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (nowMs >= s.atMs) {
                due.add(s.action);
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    void clear() {
        scheduled.clear();
    }
}

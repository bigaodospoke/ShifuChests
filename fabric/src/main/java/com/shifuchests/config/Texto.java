package com.shifuchests.config;

import net.minecraft.network.chat.Component;

/** Textos da config aceitam cores no estilo "&amp;6Dourado &amp;lNegrito". */
public final class Texto {
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    public static Component parse(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && CODES.indexOf(text.charAt(i + 1)) >= 0) {
                out.append('§');
            } else {
                out.append(c);
            }
        }
        return Component.literal(out.toString());
    }

    private Texto() {
    }
}

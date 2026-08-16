package com.eia.reproductor.servicios;

import java.text.Normalizer;
import java.util.Locale;

/** Utilidades de texto para comparar sin que estorben las tildes ni las mayusculas. */
public final class Texto {
    private Texto() {
    }

    /** Deja el texto en minusculas y sin tildes, para que "angel" encuentre "Ángel". */
    public static String plano(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Como {@link #plano}, pero ademas sin espacios ni signos. */
    public static String soloLetrasYNumeros(String texto) {
        return plano(texto).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}

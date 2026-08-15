package com.eia.reproductor.servicios;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidades de texto para comparar sin que estorben las tildes ni las mayusculas.
 *
 * <p>Existe porque el mismo tratamiento hace falta en tres sitios —la busqueda de la biblioteca,
 * los filtros por campo y el emparejado de resultados de Spotify— y tener tres copias del mismo
 * {@code Normalizer} es justo lo que se acaba desincronizando: se arregla una y las otras dos
 * siguen mal.</p>
 */
public final class Texto {

    private Texto() {
    }

    /**
     * Deja el texto en minusculas y sin tildes, para que "angel" encuentre "Ángel".
     *
     * @param texto texto a normalizar; admite {@code null}
     * @return el texto comparable, o cadena vacia si venia {@code null}
     */
    public static String plano(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Como {@link #plano}, pero ademas sin espacios ni signos.
     *
     * <p>Es la version estricta, para decidir si dos titulos son <i>el mismo</i>. La otra sirve
     * para buscar por partes; esta no, porque borra los limites entre palabras.</p>
     *
     * @param texto texto a normalizar; admite {@code null}
     * @return solo letras y numeros, en minusculas
     */
    public static String soloLetrasYNumeros(String texto) {
        return plano(texto).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}

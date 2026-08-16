package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Por que campo se filtra la biblioteca. */
public enum FiltroDeCampo {
    /** Sin restringir: busca el texto en titulo, artista y album, como siempre. */
    TODO("TODO", null),
    TITULO("TÍTULO", Cancion::getTitulo),
    ARTISTA("ARTISTA", Cancion::getArtista),
    ALBUM("ÁLBUM", Cancion::getAlbum),
    GENERO("GÉNERO", Cancion::getGenero);

    private final String etiqueta;

    /** De donde sale el valor a comparar; {@code null} en {@link #TODO}, que mira varios campos. */
    private final Function<Cancion, String> lector;

    FiltroDeCampo(String etiqueta, Function<Cancion, String> lector) {
        this.etiqueta = etiqueta;
        this.lector = lector;
    }

    /** @return el nombre que se muestra en el desplegable */
    public String etiqueta() {
        return etiqueta;
    }

    /** Busca la opcion que corresponde a un texto del desplegable. */
    public static FiltroDeCampo porEtiqueta(String etiqueta) {
        for (FiltroDeCampo campo : values()) {
            if (campo.etiqueta.equals(etiqueta)) {
                return campo;
            }
        }
        return TODO;
    }

    /** @return las etiquetas de todas las opciones, en orden, para llenar el desplegable */
    public static List<String> etiquetas() {
        List<String> nombres = new ArrayList<>();
        for (FiltroDeCampo campo : values()) {
            nombres.add(campo.etiqueta);
        }
        return nombres;
    }

    /** Decide si una cancion pasa el filtro. */
    public boolean coincide(Cancion cancion, String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        String aguja = Texto.plano(texto);
        if (lector == null) {
            return Texto.plano(cancion.getTitulo()).contains(aguja)
                    || Texto.plano(cancion.getArtista()).contains(aguja)
                    || Texto.plano(cancion.getAlbum()).contains(aguja);
        }
        return Texto.plano(lector.apply(cancion)).contains(aguja);
    }

    /** Los valores distintos que tiene este campo en una lista de canciones. */
    public List<String> valoresEn(List<Cancion> canciones) {
        if (lector == null) {
            return List.of();
        }
        // Nada de TreeSet: el enunciado lo prohibe y no conviene que aparezca en el proyecto ni
        // para algo tan lejano a los modos como llenar un desplegable. Se deduplica con un HashSet
        Set<String> vistos = new HashSet<>();
        List<String> valores = new ArrayList<>();
        for (Cancion cancion : canciones) {
            String valor = lector.apply(cancion);
            if (valor != null && !valor.isBlank() && vistos.add(Texto.plano(valor))) {
                valores.add(valor);
            }
        }
        valores.sort(Comparator.comparing(Texto::plano));
        return List.copyOf(valores);
    }
}

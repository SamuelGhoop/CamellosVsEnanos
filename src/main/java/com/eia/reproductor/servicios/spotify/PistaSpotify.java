package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Un resultado de busqueda en el catalogo de Spotify.
 *
 * @param uri        identificador {@code spotify:track:...}
 * @param titulo     titulo segun Spotify
 * @param artista    interprete principal segun Spotify
 * @param duracionMs duracion segun Spotify
 */
public record PistaSpotify(String uri, String titulo, String artista, long duracionMs) {

    /**
     * Comparador que ignora tildes y mayusculas, igual que el que ordena la biblioteca.
     *
     * <p>Sin esto, "Se sabia" y "Sé sabía" no coincidirian y se descartarian resultados buenos.</p>
     */
    private static final Collator COLLATOR = crearCollator();

    private static Collator crearCollator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    /**
     * Lee un elemento del arreglo {@code tracks.items} de la busqueda.
     *
     * @param objeto elemento a leer
     * @return la pista leida
     */
    static PistaSpotify desdeJson(JsonObject objeto) {
        return new PistaSpotify(
                texto(objeto, "uri"),
                texto(objeto, "name"),
                primerArtista(objeto),
                objeto.has("duration_ms") ? objeto.get("duration_ms").getAsLong() : 0);
    }

    /**
     * Decide si este resultado corresponde de verdad a lo que se buscaba.
     *
     * <p><b>Por que hace falta.</b> La busqueda de Spotify siempre devuelve <i>algo</i>: si se pide
     * una cancion que no esta en el catalogo, contesta con lo que mas se le parezca. Guardar eso
     * sin comprobar llenaria la biblioteca de canciones equivocadas, y peor aun, sonarian sin que
     * el usuario entienda por que. Ante la duda, es mejor no guardar nada.</p>
     *
     * @param tituloBuscado  titulo de la cancion de la biblioteca
     * @param artistaBuscado interprete de la cancion de la biblioteca
     * @return {@code true} si el titulo y el interprete concuerdan
     */
    public boolean concuerdaCon(String tituloBuscado, String artistaBuscado) {
        return coincide(titulo, tituloBuscado) && coincide(artista, artistaBuscado);
    }

    /**
     * Compara dos textos con tolerancia.
     *
     * <p>Se acepta que uno contenga al otro porque Spotify agrega coletillas como
     * "- Remastered 2011" o "(feat. Alguien)" que la biblioteca no tiene.</p>
     */
    private static boolean coincide(String deSpotify, String deLaBiblioteca) {
        if (deSpotify == null || deLaBiblioteca == null) {
            return false;
        }
        String uno = normalizar(deSpotify);
        String otro = normalizar(deLaBiblioteca);
        if (uno.isEmpty() || otro.isEmpty()) {
            return false;
        }
        return COLLATOR.compare(uno, otro) == 0 || uno.contains(otro) || otro.contains(uno);
    }

    /**
     * Deja el texto en minusculas, sin tildes y sin signos, para comparar solo el contenido.
     *
     * <p>Quitar las tildes es imprescindible y no un adorno: la comparacion por contencion trabaja
     * con {@code String.contains}, que si distingue acentos. Sin esto, "Ángel" no encontraria a
     * "Angels" aunque el {@link Collator} las considere iguales — dos criterios distintos dentro de
     * la misma clase.</p>
     */
    private static String normalizar(String valor) {
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String primerArtista(JsonObject objeto) {
        if (!objeto.has("artists") || !objeto.get("artists").isJsonArray()) {
            return null;
        }
        JsonArray artistas = objeto.getAsJsonArray("artists");
        if (artistas.isEmpty()) {
            return null;
        }
        return texto(artistas.get(0).getAsJsonObject(), "name");
    }

    private static String texto(JsonObject objeto, String campo) {
        return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                ? objeto.get(campo).getAsString()
                : null;
    }
}

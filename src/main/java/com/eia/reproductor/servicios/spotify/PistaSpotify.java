package com.eia.reproductor.servicios.spotify;

import com.eia.reproductor.servicios.Texto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.Collator;
import java.util.Locale;

/** Un resultado de busqueda en el catalogo de Spotify. */
public record PistaSpotify(String uri, String titulo, String artista, long duracionMs) {
    /** Comparador que ignora tildes y mayusculas, igual que el que ordena la biblioteca. */
    private static final Collator COLLATOR = crearCollator();

    private static Collator crearCollator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    /** Lee un elemento del arreglo {@code tracks.items} de la busqueda. */
    static PistaSpotify desdeJson(JsonObject objeto) {
        return new PistaSpotify(
                texto(objeto, "uri"),
                texto(objeto, "name"),
                primerArtista(objeto),
                objeto.has("duration_ms") ? objeto.get("duration_ms").getAsLong() : 0);
    }

    /** Decide si este resultado corresponde de verdad a lo que se buscaba. */
    public boolean concuerdaCon(String tituloBuscado, String artistaBuscado) {
        return coincide(titulo, tituloBuscado) && coincide(artista, artistaBuscado);
    }

    /** Compara dos textos con tolerancia. */
    private static boolean coincide(String deSpotify, String deLaBiblioteca) {
        if (deSpotify == null || deLaBiblioteca == null) {
            return false;
        }
        // Se usa la version estricta —sin tildes ni signos— y no la de la busqueda: quitar las
        // tildes aqui es imprescindible y no un adorno, porque la comparacion por contencion
        String uno = Texto.soloLetrasYNumeros(deSpotify);
        String otro = Texto.soloLetrasYNumeros(deLaBiblioteca);
        if (uno.isEmpty() || otro.isEmpty()) {
            return false;
        }
        return COLLATOR.compare(uno, otro) == 0 || uno.contains(otro) || otro.contains(uno);
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

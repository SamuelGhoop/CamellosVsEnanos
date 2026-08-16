package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del emparejamiento entre la biblioteca y el catalogo de Spotify. */
class PistaSpotifyTest {
    private static PistaSpotify deSpotify(String titulo, String artista) {
        return new PistaSpotify("spotify:track:xyz", titulo, artista, 200_000);
    }

    @Test
    @DisplayName("Coincidencia exacta")
    void coincidenciaExacta() {
        assertTrue(deSpotify("Bohemian Rhapsody", "Queen")
                .concuerdaCon("Bohemian Rhapsody", "Queen"));
    }

    @Test
    @DisplayName("Ignora mayúsculas: 'TOTO' es 'Toto'")
    void ignoraMayusculas() {
        // Caso real: Spotify devuelve el nombre en mayúsculas y la biblioteca no.
        assertTrue(deSpotify("Africa", "TOTO").concuerdaCon("Africa", "Toto"));
        assertTrue(deSpotify("Stressed Out", "Twenty One Pilots")
                .concuerdaCon("Stressed Out", "twenty one pilots"));
    }

    @Test
    @DisplayName("Ignora tildes: 'Ángel' encuentra a 'Angels'")
    void ignoraTildes() {
        // Sin quitar las tildes, String.contains falla aunque el Collator las considere iguales.
        assertTrue(deSpotify("Angels", "Robbie Williams")
                .concuerdaCon("Ángel", "Robbie Williams"));
    }

    @Test
    @DisplayName("Tolera la coletilla de invitados que Spotify guarda aparte")
    void toleraElFeat() {
        // Caso real: en Spotify la pista se llama solo "Roger Federer".
        assertTrue(deSpotify("Roger Federer", "Luis7Lunes")
                .concuerdaCon("Roger Federer (feat. Dj Fazeta)", "Luis7Lunes"));
    }

    @Test
    @DisplayName("Tolera sufijos de remasterización")
    void toleraRemasterizaciones() {
        assertTrue(deSpotify("Enter Sandman - Remastered 2021", "Metallica")
                .concuerdaCon("Enter Sandman", "Metallica"));
    }

    @Test
    @DisplayName("Rechaza otra canción del mismo artista")
    void rechazaOtraCancionDelMismoArtista() {
        assertFalse(deSpotify("Under Pressure", "Queen")
                .concuerdaCon("Bohemian Rhapsody", "Queen"));
    }

    @Test
    @DisplayName("Rechaza la misma canción de otro intérprete")
    void rechazaVersionDeOtroArtista() {
        // Las versiones de otros abundan en el catálogo y son la trampa más común.
        assertFalse(deSpotify("Creep", "Karaoke Hits Band").concuerdaCon("Creep", "Radiohead"));
    }

    @Test
    @DisplayName("Rechaza resultados sin título o sin intérprete")
    void rechazaResultadosIncompletos() {
        assertFalse(deSpotify(null, "Queen").concuerdaCon("Bohemian Rhapsody", "Queen"));
        assertFalse(deSpotify("Bohemian Rhapsody", null).concuerdaCon("Bohemian Rhapsody", "Queen"));
        assertFalse(deSpotify("", "").concuerdaCon("Bohemian Rhapsody", "Queen"));
    }

    @Test
    @DisplayName("Rechaza cuando la búsqueda no tenía qué comparar")
    void rechazaSinDatosDeLaBiblioteca() {
        assertFalse(deSpotify("Africa", "Toto").concuerdaCon(null, "Toto"));
        assertFalse(deSpotify("Africa", "Toto").concuerdaCon("Africa", null));
    }

    @Test
    @DisplayName("Quita paréntesis y sufijos al preparar la búsqueda")
    void limpiaElTituloParaBuscar() {
        assertEquals("Roger Federer",
                ClienteWebApiSpotify.sinColetillas("Roger Federer (feat. Dj Fazeta)"));
        assertEquals("Middle", ClienteWebApiSpotify.sinColetillas("Middle (feat. Bipolar Sunshine)"));
        assertEquals("Enter Sandman",
                ClienteWebApiSpotify.sinColetillas("Enter Sandman - Remastered 2021"));
        assertEquals("Africa", ClienteWebApiSpotify.sinColetillas("Africa"));
    }

    @Test
    @DisplayName("Un título que es solo paréntesis se deja como está")
    void tituloEnteroEntreParentesis() {
        // Quitarlo dejaría la búsqueda sin nada que buscar.
        assertEquals("(Reprise)", ClienteWebApiSpotify.sinColetillas("(Reprise)"));
    }
}

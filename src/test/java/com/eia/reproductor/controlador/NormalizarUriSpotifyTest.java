package com.eia.reproductor.controlador;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pruebas de la traduccion del enlace de Spotify a URI. */
class NormalizarUriSpotifyTest {
    private static final String URI = "spotify:track:0GCaWksDZM7PV7mjdodhTT";

    @Test
    @DisplayName("Una URI ya correcta se deja igual")
    void uriYaCorrecta() {
        assertEquals(URI, AgregarCancionController.normalizarUriSpotify(URI));
    }

    @Test
    @DisplayName("Traduce el enlace del botón Compartir")
    void enlaceDeCompartir() {
        // Es exactamente lo que pega el usuario desde la aplicacion de Spotify.
        assertEquals(URI, AgregarCancionController.normalizarUriSpotify(
                "https://open.spotify.com/track/0GCaWksDZM7PV7mjdodhTT?si=a1b2c3d4"));
    }

    @Test
    @DisplayName("Traduce un enlace sin los parámetros de propina")
    void enlaceLimpio() {
        assertEquals(URI, AgregarCancionController.normalizarUriSpotify(
                "https://open.spotify.com/track/0GCaWksDZM7PV7mjdodhTT"));
    }

    @Test
    @DisplayName("Traduce enlaces con idioma en la ruta")
    void enlaceConIdioma() {
        assertEquals(URI, AgregarCancionController.normalizarUriSpotify(
                "https://open.spotify.com/intl-es/track/0GCaWksDZM7PV7mjdodhTT?si=xyz"));
    }

    @Test
    @DisplayName("Recorta los espacios de un pegado descuidado")
    void recortaEspacios() {
        assertEquals(URI, AgregarCancionController.normalizarUriSpotify("   " + URI + "  "));
    }

    @Test
    @DisplayName("Un campo vacío no guarda nada")
    void campoVacio() {
        assertNull(AgregarCancionController.normalizarUriSpotify(null));
        assertNull(AgregarCancionController.normalizarUriSpotify(""));
        assertNull(AgregarCancionController.normalizarUriSpotify("   "));
    }

    @Test
    @DisplayName("Un enlace de track sin identificador no guarda nada")
    void enlaceSinIdentificador() {
        assertNull(AgregarCancionController.normalizarUriSpotify(
                "https://open.spotify.com/track/?si=xyz"));
    }

    @Test
    @DisplayName("Lo que no se reconoce se guarda tal cual")
    void formatoDesconocidoSeGuardaIgual() {
        // Mejor guardarlo y que Spotify lo rechace, que descartar en silencio algo que el
        // usuario escribio a proposito.
        assertEquals("cualquier cosa",
                AgregarCancionController.normalizarUriSpotify("cualquier cosa"));
    }
}

package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lectura del estado del reproductor.
 *
 * <p>Los JSON de aqui son recortes de respuestas reales de {@code GET /v1/me/player} obtenidas
 * durante las pruebas con la cuenta.</p>
 */
class EstadoReproductorSpotifyTest {

    private static EstadoReproductorSpotify leer(String json) {
        JsonObject objeto = JsonParser.parseString(json).getAsJsonObject();
        return EstadoReproductorSpotify.desdeJson(objeto);
    }

    @Test
    @DisplayName("Lee una respuesta con pista en curso")
    void leeUnaRespuestaCompleta() {
        EstadoReproductorSpotify estado = leer("""
                {
                  "is_playing": true,
                  "progress_ms": 61496,
                  "repeat_state": "off",
                  "shuffle_state": false,
                  "item": {
                    "uri": "spotify:track:0GCaWksDZM7PV7mjdodhTT",
                    "name": "Con el corazón",
                    "duration_ms": 189239
                  }
                }
                """);

        assertTrue(estado.reproduciendo());
        assertEquals(61496, estado.posicionMs());
        assertEquals(189239, estado.duracionMs());
        assertEquals("spotify:track:0GCaWksDZM7PV7mjdodhTT", estado.uriPista());
        assertEquals("Con el corazón", estado.nombrePista());
    }

    @Test
    @DisplayName("Tolera que item venga nulo entre pista y pista")
    void toleraItemNulo() {
        EstadoReproductorSpotify estado =
                leer("{\"is_playing\":false,\"progress_ms\":0,\"item\":null}");

        assertNull(estado.uriPista());
        assertEquals(0, estado.duracionMs());
        assertFalse(estado.reproduciendo());
    }

    @Test
    @DisplayName("Tolera que falte item por completo")
    void toleraItemAusente() {
        EstadoReproductorSpotify estado = leer("{\"is_playing\":true,\"progress_ms\":1500}");

        assertNull(estado.uriPista());
        assertEquals(1500, estado.posicionMs());
    }

    @Test
    @DisplayName("Una respuesta vacía no rompe nada")
    void respuestaVacia() {
        EstadoReproductorSpotify estado = leer("{}");

        assertNull(estado.uriPista());
        assertEquals(0, estado.posicionMs());
        assertEquals(0, estado.duracionMs());
        assertFalse(estado.reproduciendo());
    }

    @Test
    @DisplayName("cercaDelFinal respeta el margen")
    void cercaDelFinalRespetaElMargen() {
        EstadoReproductorSpotify aFaltaDeDosSegundos = new EstadoReproductorSpotify(
                "spotify:track:x", 187_239, 189_239, false, "x");

        assertTrue(aFaltaDeDosSegundos.cercaDelFinal(3_000));
        assertFalse(aFaltaDeDosSegundos.cercaDelFinal(1_000));
    }

    @Test
    @DisplayName("Sin duración conocida nunca está cerca del final")
    void sinDuracionNoEstaCercaDelFinal() {
        EstadoReproductorSpotify sinDuracion =
                new EstadoReproductorSpotify("spotify:track:x", 5_000, 0, true, "x");

        // Con duracion cero, cualquier posicion pareceria el final y saltaria de cancion sola.
        assertFalse(sinDuracion.cercaDelFinal(3_000));
    }
}

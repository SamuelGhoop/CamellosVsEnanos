package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lectura de un dispositivo de Spotify Connect.
 *
 * <p>De esto depende el health check del paso 2: si el {@code id} se leyera mal, transferir la
 * reproduccion fallaria con un 404 dificil de rastrear.</p>
 */
class DispositivoSpotifyTest {

    private static JsonObject json(String texto) {
        return JsonParser.parseString(texto).getAsJsonObject();
    }

    @Test
    @DisplayName("Lee un dispositivo completo tal como lo manda la API")
    void leeUnDispositivoCompleto() {
        // Forma real de un elemento de GET /v1/me/player/devices.
        DispositivoSpotify dispositivo = DispositivoSpotify.desdeJson(json("""
                {
                  "id": "5fbb3ba6aa454b5534c4ba43a8c7e8e45a63ad0e",
                  "is_active": true,
                  "is_private_session": false,
                  "is_restricted": false,
                  "name": "Camellos vs Enanos",
                  "type": "Computer",
                  "volume_percent": 100
                }
                """));

        assertEquals("5fbb3ba6aa454b5534c4ba43a8c7e8e45a63ad0e", dispositivo.id());
        assertEquals("Camellos vs Enanos", dispositivo.nombre());
        assertEquals("Computer", dispositivo.tipo());
        assertTrue(dispositivo.activo());
    }

    @Test
    @DisplayName("Un dispositivo recién registrado todavía no está activo")
    void dispositivoInactivo() {
        DispositivoSpotify dispositivo = DispositivoSpotify.desdeJson(json("""
                {"id":"abc","name":"Camellos vs Enanos","type":"Computer","is_active":false}
                """));

        // Es el estado justo despues de arrancar librespot: existe, pero hay que transferirle
        // la reproduccion con PUT /v1/me/player.
        assertFalse(dispositivo.activo());
    }

    @Test
    @DisplayName("Sin is_active se asume inactivo")
    void sinIsActiveSeAsumeInactivo() {
        assertFalse(DispositivoSpotify.desdeJson(json("{\"id\":\"abc\"}")).activo());
    }

    @Test
    @DisplayName("Los campos ausentes quedan vacíos, no en null")
    void camposAusentesQuedanVacios() {
        DispositivoSpotify dispositivo = DispositivoSpotify.desdeJson(json("{}"));

        assertEquals("", dispositivo.id());
        assertEquals("", dispositivo.nombre());
        assertEquals("", dispositivo.tipo());
    }

    @Test
    @DisplayName("Un id nulo no rompe la lectura")
    void idNuloNoRompe() {
        // Spotify manda id nulo en dispositivos restringidos.
        DispositivoSpotify dispositivo = DispositivoSpotify.desdeJson(json("""
                {"id":null,"name":"Restringido","type":"Speaker"}
                """));

        assertEquals("", dispositivo.id());
        assertEquals("Restringido", dispositivo.nombre());
    }
}

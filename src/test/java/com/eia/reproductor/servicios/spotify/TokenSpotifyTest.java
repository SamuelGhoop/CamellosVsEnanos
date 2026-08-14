package com.eia.reproductor.servicios.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la vigencia y la renovacion del token.
 *
 * <p>El detalle importante es que el token se considera vencido <i>antes</i> de que realmente
 * venza: si se esperara al ultimo segundo, la primera llamada de cada hora fallaria con 401.</p>
 */
class TokenSpotifyTest {

    private static final long UNA_HORA_MS = 3_600_000;

    private static TokenSpotify queVenceEn(long milisegundos) {
        return new TokenSpotify("acceso", "refresco", System.currentTimeMillis() + milisegundos);
    }

    @Test
    @DisplayName("Un token recién emitido está vigente")
    void tokenNuevoVigente() {
        assertTrue(queVenceEn(UNA_HORA_MS).vigente());
    }

    @Test
    @DisplayName("Un token vencido no está vigente")
    void tokenVencido() {
        assertFalse(queVenceEn(-1000).vigente());
    }

    @Test
    @DisplayName("Se renueva antes de vencer, no después")
    void seRenuevaPorAdelantado() {
        // Faltan 30 segundos: todavia serviria, pero se declara no vigente a proposito para que
        // la renovacion ocurra antes del primer 401 y el usuario nunca vea un fallo.
        assertFalse(queVenceEn(30_000).vigente());
        assertTrue(queVenceEn(TokenSpotify.MARGEN_RENOVACION_MS + 10_000).vigente());
    }

    @Test
    @DisplayName("Sin access token no está vigente aunque la fecha diga que sí")
    void sinAccessTokenNoEsVigente() {
        assertFalse(new TokenSpotify(null, "refresco", Long.MAX_VALUE).vigente());
        assertFalse(new TokenSpotify("  ", "refresco", Long.MAX_VALUE).vigente());
    }

    @Test
    @DisplayName("Solo es renovable si hay refresh token")
    void renovableSoloConRefresh() {
        assertTrue(new TokenSpotify("acceso", "refresco", 0).puedeRenovarse());
        assertFalse(new TokenSpotify("acceso", null, 0).puedeRenovarse());
        assertFalse(new TokenSpotify("acceso", " ", 0).puedeRenovarse());
    }

    @Test
    @DisplayName("Al renovar conserva el refresh token si la respuesta no trae uno nuevo")
    void conservaElRefreshAnterior() {
        // Spotify no siempre devuelve refresh_token al renovar. Perderlo obligaria al usuario a
        // volver a autorizar desde el navegador, que es justo lo que hay que evitar.
        TokenSpotify renovado = TokenSpotify.desdeRespuesta("nuevo-acceso", null, 3600, "el-viejo");

        assertEquals("nuevo-acceso", renovado.accessToken());
        assertEquals("el-viejo", renovado.refreshToken());
        assertTrue(renovado.vigente());
    }

    @Test
    @DisplayName("Si la respuesta trae refresh token nuevo, se queda con ese")
    void prefiereElRefreshNuevo() {
        TokenSpotify renovado =
                TokenSpotify.desdeRespuesta("acceso", "el-nuevo", 3600, "el-viejo");

        assertEquals("el-nuevo", renovado.refreshToken());
    }

    @Test
    @DisplayName("La vigencia se calcula desde ahora, no desde época")
    void vigenciaRelativaAlMomento() {
        long antes = System.currentTimeMillis();

        TokenSpotify token = TokenSpotify.desdeRespuesta("acceso", "refresco", 3600, null);

        assertTrue(token.venceEnMillis() >= antes + UNA_HORA_MS);
        assertTrue(token.venceEnMillis() <= System.currentTimeMillis() + UNA_HORA_MS);
    }
}

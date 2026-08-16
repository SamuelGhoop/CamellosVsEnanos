package com.eia.reproductor.servicios.spotify;

/** Las credenciales vivas de la sesion de Spotify. */
public record TokenSpotify(String accessToken, String refreshToken, long venceEnMillis) {
    /** Margen con el que se considera que el token "ya casi vence". */
    public static final long MARGEN_RENOVACION_MS = 60_000;

    /** @return {@code true} si el token todavia sirve y no esta a punto de vencer */
    public boolean vigente() {
        return accessToken != null
                && !accessToken.isBlank()
                && System.currentTimeMillis() < venceEnMillis - MARGEN_RENOVACION_MS;
    }

    /** @return {@code true} si se puede pedir uno nuevo sin abrir el navegador */
    public boolean puedeRenovarse() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** Construye un token a partir de la respuesta del servidor de Spotify. */
    public static TokenSpotify desdeRespuesta(String accessToken, String refreshToken,
                                              long duracionSegundos, String refreshAnterior) {
        // Spotify no siempre devuelve un refresh token nuevo al renovar. Si no viene, hay que
        // conservar el anterior: perderlo obligaria al usuario a autorizar otra vez desde cero.
        String refreshVigente = (refreshToken == null || refreshToken.isBlank())
                ? refreshAnterior
                : refreshToken;
        return new TokenSpotify(
                accessToken,
                refreshVigente,
                System.currentTimeMillis() + duracionSegundos * 1000L);
    }
}

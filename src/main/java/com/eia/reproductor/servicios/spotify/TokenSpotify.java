package com.eia.reproductor.servicios.spotify;

/**
 * Las credenciales vivas de la sesion de Spotify.
 *
 * <p>El <i>access token</i> sirve para llamar a la API y dura una hora. El <i>refresh token</i> no
 * caduca solo y es lo que permite pedir uno nuevo sin volver a abrir el navegador: por eso hay que
 * conservarlo aunque el de acceso ya no valga.</p>
 *
 * @param accessToken   credencial para las llamadas a la API
 * @param refreshToken  credencial para renovar sin intervencion del usuario
 * @param venceEnMillis instante (epoch) en el que el access token deja de servir
 */
public record TokenSpotify(String accessToken, String refreshToken, long venceEnMillis) {

    /**
     * Margen con el que se considera que el token "ya casi vence".
     *
     * <p>Se renueva un minuto antes de la hora, no despues: si se esperara a que venciera, la
     * primera llamada de cada hora fallaria con 401 y habria que reintentarla. Renovar por
     * adelantado hace que ese fallo no ocurra nunca.</p>
     */
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

    /**
     * Construye un token a partir de la respuesta del servidor de Spotify.
     *
     * @param accessToken       token de acceso recibido
     * @param refreshToken      token de refresco recibido, puede ser {@code null}
     * @param duracionSegundos  vigencia declarada por el servidor
     * @param refreshAnterior   token de refresco previo, por si la respuesta no trae uno nuevo
     * @return el token listo para guardar
     */
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

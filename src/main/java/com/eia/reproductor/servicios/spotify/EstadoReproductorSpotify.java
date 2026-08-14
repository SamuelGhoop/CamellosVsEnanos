package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;

/**
 * Instantanea del reproductor de Spotify, tal como la devuelve {@code GET /v1/me/player}.
 *
 * <p>Es la unica fuente de posicion que hay: Spotify no empuja avisos, hay que preguntarle. Por eso
 * la barra de progreso se resincroniza con cada respuesta en vez de llevar un reloj propio.</p>
 *
 * @param uriPista      identificador de la pista en curso, o {@code null} si no hay ninguna
 * @param posicionMs    milisegundos transcurridos
 * @param duracionMs    duracion total de la pista
 * @param reproduciendo si esta sonando en este momento
 * @param nombrePista   titulo, util para diagnosticos
 */
public record EstadoReproductorSpotify(String uriPista, long posicionMs, long duracionMs,
                                       boolean reproduciendo, String nombrePista) {

    /**
     * Lee el estado del JSON de la API.
     *
     * <p>El campo {@code item} puede venir nulo aunque la respuesta sea correcta: pasa entre pista
     * y pista, y cuando la reproduccion es de un tipo que la API no describe. Se tolera devolviendo
     * un estado sin pista en vez de reventar.</p>
     *
     * @param objeto cuerpo de la respuesta
     * @return el estado leido
     */
    static EstadoReproductorSpotify desdeJson(JsonObject objeto) {
        boolean sonando = objeto.has("is_playing")
                && !objeto.get("is_playing").isJsonNull()
                && objeto.get("is_playing").getAsBoolean();
        long posicion = entero(objeto, "progress_ms");

        if (!objeto.has("item") || !objeto.get("item").isJsonObject()) {
            return new EstadoReproductorSpotify(null, posicion, 0, sonando, null);
        }
        JsonObject pista = objeto.getAsJsonObject("item");
        return new EstadoReproductorSpotify(
                texto(pista, "uri"),
                posicion,
                entero(pista, "duration_ms"),
                sonando,
                texto(pista, "name"));
    }

    /** @return {@code true} si la posicion esta dentro del margen final de la pista */
    public boolean cercaDelFinal(long margenMs) {
        return duracionMs > 0 && posicionMs >= duracionMs - margenMs;
    }

    private static long entero(JsonObject objeto, String campo) {
        return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                ? objeto.get(campo).getAsLong()
                : 0;
    }

    private static String texto(JsonObject objeto, String campo) {
        return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                ? objeto.get(campo).getAsString()
                : null;
    }
}

package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;

/** Instantanea del reproductor de Spotify, tal como la devuelve {@code GET /v1/me/player}. */
public record EstadoReproductorSpotify(String uriPista, long posicionMs, long duracionMs,
                                       boolean reproduciendo, String nombrePista) {
    /** Lee el estado del JSON de la API. */
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

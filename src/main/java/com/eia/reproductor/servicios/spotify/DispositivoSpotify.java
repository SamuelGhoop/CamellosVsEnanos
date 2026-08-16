package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;

/** Un dispositivo de Spotify Connect, tal como lo devuelve {@code GET /v1/me/player/devices}. */
public record DispositivoSpotify(String id, String nombre, boolean activo, String tipo) {
    /** Construye el dispositivo a partir del JSON de la API. */
    static DispositivoSpotify desdeJson(JsonObject objeto) {
        return new DispositivoSpotify(
                texto(objeto, "id"),
                texto(objeto, "name"),
                objeto.has("is_active") && objeto.get("is_active").getAsBoolean(),
                texto(objeto, "type"));
    }

    private static String texto(JsonObject objeto, String campo) {
        return objeto.has(campo) && !objeto.get(campo).isJsonNull()
                ? objeto.get(campo).getAsString()
                : "";
    }
}

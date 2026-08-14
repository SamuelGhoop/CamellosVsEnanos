package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;

/**
 * Un dispositivo de Spotify Connect, tal como lo devuelve {@code GET /v1/me/player/devices}.
 *
 * <p>Interesa por dos motivos: encontrar el {@code id} del librespot que lanzamos —sin ese id no se
 * le puede transferir la reproduccion— y saber si ya es el dispositivo activo.</p>
 *
 * @param id       identificador que pide la API para transferir la reproduccion
 * @param nombre   nombre visible; es el que se configura en {@code device.name}
 * @param activo   si la reproduccion ya esta en este dispositivo
 * @param tipo     categoria que reporta Spotify (Computer, Speaker, ...)
 */
public record DispositivoSpotify(String id, String nombre, boolean activo, String tipo) {

    /**
     * Construye el dispositivo a partir del JSON de la API.
     *
     * @param objeto elemento del arreglo {@code devices}
     * @return el dispositivo leido
     */
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

package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/** Convierte un objeto JSON en una {@link Cancion}. */
class DeserializadorCancion implements JsonDeserializer<Cancion> {
    @Override
    public Cancion deserialize(JsonElement elemento, Type tipo, JsonDeserializationContext contexto) {
        if (!elemento.isJsonObject()) {
            throw new JsonParseException("Se esperaba un objeto JSON por cada cancion.");
        }
        JsonObject objeto = elemento.getAsJsonObject();

        String titulo = texto(objeto, "titulo");
        if (titulo == null || titulo.isBlank()) {
            throw new JsonParseException("Hay una cancion sin titulo en el archivo.");
        }

        String id = texto(objeto, "id");
        Cancion cancion = (id == null || id.isBlank())
                ? new Cancion(titulo)          // sin id valido se genera uno nuevo
                : new Cancion(id, titulo);

        cancion.setArtista(texto(objeto, "artista"));
        cancion.setAlbum(texto(objeto, "album"));
        cancion.setGenero(texto(objeto, "genero"));
        cancion.setRutaArchivo(texto(objeto, "rutaArchivo"));
        cancion.setUriSpotify(texto(objeto, "uriSpotify"));
        cancion.setRutaPortada(texto(objeto, "rutaPortada"));
        cancion.setUrlPortadaRemota(texto(objeto, "urlPortadaRemota"));

        cancion.setDuracionSegundos(Math.max(0, entero(objeto, "duracionSegundos")));
        cancion.setAnio(Math.max(0, entero(objeto, "anio")));
        cancion.setVecesReproducida(Math.max(0, entero(objeto, "vecesReproducida")));
        cancion.setCalificacion(acotarCalificacion(entero(objeto, "calificacion")));
        cancion.setFavorita(booleano(objeto, "favorita"));

        return cancion;
    }

    /** Recorta la calificacion al rango valido en vez de descartar la cancion completa. */
    private static int acotarCalificacion(int valor) {
        return Math.max(Cancion.CALIFICACION_MIN, Math.min(Cancion.CALIFICACION_MAX, valor));
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull() || !valor.getAsJsonPrimitive().isString()) {
            return null;
        }
        return valor.getAsString();
    }

    private static int entero(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull() || !valor.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return valor.getAsInt();
    }

    private static boolean booleano(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull() || !valor.getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        return valor.getAsBoolean();
    }
}

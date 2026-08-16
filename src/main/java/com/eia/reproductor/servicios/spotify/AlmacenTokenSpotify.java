package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Guarda y recupera el token de Spotify en {@code config/token-spotify.json}. */
public class AlmacenTokenSpotify {
    /** Ubicacion del archivo, relativa a la carpeta desde donde se ejecuta la aplicacion. */
    public static final Path RUTA_POR_DEFECTO = Path.of("config", "token-spotify.json");

    private static final String CAMPO_ACCESO = "accessToken";
    private static final String CAMPO_REFRESCO = "refreshToken";
    private static final String CAMPO_VENCIMIENTO = "venceEnMillis";

    private final Path archivo;

    /** Crea un almacen sobre la ubicacion por defecto. */
    public AlmacenTokenSpotify() {
        this(RUTA_POR_DEFECTO);
    }

    /** Crea un almacen sobre una ruta concreta. */
    public AlmacenTokenSpotify(Path archivo) {
        this.archivo = archivo;
    }

    /** Lee el token guardado. */
    public Optional<TokenSpotify> cargar() {
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            JsonObject objeto = JsonParser.parseString(contenido).getAsJsonObject();

            String acceso = texto(objeto, CAMPO_ACCESO);
            String refresco = texto(objeto, CAMPO_REFRESCO);
            if (acceso == null && refresco == null) {
                return Optional.empty();
            }
            long vencimiento = objeto.has(CAMPO_VENCIMIENTO) && objeto.get(CAMPO_VENCIMIENTO).isJsonPrimitive()
                    ? objeto.get(CAMPO_VENCIMIENTO).getAsLong()
                    : 0;
            return Optional.of(new TokenSpotify(acceso, refresco, vencimiento));
        } catch (IOException | JsonSyntaxException | IllegalStateException archivoInservible) {
            return Optional.empty();
        }
    }

    /** Guarda el token, sobreescribiendo el anterior. */
    public boolean guardar(TokenSpotify token) {
        if (token == null) {
            return false;
        }
        JsonObject objeto = new JsonObject();
        objeto.addProperty(CAMPO_ACCESO, token.accessToken());
        objeto.addProperty(CAMPO_REFRESCO, token.refreshToken());
        objeto.addProperty(CAMPO_VENCIMIENTO, token.venceEnMillis());

        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try {
            Path carpeta = archivo.getParent();
            if (carpeta != null) {
                Files.createDirectories(carpeta);
            }
            Files.writeString(temporal, objeto.toString(), StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException excepcion) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException noSePudoLimpiar) {
                // Un temporal huerfano no justifica tumbar la aplicacion.
            }
            return false;
        }
    }

    /** Borra el token guardado. */
    public boolean borrar() {
        try {
            Files.deleteIfExists(archivo);
            return true;
        } catch (IOException excepcion) {
            return false;
        }
    }

    private static String texto(JsonObject objeto, String campo) {
        if (!objeto.has(campo) || objeto.get(campo).isJsonNull()) {
            return null;
        }
        String valor = objeto.get(campo).getAsString();
        return valor.isBlank() ? null : valor;
    }
}

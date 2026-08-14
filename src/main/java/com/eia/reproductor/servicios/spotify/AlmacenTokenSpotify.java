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

/**
 * Guarda y recupera el token de Spotify en {@code config/token-spotify.json}.
 *
 * <p>Persistirlo es lo que evita tener que abrir el navegador en cada arranque: el token de
 * refresco no caduca solo, asi que basta autorizar una vez.</p>
 *
 * <p>El archivo esta en {@code .gitignore}. Es el mas sensible del proyecto — con el se puede
 * controlar la cuenta de Spotify de su dueno — y por eso nunca se versiona.</p>
 *
 * <p>El JSON se arma y se lee a mano en vez de dejarselo a la reflexion de Gson. Son tres campos, y
 * asi un archivo corrupto o editado a mano se detecta aqui en vez de producir un objeto a medio
 * construir mas adelante. Es el mismo criterio que sigue {@code DeserializadorCancion}.</p>
 */
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

    /**
     * Crea un almacen sobre una ruta concreta.
     *
     * @param archivo destino del token; las pruebas usan una carpeta temporal
     */
    public AlmacenTokenSpotify(Path archivo) {
        this.archivo = archivo;
    }

    /**
     * Lee el token guardado.
     *
     * <p>Un archivo ausente, ilegible o corrupto se trata igual que "no hay token": se devuelve
     * vacio y el usuario tendra que autorizar de nuevo. No se lanza excepcion, porque no poder
     * leerlo no es un error de la aplicacion sino un estado normal del primer arranque.</p>
     *
     * @return el token guardado, o vacio si no hay uno utilizable
     */
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

    /**
     * Guarda el token, sobreescribiendo el anterior.
     *
     * <p>La escritura es atomica: se escribe en un temporal y se mueve encima. Si el proceso muere
     * a mitad, el archivo viejo queda intacto en vez de quedar un token truncado que obligaria a
     * autorizar otra vez.</p>
     *
     * @param token token a persistir
     * @return {@code true} si se pudo guardar
     */
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

    /**
     * Borra el token guardado.
     *
     * <p>Se usa cuando Spotify lo rechaza: conservar una credencial que el servidor ya no acepta
     * solo consigue que cada arranque intente renovarla y falle.</p>
     *
     * @return {@code true} si el archivo ya no existe al terminar
     */
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

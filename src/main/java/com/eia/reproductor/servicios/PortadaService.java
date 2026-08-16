package com.eia.reproductor.servicios;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Descarga y guarda las caratulas de los albumes. */
public class PortadaService {
    /** Carpeta donde viven las caratulas, relativa al proyecto. */
    public static final Path CARPETA_POR_DEFECTO = Path.of("data", "covers");

    /** Tiempo maximo de espera de la descarga. */
    public static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(10);

    /** Tamanio minimo para considerar que lo descargado es una imagen y no una pagina de error. */
    private static final long TAMANIO_MINIMO_VALIDO = 512;

    private static final String EXTENSION = ".jpg";

    private final Path carpeta;
    private final HttpClient cliente;
    private String ultimoAviso;

    /** Crea el servicio sobre {@link #CARPETA_POR_DEFECTO}. */
    public PortadaService() {
        this(CARPETA_POR_DEFECTO);
    }

    /** Crea el servicio sobre una carpeta concreta. */
    public PortadaService(Path carpeta) {
        this.carpeta = Objects.requireNonNull(carpeta, "La carpeta no puede ser nula.");
        this.cliente = HttpClient.newBuilder()
                .connectTimeout(TIEMPO_MAXIMO)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Devuelve la caratula de una cancion, descargandola solo si no estaba ya guardada. */
    public Optional<Path> obtener(String idCancion, String urlRemota) {
        ultimoAviso = null;
        Optional<Path> yaGuardada = cacheada(idCancion);
        if (yaGuardada.isPresent()) {
            return yaGuardada;
        }
        if (urlRemota == null || urlRemota.isBlank()) {
            return Optional.empty();
        }
        return descargar(idCancion, urlRemota);
    }

    /** Consulta si la caratula ya esta en disco, sin tocar la red. */
    public Optional<Path> cacheada(String idCancion) {
        if (idCancion == null || idCancion.isBlank()) {
            return Optional.empty();
        }
        Path archivo = rutaDe(idCancion);
        return Files.isRegularFile(archivo) ? Optional.of(archivo) : Optional.empty();
    }

    /** Borra la caratula guardada de una cancion, si la hay. */
    public boolean borrar(String idCancion) {
        try {
            return idCancion != null && Files.deleteIfExists(rutaDe(idCancion));
        } catch (IOException excepcion) {
            return false;
        }
    }

    /** @return la ruta donde iria la caratula de esa cancion, exista o no */
    public Path rutaDe(String idCancion) {
        return carpeta.resolve(idCancion + EXTENSION);
    }

    /** @return el motivo del ultimo fallo de descarga, si lo hubo */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    // --- Apoyo interno ---

    private Optional<Path> descargar(String idCancion, String urlRemota) {
        Path destino = rutaDe(idCancion);
        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.createDirectories(carpeta);
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(urlRemota))
                    .timeout(TIEMPO_MAXIMO)
                    .GET()
                    .build();
            HttpResponse<InputStream> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofInputStream());

            if (respuesta.statusCode() / 100 != 2) {
                ultimoAviso = "La carátula no está disponible (HTTP " + respuesta.statusCode() + ").";
                return Optional.empty();
            }

            try (InputStream flujo = respuesta.body()) {
                Files.copy(flujo, temporal, StandardCopyOption.REPLACE_EXISTING);
            }

            // Un 404 disfrazado de pagina HTML pesa muy poco: si lo guardaramos, la interfaz
            // intentaria pintar como imagen algo que no lo es.
            if (Files.size(temporal) < TAMANIO_MINIMO_VALIDO) {
                Files.deleteIfExists(temporal);
                ultimoAviso = "Lo que devolvió el servidor no parece una imagen.";
                return Optional.empty();
            }

            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(destino);
        } catch (IOException | InterruptedException | IllegalArgumentException excepcion) {
            if (excepcion instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            ultimoAviso = "No se pudo descargar la carátula: " + excepcion.getMessage();
            borrarSilenciosamente(temporal);
            return Optional.empty();
        }
    }

    private static void borrarSilenciosamente(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignorada) {
            // Un temporal huerfano no rompe nada.
        }
    }
}

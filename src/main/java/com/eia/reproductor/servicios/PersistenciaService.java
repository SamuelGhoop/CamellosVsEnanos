package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Guarda y recupera la biblioteca en {@code data/biblioteca.json}.
 *
 * <p>La ruta es siempre relativa a la carpeta del proyecto, nunca absoluta, para que el proyecto
 * funcione igual en el computador de cualquier integrante del grupo.</p>
 *
 * <p><b>Tolerancia a fallos.</b> Ninguna operacion de disco puede tumbar la aplicacion:</p>
 * <ul>
 *   <li>Si el archivo no existe todavia, se arranca con una biblioteca vacia.</li>
 *   <li>Si el archivo esta corrupto, se conserva una copia en {@code biblioteca.json.bak} antes de
 *       arrancar vacio, para no destruir el trabajo del usuario, y se deja un aviso para mostrar
 *       en pantalla.</li>
 *   <li>Al guardar se escribe primero un archivo temporal y solo despues se reemplaza el
 *       definitivo. Asi, si el proceso muere a mitad de la escritura, el archivo bueno sigue
 *       intacto en vez de quedar a medias.</li>
 * </ul>
 */
public class PersistenciaService {

    /** Ubicacion por defecto de la biblioteca, relativa a la carpeta del proyecto. */
    public static final Path RUTA_POR_DEFECTO = Path.of("data", "biblioteca.json");

    private static final String SUFIJO_RESPALDO = ".bak";
    private static final String SUFIJO_TEMPORAL = ".tmp";

    private static final Type TIPO_LISTA_CANCIONES = new TypeToken<List<Cancion>>() { }.getType();

    private final Path archivo;
    private final Gson gson;
    private String ultimoAviso;

    /** Crea el servicio apuntando a {@link #RUTA_POR_DEFECTO}. */
    public PersistenciaService() {
        this(RUTA_POR_DEFECTO);
    }

    /**
     * Crea el servicio apuntando a un archivo concreto.
     *
     * <p>El constructor con ruta explicita permite que las pruebas trabajen sobre una carpeta
     * temporal sin tocar los datos reales.</p>
     *
     * @param archivo ruta del archivo JSON de la biblioteca
     */
    public PersistenciaService(Path archivo) {
        this.archivo = Objects.requireNonNull(archivo, "La ruta del archivo no puede ser nula.");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Cancion.class, new DeserializadorCancion())
                .create();
    }

    /**
     * Lee la biblioteca desde disco.
     *
     * @return las canciones guardadas; lista vacia si no hay archivo o si estaba corrupto
     */
    public List<Cancion> cargar() {
        ultimoAviso = null;

        if (!Files.exists(archivo)) {
            return new ArrayList<>();
        }

        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            List<Cancion> canciones = gson.fromJson(lector, TIPO_LISTA_CANCIONES);
            if (canciones == null) {
                // El archivo existia pero estaba vacio o contenia solo "null".
                return new ArrayList<>();
            }
            // Gson puede dejar huecos nulos si el JSON traia elementos "null" dentro del arreglo.
            canciones.removeIf(Objects::isNull);
            return canciones;
        } catch (JsonParseException excepcion) {
            return recuperarDeArchivoCorrupto("El archivo tiene un formato invalido", excepcion);
        } catch (IOException excepcion) {
            ultimoAviso = "No se pudo leer " + archivo + ": " + excepcion.getMessage()
                    + ". Se arranca con la biblioteca vacia.";
            return new ArrayList<>();
        }
    }

    /**
     * Escribe la biblioteca en disco.
     *
     * @param canciones canciones a guardar
     * @return {@code true} si se guardo correctamente
     */
    public boolean guardar(Iterable<Cancion> canciones) {
        Objects.requireNonNull(canciones, "La coleccion de canciones no puede ser nula.");
        ultimoAviso = null;

        List<Cancion> aGuardar = new ArrayList<>();
        for (Cancion cancion : canciones) {
            aGuardar.add(cancion);
        }

        Path temporal = archivo.resolveSibling(archivo.getFileName() + SUFIJO_TEMPORAL);
        try {
            crearCarpetaContenedora();
            try (Writer escritor = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
                gson.toJson(aGuardar, TIPO_LISTA_CANCIONES, escritor);
            }
            reemplazar(temporal, archivo);
            return true;
        } catch (IOException excepcion) {
            ultimoAviso = "No se pudo guardar la biblioteca en " + archivo + ": "
                    + excepcion.getMessage();
            borrarSilenciosamente(temporal);
            return false;
        }
    }

    /**
     * @return la ruta del archivo que gestiona este servicio
     */
    public Path getArchivo() {
        return archivo;
    }

    /**
     * Indica si el archivo de la biblioteca ya existe en disco.
     *
     * <p>Sirve para distinguir el primer arranque de la aplicacion de un arranque con una
     * biblioteca que el usuario vacio a proposito.</p>
     *
     * @return {@code true} si el archivo existe
     */
    public boolean existeArchivo() {
        return Files.exists(archivo);
    }

    /**
     * @return el aviso de la ultima operacion, si hubo algun problema que valga la pena mostrar
     */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private List<Cancion> recuperarDeArchivoCorrupto(String motivo, Exception causa) {
        Path respaldo = archivo.resolveSibling(archivo.getFileName() + SUFIJO_RESPALDO);
        String detalleRespaldo;
        try {
            Files.copy(archivo, respaldo, StandardCopyOption.REPLACE_EXISTING);
            detalleRespaldo = "Se guardo una copia en " + respaldo.getFileName() + ".";
        } catch (IOException fallaRespaldo) {
            detalleRespaldo = "Ademas no se pudo crear la copia de respaldo ("
                    + fallaRespaldo.getMessage() + ").";
        }
        ultimoAviso = motivo + ": " + causa.getMessage() + ". " + detalleRespaldo
                + " Se arranca con la biblioteca vacia.";
        return new ArrayList<>();
    }

    private void crearCarpetaContenedora() throws IOException {
        Path carpeta = archivo.getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
    }

    private static void reemplazar(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (UnsupportedOperationException | IOException sinMovimientoAtomico) {
            // No todos los sistemas de archivos admiten el movimiento atomico.
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void borrarSilenciosamente(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignorada) {
            // Un temporal que sobrevive no rompe nada; no vale la pena molestar al usuario.
        }
    }
}

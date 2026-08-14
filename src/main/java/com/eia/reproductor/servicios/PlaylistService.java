package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.Playlist;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Administra las listas de reproduccion hechas por el usuario.
 *
 * <p>Guarda en {@code data/playlists.json}, aparte de la biblioteca: son dos cosas con vidas
 * distintas, y mezclarlas obligaria a reescribir el archivo entero de canciones cada vez que
 * alguien renombra una lista.</p>
 *
 * <p>El JSON se lee y se escribe a mano, sin dejarselo a la reflexion de Gson, por el mismo motivo
 * que en {@code DeserializadorCancion}: un archivo editado a mano o a medio escribir se detecta
 * aqui en vez de producir objetos invalidos mas adelante.</p>
 */
public class PlaylistService {

    /** Ubicacion por defecto, relativa a la carpeta desde donde se ejecuta la aplicacion. */
    public static final Path RUTA_POR_DEFECTO = Path.of("data", "playlists.json");

    private final Path archivo;
    private final List<Playlist> listas = new ArrayList<>();

    private String ultimoAviso;

    /** Crea el servicio sobre la ubicacion por defecto. */
    public PlaylistService() {
        this(RUTA_POR_DEFECTO);
    }

    /**
     * Crea el servicio sobre una ruta concreta.
     *
     * @param archivo destino del JSON; las pruebas usan una carpeta temporal
     */
    public PlaylistService(Path archivo) {
        this.archivo = archivo;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** @return todas las listas, en el orden en que se crearon */
    public List<Playlist> todas() {
        return List.copyOf(listas);
    }

    /** @return cuantas listas hay */
    public int tamanio() {
        return listas.size();
    }

    /**
     * Busca una lista por su nombre, sin distinguir mayusculas ni espacios sobrantes.
     *
     * @param nombre nombre a buscar
     * @return la lista, o vacio
     */
    public Optional<Playlist> porNombre(String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        String buscado = nombre.trim().toLowerCase(Locale.ROOT);
        return listas.stream()
                .filter(lista -> lista.getNombre().toLowerCase(Locale.ROOT).equals(buscado))
                .findFirst();
    }

    /** @return el motivo del ultimo fallo, si lo hubo */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    // ------------------------------------------------------------------
    // Altas, bajas y cambios
    // ------------------------------------------------------------------

    /**
     * Crea una lista nueva.
     *
     * <p>Se rechazan los nombres repetidos: dos listas con el mismo nombre en el selector serian
     * indistinguibles para el usuario, aunque por dentro tengan identificadores distintos.</p>
     *
     * @param nombre nombre visible
     * @return la lista creada, o vacio si el nombre no vale o ya existe
     */
    public Optional<Playlist> crear(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            ultimoAviso = "La lista necesita un nombre.";
            return Optional.empty();
        }
        if (porNombre(nombre).isPresent()) {
            ultimoAviso = "Ya existe una lista llamada \"" + nombre.trim() + "\".";
            return Optional.empty();
        }
        try {
            Playlist nueva = new Playlist(nombre);
            listas.add(nueva);
            guardar();
            ultimoAviso = null;
            return Optional.of(nueva);
        } catch (IllegalArgumentException nombreInvalido) {
            ultimoAviso = nombreInvalido.getMessage();
            return Optional.empty();
        }
    }

    /**
     * Cambia el nombre de una lista.
     *
     * @param lista  lista a renombrar
     * @param nombre nombre nuevo
     * @return {@code true} si se pudo
     */
    public boolean renombrar(Playlist lista, String nombre) {
        if (lista == null || !listas.contains(lista)) {
            return false;
        }
        Optional<Playlist> conEseNombre = porNombre(nombre);
        if (conEseNombre.isPresent() && !conEseNombre.get().equals(lista)) {
            ultimoAviso = "Ya existe una lista llamada \"" + nombre.trim() + "\".";
            return false;
        }
        try {
            lista.setNombre(nombre);
            guardar();
            ultimoAviso = null;
            return true;
        } catch (IllegalArgumentException nombreInvalido) {
            ultimoAviso = nombreInvalido.getMessage();
            return false;
        }
    }

    /**
     * Borra una lista.
     *
     * <p>Solo desaparece la lista: las canciones siguen en la biblioteca, porque la lista nunca
     * fue su dueña sino una forma de agruparlas.</p>
     *
     * @param lista lista a borrar
     * @return {@code true} si existia y se borro
     */
    public boolean eliminar(Playlist lista) {
        if (lista == null || !listas.remove(lista)) {
            return false;
        }
        guardar();
        return true;
    }

    /**
     * Agrega una cancion al final de una lista.
     *
     * @param lista   lista destino
     * @param cancion cancion a agregar
     * @return {@code true} si se agrego; {@code false} si ya estaba
     */
    public boolean agregarCancion(Playlist lista, Cancion cancion) {
        if (lista == null || cancion == null || !listas.contains(lista)) {
            return false;
        }
        if (!lista.agregar(cancion.getId())) {
            ultimoAviso = "\"" + cancion.getTitulo() + "\" ya está en " + lista.getNombre() + ".";
            return false;
        }
        guardar();
        ultimoAviso = null;
        return true;
    }

    /**
     * Quita una cancion de una lista.
     *
     * @param lista   lista de la que quitarla
     * @param cancion cancion a quitar
     * @return {@code true} si estaba y se quito
     */
    public boolean quitarCancion(Playlist lista, Cancion cancion) {
        if (lista == null || cancion == null || !lista.quitar(cancion.getId())) {
            return false;
        }
        guardar();
        return true;
    }

    /**
     * Saca de todas las listas las canciones que ya no existen en la biblioteca.
     *
     * <p>Se llama despues de borrar canciones. Sin esto, los identificadores huerfanos se quedan
     * para siempre en el archivo y los contadores del selector mienten.</p>
     *
     * @param biblioteca de donde se sacan los identificadores que siguen siendo validos
     * @return cuantas referencias se descartaron
     */
    public int limpiarHuerfanas(BibliotecaService biblioteca) {
        Set<String> vigentes = new HashSet<>();
        for (Cancion cancion : biblioteca.todas()) {
            vigentes.add(cancion.getId());
        }
        int descartadas = 0;
        for (Playlist lista : listas) {
            descartadas += lista.descartarHuerfanas(vigentes);
        }
        if (descartadas > 0) {
            guardar();
        }
        return descartadas;
    }

    // ------------------------------------------------------------------
    // Persistencia
    // ------------------------------------------------------------------

    /**
     * Carga las listas del disco, reemplazando las que hubiera en memoria.
     *
     * @return cuantas listas se cargaron
     */
    public int cargarDesdeDisco() {
        listas.clear();
        if (!Files.isRegularFile(archivo)) {
            return 0;
        }
        try {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            JsonArray arreglo = JsonParser.parseString(contenido).getAsJsonArray();
            for (JsonElement elemento : arreglo) {
                leerUna(elemento).ifPresent(listas::add);
            }
            ultimoAviso = null;
        } catch (IOException | RuntimeException archivoInservible) {
            respaldarArchivoCorrupto();
            ultimoAviso = "El archivo de listas estaba dañado; se guardó una copia y se empezó "
                    + "de cero.";
        }
        return listas.size();
    }

    /**
     * Lee una lista del JSON, descartando lo que no tenga sentido.
     *
     * @param elemento elemento del arreglo
     * @return la lista, o vacio si el elemento no vale
     */
    private static Optional<Playlist> leerUna(JsonElement elemento) {
        if (!elemento.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject objeto = elemento.getAsJsonObject();
        String nombre = texto(objeto, "nombre");
        if (nombre == null) {
            // Una lista sin nombre no se puede ni mostrar ni elegir.
            return Optional.empty();
        }
        try {
            String id = texto(objeto, "id");
            Playlist lista = id == null ? new Playlist(nombre) : new Playlist(id, nombre);
            if (objeto.has("canciones") && objeto.get("canciones").isJsonArray()) {
                for (JsonElement idCancion : objeto.getAsJsonArray("canciones")) {
                    if (idCancion.isJsonPrimitive()) {
                        lista.agregar(idCancion.getAsString());
                    }
                }
            }
            return Optional.of(lista);
        } catch (IllegalArgumentException datosInvalidos) {
            return Optional.empty();
        }
    }

    /**
     * Escribe las listas en el disco.
     *
     * <p>Con temporal y movimiento, como la biblioteca: si el proceso muere a mitad, el archivo
     * viejo queda intacto en vez de quedar uno truncado.</p>
     *
     * @return {@code true} si se pudo guardar
     */
    public boolean guardar() {
        JsonArray arreglo = new JsonArray();
        for (Playlist lista : listas) {
            JsonObject objeto = new JsonObject();
            objeto.addProperty("id", lista.getId());
            objeto.addProperty("nombre", lista.getNombre());
            JsonArray canciones = new JsonArray();
            lista.idsCanciones().forEach(canciones::add);
            objeto.add("canciones", canciones);
            arreglo.add(objeto);
        }

        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try {
            Path carpeta = archivo.getParent();
            if (carpeta != null) {
                Files.createDirectories(carpeta);
            }
            Files.writeString(temporal, arreglo.toString(), StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            ultimoAviso = null;
            return true;
        } catch (IOException fallo) {
            ultimoAviso = "No se pudieron guardar las listas: " + fallo.getMessage();
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException noSePudoLimpiar) {
                // Un temporal huerfano no justifica tumbar la aplicacion.
            }
            return false;
        }
    }

    /** Conserva el archivo ilegible en vez de pisarlo, por si se puede recuperar a mano. */
    private void respaldarArchivoCorrupto() {
        try {
            if (Files.isRegularFile(archivo)) {
                Files.move(archivo, archivo.resolveSibling(archivo.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException noSePudoRespaldar) {
            // Si ni eso se puede, se sigue con las listas vacias.
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

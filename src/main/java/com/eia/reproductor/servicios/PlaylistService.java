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

/** Administra las listas de reproduccion hechas por el usuario. */
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

    /** Crea el servicio sobre una ruta concreta. */
    public PlaylistService(Path archivo) {
        this.archivo = archivo;
    }

    // --- Consultas ---

    /** @return todas las listas, en el orden en que se crearon */
    public List<Playlist> todas() {
        return List.copyOf(listas);
    }

    /** @return cuantas listas hay */
    public int tamanio() {
        return listas.size();
    }

    /** Busca una lista por su nombre, sin distinguir mayusculas ni espacios sobrantes. */
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

    // --- Altas, bajas y cambios ---

    /** Crea una lista nueva. */
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

    /** Cambia el nombre de una lista. */
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

    /** Borra una lista. */
    public boolean eliminar(Playlist lista) {
        if (lista == null || !listas.remove(lista)) {
            return false;
        }
        guardar();
        return true;
    }

    /** Agrega una cancion al final de una lista. */
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

    /** Quita una cancion de una lista. */
    public boolean quitarCancion(Playlist lista, Cancion cancion) {
        if (lista == null || cancion == null || !lista.quitar(cancion.getId())) {
            return false;
        }
        guardar();
        return true;
    }

    /** Saca de todas las listas las canciones que ya no existen en la biblioteca. */
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

    // --- Persistencia ---

    /** Carga las listas del disco, reemplazando las que hubiera en memoria. */
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

    /** Lee una lista del JSON, descartando lo que no tenga sentido. */
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

    /** Escribe las listas en el disco. */
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

package com.eia.reproductor.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una lista de canciones armada por el usuario.
 *
 * <p><b>Guarda identificadores, no canciones.</b> Si guardara los objetos, la misma cancion
 * existiria dos veces —una en la biblioteca y otra en cada lista— y editar el titulo en un sitio no
 * lo cambiaria en el otro. Con identificadores hay una sola cancion de verdad, la de la biblioteca,
 * y las listas solo apuntan a ella.</p>
 *
 * <p>El orden de insercion se respeta: es el que el usuario ve y el que recibe el modo de orden de
 * llegada. Una cancion no se puede repetir dentro de la misma lista.</p>
 */
public class Playlist {

    /** Largo maximo del nombre, para que quepa en el selector sin romper la maquetacion. */
    public static final int LARGO_MAXIMO_NOMBRE = 40;

    private final String id;
    private String nombre;
    private final List<String> idsCanciones;

    /**
     * Crea una lista nueva y vacia.
     *
     * @param nombre nombre visible; no puede estar en blanco
     * @throws IllegalArgumentException si el nombre esta vacio o es demasiado largo
     */
    public Playlist(String nombre) {
        this(UUID.randomUUID().toString(), nombre);
    }

    /**
     * Reconstruye una lista conservando su identificador.
     *
     * <p>Se usa al cargar {@code data/playlists.json}: el identificador tiene que sobrevivir entre
     * ejecuciones para que renombrar una lista no la convierta en otra.</p>
     *
     * @param id     identificador previamente asignado
     * @param nombre nombre visible
     * @throws IllegalArgumentException si el id o el nombre no valen
     */
    public Playlist(String id, String nombre) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id de la lista no puede estar vacio.");
        }
        this.id = id;
        this.idsCanciones = new ArrayList<>();
        setNombre(nombre);
    }

    /** @return el identificador unico e inmutable de la lista */
    public String getId() {
        return id;
    }

    /** @return el nombre visible */
    public String getNombre() {
        return nombre;
    }

    /**
     * Cambia el nombre de la lista.
     *
     * @param nombre nuevo nombre
     * @throws IllegalArgumentException si esta en blanco o pasa de {@link #LARGO_MAXIMO_NOMBRE}
     */
    public final void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("La lista necesita un nombre.");
        }
        String recortado = nombre.trim();
        if (recortado.length() > LARGO_MAXIMO_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre no puede pasar de " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }
        this.nombre = recortado;
    }

    /**
     * Agrega una cancion al final.
     *
     * @param idCancion identificador de la cancion
     * @return {@code true} si se agrego; {@code false} si ya estaba o el id no vale
     */
    public boolean agregar(String idCancion) {
        if (idCancion == null || idCancion.isBlank() || idsCanciones.contains(idCancion)) {
            return false;
        }
        return idsCanciones.add(idCancion);
    }

    /**
     * Quita una cancion de la lista.
     *
     * @param idCancion identificador de la cancion
     * @return {@code true} si estaba y se quito
     */
    public boolean quitar(String idCancion) {
        return idsCanciones.remove(idCancion);
    }

    /**
     * Indica si una cancion pertenece a la lista.
     *
     * @param idCancion identificador de la cancion
     * @return {@code true} si esta
     */
    public boolean contiene(String idCancion) {
        return idsCanciones.contains(idCancion);
    }

    /** @return los identificadores en el orden en que se agregaron */
    public List<String> idsCanciones() {
        return List.copyOf(idsCanciones);
    }

    /** @return cuantas canciones tiene, contando las que ya no existan en la biblioteca */
    public int tamanio() {
        return idsCanciones.size();
    }

    /**
     * Olvida las canciones que ya no estan en la biblioteca.
     *
     * <p>Al borrar una cancion, su identificador se queda huerfano dentro de las listas. Limpiarlo
     * evita que el archivo crezca con basura y que el contador muestre mas canciones de las que
     * realmente van a sonar.</p>
     *
     * @param idsQueSiguenExistiendo identificadores validos de la biblioteca
     * @return cuantas referencias se descartaron
     */
    public int descartarHuerfanas(java.util.Set<String> idsQueSiguenExistiendo) {
        int antes = idsCanciones.size();
        idsCanciones.removeIf(id -> !idsQueSiguenExistiendo.contains(id));
        return antes - idsCanciones.size();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Playlist lista && id.equals(lista.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return nombre + " (" + idsCanciones.size() + ")";
    }
}

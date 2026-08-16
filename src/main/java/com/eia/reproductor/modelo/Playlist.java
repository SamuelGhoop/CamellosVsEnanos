package com.eia.reproductor.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Una lista de canciones armada por el usuario. */
public class Playlist {
    /** Largo maximo del nombre, para que quepa en el selector sin romper la maquetacion. */
    public static final int LARGO_MAXIMO_NOMBRE = 40;

    private final String id;
    private String nombre;
    private final List<String> idsCanciones;

    /** Crea una lista nueva y vacia. */
    public Playlist(String nombre) {
        this(UUID.randomUUID().toString(), nombre);
    }

    /** Reconstruye una lista conservando su identificador. */
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

    /** Cambia el nombre de la lista. */
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

    /** Agrega una cancion al final. */
    public boolean agregar(String idCancion) {
        if (idCancion == null || idCancion.isBlank() || idsCanciones.contains(idCancion)) {
            return false;
        }
        return idsCanciones.add(idCancion);
    }

    /** Quita una cancion de la lista. */
    public boolean quitar(String idCancion) {
        return idsCanciones.remove(idCancion);
    }

    /** Indica si una cancion pertenece a la lista. */
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

    /** Olvida las canciones que ya no estan en la biblioteca. */
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

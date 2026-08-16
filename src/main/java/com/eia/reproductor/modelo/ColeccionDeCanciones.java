package com.eia.reproductor.modelo;

import java.util.List;

/** Un conjunto de canciones que se puede poner a sonar. */
public interface ColeccionDeCanciones {
    /** @return nombre para mostrar en el selector */
    String nombre();

    /** Devuelve las canciones en el orden propio de la coleccion. */
    List<Cancion> canciones();

    /** Indica si el usuario puede meter y sacar canciones a mano. */
    boolean admiteEdicion();

    /** @return cuantas canciones tiene */
    default int tamanio() {
        return canciones().size();
    }

    /** @return {@code true} si no hay nada que reproducir */
    default boolean estaVacia() {
        return canciones().isEmpty();
    }
}

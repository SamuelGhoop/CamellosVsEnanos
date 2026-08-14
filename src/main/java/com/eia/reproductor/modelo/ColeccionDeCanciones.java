package com.eia.reproductor.modelo;

import java.util.List;

/**
 * Un conjunto de canciones que se puede poner a sonar.
 *
 * <p><b>Por que existe.</b> Los tres modos de reproduccion reciben las canciones con
 * {@code cargar(Iterable<Cancion>)} y les da igual de donde salgan. Esta interfaz aprovecha esa
 * indiferencia: cualquier coleccion —toda la biblioteca, las favoritas o una lista hecha a mano— se
 * puede reproducir con cualquiera de los tres modos, sin que las estructuras de datos se enteren de
 * que existen las listas.</p>
 *
 * <p>El controlador guarda una referencia de este tipo y nunca pregunta de que clase es, igual que
 * hace con {@link com.eia.reproductor.modos.ModoReproduccion}. De ahi que agregar una coleccion
 * nueva no obligue a tocar la logica de reproduccion.</p>
 */
public interface ColeccionDeCanciones {

    /** @return nombre para mostrar en el selector */
    String nombre();

    /**
     * Devuelve las canciones en el orden propio de la coleccion.
     *
     * <p>Se calcula en cada llamada y no se guarda: una coleccion como las favoritas cambia sola
     * cuando el usuario marca una estrella, y devolver una copia vieja mostraria datos rancios.</p>
     *
     * @return las canciones que la componen
     */
    List<Cancion> canciones();

    /**
     * Indica si el usuario puede meter y sacar canciones a mano.
     *
     * <p>Es falso para la biblioteca entera y para las favoritas: la primera contiene todo por
     * definicion, y la segunda se gobierna con la estrella de cada cancion. Solo las listas
     * personales se editan directamente.</p>
     *
     * @return {@code true} si admite agregar y quitar canciones
     */
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

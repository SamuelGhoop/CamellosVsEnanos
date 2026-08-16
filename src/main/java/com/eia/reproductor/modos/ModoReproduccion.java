package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.EstructuraVisual;

import java.util.List;

/** Contrato que cumple todo modo de reproduccion. */
public interface ModoReproduccion {
    /** Vuelca la biblioteca dentro de la estructura del modo. */
    void cargar(Iterable<Cancion> canciones);

    /** @return la cancion que se esta reproduciendo, o {@code null} si no ha empezado la reproduccion */
    Cancion actual();

    /** Avanza a la siguiente cancion segun la logica del modo. */
    Cancion siguiente();

    /** Retrocede a la cancion anterior segun la logica del modo. */
    Cancion anterior();

    /** Indica si el modo admite retroceder. */
    boolean permiteAnterior();

    /** @return {@code true} si todavia queda algo por reproducir */
    boolean hayMas();

    /** Incorpora una cancion recien agregada a la biblioteca. */
    void agregar(Cancion cancion);

    /** Saca de la estructura una cancion eliminada de la biblioteca. */
    void eliminar(Cancion cancion);

    /** Avisa que una cancion ya presente esta a punto de cambiar de datos. */
    void prepararEdicion(Cancion cancion);

    /** Avisa que una cancion termino de cambiar de datos y hay que reubicarla. */
    void confirmarEdicion(Cancion cancion);

    /** @return el nombre del modo, para mostrarlo en la interfaz */
    String nombre();

    /** @return el nombre de la estructura de datos que usa el modo, para mostrarlo en la interfaz */
    String estructuraUsada();

    /** Devuelve el orden de reproduccion tal como lo ve el modo, solo para mostrarlo. */
    List<Cancion> listaReproduccion();

    /** Devuelve las canciones ya reproducidas, de la mas antigua a la mas reciente. */
    List<Cancion> historial();

    /** Describe la estructura de datos que este modo tiene cargada, para poder dibujarla. */
    EstructuraVisual estructuraVisual();

    /** Devuelve la reproduccion al comienzo de lo que el modo tenga cargado. */
    void reiniciar();
}

package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ColaSimple;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/** Base comun de los tres modos de reproduccion. */
public abstract class ModoBase implements ModoReproduccion {
    /** Cantidad maxima de canciones que se conservan en el historial. */
    public static final int MAX_HISTORIAL = 100;

    private final ColaSimple<Cancion> historial = new ColaSimple<>();
    private Cancion cancionActual;

    // --- Plantilla de navegacion (identica para los tres modos) ---

    @Override
    public final Cancion actual() {
        return cancionActual;
    }

    @Override
    public final Cancion siguiente() {
        exigirCanciones();
        Cancion elegida = calcularSiguiente();
        pasarAReproducir(elegida);
        return elegida;
    }

    @Override
    public final Cancion anterior() {
        if (!permiteAnterior()) {
            throw new UnsupportedOperationException(
                    "El modo " + nombre() + " no permite regresar a canciones anteriores: "
                            + estructuraUsada() + " solo avanza en un sentido.");
        }
        exigirCanciones();
        Cancion elegida = calcularAnterior();
        pasarAReproducir(elegida);
        return elegida;
    }

    @Override
    public final void reiniciar() {
        historial.limpiar();
        cancionActual = null;
        reiniciarNavegacion();
    }

    // --- Pasos que cada modo resuelve a su manera ---

    /** Decide cual es la siguiente cancion segun la estructura del modo. */
    protected abstract Cancion calcularSiguiente();

    /** Decide cual es la cancion anterior segun la estructura del modo. */
    protected Cancion calcularAnterior() {
        throw new UnsupportedOperationException(
                "El modo " + nombre() + " no implementa el retroceso.");
    }

    /** Devuelve la estructura del modo a su posicion inicial, sin vaciarla. */
    protected abstract void reiniciarNavegacion();

    // --- Edicion de canciones ya cargadas ---

    /**
     * {@inheritDoc} Por defecto no hace nada, porque el caso general es que la posicion de una
     * cancion dentro de la estructura no dependa de sus datos: una lista circular la ubica donde se
     * agrego y una cola por su orden de llegada, asi que cambiarle el titulo o la calificacion no
     * las afecta.
     */
    @Override
    public void prepararEdicion(Cancion cancion) {
    }

    /**
     * {@inheritDoc} Por defecto no hace nada, por el mismo motivo que {@link
     * #prepararEdicion(Cancion)}.
     */
    @Override
    public void confirmarEdicion(Cancion cancion) {
    }

    // --- Utilidades para las subclases ---

    /** Fija la cancion actual sin tocar el historial. */
    protected final void establecerActual(Cancion cancion) {
        this.cancionActual = cancion;
    }

    /** Lanza una excepcion si el modo no tiene nada que reproducir. */
    private void exigirCanciones() {
        if (!hayMas()) {
            throw new NoSuchElementException(
                    "El modo " + nombre() + " no tiene canciones disponibles para reproducir.");
        }
    }

    private void pasarAReproducir(Cancion cancion) {
        cancionActual = cancion;
        historial.encolar(cancion);
        // La cola crece solo hasta el tope: cuando se pasa, se descarta la mas antigua en O(1).
        while (historial.tamanio() > MAX_HISTORIAL) {
            historial.desencolar();
        }
    }

    // --- Historial ---

    /** Devuelve las canciones reproducidas, de la mas antigua a la mas reciente. */
    @Override
    public List<Cancion> historial() {
        List<Cancion> copia = new ArrayList<>(historial.tamanio());
        for (Cancion cancion : historial) {
            copia.add(cancion);
        }
        return copia;
    }

    @Override
    public String toString() {
        return nombre() + " [" + estructuraUsada() + "]";
    }
}

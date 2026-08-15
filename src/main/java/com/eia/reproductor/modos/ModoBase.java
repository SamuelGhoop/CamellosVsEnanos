package com.eia.reproductor.modos;

import com.eia.reproductor.estructuras.ColaSimple;
import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Base comun de los tres modos de reproduccion.
 *
 * <p>Concentra lo que los tres modos hacen igual: recordar que cancion suena, llevar el historial
 * y validar antes de moverse. Lo unico que cambia de un modo a otro es <i>como</i> se decide cual
 * es la cancion siguiente o la anterior, y eso queda en manos de las subclases.</p>
 *
 * <p><b>Patron plantilla.</b> {@link #siguiente()} y {@link #anterior()} son {@code final}: fijan
 * el procedimiento (validar, pedir la cancion a la subclase, guardarla como actual, registrarla en
 * el historial) y delegan el unico paso variable en {@link #calcularSiguiente()} y
 * {@link #calcularAnterior()}. Asi ninguna subclase puede olvidarse de validar o de actualizar el
 * historial.</p>
 *
 * <p>El historial se guarda en una {@link ColaSimple} propia: es la estructura natural para una
 * ventana de los ultimos elementos, porque agregar al final y descartar el mas antiguo cuestan
 * ambos O(1).</p>
 */
public abstract class ModoBase implements ModoReproduccion {

    /** Cantidad maxima de canciones que se conservan en el historial. */
    public static final int MAX_HISTORIAL = 100;

    private final ColaSimple<Cancion> historial = new ColaSimple<>();
    private Cancion cancionActual;

    // ------------------------------------------------------------------
    // Plantilla de navegacion (identica para los tres modos)
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Pasos que cada modo resuelve a su manera
    // ------------------------------------------------------------------

    /**
     * Decide cual es la siguiente cancion segun la estructura del modo.
     *
     * <p>Se invoca con la garantia de que el modo tiene canciones disponibles. Cuando
     * {@link #actual()} es {@code null} significa que la reproduccion aun no empezo, y lo que
     * corresponde devolver es la <i>primera</i> cancion del orden, no la segunda.</p>
     *
     * @return la cancion que debe sonar a continuacion
     */
    protected abstract Cancion calcularSiguiente();

    /**
     * Decide cual es la cancion anterior segun la estructura del modo.
     *
     * <p>La implementacion por defecto falla, porque el caso general es que un modo no pueda
     * retroceder. Solo la sobrescriben los modos cuya estructura lo permite; los demas se limitan
     * a devolver {@code false} en {@link #permiteAnterior()} y no tienen que escribir nada.</p>
     *
     * @return la cancion anterior en el orden del modo
     * @throws UnsupportedOperationException si el modo no admite retroceder
     */
    protected Cancion calcularAnterior() {
        throw new UnsupportedOperationException(
                "El modo " + nombre() + " no implementa el retroceso.");
    }

    /** Devuelve la estructura del modo a su posicion inicial, sin vaciarla. */
    protected abstract void reiniciarNavegacion();

    // ------------------------------------------------------------------
    // Edicion de canciones ya cargadas
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Por defecto no hace nada, porque el caso general es que la posicion de una cancion dentro
     * de la estructura <b>no dependa de sus datos</b>: una lista circular la ubica donde se
     * agrego y una cola por su orden de llegada, asi que cambiarle el titulo o la calificacion no
     * las afecta. Solo el modo alfabetico, que ordena por titulo, necesita reaccionar.</p>
     */
    @Override
    public void prepararEdicion(Cancion cancion) {
    }

    /**
     * {@inheritDoc}
     *
     * <p>Por defecto no hace nada, por el mismo motivo que {@link #prepararEdicion(Cancion)}.</p>
     */
    @Override
    public void confirmarEdicion(Cancion cancion) {
    }

    // ------------------------------------------------------------------
    // Utilidades para las subclases
    // ------------------------------------------------------------------

    /**
     * Fija la cancion actual sin tocar el historial.
     *
     * <p>La usan las subclases cuando la cancion en curso cambia por un motivo ajeno a la
     * navegacion, por ejemplo si el usuario elimina de la biblioteca justo la que estaba sonando.
     * Ese cambio no es una reproduccion y por eso no debe quedar registrado.</p>
     *
     * @param cancion nueva cancion actual, admite {@code null}
     */
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

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    /**
     * Devuelve las canciones reproducidas, de la mas antigua a la mas reciente.
     *
     * <p>Solo para mostrar en la interfaz. Se limita a las ultimas {@value #MAX_HISTORIAL}.</p>
     *
     * @return copia del historial en orden cronologico
     */
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
